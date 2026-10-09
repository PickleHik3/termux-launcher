package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The app-process half of a bench run that survives the runtime dying under it. The harness lives
 * in {@code :tai_runtime}, so when that process crashes mid-load the harness dies with it and
 * cannot write anything; this class watches the event stream from the app side, where the
 * {@link TaiBenchStore} is. It marks each entry in progress when {@code entry_start} arrives (the
 * marker is on disk before the load the harness runs next), appends each {@code entry_done}
 * record and clears the marker, and on {@code tai_runtime_crashed} writes a
 * {@code crashed} record for the entry that was on, then asks for the rest of the run again with
 * the finished entries listed in {@code skip}. Each crash finishes one more entry, so the loop
 * ends. A marker left on disk from an earlier run (the app itself died) is turned into a crashed
 * record before a new run begins.
 *
 * <p>Events the caller sees are the harness's own, with {@code index} and {@code total} of
 * {@code entry_start}/{@code entry_done} counted over the whole run rather than the part after
 * a restart; the crashed entry gets a synthetic {@code entry_done} so the run screen shows it.
 */
final class TaiBenchCrashRecovery {
    static final String CRASH_CODE = "tai_runtime_crashed";
    /** The runtime gives its GPU buffers back for about a second after a death; wait before asking again. */
    static final long DEFAULT_RESTART_PAUSE_MS = 1_500L;

    /** Sends one {@code benchRun} request and feeds its events to {@code sink}; {@link TaiRuntimeServiceClient#stream}. */
    interface Stream {
        void run(@NonNull JSONObject request, @NonNull TaiManager.OpenAiStreamSink sink) throws JSONException, IOException;
    }

    @NonNull private final TaiBenchStore store;
    @NonNull private final String appVersion;
    private final long restartPauseMs;

    TaiBenchCrashRecovery(@NonNull TaiBenchStore store, @NonNull String appVersion, long restartPauseMs) {
        this.store = store;
        this.appVersion = appVersion;
        this.restartPauseMs = restartPauseMs;
    }

    /** What one attempt (one stream call) saw. */
    private static final class Attempt {
        @Nullable JSONObject current;
        int currentIndex;
        int total;
        boolean crashed;
    }

    void run(@NonNull JSONObject request, @NonNull TaiManager.OpenAiStreamSink sink, @NonNull Stream stream)
        throws JSONException, IOException {
        // The app (or the runtime with it) died mid-load last time: say so before this run starts.
        store.recoverStaleMarker("The app closed while this model was loading.");
        String preset = request.optString("preset", "");
        Set<String> finished = new LinkedHashSet<>();
        int total = 0;
        try {
            while (true) {
                JSONObject attemptRequest = new JSONObject(request.toString());
                attemptRequest.put("skip", new JSONArray(finished));
                Attempt attempt = new Attempt();
                int offset = finished.size();
                stream.run(attemptRequest, new AttemptSink(sink, attempt, offset, preset, finished));
                total = Math.max(total, attempt.total);
                if (!attempt.crashed) return;
                if (finished.size() >= total) {
                    // The crash was on the last entry: nothing left to ask for, and the run is over.
                    sink.onEvent(new JSONObject().put("event", "done").put("at", System.currentTimeMillis())
                        .put("ok", true).put("benchVersion", TaiBenchSuite.BENCH_VERSION).put("preset", preset)
                        .put("entries", total).put("stopped", JSONObject.NULL));
                    sink.onDone();
                    return;
                }
                pause();
            }
        } finally {
            // A run that ended some other way left the runtime in charge of its own state; only a
            // death (which never reaches here with the marker still set) needs the marker kept.
            store.clearInProgress();
        }
    }

    private void pause() {
        if (restartPauseMs <= 0L) return;
        try {
            Thread.sleep(restartPauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class AttemptSink implements TaiManager.OpenAiStreamSink {
        @NonNull private final TaiManager.OpenAiStreamSink out;
        @NonNull private final Attempt attempt;
        private final int offset;
        @NonNull private final String preset;
        @NonNull private final Set<String> finished;

        AttemptSink(@NonNull TaiManager.OpenAiStreamSink out, @NonNull Attempt attempt, int offset,
                    @NonNull String preset, @NonNull Set<String> finished) {
            this.out = out;
            this.attempt = attempt;
            this.offset = offset;
            this.preset = preset;
            this.finished = finished;
        }

        @Override
        public void onEvent(@NonNull JSONObject event) throws IOException {
            try {
                String name = event.optString("event", "");
                if (!event.has("event") && event.has("error")) {
                    JSONObject tai = event.optJSONObject("tai");
                    String code = tai == null ? "" : tai.optString("error", "");
                    if (CRASH_CODE.equals(code) && attempt.current != null) {
                        crashed(tai);
                        return;
                    }
                } else if ("entry_start".equals(name)) {
                    attempt.current = event.optJSONObject("entry");
                    attempt.currentIndex = event.optInt("index", 0) + offset;
                    attempt.total = event.optInt("total", 0) + offset;
                    event.put("index", attempt.currentIndex).put("total", attempt.total);
                    if (attempt.current != null) store.markInProgress(marker(attempt.current));
                } else if ("entry_done".equals(name)) {
                    event.put("index", event.optInt("index", 0) + offset).put("total", event.optInt("total", 0) + offset);
                    JSONObject record = event.optJSONObject("record");
                    if (record != null) {
                        finished.add(TaiBenchStore.keyOf(record));
                        try {
                            store.append(record);
                            event.put("stored", true);
                        } catch (IOException e) {
                            event.put("stored", false).put("storeError", String.valueOf(e.getMessage()));
                        }
                    }
                    store.clearInProgress();
                    attempt.current = null;
                }
            } catch (JSONException e) {
                throw new IOException(e);
            }
            out.onEvent(event);
        }

        /** The runtime died on {@code attempt.current}: record it, tell the run screen, and let the loop go on. */
        private void crashed(@NonNull JSONObject tai) throws JSONException, IOException {
            JSONObject marker = store.inProgress();
            if (marker == null) marker = marker(attempt.current);
            String reason = tai.optString("message", "The AI runtime process died.");
            JSONObject record = TaiBenchStore.crashedRecord(marker, reason);
            JSONObject event = new JSONObject().put("event", "entry_done").put("at", System.currentTimeMillis())
                .put("index", attempt.currentIndex).put("total", attempt.total).put("record", record);
            try {
                store.append(record);
                event.put("stored", true);
            } catch (IOException e) {
                event.put("stored", false).put("storeError", String.valueOf(e.getMessage()));
            }
            store.clearInProgress();
            finished.add(TaiBenchStore.keyOf(record));
            attempt.current = null;
            attempt.crashed = true;
            out.onEvent(event);
        }

        @Override
        public void onDone() throws IOException {
            // A crash ends this stream with a done of its own; the run's done comes from the loop.
            if (!attempt.crashed) out.onDone();
        }

        @NonNull
        private JSONObject marker(@NonNull JSONObject entry) throws JSONException {
            return new JSONObject()
                .put("modelId", entry.optString("modelId", ""))
                .put("backend", entry.optString("backend", ""))
                .put("accelerator", entry.optString("accelerator", ""))
                .put("speculative", entry.optBoolean("speculative", false))
                .put("preset", preset)
                .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
                .put("appVersion", appVersion)
                .put("timestamp", System.currentTimeMillis());
        }
    }
}
