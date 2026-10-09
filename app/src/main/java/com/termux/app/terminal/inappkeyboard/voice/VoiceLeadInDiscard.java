package com.termux.app.terminal.inappkeyboard.voice;

/**
 * Drops the first stretch of captured audio so the start tone never reaches the VAD — and so
 * its 300 ms pre-roll cannot hold it either, which is why this sits in front of the detector
 * rather than inside it. The microphone hears the phone's own speaker; a 125 ms blip at −16 dBFS
 * is well over the 9 dB voice threshold and would open a segment on its own. Pure: it only counts
 * samples, so the capture loop asks it how many of each read to skip.
 */
public final class VoiceLeadInDiscard {

    /**
     * The 125 ms tone plus up to ~50 ms of output latency before it is heard; nothing said in this
     * window survives, and a whole number of 30 ms frames so the count is easy to reason about.
     */
    public static final int START_TONE_MS = 180;

    private int remaining;

    /** @param discardMs how much audio from the first sample fed is dropped; 0 drops nothing. */
    public VoiceLeadInDiscard(int discardMs, int sampleRate) {
        remaining = Math.max(0, discardMs) * sampleRate / 1000;
    }

    /** @return how many of the next {@code count} samples to drop, from the front; the rest go to the VAD. */
    public int take(int count) {
        int drop = Math.min(remaining, Math.max(0, count));
        remaining -= drop;
        return drop;
    }

    public boolean done() {
        return remaining == 0;
    }
}
