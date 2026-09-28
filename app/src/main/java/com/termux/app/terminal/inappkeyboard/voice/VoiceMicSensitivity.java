package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How readily quiet speech opens a phrase: Keyboard → Voice input → "Mic sensitivity".
 *
 * <p>Silero's speech probability depends on how loud the audio is, not only on what it sounds
 * like. {@code VOICE_RECOGNITION} has no AGC, and on pong speech from arm's length reaches the
 * detector at about −45 to −52 dBFS frame RMS over a −60 to −65 dBFS room. Spoken 6–10 dB more
 * quietly — an office voice — the probability sinks under the 0.5 onset: on the developer's own
 * captured phrases, 75 % of one phrase's chunks were voiced at its own level, 29 % 6 dB down and
 * 4 % 10 dB down. Lowering the onset to 0.4 saves some short key words but none of those quiet
 * phrases (their probabilities sit far under any sane threshold); raising the level does. So the detector hands Silero a copy of each frame lifted
 * until the learned noise floor sits at {@link #floorTargetDbfs}, never turned down and never by
 * more than {@link #maxGainDb}. The floor, not the frame, sets the gain, so speech keeps its
 * distance over the room; the segment sent for transcription is untouched (it has
 * {@link VoiceGain} of its own).
 *
 * <p>Stationary noise (fan, pink room tone, keyboard clicks) never read as speech at any level
 * in the evaluation; background talk (a TV, colleagues) does, more the louder it is shown to
 * Silero. That is the one trade-off no single default serves, hence two settings. The numbers
 * and the runs behind them are in {@code project-docs/plans/voice-vad-eval-2026-09-27.md}
 * ("Round 3: quiet speech"), probed with {@code scripts/voice-eval/sensitivity_probe.py}.
 */
public enum VoiceMicSensitivity {

    /**
     * The default: a small lift where the room is very quiet (about +2 dB on pong's −65 dBFS
     * room, never more than +6). At −62 dBFS it missed 1 of 16 sentences rather than 3 and 2 of
     * 12 key words rather than 5, and 3 of the developer's 7 phrases played 10 dB down rather than
     * 4, at no cost on fan, clicks or babble, and one false phrase in two minutes of a TV at
     * −66 dBFS.
     */
    NORMAL("normal", -63f, 6f, 300),

    /**
     * For speaking softly: up to +12 dB, the floor lifted to −56 dBFS. Every quiet phrase and key
     * word in the evaluation came through (87 % of the phrase audio kept 10 dB down, against 37 %
     * before), and a phrase needs 420 ms of voiced frames rather than 300 so that a burst of
     * someone else's speech is less likely to open one. It will hear a TV or a conversation close
     * by: 4–11 false phrases a minute with a TV at −70 to −66 dBFS, 8 with babble at −62, in the
     * evaluation (Normal: 0–0.5).
     */
    HIGH("high", -56f, 12f, 420);

    /** What the preference stores. */
    @NonNull public final String storageValue;
    /** The level, in dBFS, the gain lifts the learned noise floor to. */
    public final float floorTargetDbfs;
    /** The most the gain may lift a frame by, in dB. */
    public final float maxGainDb;
    /** Voiced time a segment needs to be sent; shorter ones are dropped as noise. */
    public final int minVoicedMs;

    private final float floorTarget;
    private final float maxGain;

    VoiceMicSensitivity(@NonNull String storageValue, float floorTargetDbfs, float maxGainDb,
                        int minVoicedMs) {
        this.storageValue = storageValue;
        this.floorTargetDbfs = floorTargetDbfs;
        this.maxGainDb = maxGainDb;
        this.minVoicedMs = minVoicedMs;
        this.floorTarget = (float) Math.pow(10.0, floorTargetDbfs / 20.0);
        this.maxGain = (float) Math.pow(10.0, maxGainDb / 20.0);
    }

    /** The stored value's sensitivity; anything else, missing included, is {@link #NORMAL}. */
    @NonNull
    public static VoiceMicSensitivity fromStorage(@Nullable String value) {
        return HIGH.storageValue.equals(value) ? HIGH : NORMAL;
    }

    /**
     * The factor Silero's copy of a frame is scaled by, for the detector's current noise floor
     * (an RMS in [0, 1]): enough to bring the floor up to {@link #floorTargetDbfs}, at least 1 and
     * at most {@link #maxGainDb}. A floor of zero (digital silence) gets the most.
     */
    float sileroGain(float noiseFloor) {
        if (!(noiseFloor > 0f)) return maxGain;
        return Math.max(1f, Math.min(maxGain, floorTarget / noiseFloor));
    }
}
