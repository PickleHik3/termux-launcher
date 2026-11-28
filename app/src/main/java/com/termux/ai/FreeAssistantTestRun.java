package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The free online assistant's Test, one at a time (review of terl-window-ops, P1): a test takes
 * the endpoint, the key and the model in one snapshot on the main thread, where a save happens,
 * so its request never pairs the new key with the old endpoint; and a test that a newer test, a
 * save or a change of service has superseded has its answer dropped. Pure apart from reading the
 * settings it is handed.
 */
public final class FreeAssistantTestRun {

    /** One test's request: what it sends, as it stood when the test began. */
    public static final class Request {
        @NonNull public final String baseUrl;
        @Nullable public final String apiKey;
        @NonNull public final String modelId;
        final int generation;

        Request(@NonNull String baseUrl, @Nullable String apiKey, @NonNull String modelId, int generation) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.modelId = modelId;
            this.generation = generation;
        }
    }

    private int mGeneration;

    /** A new test from {@code settings} as they stand now; any test still running is superseded. */
    @NonNull
    public Request begin(@NonNull TaiRemoteSettings settings) {
        String baseUrl = settings.baseUrl();
        String apiKey = settings.apiKey();
        String modelId = settings.modelId();
        return new Request(baseUrl, apiKey, modelId, ++mGeneration);
    }

    /** A save or a change of service: the answer of any test still running is no longer this one's. */
    public void supersede() {
        mGeneration++;
    }

    /** Whether {@code request}'s answer still belongs on screen. */
    public boolean isCurrent(@NonNull Request request) {
        return request.generation == mGeneration;
    }
}
