package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiModelSpec;

import org.junit.Test;

/** The Choose screen's filter: RAM fit, storage for a download, the chip a build was made for, MNN support, and the GPU count. */
public class TaiBenchChoiceTest {
    private static final long GB = 1024L * 1024L * 1024L;
    /** An 8 GB phone with 20 GB free, a Snapdragon, MNN and the GPU. */
    private static final TaiBenchChoice.Device PHONE = new TaiBenchChoice.Device(8L * GB, 20L * GB, "SM8650", true, true);

    private static TaiBenchChoice.Candidate candidate(long sizeBytes, String backend, boolean installed, boolean recommended, String fileName) {
        return new TaiBenchChoice.Candidate("m", "Model", backend, sizeBytes, installed, recommended, fileName);
    }

    @Test
    public void aModelThatFitsIsShown() {
        TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate(1_500L * 1024L * 1024L, TaiModelSpec.BACKEND_MNN_LLM, true, false, "qwen.mnn"), PHONE);
        assertEquals(TaiBenchChoice.Fit.SHOWN, verdict.fit);
        assertNull(verdict.reason);
    }

    @Test
    public void onePhoneClassUnderTheTierIsTightTwoIsHidden() {
        // 2.4 GB wants 8 GB: shown on 8, Tight on 6, hidden on 4.
        long size = (long) (2.4 * GB);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, true, false, "g.litertlm"), PHONE).fit);
        TaiBenchChoice.Device six = new TaiBenchChoice.Device(6L * GB, 20L * GB, "SM8650", true, true);
        assertEquals(TaiBenchChoice.Fit.TIGHT, TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, true, false, "g.litertlm"), six).fit);
        TaiBenchChoice.Device four = new TaiBenchChoice.Device(4L * GB, 20L * GB, "SM8650", true, true);
        TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, true, false, "g.litertlm"), four);
        assertEquals(TaiBenchChoice.Fit.HIDDEN, verdict.fit);
        assertEquals(TaiBenchChoice.Reason.TOO_BIG, verdict.reason);
    }

    @Test
    public void aDownloadNeedsItsSizePlusTenPercentOfFreeStorage() {
        long size = 2L * GB;
        TaiBenchChoice.Device tight = new TaiBenchChoice.Device(8L * GB, size + size / 20L, "SM8650", true, true);
        TaiBenchChoice.Verdict download = TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, false, true, "g.litertlm"), tight);
        assertEquals(TaiBenchChoice.Fit.HIDDEN, download.fit);
        assertEquals(TaiBenchChoice.Reason.NO_SPACE, download.reason);
        // Installed models take no storage; the same phone shows them.
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, true, false, "g.litertlm"), tight).fit);
        TaiBenchChoice.Device roomy = new TaiBenchChoice.Device(8L * GB, size + size / 5L, "SM8650", true, true);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, false, true, "g.litertlm"), roomy).fit);
        // Unknown free space never hides.
        TaiBenchChoice.Device unknown = new TaiBenchChoice.Device(8L * GB, -1L, "SM8650", true, true);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(size, TaiModelSpec.BACKEND_LITERT_LM, false, true, "g.litertlm"), unknown).fit);
    }

    @Test
    public void aBuildForAnotherChipIsHidden() {
        TaiBenchChoice.Verdict other = TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, false, "gemma_google.tensor_g5.litertlm"), PHONE);
        assertEquals(TaiBenchChoice.Fit.HIDDEN, other.fit);
        assertEquals(TaiBenchChoice.Reason.WRONG_CHIP, other.reason);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, false, "gemma_qualcomm.sm8650.litertlm"), PHONE).fit);
        // A portable build runs anywhere, an unknown chip included.
        TaiBenchChoice.Device unknownSoc = new TaiBenchChoice.Device(8L * GB, 20L * GB, null, true, true);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, false, "gemma.litertlm"), unknownSoc).fit);
        assertEquals(TaiBenchChoice.Reason.WRONG_CHIP, TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, false, "gemma_qualcomm.sm8650.litertlm"), unknownSoc).reason);
    }

    @Test
    public void mnnNeedsMnnSupport() {
        TaiBenchChoice.Device noMnn = new TaiBenchChoice.Device(8L * GB, 20L * GB, "SM8650", false, true);
        TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_MNN_LLM, true, false, "qwen.mnn"), noMnn);
        assertEquals(TaiBenchChoice.Fit.HIDDEN, verdict.fit);
        assertEquals(TaiBenchChoice.Reason.MNN_UNSUPPORTED, verdict.reason);
        assertEquals(TaiBenchChoice.Fit.SHOWN, TaiBenchChoice.judge(candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, false, "g.litertlm"), noMnn).fit);
    }

    @Test
    public void theRulesApplyInTheSpecsOrder() {
        // Too big for RAM and for another chip: RAM is the reason given.
        TaiBenchChoice.Device four = new TaiBenchChoice.Device(4L * GB, 1L, "MT6991", false, true);
        TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate(3L * GB, TaiModelSpec.BACKEND_MNN_LLM, false, true, "x_qualcomm.sm8650.mnn"), four);
        assertEquals(TaiBenchChoice.Reason.TOO_BIG, verdict.reason);
    }

    @Test
    public void worthADownloadIsARecommendedUninstalledEntryThatPasses() {
        TaiBenchChoice.Candidate recommended = candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, false, true, "g.litertlm");
        assertTrue(TaiBenchChoice.worthADownload(recommended, TaiBenchChoice.judge(recommended, PHONE)));
        TaiBenchChoice.Candidate installed = candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, true, true, "g.litertlm");
        assertFalse(TaiBenchChoice.worthADownload(installed, TaiBenchChoice.judge(installed, PHONE)));
        TaiBenchChoice.Candidate notRecommended = candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, false, false, "g.litertlm");
        assertFalse(TaiBenchChoice.worthADownload(notRecommended, TaiBenchChoice.judge(notRecommended, PHONE)));
        TaiBenchChoice.Candidate hidden = candidate(1L * GB, TaiModelSpec.BACKEND_LITERT_LM, false, true, "g_google.tensor_g5.litertlm");
        assertFalse(TaiBenchChoice.worthADownload(hidden, TaiBenchChoice.judge(hidden, PHONE)));
    }

    @Test
    public void processorsFollowThePresetAndTheGpu() {
        TaiBenchChoice.Device noGpu = new TaiBenchChoice.Device(8L * GB, 20L * GB, "SM8650", true, false);
        assertEquals(1, TaiBenchChoice.processors(TaiBenchSuite.Preset.QUICK, PHONE));
        assertEquals(2, TaiBenchChoice.processors(TaiBenchSuite.Preset.STANDARD, PHONE));
        assertEquals(2, TaiBenchChoice.processors(TaiBenchSuite.Preset.THOROUGH, PHONE));
        assertEquals(1, TaiBenchChoice.processors(TaiBenchSuite.Preset.STANDARD, noGpu));
        assertEquals(4 * 60_000L, TaiBenchChoice.estimateMs(TaiBenchSuite.Preset.STANDARD, PHONE));
        assertEquals(2 * 60_000L, TaiBenchChoice.estimateMs(TaiBenchSuite.Preset.STANDARD, noGpu));
        assertEquals(60_000L, TaiBenchChoice.estimateMs(TaiBenchSuite.Preset.QUICK, PHONE));
    }
}
