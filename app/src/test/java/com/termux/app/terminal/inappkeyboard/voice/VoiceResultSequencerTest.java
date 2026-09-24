package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Results come back in spoken order, whatever order they transcribe in. */
public class VoiceResultSequencerTest {

    @Test
    public void inOrderResultsAreDeliveredAtOnce() {
        VoiceResultSequencer<String> sequencer = new VoiceResultSequencer<>();
        assertEquals(Collections.singletonList("ls"), sequencer.offer(0, "ls"));
        assertEquals(Collections.singletonList("enter"), sequencer.offer(1, "enter"));
        assertEquals(0, sequencer.pendingCount());
    }

    @Test
    public void aLaterResultWaitsForTheEarlierOne() {
        VoiceResultSequencer<String> sequencer = new VoiceResultSequencer<>();
        assertTrue(sequencer.offer(1, "enter").isEmpty());
        assertTrue(sequencer.offer(2, "git status").isEmpty());
        assertEquals(2, sequencer.pendingCount());
        assertEquals(Arrays.asList("ls", "enter", "git status"), sequencer.offer(0, "ls"));
        assertEquals(0, sequencer.pendingCount());
        assertEquals(Collections.singletonList("enter"), sequencer.offer(3, "enter"));
    }

    @Test
    public void anEmptyResultStillHoldsItsPlace() {
        VoiceResultSequencer<String> sequencer = new VoiceResultSequencer<>();
        assertTrue(sequencer.offer(1, "b").isEmpty());
        assertEquals(Arrays.asList("", "b"), sequencer.offer(0, ""));
    }
}
