package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeyIcons;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ExtraKeyIconRegistryTest {

    @Test public void everyIconCodePointHasExactlyOneDrawable() {
        Set<Integer> registered = new HashSet<>();
        for (int[] row : ExtraKeyActionLabels.ICON_DRAWABLES) {
            assertTrue("registered twice: U+" + Integer.toHexString(row[0]), registered.add(row[0]));
            assertTrue(row[1] != 0);
        }
        for (int codePoint : ExtraKeyIcons.ALL) {
            assertTrue("no drawable for U+" + Integer.toHexString(codePoint),
                registered.contains(codePoint));
        }
        assertEquals(ExtraKeyIcons.ALL.length, registered.size());
    }

    @Test public void toolsWithAnIconWearItAndOthersKeepTheirTitle() {
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.HOME),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.SPLIT),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_SPLIT));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.COMMAND),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE));
        assertNotNull(ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_VOICE_DICTATE));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PANE_MOVE_EDGE),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_MOVE_TO_EDGE));
    }

    @Test public void launcherActionsWearTheirIconAndArgumentRowsKeepText() {
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.DOCK),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_TOOLBAR));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.EDIT),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_RENAME_PROMPT));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.KEYBOARD),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_KEYBOARD_SHOW));
        // A row that picks one value is told apart by it, except the wall's places.
        assertNull(ExtraKeyActionLabels.defaultCapGlyph(
            LauncherToolRegistry.TOOL_PANE_LAYOUT, "grid"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.LAYOUT),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_LAYOUT, null));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.DISPLAY),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_WALL_GO, "display"));
        assertNull(ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_WALL_GO, "left"));
    }

    @Test public void theFiveActionsAndEachFocusDirectionWearTheirIcon() {
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PROMPT_PREVIOUS),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PROMPT_NEXT),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_TERMINAL_JUMP_NEXT_PROMPT));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PANE_FOCUS),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PANE_RESIZE),
            ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_RESIZE));
        String focus = LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION;
        String[] directions = {"left", "right", "up", "down"};
        int[] icons = {ExtraKeyIcons.PANE_FOCUS_LEFT, ExtraKeyIcons.PANE_FOCUS_RIGHT,
            ExtraKeyIcons.PANE_FOCUS_UP, ExtraKeyIcons.PANE_FOCUS_DOWN};
        Set<String> caps = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String cap = ExtraKeyActionLabels.defaultCapGlyph(focus, directions[i]);
            assertEquals(ExtraKeyIcons.string(icons[i]), cap);
            assertTrue(caps.add(cap));
        }
    }

    @Test public void resizeAndMoveToEdgeDirectionsEachWearTheirOwnIcon() {
        String[] directions = {"left", "right", "up", "down"};
        int[] resize = {ExtraKeyIcons.PANE_RESIZE_LEFT, ExtraKeyIcons.PANE_RESIZE_RIGHT,
            ExtraKeyIcons.PANE_RESIZE_UP, ExtraKeyIcons.PANE_RESIZE_DOWN};
        int[] edge = {ExtraKeyIcons.PANE_MOVE_EDGE_LEFT, ExtraKeyIcons.PANE_MOVE_EDGE_RIGHT,
            ExtraKeyIcons.PANE_MOVE_EDGE_UP, ExtraKeyIcons.PANE_MOVE_EDGE_DOWN};
        Set<String> caps = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String r = ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_RESIZE, directions[i]);
            String e = ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_MOVE_TO_EDGE, directions[i]);
            assertEquals(ExtraKeyIcons.string(resize[i]), r);
            assertEquals(ExtraKeyIcons.string(edge[i]), e);
            assertTrue(caps.add(r));
            assertTrue(caps.add(e));
        }
        assertNull(ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_PANE_RESIZE, "grow"));
    }
}
