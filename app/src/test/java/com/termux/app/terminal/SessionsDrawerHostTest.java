package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the drawer asks of the wall: floats under their sessions, the Displays group, and Home.
 *
 * <p>The drawer only asks; every answer here comes from a recording host, so none of it needs the
 * activity. With no host, nothing new appears but the Home row.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SessionsDrawerHostTest {

    @Before
    public void themeTheApplication() {
        RuntimeEnvironment.getApplication().setTheme(
            com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
    }

    @Test
    public void aWindowWhoseOnlyPaneFloatsIsMarkedWithItsPage() {
        RecordingHost host = new RecordingHost();
        host.pages.put(71L, 1);
        SessionsDrawerView drawer = drawer(host, new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Arrays.asList(tiled(70, 0), floating(71, 1)))));

        findText(drawer, "›").performClick();

        assertNotNull("the page is one-based in prose",
            findText(drawer, string(R.string.sessions_drawer_float_page, 2)));
        assertNotNull("the tiled window still counts its panes",
            findText(drawer, RuntimeEnvironment.getApplication().getResources()
                .getQuantityString(R.plurals.session_browser_pane_count, 1, 1)));
    }

    @Test
    public void aFloatWhosePageIsUnknownIsStillMarkedAsAFloat() {
        SessionsDrawerView drawer = drawer(new RecordingHost(), new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Collections.singletonList(floating(71, 0)))));

        findText(drawer, "›").performClick();

        assertNotNull(findText(drawer, string(R.string.sessions_drawer_float)));
    }

    /** A tiled window with a float over it is a tiled window: the float is only a pane of it. */
    @Test
    public void aWindowWithATiledPaneAndAFloatIsNotAFloat() {
        SessionBrowserModel.Window mixed = new SessionBrowserModel.Window(70, 0, true, 0,
            Arrays.asList(new SessionBrowserModel.Pane("/home", "bash"),
                new SessionBrowserModel.Pane("/home", "htop", null, true)), null);
        assertFalse(mixed.isFloat());
        assertTrue(floating(71, 1).isFloat());
        assertFalse(tiled(72, 2).isFloat());
    }

    @Test
    public void tappingAFloatAsksTheHostToRestoreItRatherThanActivatingTheWindow() {
        RecordingHost host = new RecordingHost();
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(host, listener);
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Arrays.asList(tiled(70, 0), floating(71, 1)))));
        findText(drawer, "›").performClick();

        drawer.rowsForTest().getChildAt(2).performClick();
        assertEquals(Collections.singletonList("restore:71"), host.events);
        assertTrue(listener.events.isEmpty());

        drawer.rowsForTest().getChildAt(1).performClick();
        assertEquals("a tiled window is still activated the way it always was",
            Collections.singletonList("window:7/70"), listener.events);
    }

    @Test
    public void theDisplaysGroupIsAbsentWithoutADisplay() {
        SessionsDrawerView drawer = drawer(new RecordingHost(), new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Collections.singletonList(tiled(70, 0)))));

        assertNull(findText(drawer, string(R.string.sessions_drawer_displays)));
        assertEquals(1, drawer.rowsForTest().getChildCount());
    }

    @Test
    public void aDisplayListsItsToplevelsAndRoutesEachTap() {
        RecordingHost host = new RecordingHost();
        host.displays.add(new SessionsDrawerHost.Display(":0", "Display",
            Arrays.asList(new SessionsDrawerHost.Toplevel(0x400001, "Firefox"),
                new SessionsDrawerHost.Toplevel(0x600002, "  "))));
        SessionsDrawerView drawer = drawer(host, new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Collections.singletonList(tiled(70, 0)))));

        assertNotNull(findText(drawer, string(R.string.sessions_drawer_displays)));
        assertEquals("the session, the caption, the display and its two toplevels",
            5, drawer.rowsForTest().getChildCount());
        assertNotNull("a toplevel with no name still has a row to tap",
            findText(drawer, string(R.string.sessions_panel_window_fallback)));

        assertNotNull(findText(drawer.rowsForTest().getChildAt(2), "Display"));
        assertNotNull(findText(drawer.rowsForTest().getChildAt(3), "Firefox"));
        drawer.rowsForTest().getChildAt(2).performClick();
        drawer.rowsForTest().getChildAt(3).performClick();
        assertEquals(Arrays.asList("display::0", "raise::0/" + 0x400001), host.events);
    }

    /** A display with no sessions beside it is something to show, not an empty drawer. */
    @Test
    public void aDisplayAloneIsNotTheEmptyState() {
        RecordingHost host = new RecordingHost();
        host.displays.add(new SessionsDrawerHost.Display(":0", null,
            Collections.<SessionsDrawerHost.Toplevel>emptyList()));
        SessionsDrawerView drawer = drawer(host, new RecordingListener());
        drawer.bindLive(Collections.<SessionBrowserModel.Session>emptyList());

        assertNotNull("a blank label falls back to the display's name", findText(drawer, ":0"));
        assertEquals("the caption and the display", 2, drawer.rowsForTest().getChildCount());
        assertEquals("the list shows instead of the empty line",
            View.VISIBLE, ((View) drawer.rowsForTest().getParent()).getVisibility());
    }

    @Test
    public void theHomeRowSitsAboveTheSaveRowOnTheLiveHalfAndAloneOnTheSavedHalf() {
        RecordingHost host = new RecordingHost();
        SessionsDrawerView drawer = drawer(host, new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Collections.singletonList(tiled(70, 0)))));

        View home = findText(drawer, string(R.string.sessions_drawer_home));
        View save = findText(drawer, string(R.string.sessions_drawer_save_workspace));
        assertNotNull(home);
        assertNotNull(save);
        ViewGroup footer = (ViewGroup) home.getParent();
        assertEquals(footer, save.getParent());
        assertEquals(0, footer.indexOfChild(home));
        assertEquals(1, footer.indexOfChild(save));

        home.performClick();
        assertEquals(Collections.singletonList("home"), host.events);

        drawer.setTab(SessionsDrawerView.Tab.SAVED);
        assertNotNull(findText(drawer, string(R.string.sessions_drawer_home)));
        assertNull(findText(drawer, string(R.string.sessions_drawer_save_workspace)));
    }

    /** The Home row stays while the save field is open, so the way out is never folded away. */
    @Test
    public void theHomeRowStaysAboveAnOpenSaveField() {
        SessionsDrawerView drawer = drawer(new RecordingHost(), new RecordingListener());
        drawer.openSaveField();

        assertTrue(drawer.isSaveFieldOpen());
        assertNotNull(findText(drawer, string(R.string.sessions_drawer_home)));
    }

    @Test
    public void withoutAHostTheDrawerShowsOnlyWhatItAlwaysDidPlusHome() {
        SessionsDrawerView drawer = new SessionsDrawerView(RuntimeEnvironment.getApplication());
        drawer.setListener(new RecordingListener());
        drawer.bindLive(Collections.singletonList(new SessionBrowserModel.Session(7, 0, true,
            "build", Collections.singletonList(floating(71, 0)))));

        findText(drawer, "›").performClick();
        assertNull(findText(drawer, string(R.string.sessions_drawer_displays)));
        assertNotNull(findText(drawer, string(R.string.sessions_drawer_float)));
        findText(drawer, string(R.string.sessions_drawer_home)).performClick();
        drawer.rowsForTest().getChildAt(1).performClick();
    }

    // ------------------------------------------------------------------ helpers

    @NonNull
    private static SessionsDrawerView drawer(@NonNull SessionsDrawerHost host,
                                             @NonNull SessionsDrawerView.Listener listener) {
        SessionsDrawerView view = new SessionsDrawerView(RuntimeEnvironment.getApplication());
        view.setListener(listener);
        view.setHost(host);
        return view;
    }

    @NonNull
    private static SessionBrowserModel.Window tiled(long id, int index) {
        return new SessionBrowserModel.Window(id, index, index == 0, 0,
            Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")), null);
    }

    @NonNull
    private static SessionBrowserModel.Window floating(long id, int index) {
        return new SessionBrowserModel.Window(id, index, index == 0, 0,
            Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash", null, true)),
            null);
    }

    @NonNull
    private static String string(int res, Object... args) {
        return RuntimeEnvironment.getApplication().getString(res, args);
    }

    @Nullable
    private static View findText(@NonNull View view, @NonNull String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static final class RecordingHost implements SessionsDrawerHost {
        final List<String> events = new ArrayList<>();
        final Map<Long, Integer> pages = new HashMap<>();
        final List<Display> displays = new ArrayList<>();

        @Override public int homeFloatPage(long windowId) {
            Integer page = pages.get(windowId);
            return page == null ? UNKNOWN_PAGE : page;
        }
        @NonNull @Override public List<Display> displays() { return displays; }
        @Override public void goHome() { events.add("home"); }
        @Override public void goToDisplay(@NonNull String id) { events.add("display:" + id); }
        @Override public void raiseToplevel(@NonNull String id, int toplevel) {
            events.add("raise:" + id + "/" + toplevel);
        }
        @Override public void restoreFloat(long windowId) { events.add("restore:" + windowId); }
    }

    private static final class RecordingListener implements SessionsDrawerView.Listener {
        final List<String> events = new ArrayList<>();

        @Override public void onNewSession() { events.add("new"); }
        @Override public void onNewSessionPrompt() { events.add("newPrompt"); }
        @Override public void onActivateSession(long id) { events.add("activate:" + id); }
        @Override public void onActivateWindow(long s, long w) { events.add("window:" + s + "/" + w); }
        @Override public void onCloneSession(long id) { events.add("clone:" + id); }
        @Override public void onRenameSession(long id, @NonNull String name) {
            events.add("rename:" + id + "=" + name);
        }
        @Override public void onCloseSession(long id) { events.add("close:" + id); }
        @Override public void onSaveWorkspace(@NonNull String name, boolean capture) {
            events.add("save:" + name);
        }
        @Override public void onLoadWorkspace(@NonNull String name, boolean replace, boolean run) {
            events.add("load:" + name);
        }
        @Override public void onDeleteWorkspace(@NonNull String name) {
            events.add("delete:" + name);
        }
        @Override public void onTypingStarted() { events.add("typing"); }
    }
}
