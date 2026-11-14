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
        // the keyboard type on its swipe), mouse mode, a new pane — Ctrl+Alt+Enter's split, along the longer side — (with a
        // new window on its swipe) and the session browser (with a new session on its swipe). Window and session switching live on the
        // keyboard's space-bar swipes; workspaces, search, prompt jumps and the scratchpad have
        // chords and the palette.
        String[] expectedKeys = {
            "KEYBOARD",
            "tool:mouse.toggle",
            "tool:pane.split",
            "tool:session.browser",
        };
        String[] expectedPopups = {
            "tool:keyboard.cycle_form",
            null,
            "tool:window.new",
            "tool:session.new",
        };
        assertEquals(expectedKeys.length, matrix[0].length);
        for (int i = 0; i < expectedKeys.length; i++) {
            assertEquals(expectedKeys[i], matrix[0][i].getKey());
            assertEquals(expectedPopups[i],
                matrix[0][i].getPopup() == null ? null : matrix[0][i].getPopup().getKey());
        }
    }

    @Test
    public void displayAndAssistantDefaultsParseAndShareGlobalsKeyboardAndMouseKeys() throws Exception {
        ExtraKeyButton[] global = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES)
            .getMatrix()[0];
        ExtraKeyButton[] display = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_DISPLAY,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES)
            .getMatrix()[0];
        ExtraKeyButton[] assistant = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES)
            .getMatrix()[0];
        String[] displayKeys = {"KEYBOARD", "tool:mouse.toggle", "ESC", "TAB", "CTRL", "ALT"};
        assertEquals(displayKeys.length, display.length);
        for (int i = 0; i < displayKeys.length; i++) assertEquals(displayKeys[i], display[i].getKey());
        String[] assistantKeys = {"KEYBOARD", "tool:voice.dictate", "tool:clipboard.paste"};
        assertEquals(assistantKeys.length, assistant.length);
        for (int i = 0; i < assistantKeys.length; i++) assertEquals(assistantKeys[i], assistant[i].getKey());
        // Same definitions, so the caps are the same keys when the bar changes set.
        for (ExtraKeyButton[] row : new ExtraKeyButton[][]{display, assistant}) {
            assertEquals(global[0].getDisplay(), row[0].getDisplay());
            assertEquals(global[0].getPopup().getKey(), row[0].getPopup().getKey());
            assertEquals(global[0].getPopup().getDisplay(), row[0].getPopup().getDisplay());
        }
        assertEquals(global[1].getDisplay(), display[1].getDisplay());
    }

    @Test
    public void defaultExtraKeys2IsAnEmptyPage() throws Exception {
        // A page with no keys is dropped by the pager, so the launcher ships a single page.
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(0, info.getMatrix().length);
    }
}
