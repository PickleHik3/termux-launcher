package com.termux.app.terminal.inappkeyboard.voice;

/**
 * The noise floor the pill's waveform measures from: the VAD's own floor while the room is quiet,
 * held still from the first voiced frame until {@link #RELEASE_FRAMES} quiet frames have passed.
 * The VAD's floor is a rolling percentile over all frames, so it rises while someone keeps
 * talking; measured against that, a long sentence faded to one or two bars on pong's quiet mic.
 * Only the display holds it: the VAD's decisions are untouched. One thread (main) feeds it.
 */
final class VoiceWaveformFloor {

    /** 1.5 s of 30 ms frames: a pause between sentences keeps the floor, a quiet spell re-learns it. */
    static final int RELEASE_FRAMES = 50;

    private float floor;
    private int quietFrames = RELEASE_FRAMES;

    /** The floor for this frame, given the VAD's floor and whether the frame was voiced. */
    float update(float vadFloor, boolean voiced) {
        if (voiced) {
            quietFrames = 0;
            if (floor <= 0f) floor = vadFloor;
            return floor;
        }
        if (quietFrames < RELEASE_FRAMES) {
            quietFrames++;
            if (floor <= 0f) floor = vadFloor;
            return floor;
        }
        floor = vadFloor;
        return floor;
    }
}
