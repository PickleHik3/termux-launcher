package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** The "Mic sensitivity" setting: what it stores, and how far it lifts Silero's copy of a frame. */
public class VoiceMicSensitivityTest {

    private static float rms(float dbfs) {
        return (float) Math.pow(10.0, dbfs / 20.0);
    }

    private static float db(float gain) {
        return (float) (20.0 * Math.log10(gain));
    }

    @Test
    public void onlyHighReadsAsHighAndEverythingElseIsNormal() {
        assertSame(VoiceMicSensitivity.HIGH, VoiceMicSensitivity.fromStorage("high"));
        assertSame(VoiceMicSensitivity.NORMAL, VoiceMicSensitivity.fromStorage("normal"));
        assertSame(VoiceMicSensitivity.NORMAL, VoiceMicSensitivity.fromStorage(null));
        assertSame(VoiceMicSensitivity.NORMAL, VoiceMicSensitivity.fromStorage("HIGH"));
        assertSame(VoiceMicSensitivity.NORMAL, VoiceMicSensitivity.fromStorage("max"));
    }

    @Test
    public void normalKeepsTheDetectorsOwnMinimumVoicedTime() {
        assertEquals(VoiceActivityDetector.MIN_VOICED_MS, VoiceMicSensitivity.NORMAL.minVoicedMs);
        assertEquals(420, VoiceMicSensitivity.HIGH.minVoicedMs);
    }

    @Test
    public void theGainLiftsTheFloorToItsTargetAndNoFurther() {
        // pong's quiet room, −65 dBFS: Normal lifts it the 2 dB to −63, High the 9 dB to −56.
        assertEquals(2f, db(VoiceMicSensitivity.NORMAL.sileroGain(rms(-65f))), 0.01f);
        assertEquals(9f, db(VoiceMicSensitivity.HIGH.sileroGain(rms(-65f))), 0.01f);
        // A very quiet room is held at the most each allows.
        assertEquals(6f, db(VoiceMicSensitivity.NORMAL.sileroGain(rms(-90f))), 0.01f);
        assertEquals(12f, db(VoiceMicSensitivity.HIGH.sileroGain(rms(-90f))), 0.01f);
        // Digital silence gets the most, never a division by zero.
        assertEquals(6f, db(VoiceMicSensitivity.NORMAL.sileroGain(0f)), 0.01f);
    }

    @Test
    public void aRoomAlreadyOverTheTargetIsNeverTurnedDown() {
        assertEquals(1f, VoiceMicSensitivity.NORMAL.sileroGain(rms(-63f)), 1e-4f);
        assertEquals(1f, VoiceMicSensitivity.NORMAL.sileroGain(rms(-40f)), 0f);
        assertEquals(1f, VoiceMicSensitivity.HIGH.sileroGain(rms(-50f)), 0f);
        assertEquals(1f, VoiceMicSensitivity.HIGH.sileroGain(VoiceActivityDetector.NOISE_FLOOR_CAP), 0f);
    }
}
