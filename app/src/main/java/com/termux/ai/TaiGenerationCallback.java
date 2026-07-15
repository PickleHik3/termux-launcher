package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;

public interface TaiGenerationCallback {
    void onToken(@NonNull String text);
    /** Lets a consumer stop native decoding after it has recognized an app-side terminal condition. */
    default boolean shouldCancelGeneration() {
        return false;
    }
    default void onToolCalls(@NonNull JSONArray toolCalls) {
    }
    void onComplete(@NonNull String fullText);
    void onError(@NonNull Throwable throwable);
}
