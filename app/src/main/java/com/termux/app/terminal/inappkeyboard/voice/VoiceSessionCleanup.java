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

/**
 * The one cleanup pass of a dictation session (spec D5): the model warms on its own
 * {@code voice-cleanup} thread as the microphone opens, every phrase collects in the panel as heard
 * meanwhile, and once the session has ended the whole text goes through the
 * {@link VoiceTextPolisher} once, before ✓ or Copy uses it.
 * The warm-up is queued ahead of the pass on the same thread, so the pass waits for the load
 * instead of racing it. Callbacks come back on the main thread and never after {@link #cancel}.
 */
public final class VoiceSessionCleanup {

    private static final String LOG_TAG = "VoiceSessionCleanup";

    /** Called on the main thread with the raw text and what came of it. */
    public interface Callback {
        void onCleaned(@NonNull String raw, @NonNull VoiceTextPolisher.Result result);
    }

    private final VoiceTextPolisher polisher;
    private final ExecutorService worker;
    private final Executor main;
    private volatile boolean cancelled;
    /** Main-thread state. */
    private boolean warming;

    public VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher) {
        this(polisher, Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "voice-cleanup")),
            new Handler(Looper.getMainLooper())::post);
    }

    /** For tests: the worker and the main thread as plain executors. */
    VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher, @NonNull ExecutorService worker, @NonNull Executor main) {
        this.polisher = polisher;
        this.worker = worker;
        this.main = main;
    }

    /** Loads the model in the background; {@code onWarmed} runs on the main thread once it is done, loaded or not. */
    public void warm(@NonNull Runnable onWarmed) {
        warming = true;
        try {
            worker.execute(() -> {
                if (!cancelled) {
                    try {
                        polisher.warm();
                    } catch (RuntimeException e) {
                        Logger.logWarn(LOG_TAG, "cleanup warm failed: " + e.getMessage());
                    }
                }
                main.execute(() -> {
                    warming = false;
                    if (!cancelled) onWarmed.run();
                });
            });
        } catch (RejectedExecutionException e) {
            warming = false;
        }
    }

    /** Whether {@link #warm} is still loading the model. Main thread. */
    public boolean isWarming() {
        return warming;
    }

    /** Why {@code raw} would be left as heard, or {@code null} when {@link #run} would clean it. */
    @Nullable
    public static String skipReason(@NonNull String raw) {
        return VoicePolishRules.skipReason(raw);
    }

    /**
     * Cleans {@code raw} once, behind the warm-up. A session {@link #skipReason} rules out is
     * handed back at once as a fallback. The callback does not run once {@link #cancel} is called.
     */
    public void run(@NonNull String raw, @NonNull Callback callback) {
        String skip = skipReason(raw);
        if (skip != null) {
            log(0L, raw.length(), raw.length(), "skipped:" + skip);
            deliver(raw, VoiceTextPolisher.Result.fallback(raw, "skipped:" + skip), callback);
            return;
        }
        try {
            worker.execute(() -> {
                if (cancelled) return;
                long start = System.nanoTime();
                VoiceTextPolisher.Result result;
                try {
                    result = polisher.polish(raw, VoicePolishRules.timeoutMs(raw));
                } catch (RuntimeException e) {
                    result = VoiceTextPolisher.Result.fallback(raw, "exception");
                }
                log((System.nanoTime() - start) / 1_000_000L, raw.length(), result.text.length(), result.outcome);
                deliver(raw, result, callback);
            });
        } catch (RejectedExecutionException e) {
            // cancel() got there first; nobody is waiting for the answer.
        }
    }

    /** Drops the pass (and a warm-up still queued); the model is left to the runtime's idle unload. */
    public void cancel() {
        cancelled = true;
        worker.shutdown();
    }

    /** No more work after the pass already queued. */
    public void close() {
        worker.shutdown();
    }

    private void deliver(@NonNull String raw, @NonNull VoiceTextPolisher.Result result, @NonNull Callback callback) {
        main.execute(() -> {
            if (!cancelled) callback.onCleaned(raw, result);
        });
    }

    /** One line per session: timing, lengths and outcome, never the text. */
    private static void log(long cleanupMs, int inLength, int outLength, @NonNull String outcome) {
        Logger.logInfo(LOG_TAG, "cleanup: cleanupMs=" + cleanupMs + " inLength=" + inLength
            + " outLength=" + outLength + " outcome=" + outcome);
    }
}
