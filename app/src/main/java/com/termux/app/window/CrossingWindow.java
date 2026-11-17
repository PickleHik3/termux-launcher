package com.termux.app.window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether a page crossing is open, and the two counters that read it: {@code crossing-resized}
 * (a terminal size change inside the crossing) and {@code crossing-redraw-slow} (a terminal draw
 * inside it that took longer than {@link #SLOW_DRAW_NANOS}). A crossing must never resize the
 * terminal and must not redraw it stutteringly; an emulator sweep reads both off the counter table.
 *
 * <p>Opened by the crossing's begin and closed by the landing's settle (every wall settle at rest
 * closes it, whether the crossing landed or came back). A window nobody closed shuts itself
 * {@link #STALE_MS} after it opened, so it can never stick open. Pure: callers pass the clock,
 * and the shared window is the one the app feeds. Main thread only.
 */
public final class CrossingWindow {

    public static final String RESIZED = "crossing-resized";
    public static final String REDRAW_SLOW = "crossing-redraw-slow";
    /** The ring line written once at each landing. */
    public static final String DRAWS = "crossing-draws";

    /** Half a 60 Hz frame. */
    public static final long SLOW_DRAW_NANOS = 8_000_000L;
    /** A crossing and its settle take far less; an open window older than this is stale. */
    public static final long STALE_MS = 5000;

    /** Where the counters and the landing's ring line go. */
    public interface Sink {
        void count(@NonNull String name, @Nullable String detail);

        void ring(@NonNull String event, @Nullable String kv);
    }

    private static final Sink COUNTERS = new Sink() {
        @Override public void count(@NonNull String name, @Nullable String detail) {
            if (Counters.enabled) Counters.count(name, detail);
        }

        @Override public void ring(@NonNull String event, @Nullable String kv) {
            Counters.ring(event, kv);
        }
    };

    private static final CrossingWindow SHARED = new CrossingWindow(COUNTERS);

    /** The window the app feeds, counting into {@link Counters}. */
    @NonNull
    public static CrossingWindow shared() {
        return SHARED;
    }

    private final Sink mSink;
    private boolean mOpen;
    private long mOpenedAtMs;
    private int mDraws;
    private long mMaxDrawNanos;

    public CrossingWindow(@NonNull Sink sink) {
        mSink = sink;
    }

    /** A crossing began: the window opens and its draw tally starts at zero. */
    public void open(long nowMs) {
        mOpen = true;
        mOpenedAtMs = nowMs;
        mDraws = 0;
        mMaxDrawNanos = 0;
    }

    /** The landing's settle ran (or the crossing was cancelled): the window shuts, once. */
    public void close() {
        if (!mOpen) return;
        mOpen = false;
        mSink.ring(DRAWS, "draws=" + mDraws + " maxMs=" + mMaxDrawNanos / 1_000_000L);
    }

    /** Whether the window is open at {@code nowMs}; a stale one shuts here. */
    public boolean isOpen(long nowMs) {
        if (mOpen && nowMs - mOpenedAtMs > STALE_MS) close();
        return mOpen;
    }

    /** A terminal changed size; counted when a crossing is open. */
    public void resized(long nowMs, @NonNull String pane, @NonNull String from, @NonNull String to,
                        @NonNull String cause) {
        if (!isOpen(nowMs)) return;
        mSink.count(RESIZED, "pane=" + pane + " from=" + from + " to=" + to + " cause=" + cause);
    }

    /** A terminal draw took {@code durationNanos}; tallied when a crossing is open. */
    public void drew(long nowMs, @NonNull String pane, long durationNanos) {
        if (!isOpen(nowMs)) return;
        mDraws++;
        if (durationNanos > mMaxDrawNanos) mMaxDrawNanos = durationNanos;
        if (durationNanos > SLOW_DRAW_NANOS)
            mSink.count(REDRAW_SLOW, "pane=" + pane + " ms=" + durationNanos / 1_000_000L);
    }
}
