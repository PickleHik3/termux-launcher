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

        // Session switching hangs off the window keys as swipe-up popups.
        assertEquals("tool:session.previous", matrix[0][3].getPopup().getKey());
        assertEquals("tool:session.next", matrix[0][4].getPopup().getKey());
    }

    @Test
    public void defaultExtraKeys2IsAnEmptyPage() throws Exception {
        // A page with no keys is dropped by the pager, so the launcher ships a single page.
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(0, info.getMatrix().length);
    }
}
