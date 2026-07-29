package com.termux.ai;

import com.termux.BuildConfig;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiDeviceCapabilitiesTest {

    @After
    public void tearDown() {
        TaiDeviceCapabilities.clearDebugMnnUnsupportedReason();
    }

    @Test
    public void arm64DeviceWithoutLoadableNativeLibraries_reportsMnnUnavailable() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Pixel 9", "Google", "tensor", 34,
            Arrays.asList("arm64-v8a", "armeabi-v7a", "armeabi"),
            8L * 1024L * 1024L * 1024L, "totalMem", false);

        assertTrue(device.liteRtLmAbiSupported);
        assertFalse(device.mnnSupported);
        assertFalse(device.mnnBundledLibrariesAvailable);
        assertNotNull(device.mnnUnsupportedReason);
        assertTrue(device.mnnUnsupportedReason.contains("not available"));
        assertEquals(TaiDeviceCapabilities.MNN_SDK_MINIMUM, device.mnnSdkMinimum);
        assertEquals(TaiDeviceCapabilities.MNN_MEMORY_ESTIMATE_MB, device.mnnMemoryEstimateMb);
    }

    @Test
    public void unsupportedAbi_mnnSupportedIsFalseWithReason() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Emulator", "Google", "", 34,
            Arrays.asList("x86"),
            4L * 1024L * 1024L * 1024L, "totalMem", false);

        assertFalse(device.liteRtLmAbiSupported);
        assertFalse(device.mnnSupported);
        assertFalse(device.mnnBundledLibrariesAvailable);
        assertNotNull(device.mnnUnsupportedReason);
        assertTrue("Expected ABI unsupported reason, got: " + device.mnnUnsupportedReason,
            device.mnnUnsupportedReason.contains("arm64-v8a only"));
    }

    @Test
    public void lowSdk_mnnSupportedIsFalseWithReason() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Old Phone", "Generic", "", 21,
            Arrays.asList("arm64-v8a"),
            4L * 1024L * 1024L * 1024L, "totalMem", false);

        assertTrue(device.liteRtLmAbiSupported);
        assertFalse(device.mnnSupported);
        assertFalse(device.mnnBundledLibrariesAvailable);
        assertNotNull(device.mnnUnsupportedReason);
        assertTrue("Expected SDK reason, got: " + device.mnnUnsupportedReason,
            device.mnnUnsupportedReason.contains("requires Android 7.0"));
    }

    /**
     * Debug-variant only: {@code setDebugMnnUnsupportedReason} is a deliberate no-op when
     * {@code BuildConfig.DEBUG} is false, so this cannot pass under {@code testReleaseUnitTest}.
     * The release side of the same seam is asserted by {@link #releaseBuildIgnoresDebugOverride()}.
     */
    @Test
    public void debugOverrideForcesUnsupportedState() {
        assumeTrue("debug-only override, ignored by design in release builds", BuildConfig.DEBUG);
        TaiDeviceCapabilities.setDebugMnnUnsupportedReason("Debug override: forced unsupported.");

        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Pixel 9", "Google", "tensor", 34,
            Arrays.asList("arm64-v8a"),
            8L * 1024L * 1024L * 1024L, "totalMem", false);

        assertFalse(device.mnnSupported);
        assertEquals("Debug override: forced unsupported.", device.mnnUnsupportedReason);
    }

    @Test
    public void releaseBuildIgnoresDebugOverride() {
        assertFalse(TaiDeviceCapabilities.shouldApplyDebugOverride(false, "reason"));
        assertTrue(TaiDeviceCapabilities.shouldApplyDebugOverride(true, "reason"));
    }

    @Test
    public void mnnModelOnUnsupportedDevice_blocksWithReason() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Emulator", "Google", "", 34,
            Arrays.asList("x86"),
            4L * 1024L * 1024L * 1024L, "totalMem", false);

        TaiModelSpec model = new TaiModelSpec(
            "phi-3-mini-mnn",
            "Phi-3 Mini MNN",
            "chat",
            "test",
            "/models/phi-3-mini-mnn/model.mnn",
            "test",
            0L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_MNN_LLM,
            TaiModelSpec.FORMAT_MNN,
            null,
            null,
            4096,
            4,
            null
        );

        TaiDeviceCapabilities.ModelCapabilityCheck check = device.checkModelCapability(model);
        assertNotNull(check.blockingReason);
        assertNull(check.warning);
        assertTrue(check.blockingReason.contains("target ABI is not supported"));
    }

    @Test
    public void modelMemoryRequirement_producesWarning() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Low-RAM Phone", "Generic", "", 34,
            Arrays.asList("arm64-v8a"),
            2L * 1024L * 1024L * 1024L, "totalMem", false);

        TaiModelSpec model = new TaiModelSpec(
            "large-model",
            "Large Model",
            "chat",
            "test",
            "/models/large/model.bin",
            "test",
            0L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            4096,
            8,
            null
        );

        TaiDeviceCapabilities.ModelCapabilityCheck check = device.checkModelCapability(model);
        assertNull(check.blockingReason);
        assertNotNull(check.warning);
        assertTrue(check.warning.contains("below the model's recommended"));
    }

    @Test
    public void jsonOutput_containsCorrectBackendsStructure() throws Exception {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Pixel 9", "Google", "tensor", 34,
            Arrays.asList("arm64-v8a"),
            8L * 1024L * 1024L * 1024L, "totalMem", false);

        JSONObject json = device.toJson();
        assertTrue(json.getBoolean("liteRtLmAbiSupported"));
        assertFalse(json.getBoolean("mnnSupported"));
        assertNotNull(json.get("mnnUnsupportedReason"));

        JSONObject backends = json.getJSONObject("backends");
        assertTrue(backends.getBoolean(TaiModelSpec.BACKEND_LITERT_LM));
        assertFalse(backends.getBoolean(TaiModelSpec.BACKEND_MNN_LLM));
    }

    @Test
    public void jsonOutput_containsMnnUnsupportedReasonWhenFalse() throws Exception {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Emulator", "Google", "", 34,
            Arrays.asList("x86"),
            4L * 1024L * 1024L * 1024L, "totalMem", false);

        JSONObject json = device.toJson();
        assertFalse(json.getBoolean("mnnSupported"));
        assertFalse(json.getJSONObject("backends").getBoolean(TaiModelSpec.BACKEND_MNN_LLM));
        assertEquals(device.mnnUnsupportedReason, json.getString("mnnUnsupportedReason"));
    }
}
