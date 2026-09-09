package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Pins the built-in extra-keys defaults so they stay parseable and keep the intended shape:
 * the launcher's tool row for the primary bar, and an empty second page.
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultExtraKeysTest {

    @Test
    public void defaultExtraKeysParseIntoTheLauncherToolRow() throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        ExtraKeyButton[][] matrix = info.getMatrix();
        assertEquals(1, matrix.length);
        // The row carries what no key on the in-app keyboard reaches: the keyboard toggle (with
        // the keyboard type on its swipe), mouse mode, the wall's three places, a split (with a
        // new window on its swipe) and a new session. Window and session switching live on the
        // keyboard's space-bar swipes; workspaces, search, prompt jumps and the scratchpad have
        // chords and the palette.
        String[] expectedKeys = {
            "KEYBOARD",
            "tool:mouse.toggle",
            "tool:wall.widgets",
            "tool:wall.terminal",
            "tool:wall.display",
            "tool:pane.split_vertical",
            "tool:session.new",
        };
        String[] expectedPopups = {
            "tool:keyboard.cycle_form",
            null,
            null,
            null,
            null,
            "tool:window.new",
            null,
        };
        assertEquals(expectedKeys.length, matrix[0].length);
        for (int i = 0; i < expectedKeys.length; i++) {
            assertEquals(expectedKeys[i], matrix[0][i].getKey());
            assertEquals(expectedPopups[i],
                matrix[0][i].getPopup() == null ? null : matrix[0][i].getPopup().getKey());
        }
    }

    @Test
    public void defaultExtraKeys2IsAnEmptyPage() throws Exception {
        // A page with no keys is dropped by the pager, so the launcher ships a single page.
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(0, info.getMatrix().length);
    }
}
