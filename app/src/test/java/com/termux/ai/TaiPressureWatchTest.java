package com.termux.ai;

import android.content.ComponentCallbacks2;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The watch's decisions from a table. Memory figures are pong's: {@code MemoryInfo.threshold}
 * 315 MB (dumpsys activity oom, 2026-09-24), so the floor is 630 MB and the chat line 394 MB.
 */
public class TaiPressureWatchTest {

    private static final long MB = 1024L * 1024L;
    private static final long PONG_THRESHOLD = 315_000_000L;
    private static final long PONG_FLOOR = TaiLoadBudget.floorBytes(PONG_THRESHOLD, 11_530_736L * 1024L);
    private static final long NOW = 1_700_000_000_000L;

    // --- tiers ---------------------------------------------------------------------------------

    @Test
    public void tierFollowsAndroidsLinesFromTheTopDown() {
        assertEquals(TaiPressureWatch.Tier.NONE, tier(5_800L * MB, false));
        assertEquals(TaiPressureWatch.Tier.NONE, tier(PONG_FLOOR, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(PONG_FLOOR - 1L, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(500L * MB, false));
        long chatLine = PONG_THRESHOLD * 125L / 100L;
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(chatLine, false));
        assertEquals(TaiPressureWatch.Tier.CHAT, tier(chatLine - 1L, false));
        assertEquals(TaiPressureWatch.Tier.CHAT, tier(200L * MB, false));
    }

    @Test
    public void lowMemoryIsTheLastTierWhateverTheNumbersSay() {
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, tier(5_800L * MB, true));
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, tier(0L, true));
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, TaiPressureWatch.tier(100L * MB, 0L, 0L, true));
    }

    @Test
    public void unknownFreeMemorySelectsNothingAndAnUnknownThresholdHasNoChatLine() {
        assertEquals(TaiPressureWatch.Tier.NONE, tier(0L, false));
        assertEquals(TaiPressureWatch.Tier.NONE, tier(-1L, false));
        // Threshold unknown: the floor is the old reserve (1.5 GiB on this phone), and idle chat is
        // never given up on a number that does not exist.
        long oldReserve = TaiLoadBudget.floorBytes(0L, 11_530_736L * 1024L);
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, TaiPressureWatch.tier(oldReserve - 1L, oldReserve, 0L, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, TaiPressureWatch.tier(100L * MB, oldReserve, 0L, false));
    }

    @Test
    public void onlyTheRunningTrimLevelsMapToTiers() {
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
        assertEquals(TaiPressureWatch.Tier.CHAT, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE));
        // Lifecycle, not pressure: backgrounding the launcher must not drop a warm model.
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN));
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND));
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_MODERATE));
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tierForTrimLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE));
    }

    // --- victims -------------------------------------------------------------------------------

    @Test
    public void tierOneGivesUpTheLeastRecentlyUsedEmbeddingThenSttAndNeverChat() {
        TaiResidency.Entry olderEmbedding = embedding("qwen3-embedding", NOW - 60_000L);
        TaiResidency.Entry newerEmbedding = embedding("embeddinggemma", NOW - 10_000L);
        TaiResidency.Entry stt = stt("whisper-small", NOW - 120_000L, false);
        TaiResidency.Entry chat = chat("e4b", NOW - 3_600_000L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(chat, runtime(), newerEmbedding, stt, olderEmbedding);

        assertSame(olderEmbedding, TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.AUXILIARY));
        assertSame(newerEmbedding, TaiPressureWatch.nextVictim(without(residents, olderEmbedding), TaiPressureWatch.Tier.AUXILIARY));
        assertSame(stt, TaiPressureWatch.nextVictim(Arrays.asList(chat, runtime(), stt), TaiPressureWatch.Tier.AUXILIARY));
        // Chat is the oldest resident by far and still not tier one's to give.
        assertNull(TaiPressureWatch.nextVictim(Arrays.asList(chat, runtime()), TaiPressureWatch.Tier.AUXILIARY));
    }

    @Test
    public void tierTwoReachesIdleChatAfterTheOthers() {
        TaiResidency.Entry embedding = embedding("embeddinggemma", NOW - 10_000L);
        TaiResidency.Entry chat = chat("e4b", NOW - 3_600_000L, false);

        assertSame(embedding, TaiPressureWatch.nextVictim(Arrays.asList(chat, embedding, runtime()), TaiPressureWatch.Tier.CHAT));
        assertSame(chat, TaiPressureWatch.nextVictim(Arrays.asList(chat, runtime()), TaiPressureWatch.Tier.CHAT));
    }

    @Test
    public void busyResidentsAndTheBaselineAreNeverVictims() {
        TaiResidency.Entry busyEmbedding = new TaiResidency.Entry("embeddinggemma", TaiResidency.Kind.EMBEDDING,
            TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1024, 227L * MB, null, NOW - 60_000L, true);
        TaiResidency.Entry busyChat = chat("e4b", NOW, true);
        TaiResidency.Entry busyStt = stt("whisper-small", NOW - 60_000L, true);
        List<TaiResidency.Entry> residents = Arrays.asList(busyChat, busyEmbedding, busyStt, runtime());

        assertNull(TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.AUXILIARY));
        assertNull(TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.CHAT));
        assertNull(TaiPressureWatch.nextVictim(Collections.singletonList(runtime()), TaiPressureWatch.Tier.CHAT));
    }

    @Test
    public void releaseAllAndNoneNameNoVictim() {
        List<TaiResidency.Entry> residents = Arrays.asList(embedding("embeddinggemma", NOW - 60_000L), runtime());
        assertNull(TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.RELEASE_ALL));
        assertNull(TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.NONE));
    }

    // --- idle timers ---------------------------------------------------------------------------

    @Test
    public void embeddingsExpireAtFiveMinutesAndSttAtTwo() {
        TaiResidency.Entry embeddingAtFour = embedding("four-minutes", NOW - 4L * 60_000L);
        TaiResidency.Entry embeddingAtFive = embedding("five-minutes", NOW - 5L * 60_000L);
        TaiResidency.Entry sttAtOne = stt("one-minute", NOW - 60_000L, false);
        TaiResidency.Entry sttAtTwo = stt("two-minutes", NOW - 2L * 60_000L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(sttAtTwo, embeddingAtFour, embeddingAtFive, sttAtOne, runtime());

        List<TaiResidency.Entry> expired = TaiPressureWatch.idleExpired(residents, NOW);

        assertEquals(Arrays.asList(embeddingAtFive, sttAtTwo), expired);
        assertEquals(TaiPressureWatch.EMBEDDING_IDLE_MS, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.EMBEDDING));
        assertEquals(TaiPressureWatch.STT_IDLE_MS, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.STT));
    }

    /** The STT limit follows the settings value the runtime process was last sent; 0 turns the timer off. */
    @Test
    public void theSttIdleLimitComesFromSettings() {
        TaiResidency.Entry sttAtThree = stt("three-minutes", NOW - 3L * 60_000L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(sttAtThree, runtime());

        assertEquals(Collections.singletonList(sttAtThree), TaiPressureWatch.idleExpired(residents, NOW));
        assertTrue(TaiPressureWatch.idleExpired(residents, NOW, 5L * 60_000L).isEmpty());
        assertEquals(Collections.singletonList(sttAtThree), TaiPressureWatch.idleExpired(residents, NOW, 3L * 60_000L));
        assertTrue("0 means never on idle", TaiPressureWatch.idleExpired(residents, NOW, 0L).isEmpty());
        assertEquals(5L * 60_000L, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.STT, 5L * 60_000L));
        assertEquals(TaiPressureWatch.EMBEDDING_IDLE_MS, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.EMBEDDING, 5L * 60_000L));
    }

    @Test
    public void chatAndTheBaselineHaveNoWatchTimerAndBusyResidentsNeverExpire() {
        TaiResidency.Entry chat = chat("e4b", NOW - 24L * 3_600_000L, false);
        TaiResidency.Entry busyEmbedding = new TaiResidency.Entry("embeddinggemma", TaiResidency.Kind.EMBEDDING,
            TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1024, 227L * MB, null, NOW - 3_600_000L, true);
        TaiResidency.Entry busyStt = stt("whisper-small", NOW - 3_600_000L, true);

        assertTrue(TaiPressureWatch.idleExpired(Arrays.asList(chat, busyEmbedding, busyStt, runtime()), NOW).isEmpty());
        assertEquals(0L, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.CHAT));
        assertEquals(0L, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.RUNTIME));
    }

    // --- idle exit -----------------------------------------------------------------------------

    @Test
    public void theProcessExitsTenMinutesAfterTheLastModelLeftWithNothingInFlight() {
        List<TaiResidency.Entry> baselineOnly = Collections.singletonList(runtime());
        long tenMinutesAgo = NOW - TaiPressureWatch.IDLE_EXIT_MS;

        assertTrue(TaiPressureWatch.idleExitDue(baselineOnly, 0, tenMinutesAgo, NOW));
        assertFalse("nine minutes is not ten", TaiPressureWatch.idleExitDue(baselineOnly, 0, tenMinutesAgo + 60_000L, NOW));
        assertFalse("a request is being served", TaiPressureWatch.idleExitDue(baselineOnly, 1, tenMinutesAgo, NOW));
        assertFalse("no activity was ever stamped", TaiPressureWatch.idleExitDue(baselineOnly, 0, 0L, NOW));
    }

    @Test
    public void anyResidentModelOrAnEmptyRegistryHoldsTheProcess() {
        long tenMinutesAgo = NOW - TaiPressureWatch.IDLE_EXIT_MS;
        assertFalse(TaiPressureWatch.idleExitDue(Arrays.asList(runtime(), embedding("embeddinggemma", tenMinutesAgo)), 0, tenMinutesAgo, NOW));
        assertFalse(TaiPressureWatch.idleExitDue(Arrays.asList(runtime(), chat("e4b", tenMinutesAgo, false)), 0, tenMinutesAgo, NOW));
        // Never loaded a chat model: there is no baseline to give back, so nothing to exit for.
        assertFalse(TaiPressureWatch.idleExitDue(Collections.<TaiResidency.Entry>emptyList(), 0, tenMinutesAgo, NOW));
    }

    // --- fixtures ------------------------------------------------------------------------------

    private static TaiPressureWatch.Tier tier(long availMem, boolean lowMemory) {
        return TaiPressureWatch.tier(availMem, PONG_FLOOR, PONG_THRESHOLD, lowMemory);
    }

    private static TaiResidency.Entry embedding(String id, long lastUsedMs) {
        return new TaiResidency.Entry(id, TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1024,
            227L * MB, null, lastUsedMs, false);
    }

    private static TaiResidency.Entry stt(String id, long lastUsedMs, boolean busy) {
        return new TaiResidency.Entry(id, TaiResidency.Kind.STT, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 30,
            500L * MB, null, lastUsedMs, busy);
    }

    private static TaiResidency.Entry chat(String id, long lastUsedMs, boolean busy) {
        return new TaiResidency.Entry(id, TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096,
            3_500L * MB, null, lastUsedMs, busy);
    }

    private static TaiResidency.Entry runtime() {
        return new TaiResidency.Entry(TaiResidency.RUNTIME_ID, TaiResidency.Kind.RUNTIME, "process", "cpu", 0,
            TaiResidency.RUNTIME_BASELINE_BYTES, null, NOW - 3_600_000L, false);
    }

    private static List<TaiResidency.Entry> without(List<TaiResidency.Entry> residents, TaiResidency.Entry removed) {
        List<TaiResidency.Entry> rest = new java.util.ArrayList<>(residents);
        rest.remove(removed);
        return rest;
    }
}
