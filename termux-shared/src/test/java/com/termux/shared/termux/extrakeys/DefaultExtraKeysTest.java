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
        // The row carries what no key on the in-app keyboard reaches: the keyboard toggle, a new
        // session, the wall's three places, a split, and the workspaces. Window and session
        // switching live on the keyboard's space-bar swipes; search, prompt jumps and the
        // scratchpad have chords and the palette.
        String[] expectedKeys = {
            "KEYBOARD",
            "tool:session.new",
            "tool:wall.widgets",
            "tool:wall.terminal",
            "tool:wall.display",
            "tool:pane.split_vertical",
            "tool:workspace.picker",
        };
        String[] expectedPopups = {
            "tool:terminal.select_at_cursor",
            "tool:window.new",
            null,
            null,
            null,
            "tool:pane.split_horizontal",
            "tool:workspace.save_prompt",
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
