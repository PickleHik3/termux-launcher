package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiRuntimePresence;

import org.junit.Test;

/**
 * When the cleanup model is warmed: as the mic opens only when that loads nothing, else at the
 * session's end, never at a pause mid-dictation.
 */
public class VoiceCleanupWarmUpTest {

    private static final String E4B = "gemma-4-e4b-it-litert-lm";

    @Test
    public void aResidentModelWarmsAsTheMicOpens() {
        assertEquals(VoiceCleanupWarmUp.Trigger.OPEN, VoiceCleanupWarmUp.atOpen(true));
        assertNull(VoiceCleanupWarmUp.atEnd(true));
    }

    @Test
    public void aColdModelWaitsForTheEnd() {
        assertNull(VoiceCleanupWarmUp.atOpen(false));
        assertEquals(VoiceCleanupWarmUp.Trigger.END, VoiceCleanupWarmUp.atEnd(false));
    }

    @Test
    public void theRuntimesLoadedModelIsResident() {
        assertTrue(LocalTaiVoiceTextPolisher.loadsNothing(local(E4B, 0), presence(true, E4B)));
    }

    @Test
    public void anotherLoadedModelOrNoneIsCold() {
        assertFalse(LocalTaiVoiceTextPolisher.loadsNothing(local(E4B, 0), presence(true, "gemma-3n-e2b-it-litert-lm")));
        assertFalse(LocalTaiVoiceTextPolisher.loadsNothing(local(E4B, 0), presence(false, E4B)));
        assertFalse(LocalTaiVoiceTextPolisher.loadsNothing(local(E4B, 0), TaiRuntimePresence.empty()));
    }

    @Test
    public void aRemoteModelOrNoModelLoadsNothing() {
        assertTrue(LocalTaiVoiceTextPolisher.loadsNothing(
            new LocalTaiVoiceTextPolisher.Plan("remote/gpt", true, null, null), TaiRuntimePresence.empty()));
        assertTrue(LocalTaiVoiceTextPolisher.loadsNothing(
            new LocalTaiVoiceTextPolisher.Plan("", false, "no_model", null), TaiRuntimePresence.empty()));
    }

    private static LocalTaiVoiceTextPolisher.Plan local(String model, int window) {
        return new LocalTaiVoiceTextPolisher.Plan(model, false, null, null, true, window);
    }

    private static TaiRuntimePresence.Snapshot presence(boolean loaded, String model) {
        return TaiRuntimePresence.snapshotForTest(loaded, false, false, model, 0L, 0L);
    }
}
