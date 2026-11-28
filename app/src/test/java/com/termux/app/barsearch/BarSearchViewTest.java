package com.termux.app.barsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Round 5 review P2-2: the search field stands at the top of the sheet, and the sheet keeps one
 * height while it is open, so the field never moves as typing changes how many results there are.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
public class BarSearchViewTest {

    @Test
    public void theFieldStandsStillWhileTypingChangesTheResults() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
        FrameLayout root = new FrameLayout(activity);
        FrameLayout sheets = new FrameLayout(activity);
        sheets.setId(R.id.window_layer_sheets);
        root.addView(sheets, new FrameLayout.LayoutParams(-1, -1));
        View bar = new View(activity);
        root.addView(bar, new FrameLayout.LayoutParams(-1, 100, Gravity.BOTTOM));
        activity.setContentView(root);
        idle(root);

        List<LauncherAppEntry> apps = new ArrayList<>();
        apps.add(new LauncherAppEntry(LauncherEntries.terminal(), "Terminal", null));
        for (String name : new String[] {"Calendar", "Camera", "Chrome", "Clock", "Contacts", "Drive",
            "Files", "Gmail", "Maps", "Messages", "Phone", "Photos", "Settings", "YouTube"}) {
            apps.add(new LauncherAppEntry(new AppRef("com.example." + name.toLowerCase(), "Main"), name, null));
        }
        BarSearch search = new BarSearch(new Host(apps, bar));
        search.open();
        idle(root);
        BarSearchView view = search.view();
        assertNotNull(view);
        int before = view.fieldTopInWindow();

        // "Ca" leaves two results of fifteen; "Cam" one; Backspace brings more back.
        search.handleCodePoint('C', false);
        idle(root);
        search.handleCodePoint('a', false);
        idle(root);
        assertEquals("the field did not move as the results thinned", before, view.fieldTopInWindow());
        search.handleCodePoint('m', false);
        idle(root);
        assertEquals(before, view.fieldTopInWindow());
        assertTrue(search.rows().size() < apps.size());
    }

    private static void idle(@NonNull View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2000);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2000);
    }

    private static final class Host implements BarSearch.Host {
        private final List<LauncherAppEntry> mApps;
        private final View mBar;

        Host(List<LauncherAppEntry> apps, View bar) {
            mApps = apps;
            mBar = bar;
        }

        @NonNull @Override public List<LauncherAppEntry> catalogue() { return mApps; }
        @Override public boolean windowOpen(@NonNull SearchOpen.Target target, @NonNull LauncherAppEntry entry) { return false; }
        @Override public void perform(@NonNull SearchOpen.Action action, @NonNull LauncherAppEntry entry) { }
        @Override public boolean editLookOffered() { return false; }
        @Override public void openEditMode() { }
        @Override public void openDrawer() { }
        @Nullable @Override public View bar() { return mBar; }
        @Override public boolean ensureTypingKeyboard() { return false; }
        @Override public void claimTyping(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor) { }
        @Nullable @Override public Drawable icon(@NonNull LauncherAppEntry entry) { return null; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 24f; }
        @Override public int ink() { return 0xFFFFFFFF; }
    }
}
