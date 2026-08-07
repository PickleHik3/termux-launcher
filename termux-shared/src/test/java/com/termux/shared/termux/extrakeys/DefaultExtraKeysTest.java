package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Pins the built-in extra-keys defaults so they stay parseable and keep the intended shape:
 * the launcher's tool row for the primary bar, and the function/symbol rows for the second.
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultExtraKeysTest {

    @Test
    public void defaultExtraKeysParseIntoTheLauncherToolRow() throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        ExtraKeyButton[][] matrix = info.getMatrix();
        assertEquals(1, matrix.length);
        String[] expectedKeys = {
            "KEYBOARD",
            "tool:workspace.picker",
            "tool:workspace.save_prompt",
            "tool:window.previous",
            "tool:window.next",
            "tool:pane.move_to_edge:edge=left",
            "tool:terminal.toggle_scratchpad",
        };
        assertEquals(expectedKeys.length, matrix[0].length);
        for (int i = 0; i < expectedKeys.length; i++)
            assertEquals(expectedKeys[i], matrix[0][i].getKey());
    }

    @Test
    public void defaultExtraKeys2ParseIntoTwoRows() throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(2, info.getMatrix().length);
    }
}
