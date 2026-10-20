package com.termux.app.terminal.inappkeyboard.voice;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/**
 * A dictation session's cleanup (spec D5): the model warms on its own {@code voice-cleanup} thread
 * as the microphone opens when that costs no load, else at the session's end
 * ({@link #warmIfResident}, {@link VoiceCleanupWarmUp}); every phrase collects in the panel as
 * heard meanwhile, and the text goes
 * through the {@link VoiceTextPolisher} in the parts {@link VoiceCleanupPlan} hands out: early
 * passes at phrase pauses while the user speaks ({@link #onPhrase}), and once the session has ended
 * the rest ({@link #run}), before ✓ or Copy uses it. Each part goes through the gate
 * ({@link VoiceGatedCleanup}): fillers out, and only its sentences with a sign of trouble sent to the
 * model, in the dictation's language. The panel shows only the text as heard until
 * the whole cleaned text is delivered.
 * The warm-up is queued ahead of the passes on the same thread, so a pass waits for the load
 * instead of racing it. Callbacks come back on the main thread and never after {@link #cancel}.
 */
public final class VoiceSessionCleanup {

    private static final String LOG_TAG = "VoiceSessionCleanup";

    /** Called on the main thread with the raw text and what came of it. */
    public interface Callback {
        void onCleaned(@NonNull String raw, @NonNull VoiceTextPolisher.Result result);
    }

    private final VoiceTextPolisher polisher;
    @NonNull private final String level;
    private final ExecutorService worker;
    private final Executor main;
    private volatile boolean cancelled;
    /** The speech model's language for the dictation; {@code null} reads it from the text. */
    @Nullable private volatile String languageHint;
    /** The keyboard's or the phone's language, taken only when the text itself is unclear. */
    @Nullable private volatile String languageFallback;
    /** Main-thread state: the warm-up asked for as the mic opened is still checking or loading. */
    private boolean warming;
    /** Main-thread state: {@link #warmIfResident} was asked, so a cold model loads at {@link #run}. */
    private boolean warmAtEnd;
    /** Cleanup-thread state: the model has been warmed this session. */
    private boolean warmedOnWorker;
    private final VoiceCleanupPlan plan;
    /** Set by {@link #run}: the session has ended on this text, and this is waiting for it. */
    @Nullable private String finishingRaw;
    @Nullable private Callback finishing;
    private long finishStartNs;
    /** Main-thread state: the session has said once that it cuts parts at phrases. */
    private boolean loggedUnpunctuated;

    /** @param level the "Cleanup level" setting, for how the parts join */
    public VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher, @Nullable String level) {
        this(polisher, level, Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "voice-cleanup")),
            new Handler(Looper.getMainLooper())::post);
    }

    /** For tests: the worker and the main thread as plain executors. */
    VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher, @Nullable String level, @NonNull ExecutorService worker,
                        @NonNull Executor main) {
        this.polisher = polisher;
        this.level = VoicePolishRules.normalizeLevel(level);
        this.plan = new VoiceCleanupPlan(level);
        this.worker = worker;
        this.main = main;
    }

    /**
     * The dictation carries on from {@code base}, the text already in the panel: as it is when it is
     * final (a cleanup or the user's edit), else with what {@code previous}, the cleanup it replaces,
     * had cleaned of it. Call before the first phrase.
     */
    public void carryOn(@NonNull String base, boolean baseFinal, @Nullable VoiceSessionCleanup previous) {
        plan.carryOn(base, baseFinal, previous == null ? null : previous.plan);
    }

    /**
     * Loads the model in the background now; {@code onWarmed} runs on the main thread once it is
     * done, loaded or not. Early passes start only after it, and only when the model allows them.
     * {@link #isWarming} holds until then.
     */
    public void warm(@NonNull Runnable onWarmed) {
        warming = true;
        onWorkerThenMain(this::warmOnWorker, onWarmed);
    }

    /**
     * {@link #warm}, but only when it costs no load ({@link VoiceTextPolisher#warmLoadsNothing}: the
     * model is already resident, or is remote, or there is none); a cold model loads at the
     * session's end instead ({@link #run}), queued ahead of the passes, and early passes stay off
     * for this session. A cold GPU load held the GPU for seconds and the launcher's frames, the
     * waveform included, waited on it, and the first phrase pause comes while the user is still
     * talking. The next session finds the model warm. {@code onWarmed} runs on the main thread
     * once the check, and a load it allowed, is done. Call once, as the microphone opens.
     */
    public void warmIfResident(@NonNull Runnable onWarmed) {
        warmAtEnd = true;
        warming = true;
        onWorkerThenMain(() -> {
            boolean resident;
            try {
                resident = polisher.warmLoadsNothing();
            } catch (RuntimeException e) {
                resident = false;
            }
            VoiceCleanupWarmUp.Trigger trigger = VoiceCleanupWarmUp.atOpen(resident);
            if (trigger == null) {
                Logger.logInfo(LOG_TAG, "cleanup warm deferred: the model is cold; it loads at the session's end"
                    + " so no load holds the GPU while the user speaks; early passes off");
                return false;
            }
            Logger.logInfo(LOG_TAG, "cleanup warm start: at " + trigger.why);
            return warmOnWorker();
        }, onWarmed);
    }

    /** Runs {@code onWorker} on the cleanup thread; its answer allows early passes, then {@code onWarmed} runs. */
    private void onWorkerThenMain(@NonNull BooleanSupplier onWorker, @NonNull Runnable onWarmed) {
        try {
            worker.execute(() -> {
                boolean allowed = !cancelled && onWorker.getAsBoolean();
                main.execute(() -> {
                    warming = false;
                    if (cancelled) return;
                    plan.allowEarlyPasses(allowed);
                    onWarmed.run();
                });
            });
        } catch (RejectedExecutionException e) {
            warming = false;
        }
    }

    /** Loads the model, once a session. Whether early passes may run. Cleanup thread. */
    private boolean warmOnWorker() {
        warmedOnWorker = true;
        try {
            polisher.warm();
            return polisher.cleansWhileListening();
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "cleanup warm failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * The language the speech model heard, as an ISO 639 code ("es", "en-US"), or {@code null} to read
     * it from the text ({@link VoiceTextLanguage#guess}). Passes that start after this use it to name
     * the language in the prompt and to pick the fillers they strip. Any thread.
     */
    public void setLanguageHint(@Nullable String iso639) {
        languageHint = VoiceTextLanguage.normalize(iso639);
    }

    /**
     * The language to assume when there is no hint and the text does not say ({@link
     * VoiceTextLanguage#guess} is unclear): the keyboard layout's or the phone's. Without it, a
     * short part that reads as no language in particular gets its fillers stripped and nothing
     * else. Any thread.
     */
    public void setLanguageFallback(@Nullable String iso639) {
        languageFallback = VoiceTextLanguage.normalize(iso639);
    }

    /** Whether the warm-up asked for as the mic opened is still checking or loading the model. Main thread. */
    public boolean isWarming() {
        return warming;
    }

    /** Why {@code raw} would be left as heard, or {@code null} when {@link #run} would clean it. */
    @Nullable
    public static String skipReason(@NonNull String raw) {
        return VoicePolishRules.skipReason(raw);
    }

    /**
     * A phrase has landed and the dictation's text is now {@code raw}: starts an early pass when one
     * is due ({@link VoiceCleanupPlan#nextEarlyPass}). Main thread; no-op once the session has ended.
     */
    public void onPhrase(@NonNull String raw, boolean transcriptionPending, boolean byHand) {
        if (cancelled || finishing != null) return;
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(raw, transcriptionPending, byHand);
        if (pass == null) return;
        if (pass.unpunctuated && !loggedUnpunctuated) {
            loggedUnpunctuated = true;
            Logger.logInfo(LOG_TAG, "cleanup early: no sentence ends from the recogniser; parts cut at phrases");
        }
        submit(pass);
    }

    /**
     * The session has ended on {@code raw}: cleans what the early passes have not, after the one in
     * flight, and hands the whole text back once. A session {@link #skipReason} rules out is handed
     * back at once as a fallback. The callback does not run once {@link #cancel} is called. Main
     * thread; no work is taken after this.
     */
    public void run(@NonNull String raw, @NonNull Callback callback) {
        String skip = skipReason(raw);
        if (skip != null) {
            log("skipped", 0L, 0, raw.length(), raw.length(), "skipped:" + skip);
            worker.shutdown();
            deliver(raw, VoiceTextPolisher.Result.fallback(raw, "skipped:" + skip), callback);
            return;
        }
        if (cancelled) return;
        if (warmAtEnd) warmAtEnd();
        finishingRaw = raw;
        finishing = callback;
        finishStartNs = System.nanoTime();
        advance();
    }

    /** Drops every pass (and a warm-up still queued); the model is left to the runtime's idle unload. */
    public void cancel() {
        cancelled = true;
        worker.shutdown();
    }

    /** A model that was cold as the mic opened loads now, queued ahead of the passes. */
    private void warmAtEnd() {
        try {
            worker.execute(() -> {
                if (cancelled) return;
                VoiceCleanupWarmUp.Trigger trigger = VoiceCleanupWarmUp.atEnd(warmedOnWorker);
                if (trigger == null) return;
                Logger.logInfo(LOG_TAG, "cleanup warm start: at " + trigger.why);
                warmOnWorker();
            });
        } catch (RejectedExecutionException ignored) {
            // cancel() got there first: nothing will be cleaned.
        }
    }

    /** The end's next step: wait for the pass in flight, send the next, or deliver the whole text. */
    private void advance() {
        String raw = finishingRaw;
        Callback callback = finishing;
        if (raw == null || callback == null || cancelled) return;
        while (true) {
            VoiceCleanupPlan.Step step = plan.finish(raw);
            if (step.waits) return;
            VoiceCleanupPlan.Pass pass = step.pass;
            if (pass == null) break;
            String skip = VoiceCleanupPlan.skipReason(pass);
            if (skip != null) {
                plan.onPassDone(pass, VoiceTextPolisher.Result.fallback(pass.raw, "skipped:" + skip));
                continue;
            }
            if (submit(pass)) return;
        }
        finishing = null;
        finishingRaw = null;
        worker.shutdown();
        VoiceTextPolisher.Result result = plan.result(raw);
        log("session", (System.nanoTime() - finishStartNs) / 1_000_000L, plan.partCount(), raw.length(),
            result.text.length(), result.outcome);
        deliver(raw, result, callback);
    }

    /**
     * Sends {@code pass} to the cleanup thread; its answer comes back to the plan on the main thread,
     * and moves the end on when the session has ended. False when the thread took no more work, the
     * pass then kept as heard.
     */
    private boolean submit(@NonNull VoiceCleanupPlan.Pass pass) {
        try {
            worker.execute(() -> {
                if (cancelled) return;
                long start = System.nanoTime();
                VoiceTextPolisher.Result result;
                String gated = "";
                try {
                    VoiceGatedCleanup.Outcome outcome =
                        VoiceGatedCleanup.clean(polisher, pass.raw, pass.context, level, languageHint, languageFallback);
                    result = outcome.result(pass.raw, pass.early);
                    gated = " lang=" + (outcome.language == null ? "unclear" : outcome.language)
                        + " sent=" + outcome.calls + "/" + outcome.groups;
                } catch (RuntimeException e) {
                    result = VoiceTextPolisher.Result.fallback(pass.raw, "exception");
                }
                log(pass.early ? "early" : "final", (System.nanoTime() - start) / 1_000_000L,
                    VoicePolishRules.wordCount(pass.context), pass.raw.length(), result.text.length(),
                    result.outcome + gated);
                VoiceTextPolisher.Result done = result;
                main.execute(() -> {
                    if (cancelled) return;
                    plan.onPassDone(pass, done);
                    advance();
                });
            });
            return true;
        } catch (RejectedExecutionException e) {
            // cancel() got there first, or the end already delivered: nobody waits for this pass.
            plan.onPassDone(pass, VoiceTextPolisher.Result.fallback(pass.raw, "rejected"));
            return false;
        }
    }

    private void deliver(@NonNull String raw, @NonNull VoiceTextPolisher.Result result, @NonNull Callback callback) {
        main.execute(() -> {
            if (!cancelled) callback.onCleaned(raw, result);
        });
    }

    /** One line per pass and one per session: timing, sizes and outcome, never the text. */
    private static void log(@NonNull String what, long cleanupMs, int count, int inLength, int outLength,
                            @NonNull String outcome) {
        String counted = "session".equals(what) ? " passes=" + count : " contextWords=" + count;
        Logger.logInfo(LOG_TAG, "cleanup " + what + ": cleanupMs=" + cleanupMs + counted + " inLength=" + inLength
            + " outLength=" + outLength + " outcome=" + outcome);
    }
}
