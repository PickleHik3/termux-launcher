package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** Window names: the policy caps, the tab label, and the workspace round trip. */
public class TerminalWindowNameTest {

    @Test
    public void policyCapsDifferPerTargetAndBlankClears() {
        assertEquals("abcdefgh", TerminalNamePolicy.normalizeSession("abcdefghij"));
        assertEquals("abcdefghijklmn", TerminalNamePolicy.normalizeWindow("abcdefghijklmnop"));
        assertEquals("build", TerminalNamePolicy.normalizeWindow("  build  "));
        assertNull(TerminalNamePolicy.normalizeWindow("   "));
        assertNull(TerminalNamePolicy.normalizePane(null));
        assertEquals(TerminalNamePolicy.WINDOW_MAX_CODE_POINTS,
            TerminalNamePolicy.maxCodePointsFor(TerminalRenameTarget.WINDOW));
        assertEquals(TerminalNamePolicy.SESSION_MAX_CODE_POINTS,
            TerminalNamePolicy.maxCodePointsFor(TerminalRenameTarget.SESSION));
        assertEquals(TerminalNamePolicy.PANE_MAX_CODE_POINTS,
            TerminalNamePolicy.maxCodePointsFor(TerminalRenameTarget.PANE));
    }

    @Test
    public void namedTabKeepsTheProcessGlyphAndShowsTheName() {
        TerminalWindowBar.WindowItem named = TerminalWindowBar.itemForNamed("build", "gradle");
        assertTrue(named.label.endsWith(" build"));
        assertTrue(named.spokenLabel.contains("build"));
        assertTrue(named.spokenLabel.contains("gradle"));
        // Same glyph the derived label would have used for that process, so the tab still says what
        // is running in it.
        TerminalWindowBar.WindowItem derived =
            TerminalWindowBar.itemForResolved("gradle", "x", "gradle");
        assertEquals(derived.label.charAt(0), named.label.charAt(0));
    }

    @Test
    public void workspaceRoundTripsTheWindowName() throws Exception {
        TerminalWorkspace.Pane pane =
            new TerminalWorkspace.Pane("/home", null, Collections.emptyList());
        TerminalWorkspace.Window window =
            new TerminalWorkspace.Window(0, pane, null, "build");
        TerminalWorkspace.Session session =
            new TerminalWorkspace.Session("work", 0, Collections.singletonList(window));
        TerminalWorkspace saved =
            new TerminalWorkspace("ws", 1L, 0, Collections.singletonList(session));
        saved.validate();

        JSONObject json = saved.toJson();
        assertEquals(3, json.optInt("version"));
        TerminalWorkspace loaded = TerminalWorkspace.fromJson(json);
        List<TerminalWorkspace.Window> windows = loaded.sessions.get(0).windows;
        assertEquals(1, windows.size());
        assertEquals("build", windows.get(0).name);
    }

    @Test
    public void olderWorkspaceFilesWithoutAWindowNameStillLoad() throws Exception {
        TerminalWorkspace.Pane pane =
            new TerminalWorkspace.Pane("/home", null, Collections.emptyList());
        TerminalWorkspace.Window window = new TerminalWorkspace.Window(0, pane);
        TerminalWorkspace.Session session =
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window));
        JSONObject json = new TerminalWorkspace("ws", 1L, 0,
            Collections.singletonList(session)).toJson();
        json.put("version", 1);
        json.getJSONArray("sessions").getJSONObject(0)
            .getJSONArray("windows").getJSONObject(0).remove("name");

        TerminalWorkspace loaded = TerminalWorkspace.fromJson(json);
        assertNotNull(loaded);
        assertNull(loaded.sessions.get(0).windows.get(0).name);
    }
}
