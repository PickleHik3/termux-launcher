package com.termux.app.assistant;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The settings shortlist, by meaning when the phone has an embedding model and by keywords when it
 * has not (ADR 0035). The corpus vectors are read from app-private storage on the first turn after
 * the page opens, rebuilt only when the corpus hash or the embedding model differs from what the
 * file holds, and dropped from memory by {@link #release} when the page is left. The embedding
 * model itself is closed as soon as the person's words are embedded, before the chat model runs.
 * Any failure on the embedding path answers with the keyword lookup instead.
 */
final class AssistantSettingsLookup {
    private static final String LOG_TAG = "AssistantSettingsLookup";
    private static final int BATCH = 32;
    private static final int LINK_RANK = AssistantSettingsTurn.SHORTLIST;

    @Nullable private final Embedder embedder;
    private final Supplier<String> corpusSource;
    private final File cacheFile;

    @Nullable private SettingsCorpus corpus;
    @Nullable private float[][] vectors;
    @Nullable private String vectorsModel;
    /** How many times the corpus was embedded, for tests. */
    private int rebuilds;

    AssistantSettingsLookup(@Nullable Embedder embedder, @NonNull Supplier<String> corpusSource,
                            @NonNull File cacheFile) {
        this.embedder = embedder;
        this.corpusSource = corpusSource;
        this.cacheFile = cacheFile;
    }

    /** The shortlist for {@code message}: by embeddings when they answer, else by keywords. */
    @NonNull
    synchronized AssistantSettingsTurn.Shortlist lookup(@NonNull SettingsRegistry registry,
                                                       @NonNull String message) {
        if (embedder != null) {
            String modelId = embedder.modelId();
            if (modelId != null && !modelId.isEmpty()) {
                try {
                    AssistantSettingsTurn.Shortlist byMeaning = byMeaning(registry, message, modelId);
                    if (byMeaning != null) return byMeaning;
                } catch (Exception | LinkageError e) {
                    Log.w(LOG_TAG, "Embedding lookup failed, using keywords: " + e);
                } finally {
                    embedder.unload();
                }
            }
        }
        return AssistantSettingsTurn.lookup(registry, message);
    }

    /** Drops the vectors from memory; the file stays. Called when the page is left. */
    synchronized void release() {
        vectors = null;
        vectorsModel = null;
        corpus = null;
    }

    synchronized int rebuilds() {
        return rebuilds;
    }

    @Nullable
    private AssistantSettingsTurn.Shortlist byMeaning(@NonNull SettingsRegistry registry,
                                                      @NonNull String message,
                                                      @NonNull String modelId) throws Exception {
        float[][] docs = vectorsFor(modelId);
        SettingsCorpus c = corpus;
        if (docs == null || c == null) return null;
        float[][] asked = embedder.embed(Collections.singletonList(message), true);
        if (asked.length != 1 || asked[0].length != docs[0].length) return null;
        float[] query = normalized(asked[0]);

        Map<String, Float> score = new HashMap<>();
        for (int i = 0; i < docs.length; i++) score.put(c.ids.get(i), dot(query, docs[i]));
        List<SettingsRegistry.Entry> entries = new ArrayList<>();
        for (SettingsRegistry.Entry entry : registry.list()) {
            if (score.containsKey(entry.declaration.id)) entries.add(entry);
        }
        if (entries.isEmpty()) return null;
        entries.sort((a, b) -> Float.compare(score.get(b.declaration.id), score.get(a.declaration.id)));

        List<AssistantSettingsTurn.Candidate> offered = new ArrayList<>();
        List<SettingDeclaration> linked = new ArrayList<>();
        for (int rank = 0; rank < entries.size(); rank++) {
            SettingsRegistry.Entry entry = entries.get(rank);
            SettingDeclaration d = entry.declaration;
            if (d.mayOffer) {
                if (offered.size() < AssistantSettingsTurn.SHORTLIST) {
                    offered.add(new AssistantSettingsTurn.Candidate(d, entry.value));
                }
            } else if (rank < LINK_RANK && linked.size() < 2) {
                linked.add(d);
            }
        }
        return new AssistantSettingsTurn.Shortlist(offered, linked);
    }

    /** The corpus vectors for {@code modelId}: in memory, else from the file, else embedded now. */
    @Nullable
    private float[][] vectorsFor(@NonNull String modelId) throws Exception {
        if (corpus == null) corpus = SettingsCorpus.parse(corpusSource.get());
        SettingsCorpus c = corpus;
        if (c.ids.isEmpty()) return null;
        if (vectors != null && modelId.equals(vectorsModel)) return vectors;
        float[][] saved = SettingsVectorCache.read(cacheFile, modelId, c.hash, c.ids.size());
        if (saved == null) {
            saved = embedCorpus(c);
            rebuilds++;
            try {
                SettingsVectorCache.write(cacheFile, modelId, c.hash, saved);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Vector cache not saved: " + e);
            }
        }
        vectors = saved;
        vectorsModel = modelId;
        return saved;
    }

    @NonNull
    private float[][] embedCorpus(@NonNull SettingsCorpus c) throws Exception {
        float[][] out = new float[c.texts.size()][];
        for (int from = 0; from < out.length; from += BATCH) {
            int to = Math.min(out.length, from + BATCH);
            float[][] part = embedder.embed(c.texts.subList(from, to), false);
            if (part.length != to - from) throw new IOException("embedder answered " + part.length + " of " + (to - from));
            for (int i = 0; i < part.length; i++) out[from + i] = normalized(part[i]);
        }
        int dims = out[0].length;
        for (float[] vector : out) if (vector.length != dims) throw new IOException("vector sizes differ");
        return out;
    }

    @NonNull
    private static float[] normalized(@NonNull float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        float norm = (float) Math.sqrt(sum);
        float[] out = new float[v.length];
        if (norm == 0f) return out;
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }

    private static float dot(@NonNull float[] a, @NonNull float[] b) {
        float sum = 0f;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
}
