package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The window routes under Terl (terl-integration-1): {@code window list} (pane.list) lists the
 * stack's windows by their ids, and {@code window focus} takes such an id, or the id of the pane in
 * it, and focuses that window of the stack.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalActionDispatcherTerlWindowsTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
    private FakeTerminalHost host;
    private final List<Long> focused = new ArrayList<>();
    /** The stack's windows as the fake desktop holds them; close takes one out. */
    private final List<TerlWindows.Window> stack = new ArrayList<>();
    /** Whether the fake desktop's close takes the window out. */
    private boolean closes = true;
    private TerminalSession first;
    private TerminalSession second;

    @Before public void attach() throws java.io.IOException {
        AgentPaneRegistry.getInstance().clear();
        host = new FakeTerminalHost(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties());
        host.terlOn = true;
        first = new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
        second = new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
        stack.clear();
        stack.add(new TerlWindows.Window(1, "Workspace 1", "fish", "shell", true, first));
        stack.add(new TerlWindows.Window(4, "Workspace 2", "htop", "shell", false, second));
        stack.add(new TerlWindows.Window(7, "Workspace 2", "Firefox", "linux_app", false, null));
        host.terlWindows = new TerlWindows() {
            @NonNull @Override public List<Window> windows() {
                return new ArrayList<>(stack);
            }
            @Override public boolean focus(long id) {
                focused.add(id);
                return true;
            }
            @Override public boolean close(long id) {
                if (!closes) return false;
                return stack.removeIf(window -> window.id == id);
            }
        };
        dispatcher.attach(host);
    }

    @After public void detach() {
        dispatcher.detach(host);
        AgentPaneRegistry.getInstance().clear();
    }

    @Test public void windowListListsTheStacksWindowsByTheirIds() throws JSONException {
        JSONObject result = dispatcher.execute("pane.list", new JSONObject());

        assertTrue(result.toString(), result.getBoolean("ok"));
        JSONArray windows = result.getJSONArray("windows");
        assertEquals(3, windows.length());
        assertEquals(4, windows.getJSONObject(1).getLong("id"));
        assertEquals("Workspace 2", windows.getJSONObject(1).getString("place"));
        assertEquals(second.mHandle, windows.getJSONObject(1).getString("focusedPane"));
        assertEquals(4, windows.getJSONObject(1).getJSONArray("panes").getJSONObject(0).getLong("window"));
        assertEquals(0, windows.getJSONObject(2).getJSONArray("panes").length());
        assertEquals(first.mHandle, result.getString("activePane"));
    }

    @Test public void windowFocusTakesAStackWindowId() throws JSONException {
        JSONObject result = dispatcher.execute("window.focus", new JSONObject().put("id", "4"));

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(Arrays.asList(4L), focused);
        assertEquals(4, result.getJSONObject("window").getLong("id"));
    }

    @Test public void windowFocusTakesThePaneIdInIt() throws JSONException {
        JSONObject result = dispatcher.execute("window.focus", new JSONObject().put("id", second.mHandle));

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(Arrays.asList(4L), focused);
    }

    @Test public void anUnknownWindowIsNotFound() throws JSONException {
        assertEquals(404, dispatcher.execute("window.focus", new JSONObject().put("id", "99")).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("window.focus", new JSONObject()).getInt("_statusCode"));
        assertTrue(focused.isEmpty());
    }

    @Test public void windowCloseClosesTheStacksWindowByItsId() throws JSONException {
        // Round 2: launcherctl window close under Terl found no window for the stack's id.
        AgentPaneRegistry.getInstance().register(second.mHandle, "", java.util.Collections.<String>emptyList());

        JSONObject result = dispatcher.execute("window.close_by_id", new JSONObject().put("id", "4"));

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(4, result.getLong("id"));
        assertTrue(result.getBoolean("closed"));
        for (TerlWindows.Window window : host.terlWindows.windows()) {
            assertFalse("the window has left the stack", window.id == 4);
        }
        assertFalse(AgentPaneRegistry.getInstance().isOwned(second.mHandle));
    }

    @Test public void windowCloseSaysClosedOnlyOnceTheWindowHasGone() throws JSONException {
        // Review of round 2: a shell with no process yet was not ended, yet closed was answered and
        // the window was no longer owned, so a retry was refused while it stayed.
        AgentPaneRegistry.getInstance().register(second.mHandle, "", java.util.Collections.<String>emptyList());
        closes = false;

        JSONObject result = dispatcher.execute("window.close_by_id", new JSONObject().put("id", "4"));

        assertFalse(result.toString(), result.getBoolean("ok"));
        assertEquals(3, host.terlWindows.windows().size());
        assertTrue("still owned: a retry may close it", AgentPaneRegistry.getInstance().isOwned(second.mHandle));
        closes = true;
        assertTrue(dispatcher.execute("window.close_by_id", new JSONObject().put("id", "4")).getBoolean("ok"));
        assertEquals(2, host.terlWindows.windows().size());
    }

    @Test public void windowCloseRefusesAWindowItDidNotOpen() throws JSONException {
        JSONObject mine = dispatcher.execute("window.close_by_id", new JSONObject().put("id", "1"));
        assertFalse(mine.getBoolean("ok"));
        assertEquals(403, mine.getInt("_statusCode"));

        JSONObject none = dispatcher.execute("window.close_by_id", new JSONObject().put("id", "99"));
        assertFalse(none.getBoolean("ok"));
        assertEquals(404, none.getInt("_statusCode"));
    }
}
