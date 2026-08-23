package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import android.widget.LinearLayout;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.terminal.TerminalSession;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The dispatcher against a {@link FakeTerminalHost}: which arguments it accepts, which host call
 * each tool routes to, and what it reports back when it cannot act.
 *
 * <p>The tools that need a live pane controller, terminal view or package manager are exercised on
 * their refusal path — those need a real surface and belong in an instrumentation test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalActionDispatcherTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();

    @After
    public void detach() {
        if (attachedHost != null) dispatcher.detach(attachedHost);
        attachedHost = null;
    }

    private FakeTerminalHost attachedHost;

    // --- Tool table ---

    @Test
    public void handles_coversEveryRegisteredTerminalTool() {
        String[] handled = {
            "terminal.state", "pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "pane.kill_focused", "window.new", "window.close", "window.next",
            "window.previous", "session.new", "session.next", "session.previous", "session.close_current",
            "session.browser", "session.clone_current",
            "terminal.toggle_soft_keyboard", "terminal.toggle_toolbar", "terminal.font_size_increase",
            "terminal.font_size_decrease", "terminal.select_url", "terminal.share_transcript",
            "clipboard.paste", "window.select", "window.rename", "session.rename",
            "session.rename_at_index", "terminal.reset",
            "appearance.set_wallpaper", "appearance.toggle_wallpaper", "appearance.surface_editor", "appearance.glass_lab",
            "app.command_palette", "app.open_drawer", "app.close_drawer", "terminal.action_sheet",
            "session.activate_by_index", "window.rename_prompt", "session.rename_prompt",
            "terminal.share_selected", "clipboard.copy_selected",
            "app.open_settings", "app.open_look_and_feel", "app.open_apps_bar",
            "workspace.save", "workspace.load", "workspace.list", "workspace.delete",
            "pane.layout", "pane.equalize", "pane.rotate", "pane.move_to_edge",
            "pane.next_layout", "pane.toggle_float"};
        for (String name : handled) {
            assertTrue(name, TerminalActionDispatcher.handles(name));
        }
    }

    @Test
    public void handles_rejectsOtherTools() {
        assertFalse(TerminalActionDispatcher.handles("apps.launch"));
        assertFalse(TerminalActionDispatcher.handles("memory.write"));
        assertFalse(TerminalActionDispatcher.handles("session.kill"));
        assertFalse(TerminalActionDispatcher.handles("appearance.reset_glass_lab_section"));
        assertFalse(TerminalActionDispatcher.handles(""));
        assertFalse(TerminalActionDispatcher.handles(null));
    }

    // --- Nothing attached ---

    @Test
    public void execute_withoutAttachedActivity_reportsConflict() throws Exception {
        assertFalse(dispatcher.isAttached());

        JSONObject result = dispatcher.execute("pane.split_vertical", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("activity_not_running", result.getString("error"));
        assertTrue(result.getString("message").contains("pane.split_vertical"));
    }

    @Test
    public void execute_everyTerminalTool_failsCleanlyWhenDetached() throws Exception {
        String[] tools = {
            "terminal.state", "pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "pane.kill_focused", "window.new", "window.close", "window.next",
            "window.previous", "session.new", "session.next", "session.previous", "session.close_current",
            "session.browser", "session.clone_current",
            "terminal.toggle_soft_keyboard", "terminal.toggle_toolbar", "terminal.font_size_increase",
            "terminal.font_size_decrease", "terminal.select_url", "terminal.share_transcript",
            "clipboard.paste", "window.select", "window.rename", "session.rename", "terminal.reset",
            "appearance.set_wallpaper", "appearance.toggle_wallpaper", "appearance.surface_editor", "appearance.glass_lab",
            "app.command_palette", "app.open_drawer", "app.close_drawer", "terminal.action_sheet",
            "session.activate_by_index", "window.rename_prompt", "session.rename_prompt",
            "terminal.share_selected", "clipboard.copy_selected",
            "app.open_settings", "app.open_look_and_feel", "app.open_apps_bar",
            "workspace.save", "workspace.load", "workspace.list", "workspace.delete",
            "pane.layout", "pane.equalize", "pane.rotate", "pane.move_to_edge",
            "pane.next_layout", "pane.toggle_float"};
        for (String name : tools) {
            JSONObject result = dispatcher.execute(name, new JSONObject());
            assertFalse(name, result.getBoolean("ok"));
            assertEquals(name, 409, result.getInt("_statusCode"));
        }
    }

    @Test
    public void execute_unknownTool_isNotImplemented() throws Exception {
        JSONObject result = dispatcher.execute("pane.teleport", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertEquals(501, result.getInt("_statusCode"));
        assertEquals("not_implemented", result.getString("error"));
    }

    @Test
    public void execute_neverThrowsForMissingArguments() throws Exception {
        // A caller that omits 'direction' must get an envelope, not an exception.
        JSONObject result = dispatcher.execute("pane.focus_direction", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertTrue(result.has("_statusCode"));
    }

    @Test
    public void aHostThatIsFinishingCountsAsDetached() throws Exception {
        FakeTerminalHost host = attach();
        host.alive = false;

        assertFalse(dispatcher.isAttached());
        assertEquals(409, dispatcher.execute("app.open_settings", new JSONObject()).getInt("_statusCode"));
        assertFalse(host.called("openSettings"));
    }

    @Test
    public void detachIgnoresAnOlderHostThanTheOneAttached() throws Exception {
        FakeTerminalHost replaced = host();
        FakeTerminalHost current = attach();

        dispatcher.detach(replaced);
        assertTrue(dispatcher.isAttached());
        assertTrue(dispatcher.execute("app.open_settings", new JSONObject()).getBoolean("ok"));
        assertTrue(current.called("openSettings"));
    }

    // --- Availability context ---

    @Test
    public void actionContextIsAllFalseWithNothingAttached() {
        assertFalse(dispatcher.actionContext().isSplitPanesEnabled());
        assertFalse(dispatcher.actionContext().hasCurrentSession());
        assertFalse(dispatcher.actionContext().hasSelectedText());
    }

    @Test
    public void actionContextReportsWhatTheHostSays() throws IOException {
        FakeTerminalHost host = attach();
        host.splitPanesEnabled = true;
        host.currentSession = session();

        assertTrue(dispatcher.actionContext().isSplitPanesEnabled());
        assertTrue(dispatcher.actionContext().hasCurrentSession());
        // No view, so no stored selection to report.
        assertFalse(dispatcher.actionContext().hasSelectedText());

        host.splitPanesEnabled = false;
        host.currentSession = null;
        assertFalse(dispatcher.actionContext().isSplitPanesEnabled());
        assertFalse(dispatcher.actionContext().hasCurrentSession());
    }

    // --- Plain routing ---

    @Test
    public void voidToolsRouteToTheirHostCall() throws Exception {
        String[][] routes = {
            {"app.open_settings", "openSettings"},
            {"app.open_look_and_feel", "openLookAndFeel"},
            {"app.open_apps_bar", "openAppsBar"},
            {"app.command_palette", "showCommandPalette"},
            {"app.open_drawer", "openDrawer"},
            {"app.close_drawer", "closeDrawers"},
            {"appearance.set_wallpaper", "openWallpaperPicker"},
            {"appearance.surface_editor", "openSurfaceEditor"},
            {"extrakeys.edit", "showExtraKeysRowEditor"},
            {"workspace.picker", "showWorkspacePicker"},
            {"workspace.save_prompt", "promptSaveWorkspace"},
            {"session.browser", "showSessionBrowser"},
            {"session.close_current", "closeCurrentSession"},
            {"terminal.toggle_toolbar", "toggleTerminalToolbar"},
            {"window.new", "createNewWindow"},
            {"window.close", "closeCurrentWindow"},
        };
        for (String[] route : routes) {
            FakeTerminalHost host = attach();
            JSONObject result = dispatcher.execute(route[0], new JSONObject());
            assertTrue(route[0], result.getBoolean("ok"));
            assertTrue(route[0], host.called(route[1]));
            // A tool that worked always says which one it was.
            assertEquals(route[0], route[0], host.lastActionHint);
            detach();
        }
    }

    @Test
    public void aRefusedToolRaisesNoActionHint() throws Exception {
        FakeTerminalHost host = attach();
        host.splitPanesEnabled = false;

        assertFalse(dispatcher.execute("window.new", new JSONObject()).getBoolean("ok"));
        assertNull(host.lastActionHint);
    }

    @Test
    public void windowNextAndPreviousCarryTheirDirection() throws Exception {
        FakeTerminalHost host = attach();

        assertTrue(dispatcher.execute("window.next", new JSONObject()).getBoolean("ok"));
        assertEquals(Boolean.TRUE, host.lastSwitchWindowForward);
        assertTrue(dispatcher.execute("window.previous", new JSONObject()).getBoolean("ok"));
        assertEquals(Boolean.FALSE, host.lastSwitchWindowForward);
    }

    @Test
    public void sessionPanelReportsWhetherThePanelIsNowOpen() throws Exception {
        FakeTerminalHost host = attach();

        assertTrue(dispatcher.execute("session.panel", new JSONObject()).getBoolean("panelOpen"));
        assertFalse(dispatcher.execute("session.panel", new JSONObject()).getBoolean("panelOpen"));
        assertTrue(host.called("toggleSessionsPanel"));
    }

    @Test
    public void appearanceTogglesReportTheStateTheyLandedIn() throws Exception {
        FakeTerminalHost host = attach();
        host.toggleWallpaperModeResult = false;
        host.toggleCursorTrailResult = true;

        assertFalse(dispatcher.execute("appearance.toggle_wallpaper", new JSONObject())
            .getBoolean("wallpaperEnabled"));
        assertTrue(dispatcher.execute("appearance.toggle_cursor_trail", new JSONObject())
            .getBoolean("cursorTrailEnabled"));
        assertTrue(host.called("toggleWallpaperMode"));
        assertTrue(host.called("toggleCursorTrail"));
    }

    @Test
    public void keyInspectorReportsWhetherItIsNowOpen() throws Exception {
        FakeTerminalHost host = attach();
        host.keyInspectorOpen = true;

        assertTrue(dispatcher.execute("app.key_inspector", new JSONObject())
            .getBoolean("keyInspectorOpen"));
        assertTrue(host.called("toggleKeyInspector"));
    }

    // --- Splits ---

    @Test
    public void splitToolsMapToTheirLayoutOrientation() throws Exception {
        FakeTerminalHost host = attach();

        JSONObject vertical = dispatcher.execute("pane.split_vertical", new JSONObject());
        assertEquals("vertical", vertical.getString("split"));
        assertEquals(Integer.valueOf(LinearLayout.HORIZONTAL), host.lastSplitOrientation);

        JSONObject horizontal = dispatcher.execute("pane.split_horizontal", new JSONObject());
        assertEquals("horizontal", horizontal.getString("split"));
        assertEquals(Integer.valueOf(LinearLayout.VERTICAL), host.lastSplitOrientation);
    }

    @Test
    public void everySplitOnlyToolRefusesWhileSplitsAreOff() throws Exception {
        FakeTerminalHost host = attach();
        host.splitPanesEnabled = false;
        // pane.resize is left out: it validates its direction before the splits check, and has a
        // test of its own for that order.
        String[] tools = {"pane.split_vertical", "pane.split_horizontal",
            "pane.layout", "pane.next_layout", "pane.equalize", "pane.rotate", "pane.move_to_edge",
            "pane.toggle_float", "terminal.toggle_scratchpad", "window.new", "window.close",
            "window.select", "window.rename", "session.rename", "window.rename_prompt",
            "session.rename_prompt", "workspace.picker", "workspace.save_prompt"};

        for (String tool : tools) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertFalse(tool, result.getBoolean("ok"));
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "splits_disabled", result.getString("error"));
        }
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void focusDirectionMapsEveryDirectionToItsKeyCode() throws Exception {
        FakeTerminalHost host = attach();
        int[][] cases = {
            {KeyEvent.KEYCODE_DPAD_LEFT, 0}, {KeyEvent.KEYCODE_DPAD_RIGHT, 1},
            {KeyEvent.KEYCODE_DPAD_UP, 2}, {KeyEvent.KEYCODE_DPAD_DOWN, 3}};
        String[] names = {"left", "right", "up", "down"};

        for (int i = 0; i < names.length; i++) {
            JSONObject result = dispatcher.execute("pane.focus_direction",
                new JSONObject().put("direction", names[i].toUpperCase()));
            assertTrue(names[i], result.getBoolean("handled"));
            assertEquals(names[i], Integer.valueOf(cases[i][0]), host.lastFocusKeyCode);
        }
    }

    @Test
    public void focusDirectionReportsWhatTheHostDidWithIt() throws Exception {
        FakeTerminalHost host = attach();
        host.focusPaneDirectionResult = false;

        JSONObject result = dispatcher.execute("pane.focus_direction",
            new JSONObject().put("direction", "left"));
        assertTrue(result.getBoolean("ok"));
        assertFalse(result.getBoolean("handled"));
    }

    @Test
    public void aMissingOrUnknownDirectionIsABadRequest() throws Exception {
        attach();
        for (JSONObject arguments : new JSONObject[]{new JSONObject(),
                new JSONObject().put("direction", "sideways")}) {
            for (String tool : new String[]{"pane.focus_direction", "pane.resize"}) {
                JSONObject result = dispatcher.execute(tool, arguments);
                assertFalse(tool, result.getBoolean("ok"));
                assertEquals(tool, 400, result.getInt("_statusCode"));
                assertEquals(tool, "bad_request", result.getString("error"));
            }
        }
    }

    @Test
    public void resizeIsCheckedForSplitsOnlyAfterItsDirection() throws Exception {
        FakeTerminalHost host = attach();
        host.splitPanesEnabled = false;

        // A bad direction outranks the splits check, so the caller hears about the argument first.
        assertEquals("bad_request", dispatcher.execute("pane.resize", new JSONObject())
            .getString("error"));
        assertEquals("splits_disabled", dispatcher.execute("pane.resize",
            new JSONObject().put("direction", "up")).getString("error"));

        host.splitPanesEnabled = true;
        assertTrue(dispatcher.execute("pane.resize", new JSONObject().put("direction", "down"))
            .getBoolean("handled"));
        assertEquals(Integer.valueOf(KeyEvent.KEYCODE_DPAD_DOWN), host.lastResizeKeyCode);
    }

    @Test
    public void killFocusedPaneReportsWhetherItKilledOne() throws Exception {
        FakeTerminalHost host = attach();
        host.killFocusedPaneResult = false;

        JSONObject result = dispatcher.execute("pane.kill_focused", new JSONObject());
        assertTrue(result.getBoolean("ok"));
        assertFalse(result.getBoolean("killed"));
        assertTrue(host.called("killFocusedPane"));
    }

    @Test
    public void paneLayoutAcceptsOnlyTheKnownLayouts() throws Exception {
        FakeTerminalHost host = attach();

        assertEquals(400, dispatcher.execute("pane.layout", new JSONObject()).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("pane.layout",
            new JSONObject().put("layout", "spiral")).getInt("_statusCode"));
        assertFalse(host.called("applyPaneLayout"));

        for (String layout : new String[]{TerminalPaneController.LAYOUT_STACK,
                TerminalPaneController.LAYOUT_GRID, TerminalPaneController.LAYOUT_TALL,
                TerminalPaneController.LAYOUT_FAT, TerminalPaneController.LAYOUT_HORIZONTAL,
                TerminalPaneController.LAYOUT_VERTICAL}) {
            JSONObject result = dispatcher.execute("pane.layout",
                new JSONObject().put("layout", layout));
            assertEquals(layout, layout, result.getString("layout"));
            assertEquals(layout, layout, host.lastLayout);
        }
    }

    @Test
    public void paneLayoutWithoutASessionIsAConflict() throws Exception {
        FakeTerminalHost host = attach();
        host.applyPaneLayoutResult = false;
        host.cyclePaneLayoutResult = false;
        host.equalizePaneLayoutResult = false;
        host.rotatePaneLayoutResult = false;

        for (String tool : new String[]{"pane.next_layout", "pane.equalize", "pane.rotate"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "no_session", result.getString("error"));
        }
        assertEquals(409, dispatcher.execute("pane.layout",
            new JSONObject().put("layout", TerminalPaneController.LAYOUT_GRID)).getInt("_statusCode"));
    }

    @Test
    public void nextLayoutReportsTheLayoutItLandedOn() throws Exception {
        FakeTerminalHost host = attach();
        host.layoutPolicy = TerminalPaneController.LAYOUT_TALL;

        JSONObject result = dispatcher.execute("pane.next_layout", new JSONObject());
        assertEquals(TerminalPaneController.LAYOUT_TALL, result.getString("layout"));
        assertTrue(host.called("cyclePaneLayout"));
    }

    @Test
    public void rotateDefaultsToClockwiseAndRejectsAnythingElse() throws Exception {
        FakeTerminalHost host = attach();

        assertEquals("clockwise",
            dispatcher.execute("pane.rotate", new JSONObject()).getString("direction"));
        assertEquals(Boolean.TRUE, host.lastRotateClockwise);

        assertEquals("counterclockwise", dispatcher.execute("pane.rotate",
            new JSONObject().put("direction", "counterclockwise")).getString("direction"));
        assertEquals(Boolean.FALSE, host.lastRotateClockwise);

        assertEquals(400, dispatcher.execute("pane.rotate",
            new JSONObject().put("direction", "widdershins")).getInt("_statusCode"));
    }

    @Test
    public void moveToEdgeAcceptsOnlyTheFourEdges() throws Exception {
        FakeTerminalHost host = attach();

        assertEquals(400, dispatcher.execute("pane.move_to_edge", new JSONObject()).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("pane.move_to_edge",
            new JSONObject().put("edge", "middle")).getInt("_statusCode"));

        for (String edge : new String[]{TerminalPaneController.EDGE_LEFT,
                TerminalPaneController.EDGE_RIGHT, TerminalPaneController.EDGE_UP,
                TerminalPaneController.EDGE_DOWN}) {
            assertEquals(edge, edge,
                dispatcher.execute("pane.move_to_edge", new JSONObject().put("edge", edge))
                    .getString("edge"));
            assertEquals(edge, edge, host.lastEdge);
        }

        host.moveFocusedPaneToEdgeResult = false;
        JSONObject refused = dispatcher.execute("pane.move_to_edge",
            new JSONObject().put("edge", TerminalPaneController.EDGE_LEFT));
        assertEquals(409, refused.getInt("_statusCode"));
        assertEquals("single_pane", refused.getString("error"));
    }

    @Test
    public void toolsThatNeedAPaneControllerReportNoSessionWithoutOne() throws Exception {
        attach();
        for (String tool : new String[]{"pane.toggle_float", "terminal.toggle_scratchpad"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "no_session", result.getString("error"));
        }
    }

    // --- Windows ---

    @Test
    public void windowSelectNeedsAnIndexThatExists() throws Exception {
        FakeTerminalHost host = attach();
        host.windowCount = 3;

        assertEquals(400, dispatcher.execute("window.select", new JSONObject()).getInt("_statusCode"));

        host.selectWindowResult = false;
        JSONObject missing = dispatcher.execute("window.select", new JSONObject().put("index", 7));
        assertEquals(400, missing.getInt("_statusCode"));
        assertTrue(missing.getString("message").contains("7"));
        assertTrue(missing.getString("message").contains("3"));

        host.selectWindowResult = true;
        assertEquals(1, dispatcher.execute("window.select", new JSONObject().put("index", 1))
            .getInt("index"));
        assertEquals(Integer.valueOf(1), host.lastSelectedWindow);
    }

    @Test
    public void windowRenameTakesAnEmptyNameButNotAnAbsentOne() throws Exception {
        FakeTerminalHost host = attach();
        host.windowName = "build";

        assertEquals(400, dispatcher.execute("window.rename", new JSONObject()).getInt("_statusCode"));

        JSONObject result = dispatcher.execute("window.rename", new JSONObject().put("name", ""));
        // Reported back is what the host stored, not what was asked for.
        assertEquals("build", result.getString("name"));
        assertEquals("", host.lastRenamedWindowName);

        host.windowName = null;
        assertEquals(JSONObject.NULL, dispatcher.execute("window.rename",
            new JSONObject().put("name", "logs")).get("name"));
        assertEquals("logs", host.lastRenamedWindowName);

        host.renameCurrentWindowToResult = false;
        JSONObject refused = dispatcher.execute("window.rename", new JSONObject().put("name", "x"));
        assertEquals(409, refused.getInt("_statusCode"));
        assertEquals("no_window", refused.getString("error"));
    }

    @Test
    public void windowRenamePromptReportsWhenThereIsNoWindow() throws Exception {
        FakeTerminalHost host = attach();
        host.promptCurrentWindowRenameResult = false;

        JSONObject result = dispatcher.execute("window.rename_prompt", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("no_window", result.getString("error"));

        host.promptCurrentWindowRenameResult = true;
        assertTrue(dispatcher.execute("window.rename_prompt", new JSONObject()).getBoolean("ok"));
    }

    // --- Sessions ---

    @Test
    public void sessionRenameTakesAnEmptyNameButNotAnAbsentOne() throws Exception {
        FakeTerminalHost host = attach();
        host.sessionName = "kept";

        assertEquals(400, dispatcher.execute("session.rename", new JSONObject()).getInt("_statusCode"));
        assertEquals("kept", dispatcher.execute("session.rename", new JSONObject().put("name", ""))
            .getString("name"));
        assertEquals("", host.lastRenamedSessionName);

        host.renameCurrentSessionToResult = false;
        JSONObject refused = dispatcher.execute("session.rename", new JSONObject().put("name", "x"));
        assertEquals(409, refused.getInt("_statusCode"));
        assertEquals("no_session", refused.getString("error"));
    }

    @Test
    public void sessionRenameAtIndexNeedsBothAnIndexAndAName() throws Exception {
        FakeTerminalHost host = attach();
        host.browserSessionName = "stored";

        assertEquals(400, dispatcher.execute("session.rename_at_index",
            new JSONObject().put("name", "a")).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("session.rename_at_index",
            new JSONObject().put("index", 0)).getInt("_statusCode"));

        JSONObject result = dispatcher.execute("session.rename_at_index",
            new JSONObject().put("index", 2).put("name", "asked"));
        assertEquals(2, result.getInt("index"));
        assertEquals("stored", result.getString("name"));
        assertEquals(2, host.lastRenamedBrowserIndex);
        assertEquals("asked", host.lastRenamedBrowserName);

        host.renameBrowserSessionResult = false;
        JSONObject missing = dispatcher.execute("session.rename_at_index",
            new JSONObject().put("index", 9).put("name", "x"));
        assertEquals(400, missing.getInt("_statusCode"));
        assertTrue(missing.getString("message").contains("9"));
    }

    @Test
    public void sessionToolsThatNeedTheSessionClientSayWhenItIsMissing() throws Exception {
        attach();
        for (String tool : new String[]{"session.new", "session.next", "session.previous",
                "session.activate_by_index", "pane.rename_prompt", "pane.rename"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 503, result.getInt("_statusCode"));
            assertEquals(tool, "unavailable", result.getString("error"));
        }
    }

    @Test
    public void activateByIndexIsBoundedByTheDrawerList() throws Exception {
        FakeTerminalHost host = attach();
        host.sessionClient = new TermuxTerminalSessionActivityClient(context(), host);
        TerminalSession first = session();
        TerminalSession second = session();
        host.sessions.rows.add(first);
        host.sessions.rows.add(second);

        assertEquals(400, dispatcher.execute("session.activate_by_index", new JSONObject())
            .getInt("_statusCode"));

        JSONObject outOfRange = dispatcher.execute("session.activate_by_index",
            new JSONObject().put("index", 5));
        assertEquals(400, outOfRange.getInt("_statusCode"));
        assertTrue(outOfRange.getString("message").contains("there are 2"));

        JSONObject result = dispatcher.execute("session.activate_by_index",
            new JSONObject().put("index", 1));
        assertEquals(1, result.getInt("index"));
        // The session client took it from there and switched the panes over.
        assertTrue(host.called("activateSessionInPanes"));
        assertTrue(host.called("updateWindowBackgroundForCurrentSession"));
    }

    @Test
    public void sessionNextAndPreviousWalkTheDrawerListThroughTheSessionClient() throws Exception {
        FakeTerminalHost host = attach();
        host.sessionClient = new TermuxTerminalSessionActivityClient(context(), host);
        TerminalSession only = session();
        host.sessions.rows.add(only);

        assertTrue(dispatcher.execute("session.next", new JSONObject()).getBoolean("ok"));
        assertTrue(host.called("activateSessionInPanes"));
        assertTrue(dispatcher.execute("session.previous", new JSONObject()).getBoolean("ok"));
    }

    @Test
    public void paneRenameGoesThroughTheSessionClientAndNeedsAName() throws Exception {
        FakeTerminalHost host = attach();
        host.sessionClient = new TermuxTerminalSessionActivityClient(context(), host);

        assertEquals(400, dispatcher.execute("pane.rename", new JSONObject()).getInt("_statusCode"));

        // No focused shell, so the client refuses and the dispatcher calls that a missing session.
        JSONObject noSession = dispatcher.execute("pane.rename", new JSONObject().put("name", "logs"));
        assertEquals(409, noSession.getInt("_statusCode"));
        assertEquals("no_session", noSession.getString("error"));

        host.currentSession = session();
        JSONObject result = dispatcher.execute("pane.rename", new JSONObject().put("name", "logs"));
        assertEquals("logs", result.getString("name"));
        assertEquals("logs", host.currentSession.mSessionName);

        JSONObject cleared = dispatcher.execute("pane.rename", new JSONObject().put("name", ""));
        assertEquals(JSONObject.NULL, cleared.get("name"));
        assertNull(host.currentSession.mSessionName);
    }

    @Test
    public void sessionCloneAndResetReportARefusalAsNoSession() throws Exception {
        FakeTerminalHost host = attach();
        host.cloneCurrentBrowserSessionResult = false;
        host.resetCurrentSessionResult = false;

        for (String tool : new String[]{"session.clone_current", "terminal.reset"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "no_session", result.getString("error"));
        }

        host.cloneCurrentBrowserSessionResult = true;
        host.resetCurrentSessionResult = true;
        assertTrue(dispatcher.execute("session.clone_current", new JSONObject()).getBoolean("cloned"));
        assertTrue(dispatcher.execute("terminal.reset", new JSONObject()).getBoolean("ok"));
    }

    @Test
    public void sessionRenamePromptReportsWhenThereIsNoSession() throws Exception {
        FakeTerminalHost host = attach();
        host.promptCurrentSessionRenameResult = false;

        JSONObject result = dispatcher.execute("session.rename_prompt", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("no_session", result.getString("error"));
    }

    // --- Workspaces ---

    @Test
    public void workspaceSaveForwardsItsFlagsAndSummarisesWhatItWrote() throws Exception {
        FakeTerminalHost host = attach();

        assertEquals(400, dispatcher.execute("workspace.save", new JSONObject()).getInt("_statusCode"));

        JSONObject result = dispatcher.execute("workspace.save", new JSONObject()
            .put("name", "morning").put("overwrite", true).put("captureCommands", true));
        assertEquals("morning", result.getString("name"));
        assertEquals(0, result.getInt("sessions"));
        assertEquals(0, result.getInt("panes"));
        assertEquals(0, result.getInt("commandsCaptured"));
        assertEquals("morning", host.lastSavedWorkspaceName);
        assertTrue(host.lastSaveOverwrite);
        assertTrue(host.lastSaveCaptureCommands);
    }

    @Test
    public void workspaceLoadValidatesItsModeAndReportsTheCounts() throws Exception {
        FakeTerminalHost host = attach();

        assertEquals(400, dispatcher.execute("workspace.load", new JSONObject()).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("workspace.load",
            new JSONObject().put("name", "a").put("mode", "merge")).getInt("_statusCode"));

        JSONObject appended = dispatcher.execute("workspace.load",
            new JSONObject().put("name", "  morning  "));
        assertEquals("morning", appended.getString("name"));
        assertEquals("append", appended.getString("mode"));
        assertEquals(1, appended.getInt("sessions"));
        assertEquals(2, appended.getInt("windows"));
        assertEquals(3, appended.getInt("panes"));
        assertEquals(4, appended.getInt("commandsRun"));
        assertEquals(5, appended.getInt("commandsSkipped"));
        assertFalse(host.lastLoadReplace);
        assertFalse(host.lastLoadRunCommands);

        JSONObject replaced = dispatcher.execute("workspace.load", new JSONObject()
            .put("name", "morning").put("mode", "replace").put("runCommands", true));
        assertEquals("replace", replaced.getString("mode"));
        assertTrue(host.lastLoadReplace);
        assertTrue(host.lastLoadRunCommands);
    }

    @Test
    public void workspaceListAndDeleteGoThroughTheHost() throws Exception {
        FakeTerminalHost host = attach();

        JSONObject listed = dispatcher.execute("workspace.list", new JSONObject());
        assertEquals(0, listed.getInt("count"));
        assertEquals(0, listed.getJSONArray("workspaces").length());

        assertEquals(400, dispatcher.execute("workspace.delete", new JSONObject()).getInt("_statusCode"));

        JSONObject deleted = dispatcher.execute("workspace.delete",
            new JSONObject().put("name", " morning "));
        assertTrue(deleted.getBoolean("deleted"));
        // The name is validated (and trimmed) before it reaches the host.
        assertEquals("morning", deleted.getString("name"));
        assertEquals("morning", host.lastDeletedWorkspaceName);

        assertEquals(400, dispatcher.execute("workspace.delete",
            new JSONObject().put("name", "")).getInt("_statusCode"));
    }

    @Test
    public void aWorkspaceFailureKeepsItsOwnCodeAndStatus() throws Exception {
        FakeTerminalHost host = attach();
        host.workspaceFailure = new TerminalWorkspace.WorkspaceException("not_found", "No such thing");

        JSONObject result = dispatcher.execute("workspace.load", new JSONObject().put("name", "a"));
        assertFalse(result.getBoolean("ok"));
        assertEquals(404, result.getInt("_statusCode"));
        assertEquals("not_found", result.getString("error"));
        assertEquals("No such thing", result.getString("message"));

        host.workspaceFailure = new TerminalWorkspace.WorkspaceException("too_many_panes", "Too many");
        assertEquals(409, dispatcher.execute("workspace.load", new JSONObject().put("name", "a"))
            .getInt("_statusCode"));

        host.workspaceFailure = new TerminalWorkspace.WorkspaceException("mystery", "Unknown");
        assertEquals(500, dispatcher.execute("workspace.list", new JSONObject())
            .getInt("_statusCode"));
    }

    // --- Selection, view client and state ---

    @Test
    public void selectionToolsReportAnEmptySelection() throws Exception {
        attach();
        for (String tool : new String[]{"terminal.share_selected", "clipboard.copy_selected"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 409, result.getInt("_statusCode"));
            assertEquals(tool, "no_selection", result.getString("error"));
        }
    }

    @Test
    public void selectionToolsNeedATerminalView() throws Exception {
        FakeTerminalHost host = attach();
        host.currentSession = session();

        for (String tool : new String[]{"terminal.select_all", "terminal.select_at_cursor"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 503, result.getInt("_statusCode"));
            assertEquals(tool, "unavailable", result.getString("error"));
        }
    }

    @Test
    public void viewClientToolsNeedAViewClient() throws Exception {
        attach();
        for (String tool : new String[]{"terminal.toggle_soft_keyboard",
                "terminal.font_size_increase", "terminal.font_size_decrease",
                "terminal.select_url", "terminal.hints", "terminal.search_scrollback",
                "terminal.share_transcript", "clipboard.paste"}) {
            JSONObject result = dispatcher.execute(tool, new JSONObject());
            assertEquals(tool, 503, result.getInt("_statusCode"));
            assertEquals(tool, "unavailable", result.getString("error"));
        }
    }

    @Test
    public void viewClientToolsNeedASessionOnceTheClientIsThere() throws Exception {
        FakeTerminalHost host = attach();
        host.viewClient = new TermuxTerminalViewClient(context(), host, null);

        JSONObject result = dispatcher.execute("terminal.font_size_increase", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("no_session", result.getString("error"));
    }

    @Test
    public void fontSizeToolsStepTheFocusedPaneThroughTheViewClient() throws Exception {
        FakeTerminalHost host = attach();
        host.viewClient = new TermuxTerminalViewClient(context(), host, null);
        host.currentSession = session();
        int base = host.preferences.getFontSize();

        assertTrue(dispatcher.execute("terminal.font_size_increase", new JSONObject())
            .getBoolean("ok"));
        assertEquals(host.preferences.stepFontSize(base, true), host.paneFontSize);

        assertTrue(dispatcher.execute("terminal.font_size_decrease", new JSONObject())
            .getBoolean("ok"));
        assertEquals(base, host.paneFontSize);
    }

    @Test
    public void actionSheetReportsWhenThereIsNothingToShowItFor() throws Exception {
        FakeTerminalHost host = attach();
        host.showTerminalActionSheetResult = false;

        JSONObject result = dispatcher.execute("terminal.action_sheet", new JSONObject());
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("no_session", result.getString("error"));

        host.showTerminalActionSheetResult = true;
        assertTrue(dispatcher.execute("terminal.action_sheet", new JSONObject()).getBoolean("ok"));
        assertNull(host.lastActionSheetAnchor);
    }

    @Test
    public void appLaunchNeedsAQuery() throws Exception {
        attach();
        JSONObject result = dispatcher.execute("app.launch", new JSONObject().put("query", "  "));
        assertEquals(400, result.getInt("_statusCode"));
        assertEquals("bad_request", result.getString("error"));
    }

    @Test
    public void fontInstallNeedsAFamilyId() throws Exception {
        attach();
        JSONObject result = dispatcher.execute("fonts.install", new JSONObject());
        assertEquals(400, result.getInt("_statusCode"));
        assertEquals("bad_request", result.getString("error"));
    }

    @Test
    public void terminalStateDescribesTheHost() throws Exception {
        FakeTerminalHost host = attach();
        host.currentSession = session();
        host.sessions.rows.add(host.currentSession);
        host.windowCount = 4;
        host.windowIndex = 2;
        host.layoutPolicy = TerminalPaneController.LAYOUT_FAT;
        host.sessionName = "work";
        host.wallpaperEnabled = true;
        host.cursorTrailEnabled = true;

        JSONObject state = dispatcher.execute("terminal.state", new JSONObject());
        assertTrue(state.getBoolean("ok"));
        assertTrue(state.getBoolean("splitPanesEnabled"));
        assertEquals(1, state.getInt("drawerSessions"));
        assertTrue(state.getBoolean("hasCurrentSession"));
        assertEquals(0, state.getInt("visiblePanes"));
        assertEquals(0, state.getInt("floatingPanes"));
        assertFalse(state.getBoolean("focusedPaneFloating"));
        assertEquals(4, state.getInt("windows"));
        assertEquals(2, state.getInt("currentWindow"));
        assertEquals(TerminalPaneController.LAYOUT_FAT, state.getString("paneLayout"));
        assertEquals("work", state.getString("windowSessionName"));
        assertTrue(state.getBoolean("wallpaperEnabled"));
        assertTrue(state.getBoolean("cursorTrailEnabled"));
        assertNotNull(state.getJSONObject("performance").getJSONObject("windowFrames"));
        assertEquals(0, state.getJSONObject("performance").getJSONArray("terminalPanes").length());
        assertFalse(host.called("resetTerminalPerformanceMetrics"));
    }

    @Test
    public void terminalStateOmitsALayoutThatIsNotRetainedAndCanResetTheMetrics() throws Exception {
        FakeTerminalHost host = attach();
        host.layoutPolicy = null;
        host.sessionName = null;

        JSONObject state = dispatcher.execute("terminal.state",
            new JSONObject().put("resetPerformance", true));
        assertFalse(state.has("paneLayout"));
        assertFalse(state.has("windowSessionName"));
        assertTrue(host.called("resetTerminalPerformanceMetrics"));
    }

    // --- Fixtures ---

    private FakeTerminalHost attach() throws IOException {
        FakeTerminalHost host = host();
        attachedHost = host;
        dispatcher.attach(host);
        return host;
    }

    private static FakeTerminalHost host() throws IOException {
        return new FakeTerminalHost(context(), properties());
    }

    private static TerminalSession session() {
        // Inert: nothing is started until the emulator is initialized, which no test here does.
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], null, null);
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static TermuxSharedProperties properties(String... lines) throws IOException {
        File file = File.createTempFile("termux-dispatcher", ".properties");
        Files.write(file.toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return new TermuxSharedProperties(context(), "test",
            Collections.singletonList(file.getAbsolutePath()),
            TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST,
            new TermuxSharedProperties.SharedPropertiesParserClient()) {
        };
    }
}
