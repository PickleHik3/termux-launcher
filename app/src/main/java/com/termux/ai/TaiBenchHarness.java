package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs bench v1 ({@link TaiBenchSuite}) over a list of entries in the {@code :tai_runtime}
 * process and streams what happens as JSON events. LiteRT-LM and MNN are measured by the same
 * code: every generation goes through {@link TaiGenerationCallback}, the first and last token are
 * stamped as they arrive, and the prompt and generated token counts come from the runtime's own
 * usage figures. Nothing here calls a backend's own benchmark routine.
 *
 * <p>Per entry: unload whatever is resident, load cold (timed, memory through
 * {@link TaiLoadMeter}), one warm-up reply that is discarded, the preset's reading / first word /
 * writing runs (and the sustained window on Thorough), the three check questions, unload. The
 * {@link Host} does the loading and generating so this class knows nothing about the router; the
 * {@link TaiBenchGuard} is asked before every phase; {@link #requestStop} ends the run from
 * outside (the runtime's cancel and unload operations), keeping the phases that finished.
 *
 * <p>Timing: TTFT is the first token minus the submit; prefill tok/s is prompt tokens over that;
 * decode tok/s is (generated − 1) over the first-to-last-token interval
 * ({@link TaiBenchStats}). Each run has a time limit of three times its expected length
 * ({@link TaiBenchSuite#timeLimitMs}): a watchdog cancels the runtime and the phase is marked
 * {@code timeout}.
 *
 * <p>Events ({@code event} field): {@code entry_start}, {@code phase_start} (with the prompt),
 * {@code token} (at most one per {@link #TOKEN_EVENT_INTERVAL_MS}, with the text since the last
 * one and the running tok/s), {@code phase_done} (the metrics and the whole reply),
 * {@code entry_done} (the record the store keeps), {@code skipped}, {@code error}, {@code done};
 * also {@code check_start} (one per check question) and {@code paused} (the guard asked for a
 * wait). Every event carries {@code at}, the wall-clock millisecond it was made.
 */
final class TaiBenchHarness {
    /** Token events are coalesced to about 20 a second; a live view needs no more. */
    static final long TOKEN_EVENT_INTERVAL_MS = 50L;
    /** The sustained phase compares the tokens of its first and last window of this length. */
    static final long SUSTAINED_WINDOW_MS = 10_000L;

    /** What the harness needs from the runtime process; {@link TaiManager} implements it. */
    interface Host {
        /** Loads the entry's model on its processor through the normal preflight and budget; {@code ok:false} is a refusal. */
        @NonNull JSONObject load(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException;
        @NonNull JSONObject unload() throws JSONException;
        /** One greedy reply to {@code userPrompt}, capped at {@code maxTokens}, with token callbacks. */
        @NonNull JSONObject chat(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String userPrompt, int maxTokens,
                                 @NonNull TaiGenerationCallback callback) throws JSONException;
        /** Cancels the load or generation in progress; the watchdog's lever. */
        void cancel();
        @NonNull TaiLoadMeter startMeter();
        @NonNull String readingPassage() throws IOException;
        /** The model, runtime and device stamps for an entry's record: displayName, sizeBytes, sha256, runtimeVersion. */
        @NonNull JSONObject describe(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException;
        /**
         * MNN only: throws away the entry's model's mmap weight cache, so the next {@link #load}
         * rebuilds it from scratch. A no-op for other backends; the harness never needs to check
         * which one it is calling.
         */
        void clearMmapCache(@NonNull TaiBenchSuite.EntryPlan entry);
        /**
         * The runtime process's own proportional set size right now, in bytes, or {@code -1}
         * when it cannot be read. Taken after the load and the warm-up, it is what the model
         * really costs the phone: {@link TaiLoadMeter}'s MemAvailable difference is meaningless
         * for an mmap'd MNN package, whose pages are counted only as they are touched.
         */
        long processPssBytes();
    }

    interface Sink {
        void onEvent(@NonNull JSONObject event) throws IOException;
    }

    @NonNull private final TaiBenchSuite.Preset preset;
    @NonNull private final List<TaiBenchSuite.EntryPlan> entries;
    @NonNull private final Host host;
    @NonNull private final TaiBenchGuard guard;
    @NonNull private final Sink sink;
    @NonNull private final String appVersion;
    @NonNull private final JSONObject device;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-bench-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    @Nullable private volatile String stopReason;

    /**
     * @param device {@code {soc, ramClassGb}}
     */
    TaiBenchHarness(@NonNull TaiBenchSuite.Preset preset, @NonNull List<TaiBenchSuite.EntryPlan> entries,
                    @NonNull Host host, @NonNull TaiBenchGuard guard, @NonNull Sink sink,
                    @NonNull String appVersion, @NonNull JSONObject device) {
        this.preset = preset;
        this.entries = entries;
        this.host = host;
        this.guard = guard;
        this.sink = sink;
        this.appVersion = appVersion;
        this.device = device;
    }

    /** Ends the run after the current generation; the entry in progress is recorded as stopped. */
    void requestStop(@NonNull String reason) {
        if (stopReason == null) stopReason = reason;
        host.cancel();
    }

    /** Ends the current or next cool-down wait at once; that entry's record is marked {@code warmStart}. */
    void skipCooldown() {
        guard.skipCooldown();
    }

    /** Forwards to the guard: whether the in-app screen that owns this run has left the foreground. */
    void setHeld(boolean held) {
        guard.setHeld(held);
    }

    boolean stopRequested() {
        return stopReason != null;
    }

    /**
     * Runs every entry and returns the summary the {@code done} event carries:
     * {@code {ok, benchVersion, preset, entries, records, skipped, stopped}}.
     */
    @NonNull
    JSONObject run() throws IOException, JSONException {
        JSONArray records = new JSONArray();
        JSONArray skipped = new JSONArray();
        try {
            for (int i = 0; i < entries.size(); i++) {
                if (stopRequested()) break;
                TaiBenchSuite.EntryPlan entry = entries.get(i);
                emit(event("entry_start").put("index", i).put("total", entries.size()).put("entry", entry.toJson()));
                JSONObject record = runEntry(entry);
                if (record.optString("status", "").startsWith("skipped:")) {
                    skipped.put(new JSONObject().put("entry", entry.toJson()).put("reason", record.optString("skipReason", "")));
                }
                records.put(record);
                emit(event("entry_done").put("index", i).put("total", entries.size()).put("record", record));
            }
        } finally {
            watchdog.shutdownNow();
        }
        JSONObject done = event("done")
            .put("ok", true)
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
            .put("preset", preset.id)
            .put("entries", entries.size())
            .put("records", records)
            .put("skipped", skipped)
            .put("stopped", stopReason == null ? JSONObject.NULL : stopReason);
        emit(done);
        return done;
    }

    // ---- one entry ---------------------------------------------------------------------------

    /**
     * Runs one entry, and once, self-heals an MNN entry whose check phase came back broken in the
     * shape a stale or corrupt mmap weight cache produces (see {@link MnnTaiRuntime}): every check
     * reply degenerate — one repeated character, whitespace or empty — rather than a real, if
     * wrong, answer. That shape rules out a merely weak model or a hard question; a model that is
     * actually trying produces varied text even when it gets the answer wrong. On that shape the
     * cache is cleared and the whole entry (load included) is run again from scratch; if it comes
     * back broken the same way, it stays recorded as broken rather than retried forever.
     */
    @NonNull
    private JSONObject runEntry(@NonNull TaiBenchSuite.EntryPlan entry) throws IOException, JSONException {
        JSONObject record = runEntryOnce(entry);
        if (!stopRequested() && looksLikeStaleMmapCache(entry, record)) {
            emit(event("cache_rebuilt").put("entry", entry.toJson())
                .put("reason", "mnn_check_degenerate"));
            try {
                host.clearMmapCache(entry);
            } catch (RuntimeException ignored) {
            }
            JSONObject rebuilt = runEntryOnce(entry);
            rebuilt.put("cacheRebuilt", true);
            return rebuilt;
        }
        return record;
    }

    /**
     * True when {@code record} is an MNN entry whose check phase ran (every question got a reply)
     * but every reply was degenerate. Pure and static over the record's own JSON so it can be
     * exercised without running a bench.
     */
    static boolean looksLikeStaleMmapCache(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull JSONObject record) {
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return false;
        JSONObject check = record.optJSONObject("check");
        if (check == null) return false;
        int total = check.optInt("total", 0);
        int passed = check.optInt("passed", 0);
        if (total <= 0 || passed >= total) return false;
        JSONArray details = check.optJSONArray("details");
        if (details == null || details.length() == 0) return false;
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail == null || !isDegenerateReply(detail.optString("reply", null))) return false;
        }
        return true;
    }

    /** All one repeated character, whitespace only, or empty; {@code null} counts as empty. */
    static boolean isDegenerateReply(@Nullable String reply) {
        if (reply == null) return true;
        String trimmed = reply.trim();
        if (trimmed.isEmpty()) return true;
        char first = trimmed.charAt(0);
        for (int i = 1; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != first) return false;
        }
        return true;
    }

    @NonNull
    private JSONObject runEntryOnce(@NonNull TaiBenchSuite.EntryPlan entry) throws IOException, JSONException {
        Record record = new Record(entry);
        try {
            // Whatever is resident goes first: a cold load is the number, and the budget credits
            // the closed chat model's bytes to this one.
            host.unload();

            if (!consultGuard(TaiBenchSuite.PHASE_LOAD, entry, record)) return record.finish();
            guard.entryStarted(entry);
            if (!runLoad(entry, record)) return record.finish();

            if (!consultGuard(TaiBenchSuite.PHASE_WARMUP, entry, record)) return record.finish();
            Generation warmup = generate(entry, TaiBenchSuite.PHASE_WARMUP, 1, 1,
                TaiBenchSuite.WARMUP_PROMPT, TaiBenchSuite.WARMUP_MAX_TOKENS, 0L);
            if (!warmup.ok && !record.noteFailure(TaiBenchSuite.PHASE_WARMUP, warmup)) return record.finish();
            // The weights are all touched once the warm-up has run: the process's PSS now is the
            // model's real footprint, for both backends alike.
            try {
                record.pssBytes = host.processPssBytes();
            } catch (RuntimeException ignored) {
                record.pssBytes = -1L;
            }
            PhaseEnd warmupEnd = new PhaseEnd();
            warmupEnd.add(warmup);
            emit(warmupEnd.into(event("phase_done").put("phase", TaiBenchSuite.PHASE_WARMUP).put("status", warmup.status())
                .put("reply", warmup.reply()).put("metrics", new JSONObject().put("pssBytes", record.pssBytes))));

            if (!runReading(entry, record)) return record.finish();
            if (!runFirstWord(entry, record)) return record.finish();
            if (!runWriting(entry, record)) return record.finish();
            if (preset.sustained && !runSustained(entry, record)) return record.finish();
            if (!runCheck(entry, record)) return record.finish();
            record.status = TaiBenchStore.STATUS_COMPLETE;
            return record.finish();
        } catch (IOException e) {
            // The client went away: stop the runtime and let the caller see the failure.
            host.cancel();
            throw e;
        } catch (RuntimeException | JSONException e) {
            record.status = "stopped:harness_error";
            record.skipReason = message(e);
            emit(event("error").put("entry", entry.toJson()).put("code", "harness_error").put("message", message(e)));
            return record.finish();
        } finally {
            try {
                guard.entryFinished(entry);
            } catch (RuntimeException ignored) {
            }
            try {
                host.unload();
            } catch (JSONException | RuntimeException ignored) {
            }
        }
    }

    /** {@code false} when the guard or an outside stop ended the run; the record says why. */
    private boolean consultGuard(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record)
        throws IOException, JSONException {
        while (true) {
            String outside = stopReason;
            if (outside != null) {
                record.status = "stopped:" + outside;
                return false;
            }
            TaiBenchGuard.Decision decision = guard.beforePhase(phase, entry);
            if (TaiBenchGuard.CONTINUE.equals(decision.action)) return true;
            if (TaiBenchGuard.STOP.equals(decision.action)) {
                record.status = "stopped:" + (decision.reason == null ? "guard" : decision.reason);
                return false;
            }
            JSONObject paused = event("paused").put("phase", phase).put("ms", decision.pauseMs)
                .put("reason", decision.reason == null ? JSONObject.NULL : decision.reason);
            if (decision.detail != null) {
                Iterator<String> keys = decision.detail.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    paused.put(key, decision.detail.opt(key));
                }
            }
            emit(paused);
            try {
                Thread.sleep(decision.pauseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                record.status = "stopped:interrupted";
                return false;
            }
        }
    }

    private boolean runLoad(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_LOAD).put("runs", 1));
        TaiLoadMeter meter = host.startMeter();
        ScheduledFuture<?> limit = armWatchdog(TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_LOAD));
        long started = System.nanoTime();
        JSONObject load;
        try {
            load = host.load(entry);
        } catch (RuntimeException e) {
            meter.stop();
            limit.cancel(false);
            record.status = "skipped:load_failed";
            record.skipReason = message(e);
            emit(event("skipped").put("entry", entry.toJson()).put("code", "load_failed").put("reason", message(e)));
            return false;
        }
        long loadMs = (System.nanoTime() - started) / 1_000_000L;
        long memBytes = meter.stop();
        boolean timedOut = limit.isDone() && !limit.isCancelled();
        limit.cancel(false);
        if (!load.optBoolean("ok", false)) {
            String code = timedOut ? "timeout" : load.optString("error", "load_refused");
            String reason = load.optString("message", code);
            record.status = timedOut ? "timeout" : "skipped:" + code;
            record.skipReason = reason;
            emit(event("skipped").put("entry", entry.toJson()).put("code", code).put("reason", reason)
                .put("refusal", load));
            return false;
        }
        record.loadMs = loadMs;
        record.memBytes = memBytes;
        record.loadResult = load;
        emit(event("phase_done").put("phase", TaiBenchSuite.PHASE_LOAD).put("status", "ok")
            .put("metrics", new JSONObject().put("ms", loadMs).put("memBytes", memBytes)));
        return true;
    }

    private boolean runReading(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        if (!consultGuard(TaiBenchSuite.PHASE_READING, entry, record)) return false;
        String prompt = TaiBenchSuite.readingPrompt(host.readingPassage());
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_READING).put("runs", preset.readingRuns).put("prompt", prompt));
        for (int run = 1; run <= preset.readingRuns; run++) {
            Generation generation = generate(entry, TaiBenchSuite.PHASE_READING, run, preset.readingRuns,
                prompt, TaiBenchSuite.READING_MAX_TOKENS, 0L);
            if (!generation.ok && !record.noteFailure(TaiBenchSuite.PHASE_READING, generation)) return false;
            record.lastReply = generation.reply();
            record.readingEnd.add(generation);
            if (generation.promptTokens > 0 && generation.ttftMs() > 0L) {
                record.reading.add(TaiBenchStats.prefillTps(generation.promptTokens, generation.ttftMs()));
            }
            record.readingTokens = Math.max(record.readingTokens, generation.promptTokens);
            if (generation.timedOut) break;
        }
        emit(record.readingEnd.into(event("phase_done").put("phase", TaiBenchSuite.PHASE_READING).put("status", record.phaseStatus)
            .put("metrics", metrics(record.reading).put("promptTokens", record.readingTokens))
            .put("reply", record.lastReply)));
        return true;
    }

    private boolean runFirstWord(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        if (!consultGuard(TaiBenchSuite.PHASE_FIRST_WORD, entry, record)) return false;
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_FIRST_WORD).put("runs", preset.firstWordRuns)
            .put("prompt", TaiBenchSuite.FIRST_WORD_PROMPT));
        for (int run = 1; run <= preset.firstWordRuns; run++) {
            Generation generation = generate(entry, TaiBenchSuite.PHASE_FIRST_WORD, run, preset.firstWordRuns,
                TaiBenchSuite.FIRST_WORD_PROMPT, TaiBenchSuite.FIRST_WORD_MAX_TOKENS, 0L);
            if (!generation.ok && !record.noteFailure(TaiBenchSuite.PHASE_FIRST_WORD, generation)) return false;
            record.lastReply = generation.reply();
            record.firstWordEnd.add(generation);
            if (generation.ttftMs() > 0L) record.firstWord.add(generation.ttftMs());
            if (generation.timedOut) break;
        }
        emit(record.firstWordEnd.into(event("phase_done").put("phase", TaiBenchSuite.PHASE_FIRST_WORD).put("status", record.phaseStatus)
            .put("metrics", metrics(record.firstWord)).put("reply", record.lastReply)));
        return true;
    }

    private boolean runWriting(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        if (!consultGuard(TaiBenchSuite.PHASE_WRITING, entry, record)) return false;
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_WRITING).put("runs", preset.writingRuns)
            .put("prompt", TaiBenchSuite.WRITING_PROMPT));
        TaiBenchStats.Series tokens = new TaiBenchStats.Series();
        for (int run = 1; run <= preset.writingRuns; run++) {
            Generation generation = generate(entry, TaiBenchSuite.PHASE_WRITING, run, preset.writingRuns,
                TaiBenchSuite.WRITING_PROMPT, TaiBenchSuite.WRITING_MAX_TOKENS, 0L);
            if (!generation.ok && !record.noteFailure(TaiBenchSuite.PHASE_WRITING, generation)) return false;
            record.lastReply = generation.reply();
            record.writingEnd.add(generation);
            double tps = generation.decodeTps();
            if (tps > 0.0) record.writing.add(tps);
            if (generation.generatedTokens() > 0) tokens.add(generation.generatedTokens());
            if (generation.timedOut) break;
        }
        record.writingTokens = (int) Math.round(tokens.median());
        emit(record.writingEnd.into(event("phase_done").put("phase", TaiBenchSuite.PHASE_WRITING).put("status", record.phaseStatus)
            .put("metrics", metrics(record.writing).put("tokens", record.writingTokens)).put("reply", record.lastReply)));
        return true;
    }

    /**
     * Thorough only: one reply held for {@link TaiBenchSuite#SUSTAINED_SECONDS}, then stopped
     * through the callback. The token rate of the first {@link #SUSTAINED_WINDOW_MS} against the
     * last shows how far the phone throttled while it wrote.
     */
    private boolean runSustained(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        if (!consultGuard(TaiBenchSuite.PHASE_SUSTAINED, entry, record)) return false;
        String prompt = TaiBenchSuite.WRITING_PROMPT + " Then keep going: describe every kind of cloud you know, one paragraph each.";
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_SUSTAINED).put("runs", 1).put("prompt", prompt)
            .put("seconds", TaiBenchSuite.SUSTAINED_SECONDS));
        Generation generation = generate(entry, TaiBenchSuite.PHASE_SUSTAINED, 1, 1, prompt,
            TaiBenchSuite.SUSTAINED_MAX_TOKENS, TaiBenchSuite.SUSTAINED_SECONDS * 1000L);
        // A cancelled reply is the expected end of this phase, not a failure, as long as it wrote.
        if (!generation.ok && generation.stamps.size() < 2 && !record.noteFailure(TaiBenchSuite.PHASE_SUSTAINED, generation)) return false;
        JSONObject sustained = sustainedMetrics(generation.stamps);
        record.sustained = sustained;
        emit(event("phase_done").put("phase", TaiBenchSuite.PHASE_SUSTAINED).put("status", generation.timedOut ? "timeout" : "ok")
            .put("metrics", sustained).put("reply", generation.reply()));
        return true;
    }

    /** {@code {startTps, endTps, dropPct, tokens, seconds}} from the token stamps of one reply. */
    @NonNull
    static JSONObject sustainedMetrics(@NonNull List<Long> stamps) throws JSONException {
        JSONObject metrics = new JSONObject();
        if (stamps.size() < 2) {
            return metrics.put("startTps", 0.0).put("endTps", 0.0).put("dropPct", 0.0).put("tokens", stamps.size()).put("seconds", 0.0);
        }
        long first = stamps.get(0);
        long last = stamps.get(stamps.size() - 1);
        long window = Math.min(SUSTAINED_WINDOW_MS, Math.max(1L, last - first));
        int startCount = 0;
        int endCount = 0;
        for (long stamp : stamps) {
            if (stamp - first < window) startCount++;
            if (last - stamp < window) endCount++;
        }
        double startTps = startCount * 1000.0 / window;
        double endTps = endCount * 1000.0 / window;
        double dropPct = startTps <= 0.0 ? 0.0 : Math.max(0.0, (startTps - endTps) / startTps * 100.0);
        return metrics.put("startTps", startTps).put("endTps", endTps).put("dropPct", dropPct)
            .put("tokens", stamps.size()).put("seconds", (last - first) / 1000.0);
    }

    private boolean runCheck(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull Record record) throws IOException, JSONException {
        if (!consultGuard(TaiBenchSuite.PHASE_CHECK, entry, record)) return false;
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_CHECK).put("runs", TaiBenchSuite.CHECKS.size()));
        JSONArray details = new JSONArray();
        PhaseEnd checkEnd = new PhaseEnd();
        int passed = 0;
        for (int i = 0; i < TaiBenchSuite.CHECKS.size(); i++) {
            TaiBenchSuite.Check check = TaiBenchSuite.CHECKS.get(i);
            emit(event("check_start").put("phase", TaiBenchSuite.PHASE_CHECK).put("run", i + 1).put("name", check.name).put("prompt", check.prompt));
            Generation generation = generate(entry, TaiBenchSuite.PHASE_CHECK, i + 1, TaiBenchSuite.CHECKS.size(),
                check.prompt, TaiBenchSuite.CHECK_MAX_TOKENS, 0L);
            if (!generation.ok && !record.noteFailure(TaiBenchSuite.PHASE_CHECK, generation)) return false;
            boolean ok = generation.ok && check.grade(generation.reply());
            if (ok) passed++;
            // The reply is kept whole, with how it ended, so a wrong answer can be told from a cut-off one.
            PhaseEnd replyEnd = new PhaseEnd();
            replyEnd.add(generation);
            checkEnd.add(generation);
            details.put(replyEnd.into(new JSONObject().put("name", check.name).put("passed", ok).put("reply", generation.reply())));
            if (generation.timedOut) break;
        }
        record.checkPassed = passed;
        record.checkDetails = details;
        emit(checkEnd.into(event("phase_done").put("phase", TaiBenchSuite.PHASE_CHECK).put("status", record.phaseStatus)
            .put("metrics", new JSONObject().put("passed", passed).put("total", TaiBenchSuite.CHECKS.size()).put("details", details))));
        return true;
    }

    // ---- one generation ----------------------------------------------------------------------

    /**
     * How the replies of one phase ended, for its {@code phase_done} event and its part of the
     * record: {@code finishReason} ({@code "length"} if any run hit the cap, else the last run's),
     * {@code reasoningTokens} (the most any run spent thinking) and {@code tokenLimit} (the cap).
     * The caps are fixed by the bench version, so a reply that ends at one is intended, and the UI
     * says so.
     */
    static final class PhaseEnd {
        @NonNull String finishReason = "";
        int reasoningTokens;
        int tokenLimit;

        void add(@NonNull Generation generation) {
            if (!"length".equals(finishReason)) finishReason = generation.effectiveFinishReason();
            reasoningTokens = Math.max(reasoningTokens, generation.reasoningTokens);
            tokenLimit = generation.tokenLimit;
        }

        /** Adds the three fields to {@code json} and returns it. */
        @NonNull
        JSONObject into(@NonNull JSONObject json) throws JSONException {
            return json.put("finishReason", finishReason).put("reasoningTokens", reasoningTokens).put("tokenLimit", tokenLimit);
        }
    }

    /** The stamps and counts of one reply, and what the runtime said about it. */
    static final class Generation {
        final List<Long> stamps = new ArrayList<>();
        long submitMs;
        long firstTokenMs;
        long lastTokenMs;
        int callbackTokens;
        int promptTokens;
        int usageCompletionTokens = -1;
        boolean usageEstimated;
        /** Thinking callbacks seen (a floor of the thinking tokens; LiteRT may batch several per callback). */
        int reasoningTokens;
        /** The runtime's {@code finishReason} for the reply; empty when it gave none. */
        @NonNull String finishReason = "";
        /** The {@code max_tokens} this reply was held to. */
        int tokenLimit;
        boolean ok;
        boolean timedOut;
        @NonNull String errorCode = "";
        @NonNull String errorMessage = "";
        private final StringBuilder text = new StringBuilder();

        long ttftMs() {
            return firstTokenMs > 0L ? firstTokenMs - submitMs : 0L;
        }

        /**
         * The runtime's own count when it is a measurement, the callback count when the runtime
         * only estimated. LiteRT's callbacks can carry more than one token, so its callback count
         * is a floor; its BenchmarkInfo count is exact and preferred.
         */
        int generatedTokens() {
            if (usageCompletionTokens > 0 && !usageEstimated) return usageCompletionTokens;
            return callbackTokens;
        }

        double decodeTps() {
            return TaiBenchStats.decodeTps(generatedTokens(), firstTokenMs, lastTokenMs);
        }

        /**
         * How the reply ended: {@code "length"} when the runtime said so, or when a reply that
         * finished normally used its whole token cap (MNN always reports {@code stop}); else the
         * runtime's reason, {@code "stop"} when it gave none.
         */
        @NonNull
        String effectiveFinishReason() {
            if ("length".equals(finishReason)) return "length";
            if (ok && tokenLimit > 0 && generatedTokens() >= tokenLimit) return "length";
            return finishReason.isEmpty() ? "stop" : finishReason;
        }

        @NonNull
        String reply() {
            synchronized (text) {
                return text.toString();
            }
        }

        @NonNull
        String status() {
            if (timedOut) return "timeout";
            return ok ? "ok" : "error";
        }

        void append(@NonNull String delta) {
            synchronized (text) {
                text.append(delta);
            }
        }
    }

    /**
     * One reply with every token stamped. {@code holdMs > 0} is the sustained window: the callback
     * stops the reply after that long. The watchdog cancels the runtime at the phase's limit.
     */
    @NonNull
    private Generation generate(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String phase, int run, int runs,
                                @NonNull String prompt, int maxTokens, long holdMs) throws IOException, JSONException {
        Generation generation = new Generation();
        long limitMs = TaiBenchSuite.timeLimitMs(phase);
        AtomicBoolean pastLimit = new AtomicBoolean();
        StringBuilder pending = new StringBuilder();
        AtomicLong lastEventMs = new AtomicLong();
        AtomicInteger eventTokens = new AtomicInteger();
        ScheduledFuture<?> limit = armWatchdog(limitMs, () -> pastLimit.set(true));
        generation.submitMs = System.currentTimeMillis();
        long submitNanos = System.nanoTime();
        List<IOException> sinkFailure = new ArrayList<>(1);
        TaiGenerationCallback callback = new TaiGenerationCallback() {
            @Override
            public void onToken(@NonNull String text) {
                if (text.isEmpty()) return;
                stamp();
                generation.append(text);
                synchronized (pending) {
                    pending.append(text);
                }
                maybeEmitToken(false);
            }

            @Override
            public void onThinkingToken(@NonNull String text) {
                // Thinking is switched off for the bench; a model that thinks anyway still has
                // its first output stamped, so TTFT is the wait the user sees. Thinking tokens
                // count toward the live decode rate and the token counter, and emit throttled
                // token events like reply text, so a thinking model's dials and counter move.
                if (text.isEmpty()) return;
                stamp(true);
                maybeEmitToken(false);
            }

            private void stamp() {
                stamp(false);
            }

            private void stamp(boolean thinking) {
                long now = System.currentTimeMillis();
                synchronized (generation.stamps) {
                    if (thinking) generation.reasoningTokens++;
                    if (generation.firstTokenMs == 0L) generation.firstTokenMs = now;
                    generation.lastTokenMs = now;
                    generation.callbackTokens++;
                    generation.stamps.add(now);
                }
            }

            @Override
            public boolean shouldCancelGeneration() {
                if (pastLimit.get() || stopRequested()) return true;
                return holdMs > 0L && (System.nanoTime() - submitNanos) / 1_000_000L >= holdMs;
            }

            private void maybeEmitToken(boolean flush) {
                long now = System.currentTimeMillis();
                if (!flush && now - lastEventMs.get() < TOKEN_EVENT_INTERVAL_MS) return;
                String delta;
                synchronized (pending) {
                    delta = pending.toString();
                    pending.setLength(0);
                }
                int count;
                double tps;
                synchronized (generation.stamps) {
                    count = generation.callbackTokens;
                    tps = TaiBenchStats.decodeTps(count, generation.firstTokenMs, generation.lastTokenMs);
                }
                // A stretch of thinking leaves the reply text empty but still advances the count.
                if (delta.isEmpty() && count == eventTokens.get()) return;
                lastEventMs.set(now);
                eventTokens.set(count);
                try {
                    emit(event("token").put("phase", phase).put("run", run).put("runs", runs)
                        .put("text", delta).put("tokens", count).put("tps", tps)
                        .put("ttftMs", generation.ttftMs()));
                } catch (IOException e) {
                    synchronized (sinkFailure) {
                        sinkFailure.add(e);
                    }
                    host.cancel();
                } catch (JSONException ignored) {
                }
            }

            @Override
            public void onComplete(@NonNull String fullText) {
                maybeEmitToken(true);
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
            }
        };
        JSONObject result;
        try {
            result = host.chat(entry, prompt, maxTokens, callback);
        } catch (RuntimeException e) {
            result = new JSONObject().put("ok", false).put("error", "generation_failed").put("message", message(e));
        } finally {
            limit.cancel(false);
        }
        synchronized (sinkFailure) {
            if (!sinkFailure.isEmpty()) throw sinkFailure.get(0);
        }
        generation.timedOut = pastLimit.get();
        generation.ok = result.optBoolean("ok", false);
        // A reply the callback cut short (the sustained window, an outside stop) comes back as
        // cancelled from MNN; it still measured what it measured.
        if (!generation.ok && "generation_cancelled".equals(result.optString("error", ""))
                && (holdMs > 0L || stopRequested()) && generation.callbackTokens > 0) {
            generation.ok = holdMs > 0L;
        }
        if (!generation.ok) {
            generation.errorCode = generation.timedOut ? "timeout" : result.optString("error", "generation_failed");
            generation.errorMessage = result.optString("message", generation.errorCode);
        }
        JSONObject usage = result.optJSONObject("usage");
        if (usage != null) {
            generation.promptTokens = usage.optInt("prompt_tokens", 0);
            generation.usageCompletionTokens = usage.optInt("completion_tokens", -1);
        }
        generation.usageEstimated = result.optBoolean("usageEstimated", usage == null);
        generation.finishReason = result.optString("finishReason", "");
        generation.tokenLimit = maxTokens;
        return generation;
    }

    /** Cancels the runtime when {@code limitMs} passes; {@code isDone() && !isCancelled()} afterwards means it fired. */
    @NonNull
    private ScheduledFuture<?> armWatchdog(long limitMs) {
        return armWatchdog(limitMs, null);
    }

    @NonNull
    private ScheduledFuture<?> armWatchdog(long limitMs, @Nullable Runnable before) {
        return watchdog.schedule(() -> {
            if (before != null) before.run();
            try {
                host.cancel();
            } catch (RuntimeException ignored) {
            }
        }, limitMs, TimeUnit.MILLISECONDS);
    }

    // ---- the record --------------------------------------------------------------------------

    /** Everything one entry produced, and the record it becomes. */
    private final class Record {
        @NonNull final TaiBenchSuite.EntryPlan entry;
        final long startedMs = System.currentTimeMillis();
        long loadMs = -1L;
        long memBytes = -1L;
        /** The runtime process's PSS after load and warm-up; {@code -1} until measured or when unreadable. */
        long pssBytes = -1L;
        @Nullable JSONObject loadResult;
        final TaiBenchStats.Series reading = new TaiBenchStats.Series();
        int readingTokens;
        final TaiBenchStats.Series firstWord = new TaiBenchStats.Series();
        final TaiBenchStats.Series writing = new TaiBenchStats.Series();
        int writingTokens;
        final PhaseEnd readingEnd = new PhaseEnd();
        final PhaseEnd firstWordEnd = new PhaseEnd();
        final PhaseEnd writingEnd = new PhaseEnd();
        @Nullable JSONObject sustained;
        int checkPassed = -1;
        @Nullable JSONArray checkDetails;
        @NonNull String status = "stopped:incomplete";
        @NonNull String phaseStatus = "ok";
        @Nullable String skipReason;
        @NonNull String lastReply = "";

        Record(@NonNull TaiBenchSuite.EntryPlan entry) {
            this.entry = entry;
        }

        /**
         * A run that failed or timed out: a timeout marks the phase and the entry keeps going with
         * what the earlier runs measured; a runtime error or an outside stop ends the entry.
         * Returns whether to go on.
         */
        boolean noteFailure(@NonNull String phase, @NonNull Generation generation) throws IOException, JSONException {
            lastReply = generation.reply();
            if (generation.timedOut) {
                phaseStatus = "timeout";
                status = "timeout";
                emit(event("error").put("entry", entry.toJson()).put("phase", phase).put("code", "timeout")
                    .put("message", "Stopped after " + TaiBenchSuite.timeLimitMs(phase) / 1000L + " s."));
                return true;
            }
            String outside = stopReason;
            status = outside != null ? "stopped:" + outside : "stopped:" + generation.errorCode;
            skipReason = generation.errorMessage;
            emit(event("error").put("entry", entry.toJson()).put("phase", phase)
                .put("code", generation.errorCode).put("message", generation.errorMessage));
            return false;
        }

        @NonNull
        JSONObject finish() throws JSONException {
            JSONObject describe = host.describe(entry);
            JSONObject phases = new JSONObject();
            phases.put("load", loadMs < 0L ? JSONObject.NULL
                : new JSONObject().put("ms", loadMs).put("memBytes", memBytes).put("pssBytes", pssBytes));
            phases.put("reading", orNull(reading.toJson() == null ? null
                : readingEnd.into(reading.toJson().put("promptTokens", readingTokens))));
            phases.put("firstWord", orNull(firstWord.toJson() == null ? null : firstWordEnd.into(firstWord.toJson())));
            phases.put("writing", orNull(writing.toJson() == null ? null
                : writingEnd.into(writing.toJson().put("tokens", writingTokens))));
            phases.put("sustained", orNull(sustained));
            JSONObject check = new JSONObject()
                .put("passed", Math.max(0, checkPassed))
                .put("total", TaiBenchSuite.CHECKS.size())
                .put("details", checkDetails == null ? new JSONArray() : checkDetails);
            boolean checkOk = checkPassed >= TaiBenchSuite.CHECKS.size();
            // Timeouts inside phases leave the entry marked, but the phases that finished still
            // count: "timeout" ranks nowhere, the figures are there to read.
            if (TaiBenchStore.STATUS_COMPLETE.equals(status) && "timeout".equals(phaseStatus)) status = "timeout";
            JSONObject record = new JSONObject();
            record.put("id", UUID.randomUUID().toString());
            record.put("benchVersion", TaiBenchSuite.BENCH_VERSION);
            record.put("preset", preset.id);
            record.put("timestamp", startedMs);
            record.put("durationMs", System.currentTimeMillis() - startedMs);
            record.put("modelId", entry.modelId);
            record.put("displayName", describe.optString("displayName", entry.modelId));
            record.put("sizeBytes", describe.optLong("sizeBytes", 0L));
            record.put("sha256", describe.isNull("sha256") ? JSONObject.NULL : describe.optString("sha256", null));
            record.put("backend", entry.backend);
            record.put("accelerator", entry.accelerator);
            record.put("speculative", entry.speculative);
            record.put("runtimeVersion", describe.optString("runtimeVersion", ""));
            record.put("appVersion", appVersion);
            record.put("device", device);
            record.put("conditions", guard.entryConditions(entry));
            record.put("phases", phases);
            record.put("check", check);
            record.put("status", status);
            record.put("verdict", TaiBenchStore.STATUS_COMPLETE.equals(status) || "timeout".equals(status)
                ? TaiBenchStats.verdict(writing.median(), checkOk) : JSONObject.NULL);
            if (skipReason != null) record.put("skipReason", skipReason);
            if (loadResult != null && loadResult.has("memoryBudget")) record.put("memoryBudget", loadResult.opt("memoryBudget"));
            return record;
        }
    }

    @NonNull
    private static Object orNull(@Nullable JSONObject json) {
        return json == null ? JSONObject.NULL : json;
    }

    @NonNull
    private static JSONObject metrics(@NonNull TaiBenchStats.Series series) throws JSONException {
        JSONObject json = series.toJson();
        return json == null ? new JSONObject().put("runs", 0) : json;
    }

    @NonNull
    private static JSONObject event(@NonNull String name) throws JSONException {
        return new JSONObject().put("event", name).put("at", System.currentTimeMillis());
    }

    private void emit(@NonNull JSONObject event) throws IOException {
        sink.onEvent(event);
    }

    @NonNull
    private static String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
