package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** The Ctrl+Space pass-through predicate of {@link TerminalView}. */
public class CtrlSpacePassThroughTest {

    @Test
    public void onlyCtrlSpaceWithTheOptionOnIsYielded() {
        assertTrue(TerminalView.isCtrlSpacePassThrough(true, KeyEvent.KEYCODE_SPACE, true));
        assertFalse(TerminalView.isCtrlSpacePassThrough(false, KeyEvent.KEYCODE_SPACE, true));
        assertFalse(TerminalView.isCtrlSpacePassThrough(true, KeyEvent.KEYCODE_SPACE, false));
        assertFalse(TerminalView.isCtrlSpacePassThrough(true, KeyEvent.KEYCODE_A, true));
    }
}
