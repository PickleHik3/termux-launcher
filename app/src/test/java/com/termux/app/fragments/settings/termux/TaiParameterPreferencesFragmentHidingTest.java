package com.termux.app.fragments.settings.termux;

import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiSettings;

import org.junit.Test;

import java.util.LinkedHashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiParameterPreferencesFragmentHidingTest {

    private TaiModelSpec litertMultimodal(boolean speculative) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        caps.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        caps.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
        if (speculative) caps.add(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING);
        return new TaiModelSpec(
            "gemma-4-e2b-it-litert-lm",
            "Gemma 4 E2B",
            "chat",
            "test",
            "/models/gemma-4-e2b-it-litert-lm/model.litertlm",
            "test",
            0L,
            caps,
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            "gemma",
            null,
            4096,
            4,
            null
        );
    }

    private TaiModelSpec mobileActions() {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        caps.add(TaiModelSpec.CAPABILITY_TOOL_USE);
        caps.add(TaiModelSpec.CAPABILITY_MOBILE_ACTIONS);
        return new TaiModelSpec(
            TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M,
            "FunctionGemma Mobile Actions",
            "Mobile actions tool-call model",
            "test",
            "/models/functiongemma-270m-mobile-actions-litert-lm/model.litertlm",
            "test",
            0L,
            caps,
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            "gemma",
            null,
            1024,
            6,
            null
        );
    }

    private TaiModelSpec qwenAlwaysThinking() {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        caps.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
        return new TaiModelSpec(
            "Qwen3-4B-Thinking-2507", "Qwen3 Thinking", "chat", "test",
            "/models/Qwen3-4B-Thinking-2507/model.litertlm", "test", 0L, caps, false,
            null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            "qwen3", null, 4096, 3, null);
    }

    private TaiModelSpec mnnModel() {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        caps.add(TaiModelSpec.CAPABILITY_CODE);
        return new TaiModelSpec(
            "qwen2.5-coder-1.5b-instruct-mnn",
            "Qwen2.5 Coder MNN",
            "chat",
            "test",
            "/models/qwen2.5-coder-1.5b-instruct-mnn/config.json",
            "test",
            0L,
            caps,
            false,
            null,
            TaiModelSpec.BACKEND_MNN_LLM,
            TaiModelSpec.FORMAT_MNN,
            "qwen2.5",
            "int4",
            8192,
            4,
            null
        );
    }

    @Test
    public void thinkingParam_visibleOnlyForToggleableThinkingModels() {
        assertTrue(TaiParameterPreferencesFragment.shouldShowParameter(
            litertMultimodal(false), "gemma-4-e2b-it-litert-lm", TaiSettings.FIELD_ENABLE_THINKING, true));
        assertFalse(TaiParameterPreferencesFragment.shouldShowParameter(
            mnnModel(), "qwen2.5-coder-1.5b-instruct-mnn", TaiSettings.FIELD_ENABLE_THINKING, true));
        assertFalse(TaiParameterPreferencesFragment.shouldShowParameter(
            qwenAlwaysThinking(), "Qwen3-4B-Thinking-2507", TaiSettings.FIELD_ENABLE_THINKING, true));
        assertFalse(TaiParameterPreferencesFragment.shouldShowParameter(
            null, null, TaiSettings.FIELD_ENABLE_THINKING, false));
    }

    /**
     * The switch keys off {@link TaiModelSpec#capabilities} (the endpoint set), and
     * {@code TaiModelSpec} only promotes speculative decoding into that set after reading
     * capability flags out of the real {@code .litertlm} package. A JVM test has no package and
     * no native reader, so every case here is a hidden case — including a spec whose *source*
     * metadata declares the capability. That last one is the assertion worth having: declared
     * intent alone must not surface a runtime override.
     *
     * The shown case needs a real installed package; it is covered by instrumentation, not here.
     */
    @Test
    public void speculativeDecodingParam_hiddenWithoutAReadablePackageThatAdvertisesIt() {
        assertFalse("LiteRT spec not declaring the capability", TaiParameterPreferencesFragment.shouldShowParameter(
            litertMultimodal(false), "gemma-4-e2b-it-litert-lm", TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, true));
        assertFalse("source metadata declares it but no package backs it",
            litertMultimodal(true).capabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING));
        assertFalse("source metadata alone must not surface the switch",
            TaiParameterPreferencesFragment.shouldShowParameter(
                litertMultimodal(true), "gemma-4-e2b-it-litert-lm", TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, true));
        assertFalse("MNN speculative_type is package-fixed, never a runtime toggle",
            TaiParameterPreferencesFragment.shouldShowParameter(
                mnnModel(), "qwen2.5-coder-1.5b-instruct-mnn", TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, true));
        assertFalse("no resolved model", TaiParameterPreferencesFragment.shouldShowParameter(
            null, "gemma-4-e2b-it-litert-lm", TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, true));
    }

    @Test
    public void acceleratorParam_hiddenForCpuOnlyFunctionGemma() {
        assertFalse(TaiParameterPreferencesFragment.shouldShowParameter(
            mobileActions(), TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, TaiSettings.FIELD_ACCELERATOR, true));
        assertTrue(TaiParameterPreferencesFragment.shouldShowParameter(
            litertMultimodal(false), "gemma-4-e2b-it-litert-lm", TaiSettings.FIELD_ACCELERATOR, true));
    }

    @Test
    public void acceleratorParam_visibleForGlobalScreen() {
        assertTrue(TaiParameterPreferencesFragment.shouldShowParameter(
            null, null, TaiSettings.FIELD_ACCELERATOR, false));
    }
}
