package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

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
import static org.junit.Assert.assertNull;
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
                "pane.read", "pane.close", "pane.set_size", "window.focus", "window.close_by_id",
                "pane.info"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject().put("id", "whatever"));
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "activity_not_running", result.getString("error"));
        }
    }

    @Test
    public void open_splitsTheNamedPaneOnTheAskedSideAtTheAskedSize() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        JSONObject result = dispatcher.execute("pane.open", new JSONObject()
            .put("from", usersShell.mHandle).put("direction", "left").put("size", 0.3)
            .put("focus", false));
        assertTrue(result.toString(), result.getBoolean("ok"));
        JSONObject pane = result.getJSONObject("pane");
        TerminalSession opened = host.findPaneById(pane.getString("id"));
        assertNotNull(opened);
        assertEquals(host.window.id, pane.getLong("window"));
        assertFalse(pane.getBoolean("floating"));
        assertTrue(pane.has("geometry"));
        assertTrue(result.isNull("layout"));

        TerminalPaneController.Split split = (TerminalPaneController.Split) host.window.root;
        assertEquals(LinearLayout.HORIZONTAL, split.orientation);
        assertSame(opened, ((TerminalPaneController.Leaf) split.a).session);
        assertEquals(0.3f, share(split, true), 1e-4f);
        assertSame(usersShell, host.controller.getActiveSession());
    }

    @Test
    public void open_downStacksBelowAndRowsAloneMeanDown() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        String below = dispatcher.execute("pane.open", new JSONObject()
            .put("from", usersShell.mHandle).put("direction", "down"))
            .getJSONObject("pane").getString("id");
        TerminalPaneController.Split split = (TerminalPaneController.Split) host.window.root;
        assertEquals(LinearLayout.VERTICAL, split.orientation);
        assertEquals(below, ((TerminalPaneController.Leaf) split.b).session.mHandle);
        assertEquals(0.5f, share(split, false), 1e-4f);

        // Rows and no direction: below the anchor. The anchor was never measured here, so the
        // cell count cannot become a share and the split stays even.
        JSONObject byRows = dispatcher.execute("pane.open", new JSONObject()
            .put("from", below).put("rows", 8));
        assertTrue(byRows.toString(), byRows.getBoolean("ok"));
        TerminalPaneController.Split inner = (TerminalPaneController.Split) split.b;
        assertEquals(LinearLayout.VERTICAL, inner.orientation);
    }

    @Test
    public void open_explicitPlacementTurnsARetainedLayoutOffButAnAnchorAloneDoesNot() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        assertTrue(host.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        JSONObject anchorOnly = dispatcher.execute("pane.open", new JSONObject()
            .put("from", usersShell.mHandle));
        assertTrue(anchorOnly.toString(), anchorOnly.getBoolean("ok"));
        assertEquals(TerminalPaneController.LAYOUT_GRID, anchorOnly.getString("layout"));

        JSONObject placed = dispatcher.execute("pane.open", new JSONObject()
            .put("from", usersShell.mHandle).put("direction", "right").put("size", "0.25"));
        assertTrue(placed.toString(), placed.getBoolean("ok"));
        assertTrue(placed.isNull("layout"));
        assertNull(host.controller.layoutPolicyOf(host.window));
    }

    @Test
    public void open_refusesBadPlacementWithoutOpeningAnything() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        JSONObject[] refusals = {
            dispatcher.execute("pane.open", new JSONObject().put("direction", "sideways")),
            dispatcher.execute("pane.open", new JSONObject().put("size", 0.95)),
            dispatcher.execute("pane.open", new JSONObject().put("size", "half")),
            dispatcher.execute("pane.open", new JSONObject().put("columns", 0)),
            dispatcher.execute("pane.open", new JSONObject().put("rows", 2.5)),
        };
        for (JSONObject refusal : refusals) {
            assertEquals(refusal.toString(), 400, refusal.getInt("_statusCode"));
            assertEquals("bad_request", refusal.getString("error"));
        }
        JSONObject unknown = dispatcher.execute("pane.open", new JSONObject()
            .put("from", "nope").put("direction", "right"));
        assertEquals(404, unknown.getInt("_statusCode"));
        assertEquals("pane_not_found", unknown.getString("error"));
        assertEquals(1, host.controller.shellsOf(host.window).size());
        assertEquals(0, AgentPaneRegistry.getInstance().size());

        // A floating pane shares no divider to split.
        String floated = dispatcher.execute("pane.open", new JSONObject())
            .getJSONObject("pane").getString("id");
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED, host.controller.toggleFloatActivePane());
        JSONObject notTiled = dispatcher.execute("pane.open", new JSONObject()
            .put("from", floated).put("direction", "down"));
        assertEquals(409, notTiled.getInt("_statusCode"));
        assertEquals("pane_not_tiled", notTiled.getString("error"));
        assertTrue(host.controller.isFloating(host.findPaneById(floated)));
        assertSame(usersShell, ((TerminalPaneController.Leaf) host.window.root).session);
    }

    @Test
    public void open_fromAPaneInAnotherWindowSplitsThatWindow() throws JSONException {
        TerminalSession behind = PaneHost.shell();
        TerminalPaneController.Window other = host.addWindow(behind);
        JSONObject result = dispatcher.execute("pane.open", new JSONObject()
            .put("from", behind.mHandle).put("direction", "right").put("focus", false));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(other.id, result.getJSONObject("pane").getLong("window"));
        assertEquals(2, host.controller.shellsOf(other).size());
        assertEquals(1, host.controller.shellsOf(host.window).size());
        assertSame(host.window, host.controller.activeWindow());
    }

    @Test
    public void list_reportsFeaturesWindowsAndWhichWindowsAreOwned() throws JSONException {
        TerminalSession agents = PaneHost.shell();
        TerminalPaneController.Window agentsWindow = host.addWindow(agents);
        AgentPaneRegistry.getInstance().register(agents.mHandle, "codex", Collections.<String>emptyList());

        JSONObject result = dispatcher.execute("pane.list", new JSONObject());
        assertTrue(result.toString(), result.getBoolean("ok"));
        List<String> features = new ArrayList<>();
        JSONArray array = result.getJSONArray("features");
        for (int i = 0; i < array.length(); i++) features.add(array.getString(i));
        assertTrue(features.toString(), features.containsAll(java.util.Arrays.asList(
            "placement", "resize", "geometry", "windows", "info", "wait", "print-id")));

        JSONArray windows = result.getJSONArray("windows");
        assertEquals(2, windows.length());
        assertFalse(windows.getJSONObject(0).getBoolean("owned"));
        assertTrue(windows.getJSONObject(1).getBoolean("owned"));
        assertEquals(agentsWindow.id, windows.getJSONObject(1).getLong("id"));
        JSONObject record = windows.getJSONObject(1).getJSONArray("panes").getJSONObject(0);
        assertEquals(agentsWindow.id, record.getLong("window"));
        assertFalse(record.getBoolean("floating"));
        assertTrue(record.has("geometry"));
    }

    @Test
    public void resize_onlyReachesOwnedPanesAndSetsTheirShare() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        String owned = dispatcher.execute("pane.open", new JSONObject()
            .put("from", usersShell.mHandle).put("direction", "right"))
            .getJSONObject("pane").getString("id");

        JSONObject refused = dispatcher.execute("pane.set_size", new JSONObject()
            .put("id", usersShell.mHandle).put("size", 0.7));
        assertEquals(403, refused.getInt("_statusCode"));
        assertEquals("not_owned", refused.getString("error"));

        JSONObject resized = dispatcher.execute("pane.set_size", new JSONObject()
            .put("id", owned).put("size", 0.7));
        assertTrue(resized.toString(), resized.getBoolean("ok"));
        assertEquals(owned, resized.getJSONObject("pane").getString("id"));
        TerminalPaneController.Split split = (TerminalPaneController.Split) host.window.root;
        assertEquals(0.7f, share(split, false), 1e-4f);

        assertEquals(400, dispatcher.execute("pane.set_size", new JSONObject().put("id", owned))
            .getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("pane.set_size", new JSONObject()
            .put("id", owned).put("size", 2)).getInt("_statusCode"));
        assertEquals(404, dispatcher.execute("pane.set_size", new JSONObject()
            .put("id", "nope").put("size", 0.5)).getInt("_statusCode"));

        // Alone in its window there is no divider to move.
        TerminalSession alone = PaneHost.shell();
        host.addWindow(alone);
        AgentPaneRegistry.getInstance().register(alone.mHandle, "", Collections.<String>emptyList());
        JSONObject noSplit = dispatcher.execute("pane.set_size", new JSONObject()
            .put("id", alone.mHandle).put("columns", 40));
        assertEquals(409, noSplit.getInt("_statusCode"));
        assertEquals("no_split", noSplit.getString("error"));
    }

    @Test
    public void windowFocusReachesAnyWindowAndCloseOnlyOwnedOnes() throws JSONException {
        TerminalSession usersOther = PaneHost.shell();
        TerminalPaneController.Window usersWindow = host.addWindow(usersOther);
        TerminalSession agents = PaneHost.shell();
        TerminalPaneController.Window agentsWindow = host.addWindow(agents);
        AgentPaneRegistry.getInstance().register(agents.mHandle, "", Collections.<String>emptyList());

        JSONObject focus = dispatcher.execute("window.focus", new JSONObject()
            .put("id", String.valueOf(usersWindow.id)));
        assertTrue(focus.toString(), focus.getBoolean("ok"));
        assertEquals(usersWindow.id, focus.getJSONObject("window").getLong("id"));
        assertTrue("on screen, the window strip switches", host.called("selectWindow"));

        // Stopped, the window comes up through its focused pane; a pane id names its window too.
        host.visible = false;
        JSONObject byPane = dispatcher.execute("window.focus", new JSONObject().put("id", agents.mHandle));
        assertTrue(byPane.toString(), byPane.getBoolean("ok"));
        assertSame(agentsWindow, host.controller.activeWindow());

        assertEquals(404, dispatcher.execute("window.focus", new JSONObject().put("id", "999999"))
            .getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("window.focus", new JSONObject()).getInt("_statusCode"));

        JSONObject refused = dispatcher.execute("window.close_by_id", new JSONObject()
            .put("id", String.valueOf(usersWindow.id)));
        assertEquals(403, refused.getInt("_statusCode"));
        assertEquals("not_owned", refused.getString("error"));

        JSONObject closed = dispatcher.execute("window.close_by_id", new JSONObject()
            .put("id", String.valueOf(agentsWindow.id)));
        assertTrue(closed.toString(), closed.getBoolean("closed"));
        assertFalse(AgentPaneRegistry.getInstance().isOwned(agents.mHandle));
    }

    /**
     * pane.info (GET /v1/panes/{id}, launcherctl pane current) reaches every pane of the current
     * session, the user's own included, since it only reads; it works with the host stopped.
     */
    @Test
    public void info_reportsAnyPaneOfTheCurrentSessionWithItsWindow() throws JSONException {
        TerminalSession usersShell = host.controller.getActiveSession();
        host.visible = false;
        JSONObject result = dispatcher.execute("pane.info", new JSONObject().put("id", usersShell.mHandle));
        assertTrue(result.toString(), result.getBoolean("ok"));
        JSONObject pane = result.getJSONObject("pane");
        assertEquals(usersShell.mHandle, pane.getString("id"));
        assertEquals(host.window.id, pane.getLong("window"));
        assertFalse(pane.getBoolean("floating"));
        assertTrue(pane.getBoolean("focused"));
        assertTrue(pane.has("geometry"));
        assertTrue(pane.isNull("agent"));
        JSONObject window = result.getJSONObject("window");
        assertEquals(host.window.id, window.getLong("id"));
        assertEquals(0, window.getInt("index"));
        assertTrue(window.getBoolean("current"));
        assertFalse(window.getBoolean("owned"));
        assertTrue(result.isNull("layout"));

        // A pane in a window behind the one on screen is still reported, with that window.
        TerminalSession behind = PaneHost.shell();
        TerminalPaneController.Window other = host.addWindow(behind);
        JSONObject back = dispatcher.execute("pane.info", new JSONObject().put("id", behind.mHandle));
        assertTrue(back.toString(), back.getBoolean("ok"));
        assertEquals(other.id, back.getJSONObject("window").getLong("id"));
        assertFalse(back.getJSONObject("window").getBoolean("current"));
        assertFalse(back.getJSONObject("pane").getBoolean("focused"));

        assertEquals(404, dispatcher.execute("pane.info", new JSONObject().put("id", "nope")).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("pane.info", new JSONObject()).getInt("_statusCode"));
    }

    /** One side's share of a split: the first child's, or the second's. */
    private static float share(@NonNull TerminalPaneController.Split split, boolean first) {
        float total = split.weightA + split.weightB;
        return (first ? split.weightA : split.weightB) / total;
    }

    @Test
    public void lastLines_keepsTheNewestLinesAndDropsTrailingBlanks() {
        assertEquals("c\nd", TerminalActionDispatcher.lastLines("a\nb\nc\nd\n\n", 2));
        assertEquals("a\nb", TerminalActionDispatcher.lastLines("a\nb", 10));
        assertEquals("", TerminalActionDispatcher.lastLines("\n\n   ", 3));
        assertEquals("d", TerminalActionDispatcher.lastLines("a\nb\nc\nd", 1));
    }

    /**
     * A host whose pane surface is a real controller with one window and one shell, on screen;
     * {@link #addWindow} adds more windows behind it.
     */
    private static final class PaneHost extends FakeTerminalHost {
        final TerminalPaneController controller;
        final TerminalPaneController.Window window;
        final List<TerminalPaneController.Window> windows = new ArrayList<>();
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
            windows.add(window);
        }

        /** A new window of the current session, holding {@code shell}, left behind the one on screen. */
        TerminalPaneController.Window addWindow(@NonNull TerminalSession shell) {
            TerminalPaneController.Window added = controller.newWindow(shell);
            windows.add(added);
            return added;
        }

        static TerminalSession shell() {
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
            return openCommandPane(command, cwd, title, focus, null);
        }

        @Override @Nullable public TerminalSession openCommandPane(
                @NonNull List<String> command, @Nullable String cwd, @Nullable String title,
                boolean focus, @Nullable TerminalPaneController.PlacementRequest placement) {
            if (refuseOpen) return null;
            lastCommand = new ArrayList<>(command);
            TerminalSession session = shell();
            session.mSessionName = title;
            boolean added = placement == null
                ? controller.addPane(session, focus)
                : controller.addPaneAt(session, placement, focus);
            return added ? session : null;
        }

        @Override @NonNull public List<TerminalPaneController.Window> currentSessionWindows() {
            return new ArrayList<>(windows);
        }

        @Override public int currentWindowIndex() {
            return windows.indexOf(controller.activeWindow());
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
            TerminalPaneController.Window holder = controller.windowOf(session);
            if (holder != null && holder != controller.activeWindow()) controller.showWindow(holder);
            controller.focusSession(session);
            return true;
        }

        @Override @Nullable public String activePaneLayoutPolicy() {
            return controller.activeLayoutPolicy();
        }
    }
}
