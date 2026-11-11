package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.ai.FeatureRequest;
import com.termux.ai.TaiFunction;

import org.json.JSONObject;

/** The real transport: the Assistant's answers go through Feature request, as its function. */
public final class TaiChatTransport implements ChatTransport {
    private final Context appContext;

    public TaiChatTransport(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Call start(@NonNull String body, @NonNull FeatureRequest.Sink sink) throws Exception {
        FeatureRequest.Handle handle = FeatureRequest.get(appContext).start(
            FeatureRequest.Call.chat(TaiFunction.ASSISTANT, new JSONObject(body))
                .priority(FeatureRequest.Priority.INTERACTIVE), sink);
        return new Call() {
            @Override
            public void cancel() {
                handle.cancel();
            }

            @Override
            public void await() throws Exception {
                handle.await();
            }
        };
    }
}
