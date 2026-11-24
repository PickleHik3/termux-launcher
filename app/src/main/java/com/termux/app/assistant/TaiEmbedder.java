package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * The real embedder: the runtime's {@code /v1/embeddings} path through {@link TaiManager}, with the
 * embedding model the phone's plan names for embeddings (EmbeddingGemma 2 when it is installed).
 * Blocking; call it off the main thread. The model loads on the first request.
 */
final class TaiEmbedder implements Embedder {
    private static final int MAX_BATCH = 64;

    private final Context appContext;

    TaiEmbedder(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Nullable
    @Override
    public String modelId() {
        try {
            return TaiFeaturePlans.forContext(appContext).plan(TaiFunction.EMBEDDINGS).modelId;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public float[][] embed(@NonNull List<String> texts, boolean query) throws Exception {
        if (texts.size() > MAX_BATCH) throw new IOException("batch too large");
        JSONObject body = new JSONObject()
            .put("input", new JSONArray(texts))
            .put("input_type", query ? "query" : "document");
        JSONObject result = TaiManager.getInstance(appContext).embeddings(body.toString());
        if (result.optInt("_statusCode", 200) >= 400) {
            throw new IOException("embeddings refused: " + result.optInt("_statusCode"));
        }
        JSONArray data = result.optJSONArray("data");
        if (data == null || data.length() != texts.size()) throw new IOException("embeddings returned no data");
        float[][] out = new float[texts.size()][];
        for (int i = 0; i < out.length; i++) {
            JSONObject item = data.optJSONObject(i);
            JSONArray vector = item == null ? null : item.optJSONArray("embedding");
            if (vector == null) throw new JSONException("no embedding at " + i);
            out[i] = new float[vector.length()];
            for (int j = 0; j < out[i].length; j++) out[i][j] = (float) vector.optDouble(j, 0.0);
        }
        return out;
    }

    @Override
    public void unload() {
        try {
            TaiManager.getInstance(appContext).unloadEmbedder();
        } catch (Exception | LinkageError ignored) {
            // The runtime closes an idle embedder on its own pressure watch; a failed ask costs nothing.
        }
    }
}
