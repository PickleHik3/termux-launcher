package com.termux.ai;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Memory-mode choice and the image kind's place in the residency table. Sizes are the published
 * packages': Stable Diffusion 1.5 is 1.15 GB; the phone is pong (12 GB class, 600 MB low-memory threshold).
 */
@RunWith(RobolectricTestRunner.class)
public class TaiImageAdmissionTest {
    private static final long PONG_TOTAL = 11_530_736L * 1024L;
    private static final long THRESHOLD = 600L * 1024L * 1024L;
    private static final long SD15 = 1_150_000_000L;
    private static final long GB = 1024L * 1024L * 1024L;
    private static final TaiImageAdmission.Measured UNKNOWN = mode -> 0L;

    private static TaiImageAdmission.Decision decide(long peak, int requested, long available,
                                                      List<TaiResidency.Entry> evictable, TaiImageAdmission.Measured measured) {
        return TaiImageAdmission.decide(peak, requested, "opencl", PONG_TOTAL, available, THRESHOLD, evictable, measured);
    }

    @Test
    public void theEstimateFactorsFallWithTheMemorySaving() {
        assertEquals(SD15 * 15L / 10L, TaiResidency.imageEstimateBytes(SD15, 1));
        assertEquals(SD15 * 11L / 10L, TaiResidency.imageEstimateBytes(SD15, 2));
        assertEquals(SD15 * 8L / 10L, TaiResidency.imageEstimateBytes(SD15, 0));
    }

    @Test
    public void plentyOfMemoryPicksTheFastestMode() {
        TaiImageAdmission.Decision d = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, 6 * GB, Collections.emptyList(), UNKNOWN);
        assertTrue(d.fits);
        assertEquals(1, d.memoryMode);
        assertTrue(d.plan.evicted.isEmpty());
    }

    @Test
    public void mode1NeedingTheChatModelEvictedStillWinsWhileItFits() {
        TaiResidency.Entry chat = chat(3 * GB);
        // Free memory alone is short for mode 1 (1.5x) but closing idle chat makes it fit.
        long available = 2 * GB;
        TaiImageAdmission.Decision d = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, available, Collections.singletonList(chat), UNKNOWN);
        assertTrue(d.fits);
        assertEquals(1, d.memoryMode);
        assertEquals(Collections.singletonList(chat), d.plan.evicted);
    }

    /** What the budget asks free memory to cover for a ratio estimate: the estimate plus the 25% margin. */
    private static long need(int mode) {
        return TaiResidency.imageEstimateBytes(SD15, mode) * 125L / 100L;
    }

    private static long reserve() {
        return TaiLoadBudget.holdFloorBytes(TaiLoadBudget.ramClassBytes(PONG_TOTAL));
    }

    @Test
    public void whenMode1DoesNotFitTheBalanceModeIsTriedThenSaving() {
        assertTrue(need(1) > need(2) && need(2) > need(0));
        TaiImageAdmission.Decision balance = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, reserve() + need(2) + 1L,
            Collections.emptyList(), UNKNOWN);
        assertTrue(balance.fits);
        assertEquals(2, balance.memoryMode);

        TaiImageAdmission.Decision saving = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, reserve() + need(0) + 1L,
            Collections.emptyList(), UNKNOWN);
        assertTrue(saving.fits);
        assertEquals(0, saving.memoryMode);
    }

    @Test
    public void noModeFittingIsRefused() {
        TaiImageAdmission.Decision d = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, 400L * 1024L * 1024L, Collections.emptyList(), UNKNOWN);
        assertFalse(d.fits);
        assertEquals(0, d.memoryMode);
    }

    @Test
    public void aForcedModeIsJudgedAlone() {
        long available = reserve() + need(2) + 1L;
        assertFalse(decide(SD15, 1, available, Collections.emptyList(), UNKNOWN).fits);
        TaiImageAdmission.Decision forced = decide(SD15, 2, available, Collections.emptyList(), UNKNOWN);
        assertTrue(forced.fits);
        assertEquals(2, forced.memoryMode);
    }

    @Test
    public void aMeasuredLoadReplacesTheRatioEstimate() {
        // The measured worst case for mode 1 is small, so mode 1 fits where the 1.5x ratio would not.
        long available = reserve() + 400L * 1024L * 1024L;
        assertFalse(decide(SD15, 1, available, Collections.emptyList(), UNKNOWN).fits);
        TaiImageAdmission.Decision d = decide(SD15, 1, available, Collections.emptyList(), mode -> mode == 1 ? 300L * 1024L * 1024L : 0L);
        assertTrue(d.fits);
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, d.plan.estimateSource);
    }

    @Test
    public void unknownFreeMemoryGoesAheadInTheFastestMode() {
        TaiImageAdmission.Decision d = decide(SD15, TaiImageRequest.MEMORY_MODE_AUTO, 0L, Collections.emptyList(), UNKNOWN);
        assertTrue(d.fits);
        assertEquals(1, d.memoryMode);
    }

    @Test
    public void onlyModeOneStableDiffusionStaysResident() {
        assertTrue(TaiImageAdmission.staysResident(1, TaiDiffusionPackage.TYPE_SD15));
        assertTrue(TaiImageAdmission.staysResident(1, TaiDiffusionPackage.TYPE_TAIYI));
        assertFalse(TaiImageAdmission.staysResident(2, TaiDiffusionPackage.TYPE_SD15));
        assertFalse(TaiImageAdmission.staysResident(0, TaiDiffusionPackage.TYPE_SD15));
        assertFalse(TaiImageAdmission.staysResident(1, TaiDiffusionPackage.TYPE_SANA));
    }

    // --- TaiResidency: the IMAGE kind ---

    @Test
    public void anImageLoadIsCreditedItsOwnResidentAndMayEvictEveryIdleAuxiliaryAndChat() {
        TaiResidency.Entry image = TaiResidency.Entry.image("sd15", "opencl", 1, 2 * GB);
        TaiResidency.Entry chat = chat(3 * GB);
        TaiResidency.Entry stt = new TaiResidency.Entry("whisper", TaiResidency.Kind.STT, "litert-lm", "cpu", 10, 200L, null, 3L, false);
        TaiResidency.Entry tts = new TaiResidency.Entry("kitten", TaiResidency.Kind.TTS, "litert-lm", "cpu", 0, 100L, null, 2L, false);
        TaiResidency.Entry emb = new TaiResidency.Entry("emb", TaiResidency.Kind.EMBEDDING, "litert-lm", "cpu", 0, 50L, null, 1L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(image, chat, stt, tts, emb);

        assertEquals(5 * GB + image.bytes(), TaiResidency.creditedAvailable(5 * GB, residents, TaiResidency.Kind.IMAGE, null));
        assertEquals(Arrays.asList(emb, tts, stt, chat),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.IMAGE, null));
    }

    @Test
    public void otherLoadsMayCloseAnIdleImageModelBehindEmbeddingsAndAheadOfStt() {
        TaiResidency.Entry image = TaiResidency.Entry.image("sd15", "opencl", 1, 2 * GB);
        TaiResidency.Entry stt = new TaiResidency.Entry("whisper", TaiResidency.Kind.STT, "litert-lm", "cpu", 10, 200L, null, 3L, false);
        TaiResidency.Entry tts = new TaiResidency.Entry("kitten", TaiResidency.Kind.TTS, "litert-lm", "cpu", 0, 100L, null, 2L, false);
        TaiResidency.Entry emb = new TaiResidency.Entry("emb", TaiResidency.Kind.EMBEDDING, "litert-lm", "cpu", 0, 50L, null, 1L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(stt, image, tts, emb);
        // Speech output is only ever closed to make room for an image; every other load leaves it.
        assertEquals(Arrays.asList(emb, image, stt), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, null));
        assertEquals(Arrays.asList(emb, image, stt), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.TTS, null));
        // An embedding load keeps this backend's own embedder (it replaces it) and takes the image model.
        assertEquals(Arrays.asList(image, stt), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.EMBEDDING, "litert-lm"));
        // A busy image model (mid-run) is never a candidate.
        assertFalse(TaiResidency.evictionCandidates(Collections.singletonList(image.withBusy(true, 9L)), TaiResidency.Kind.CHAT, null)
            .contains(image));
    }

    @Test
    public void theRegistryRegistersAndDeregistersAnImageResident() {
        TaiResidency residency = new TaiResidency();
        residency.register(TaiResidency.Entry.image("sd15", "opencl", 1, GB));
        assertTrue(residency.isResident(TaiResidency.Kind.IMAGE, "sd15"));
        assertEquals(1, residency.find(TaiResidency.Kind.IMAGE, "sd15").window);
        assertEquals(GB, residency.bytes(TaiResidency.Kind.IMAGE, null));
        residency.deregister(TaiResidency.Kind.IMAGE, "sd15");
        assertFalse(residency.hasModels());
    }

    @Test
    public void theWatchClosesAnIdleImageModelAfterItsLimitAndBeforeSttOrChat() {
        long now = 10_000_000L;
        TaiResidency.Entry image = new TaiResidency.Entry("sd15", TaiResidency.Kind.IMAGE, TaiModelSpec.BACKEND_MNN_DIFFUSION,
            "opencl", 1, GB, null, now - TaiPressureWatch.IMAGE_IDLE_MS - 1L, false);
        TaiResidency.Entry fresh = new TaiResidency.Entry("sd15b", TaiResidency.Kind.IMAGE, TaiModelSpec.BACKEND_MNN_DIFFUSION,
            "opencl", 1, GB, null, now - 1_000L, false);
        TaiResidency.Entry stt = new TaiResidency.Entry("whisper", TaiResidency.Kind.STT, "litert-lm", "cpu", 10, 200L, null, 0L, false);
        assertEquals(TaiPressureWatch.IMAGE_IDLE_MS, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.IMAGE));
        assertEquals(Collections.singletonList(image), TaiPressureWatch.idleExpired(Arrays.asList(fresh, image), now, 0L));
        assertEquals(image, TaiPressureWatch.nextVictim(Arrays.asList(stt, image), TaiPressureWatch.Tier.AUXILIARY));
    }

    private static TaiResidency.Entry chat(long bytes) {
        return new TaiResidency.Entry("chat", TaiResidency.Kind.CHAT, "litert-lm", "gpu", 4096, bytes, null, 5L, false);
    }
}
