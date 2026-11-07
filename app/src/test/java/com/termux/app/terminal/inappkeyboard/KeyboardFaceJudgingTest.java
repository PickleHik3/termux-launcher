package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyboardFaceJudgingTest {
    @Test public void atRestTheFacesAreJudged() {
        assertTrue(KeyboardFaceJudging.due(false, false));
    }

    @Test public void aCrossingNeverBuildsAPalette() {
        assertFalse(KeyboardFaceJudging.due(false, true));
    }

    @Test public void aHeldEditorThumbWaits() {
        assertFalse(KeyboardFaceJudging.due(true, false));
        assertFalse(KeyboardFaceJudging.due(true, true));
    }
}
