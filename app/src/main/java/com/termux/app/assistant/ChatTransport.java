package com.termux.app.assistant;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;

/**
 * How a request reaches the model, and how a running answer is stopped. The engine talks to this so
 * it can be tested without the runtime.
 */
public interface ChatTransport {
    interface Sink {
        void onEvent(@NonNull JSONObject event) throws IOException;

        void onDone() throws IOException;
    }

    /** Blocks until the answer ends. Called on a background thread. A throw from the sink aborts it. */
    void stream(@NonNull String body, @NonNull Sink sink) throws Exception;

    /** Stops the generation that is running. Called on a background thread. */
    void cancel() throws Exception;
}
