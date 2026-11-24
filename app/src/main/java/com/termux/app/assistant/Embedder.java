package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The Assistant's way to turn text into vectors, over the runtime's embeddings path. The settings
 * lookup talks to this so it can be tested without a model. An implementation loads its model when
 * {@link #embed} first needs it and gives it back in {@link #unload}.
 */
interface Embedder {
    /** The embedding model that would answer, or null when the phone has none. It keys the vector cache. */
    @Nullable
    String modelId();

    /**
     * One vector per text, in order. {@code query} is true for the person's words and false for the
     * corpus. Throws when the model cannot answer; the lookup then falls back to keywords.
     */
    @NonNull
    float[][] embed(@NonNull List<String> texts, boolean query) throws Exception;

    /** Closes the embedding model so its memory goes back before the chat model runs. */
    void unload();
}
