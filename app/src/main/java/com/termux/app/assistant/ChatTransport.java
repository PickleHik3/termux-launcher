package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.ai.FeatureRequest;

/**
 * How a request reaches the model. The engine talks to this so it can be tested without the
 * runtime. Each request has its own {@link Call}: stopping one never touches another.
 */
public interface ChatTransport {
    interface Call {
        /** Stops this request only; a waiting one leaves the queue. May be called from any thread. */
        void cancel();

        /** Blocks until the answer ends. A throw from the sink aborts it. */
        void await() throws Exception;
    }

    /** Starts the request; events reach the sink as they come. */
    @NonNull
    Call start(@NonNull String body, @NonNull FeatureRequest.Sink sink) throws Exception;
}
