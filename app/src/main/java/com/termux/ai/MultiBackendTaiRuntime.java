package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes the one loaded assistant between the LiteRT-LM and MNN backends and serves embeddings from
 * their own runtimes.
 *
 * <p>Locking. {@link #loadLock} serializes what changes the loaded model — load, keep-warm and unload,
 * including the backend switch — and is the only router lock held across native initialization or
 * close. Which backend is active is a volatile pointer, so {@link #getState()}, {@link #isModelLoaded},
 * {@link #cancel()} and the generation entry points take no router lock at all: a status poll or a
 * cancel that arrives mid-load reaches the backend's own short monitor instead of queuing behind the
 * load. Each backend keeps its monitor off its native load and generation paths, and each embedding
 * runtime serializes embed/close on its own monitor, so nothing here is held for the duration of
 * native work.
 *
 * <p>Residency. The router owns the one {@link TaiResidency} table and hands it to all four
 * runtimes, which register and deregister at their own load and close points — the backends close
 * engines on idle timers and after cancelled generations without passing through here, so the
 * router itself never writes the table. {@link #residency()} is read without any lock.
 */
public class MultiBackendTaiRuntime implements TaiRuntime {
    private final TaiRuntime liteRt;
    private final TaiRuntime mnn;
    private final LiteRtEmbeddingRuntime embeddings;
    private final MnnEmbeddingRuntime mnnEmbeddings;
    private final TaiResidency residency;
    /** Held across load, keep-warm and unload; never by a read, a cancel or a generation. */
    private final Object loadLock = new Object();
    /** The backend that owns the loaded model. Written under {@link #loadLock}, read without it. */
    private volatile TaiRuntime activeAssistant;
    /** The one router the runtime process runs; see {@link #processInstance()}. */
    @Nullable private static volatile MultiBackendTaiRuntime processInstance;

    public MultiBackendTaiRuntime(@NonNull Context context) {
        this(context, new TaiResidency());
        processInstance = this;
    }

    /**
     * The router {@link TaiManager} built for this process, for the service's memory watch, which
     * evicts through {@link #evict} and reads {@link #residency()} but reaches the runtime only
     * through the manager's request API otherwise. {@code null} until the manager has built it,
     * and always in a process that is not {@code :tai_runtime}; the test-seam constructor never
     * sets it.
     */
    @Nullable
    static MultiBackendTaiRuntime processInstance() {
        return processInstance;
    }

    private MultiBackendTaiRuntime(@NonNull Context context, @NonNull TaiResidency residency) {
        this(new LiteRtTaiRuntime(context, residency), new MnnTaiRuntime(context, residency), residency, context);
    }

    /** Test seam: the backends stand in for the native runtimes; embedding loads are not metered. */
    MultiBackendTaiRuntime(@NonNull TaiRuntime liteRt, @NonNull TaiRuntime mnn) {
        this(liteRt, mnn, new TaiResidency(), null);
    }

    private MultiBackendTaiRuntime(@NonNull TaiRuntime liteRt, @NonNull TaiRuntime mnn, @NonNull TaiResidency residency,
                                   @Nullable Context context) {
        this.liteRt = liteRt;
        this.mnn = mnn;
        this.residency = residency;
        embeddings = new LiteRtEmbeddingRuntime(residency, context);
        mnnEmbeddings = new MnnEmbeddingRuntime(residency, context);
        activeAssistant = liteRt;
    }

    /** Every resident model in this process. Lock-free; see {@link TaiResidency}. */
    @NonNull
    public TaiResidency residency() {
        return residency;
    }

    @NonNull @Override public TaiRuntimeState getState() {
        return activeAssistant.getState();
    }

    @Override public boolean isModelLoaded(@NonNull String modelId) {
        return runtimeForId(modelId).isModelLoaded(modelId);
    }

    @NonNull @Override public JSONObject load(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options) throws JSONException {
        synchronized (loadLock) {
            TaiRuntime target = runtimeForModel(model);
            JSONObject conflict = activateLocked(target);
            if (conflict != null) return conflict;
            return target.load(model, options);
        }
    }

    @NonNull @Override public JSONObject unload() throws JSONException {
        // A load in progress holds loadLock. Asking the loading backend to cancel first lets LiteRT
        // discard its engine as soon as native initialization returns, instead of finishing a load
        // that this unload would throw away a moment later.
        TaiRuntime loading = activeAssistant;
        if ("loading".equals(loading.getState().state)) loading.cancel();
        synchronized (loadLock) {
            JSONObject result = activeAssistant.unload();
            embeddings.close();
            mnnEmbeddings.close();
            return result;
        }
    }

    @NonNull @Override public JSONObject keepWarm(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        synchronized (loadLock) {
            TaiRuntime target = runtimeForModel(model);
            JSONObject conflict = activateLocked(target);
            if (conflict != null) return conflict;
            return target.keepWarm(model, options, minutes);
        }
    }

    /**
     * Closes the idle residents the budget chose to make room for a load, through the runtime that
     * holds each — an embedding runtime's {@code close()}, a chat backend's {@code unload()} — so
     * the registry deregisters them at the same points it always does. A resident that has become
     * busy or has gone since the plan was made is skipped, never interrupted. Returns the ids
     * actually evicted, for the load response's {@code evicted} list.
     */
    @NonNull
    public List<String> evict(@NonNull List<TaiResidency.Entry> victims) throws JSONException {
        ArrayList<String> evicted = new ArrayList<>();
        synchronized (loadLock) {
            for (TaiResidency.Entry victim : victims) {
                // Re-read the entry: the budget's plan or the pressure watch chose it lock-free,
                // and an embedding batch may have started on it since.
                TaiResidency.Entry current = residency.find(victim.kind, victim.modelId);
                if (current == null || current.busy) continue;
                switch (victim.kind) {
                    case EMBEDDING:
                        if (TaiModelSpec.BACKEND_MNN_LLM.equals(victim.backend)) mnnEmbeddings.close();
                        else embeddings.close();
                        break;
                    case CHAT: {
                        TaiRuntime holder = chatHolder(victim.modelId);
                        if (holder == null || holder.getState().activeGeneration) continue;
                        holder.unload();
                        break;
                    }
                    default:
                        continue;
                }
                if (!residency.isResident(victim.kind, victim.modelId)) evicted.add(victim.modelId);
            }
        }
        return evicted;
    }

    /** The backend holding chat model {@code modelId}, or {@code null} when neither does. */
    @Nullable
    private TaiRuntime chatHolder(@NonNull String modelId) {
        for (TaiRuntime candidate : new TaiRuntime[] {liteRt, mnn}) {
            TaiRuntimeState state = candidate.getState();
            if (state.loaded && modelId.equals(state.loadedModelId)) return candidate;
        }
        return null;
    }

    // Never waits on loadLock: a load-cancel is only worth anything while the load is still running.
    @NonNull @Override public JSONObject cancel() throws JSONException {
        return activeAssistant.cancel();
    }

    // Native generation is long-running. Do not hold any router lock while it runs, otherwise
    // cancel/unload cannot reach the active backend until generation has already finished.
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, system, user, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, system, user, options, callback); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, request, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, request, options, callback); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).complete(id, prompt, options); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).complete(id, prompt, options, callback); }

    @NonNull
    public JSONObject embed(@NonNull String modelId, @NonNull String input) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for the active LiteRT/MNN backends.");
        error.put("type", "invalid_request_error");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    // Each embedding runtime serializes embed() and close() on its own monitor, so a running batch
    // finishes before unload() can close it. No router lock here: status and cancel never queue
    // behind an embedding batch.
    @NonNull
    public JSONObject embed(@NonNull TaiModelSpec model, @NonNull List<String> inputs, int dimensions) throws JSONException {
        if (isLiteRtEmbeddingFlatbuffer(model)) return embeddings.embed(model, inputs, dimensions);
        if (isMnnEmbeddingModel(model)) return mnnEmbeddings.embed(model, inputs, dimensions);
        if (inputs.size() == 1 && dimensions <= 0) return embed(model.id, inputs.get(0));
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for model '" + model.id + "'.");
        error.put("type", "invalid_request_error");
        error.put("param", "model");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    /** Makes {@code target} the active backend, unloading the previous one. Caller holds loadLock. */
    @Nullable
    private JSONObject activateLocked(@NonNull TaiRuntime target) throws JSONException {
        TaiRuntime current = activeAssistant;
        if (target == current) return null;
        if (current.getState().activeGeneration) return error("generation_active", "Cancel active generation before switching AI backends.");
        current.unload();
        activeAssistant = target;
        return null;
    }

    private TaiRuntime runtimeForId(String id) {
        TaiRuntimeState mnnState = mnn.getState();
        if (mnnState.loadedModelId != null && mnnState.loadedModelId.equals(id)) return mnn;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry != null && TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return mnn;
        return activeAssistant;
    }

    private TaiRuntime runtimeForModel(TaiModelSpec model) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) return mnn;
        return liteRt;
    }

    private boolean isLiteRtEmbeddingFlatbuffer(@NonNull TaiModelSpec model) {
        String path = model.localPath == null ? "" : model.localPath.toLowerCase(Locale.ROOT);
        return TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)
            && model.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && path.endsWith(".tflite");
    }

    private boolean isMnnEmbeddingModel(@NonNull TaiModelSpec model) {
        String path = model.localPath == null ? "" : model.localPath.toLowerCase(Locale.ROOT);
        return TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)
            && model.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && path.endsWith("config.json");
    }

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", false); result.put("error", code); result.put("message", message); result.put("_statusCode", 409);
        return result;
    }
}
