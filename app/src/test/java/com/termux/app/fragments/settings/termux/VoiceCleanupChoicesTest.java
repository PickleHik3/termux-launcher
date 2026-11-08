package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiTierPolicy.Env;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** The Voice input page's Cleanup model list and the "Runs on" segments it offers. */
public class VoiceCleanupChoicesTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    private static final String FORMAT = "Automatic · %1$s";

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, ModelInfo> installed = new LinkedHashMap<>();
    private boolean remoteConfigured;

    private final TaiFunctionModels.Remote remote = new TaiFunctionModels.Remote() {
        @Override public boolean configured() { return remoteConfigured; }
        @Override public String modelId() { return remoteConfigured ? "gpt-x" : ""; }
        @Override public boolean understandsImages() { return false; }
        @Override public boolean prefersRemote() { return false; }
    };

    private static Env env(int gb, GpuPath gpu) {
        return new Env(TaiDeviceTier.from(gb * GIB), gb * GIB, 34, true, true, gpu, false);
    }

    private TaiFunctionModels models(Env env) {
        TaiFunctionModels.Store store = new TaiFunctionModels.Store() {
            @Override public String get(String key) {
                String value = prefs.get(key);
                return value == null ? "" : value;
            }

            @Override public void put(String key, String value) {
                if (value.isEmpty()) prefs.remove(key);
                else prefs.put(key, value);
            }
        };
        return new TaiFunctionModels(env, store, () -> installed, remote);
    }

    private void install(String id, long bytes, String... capabilities) {
        installed.put(id, new ModelInfo(id, bytes, new HashSet<>(Arrays.asList(capabilities)), TaiModelSpec.BACKEND_LITERT_LM));
    }

    private VoiceCleanupChoices.Result result(Env env) {
        return VoiceCleanupChoices.of(models(env), remote, TaiFunctionRowsTest.LABELS, FORMAT);
    }

    @Test
    public void automaticNamesTheTiersModelAndIsPickedByDefault() {
        install(E2B, 2_588_147_712L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        VoiceCleanupChoices.Result result = result(env(12, GpuPath.YES));
        assertEquals("Automatic · " + E2B, result.options.get(0).title);
        assertTrue(result.options.get(0).selected);
        assertEquals(result.options.get(0).title, result.summary);
        assertFalse(result.runsElsewhere);
    }

    @Test
    public void listsInstalledChatModelsOnlyAndNoRemoteRowUntilOneIsSetUp() {
        install(E2B, 2_588_147_712L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        install(E4B, 3_659_530_240L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        install("whisper", 100L << 20, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        VoiceCleanupChoices.Result result = result(env(12, GpuPath.YES));
        assertEquals(3, result.options.size());
        assertEquals("", result.options.get(0).value);
        assertEquals(E2B, result.options.get(1).value);
        assertEquals(E4B, result.options.get(2).value);
    }

    @Test
    public void aSetUpRemoteIsListedAndWhenPickedNothingRunsOnThePhone() {
        install(E2B, 2_588_147_712L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        remoteConfigured = true;
        TaiFunctionModels models = models(env(12, GpuPath.YES));
        models.set(TaiFunction.TIDY_DICTATION, "remote/gpt-x");
        VoiceCleanupChoices.Result result = VoiceCleanupChoices.of(models, remote, TaiFunctionRowsTest.LABELS, FORMAT);
        VoiceCleanupChoices.Option last = result.options.get(result.options.size() - 1);
        assertEquals("remote/gpt-x", last.value);
        assertTrue(last.selected);
        assertFalse(result.options.get(0).selected);
        assertEquals(last.title, result.summary);
        assertTrue(result.runsElsewhere);
    }

    @Test
    public void aStoredOffIsNamedButNeverListed() {
        install(E2B, 2_588_147_712L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFunctionModels models = models(env(12, GpuPath.YES));
        models.set(TaiFunction.TIDY_DICTATION, TaiFunctionModels.VALUE_OFF);
        VoiceCleanupChoices.Result result = VoiceCleanupChoices.of(models, remote, TaiFunctionRowsTest.LABELS, FORMAT);
        assertEquals("RAW_TEXT", result.summary);
        for (VoiceCleanupChoices.Option option : result.options) {
            assertFalse(option.selected);
            assertFalse(TaiFunctionModels.VALUE_OFF.equals(option.value));
        }
        assertTrue(result.runsElsewhere);
    }

    @Test
    public void aLocalPickIsCheckedAndRunsOnThePhone() {
        install(E2B, 2_588_147_712L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        install(E4B, 3_659_530_240L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFunctionModels models = models(env(12, GpuPath.YES));
        models.set(TaiFunction.TIDY_DICTATION, E4B);
        VoiceCleanupChoices.Result result = VoiceCleanupChoices.of(models, remote, TaiFunctionRowsTest.LABELS, FORMAT);
        assertTrue(result.options.get(2).selected);
        assertFalse(result.options.get(0).selected);
        assertFalse(result.runsElsewhere);
    }

    @Test
    public void gpuIsOfferedOnlyWhereThePhoneHasAGpuPath() {
        assertEquals(Arrays.asList("cpu", "gpu"), VoiceCleanupChoices.acceleratorValues(env(12, GpuPath.YES)));
        assertEquals(Arrays.asList("cpu", "gpu"), VoiceCleanupChoices.acceleratorValues(env(12, GpuPath.UNKNOWN)));
        assertTrue(VoiceCleanupChoices.acceleratorValues(env(12, GpuPath.NO)).isEmpty());
    }

    @Test
    public void theShownSegmentIsThePlansElseTheTiersDefault() {
        assertEquals("cpu", VoiceCleanupChoices.shownAccelerator("cpu", env(12, GpuPath.YES)));
        assertEquals("gpu", VoiceCleanupChoices.shownAccelerator(null, env(12, GpuPath.YES)));
        assertEquals("cpu", VoiceCleanupChoices.shownAccelerator(null, env(12, GpuPath.NO)));
    }
}
