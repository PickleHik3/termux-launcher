package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

/** The Choose a key sheet opens on its grid, and keeps the grid when the keyboard comes up. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, application = Application.class,
    qualifiers = "w411dp-h914dp-xxhdpi")
public class ExtraKeyActionPickerSheetTest {
    private static final int WIDTH = 1233;
    private static final int HEIGHT = 2742;

    private static Dialog open() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxApp_DayNight_DarkActionBar);
        ExtraKeyActionPicker.show(activity, "Adding to Row 1",
            R.string.settings_extra_keys_picker_confirm_use, key -> { }, null);
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static void layout(View decor) {
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
    }

    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T hit = find(group.getChildAt(i), type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @Test
    public void opensOnTheGridWithTheKeyboardDown() {
        Dialog dialog = open();
        EditText search = find(dialog.getWindow().getDecorView(), EditText.class);
        assertNotNull(search);
        assertFalse("the search field must not take the first focus", search.isFocused());
        int state = dialog.getWindow().getAttributes().softInputMode
            & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN, state);
    }

    @Test
    public void gridKeepsItsRoomAboveTheKeyboard() {
        Dialog dialog = open();
        View decor = dialog.getWindow().getDecorView();
        layout(decor);
        RecyclerView grid = find(decor, RecyclerView.class);
        assertNotNull(grid);
        int restingGrid = grid.getHeight();
        assertTrue("grid has room at rest: " + restingGrid, restingGrid > 0);

        // With the keyboard up under adjustResize the legacy system-window inset carries the
        // keyboard, so the sheet already pads itself above it; the column must not pad again.
        int keyboard = 1000;
        WindowInsets insets = new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 100, 0, keyboard))
            .build();
        decor.dispatchApplyWindowInsets(insets);
        layout(decor);

        View column = (View) grid.getParent();
        int[] location = new int[2];
        column.getLocationInWindow(location);
        int contentBottom = location[1] + column.getHeight() - column.getPaddingBottom();
        assertTrue("the column ends above the keyboard: " + contentBottom,
            contentBottom <= HEIGHT - keyboard);
        assertTrue("the grid keeps most of its room with the keyboard up: " + grid.getHeight(),
            grid.getHeight() >= restingGrid - keyboard);
        assertTrue("the grid is not squeezed to nothing: " + grid.getHeight(),
            grid.getHeight() > grid.getPaddingTop() + grid.getPaddingBottom());
    }
}
