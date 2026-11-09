package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.ai.TaiManager;

import org.json.JSONObject;

import java.io.IOException;

/**
 * The real transport: the existing runtime client behind {@link TaiManager}, which reaches the
 * engine in the {@code :tai_runtime} process. Nothing runs in this process.
 */
public final class TaiChatTransport implements ChatTransport {
    private final Context appContext;

    public TaiChatTransport(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public void stream(@NonNull String body, @NonNull Sink sink) throws Exception {
        TaiManager.getInstance(appContext).openAiChatCompletionsStream(body, new TaiManager.OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) throws IOException {
                sink.onEvent(event);
            }

            @Override
            public void onDone() throws IOException {
                sink.onDone();
            }
        });
    }

    /**
     * {@link TaiManager#cancelRuntime()} ends whatever generation the runtime is running, whoever
     * asked for it (the local API's included), and stops a running benchmark. The engine calls it
     * only while its own answer is the one running.
     */
    @Override
    public void cancel() throws Exception {
        TaiManager.getInstance(appContext).cancelRuntime();
    }
}
