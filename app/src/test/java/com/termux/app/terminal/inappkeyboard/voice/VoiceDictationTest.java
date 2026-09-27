package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The panel's dictation: phrases collect, the text settles, and ✓ or Copy use it once. */
public class VoiceDictationTest {

    @Test
    public void phrasesJoinWithOneSpaceAndNonSpeechIsDropped() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        assertEquals("so the pc is busy", dictation.append("so the pc is busy"));
        assertEquals("", dictation.append("[BLANK_AUDIO]"));
        assertEquals(" never run gradle", dictation.append("  never run gradle "));
        assertEquals("so the pc is busy never run gradle", dictation.raw());
    }

    @Test
    public void aNewlineInATranscriptNeverReachesTheText() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("ls\nrm -rf");
        assertEquals("ls rm -rf", dictation.raw());
    }

    @Test
    public void thePanelWaitsForAButtonOnceTheTextSettles() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("open the settings page");
        dictation.onStopped();
        assertEquals(VoiceDictation.Phase.FINISHING, dictation.phase());
        assertNull(dictation.onSettled("Open the settings page."));
        assertEquals(VoiceDictation.Phase.WAITING, dictation.phase());
        assertTrue(dictation.press(VoiceDictation.Use.INSERT));
        assertEquals("Open the settings page.", dictation.result());
        // Used once: a second press does nothing.
        assertFalse(dictation.press(VoiceDictation.Use.INSERT));
        assertFalse(dictation.press(VoiceDictation.Use.COPY));
        assertEquals(VoiceDictation.Phase.USED, dictation.phase());
    }

    @Test
    public void aPressWhileListeningIsCarriedOutWhenTheTextSettles() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("commit the voice change");
        assertFalse(dictation.press(VoiceDictation.Use.COPY));
        assertEquals(VoiceDictation.Use.COPY, dictation.pending());
        dictation.onStopped();
        // The later press wins: the user changed their mind while the cleanup ran.
        assertFalse(dictation.press(VoiceDictation.Use.INSERT));
        assertEquals(VoiceDictation.Use.INSERT, dictation.onSettled("Commit the voice change."));
        assertEquals(VoiceDictation.Phase.USED, dictation.phase());
        assertNull(dictation.pending());
    }

    @Test
    public void nothingIsUsedBeforeADictationOrAfterItIsDiscarded() {
        VoiceDictation dictation = new VoiceDictation();
        assertFalse(dictation.press(VoiceDictation.Use.INSERT));
        dictation.start("");
        dictation.append("never mind");
        dictation.clear();
        assertEquals(VoiceDictation.Phase.IDLE, dictation.phase());
        assertTrue(dictation.isEmpty());
        assertFalse(dictation.press(VoiceDictation.Use.COPY));
        assertNull(dictation.result());
        // A phrase that comes back after the discard has nowhere to go.
        assertEquals("", dictation.append("too late"));
        assertTrue(dictation.isEmpty());
    }

    @Test
    public void phrasesStillTranscribingAfterStopJoinTheText() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("the last words");
        dictation.onStopped();
        assertEquals(" came in late", dictation.append("came in late"));
        assertEquals("the last words came in late", dictation.raw());
        dictation.onSettled(dictation.raw());
        // Settled: the text is what the buttons use, and nothing more joins it.
        assertEquals("", dictation.append("after the end"));
        assertEquals("the last words came in late", dictation.result());
    }

    @Test
    public void aNewDictationCarriesOnFromTheWaitingText() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("first thought");
        dictation.onStopped();
        // While the cleanup runs, what was heard is what carries over.
        assertEquals("first thought", dictation.carryOver());
        dictation.onSettled("First thought.");
        assertEquals("First thought.", dictation.carryOver());
        dictation.start(dictation.carryOver());
        assertEquals(" and a second", dictation.append("and a second"));
        assertEquals("First thought. and a second", dictation.raw());
        // Once used, nothing carries over.
        dictation.onStopped();
        dictation.onSettled(dictation.raw());
        dictation.press(VoiceDictation.Use.INSERT);
        assertEquals("", dictation.carryOver());
    }
}
