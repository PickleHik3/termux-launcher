package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * When a page whose picture is a {@code SurfaceView} — the Display place — stands a still copy of
 * that picture in for its surface, so the wall's motions reach it like any other page
 * ({@link SurfacePage}). A surface is composited outside the view hierarchy: a tilt leaves it
 * flat and a hardware layer strands it. A plain view holding a copy of its last frame takes both,
 * and the picture freezes for the motion's few hundred milliseconds, which is the price.
 *
 * <p>The lifecycle, one motion at a time:
 * <ul>
 *   <li><b>Begin</b> ({@link #begin}). A live surface is copied ({@link Begin#COPY}); the copy
 *   is asynchronous, and until it lands the page keeps today's motion — a scale, no layer, no
 *   tilt. A surface parked off screen has none to copy, so the page arriving uses the copy kept
 *   from its last motion when it is recent and the same size ({@link Begin#REUSE}), and slides in
 *   flat otherwise. A page with no display running has no surface at all: it is plain views and
 *   takes every transform as it is ({@link Begin#FLAT}).</li>
 *   <li><b>The copy lands</b> ({@link #copied}). Still wanted, the stand-in goes up at once, at
 *   whatever the motion has reached, and the surface goes down beneath it. Too late, it is only
 *   kept for the next arrival. Failed — no data, secure content, a surface gone — it is dropped
 *   and the motion carries on as it began.</li>
 *   <li><b>Rest</b> ({@link #rest}). The surface comes back first, beneath the stand-in, which
 *   goes once the surface has drawn ({@link #swapped}) — or at once, where the surface stays down
 *   (the page parked, the display stopped).</li>
 * </ul>
 *
 * <p>One copy is kept at most, and not for long: past {@link #ARRIVAL_MAX_AGE_MS} it is no
 * picture of what the display shows and is dropped ({@link #expire}); a display that stops, or a
 * page that leaves the wall, drops it too. Pure, so the lifecycle is testable without a surface;
 * the page holds the pixels and does what the answers say.
 */
public final class SurfaceStandIn {

    /**
     * How old a kept copy may be and still stand in for a page arriving: the flip back from the
     * place beside it, not a picture from minutes ago that snaps to the live screen as it lands.
     */
    public static final long ARRIVAL_MAX_AGE_MS = 30_000L;

    /** The frames the stand-in waits, once the surface is back, for the display to draw into it. */
    public static final int SWAP_FRAMES = 2;

    /** The swap's fail-safe: the stand-in goes by now whether or not the surface said it drew. */
    public static final long SWAP_BACKSTOP_MS = 300L;

    public enum State {
        /** No motion: the live surface is the picture, if there is one. */
        IDLE,
        /** A motion began and a copy of the surface is on its way; the page moves as it did. */
        COPYING,
        /** The copy stands in, the surface is down: the page takes a layer, a scale and a tilt. */
        SHOWN,
        /** No display runs: the page is plain views, and a display starting holds off till rest. */
        FLAT,
        /** At rest again: the surface is back beneath the stand-in, which waits for it to draw. */
        RESTORING
    }

    /** What {@link #begin} asks of the page. */
    public enum Begin {
        /** Nothing to stand in: the page moves as it always has. */
        NOTHING,
        /** Copy the live surface, into a bitmap its size; answer with {@link #copied}. */
        COPY,
        /** Stand the kept copy in now: the page is still at once. */
        REUSE,
        /** No surface to hide: the page is still at once, as it is. */
        FLAT,
        /** Already still, or its copy is on the way: nothing new to do. */
        KEEP
    }

    /** What a landed copy is for. */
    public enum Copied {
        /** Stand it in now. */
        SHOW,
        /** Keep it for the next arrival; the motion it was taken for is over. */
        KEEP,
        /** Let it go: it failed, or the page was told to forget its copies meanwhile. */
        DROP
    }

    /** What rest asks of the page. */
    public enum Rest {
        /** Nothing was standing in. */
        NONE,
        /** Take the stand-in down now, and let the surface follow its own state. */
        REMOVE,
        /** Put the surface back beneath the stand-in, and take it down once the surface drew. */
        SWAP
    }

    @NonNull private State mState = State.IDLE;
    /** Bumped for every motion's copy and every rest: a copy from an older one only keeps. */
    private int mGeneration;
    /** Bumped by every drop: a copy begun before one is let go, not kept. */
    private int mEpoch;
    private boolean mCached;
    private int mCachedWidth;
    private int mCachedHeight;
    private long mCachedAtMs;

    /**
     * A motion that moves the page begins.
     *
     * @param running     whether a display runs, so the page has a surface at all
     * @param surfaceLive whether that surface is on screen now with a valid buffer to copy
     * @param width       the surface view's size, which a copy and a kept copy must match
     */
    @NonNull
    public Begin begin(boolean running, boolean surfaceLive, int width, int height, long nowMs) {
        switch (mState) {
            case SHOWN:
            case FLAT:
            case COPYING:
                return Begin.KEEP;
            case RESTORING:
                // Moved again before the swap finished: the stand-in is still up and a moment
                // old, so the surface goes back down under it.
                mState = State.SHOWN;
                return Begin.KEEP;
            case IDLE:
            default:
                break;
        }
        if (!running) {
            mState = State.FLAT;
            return Begin.FLAT;
        }
        if (width <= 0 || height <= 0) return Begin.NOTHING;
        if (surfaceLive) {
            mGeneration++;
            mState = State.COPYING;
            return Begin.COPY;
        }
        if (cacheFits(width, height, nowMs)) {
            mState = State.SHOWN;
            return Begin.REUSE;
        }
        return Begin.NOTHING;
    }

    /** The generation a {@link Begin#COPY} belongs to; hand it back to {@link #copied}. */
    public int copyGeneration() {
        return mGeneration;
    }

    /** The drop epoch a {@link Begin#COPY} was begun in; hand it back to {@link #copied}. */
    public int copyEpoch() {
        return mEpoch;
    }

    /**
     * The copy begun as {@code generation} in {@code epoch} landed, {@code success} or not, into a
     * bitmap {@code width} by {@code height}. A success is the one copy kept from here, whatever
     * it is for; the page lets go of the one it held before.
     */
    @NonNull
    public Copied copied(int generation, int epoch, boolean success, int width, int height,
                         long nowMs) {
        boolean current = generation == mGeneration && mState == State.COPYING;
        if (!success || epoch != mEpoch || width <= 0 || height <= 0) {
            // The motion goes on without it, as it began.
            if (current) mState = State.IDLE;
            return Copied.DROP;
        }
        // A copy from an older motion while another stands in: the one standing in is the one
        // copy kept, and a second would be a second surface-sized bitmap.
        if (!current && isStandInShown()) return Copied.DROP;
        mCached = true;
        mCachedWidth = width;
        mCachedHeight = height;
        mCachedAtMs = nowMs;
        if (!current) return Copied.KEEP;
        mState = State.SHOWN;
        return Copied.SHOW;
    }

    /**
     * The wall is at rest with the page's sink risen.
     *
     * @param surfaceWillShow whether the surface comes back now: a display runs and the page is
     *                        on screen; otherwise the stand-in simply goes
     */
    @NonNull
    public Rest rest(boolean surfaceWillShow) {
        switch (mState) {
            case COPYING:
                // Too late for this motion; the landing only keeps it.
                mGeneration++;
                mState = State.IDLE;
                return Rest.NONE;
            case FLAT:
                mState = State.IDLE;
                return Rest.REMOVE;
            case SHOWN:
                mGeneration++;
                if (surfaceWillShow) {
                    mState = State.RESTORING;
                    return Rest.SWAP;
                }
                mState = State.IDLE;
                return Rest.REMOVE;
            case RESTORING:
            case IDLE:
            default:
                return Rest.NONE;
        }
    }

    /**
     * The surface is back and has drawn, or the swap's backstop ran out: whether the stand-in
     * comes down now. False once another motion took the page back.
     */
    public boolean swapped() {
        if (mState != State.RESTORING) return false;
        mState = State.IDLE;
        return true;
    }

    /**
     * Forget the kept copy: the display stopped, or its size moved on. A copy on its way is let
     * go when it lands. One standing in stays up until rest, since the page is mid-motion on it.
     *
     * @return whether the page can let go of the bitmap now, which it cannot while it stands in
     */
    public boolean drop() {
        mEpoch++;
        mCached = false;
        if (mState == State.COPYING) {
            mGeneration++;
            mState = State.IDLE;
        }
        return !isStandInShown();
    }

    /** The page is leaving the wall or the window: nothing stands in and nothing is kept. */
    public void reset() {
        mEpoch++;
        mGeneration++;
        mCached = false;
        mState = State.IDLE;
    }

    /**
     * The kept copy's time is up: whether it went, so the page lets go of its bitmap. A copy
     * standing in is not the page's to drop mid-motion.
     */
    public boolean expire(long nowMs) {
        if (!mCached || isStandInShown()) return false;
        if (nowMs - mCachedAtMs < ARRIVAL_MAX_AGE_MS && nowMs >= mCachedAtMs) return false;
        mCached = false;
        return true;
    }

    /** Whether the kept copy can stand in for a page {@code width} by {@code height} now. */
    public boolean cacheFits(int width, int height, long nowMs) {
        return mCached && width == mCachedWidth && height == mCachedHeight
            && nowMs >= mCachedAtMs && nowMs - mCachedAtMs < ARRIVAL_MAX_AGE_MS;
    }

    /** Whether a copy is kept, standing in or not. */
    public boolean hasCache() {
        return mCached;
    }

    /**
     * Whether the page can take a layer and a tilt now: its surface is down, or it has none. It
     * is also whether the page holds its surface down, whatever the display and the wall say.
     */
    public boolean isStill() {
        return mState == State.SHOWN || mState == State.FLAT;
    }

    /** Whether the stand-in view is up, over the surface or in its place. */
    public boolean isStandInShown() {
        return mState == State.SHOWN || mState == State.RESTORING;
    }

    @NonNull
    public State state() {
        return mState;
    }
}
