package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.font.NerdFontSpans;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ExtraKeyIconsTest {

    @Test public void codePointsAreUniqueAndOutsideTheNerdRanges() {
        Set<Integer> seen = new HashSet<>();
        for (int codePoint : ExtraKeyIcons.ALL) {
            assertTrue("duplicate U+" + Integer.toHexString(codePoint), seen.add(codePoint));
            assertFalse("inside a Nerd range: U+" + Integer.toHexString(codePoint),
                NerdFontSpans.isNerdSymbol(codePoint));
            assertTrue("not plane 16 private use: U+" + Integer.toHexString(codePoint),
                codePoint >= 0x100000 && codePoint <= 0x10FFFD);
        }
    }

    @Test public void aLabelIsOneCodePoint() {
        for (int codePoint : ExtraKeyIcons.ALL) {
            String label = ExtraKeyIcons.string(codePoint);
            assertEquals(1, label.codePointCount(0, label.length()));
            assertEquals(codePoint, label.codePointAt(0));
            assertTrue(ExtraKeyIcons.isIcon(codePoint));
        }
    }

    @Test public void theDefaultMapsDrawArrowsAndKeySymbolsAsIcons() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.ARROW_LEFT), map.get("LEFT"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.ENTER), map.get("ENTER"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.TAB), map.get("TAB"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.BACKSPACE), map.get("BKSP"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.DELETE_FORWARD), map.get("DEL"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.MENU), map.get("DRAWER"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PASTE), map.get("PASTE"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.SCROLL), map.get("SCROLL"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.KEYBOARD), map.get("KEYBOARD"));
        // Word keys stay words.
        assertNull(map.get("ESC"));
        assertNull(map.get("CTRL"));
        assertNull(map.get("HOME"));
        ExtraKeysConstants.ExtraKeyDisplayMap all = ExtraKeysInfo.getCharDisplayMapForStyle("all");
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.KEY_HOME), all.get("HOME"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.KEY_END), all.get("END"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PAGE_UP), all.get("PGUP"));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.PAGE_DOWN), all.get("PGDN"));
    }

    @Test public void aStoredSymbolOrNerdDisplayStaysLiteral() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");
        assertEquals("←", ExtraKeyButton.resolveDisplay("LEFT", "←", map));
        assertEquals("󰥻",
            ExtraKeyButton.resolveDisplay("KEYBOARD", "󰥻", map));
    }
}
