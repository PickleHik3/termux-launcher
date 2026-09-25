package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiModelSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * What a model can do and where it runs, read off its name. These are the importer's long-standing
 * guesses from known LiteRT and MNN publications; the flow shows them as read-only chips and lets
 * Advanced correct them, instead of asking the user to declare them first.
 */
final class TaiImportGuess {
    private TaiImportGuess() {
    }

    @NonNull
    static LinkedHashSet<String> capabilities(@Nullable String source) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        boolean embedding = value.endsWith(".tflite")
            || value.contains("embeddinggemma-300m")
            || value.contains("qwen3-embedding-0.6b-mnn")
            || value.contains("qwen3-embedding-4b-mnn")
            || value.contains("qwen3-embedding-8b-mnn")
            || value.contains("bge-")
            || value.contains("e5-")
            || value.contains("gte-")
            || value.contains("jina-embedding")
            || value.contains("embed");
        if (embedding) {
            capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
            // A raw .tflite is only ever served by the embedding runtime; no chat claim survives it.
            if (value.endsWith(".tflite")) return capabilities;
        } else {
            capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        }
        boolean gemma4 = value.contains("gemma-4-e2b") || value.contains("gemma-4-e4b");
        if (value.contains("-vl") || value.contains("_vl") || value.contains("vision")
            || value.contains("image") || value.contains("multimodal") || gemma4) {
            capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        }
        if (value.contains("audio") || value.contains("speech") || gemma4) {
            capabilities.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        }
        if (value.contains("functiongemma") || value.contains("function-calling")
            || value.contains("tool-use") || value.contains("tool_use")) {
            capabilities.add(TaiModelSpec.CAPABILITY_TOOL_USE);
        }
        if (value.contains("coder") || value.contains("code-")) capabilities.add(TaiModelSpec.CAPABILITY_CODE);
        if (value.contains("deepseek-r1") || value.contains("reasoning")) capabilities.add("reasoning");
        if (qwenThinking(value)) {
            capabilities.add("reasoning");
            capabilities.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
        }
        if (value.contains("qwen") || value.contains("multilingual")) capabilities.add("multilingual");
        return capabilities;
    }

    /**
     * The accelerators a load may try, best first. Only publications known to run on the GPU list
     * it; everything else is CPU, the choice that never fails a first load.
     */
    @NonNull
    static List<String> accelerators(@Nullable String source) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        boolean mnn = value.contains("-mnn") || value.contains("_mnn") || value.endsWith("config.json");
        boolean knownGpu = !mnn && (qwenThinking(value)
            || value.contains("gemma-4-e2b") || value.contains("gemma-4-e4b")
            || value.contains("deepseek-r1-distill-qwen-1.5b")
            || value.contains("qwen2.5-1.5b-instruct"));
        return knownGpu ? Arrays.asList("gpu", "cpu") : Collections.singletonList("cpu");
    }

    /** Qwen3's thinking publications: the one family whose thought markers the importer knows. */
    static boolean qwenThinking(@Nullable String identity) {
        String value = identity == null ? "" : identity.toLowerCase(Locale.ROOT);
        return value.contains("qwen3") && value.contains("thinking");
    }
}
