package com.termux.ai;

import com.termux.ai.TaiPlatformCaps.Facts;
import com.termux.ai.TaiPlatformCaps.GpuFamily;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiPlatformCaps.GpuVerdict;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The GPU classification of tai-device-tiers §2.2 and the ABI rules of §2.1. */
public class TaiPlatformCapsTest {
    private static final int QUALCOMM = 0x5143;
    private static final int ARM = 0x13B5;
    private static final int IMAGINATION = 0x1010;
    private static final int SAMSUNG = 0x144D;

    private static Facts vulkan(int vendor, Boolean openCl, String socMfr, String socModel, String model) {
        return new Facts(openCl, vendor, "", "", socMfr, socModel, model, "", "", "");
    }

    private static void assertVerdict(GpuFamily family, GpuPath path, GpuVerdict verdict) {
        assertEquals(family, verdict.family);
        assertEquals(path, verdict.path);
    }

    @Test
    public void adrenoIsYes() {
        assertVerdict(GpuFamily.ADRENO, GpuPath.YES,
            TaiPlatformCaps.classify(vulkan(QUALCOMM, true, "QTI", "SM8450", "Nothing Phone (2)")));
    }

    @Test
    public void adreno830WithTheWarnedCompilerIsUnknown() {
        Facts facts = new Facts(true, QUALCOMM, "Adreno (TM) 830", "", "QTI", "SM8750", "Phone",
            "QUALCOMM Adreno(TM) 830", "OpenCL 3.0 QUALCOMM build: commit Compiler E031.47.12.03", "");
        GpuVerdict verdict = TaiPlatformCaps.classify(facts);
        assertVerdict(GpuFamily.ADRENO, GpuPath.UNKNOWN, verdict);
        assertEquals("This GPU driver is known to give wrong answers; update the phone's software", verdict.reason);
    }

    @Test
    public void adreno830WithANewerCompilerIsYes() {
        Facts facts = new Facts(true, QUALCOMM, "Adreno (TM) 830", "", "QTI", "SM8750", "Phone",
            "QUALCOMM Adreno(TM) 830", "OpenCL 3.0 QUALCOMM build: commit Compiler E031.48.00.00", "");
        assertVerdict(GpuFamily.ADRENO, GpuPath.YES, TaiPlatformCaps.classify(facts));
    }

    @Test
    public void theWarnedCompilerOnAnOlderAdrenoIsStillYes() {
        Facts facts = new Facts(true, QUALCOMM, "Adreno (TM) 740", "", "QTI", "SM8550", "Phone",
            "QUALCOMM Adreno(TM) 740", "OpenCL 3.0 Compiler E031.47.12.03", "");
        assertVerdict(GpuFamily.ADRENO, GpuPath.YES, TaiPlatformCaps.classify(facts));
    }

    @Test
    public void theDriverStringReachesTheCaps() {
        Facts facts = new Facts(true, QUALCOMM, "Adreno (TM) 830", "", "QTI", "SM8750", "Phone",
            "QUALCOMM Adreno(TM) 830", "Compiler E031.48.00.00", "");
        TaiPlatformCaps caps = TaiPlatformCaps.of(35, Collections.singletonList("arm64-v8a"), facts, true);
        assertEquals("Compiler E031.48.00.00", caps.gpuDriver);
    }

    @Test
    public void maliOnTensorG4OrNewerIsUnknown() {
        assertVerdict(GpuFamily.MALI, GpuPath.UNKNOWN,
            TaiPlatformCaps.classify(vulkan(ARM, true, "Google", "Tensor G4", "Pixel 9")));
    }

    @Test
    public void otherMaliIsCpuFirst() {
        assertVerdict(GpuFamily.MALI, GpuPath.CPU_FIRST,
            TaiPlatformCaps.classify(vulkan(ARM, true, "Google", "Tensor G3", "Pixel 8")));
        assertVerdict(GpuFamily.MALI, GpuPath.CPU_FIRST,
            TaiPlatformCaps.classify(vulkan(ARM, true, "Mediatek", "MT6985", "Phone")));
    }

    @Test
    public void xclipseAndPowerVrAreCpuFirst() {
        assertVerdict(GpuFamily.XCLIPSE, GpuPath.CPU_FIRST,
            TaiPlatformCaps.classify(vulkan(SAMSUNG, true, "Samsung", "Exynos 2400", "SM-S921B")));
        assertVerdict(GpuFamily.POWERVR, GpuPath.CPU_FIRST,
            TaiPlatformCaps.classify(vulkan(IMAGINATION, true, "", "", "Phone")));
    }

    @Test
    public void pixel10IsNoWhateverTheGpu() {
        GpuVerdict verdict = TaiPlatformCaps.classify(vulkan(IMAGINATION, true, "Google", "Tensor G5", "Pixel 10 Pro"));
        assertEquals(GpuPath.NO, verdict.path);
        assertEquals(GpuPath.NO, TaiPlatformCaps.classify(vulkan(ARM, true, "Google", "Tensor G4", "pixel 10")).path);
    }

    @Test
    public void noOpenClIsNo() {
        assertVerdict(GpuFamily.ADRENO, GpuPath.NO,
            TaiPlatformCaps.classify(vulkan(QUALCOMM, false, "QTI", "SM8450", "Phone")));
    }

    @Test
    public void aProbeThatCouldNotRunDoesNotCountAsNoOpenCl() {
        assertEquals(GpuPath.YES, TaiPlatformCaps.classify(vulkan(QUALCOMM, null, "QTI", "SM8450", "Phone")).path);
    }

    @Test
    public void eglNameIsTheSecondSource() {
        assertVerdict(GpuFamily.ADRENO, GpuPath.YES,
            TaiPlatformCaps.classify(new Facts(true, -1, "", "adreno", "", "", "Phone", "", "", "")));
        assertVerdict(GpuFamily.MALI, GpuPath.CPU_FIRST,
            TaiPlatformCaps.classify(new Facts(true, -1, "", "mali", "", "", "Phone", "", "", "")));
    }

    @Test
    public void socTableIsTheThirdSource() {
        assertEquals(GpuFamily.ADRENO, TaiPlatformCaps.familyFromSoc("QTI", "SM8550"));
        assertEquals(GpuFamily.XCLIPSE, TaiPlatformCaps.familyFromSoc("Samsung", "Exynos 2200"));
        assertEquals(GpuFamily.MALI, TaiPlatformCaps.familyFromSoc("Samsung", "Exynos 990"));
        assertEquals(GpuFamily.MALI, TaiPlatformCaps.familyFromSoc("Google", "Tensor G2"));
        assertEquals(GpuFamily.POWERVR, TaiPlatformCaps.familyFromSoc("Google", "Tensor G5"));
        assertEquals(GpuFamily.MALI, TaiPlatformCaps.familyFromSoc("Mediatek", "MT6989"));
        assertEquals(GpuFamily.UNKNOWN, TaiPlatformCaps.familyFromSoc("", ""));
    }

    @Test
    public void nothingKnownIsUnknown() {
        assertVerdict(GpuFamily.UNKNOWN, GpuPath.UNKNOWN,
            TaiPlatformCaps.classify(new Facts(null, -1, "", "", "", "", "Phone", "", "", "")));
    }

    @Test
    public void anUnrecognisedVulkanVendorIsOtherAndUnconfirmed() {
        assertVerdict(GpuFamily.OTHER, GpuPath.UNKNOWN,
            TaiPlatformCaps.classify(vulkan(0x10005, true, "QTI", "SM8450", "Phone")));
    }

    @Test
    public void abiRules() {
        assertTrue(TaiPlatformCaps.liteRtAbiOk(Arrays.asList("arm64-v8a", "armeabi-v7a")));
        assertTrue(TaiPlatformCaps.liteRtAbiOk(Collections.singletonList("x86_64")));
        assertFalse(TaiPlatformCaps.liteRtAbiOk(Collections.singletonList("armeabi-v7a")));
        assertTrue(TaiPlatformCaps.mnnAbiOk(Collections.singletonList("arm64-v8a"), 30));
        assertFalse(TaiPlatformCaps.mnnAbiOk(Collections.singletonList("arm64-v8a"), 29));
        assertFalse(TaiPlatformCaps.mnnAbiOk(Collections.singletonList("x86_64"), 34));
    }

    @Test
    public void mnnMinimumIsApi30() {
        assertEquals(30, TaiDeviceCapabilities.MNN_SDK_MINIMUM);
    }

    @Test
    public void cpuFeaturesAreLabelsFromTheFeaturesLine() {
        assertEquals(new LinkedHashSet<>(Arrays.asList("dotprod", "i8mm", "sme2")),
            TaiPlatformCaps.parseCpuFeatures("fp asimd asimddp i8mm sme2 bf16"));
        assertTrue(TaiPlatformCaps.parseCpuFeatures("fp asimd").isEmpty());
        assertTrue(TaiPlatformCaps.parseCpuFeatures(null).isEmpty());
    }

    @Test
    public void ofAssemblesAbiGpuAndFeatures() {
        Facts facts = new Facts(true, QUALCOMM, "Adreno (TM) 740", "", "QTI", "SM8550", "Phone", "", "", "asimddp");
        TaiPlatformCaps caps = TaiPlatformCaps.of(34, Collections.singletonList("arm64-v8a"), facts, true);
        assertTrue(caps.liteRtAbiOk);
        assertTrue(caps.mnnAbiOk);
        assertEquals(34, caps.sdkInt);
        assertEquals(GpuPath.YES, caps.gpuPath);
        assertEquals("Adreno (TM) 740", caps.gpuName);
        assertTrue(caps.cpuFeatures.contains("dotprod"));
        assertTrue(caps.anyLocalBackend());
    }
}
