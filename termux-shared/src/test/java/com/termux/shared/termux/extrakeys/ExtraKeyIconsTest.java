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

    private static String icon(int codePoint) {
        return ExtraKeyIcons.string(codePoint);
    }

    /** An old shipped display on its own key reads as the icon; everything else is the user's. */
    @Test public void oldShippedDisplaysOnTheirOwnKeyReadAsTheIcon() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");
        Object[][] table = {
            // key spec, stored display, expected icon
            {"tool:wall.widgets", "\uf015", ExtraKeyIcons.HOME},
            {"tool:wall.widgets", "\uf00a", ExtraKeyIcons.HOME},
            {"tool:wall.terminal", "\uf120", ExtraKeyIcons.TERMINAL},
            {"tool:wall.display", "\uf108", ExtraKeyIcons.DISPLAY},
            {"tool:mouse.toggle", "\uDB80\uDF7D", ExtraKeyIcons.MOUSE},
            {"tool:mouse.toggle", "\uf245", ExtraKeyIcons.MOUSE},
            {"tool:keyboard.cycle_form", "\uf11c", ExtraKeyIcons.KEYBOARD},
            {"tool:app.open_app_drawer", "\uDB80\uDC3B", ExtraKeyIcons.APPS},
            {"tool:voice.dictate", "\uf130", ExtraKeyIcons.MIC},
            {"tool:voice.dictate", "\uDB80\uDF6C", ExtraKeyIcons.MIC},
            {"tool:pane.split", "\uDB84\uDDDA", ExtraKeyIcons.SPLIT},
            {"tool:window.new", "\uDB84\uDCAC", ExtraKeyIcons.WINDOW_ADD},
            {"tool:session.browser", "\uDB80\uDE79", ExtraKeyIcons.SESSIONS},
            {"tool:clipboard.paste", "\uDB80\uDD92", ExtraKeyIcons.PASTE},
            {"tool:app.command_palette", "\uf002", ExtraKeyIcons.COMMAND},
            {"tool:session.new", "\uDB81\uDF5C", ExtraKeyIcons.ADD},
            {"tool:pane.split_vertical", "\uDB82\uDFCC", ExtraKeyIcons.SPLIT},
            {"tool:app.open_settings", "\uf013", ExtraKeyIcons.SETTINGS},
            {"tool:wall.terminal:page=x", "\uf120", ExtraKeyIcons.TERMINAL},
            {"KEYBOARD", "\uDB82\uDD7B", ExtraKeyIcons.KEYBOARD},
            {"KEYBOARD", "\u2328", ExtraKeyIcons.KEYBOARD},
            {"LEFT", "\u2190", ExtraKeyIcons.ARROW_LEFT},
            {"RIGHT", "\u2192", ExtraKeyIcons.ARROW_RIGHT},
            {"UP", "\u2191", ExtraKeyIcons.ARROW_UP},
            {"DOWN", "\u2193", ExtraKeyIcons.ARROW_DOWN},
            {"ENTER", "\u21b2", ExtraKeyIcons.ENTER},
            {"TAB", "\u21b9", ExtraKeyIcons.TAB},
            {"BKSP", "\u232b", ExtraKeyIcons.BACKSPACE},
            {"DEL", "\u2326", ExtraKeyIcons.DELETE_FORWARD},
            {"DRAWER", "\u2630", ExtraKeyIcons.MENU},
            {"PASTE", "\u2398", ExtraKeyIcons.PASTE},
            {"SCROLL", "\u21f3", ExtraKeyIcons.SCROLL},
            {"HOME", "\u21f1", ExtraKeyIcons.KEY_HOME},
            {"END", "\u21f2", ExtraKeyIcons.KEY_END},
            {"PGUP", "\u21d1", ExtraKeyIcons.PAGE_UP},
            {"PGDN", "\u21d3", ExtraKeyIcons.PAGE_DOWN},
        };
        for (Object[] row : table) {
            assertEquals(row[0] + " " + row[1], icon((Integer) row[2]),
                ExtraKeyButton.resolveDisplay((String) row[0], (String) row[1], map));
            assertEquals(icon((Integer) row[2]),
                ExtraKeyIcons.effectiveDisplay((String) row[0], (String) row[1]));
        }
    }

    @Test public void theSameGlyphOnAnotherKeyOrAUserGlyphStaysAsStored() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");
        // F120 is the terminal action's old glyph, not this key's.
        assertEquals("\uf120", ExtraKeyButton.resolveDisplay("tool:wall.display", "\uf120", map));
        assertEquals("\uf120", ExtraKeyButton.resolveDisplay("tool:app.open_settings", "\uf120", map));
        assertEquals("\u2190", ExtraKeyButton.resolveDisplay("RIGHT", "\u2190", map));
        // A glyph the user chose.
        assertEquals("\uf121", ExtraKeyButton.resolveDisplay("tool:wall.terminal", "\uf121", map));
        assertEquals("exit", ExtraKeyButton.resolveDisplay("KEYBOARD", "exit", map));
        // A macro is never rewritten.
        assertEquals("\u2190", ExtraKeyButton.resolveDisplay("CTRL LEFT", "\u2190", map));
        assertEquals("\u2190", ExtraKeyIcons.effectiveDisplay(null, "\u2190"));
    }

    @Test public void anAbsentDisplayResolvesToTheDefaultIconAsBefore() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");
        assertEquals(icon(ExtraKeyIcons.ARROW_LEFT), ExtraKeyButton.resolveDisplay("LEFT", null, map));
        assertEquals("", ExtraKeyButton.resolveDisplay("LEFT", "", map));
        assertNull(ExtraKeyIcons.effectiveDisplay("LEFT", null));
    }
}