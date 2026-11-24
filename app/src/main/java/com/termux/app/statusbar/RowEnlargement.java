package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * When the thin row ({@link PaneRowPolicy}) is enlarged. Pure: the host feeds it the press, its
 * moves, the release and the ways out, and reads {@link #enlarged()} and {@link #timeoutMs()}.
 *
 * <p>A press enlarges the row. Moving past the slop makes it a slide (the Marks slide): the
 * release ends it. A press that does not move holds the row for {@link #HOLD_MS}, so the next
 * tap lands on a full-size mark; each further touch stops that timer, and its release restarts
 * it. The tap that only enlarged the row does not act ({@link Release#TAP_ENLARGED_ONLY}); the
 * ones after it do. A touch whose release never comes is ended by {@link #BACKSTOP_MS}. The
 * enlarged state also ends on a cancel, a page change and {@link #end} (focus loss, a pause, the
 * presentation leaving the row): the host calls {@link #timeout} when the armed time runs out.
 */
public final class RowEnlargement {

    /** How long a tap that did not move keeps the row enlarged, in ms. */
    public static final long HOLD_MS = 2000L;
    /** The longest a press may last before the row settles without it, in ms. */
    public static final long BACKSTOP_MS = 10000L;

    public enum Phase { IDLE, PRESSED, SLIDING, HELD }

    /** What a release meant. */
    public enum Release {
        /** No press was held. */
        NONE,
        /** A slide ended; the row settles. */
        SLIDE_ENDED,
        /** A tap on the row at rest: it enlarged the row and is not delivered as a tap. */
        TAP_ENLARGED_ONLY,
        /** A tap on the enlarged row: delivered, and the row stays for {@link #HOLD_MS}. */
        TAP
    }

    private final float mSlopSq;
    private Phase mPhase = Phase.IDLE;
    private boolean mFirstTouch;
    private float mDownX;
    private float mDownY;

    public RowEnlargement(float slopPx) {
        mSlopSq = slopPx * slopPx;
    }

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    public boolean enlarged() {
        return mPhase != Phase.IDLE;
    }

    /** The delay to arm after the last event, in ms; 0 when nothing is armed. */
    public long timeoutMs() {
        switch (mPhase) {
            case HELD: return HOLD_MS;
            case PRESSED:
            case SLIDING: return BACKSTOP_MS;
            default: return 0L;
        }
    }

    public void down(float x, float y) {
        mFirstTouch = mPhase == Phase.IDLE;
        mPhase = Phase.PRESSED;
        mDownX = x;
        mDownY = y;
    }

    public void move(float x, float y) {
        if (mPhase != Phase.PRESSED) return;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy > mSlopSq) mPhase = Phase.SLIDING;
    }

    @NonNull
    public Release up() {
        switch (mPhase) {
            case PRESSED: {
                mPhase = Phase.HELD;
                boolean first = mFirstTouch;
                mFirstTouch = false;
                return first ? Release.TAP_ENLARGED_ONLY : Release.TAP;
            }
            case SLIDING:
                mPhase = Phase.IDLE;
                return Release.SLIDE_ENDED;
            default:
                return Release.NONE;
        }
    }

    public void cancel() {
        end();
    }

    public void pageChanged() {
        end();
    }

    /** The armed time ran out: a hold or a press that never released. */
    public void timeout() {
        end();
    }

    /** Settles at once, whatever the phase. */
    public void end() {
        mPhase = Phase.IDLE;
        mFirstTouch = false;
    }
}
