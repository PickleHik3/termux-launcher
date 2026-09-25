package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The silence auto-stop setting's ms mapping: a pure function, no VAD or store involved. */
public class VoiceSilenceTimeoutTest {

    @Test
    public void offeredChoicesRoundTrip() {
        assertEquals(5_000, VoiceSilenceTimeout.normalize(5_000));
        assertEquals(10_000, VoiceSilenceTimeout.normalize(10_000));
        assertEquals(30_000, VoiceSilenceTimeout.normalize(30_000));
        assertEquals(VoiceSilenceTimeout.UNTIL_TAP, VoiceSilenceTimeout.normalize(VoiceSilenceTimeout.UNTIL_TAP));
    }

    @Test
    public void anythingElseReadsAsTheDefault() {
        assertEquals(VoiceSilenceTimeout.DEFAULT_MS, VoiceSilenceTimeout.normalize(2_500));
        assertEquals(VoiceSilenceTimeout.DEFAULT_MS, VoiceSilenceTimeout.normalize(-1));
        assertEquals(VoiceSilenceTimeout.DEFAULT_MS, VoiceSilenceTimeout.normalize(60_000));
    }

    @Test
    public void theDefaultIsTenSeconds() {
        assertEquals(10_000, VoiceSilenceTimeout.DEFAULT_MS);
    }
}
