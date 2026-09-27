package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the cleanup may swap the dictated line in place, and what it writes to do so. */
public class VoiceTypedLineTest {

    @Test
    public void theLineIsUntouchedWhileTheWriteMarkHasNotMoved() {
        VoiceTypedLine line = new VoiceTypedLine();
        line.onTyped("so the pc is busy", true, 100L);
        line.onTyped(" never run gradle", true, 250L);
        assertEquals("so the pc is busy never run gradle", line.text());
        assertTrue(line.isUntouched(250L, true));
        // A key, an Enter, a paste or a terminal reply since the last phrase.
        assertFalse(line.isUntouched(300L, true));
        // The shell has gone.
        assertFalse(line.isUntouched(250L, false));
    }

    @Test
    public void anEmptyLineOrOneThatWentElsewhereIsNeverSwapped() {
        VoiceTypedLine line = new VoiceTypedLine();
        assertFalse(line.isUntouched(0L, true));
        line.onTyped("open the settings", false, 0L);
        assertTrue(line.wentOffTerminal());
        assertFalse(line.isUntouched(0L, true));
        line.onTyped(" and the rest", true, 10L);
        assertFalse(line.isUntouched(10L, true));
    }

    @Test
    public void theSwapErasesOneCharacterPerCodePointThenTypes() {
        VoiceTypedLine line = new VoiceTypedLine();
        line.onTyped("ok 👍", true, 5L);
        String swap = line.swapSequence("OK.");
        // "ok 👍" is four code points (five chars: the emoji is a surrogate pair).
        assertEquals("\u007f\u007f\u007f\u007fOK.", swap);
        assertEquals(4, line.eraseCount());
    }

    @Test
    public void afterASwapTheLineIsTheCleanedText() {
        VoiceTypedLine line = new VoiceTypedLine();
        line.onTyped("uh run the tests", true, 5L);
        line.onSwapped("Run the tests.", 9L);
        assertEquals("Run the tests.", line.text());
        assertTrue(line.isUntouched(9L, true));
        line.reset();
        assertTrue(line.isEmpty());
        assertFalse(line.wentOffTerminal());
    }
}
