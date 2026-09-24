package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The window API ({@code window.open}), against a real pane controller with a
 * {@link WindowHost} that tracks every window it is asked to create — separately from the pane
 * API's own {@link TerminalActionDispatcherPaneTest}, since a window is a whole new top-level
 * window rather than a split of the current one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalActionDispatcherWindowTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
    private WindowHost host;

    @Before
    public void attach() throws java.io.IOException {
        AgentPaneRegistry.getInstance().clear();
        host = new WindowHost();
        dispatcher.attach(host);
    }

    @After
    public void detach() {
        dispatcher.detach(host);
        AgentPaneRegistry.getInstance().clear();
    }

    @Test
    public void open_createsANewWindowRatherThanASplit() throws JSONException {
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make").put("test"))
            .put("title", "build"));
        assertTrue(result.toString(), result.getBoolean("ok"));

        // A window, not a split: the original window still holds exactly its one original pane,
        // and a second window now exists holding only the new one.
        assertEquals(1, host.controller.shellsOf(host.firstWindow).size());
        assertEquals(2, host.windows.size());
        TerminalPaneController.Window secondWindow = host.windows.get(1);
        assertEquals(1, host.controller.shellsOf(secondWindow).size());

        String id = result.getString("id");
        assertEquals(id, host.controller.shellsOf(secondWindow).get(0).mHandle);
        assertEquals(1, result.getInt("window"));
        assertTrue(AgentPaneRegistry.getInstance().isOwned(id));
        assertEquals(java.util.Arrays.asList("make", "test"), host.lastCommand);
        assertEquals("build", host.lastTitle);
        assertTrue("focus defaults true", host.lastFocus);
    }

    @Test
    public void open_acceptsACommandLineAndDefaultsFocusTrue() throws JSONException {
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", "kitten icat out.png"));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(java.util.Arrays.asList("sh", "-c", "kitten icat out.png"), host.lastCommand);
        assertTrue(host.lastFocus);
    }

    @Test
    public void open_noFocusLeavesFocusFalse() throws JSONException {
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("sleep").put("1"))
            .put("focus", false));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertFalse(host.lastFocus);
        assertFalse(host.shown.contains(host.windows.get(1)));
    }

    @Test
    public void open_refusesABadCommandTypeAndAMissingCommand() throws JSONException {
        JSONObject badType = dispatcher.execute("window.open", new JSONObject().put("command", 42));
        assertEquals(400, badType.getInt("_statusCode"));
        assertEquals("bad_request", badType.getString("error"));

        JSONObject missing = dispatcher.execute("window.open", new JSONObject());
        assertEquals(400, missing.getInt("_statusCode"));
        assertEquals("bad_request", missing.getString("error"));

        JSONObject badElement = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("ok").put(7)));
        assertEquals(400, badElement.getInt("_statusCode"));
        assertEquals("bad_request", badElement.getString("error"));

        assertEquals(1, host.windows.size());
    }

    @Test
    public void open_refusesWhenNoWindowCanBeOpened() throws JSONException {
        host.refuseOpen = true;
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make")));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("window_open_failed", result.getString("error"));
        assertEquals(0, AgentPaneRegistry.getInstance().size());
    }

    @Test
    public void ownershipMatchesThePaneApi() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        String opened = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make")))
            .getString("id");

        // The window-opened pane is owned exactly like a pane.open pane: write/read/close reach
        // it, and every other pane -- including one the user opened by hand -- stays refused.
        JSONObject write = dispatcher.execute("pane.write", new JSONObject()
            .put("id", opened).put("text", "ls").put("enter", true));
        assertTrue(write.toString(), write.getBoolean("ok"));

        JSONObject read = dispatcher.execute("pane.read", new JSONObject().put("id", opened));
        assertTrue(read.toString(), read.getBoolean("ok"));

        JSONObject refusedWrite = dispatcher.execute("pane.write", new JSONObject()
            .put("id", usersShell.mHandle).put("text", "rm -rf /"));
        assertEquals(403, refusedWrite.getInt("_statusCode"));
        assertEquals("not_owned", refusedWrite.getString("error"));

        JSONObject close = dispatcher.execute("pane.close", new JSONObject().put("id", opened));
        assertTrue(close.toString(), close.getBoolean("ok"));
        assertFalse(AgentPaneRegistry.getInstance().isOwned(opened));
    }

    @Test
    public void aStoppedHostStillOpensAndOwnsAWindow() throws JSONException {
        host.visible = false;
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make")).put("focus", true));
        assertTrue(result.toString(), result.getBoolean("ok"));
        String id = result.getString("id");
        assertTrue(AgentPaneRegistry.getInstance().isOwned(id));
        // No hint toast: the host was never visible for this background-safe route.
        assertFalse(host.called("showTerminalActionHint"));
    }

    @Test
    public void aDestroyedHostRefusesWindowOpen() throws JSONException {
        host.visible = false;
        host.alive = false;
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make")));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("activity_not_running", result.getString("error"));
    }

    @Test
    public void compatibilityModeRefusesWindowOpen() throws JSONException {
        host.splitPanesEnabled = false;
        JSONObject result = dispatcher.execute("window.open", new JSONObject()
            .put("command", new JSONArray().put("make")));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("splits_disabled", result.getString("error"));
    }

    /** A host whose pane surface is a real controller, tracking every window it opens. */
    private static final class WindowHost extends FakeTerminalHost {
        final TerminalPaneController controller;
        final TerminalPaneController.Window firstWindow;
        final List<TerminalPaneController.Window> windows = new ArrayList<>();
        final List<TerminalPaneController.Window> shown = new ArrayList<>();
        List<String> lastCommand = new ArrayList<>();
        String lastTitle;
        boolean lastFocus;
        boolean refuseOpen;

        WindowHost() throws java.io.IOException {
            super(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties());
            android.content.Context context = RuntimeEnvironment.getApplication();
            controller = new TerminalPaneController(new TerminalPaneController.Host() {
                @Override public TerminalSession createShell(String cwd) { return shell(); }
                @Override public void configurePaneView(TerminalView view) {}
                @Override public void removeShell(TerminalSession session) {}
                @Override public void onActivePaneChanged() {}
                @Override public void onTreesChanged() {}
                @Override public String defaultCwd() { return "/"; }
            }, new FrameLayout(context), android.view.LayoutInflater.from(context));
            firstWindow = controller.newWindow(shell());
            controller.showWindow(firstWindow);
            windows.add(firstWindow);
            shown.add(firstWindow);
        }

        private static TerminalSession shell() {
            return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
        }

        @Override @Nullable public TerminalSession currentSession() {
            return controller.getActiveSession();
        }

        @Override @Nullable public TerminalPaneController paneController() {
            return controller;
        }

        @Override @NonNull public List<TerminalPaneController.Window> currentSessionWindows() {
            return new ArrayList<>(windows);
        }

        @Override @Nullable public TerminalSession findPaneById(@NonNull String id) {
            for (TerminalPaneController.Window w : windows) {
                for (TerminalSession shell : controller.shellsOf(w)) {
                    if (id.equals(shell.mHandle)) return shell;
                }
            }
            return null;
        }

        @Override public boolean activateSessionInPanes(TerminalSession session) {
            controller.focusSession(session);
            return true;
        }

        @Override @Nullable public TerminalSession openCommandWindow(
                @NonNull List<String> command, @Nullable String cwd,
                @Nullable String title, boolean focus) {
            if (refuseOpen) return null;
            lastCommand = new ArrayList<>(command);
            lastTitle = title;
            lastFocus = focus;
            TerminalSession session = shell();
            session.mSessionName = title;
            TerminalPaneController.Window w = controller.newWindow(session);
            windows.add(w);
            if (focus) {
                controller.showWindow(w);
                shown.add(w);
            }
            return session;
        }
    }
}
