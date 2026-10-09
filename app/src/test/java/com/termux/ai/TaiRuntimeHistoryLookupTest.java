package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the measured-load history answers for a window it has not seen: the ring of samples with
 * the window inside them, the lookup order (a) nearest window at or above, (b) a fit, (c) one
 * sample plus the seed slope, (d) nothing, the vision fallback to the text key, and expiry.
 */
public class TaiRuntimeHistoryLookupTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final String LOAD = TaiRuntimeHistory.PHASE_LOAD;
    private static final String PREFILL = TaiRuntimeHistory.PHASE_FIRST_PREFILL;
    /** The seed slope of E4B: file / 19000 is about 190 KB per token. */
    private static final long SEED_SLOPE = 192_606L;

    private static TaiRuntimeHistory.Sample sample(int window, long drop) {
        return new TaiRuntimeHistory.Sample(window, drop, 0, LOAD, NOW - DAY);
    }

    private static TaiRuntimeHistory.Sample sample(int window, long drop, String phase, long ageMs) {
        return new TaiRuntimeHistory.Sample(window, drop, 512, phase, NOW - ageMs);
    }

    private static long lookup(int window, TaiRuntimeHistory.Sample... samples) {
        return TaiRuntimeHistory.lookup(Arrays.asList(samples), window, SEED_SLOPE, NOW);
    }

    // --- (a) the smallest measured window at or above ---

    /** The director asks for 2048; the 11 E4B GPU samples on file were chat loads at 4096. */
    @Test
    public void aLargerWindowsSampleIsAnUpperBoundForASmallerRequest() {
        assertEquals(3_200_000_000L, lookup(2048, sample(4096, 3_000_000_000L), sample(4096, 3_200_000_000L),
            sample(4096, 2_400_000_000L)));
        assertEquals(3_200_000_000L, lookup(4096, sample(4096, 3_200_000_000L)));
    }

    @Test
    public void theSmallestWindowAtOrAboveWinsOverALargerOne() {
        TaiRuntimeHistory.Sample small = sample(2048, 2_700_000_000L);
        TaiRuntimeHistory.Sample mid = sample(4096, 3_200_000_000L);
        TaiRuntimeHistory.Sample large = sample(8192, 3_900_000_000L);
        assertEquals(2_700_000_000L, lookup(2048, small, mid, large));
        assertEquals(2_700_000_000L, lookup(1024, small, mid, large));
        assertEquals(3_200_000_000L, lookup(3000, small, mid, large));
        assertEquals(3_900_000_000L, lookup(8192, small, mid, large));
    }

    /** Within the chosen window the largest sample counts: identical loads spread over ±0.9 GB. */
    @Test
    public void theLargestSampleAtTheChosenWindowCounts() {
        assertEquals(4_200_000_000L, lookup(4096, sample(4096, 3_000_000_000L), sample(4096, 4_200_000_000L),
            sample(4096, 2_400_000_000L)));
    }

    // --- (b) a line through the per-window maxima ---

    @Test
    public void twoWindowsBelowTheRequestGiveAFittedLine() {
        // 2048 -> 2.6 GB and 4096 -> 3.0 GB is 195,312.5 bytes a token, 2.2 GB fixed.
        assertEquals(3_800_000_000L, lookup(8192, sample(2048, 2_600_000_000L), sample(4096, 3_000_000_000L)));
        // Per-window maxima, not every sample: a lower one at 4096 does not bend the line.
        assertEquals(3_800_000_000L, lookup(8192, sample(2048, 2_600_000_000L), sample(4096, 2_000_000_000L),
            sample(4096, 3_000_000_000L)));
    }

    @Test
    public void aFitThatSlopesDownFallsBackToTheSeedSlopeFromTheLargestWindow() {
        long result = lookup(6144, sample(2048, 3_000_000_000L), sample(4096, 2_800_000_000L));
        assertEquals(2_800_000_000L + SEED_SLOPE * 2048L, result);
    }

    // --- (c) one window, below ---

    @Test
    public void oneWindowBelowTheRequestIsCarriedUpBySeedSlope() {
        assertEquals(3_000_000_000L + SEED_SLOPE * 4096L, lookup(8192, sample(4096, 3_000_000_000L)));
        // Without a seed slope the sample itself is the best bound there is.
        assertEquals(3_000_000_000L, TaiRuntimeHistory.lookup(Collections.singletonList(sample(4096, 3_000_000_000L)),
            8192, 0L, NOW));
    }

    // --- (d) nothing ---

    @Test
    public void nothingMeasuredIsZeroAndTheCallerUsesItsSeed() {
        assertEquals(0L, lookup(4096));
        assertEquals(0L, lookup(4096, sample(4096, 0L)));
        assertEquals(0L, TaiRuntimeHistory.lookup(Collections.<TaiRuntimeHistory.Sample>emptyList(), 0, SEED_SLOPE, NOW));
    }

    /** Non-LLM loads (embeddings, speech) record no window: window 0 meets window 0. */
    @Test
    public void aFixedLoadWithNoWindowIsFoundAtWindowZero() {
        assertEquals(240_000_000L, lookup(0, sample(0, 200_000_000L), sample(0, 240_000_000L)));
    }

    // --- phases ---

    /** The first request is where a load's real cost lands, so first_prefill samples are planned on when there are any. */
    @Test
    public void firstPrefillSamplesReplaceLoadSamplesWhenTheKeyHasAny() {
        TaiRuntimeHistory.Sample load = sample(4096, 3_000_000_000L, LOAD, DAY);
        TaiRuntimeHistory.Sample prefill = sample(4096, 3_600_000_000L, PREFILL, DAY);
        assertEquals(3_600_000_000L, lookup(4096, load, prefill));
        assertEquals(3_000_000_000L, lookup(4096, load));
        // A load sample at a window the prefill ones lack is not mixed in.
        TaiRuntimeHistory.Sample load2k = sample(2048, 2_000_000_000L, LOAD, DAY);
        assertEquals(3_600_000_000L, lookup(2048, load2k, load, prefill));
    }

    // --- expiry ---

    @Test
    public void samplesOlderThanThirtyDaysDoNotCount() {
        long old = TaiRuntimeHistory.SAMPLE_TTL_MS + 1L;
        assertEquals(0L, lookup(4096, sample(4096, 5_000_000_000L, LOAD, old)));
        assertEquals(3_000_000_000L, lookup(4096, sample(4096, 5_000_000_000L, LOAD, old), sample(4096, 3_000_000_000L)));
        // Just inside the TTL still counts: one bad sample does not ratchet the estimate for ever.
        assertEquals(5_000_000_000L, lookup(4096, sample(4096, 5_000_000_000L, LOAD, TaiRuntimeHistory.SAMPLE_TTL_MS)));
    }

    // --- the ring ---

    private static TaiRuntimeHistory.Sample at(int window, long drop, long ageMs) {
        return new TaiRuntimeHistory.Sample(window, drop, 0, LOAD, NOW - ageMs);
    }

    @Test
    public void theRingKeepsTheLastEightSamples() throws Exception {
        JSONObject entry = new JSONObject();
        for (int i = 1; i <= 11; i++) {
            TaiRuntimeHistory.appendSample(entry, at(4096, i * 100_000_000L, (12 - i) * 60_000L), NOW, 7L);
        }
        List<TaiRuntimeHistory.Sample> kept = TaiRuntimeHistory.entrySamples(entry);
        assertEquals(TaiRuntimeHistory.RING_SIZE, kept.size());
        assertEquals(8, kept.size());
        // The first three left; the oldest of the rest is the fourth.
        assertEquals(400_000_000L, kept.get(0).dropBytes);
        assertEquals(1_100_000_000L, kept.get(7).dropBytes);
        assertEquals(7L, entry.getLong("appVersionCode"));
        assertEquals(NOW, entry.getLong("updatedAtMs"));
    }

    /** A big old sample leaves with age and is not held up by the ring: the ratchet is gone. */
    @Test
    public void expiredSamplesLeaveTheRingWhenANewOneIsAdded() throws Exception {
        JSONObject entry = new JSONObject();
        TaiRuntimeHistory.appendSample(entry, at(4096, 4_200_000_000L, TaiRuntimeHistory.SAMPLE_TTL_MS + 5L * DAY), NOW - 6L * DAY, 7L);
        TaiRuntimeHistory.appendSample(entry, at(4096, 3_000_000_000L, DAY), NOW, 7L);
        List<TaiRuntimeHistory.Sample> kept = TaiRuntimeHistory.entrySamples(entry);
        assertEquals(1, kept.size());
        assertEquals(3_000_000_000L, kept.get(0).dropBytes);
    }

    @Test
    public void anAppVersionChangeStartsTheRingOver() throws Exception {
        JSONObject entry = new JSONObject();
        TaiRuntimeHistory.appendSample(entry, at(4096, 4_200_000_000L, DAY), NOW, 7L);
        TaiRuntimeHistory.appendSample(entry, at(4096, 3_000_000_000L, 0L), NOW, 8L);
        List<TaiRuntimeHistory.Sample> kept = TaiRuntimeHistory.entrySamples(entry);
        assertEquals(1, kept.size());
        assertEquals(3_000_000_000L, kept.get(0).dropBytes);
        assertEquals(8L, entry.getLong("appVersionCode"));
        // An unknown running version (0) keeps what is there, as failure records do.
        TaiRuntimeHistory.appendSample(entry, at(4096, 3_100_000_000L, 0L), NOW, 0L);
        assertEquals(2, TaiRuntimeHistory.entrySamples(entry).size());
    }

    @Test
    public void aSampleSurvivesTheRoundTripThroughJson() throws Exception {
        JSONObject entry = new JSONObject();
        TaiRuntimeHistory.appendSample(entry,
            new TaiRuntimeHistory.Sample(2048, 3_070_000_000L, 1_300, PREFILL, NOW), NOW, 7L);
        TaiRuntimeHistory.Sample read = TaiRuntimeHistory.entrySamples(new JSONObject(entry.toString())).get(0);
        assertEquals(2048, read.window);
        assertEquals(3_070_000_000L, read.dropBytes);
        assertEquals(1_300, read.promptTokens);
        assertEquals(PREFILL, read.phase);
        assertEquals(NOW, read.timestampMs);
    }

    // --- old and damaged records ---

    /** The format before this one: one bucketed worst case, no samples. It reads as nothing and does not crash. */
    @Test
    public void anOldRecordReadsAsNoSamples() throws Exception {
        JSONObject legacy = new JSONObject().put("modelId", "gemma-4-e4b").put("bytes", 4_200_000_000L)
            .put("lastBytes", 3_000_000_000L).put("samples", 5).put("contextBucket", 4096).put("updatedAtMs", NOW);
        assertTrue(TaiRuntimeHistory.entrySamples(legacy).isEmpty());
        assertTrue(TaiRuntimeHistory.entrySamples(null).isEmpty());
        assertTrue(TaiRuntimeHistory.entrySamples(new JSONObject()).isEmpty());
    }

    @Test
    public void damagedSamplesAreSkippedNotThrown() throws Exception {
        JSONArray samples = new JSONArray()
            .put("not an object")
            .put(new JSONObject().put("w", "wide").put("d", "lots").put("t", NOW))
            .put(new JSONObject().put("w", 4096).put("d", -5).put("t", NOW))
            .put(new JSONObject().put("w", 4096).put("d", 3_000_000_000L))
            .put(new JSONObject().put("w", 4096).put("d", 3_100_000_000L).put("t", NOW).put("ph", "load"));
        List<TaiRuntimeHistory.Sample> kept = TaiRuntimeHistory.entrySamples(new JSONObject().put("samples", samples));
        assertEquals(1, kept.size());
        assertEquals(3_100_000_000L, kept.get(0).dropBytes);
    }

    @Test
    public void pruningDropsTheOldFormatOtherVersionsAndExpiredRecordsOnly() throws Exception {
        JSONObject history = new JSONObject();
        history.put("load|gemma-4-e4b|dev|litert-lm|gpu|4096", new JSONObject().put("modelId", "gemma-4-e4b").put("bytes", 4_200_000_000L));
        history.put("gemma-4-e4b|dev|gpu", new JSONObject().put("modelId", "gemma-4-e4b").put("success", true));
        history.put("system_role|gemma-4-e4b", new JSONObject().put("modelId", "gemma-4-e4b").put("success", false));

        JSONObject live = new JSONObject().put("modelId", "gemma-4-e4b");
        TaiRuntimeHistory.appendSample(live, at(4096, 3_000_000_000L, DAY), NOW, 7L);
        history.put("load2|live", live);
        JSONObject otherVersion = new JSONObject().put("modelId", "gemma-4-e4b");
        TaiRuntimeHistory.appendSample(otherVersion, at(4096, 3_000_000_000L, DAY), NOW, 6L);
        history.put("load2|other-version", otherVersion);
        JSONObject expired = new JSONObject().put("modelId", "gemma-4-e4b");
        TaiRuntimeHistory.appendSample(expired, at(4096, 3_000_000_000L, 40L * DAY), NOW - 40L * DAY, 7L);
        history.put("load2|expired", expired);
        history.put("load2|empty", new JSONObject().put("modelId", "gemma-4-e4b"));

        assertEquals(4, TaiRuntimeHistory.pruneMeasured(history, NOW, 7L));
        assertFalse(history.has("load|gemma-4-e4b|dev|litert-lm|gpu|4096"));
        assertFalse(history.has("load2|other-version"));
        assertFalse(history.has("load2|expired"));
        assertFalse(history.has("load2|empty"));
        assertTrue(history.has("load2|live"));
        assertTrue(history.has("gemma-4-e4b|dev|gpu"));
        assertTrue(history.has("system_role|gemma-4-e4b"));
    }

    // --- modality and the vision fallback ---

    @Test
    public void modalityComesFromTheVariantSuffix() {
        assertEquals("text", TaiRuntimeHistory.modalityOf("gemma-4-e4b-it-litert-lm"));
        assertEquals("vision", TaiRuntimeHistory.modalityOf("gemma-4-e4b-it-litert-lm-vision"));
        assertEquals("audio", TaiRuntimeHistory.modalityOf("gemma-4-e4b-it-litert-lm-audio"));
        assertEquals("text", TaiRuntimeHistory.modalityOf("gemma-4-e4b-it-litert-lm-text"));
    }

    private static List<TaiRuntimeHistory.Sample> list(TaiRuntimeHistory.Sample... samples) {
        return new ArrayList<>(Arrays.asList(samples));
    }

    /** A vision key with no samples starts from the text key's, plus the encoders' share, never without it. */
    @Test
    public void aVisionKeyWithNoSamplesFallsBackToTheTextKeyPlusTheEncoderDelta() {
        long delta = 365_000_000L;
        List<TaiRuntimeHistory.Sample> text = list(sample(4096, 3_000_000_000L));
        assertEquals(3_000_000_000L + delta, TaiRuntimeHistory.lookupWithVariantFallback(
            Collections.<TaiRuntimeHistory.Sample>emptyList(), "vision", () -> text, 2048, SEED_SLOPE, delta, NOW));
        assertEquals(3_000_000_000L + delta, TaiRuntimeHistory.lookupWithVariantFallback(
            Collections.<TaiRuntimeHistory.Sample>emptyList(), "audio", () -> text, 4096, SEED_SLOPE, delta, NOW));
        // No text samples either: the caller's seed.
        assertEquals(0L, TaiRuntimeHistory.lookupWithVariantFallback(Collections.<TaiRuntimeHistory.Sample>emptyList(),
            "vision", Collections::<TaiRuntimeHistory.Sample>emptyList, 2048, SEED_SLOPE, delta, NOW));
    }

    /** Once the vision key has a sample it is used on its own: no delta on top, and the text key is not consulted. */
    @Test
    public void aVisionKeyWithSamplesIgnoresTheTextKey() {
        List<TaiRuntimeHistory.Sample> vision = list(sample(4096, 3_400_000_000L));
        List<TaiRuntimeHistory.Sample> text = list(sample(4096, 9_000_000_000L));
        assertEquals(3_400_000_000L, TaiRuntimeHistory.lookupWithVariantFallback(vision, "vision", () -> text, 4096,
            SEED_SLOPE, 365_000_000L, NOW));
    }

    /** The reverse is never done: a text key with no samples does not borrow the vision key's. */
    @Test
    public void aTextKeyNeverBorrowsFromTheVisionKey() {
        assertEquals(0L, TaiRuntimeHistory.lookupWithVariantFallback(Collections.<TaiRuntimeHistory.Sample>emptyList(),
            "text", () -> list(sample(4096, 3_400_000_000L)), 4096, SEED_SLOPE, 365_000_000L, NOW));
    }

    /** The director's 2048 vision request uses the 4096 GPU samples at once: 3.07 GB, then +10 % in the budget. */
    @Test
    public void theDirectorsVisionRequestAt2048UsesThe4096GpuSamples() {
        List<TaiRuntimeHistory.Sample> vision = list(sample(4096, 3_070_000_000L), sample(4096, 2_760_000_000L));
        long measured = TaiRuntimeHistory.lookupWithVariantFallback(vision, "vision",
            Collections::<TaiRuntimeHistory.Sample>emptyList, 2048, SEED_SLOPE, 365_000_000L, NOW);
        assertEquals(3_070_000_000L, measured);
        assertEquals(3_377_000_000L, TaiLoadBudget.Estimate.measured(measured).nonReclaimableBytes);
        // Once a 2048 sample exists, that one is used instead.
        vision.add(sample(2048, 2_700_000_000L));
        assertEquals(2_700_000_000L, TaiRuntimeHistory.lookupWithVariantFallback(vision, "vision",
            Collections::<TaiRuntimeHistory.Sample>emptyList, 2048, SEED_SLOPE, 365_000_000L, NOW));
    }
}
