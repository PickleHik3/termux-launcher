package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.fragments.settings.termux.TaiParameterPreferencesFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiSettingsParameterTest {
    private Context context;
    private TaiSettings settings;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        settings = new TaiSettings(context);
    }

    @Test
    public void liteRtSchema_matchesHandoffRangesAndDefaults() {
        TaiSettings.ParameterSchema schema = TaiSettings.getParameterSchema(TaiModelSpec.BACKEND_LITERT_LM);
        Map<String, TaiSettings.ParameterSpec> fields = schema.fields();

        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, schema.backend);
        assertEquals(7, fields.size());
        assertIntegerSpec(fields.get(TaiSettings.FIELD_MAX_TOKENS), "4000", 2000, 32000);
        assertIntegerSpec(fields.get(TaiSettings.FIELD_TOP_K), "64", 5, 100);
        assertDecimalSpec(fields.get(TaiSettings.FIELD_TOP_P), "0.95", 0.0d, 1.0d);
        assertDecimalSpec(fields.get(TaiSettings.FIELD_TEMPERATURE), "1.00", 0.0d, 2.0d);
        assertArrayEquals(new String[] {"GPU", "CPU"}, fields.get(TaiSettings.FIELD_ACCELERATOR).options);
        assertBooleanSpec(fields.get(TaiSettings.FIELD_ENABLE_THINKING), "false");
        assertBooleanSpec(fields.get(TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING), "false");

        TaiRuntimeOptions options = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, null);
        assertNull(options.maxTokens);
        assertNull(options.topK);
        assertNull(options.topP);
        assertNull(options.temperature);
        assertNull(options.accelerator);
        assertNull(options.thinkingEnabled);
        assertNull(options.speculativeDecodingEnabled);
        assertNull(options.contextWindow);
    }

    @Test
    public void mnnSchema_matchesHandoffRangesAndDefaults() {
        TaiSettings.ParameterSchema schema = TaiSettings.getParameterSchema(TaiModelSpec.BACKEND_MNN_LLM);
        Map<String, TaiSettings.ParameterSpec> fields = schema.fields();

        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, schema.backend);
        assertEquals(9, fields.size());
        assertArrayEquals(new String[] {"Auto", "CPU", "OpenCL"}, fields.get(TaiSettings.FIELD_ACCELERATOR).options);
        assertIntegerSpec(fields.get(TaiSettings.FIELD_CONTEXT_WINDOW), "4096", 1024, 8192);
        assertIntegerSpec(fields.get(TaiSettings.FIELD_THREAD_COUNT), "4", 1, 16);
        assertArrayEquals(new String[] {"low", "normal", "high"}, fields.get(TaiSettings.FIELD_PRECISION).options);
        assertArrayEquals(new String[] {"low", "normal", "high"}, fields.get(TaiSettings.FIELD_MEMORY_MODE).options);
        assertIntegerSpec(fields.get(TaiSettings.FIELD_MAX_TOKENS), "1024", 64, 8192);
        assertDecimalSpec(fields.get(TaiSettings.FIELD_TEMPERATURE), "0.80", 0.0d, 2.0d);
        assertDecimalSpec(fields.get(TaiSettings.FIELD_TOP_P), "0.90", 0.0d, 1.0d);
        assertIntegerSpec(fields.get(TaiSettings.FIELD_TOP_K), "40", 1, 100);

        TaiRuntimeOptions options = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, null);
        assertNull(options.accelerator);
        assertNull(options.contextWindow);
        assertNull(options.threadCount);
        assertNull(options.precision);
        assertNull(options.memoryMode);
        assertNull(options.maxTokens);
        assertNull(options.temperature);
        assertNull(options.topP);
        assertNull(options.topK);
        assertNull(options.thinkingEnabled);
        assertNull(options.speculativeDecodingEnabled);
    }

    @Test
    public void liteRtModelProfiles_matchEdgeGalleryDefaults() {
        TaiModelRegistry registry = new TaiModelRegistry();

        TaiModelProfile gemma = TaiModelProfile.forModel(registry.getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertEquals(4000, gemma.defaultMaxTokens);
        assertEquals(64, gemma.defaultTopK);
        assertEquals(0.95d, gemma.defaultTopP, 0.0d);
        assertEquals(1.0d, gemma.defaultTemperature, 0.0d);
        assertEquals("gpu", gemma.compatibleAccelerators.get(0));

        TaiModelProfile mobileActions = TaiModelProfile.forModel(registry.getModel(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M));
        assertEquals(1024, mobileActions.defaultMaxTokens);
        assertEquals(64, mobileActions.defaultTopK);
        assertEquals(0.95d, mobileActions.defaultTopP, 0.0d);
        assertEquals(0.0d, mobileActions.defaultTemperature, 0.0d);
        assertEquals("cpu", mobileActions.compatibleAccelerators.get(0));

        TaiModelProfile deepSeek = TaiModelProfile.forModel(registry.getModel("deepseek-r1-distill-qwen-1.5b-litert-lm"));
        assertEquals(4096, deepSeek.defaultMaxTokens);
        assertEquals(64, deepSeek.defaultTopK);
        assertEquals(0.95d, deepSeek.defaultTopP, 0.0d);
        assertEquals(1.0d, deepSeek.defaultTemperature, 0.0d);

        TaiModelProfile qwen = TaiModelProfile.forModel(registry.getModel("qwen2.5-1.5b-instruct-litert-lm"));
        assertEquals(4096, qwen.defaultMaxTokens);
        assertEquals(20, qwen.defaultTopK);
        assertEquals(0.80d, qwen.defaultTopP, 0.0d);
        assertEquals(0.70d, qwen.defaultTemperature, 0.0d);
    }

    @Test
    public void validMinAndMaxValues_resolveForEveryRangedField() {
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 2000);
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_K, 5);
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_P, 0.0d);
        settings.setGlobalParameter(TaiSettings.FIELD_TEMPERATURE, 0.0d);
        TaiRuntimeOptions liteRtMin = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, null);
        assertEquals(Integer.valueOf(2000), liteRtMin.maxTokens);
        assertEquals(Integer.valueOf(5), liteRtMin.topK);
        assertEquals(Double.valueOf(0.0d), liteRtMin.topP);
        assertEquals(Double.valueOf(0.0d), liteRtMin.temperature);

        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 32000);
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_K, 100);
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_P, 1.0d);
        settings.setGlobalParameter(TaiSettings.FIELD_TEMPERATURE, 2.0d);
        TaiRuntimeOptions liteRtMax = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, null);
        assertEquals(Integer.valueOf(32000), liteRtMax.maxTokens);
        assertEquals(Integer.valueOf(100), liteRtMax.topK);
        assertEquals(Double.valueOf(1.0d), liteRtMax.topP);
        assertEquals(Double.valueOf(2.0d), liteRtMax.temperature);

        settings.setGlobalParameter(TaiSettings.FIELD_CONTEXT_WINDOW, 1024);
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 256);
        TaiRuntimeOptions mnnMin = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, null);
        assertEquals(Integer.valueOf(1024), mnnMin.contextWindow);
        assertEquals(Integer.valueOf(256), mnnMin.maxTokens);

        settings.setGlobalParameter(TaiSettings.FIELD_CONTEXT_WINDOW, 8192);
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 8192);
        TaiRuntimeOptions mnnMax = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, null);
        assertEquals(Integer.valueOf(8192), mnnMax.contextWindow);
        assertEquals(Integer.valueOf(8192), mnnMax.maxTokens);
    }

    @Test
    public void invalidValues_fallBackByPrecedenceWithoutCrashing() {
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, "not-a-number");
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_K, 101);
        settings.setGlobalParameter(TaiSettings.FIELD_TOP_P, 1.01d);
        settings.setGlobalParameter(TaiSettings.FIELD_TEMPERATURE, Double.NaN);
        settings.setGlobalParameter(TaiSettings.FIELD_ACCELERATOR, "NPU");
        settings.setGlobalParameter(TaiSettings.FIELD_ENABLE_THINKING, "maybe");
        settings.setGlobalParameter(TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, "yes");

        TaiRuntimeOptions liteRt = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, "litert-bad");
        assertNull(liteRt.maxTokens);
        assertNull(liteRt.topK);
        assertNull(liteRt.topP);
        assertNull(liteRt.temperature);
        assertNull(liteRt.accelerator);
        assertNull(liteRt.thinkingEnabled);
        assertNull(liteRt.speculativeDecodingEnabled);

        settings.setGlobalParameter(TaiSettings.FIELD_CONTEXT_WINDOW, 9000);
        TaiRuntimeOptions mnn = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, "mnn-bad");
        assertNull(mnn.accelerator);
        assertNull(mnn.contextWindow);
        assertNull(mnn.maxTokens);
    }

    @Test
    public void perModelOverrideWinsAndResetRestoresGlobalWithoutRetroactiveEdits() {
        String modelId = "model-one";
        settings.setGlobalParameter(TaiSettings.FIELD_TEMPERATURE, 1.2d);
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 5000);
        settings.setModelParameter(modelId, TaiSettings.FIELD_TEMPERATURE, 0.4d);
        settings.setModelParameter(modelId, TaiSettings.FIELD_MAX_TOKENS, 6000);

        TaiRuntimeOptions withOverride = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, modelId);
        assertEquals(Double.valueOf(0.4d), withOverride.temperature);
        assertEquals(Integer.valueOf(6000), withOverride.maxTokens);

        settings.setGlobalParameter(TaiSettings.FIELD_TEMPERATURE, 1.6d);
        settings.setGlobalParameter(TaiSettings.FIELD_MAX_TOKENS, 7000);
        TaiRuntimeOptions afterGlobalEdit = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, modelId);
        assertEquals(Double.valueOf(0.4d), afterGlobalEdit.temperature);
        assertEquals(Integer.valueOf(6000), afterGlobalEdit.maxTokens);

        settings.resetModelParameterToGlobal(modelId, TaiSettings.FIELD_TEMPERATURE);
        TaiRuntimeOptions afterFieldReset = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, modelId);
        assertEquals(Double.valueOf(1.6d), afterFieldReset.temperature);
        assertEquals(Integer.valueOf(6000), afterFieldReset.maxTokens);

        settings.resetModelParametersToGlobal(modelId);
        TaiRuntimeOptions afterModelReset = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, modelId);
        assertEquals(Double.valueOf(1.6d), afterModelReset.temperature);
        assertEquals(Integer.valueOf(7000), afterModelReset.maxTokens);
    }

    @Test
    public void backendScopedGlobalDefaults_doNotLeakAcrossBackends() {
        settings.setGlobalParameter(TaiModelSpec.BACKEND_LITERT_LM, TaiSettings.FIELD_MAX_TOKENS, 32000);
        settings.setGlobalParameter(TaiModelSpec.BACKEND_MNN_LLM, TaiSettings.FIELD_MAX_TOKENS, 256);
        settings.setGlobalParameter(TaiModelSpec.BACKEND_LITERT_LM, TaiSettings.FIELD_ACCELERATOR, "CPU");
        settings.setGlobalParameter(TaiModelSpec.BACKEND_MNN_LLM, TaiSettings.FIELD_ACCELERATOR, "Auto");

        TaiRuntimeOptions liteRt = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, "litert-one");
        TaiRuntimeOptions mnn = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, "mnn-one");

        assertEquals(Integer.valueOf(32000), liteRt.maxTokens);
        assertEquals("CPU", liteRt.accelerator);
        assertEquals(Integer.valueOf(256), mnn.maxTokens);
        assertNull(mnn.accelerator);
        assertNull(liteRt.contextWindow);
        assertNull(mnn.thinkingEnabled);
        assertFalse(context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .contains(TaiSettings.modelParameterPreferenceKey("mnn-one", TaiSettings.FIELD_ENABLE_THINKING)));
    }

    @Test
    public void parameterScreenKeys_includeBackendToPreventCrossBackendRowsColliding() {
        assertEquals("tai_global_parameter_screen.litert-lm.max_tokens",
            TaiParameterPreferencesFragment.parameterPreferenceKey(
                TaiModelSpec.BACKEND_LITERT_LM, TaiSettings.FIELD_MAX_TOKENS, false));
        assertEquals("tai_global_parameter_screen.mnn-llm.max_tokens",
            TaiParameterPreferencesFragment.parameterPreferenceKey(
                TaiModelSpec.BACKEND_MNN_LLM, TaiSettings.FIELD_MAX_TOKENS, false));
        assertEquals("tai_model_parameter_screen.mnn-llm.context_window",
            TaiParameterPreferencesFragment.parameterPreferenceKey(
                TaiModelSpec.BACKEND_MNN_LLM, TaiSettings.FIELD_CONTEXT_WINDOW, true));
    }

    @Test
    public void restoringBackendGlobalDefault_removesOnlyThatBackendDefault() {
        settings.setGlobalParameter(TaiModelSpec.BACKEND_LITERT_LM, TaiSettings.FIELD_TEMPERATURE, 1.7d);
        settings.setGlobalParameter(TaiModelSpec.BACKEND_MNN_LLM, TaiSettings.FIELD_TEMPERATURE, 0.2d);

        settings.setGlobalParameter(TaiModelSpec.BACKEND_LITERT_LM, TaiSettings.FIELD_TEMPERATURE, null);

        TaiRuntimeOptions liteRt = settings.getRuntimeOptions(TaiModelSpec.BACKEND_LITERT_LM, null);
        TaiRuntimeOptions mnn = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, null);
        assertNull(liteRt.temperature);
        assertEquals(Double.valueOf(0.2d), mnn.temperature);
    }

    @Test
    public void modelSystemPromptOverridesGeneralAndResetsWithModel() {
        String modelId = "prompt-model";
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(TaiSettings.KEY_SYSTEM_PROMPT_GENERAL, "global prompt").commit();

        assertEquals("global prompt", settings.getSystemPrompt(modelId));

        settings.setModelSystemPrompt(modelId, "model prompt");
        assertEquals("model prompt", settings.getSystemPrompt(modelId));

        settings.resetModelSystemPromptToGlobal(modelId);
        assertEquals("global prompt", settings.getSystemPrompt(modelId));

        settings.setModelSystemPrompt(modelId, "model prompt again");
        settings.resetModelParametersToGlobal(modelId);
        assertEquals("global prompt", settings.getSystemPrompt(modelId));
    }

    @Test
    public void invalidPerModelOverrideFallsBackToGlobalThenBackendDefault() {
        String modelId = "mnn-one";
        settings.setGlobalParameter(TaiSettings.FIELD_CONTEXT_WINDOW, 2048);
        settings.setModelParameter(modelId, TaiSettings.FIELD_CONTEXT_WINDOW, 100000);

        TaiRuntimeOptions withGlobalFallback = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, modelId);
        assertEquals(Integer.valueOf(2048), withGlobalFallback.contextWindow);

        settings.setGlobalParameter(TaiSettings.FIELD_CONTEXT_WINDOW, "bad");
        TaiRuntimeOptions withConfigFallback = settings.getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, modelId);
        assertNull(withConfigFallback.contextWindow);
    }

    private static void assertIntegerSpec(TaiSettings.ParameterSpec spec, String defaultValue, int min, int max) {
        assertEquals(defaultValue, spec.defaultValue);
        assertEquals(Double.valueOf(min), spec.minValue);
        assertEquals(Double.valueOf(max), spec.maxValue);
    }

    private static void assertDecimalSpec(TaiSettings.ParameterSpec spec, String defaultValue, double min, double max) {
        assertEquals(defaultValue, spec.defaultValue);
        assertEquals(Double.valueOf(min), spec.minValue);
        assertEquals(Double.valueOf(max), spec.maxValue);
    }

    private static void assertBooleanSpec(TaiSettings.ParameterSpec spec, String defaultValue) {
        assertEquals(defaultValue, spec.defaultValue);
        assertTrue(spec.fallbackValue instanceof Boolean);
    }
}
