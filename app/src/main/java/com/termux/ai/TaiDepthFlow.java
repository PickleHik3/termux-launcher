package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The order of a depth request, kept apart from threads and services so it can be tested: download
 * the model when it is missing, then make the image, then finish. It is fed events and answers what
 * to do next; it never calls out except to its {@link Sink}. After {@link #cancel} or a terminal
 * event every further event is ignored and the sink hears nothing more. All methods are
 * synchronized, so events may come from the hub's thread, the run's thread and the caller's.
 */
final class TaiDepthFlow {
    /** What the caller does next. */
    enum Action { NONE, START_DOWNLOAD, START_MAKE, CANCEL_DOWNLOAD, CANCEL_MAKE }

    enum Phase { NEW, DOWNLOADING, MAKING, DONE, FAILED, CANCELLED }

    /** The facade's callbacks, in flow order; invoked under the flow's lock, so they must only post. */
    interface Sink {
        void onDownloading(int percent);

        void onMaking(int percent);

        void onDone();

        void onFailed(@NonNull String code);
    }

    static final String CODE_DOWNLOAD_FAILED = "download_failed";
    static final String CODE_DOWNLOAD_START_FAILED = "download_start_failed";
    static final String CODE_NO_SPACE = "no_space";

    private final Sink sink;
    private Phase phase = Phase.NEW;
    private int lastPercent = -1;

    TaiDepthFlow(@NonNull Sink sink) {
        this.sink = sink;
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized boolean finished() {
        return phase == Phase.DONE || phase == Phase.FAILED || phase == Phase.CANCELLED;
    }

    /** Starts the request: the download when the model is missing, else the run. */
    synchronized Action begin(boolean modelReady) {
        if (phase != Phase.NEW) return Action.NONE;
        if (modelReady) {
            phase = Phase.MAKING;
            return Action.START_MAKE;
        }
        phase = Phase.DOWNLOADING;
        sink.onDownloading(0);
        lastPercent = 0;
        return Action.START_DOWNLOAD;
    }

    /** The download could not even be queued. */
    synchronized Action onDownloadStartFailed() {
        return fail(Phase.DOWNLOADING, CODE_DOWNLOAD_START_FAILED);
    }

    /** One download snapshot of the model's record, in the hub's shape. */
    synchronized Action onDownloadSnapshot(@Nullable TaiDownloadHub.Snapshot snapshot, boolean seenBefore) {
        if (phase != Phase.DOWNLOADING) return Action.NONE;
        if (snapshot == null) {
            // A record that was there and is gone was cancelled or the model deleted behind us.
            return seenBefore ? fail(Phase.DOWNLOADING, CODE_DOWNLOAD_FAILED) : Action.NONE;
        }
        String status = snapshot.status;
        if (TaiModelStore.STATE_INSTALLED.equals(status) || "complete".equals(status)) {
            phase = Phase.MAKING;
            lastPercent = -1;
            return Action.START_MAKE;
        }
        if (TaiModelStore.STATE_FAILED.equals(status) || TaiModelStore.STATE_CANCELLED.equals(status)
                || TaiModelStore.STATE_UNAVAILABLE.equals(status)) {
            boolean noSpace = snapshot.requiredBytes > 0L && snapshot.freeBytes > 0L
                && snapshot.freeBytes < snapshot.requiredBytes;
            return fail(Phase.DOWNLOADING, noSpace ? CODE_NO_SPACE : CODE_DOWNLOAD_FAILED);
        }
        long total = snapshot.totalBytes;
        int percent = TaiModelStore.STATE_VERIFYING.equals(status) ? 100
            : total > 0L ? (int) Math.max(0L, Math.min(100L, snapshot.bytesRead * 100L / total)) : 0;
        if (percent > lastPercent) {
            lastPercent = percent;
            sink.onDownloading(percent);
        }
        return Action.NONE;
    }

    /** A stage of the run, with its 0-100 share of the whole. */
    synchronized Action onMakeStage(int percent) {
        if (phase != Phase.MAKING) return Action.NONE;
        int clamped = Math.max(0, Math.min(100, percent));
        if (clamped > lastPercent) {
            lastPercent = clamped;
            sink.onMaking(clamped);
        }
        return Action.NONE;
    }

    /** The run's result; {@code code} is the failure code when {@code ok} is false. */
    synchronized Action onMakeResult(boolean ok, @Nullable String code) {
        if (phase != Phase.MAKING) return Action.NONE;
        if (ok) {
            phase = Phase.DONE;
            sink.onDone();
            return Action.NONE;
        }
        return fail(Phase.MAKING, code == null || code.isEmpty() ? DepthMaker.CODE_RUN_FAILED : code);
    }

    /** The user cancelled: what to stop, and nothing is heard afterwards. */
    synchronized Action cancel() {
        Phase was = phase;
        if (was == Phase.DONE || was == Phase.FAILED || was == Phase.CANCELLED) return Action.NONE;
        phase = Phase.CANCELLED;
        if (was == Phase.DOWNLOADING) return Action.CANCEL_DOWNLOAD;
        if (was == Phase.MAKING) return Action.CANCEL_MAKE;
        return Action.NONE;
    }

    private Action fail(@NonNull Phase expected, @NonNull String code) {
        if (phase != expected) return Action.NONE;
        phase = Phase.FAILED;
        sink.onFailed(code);
        return Action.NONE;
    }
}
