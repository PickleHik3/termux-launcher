package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What is resident in the {@code :tai_runtime} process right now: every loaded chat model, every
 * embedding interpreter, and the memory the process keeps once it has loaded anything at all.
 *
 * <p>One instance is shared by the router and its four runtimes ({@link MultiBackendTaiRuntime}
 * creates it). Each runtime registers on a successful load and deregisters wherever it closes —
 * explicit unload, idle timer, keep-warm expiry, the close before a replacing load, the low-memory
 * release — so the table is written at the same points the runtimes already write their own
 * "loaded" state, and nothing else keeps a notion of what is resident. The budget reads it to know
 * what a load will replace ({@link #creditedAvailable}); status reports it as {@code residents}.
 *
 * <p>Threading. Writes take this object's monitor for the few microseconds it takes to copy the
 * list; the table itself is an immutable list behind a volatile field, so {@link #snapshot()} and
 * every other read never block — not on a write, and never on the router's load lock, which this
 * class knows nothing about. Runtimes call in while holding their own monitor; the registry never
 * calls out, so it is a leaf lock and cannot take part in a cycle.
 */
public final class TaiResidency {

    public enum Kind { CHAT, EMBEDDING, STT, RUNTIME }

    private static final long MIB = 1024L * 1024L;

    /** Id of the process-baseline entry; there is at most one and it never deregisters. */
    public static final String RUNTIME_ID = "tai-runtime";

    /**
     * What {@code :tai_runtime} holds after its first chat load and never gives back: measured on
     * pong 2026-09-24 at 329–336 MB of anonymous memory after each of three load/unload cycles,
     * against 15 MB before the first load. Memory the LiteRT-LM / driver allocators keep, not a
     * leak, so it is counted from the first chat load until the process exits.
     */
    public static final long RUNTIME_BASELINE_BYTES = 330L * MIB;

    /**
     * Embedding footprint per model-file byte, used until a measured load is on record. A LiteRT
     * {@code .tflite} interpreter maps the flatbuffer and adds its tensor arena (about 1.3× the
     * file); MNN loads the package's weights about as they are on disk (1.0×).
     */
    static final long LITERT_EMBEDDING_FACTOR_TENTHS = 13L;
    static final long MNN_EMBEDDING_FACTOR_TENTHS = 10L;

    /** One resident. Immutable; {@link #setBusy} replaces the entry rather than mutating it. */
    public static final class Entry {
        @NonNull public final String modelId;
        @NonNull public final Kind kind;
        @NonNull public final String backend;
        @NonNull public final String accelerator;
        /** Context tokens for chat, sequence length for embeddings, audio seconds for STT; 0 when n/a. */
        public final int window;
        public final long estimatedBytes;
        /** The MemAvailable drop the load measured ({@link TaiLoadMeter}); {@code null} when it could not be. */
        @Nullable public final Long measuredBytes;
        public final long lastUsedMs;
        /** Generating, embedding or transcribing right now. */
        public final boolean busy;

        public Entry(@NonNull String modelId, @NonNull Kind kind, @NonNull String backend, @NonNull String accelerator,
                     int window, long estimatedBytes, @Nullable Long measuredBytes, long lastUsedMs, boolean busy) {
            this.modelId = modelId;
            this.kind = kind;
            this.backend = backend;
            this.accelerator = accelerator;
            this.window = window;
            this.estimatedBytes = estimatedBytes;
            this.measuredBytes = measuredBytes;
            this.lastUsedMs = lastUsedMs;
            this.busy = busy;
        }

        /** A chat model as its backend loaded it: the real accelerator and window, not the plan's. */
        @NonNull
        public static Entry chat(@NonNull TaiModelSpec spec, @NonNull String backend, @NonNull String accelerator, int contextTokens) {
            return new Entry(spec.id, Kind.CHAT, backend, accelerator, contextTokens,
                chatEstimateBytes(spec, accelerator, contextTokens), null, System.currentTimeMillis(), false);
        }

        /** An embedding interpreter; both backends run these on the CPU. */
        @NonNull
        public static Entry embedding(@NonNull TaiModelSpec spec, int sequenceLength) {
            return new Entry(spec.id, Kind.EMBEDDING, spec.backend, "cpu", sequenceLength,
                embeddingEstimateBytes(spec), null, System.currentTimeMillis(), false);
        }

        /** The bytes the budget counts for this resident: measured when known, else the estimate. */
        public long bytes() {
            return measuredBytes != null ? measuredBytes : estimatedBytes;
        }

        @NonNull
        Entry withBusy(boolean nowBusy, long nowMs) {
            return new Entry(modelId, kind, backend, accelerator, window, estimatedBytes, measuredBytes, nowMs, nowBusy);
        }

        /** The same resident with the MemAvailable drop its load measured; {@code null} leaves it unmeasured. */
        @NonNull
        public Entry withMeasured(@Nullable Long nowMeasuredBytes) {
            return new Entry(modelId, kind, backend, accelerator, window, estimatedBytes, nowMeasuredBytes, lastUsedMs, busy);
        }

        boolean matches(@NonNull Kind otherKind, @NonNull String otherModelId) {
            return kind == otherKind && modelId.equals(otherModelId);
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", modelId);
            json.put("kind", kind.name().toLowerCase(Locale.ROOT));
            json.put("backend", backend);
            json.put("accelerator", accelerator);
            json.put("window", window);
            json.put("estimatedBytes", estimatedBytes);
            json.put("measuredBytes", measuredBytes == null ? JSONObject.NULL : measuredBytes);
            json.put("busy", busy);
            json.put("lastUsedMs", lastUsedMs);
            return json;
        }
    }

    /** Written under this object's monitor, read without it. Always an unmodifiable list. */
    private volatile List<Entry> entries = Collections.emptyList();

    /**
     * Records a resident, replacing an entry of the same kind and id. The first chat model to
     * register also brings in the {@link #RUNTIME_BASELINE_BYTES} process entry, which stays.
     */
    public synchronized void register(@NonNull Entry entry) {
        ArrayList<Entry> next = new ArrayList<>(entries.size() + 2);
        boolean hasBaseline = false;
        for (Entry existing : entries) {
            if (existing.matches(entry.kind, entry.modelId)) continue;
            if (existing.kind == Kind.RUNTIME) hasBaseline = true;
            next.add(existing);
        }
        next.add(entry);
        if (entry.kind == Kind.CHAT && !hasBaseline) {
            next.add(new Entry(RUNTIME_ID, Kind.RUNTIME, "process", "cpu", 0, RUNTIME_BASELINE_BYTES, null,
                entry.lastUsedMs, false));
        }
        entries = Collections.unmodifiableList(next);
    }

    /** Forgets a resident; a no-op for an id that is not registered. The RUNTIME entry never leaves. */
    public synchronized void deregister(@NonNull Kind kind, @NonNull String modelId) {
        if (kind == Kind.RUNTIME) return;
        ArrayList<Entry> next = new ArrayList<>(entries.size());
        for (Entry existing : entries) {
            if (!existing.matches(kind, modelId)) next.add(existing);
        }
        if (next.size() == entries.size()) return;
        entries = Collections.unmodifiableList(next);
    }

    /** Marks a resident busy or idle and stamps its last use; a no-op for an unregistered id. */
    public synchronized void setBusy(@NonNull Kind kind, @Nullable String modelId, boolean busy) {
        if (modelId == null) return;
        long now = System.currentTimeMillis();
        ArrayList<Entry> next = new ArrayList<>(entries.size());
        boolean changed = false;
        for (Entry existing : entries) {
            if (existing.matches(kind, modelId)) {
                next.add(existing.withBusy(busy, now));
                changed = true;
            } else {
                next.add(existing);
            }
        }
        if (changed) entries = Collections.unmodifiableList(next);
    }

    /** The table as it is right now; never blocks, never changes after it is returned. */
    @NonNull
    public List<Entry> snapshot() {
        return entries;
    }

    public boolean isResident(@NonNull Kind kind, @NonNull String modelId) {
        for (Entry entry : entries) {
            if (entry.matches(kind, modelId)) return true;
        }
        return false;
    }

    /** Bytes held by residents of {@code kind}, on {@code backend} when given, on any when {@code null}. */
    public long bytes(@NonNull Kind kind, @Nullable String backend) {
        return bytes(entries, kind, backend);
    }

    private static long bytes(@NonNull List<Entry> residents, @NonNull Kind kind, @Nullable String backend) {
        long total = 0L;
        for (Entry entry : residents) {
            if (entry.kind != kind) continue;
            if (backend != null && !backend.equals(entry.backend)) continue;
            total += entry.bytes();
        }
        return total;
    }

    /**
     * The memory a load of {@code kind} on {@code backend} has to spend: what is free now plus what
     * the load closes first. The router holds one chat model, and every chat load closes it before
     * the new one initializes (whichever backend it is on), so a chat load is credited every CHAT
     * resident. Each backend's embedding runtime holds one model and replaces it only with another
     * for the same backend, so an embedding load is credited that backend's EMBEDDING resident.
     * Nothing else is credited: other residents stay, and {@code availableBytes} already reflects
     * them, the RUNTIME baseline included. Unknown free memory ({@code <= 0}) stays unknown.
     */
    public static long creditedAvailable(long availableBytes, @NonNull List<Entry> residents,
                                         @NonNull Kind kind, @Nullable String backend) {
        if (availableBytes <= 0L) return availableBytes;
        switch (kind) {
            case CHAT:
                return availableBytes + bytes(residents, Kind.CHAT, null);
            case EMBEDDING:
                return availableBytes + bytes(residents, Kind.EMBEDDING, backend);
            default:
                return availableBytes;
        }
    }

    /**
     * The residents a load of {@code kind} on {@code backend} may close to make room, in the order
     * the budget evicts them: embeddings, then STT, then idle chat; the least recently used first
     * within a kind. Left out: anything busy, the RUNTIME baseline, and what {@link
     * #creditedAvailable} already counts as replaced — every CHAT resident for a chat load (so a
     * chat load never "evicts" chat; it replaces it), and this backend's EMBEDDING resident for an
     * embedding load.
     */
    @NonNull
    public static List<Entry> evictionCandidates(@NonNull List<Entry> residents, @NonNull Kind kind, @Nullable String backend) {
        ArrayList<Entry> ordered = new ArrayList<>();
        for (Kind victimKind : new Kind[] {Kind.EMBEDDING, Kind.STT, Kind.CHAT}) {
            if (victimKind == Kind.CHAT && kind == Kind.CHAT) continue;
            ArrayList<Entry> ofKind = new ArrayList<>();
            for (Entry entry : residents) {
                if (entry.kind != victimKind || entry.busy) continue;
                if (kind == Kind.EMBEDDING && victimKind == Kind.EMBEDDING
                        && backend != null && backend.equals(entry.backend)) continue;
                ofKind.add(entry);
            }
            Collections.sort(ofKind, (a, b) -> Long.compare(a.lastUsedMs, b.lastUsedMs));
            ordered.addAll(ofKind);
        }
        return Collections.unmodifiableList(ordered);
    }

    /** What a chat load of this spec costs, by the budget's model; the one formula the plan and the registry share. */
    public static long chatEstimateBytes(@NonNull TaiModelSpec spec, @NonNull String accelerator, int contextTokens) {
        boolean encoders = spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
            || spec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        return TaiLoadBudget.estimateBytes(spec.backend, accelerator, fileBytes(spec), encoders, contextTokens);
    }

    /** What an embedding load of this spec costs: the model file times the backend's factor. */
    public static long embeddingEstimateBytes(@NonNull TaiModelSpec spec) {
        long factorTenths = TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)
            ? MNN_EMBEDDING_FACTOR_TENTHS : LITERT_EMBEDDING_FACTOR_TENTHS;
        return fileBytes(spec) * factorTenths / 10L;
    }

    /**
     * The model's size on disk. An MNN package points at its config.json or at the package
     * directory, so its weights are summed from the files in that directory — measured first,
     * because a downloaded package's spec records only config.json's length as its size. Otherwise
     * the catalog's figure when it has one, else the file itself.
     */
    public static long fileBytes(@NonNull TaiModelSpec spec) {
        File file = spec.localPath == null || spec.localPath.trim().isEmpty() ? null : new File(spec.localPath);
        File dir = file == null ? null
            : file.isDirectory() ? file : "config.json".equals(file.getName()) ? file.getParentFile() : null;
        File[] children = dir == null ? null : dir.listFiles();
        if (children != null) {
            long total = 0L;
            for (File child : children) {
                if (child.isFile()) total += child.length();
            }
            if (total > 0L) return total;
        }
        if (spec.sizeBytes > 0L) return spec.sizeBytes;
        return file == null || file.isDirectory() ? 0L : file.length();
    }

    @NonNull
    public JSONArray toJson() throws JSONException {
        JSONArray json = new JSONArray();
        for (Entry entry : entries) json.put(entry.toJson());
        return json;
    }
}
