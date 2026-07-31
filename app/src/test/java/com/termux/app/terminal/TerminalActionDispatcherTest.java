package com.termux.app.terminal;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the dispatcher's detached behavior and tool routing. Executing against a
 * live {@code TermuxActivity} needs the service, bootstrap, and a real surface, so
 * that path belongs in an instrumentation test rather than here.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalActionDispatcherTest {

    @Test
    public void handles_coversEveryRegisteredTerminalTool() {
        String[] handled = {
            "terminal.state", "pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "pane.kill_focused", "window.new", "window.close", "window.next",
            "window.previous", "session.new", "session.next", "session.previous", "session.close_current",
            "session.browser", "session.clone_current",
            "terminal.toggle_soft_keyboard", "terminal.toggle_toolbar", "terminal.font_size_increase",
            "terminal.font_size_decrease", "terminal.select_url", "terminal.share_transcript",
            "clipboard.paste", "window.select", "window.rename", "session.rename", "terminal.reset",
            "appearance.set_wallpaper", "appearance.toggle_wallpaper", "appearance.glass_lab",
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

    @Test
    public void execute_withoutAttachedActivity_reportsConflict() throws Exception {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        assertFalse(dispatcher.isAttached());

        JSONObject result = dispatcher.execute("pane.split_vertical", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("activity_not_running", result.getString("error"));
        assertTrue(result.getString("message").contains("pane.split_vertical"));
    }

    @Test
    public void execute_everyTerminalTool_failsCleanlyWhenDetached() throws Exception {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        String[] tools = {
            "terminal.state", "pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "pane.kill_focused", "window.new", "window.close", "window.next",
            "window.previous", "session.new", "session.next", "session.previous", "session.close_current",
            "session.browser", "session.clone_current",
            "terminal.toggle_soft_keyboard", "terminal.toggle_toolbar", "terminal.font_size_increase",
            "terminal.font_size_decrease", "terminal.select_url", "terminal.share_transcript",
            "clipboard.paste", "window.select", "window.rename", "session.rename", "terminal.reset",
            "appearance.set_wallpaper", "appearance.toggle_wallpaper", "appearance.glass_lab",
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
        JSONObject result = TerminalActionDispatcher.getInstance().execute("pane.teleport", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertEquals(501, result.getInt("_statusCode"));
        assertEquals("not_implemented", result.getString("error"));
    }

    @Test
    public void execute_neverThrowsForMissingArguments() throws Exception {
        // A caller that omits 'direction' must get an envelope, not an exception.
        JSONObject result = TerminalActionDispatcher.getInstance()
            .execute("pane.focus_direction", new JSONObject());
        assertFalse(result.getBoolean("ok"));
        assertTrue(result.has("_statusCode"));
    }
}
