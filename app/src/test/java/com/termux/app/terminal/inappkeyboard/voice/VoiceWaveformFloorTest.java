package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The waveform's floor: follows the VAD while quiet, held from the first voiced frame. */
public class VoiceWaveformFloorTest {

    @Test
    public void quietFramesFollowTheVadFloor() {
        VoiceWaveformFloor floor = new VoiceWaveformFloor();
        assertEquals(0.01f, floor.update(0.01f, false), 0f);
        assertEquals(0.012f, floor.update(0.012f, false), 0f);
    }

    @Test
    public void speechHoldsTheFloorWhileTheVadFloorRises() {
        VoiceWaveformFloor floor = new VoiceWaveformFloor();
        floor.update(0.01f, false);
        assertEquals(0.01f, floor.update(0.02f, true), 0f);
        assertEquals(0.01f, floor.update(0.03f, true), 0f);
        // A pause between sentences keeps it held.
        for (int i = 0; i < VoiceWaveformFloor.RELEASE_FRAMES; i++) {
            assertEquals(0.01f, floor.update(0.04f, false), 0f);
        }
        // A quiet spell long enough re-learns the room.
        assertEquals(0.04f, floor.update(0.04f, false), 0f);
    }

    @Test
    public void speechBeforeAnyFloorTakesTheFirstOneSeen() {
        VoiceWaveformFloor floor = new VoiceWaveformFloor();
        assertEquals(0f, floor.update(0f, true), 0f);
        assertEquals(0.02f, floor.update(0.02f, true), 0f);
        assertEquals(0.02f, floor.update(0.05f, true), 0f);
    }

    @Test
    public void theWaveRangeIsThreeToThirtyDbOverTheFloor() {
        float floor = 0.01f;
        assertEquals(0f, VoiceLevelCurve.waveLevel(floor, floor), 0f);
        assertEquals(0f, VoiceLevelCurve.waveLevel((float) (floor * Math.pow(10, 3.0 / 20.0)), floor), 0.001f);
        assertEquals(0.5f, VoiceLevelCurve.waveLevel((float) (floor * Math.pow(10, 16.5 / 20.0)), floor), 0.001f);
        assertEquals(1f, VoiceLevelCurve.waveLevel((float) (floor * Math.pow(10, 30.0 / 20.0)), floor), 0.001f);
        assertEquals(1f, VoiceLevelCurve.waveLevel((float) (floor * Math.pow(10, 50.0 / 20.0)), floor), 0f);
        assertEquals(0f, VoiceLevelCurve.waveLevel(0.1f, 0f), 0f);
    }
}
