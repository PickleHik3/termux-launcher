package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.desktop.TilingDesktop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Where the bar's tools go (#424): the window in front's own content first, never v1's pages. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ControlBarTest {

    private final List<String> calls = new ArrayList<>();
    private boolean frontTakesKeyboard;
    private ControlBar bar;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
    }

    @Test public void keyboardGoesToTheWindowInFrontWhenItTakesIt() {
        frontTakesKeyboard = true;
        bar.onTool(ControlBarTool.KEYBOARD);
        assertEquals(Collections.singletonList("desktop keyboard"), calls);
    }

    @Test public void aShellsKeyboardIsTheLaunchersOwn() {
        frontTakesKeyboard = false;
        bar.onTool(ControlBarTool.KEYBOARD);
        assertEquals(Arrays.asList("desktop keyboard", "host keyboard"), calls);
    }

    @Test public void mouseModeIsOnlyTheWindowInFronts() {
        bar.onTool(ControlBarTool.MOUSE_MODE);
        assertEquals(Collections.singletonList("desktop mouse"), calls);
    }

    @Test public void splitNewTerminalAndSearchGoWhereTheyBelong() {
        bar.onTool(ControlBarTool.SPLIT);
        bar.onTool(ControlBarTool.NEW_TERMINAL);
        bar.onTool(ControlBarTool.SEARCH);
        bar.onTool(ControlBarTool.WINDOW_MENU);
        bar.onTool(ControlBarTool.NAVIGATION);
        assertEquals(Arrays.asList("split", "new terminal", "search"), calls);
    }

    private final class FakeDesktop implements ControlBar.Desktop {
        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return Place.workspace(1); }
        @NonNull @Override public List<TilingWindow> frontWindows() { return Collections.emptyList(); }
        @Nullable @Override public TilingWindow frontMainWindow() { return null; }
        @Override public long frontFocusedWindowId() { return -1; }
        @Override public boolean focusWindow(long windowId) { calls.add("focus " + windowId); return true; }
        @Override public void split() { calls.add("split"); }
        @Override public void newTerminal() { calls.add("new terminal"); }
        @Override public boolean toggleFrontKeyboard() { calls.add("desktop keyboard"); return frontTakesKeyboard; }
        @Override public boolean toggleFrontMouseMode() { calls.add("desktop mouse"); return true; }
    }

    private final class FakeHost implements ControlBar.Host {
        @Override public int homePageCount() { return 1; }
        @Override public int homePageInFront() { return 0; }
        @Override public void showHomePage(int page) { calls.add("home " + page); }
        @NonNull @Override public List<ControlBarView.PinnedApp> pinnedApps() { return Collections.emptyList(); }
        @Override public void openPinnedApp(int index) { calls.add("app " + index); }
        @Override public void toggleKeyboard() { calls.add("host keyboard"); }
        @Override public void openSearch() { calls.add("search"); }
        @Override public void openDrawer() { calls.add("drawer"); }
        @Override public boolean drawerToolShown() { return false; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 0f; }
        @Override public int ink() { return 0xFFFFFFFF; }
        @Override public int accent() { return 0xFF00FF00; }
    }
}
