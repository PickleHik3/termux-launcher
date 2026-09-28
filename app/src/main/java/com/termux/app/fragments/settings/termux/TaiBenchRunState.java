package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiBenchSuite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Run screen shows, reduced from the harness's event stream ({@code TaiBenchHarness}:
 * {@code entry_start}, {@code phase_start}, {@code token}, {@code check_start},
 * {@code phase_done}, {@code paused}, {@code skipped}, {@code cache_rebuilt}, {@code error},
 * {@code entry_done}, {@code done}) plus the few synthetic events {@link TaiBenchSession} adds
 * around it: the downloads that come first ({@code download_start}, {@code download_progress},
 * {@code download_done}), the phone's conditions sampled every few seconds ({@code conditions}),
 * the leaderboard after each entry lands ({@code leaderboard}), a Stop tap
 * ({@code stop_requested}) and the stream ending without a {@code done} ({@code session_end}).
 *
 * <p>Plain Java over JSON so the table of event sequences in {@code TaiBenchRunStateTest} drives
 * it directly; no clock, no Android type. {@link #apply} is called on one thread (the main
 * thread, by the session) and every read happens there too, so nothing here is synchronised.
 */
public final class TaiBenchRunState {
    /** Where the run as a whole stands. */
    public enum Phase { IDLE, DOWNLOADING, RUNNING, WAITING, STOPPING, DONE, STOPPED, FAILED }

    public enum EntryStatus { PENDING, RUNNING, DONE, SKIPPED, STOPPED }

    public enum StepStatus { PENDING, RUNNING, DONE, FAILED }

    /** Samples the writing sparkline keeps; at 20 token events a second this is a minute. */
    static final int SERIES_CAPACITY = 240;

    /** A model the run was asked for; a row of the stepper until its entries arrive. */
    public static final class Planned {
        @NonNull public final String modelId;
        @NonNull public final String displayName;
        /** Not installed when the run began: downloaded first, then benched. */
        public final boolean download;

        public Planned(@NonNull String modelId, @NonNull String displayName, boolean download) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.download = download;
        }
    }

    /** One leading download step. */
    public static final class Download {
        @NonNull public final String modelId;
        @NonNull public final String displayName;
        public long bytesRead;
        public long totalBytes;
        @NonNull public StepStatus status = StepStatus.RUNNING;
        @Nullable public String reason;

        Download(@NonNull String modelId, @NonNull String displayName) {
            this.modelId = modelId;
            this.displayName = displayName;
        }
    }

    /** One phase of one entry, as the stepper's sub-line shows it. */
    public static final class Step {
        @NonNull public final String phase;
        @NonNull public StepStatus status = StepStatus.RUNNING;
        public int run;
        public int runs;
        @Nullable public JSONObject metrics;

        Step(@NonNull String phase, int runs) {
            this.phase = phase;
            this.runs = runs;
        }
    }

    /** One leaderboard entry the run does: a model on one processor. */
    public static final class Entry {
        public final int index;
        public final int total;
        @NonNull public final String modelId;
        @NonNull public final String backend;
        @NonNull public final String accelerator;
        public final boolean speculative;
        @NonNull public final String key;
        @NonNull public String displayName;
        @NonNull public EntryStatus status = EntryStatus.RUNNING;
        @NonNull public final Map<String, Step> steps = new LinkedHashMap<>();
        @Nullable public String currentPhase;
        /** The record the store kept, once {@code entry_done} arrived. */
        @Nullable public JSONObject record;
        @Nullable public String reason;
        @Nullable public String verdict;
        public double writingTps;
        /** The entry's place on the leaderboard once it landed there; {@code 0} until then or when unranked. */
        public int rank;
        public boolean newBest;
        public boolean cacheRebuilt;

        Entry(int index, int total, @NonNull JSONObject plan, @NonNull String displayName) {
            this.index = index;
            this.total = total;
            this.modelId = plan.optString("modelId", "");
            this.backend = plan.optString("backend", "");
            this.accelerator = plan.optString("accelerator", "");
            this.speculative = plan.optBoolean("speculative", false);
            String key = plan.optString("key", "");
            this.key = key.isEmpty() ? TaiBenchSuite.EntryPlan.key(modelId, backend, accelerator, speculative) : key;
            this.displayName = displayName;
        }

        public boolean finished() {
            return status != EntryStatus.RUNNING && status != EntryStatus.PENDING;
        }
    }

    /** The live view: the prompt, the reply as it streams, and the running figures. */
    public static final class Live {
        @NonNull public String phase = "";
        @NonNull public String prompt = "";
        @NonNull public final StringBuilder reply = new StringBuilder();
        public int run;
        public int runs;
        public int tokens;
        public double tps;
        public long ttftMs;
        public boolean active;

        void reset(@NonNull String phase, @NonNull String prompt, int run, int runs) {
            this.phase = phase;
            this.prompt = prompt;
            this.run = run;
            this.runs = runs;
            reply.setLength(0);
            tokens = 0;
            tps = 0.0;
            ttftMs = 0L;
            active = true;
        }
    }

    /** A pause the guard asked for: the cool-down between entries, or a MODERATE-thermal wait. */
    public static final class Wait {
        @NonNull public final String reason;
        @NonNull public final String phase;
        public final long startedAtMs;
        public long lastAtMs;
        public long pauseMs;
        @Nullable public String thermalStatus;
        public double headroom = Double.NaN;

        Wait(@NonNull String reason, @NonNull String phase, long startedAtMs) {
            this.reason = reason;
            this.phase = phase;
            this.startedAtMs = startedAtMs;
            this.lastAtMs = startedAtMs;
        }

        public boolean cooldown() {
            return "cooldown".equals(reason);
        }

        /** The screen/app left the foreground (spec Safety table, Screen/app row): "Paused — you left the screen". */
        public boolean left() {
            return "left".equals(reason);
        }

        /** How long the run will wait at most before it proceeds anyway (cool-down), or stops (thermal, left). */
        public long capMs() {
            if (cooldown()) return TaiBenchGuardRules.COOLDOWN_CAP_MS;
            if (left()) return TaiBenchGuardRules.HELD_TIMEOUT_MS;
            return TaiBenchGuardRules.THERMAL_TIMEOUT_MS;
        }

        /** Time waited as of {@code nowMs}, never past the cap. */
        public long elapsedMs(long nowMs) {
            return Math.max(0L, Math.min(capMs(), nowMs - startedAtMs));
        }
    }

    /** The phone as last sampled: {@code -1}/{@code NaN}/{@code null} when unknown. */
    public static final class Conditions {
        public int batteryPercent = -1;
        public boolean charging;
        @Nullable public String thermalStatus;
        public double headroom = Double.NaN;
        public long freeRamBytes = -1L;
    }

    @NonNull public Phase phase = Phase.IDLE;
    @NonNull public String presetId = TaiBenchSuite.Preset.STANDARD.id;
    @NonNull public final List<Planned> planned = new ArrayList<>();
    @NonNull public final List<Download> downloads = new ArrayList<>();
    @NonNull public final List<Entry> entries = new ArrayList<>();
    @NonNull public final Live live = new Live();
    @Nullable public Wait wait;
    @NonNull public final Conditions conditions = new Conditions();
    /** Running decode tok/s samples of the current entry's writing and sustained phases, oldest first. */
    @NonNull public final List<Float> series = new ArrayList<>();
    // The tiles: the current entry's figures as they firm up.
    public long firstWordMs = -1L;
    public double readingTps = -1.0;
    public double writingTps = -1.0;
    public long loadMs = -1L;
    public long memBytes = -1L;
    /** The entry that finished last, for the cool-down card. */
    @Nullable public Entry lastFinished;
    /** A run that could not start, or died: the {@code error} event's code and message. */
    @Nullable public String errorCode;
    @Nullable public String errorMessage;
    @Nullable public JSONObject errorDetail;
    /** The {@code done} event's {@code stopped} reason, or the reason the session ended early. */
    @Nullable public String stopReason;
    /** The fastest writing speed on the leaderboard before the run; a faster entry is a new best. */
    public double bestTpsBeforeRun;
    /** Bumps on every {@link #apply}, so a view can tell a new state from the last it drew. */
    public long version;
    public long startedAtMs;
    public long endedAtMs;

    /** Clears everything for a new run. */
    public void begin(@NonNull String presetId, @NonNull List<Planned> models, double bestTpsBeforeRun, long nowMs) {
        phase = Phase.IDLE;
        this.presetId = presetId;
        planned.clear();
        planned.addAll(models);
        downloads.clear();
        entries.clear();
        live.reset("", "", 0, 0);
        live.active = false;
        wait = null;
        series.clear();
        resetTiles();
        lastFinished = null;
        errorCode = null;
        errorMessage = null;
        errorDetail = null;
        stopReason = null;
        this.bestTpsBeforeRun = bestTpsBeforeRun;
        startedAtMs = nowMs;
        endedAtMs = 0L;
        version++;
    }

    public boolean active() {
        return phase == Phase.DOWNLOADING || phase == Phase.RUNNING || phase == Phase.WAITING || phase == Phase.STOPPING;
    }

    public boolean finished() {
        return phase == Phase.DONE || phase == Phase.STOPPED || phase == Phase.FAILED;
    }

    /** The entry in progress: the last one, while it has not finished. */
    @Nullable
    public Entry current() {
        if (entries.isEmpty()) return null;
        Entry last = entries.get(entries.size() - 1);
        return last.finished() ? null : last;
    }

    /** The planned models no entry has arrived for yet, in plan order: the stepper's pending rows. */
    @NonNull
    public List<Planned> pending() {
        List<Planned> result = new ArrayList<>();
        for (Planned model : planned) {
            boolean seen = false;
            for (Entry entry : entries) {
                if (entry.modelId.equals(model.modelId)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                boolean failedDownload = false;
                for (Download download : downloads) {
                    if (download.modelId.equals(model.modelId) && download.status == StepStatus.FAILED) failedDownload = true;
                }
                if (!failedDownload) result.add(model);
            }
        }
        return result;
    }

    @NonNull
    public String displayName(@NonNull String modelId) {
        for (Planned model : planned) {
            if (model.modelId.equals(modelId)) return model.displayName;
        }
        return modelId;
    }

    /** Folds one event in. Unknown events are ignored; a malformed one changes nothing. */
    public void apply(@NonNull JSONObject event) {
        String name = event.optString("event", "");
        long at = event.optLong("at", 0L);
        switch (name) {
            case "download_start": onDownloadStart(event); break;
            case "download_progress": onDownloadProgress(event); break;
            case "download_done": onDownloadDone(event); break;
            case "entry_start": onEntryStart(event); break;
            case "phase_start": onPhaseStart(event); break;
            case "token": onToken(event); break;
            case "check_start": onCheckStart(event); break;
            case "phase_done": onPhaseDone(event); break;
            case "paused": onPaused(event, at); break;
            case "skipped": onSkipped(event); break;
            case "cache_rebuilt": onCacheRebuilt(); break;
            case "error": onError(event); break;
            case "entry_done": onEntryDone(event); break;
            case "done": onDone(event, at); break;
            case "stop_requested": if (!finished()) phase = Phase.STOPPING; break;
            case "conditions": onConditions(event); break;
            case "leaderboard": onLeaderboard(event); break;
            case "session_end": onSessionEnd(event, at); break;
            default: return;
        }
        version++;
    }

    // ---- downloads -------------------------------------------------------------------------

    private void onDownloadStart(@NonNull JSONObject event) {
        String modelId = event.optString("modelId", "");
        Download download = new Download(modelId, event.optString("displayName", displayName(modelId)));
        download.totalBytes = event.optLong("totalBytes", 0L);
        downloads.add(download);
        if (!finished() && phase != Phase.STOPPING) phase = Phase.DOWNLOADING;
    }

    private void onDownloadProgress(@NonNull JSONObject event) {
        Download download = download(event.optString("modelId", ""));
        if (download == null) return;
        download.bytesRead = event.optLong("bytesRead", download.bytesRead);
        long total = event.optLong("totalBytes", download.totalBytes);
        if (total > 0L) download.totalBytes = total;
    }

    private void onDownloadDone(@NonNull JSONObject event) {
        Download download = download(event.optString("modelId", ""));
        if (download == null) return;
        boolean ok = event.optBoolean("ok", false);
        download.status = ok ? StepStatus.DONE : StepStatus.FAILED;
        download.reason = ok ? null : event.optString("reason", "download failed");
        if (ok && download.totalBytes > 0L) download.bytesRead = download.totalBytes;
    }

    @Nullable
    private Download download(@NonNull String modelId) {
        for (int i = downloads.size() - 1; i >= 0; i--) {
            if (downloads.get(i).modelId.equals(modelId)) return downloads.get(i);
        }
        return null;
    }

    // ---- entries ---------------------------------------------------------------------------

    private void onEntryStart(@NonNull JSONObject event) {
        JSONObject plan = event.optJSONObject("entry");
        if (plan == null) return;
        String modelId = plan.optString("modelId", "");
        Entry entry = new Entry(event.optInt("index", entries.size()), event.optInt("total", 0), plan, displayName(modelId));
        entries.add(entry);
        wait = null;
        live.active = false;
        series.clear();
        resetTiles();
        if (phase != Phase.STOPPING) phase = Phase.RUNNING;
    }

    private void onPhaseStart(@NonNull JSONObject event) {
        Entry entry = current();
        String phaseName = event.optString("phase", "");
        wait = null;
        if (phase == Phase.WAITING) phase = Phase.RUNNING;
        if (entry == null || phaseName.isEmpty()) return;
        Step step = new Step(phaseName, event.optInt("runs", 1));
        step.run = 1;
        entry.steps.put(phaseName, step);
        entry.currentPhase = phaseName;
        String prompt = event.optString("prompt", "");
        if (TaiBenchSuite.PHASE_LOAD.equals(phaseName)) {
            live.active = false;
        } else {
            live.reset(phaseName, prompt, 1, step.runs);
        }
    }

    private void onToken(@NonNull JSONObject event) {
        String phaseName = event.optString("phase", live.phase);
        int run = event.optInt("run", live.run);
        if (!live.active || !phaseName.equals(live.phase) || run != live.run) {
            // A new run of the same phase: the reply starts over, the prompt stays.
            String prompt = live.prompt;
            live.reset(phaseName, prompt, run, event.optInt("runs", live.runs));
        }
        live.reply.append(event.optString("text", ""));
        live.tokens = event.optInt("tokens", live.tokens);
        live.tps = event.optDouble("tps", live.tps);
        live.ttftMs = event.optLong("ttftMs", live.ttftMs);
        Entry entry = current();
        if (entry != null) {
            Step step = entry.steps.get(phaseName);
            if (step != null) step.run = run;
        }
        if (TaiBenchSuite.PHASE_WRITING.equals(phaseName) || TaiBenchSuite.PHASE_SUSTAINED.equals(phaseName)) {
            if (live.tps > 0.0) {
                series.add((float) live.tps);
                if (series.size() > SERIES_CAPACITY) series.remove(0);
            }
            if (writingTps < 0.0 && live.tps > 0.0) writingTps = live.tps;
        }
        if (TaiBenchSuite.PHASE_FIRST_WORD.equals(phaseName) && live.ttftMs > 0L && firstWordMs < 0L) {
            firstWordMs = live.ttftMs;
        }
    }

    private void onCheckStart(@NonNull JSONObject event) {
        int run = event.optInt("run", 1);
        live.reset(TaiBenchSuite.PHASE_CHECK, event.optString("prompt", ""), run, event.optInt("runs", live.runs));
        Entry entry = current();
        if (entry != null) {
            Step step = entry.steps.get(TaiBenchSuite.PHASE_CHECK);
            if (step != null) step.run = run;
        }
    }

    private void onPhaseDone(@NonNull JSONObject event) {
        Entry entry = current();
        String phaseName = event.optString("phase", "");
        JSONObject metrics = event.optJSONObject("metrics");
        String status = event.optString("status", "ok");
        if (entry != null) {
            Step step = entry.steps.get(phaseName);
            if (step == null) {
                step = new Step(phaseName, event.optInt("runs", 1));
                entry.steps.put(phaseName, step);
            }
            step.status = "ok".equals(status) ? StepStatus.DONE : StepStatus.FAILED;
            step.run = step.runs;
            step.metrics = metrics;
        }
        live.active = false;
        if (metrics == null) return;
        switch (phaseName) {
            case TaiBenchSuite.PHASE_LOAD:
                loadMs = metrics.optLong("ms", loadMs);
                memBytes = metrics.optLong("memBytes", memBytes);
                break;
            case TaiBenchSuite.PHASE_WARMUP:
                long pss = metrics.optLong("pssBytes", -1L);
                if (pss > 0L) memBytes = pss;
                break;
            case TaiBenchSuite.PHASE_READING:
                if (metrics.has("med")) readingTps = metrics.optDouble("med", readingTps);
                break;
            case TaiBenchSuite.PHASE_FIRST_WORD:
                if (metrics.has("med")) firstWordMs = Math.round(metrics.optDouble("med", firstWordMs));
                break;
            case TaiBenchSuite.PHASE_WRITING:
                if (metrics.has("med")) writingTps = metrics.optDouble("med", writingTps);
                break;
            default:
                break;
        }
    }

    private void onPaused(@NonNull JSONObject event, long at) {
        String reason = event.optString("reason", "cooldown");
        String phaseName = event.optString("phase", "");
        Wait current = wait;
        if (current == null || !current.reason.equals(reason)) {
            current = new Wait(reason, phaseName, at);
            wait = current;
        }
        current.lastAtMs = at;
        current.pauseMs = event.optLong("ms", current.pauseMs);
        if (event.has("thermalStatus")) current.thermalStatus = event.optString("thermalStatus", null);
        if (event.has("headroom")) current.headroom = event.optDouble("headroom", Double.NaN);
        live.active = false;
        if (phase != Phase.STOPPING) phase = Phase.WAITING;
    }

    private void onSkipped(@NonNull JSONObject event) {
        Entry entry = current();
        if (entry == null) return;
        entry.status = EntryStatus.SKIPPED;
        entry.reason = event.optString("reason", event.optString("code", "skipped"));
        Step load = entry.steps.get(TaiBenchSuite.PHASE_LOAD);
        if (load != null && load.status == StepStatus.RUNNING) load.status = StepStatus.FAILED;
        live.active = false;
    }

    private void onCacheRebuilt() {
        Entry entry = current();
        if (entry == null) return;
        entry.cacheRebuilt = true;
        entry.steps.clear();
        entry.currentPhase = null;
        series.clear();
        resetTiles();
    }

    private void onError(@NonNull JSONObject event) {
        String code = event.optString("code", "error");
        String message = event.optString("message", code);
        if (event.has("entry")) {
            Entry entry = current();
            if (entry == null) return;
            entry.reason = message;
            String phaseName = event.optString("phase", "");
            Step step = entry.steps.get(phaseName);
            if (step != null && !"timeout".equals(code)) step.status = StepStatus.FAILED;
            live.active = false;
            return;
        }
        // Outside any entry: the run could not start, or died.
        errorCode = code;
        errorMessage = message;
        errorDetail = event;
        if (!finished()) {
            phase = Phase.FAILED;
            stopReason = code;
        }
        live.active = false;
    }

    private void onEntryDone(@NonNull JSONObject event) {
        JSONObject record = event.optJSONObject("record");
        Entry entry = current();
        if (entry == null && !entries.isEmpty()) entry = entries.get(entries.size() - 1);
        if (entry == null) return;
        entry.record = record;
        String status = record == null ? "" : record.optString("status", "");
        if (TaiBenchStore.STATUS_COMPLETE.equals(status) || "timeout".equals(status)) {
            entry.status = EntryStatus.DONE;
        } else if (status.startsWith("skipped:")) {
            entry.status = EntryStatus.SKIPPED;
        } else {
            entry.status = EntryStatus.STOPPED;
        }
        if (record != null) {
            String verdict = record.optString("verdict", "");
            entry.verdict = verdict.isEmpty() || record.isNull("verdict") ? null : verdict;
            JSONObject phases = record.optJSONObject("phases");
            JSONObject writing = phases == null ? null : phases.optJSONObject("writing");
            entry.writingTps = writing == null ? 0.0 : writing.optDouble("med", 0.0);
            if (entry.reason == null && record.has("skipReason")) entry.reason = record.optString("skipReason", null);
            if (record.optBoolean("cacheRebuilt", false)) entry.cacheRebuilt = true;
        }
        entry.currentPhase = null;
        lastFinished = entry;
        live.active = false;
    }

    private void onDone(@NonNull JSONObject event, long at) {
        String stopped = event.isNull("stopped") ? null : event.optString("stopped", null);
        if (stopped != null && stopped.isEmpty()) stopped = null;
        stopReason = stopped;
        phase = stopped == null ? Phase.DONE : Phase.STOPPED;
        wait = null;
        live.active = false;
        endedAtMs = at;
    }

    private void onSessionEnd(@NonNull JSONObject event, long at) {
        if (finished()) return;
        stopReason = event.optString("reason", "ended");
        phase = errorCode != null ? Phase.FAILED : Phase.STOPPED;
        wait = null;
        live.active = false;
        endedAtMs = at;
    }

    private void onConditions(@NonNull JSONObject event) {
        conditions.batteryPercent = event.optInt("batteryPercent", -1);
        conditions.charging = event.optBoolean("charging", false);
        conditions.thermalStatus = event.isNull("thermalStatus") ? null : event.optString("thermalStatus", null);
        conditions.headroom = event.optDouble("headroom", Double.NaN);
        conditions.freeRamBytes = event.optLong("freeRamBytes", -1L);
    }

    /** {@code {ranked:[{key, rank, writingTps}…]}}: places every finished entry, and flags a new best. */
    private void onLeaderboard(@NonNull JSONObject event) {
        JSONArray ranked = event.optJSONArray("ranked");
        if (ranked == null) return;
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < ranked.length(); i++) {
            JSONObject row = ranked.optJSONObject(i);
            if (row == null) continue;
            ranks.put(row.optString("key", ""), row.optInt("rank", i + 1));
        }
        for (Entry entry : entries) {
            Integer rank = ranks.get(entry.key);
            entry.rank = rank == null ? 0 : rank;
            entry.newBest = entry.status == EntryStatus.DONE && entry.rank == 1 && entry.writingTps > bestTpsBeforeRun;
        }
    }

    private void resetTiles() {
        firstWordMs = -1L;
        readingTps = -1.0;
        writingTps = -1.0;
        loadMs = -1L;
        memBytes = -1L;
    }

    /** The phases a preset runs, in order, for the stepper's sub-line. */
    @NonNull
    public static List<String> phasesFor(@Nullable TaiBenchSuite.Preset preset) {
        List<String> phases = new ArrayList<>();
        Collections.addAll(phases, TaiBenchSuite.PHASE_LOAD, TaiBenchSuite.PHASE_WARMUP, TaiBenchSuite.PHASE_READING,
            TaiBenchSuite.PHASE_FIRST_WORD, TaiBenchSuite.PHASE_WRITING);
        if (preset != null && preset.sustained) phases.add(TaiBenchSuite.PHASE_SUSTAINED);
        phases.add(TaiBenchSuite.PHASE_CHECK);
        return phases;
    }
}
