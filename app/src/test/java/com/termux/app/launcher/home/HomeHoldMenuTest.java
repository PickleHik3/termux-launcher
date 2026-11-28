package com.termux.app.launcher.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.controlbar.ReadingCardView;
import com.termux.app.window.WindowLayer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A hold on empty Home under Terl opens a small menu, not edit mode (decided 2026-11-28): Edit
 * Home &amp; looks, New terminal, Wallpaper and Settings, on a glass card in the sheets layer,
 * anchored at the press and kept on screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
public class HomeHoldMenuTest {

    @Test public void theRowsAreEditNewTerminalWallpaperAndSettings() {
        assertEquals(Arrays.asList(HomeHoldMenu.Row.EDIT, HomeHoldMenu.Row.NEW_TERMINAL,
            HomeHoldMenu.Row.WALLPAPER, HomeHoldMenu.Row.SETTINGS), HomeHoldMenu.rows());
    }

    @Test public void theCardStandsAtThePressAndFlipsAtTheEdges() {
        assertArrayEquals("its corner at the press", new int[] {100, 200},
            HomeHoldMenu.place(100, 200, 300, 400, 1080, 2340, 16));
        assertArrayEquals("near the right edge it opens to the press's left", new int[] {700, 200},
            HomeHoldMenu.place(1000, 200, 300, 400, 1080, 2340, 16));
        assertArrayEquals("near the bottom it opens above the press", new int[] {100, 1800},
            HomeHoldMenu.place(100, 2200, 300, 400, 1080, 2340, 16));
        assertArrayEquals("never off the screen", new int[] {16, 16},
            HomeHoldMenu.place(5, 5, 300, 400, 1080, 2340, 16));
        int[] tight = HomeHoldMenu.place(200, 300, 300, 400, 320, 420, 16);
        assertTrue("a room smaller than the card keeps it at the gap", tight[0] >= 0 && tight[1] >= 0);
    }

    @Test public void theCardOpensInTheSheetsLayerAtThePressInItsGlass() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
        FrameLayout root = new FrameLayout(activity);
        FrameLayout sheets = new FrameLayout(activity);
        sheets.setId(WindowLayer.SHEETS.hostId());
        root.addView(sheets, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        lay(root);
        Drawable glass = new ColorDrawable(0x80102030);
        List<View> glassed = new ArrayList<>();
        List<HomeHoldMenu.Row> chosen = new ArrayList<>();
        HomeHoldMenuView menu = new HomeHoldMenuView(activity, new ReadingCardView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) {
                glassed.add(anchor);
                return glass;
            }
            @Override public float cornerRadiusPx() { return 40f; }
            @Override public int ink() { return 0xFFFFFFFF; }
        }, chosen::add);

        int[] at = new int[2];
        sheets.getLocationOnScreen(at);
        assertTrue(menu.show(root, at[0] + 120f, at[1] + 300f));
        lay(root);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("in the sheets layer", sheets, menu.getParent());
        assertEquals(120, menu.card().getLeft());
        assertEquals(300, menu.card().getTop());
        assertFalse("the glass service dressed the card", glassed.isEmpty());
        assertEquals(menu.card(), glassed.get(0));
        assertEquals(glass, menu.glass());
        assertEquals(Arrays.asList(activity.getString(R.string.home_hold_menu_edit),
            activity.getString(R.string.home_hold_menu_new_terminal),
            activity.getString(R.string.home_hold_menu_wallpaper),
            activity.getString(R.string.home_hold_menu_settings)), menu.rowLabels());

        menu.clickRow(0);
        assertEquals("Edit Home & looks opens edit mode", Arrays.asList(HomeHoldMenu.Row.EDIT), chosen);
        assertFalse("a row closes the card", menu.isOpen());

        // A touch outside the card closes it, and does nothing else.
        assertTrue(menu.show(root, at[0] + 120f, at[1] + 300f));
        lay(root);
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 900f, 2000f, 0);
        menu.dispatchTouchEvent(down);
        assertFalse(menu.isOpen());
        assertEquals(1, chosen.size());
        assertNotNull(menu.getContentDescription());
    }

    private static void lay(@NonNull View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
    }
}
