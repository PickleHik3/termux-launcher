package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The runtime's serial lane: one job runs at a time, on one thread of its own (ADR 0027 §4).
 * Every job has an id and a priority. A waiting {@link TaiRuntimeLink.Priority#INTERACTIVE} job
 * goes ahead of waiting {@link TaiRuntimeLink.Priority#NORMAL} ones; jobs of one priority run in
 * the order they came. A running job is never interrupted by another.
 *
 * <p>{@link #cancel} by id takes a waiting job out of the queue (its {@link Job#onCancelled} runs
 * and it never starts), asks the running job to stop through the canceller given to the
 * constructor, and does nothing for an id it does not hold.
 *
 * <p>A job submitted from the lane's own thread (a job that itself asks for a generation) runs at
 * once, inline, instead of waiting behind the job that asked for it forever.
 *
 * <p>Plain Java: no Android types, so the queue's rules are tested on the JVM alone.
 */
final class GenerationQueue {
    /** What a job is told about its place in the queue; every method is called once at most. */
    interface Job {
        /** Runs the work, on the lane's thread. */
        void run();

        /**
         * The job did not start at once: another job is running or waiting ahead of it. Called on
         * the submitting thread, before {@link #submit} returns.
         */
        default void onWaiting() {
        }

        /** The job is about to run, on the lane's thread, right before {@link #run}. */
        default void onStarted() {
        }

        /** The job was cancelled while waiting and will never run; called on the cancelling thread. */
        default void onCancelled() {
        }
    }

    /** What {@link #cancel} did. */
    enum CancelOutcome { REMOVED_WAITING, STOPPED_RUNNING, NONE }

    private static final class Entry {
        final String id;
        final TaiRuntimeLink.Priority priority;
        final long order;
        final Job job;
        /** Set when the running job's id is cancelled; the job reads it before it generates. */
        volatile boolean cancelled;

        Entry(String id, TaiRuntimeLink.Priority priority, long order, Job job) {
            this.id = id;
            this.priority = priority;
            this.order = order;
            this.job = job;
        }
    }

    /** The job the current lane thread is running, for {@link #currentJobCancelled}. */
    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private final Object lock = new Object();
    private final List<Entry> waiting = new ArrayList<>();
    private final Runnable cancelRunning;
    private final ExecutorService lane;
    private final AtomicLong order = new AtomicLong();
    private final AtomicLong taskIds = new AtomicLong();
    @Nullable private volatile Thread laneThread;
    @Nullable private Entry running;

    /**
     * @param threadName     the lane thread's name
     * @param cancelRunning  stops whatever generation is running now ({@code TaiRuntime.cancel()});
     *                       called when the running job's id is cancelled
     */
    GenerationQueue(@NonNull String threadName, @NonNull Runnable cancelRunning) {
        this.cancelRunning = cancelRunning;
        this.lane = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            laneThread = thread;
            return thread;
        });
    }

    /**
     * Queues {@code job} under {@code id}. A job that cannot start at once hears
     * {@link Job#onWaiting} before this returns.
     */
    void submit(@NonNull String id, @NonNull TaiRuntimeLink.Priority priority, @NonNull Job job) {
        if (Thread.currentThread() == laneThread) {
            // Re-entered from a running job: it would wait for itself. Run it here and now.
            job.onStarted();
            job.run();
            return;
        }
        boolean waits;
        synchronized (lock) {
            waits = running != null || !waiting.isEmpty();
            waiting.add(new Entry(id, priority, order.getAndIncrement(), job));
        }
        if (waits) job.onWaiting();
        try {
            lane.execute(this::runNext);
        } catch (RejectedExecutionException e) {
            // The lane was shut down (the service is being destroyed): nothing will run it.
            if (removeWaiting(id) != null) job.onCancelled();
        }
    }

    /** Queues plain lane work (a load, embeddings, a benchmark) at normal priority, under an id of its own. */
    void submitTask(@NonNull Runnable task) {
        submit("task-" + taskIds.incrementAndGet(), TaiRuntimeLink.Priority.NORMAL, task::run);
    }

    /**
     * Cancels {@code id}: a waiting job is removed and told; the running one is asked to stop;
     * anything else is left alone.
     */
    @NonNull
    CancelOutcome cancel(@NonNull String id) {
        Entry removed;
        boolean isRunning;
        synchronized (lock) {
            removed = removeWaitingLocked(id);
            isRunning = removed == null && running != null && running.id.equals(id);
            // Before the canceller runs: a job still loading its model sees the flag before it
            // generates, which TaiRuntime.cancel() alone could not reach.
            if (isRunning) running.cancelled = true;
        }
        if (removed != null) {
            removed.job.onCancelled();
            return CancelOutcome.REMOVED_WAITING;
        }
        if (isRunning) {
            cancelRunning.run();
            return CancelOutcome.STOPPED_RUNNING;
        }
        return CancelOutcome.NONE;
    }

    /**
     * Whether the job running on this thread has had its id cancelled. A job checks this after
     * its load and before it generates; {@code false} off a lane thread.
     */
    static boolean currentJobCancelled() {
        Entry current = CURRENT.get();
        return current != null && current.cancelled;
    }

    /**
     * Takes every waiting job out of the queue; each hears {@link Job#onCancelled} and never runs.
     * The running job is left as it is. Returns how many were cancelled.
     */
    int cancelWaiting() {
        List<Entry> removed;
        synchronized (lock) {
            removed = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (Entry entry : removed) entry.job.onCancelled();
        return removed.size();
    }

    /** The running job's id, or {@code null}. */
    @Nullable
    String runningId() {
        synchronized (lock) {
            return running == null ? null : running.id;
        }
    }

    /** The waiting ids, in the order they will run. */
    @NonNull
    List<String> waitingIds() {
        synchronized (lock) {
            List<Entry> sorted = new ArrayList<>(waiting);
            sorted.sort(GenerationQueue::compare);
            List<String> ids = new ArrayList<>(sorted.size());
            for (Entry entry : sorted) ids.add(entry.id);
            return ids;
        }
    }

    /** Stops the lane; waiting jobs are dropped without running. */
    void shutdownNow() {
        lane.shutdownNow();
    }

    private void runNext() {
        Entry next;
        synchronized (lock) {
            next = null;
            for (Entry entry : waiting) {
                if (next == null || compare(entry, next) < 0) next = entry;
            }
            if (next == null) return;
            waiting.remove(next);
            running = next;
        }
        CURRENT.set(next);
        try {
            next.job.onStarted();
            next.job.run();
        } catch (Throwable ignored) {
            // A job reports its own failures; the lane must outlive any of them.
        } finally {
            CURRENT.remove();
            synchronized (lock) {
                running = null;
            }
        }
    }

    @Nullable
    private Entry removeWaiting(@NonNull String id) {
        synchronized (lock) {
            return removeWaitingLocked(id);
        }
    }

    @Nullable
    private Entry removeWaitingLocked(@NonNull String id) {
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).id.equals(id)) return waiting.remove(i);
        }
        return null;
    }

    /** Interactive before normal; within one priority, first come first served. */
    private static int compare(@NonNull Entry a, @NonNull Entry b) {
        if (a.priority != b.priority) return a.priority == TaiRuntimeLink.Priority.INTERACTIVE ? -1 : 1;
        return Long.compare(a.order, b.order);
    }
}
