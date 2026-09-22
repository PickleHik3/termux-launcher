package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
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
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The pane API (pane.open/list/focus/close/write/read) against a real pane controller. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalActionDispatcherPaneTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
    private PaneHost host;

    @Before
    public void attach() throws java.io.IOException {
        AgentPaneRegistry.getInstance().clear();
        host = new PaneHost();
        dispatcher.attach(host);
    }

    @After
    public void detach() {
        dispatcher.detach(host);
        AgentPaneRegistry.getInstance().clear();
    }

    @Test
    public void open_addsAnOwnedPaneAndReportsIt() throws JSONException {
        JSONObject result = dispatcher.execute("pane.open", new JSONObject()
            .put("command", new JSONArray().put("make").put("test"))
            .put("title", "build").put("tag", "claude"));
        assertTrue(result.toString(), result.getBoolean("ok"));
        JSONObject pane = result.getJSONObject("pane");
        String id = pane.getString("id");
        assertEquals("build", pane.getString("name"));
        assertEquals("claude", pane.getJSONObject("agent").getString("tag"));
        assertEquals("make", pane.getJSONObject("agent").getJSONArray("command").getString(0));
        assertTrue(pane.getBoolean("focused"));
        assertEquals(2, host.controller.shellsOf(host.window).size());
        assertEquals(id, host.controller.getActiveSession().mHandle);
        assertEquals(Collections.singletonList("make"), host.lastCommand.subList(0, 1));
        assertTrue(AgentPaneRegistry.getInstance().isOwned(id));
    }

    @Test
    public void open_acceptsACommandLineAndCanLeaveFocusAlone() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        JSONObject result = dispatcher.execute("pane.open", new JSONObject()
            .put("command", "kitten icat out.png").put("focus", false));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(java.util.Arrays.asList("sh", "-c", "kitten icat out.png"), host.lastCommand);
        assertSame(usersShell, host.controller.getActiveSession());
        assertFalse(result.getJSONObject("pane").getBoolean("focused"));

        JSONObject bad = dispatcher.execute("pane.open", new JSONObject().put("command", 42));
        assertEquals("bad_request", bad.getString("error"));
    }

    @Test
    public void open_refusesWhenNoPaneCanBeAdded() throws JSONException {
        host.refuseOpen = true;
        JSONObject result = dispatcher.execute("pane.open", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("pane_open_failed", result.getString("error"));
        assertEquals(0, AgentPaneRegistry.getInstance().size());
    }

    @Test
    public void list_showsEveryPaneAndMarksTheOwnedOnes() throws JSONException {
        String opened = dispatcher.execute("pane.open", new JSONObject().put("tag", "codex"))
            .getJSONObject("pane").getString("id");
        JSONObject result = dispatcher.execute("pane.list", new JSONObject());
        assertTrue(result.toString(), result.getBoolean("ok"));
        JSONArray windows = result.getJSONArray("windows");
        assertEquals(1, windows.length());
        JSONObject window = windows.getJSONObject(0);
        assertTrue(window.getBoolean("current"));
        assertEquals(opened, window.getString("focusedPane"));
        JSONArray panes = window.getJSONArray("panes");
        assertEquals(2, panes.length());
        assertTrue(panes.getJSONObject(0).isNull("agent"));
        assertEquals("codex", panes.getJSONObject(1).getJSONObject("agent").getString("tag"));
        assertEquals(opened, result.getString("activePane"));
    }

    @Test
    public void writeReadClose_onlyReachOwnedPanes() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        JSONObject refusedWrite = dispatcher.execute("pane.write", new JSONObject()
            .put("id", usersShell.mHandle).put("text", "rm -rf /"));
        assertEquals(403, refusedWrite.getInt("_statusCode"));
        assertEquals("not_owned", refusedWrite.getString("error"));
        JSONObject refusedRead = dispatcher.execute("pane.read", new JSONObject().put("id", usersShell.mHandle));
        assertEquals("not_owned", refusedRead.getString("error"));
        JSONObject refusedClose = dispatcher.execute("pane.close", new JSONObject().put("id", usersShell.mHandle));
        assertEquals("not_owned", refusedClose.getString("error"));
        assertEquals(1, host.controller.shellsOf(host.window).size());

        String owned = dispatcher.execute("pane.open", new JSONObject())
            .getJSONObject("pane").getString("id");
        TerminalSession ownedShell = host.findPaneById(owned);
        assertNotNull(ownedShell);
        // The fake shell has no process behind it: a write must say so instead of vanishing.
        ReflectionHelpers.setField(ownedShell, "mShellPid", -1);
        JSONObject write = dispatcher.execute("pane.write", new JSONObject()
            .put("id", owned).put("text", "ls").put("enter", true));
        assertEquals("pane_not_running", write.getString("error"));
        JSONObject missingText = dispatcher.execute("pane.write", new JSONObject().put("id", owned));
        assertEquals("bad_request", missingText.getString("error"));

        JSONObject read = dispatcher.execute("pane.read", new JSONObject().put("id", owned).put("lines", 5));
        assertTrue(read.toString(), read.getBoolean("ok"));
        assertEquals("", read.getString("text"));

        JSONObject close = dispatcher.execute("pane.close", new JSONObject().put("id", owned));
        assertTrue(close.toString(), close.getBoolean("ok"));
        assertFalse(AgentPaneRegistry.getInstance().isOwned(owned));
    }

    @Test
    public void focusAndUnknownIds() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        String opened = dispatcher.execute("pane.open", new JSONObject())
            .getJSONObject("pane").getString("id");
        assertEquals(opened, host.controller.getActiveSession().mHandle);

        JSONObject focus = dispatcher.execute("pane.focus", new JSONObject().put("id", usersShell.mHandle));
        assertTrue(focus.toString(), focus.getBoolean("ok"));
        assertSame(usersShell, host.controller.getActiveSession());
        assertTrue(focus.getJSONObject("pane").getBoolean("focused"));

        assertEquals(404, dispatcher.execute("pane.focus", new JSONObject().put("id", "nope")).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("pane.focus", new JSONObject()).getInt("_statusCode"));
    }

    /**
     * The full pane API against a real pane controller, with the host stopped (alive, not
     * visible) exactly as it is once the user switches away from the launcher: open, list, focus,
     * write, read and close must all keep working, and ownership must still gate write/read/close
     * exactly as it does while visible.
     */
    @Test
    public void fullPaneLifecycleWorksWithTheHostStoppedAndOwnershipStillApplies() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        host.visible = false;

        JSONObject opened = dispatcher.execute("pane.open", new JSONObject()
            .put("command", new JSONArray().put("make")).put("tag", "claude"));
        assertTrue(opened.toString(), opened.getBoolean("ok"));
        String id = opened.getJSONObject("pane").getString("id");

        // Ownership is unaffected by visibility: the user's own shell is still off-limits.
        JSONObject refused = dispatcher.execute("pane.write", new JSONObject()
            .put("id", usersShell.mHandle).put("text", "rm -rf /"));
        assertEquals("not_owned", refused.getString("error"));

        JSONObject list = dispatcher.execute("pane.list", new JSONObject());
        assertTrue(list.toString(), list.getBoolean("ok"));
        assertEquals(id, list.getString("activePane"));

        JSONObject focus = dispatcher.execute("pane.focus", new JSONObject().put("id", usersShell.mHandle));
        assertTrue(focus.toString(), focus.getBoolean("ok"));
        assertSame(usersShell, host.controller.getActiveSession());

        JSONObject write = dispatcher.execute("pane.write", new JSONObject()
            .put("id", id).put("text", "ls").put("enter", true));
        assertTrue(write.toString(), write.getBoolean("ok"));

        JSONObject read = dispatcher.execute("pane.read", new JSONObject().put("id", id));
        assertTrue(read.toString(), read.getBoolean("ok"));

        JSONObject close = dispatcher.execute("pane.close", new JSONObject().put("id", id));
        assertTrue(close.toString(), close.getBoolean("ok"));
        assertFalse(AgentPaneRegistry.getInstance().isOwned(id));

        // No hint toast for any of the above: the host was never visible.
        assertFalse(host.called("showTerminalActionHint"));
    }

    /** A foreground-only tool run through the same stopped host must still be refused. */
    @Test
    public void aForegroundOnlyToolStillRefusesOnTheStoppedHost() throws JSONException {
        host.visible = false;
        JSONObject result = dispatcher.execute("pane.split_vertical", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("activity_not_running", result.getString("error"));
    }

    /** Once the host is destroyed outright, the pane routes fail exactly like everything else. */
    @Test
    public void aDestroyedHostRefusesEveryPaneRoute() throws JSONException {
        host.visible = false;
        host.alive = false;
        for (String tool : new String[]{"pane.open", "pane.list", "pane.focus", "pane.write",
                "pane.read", "pane.close"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject().put("id", "whatever"));
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "activity_not_running", result.getString("error"));
        }
    }

    @Test
    public void lastLines_keepsTheNewestLinesAndDropsTrailingBlanks() {
        assertEquals("c\nd", TerminalActionDispatcher.lastLines("a\nb\nc\nd\n\n", 2));
        assertEquals("a\nb", TerminalActionDispatcher.lastLines("a\nb", 10));
        assertEquals("", TerminalActionDispatcher.lastLines("\n\n   ", 3));
        assertEquals("d", TerminalActionDispatcher.lastLines("a\nb\nc\nd", 1));
    }

    /** A host whose pane surface is a real controller with one window and one shell. */
    private static final class PaneHost extends FakeTerminalHost {
        final TerminalPaneController controller;
        final TerminalPaneController.Window window;
        List<String> lastCommand = new ArrayList<>();
        boolean refuseOpen;

        PaneHost() throws java.io.IOException {
            super(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties());
            Context context = RuntimeEnvironment.getApplication();
            controller = new TerminalPaneController(new TerminalPaneController.Host() {
                @Override public TerminalSession createShell(String cwd) { return shell(); }
                @Override public void configurePaneView(TerminalView view) {}
                @Override public void removeShell(TerminalSession session) {}
                @Override public void onActivePaneChanged() {}
                @Override public void onTreesChanged() {}
                @Override public String defaultCwd() { return "/"; }
            }, new FrameLayout(context), LayoutInflater.from(context));
            window = controller.newWindow(shell());
            controller.showWindow(window);
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

        @Override @Nullable public TerminalSession openCommandPane(@NonNull List<String> command,
                                                                    @Nullable String cwd,
                                                                    @Nullable String title,
                                                                    boolean focus) {
            if (refuseOpen) return null;
            lastCommand = new ArrayList<>(command);
            TerminalSession session = shell();
            session.mSessionName = title;
            return controller.addPane(session, focus) ? session : null;
        }

        @Override @NonNull public List<TerminalPaneController.Window> currentSessionWindows() {
            return Collections.singletonList(window);
        }

        @Override @Nullable public TerminalSession findPaneById(@NonNull String id) {
            for (TerminalSession shell : controller.shellsOf(window)) {
                if (id.equals(shell.mHandle)) return shell;
            }
            return null;
        }

        @Override public boolean activateSessionInPanes(TerminalSession session) {
            controller.focusSession(session);
            return true;
        }

        @Override @Nullable public String activePaneLayoutPolicy() {
            return controller.activeLayoutPolicy();
        }
    }
}
