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
        assertNull(ExtraKeyActionLabels.defaultCapGlyph(LauncherToolRegistry.TOOL_TERMINAL_RESET));
    }
}
