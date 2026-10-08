package com.termux.ai;

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
 * The watch's decisions from a table. The floors are the relaxed memory limits': hold 0.75 GiB
 * (tier 1), peak 0.5 GiB (tier 2), the same on every RAM class; unrestricted has none.
 */
public class TaiPressureWatchTest {

    private static final long MB = 1024L * 1024L;
    private static final long HOLD = TaiLoadBudget.floorBytes(false, TaiLoadBudget.Conditions.RELAXED);
    private static final long PEAK = TaiLoadBudget.floorBytes(true, TaiLoadBudget.Conditions.RELAXED);
    private static final long NOW = 1_700_000_000_000L;

    // --- tiers ---------------------------------------------------------------------------------

    /** Tier 1 below the hold floor gives up idle auxiliaries; tier 2 below the peak floor gives up idle chat too. */
    @Test
    public void tierFollowsTheHoldAndPeakFloorsFromTheTopDown() {
        assertEquals(768L * MB, HOLD);
        assertEquals(512L * MB, PEAK);
        assertEquals(TaiPressureWatch.Tier.NONE, tier(5_800L * MB, false));
        assertEquals(TaiPressureWatch.Tier.NONE, tier(1000L * MB, false));
        assertEquals(TaiPressureWatch.Tier.NONE, tier(HOLD, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(HOLD - 1L, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(600L * MB, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(PEAK, false));
        assertEquals(TaiPressureWatch.Tier.CHAT, tier(PEAK - 1L, false));
        assertEquals(TaiPressureWatch.Tier.CHAT, tier(200L * MB, false));
    }

    /**
     * The 1.25 x threshold line (394 MB on pong, 315 MB threshold) that used to give up chat is
     * gone: 400 MB free is tier 2 because it is under the peak floor, and 700 MB is only tier 1.
     */
    @Test
    public void theOldThresholdLineIsGone() {
        assertEquals(TaiPressureWatch.Tier.CHAT, tier(400L * MB, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, tier(700L * MB, false));
    }

    @Test
    public void lowMemoryIsTheLastTierWhateverTheNumbersSay() {
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, tier(5_800L * MB, true));
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, tier(0L, true));
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, TaiPressureWatch.tier(100L * MB, 0L, 0L, true));
    }

    @Test
    public void unknownFreeMemorySelectsNothingAndAnUnknownFloorIsNoLine() {
        assertEquals(TaiPressureWatch.Tier.NONE, tier(0L, false));
        assertEquals(TaiPressureWatch.Tier.NONE, tier(-1L, false));
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tier(100L * MB, 0L, 0L, false));
        assertEquals(TaiPressureWatch.Tier.AUXILIARY, TaiPressureWatch.tier(100L * MB, HOLD, 0L, false));
    }

    /**
     * Unrestricted, the gate admits loads below both relaxed floors, so the watch must not hold
     * those lines either or it would unload what was just admitted: only lowMemory acts.
     */
    @Test
    public void unrestrictedLimitsLeaveTheWatchOnlyLowMemory() {
        long hold = TaiLoadBudget.floorBytes(false, TaiLoadBudget.Conditions.UNRESTRICTED);
        long peak = TaiLoadBudget.floorBytes(true, TaiLoadBudget.Conditions.UNRESTRICTED);
        assertEquals(0L, hold);
        assertEquals(0L, peak);
        assertEquals(TaiPressureWatch.Tier.NONE, TaiPressureWatch.tier(100L * MB, hold, peak, false));
        assertEquals(TaiPressureWatch.Tier.RELEASE_ALL, TaiPressureWatch.tier(100L * MB, hold, peak, true));
    }

    /** 250 ms while a load or first prefill runs, 2 s otherwise. */
    @Test
    public void theWatchPollsFasterWhileALoadOrFirstPrefillRuns() {
        assertEquals(250L, TaiPressureWatch.pollIntervalMs(true));
        assertEquals(2_000L, TaiPressureWatch.pollIntervalMs(false));
        assertTrue(TaiPressureWatch.POLL_BUSY_MS < TaiPressureWatch.POLL_IDLE_MS);
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
        return TaiPressureWatch.tier(availMem, HOLD, PEAK, lowMemory);
    }

    /**
     * Feature groups (decision 8): a member used within two minutes is passed over by both eviction
     * tiers; past that, or with no group, the order is as before.
     */
    @Test
    public void theEvictionTiersPassOverAGroupInUse() {
        TaiResidency.Entry dictating = stt("whisper", NOW - 60_000L, false);
        TaiResidency.Entry search = embedding("embedding-gemma", NOW - 30_000L);
        TaiResidency.Entry assistant = chat("gemma", NOW - 10_000L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(search, dictating, assistant, runtime());
        assertSame(assistant, TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.CHAT, NOW));
        assertNull(TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.AUXILIARY, NOW));
        // Three minutes on, the group is no longer in use.
        assertSame(search, TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.AUXILIARY, NOW + 180_000L));
        // The plain overload keeps no group.
        assertSame(search, TaiPressureWatch.nextVictim(residents, TaiPressureWatch.Tier.AUXILIARY));
        // Cleanup's chat model is a group member too, once a request named it.
        TaiResidency.Entry cleanup = new TaiResidency.Entry("gemma", TaiResidency.Kind.CHAT, "litert-lm", "gpu", 2048,
            3000L * MB, null, NOW - 10_000L, false, TaiFunction.TIDY_DICTATION);
        assertNull(TaiPressureWatch.nextVictim(Arrays.asList(cleanup, runtime()), TaiPressureWatch.Tier.CHAT, NOW));
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
