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
        // Window and session switching moved to the in-app keyboard's space-bar swipes, so the
        // row only carries what no key on that keyboard can reach.
        String[] expectedKeys = {
            "KEYBOARD",
            "tool:session.new",
            "tool:pane.split_vertical",
            "tool:terminal.jump_previous_prompt",
            "tool:terminal.search_scrollback",
            "tool:workspace.picker",
            "tool:terminal.toggle_scratchpad",
        };
        String[] expectedPopups = {
            "tool:terminal.select_at_cursor",
            "tool:window.new",
            "tool:pane.split_horizontal",
            "tool:terminal.jump_next_prompt",
            "tool:terminal.hints",
            "tool:workspace.save_prompt",
            "tool:pane.toggle_float",
        };
        assertEquals(expectedKeys.length, matrix[0].length);
        for (int i = 0; i < expectedKeys.length; i++) {
            assertEquals(expectedKeys[i], matrix[0][i].getKey());
            assertEquals(expectedPopups[i], matrix[0][i].getPopup().getKey());
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
