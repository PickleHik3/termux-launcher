package com.alibaba.mnnllm.android.llm;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;

public final class LlmSession {
    private long nativePtr;

    static {
        System.loadLibrary("MNN");
        System.loadLibrary("mnnllmapp");
    }

    public synchronized void load(
        @NonNull String configPath,
        @Nullable List<String> history,
        @NonNull String mergedConfigJson,
        @NonNull String extraConfigJson
    ) {
        if (nativePtr != 0L) release();
        nativePtr = initNative(configPath, history, mergedConfigJson, extraConfigJson);
        if (nativePtr == 0L) throw new IllegalStateException("MNN model load failed.");
    }

    public synchronized boolean isLoaded() {
        return nativePtr != 0L;
    }

    @NonNull
    public synchronized HashMap<String, Object> generate(
        @NonNull String prompt,
        @NonNull GenerateProgressListener listener
    ) {
        ensureLoaded();
        return submitNative(nativePtr, prompt, true, listener);
    }

    @NonNull
    public synchronized HashMap<String, Object> generateHistory(
        @NonNull List<Pair<String, String>> history,
        @NonNull GenerateProgressListener listener
    ) {
        ensureLoaded();
        return submitFullHistoryNative(nativePtr, history, listener);
    }

    public synchronized void reset() {
        if (nativePtr != 0L) resetNative(nativePtr);
    }

    public synchronized void updateMaxNewTokens(int maxNewTokens) {
        if (nativePtr != 0L) updateMaxNewTokensNative(nativePtr, maxNewTokens);
    }

    public synchronized void updateSystemPrompt(@NonNull String systemPrompt) {
        if (nativePtr != 0L) updateSystemPromptNative(nativePtr, systemPrompt);
    }

    public synchronized void updateConfig(@NonNull String configJson) {
        if (nativePtr != 0L) updateConfigNative(nativePtr, configJson);
    }

    @NonNull
    public synchronized String dumpConfig() {
        return nativePtr == 0L ? "{}" : dumpConfigNative(nativePtr);
    }

    @NonNull
    public synchronized String debugInfo() {
        return nativePtr == 0L ? "" : getDebugInfoNative(nativePtr);
    }

    public synchronized void release() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr);
            nativePtr = 0L;
        }
    }

    private void ensureLoaded() {
        if (nativePtr == 0L) throw new IllegalStateException("MNN model is not loaded.");
    }

    private native long initNative(
        String configPath,
        List<String> history,
        String mergedConfigJson,
        String extraConfigJson
    );

    private native HashMap<String, Object> submitNative(
        long instanceId,
        String input,
        boolean keepHistory,
        GenerateProgressListener listener
    );

    private native HashMap<String, Object> submitFullHistoryNative(
        long nativePtr,
        List<Pair<String, String>> history,
        GenerateProgressListener listener
    );

    private native void resetNative(long instanceId);
    private native String getDebugInfoNative(long instanceId);
    private native void releaseNative(long instanceId);
    private native void updateMaxNewTokensNative(long llmPtr, int maxNewTokens);
    private native void updateSystemPromptNative(long llmPtr, String systemPrompt);
    private native void updateConfigNative(long llmPtr, String configJson);
    private native String dumpConfigNative(long llmPtr);
}
