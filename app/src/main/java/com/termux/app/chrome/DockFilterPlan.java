package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * Which page's even-out the dock's glass (and the keyboard sharing it) wears, from a crossing's
 * begin through its swipe and landing back to rest: the one owner of that answer. Pure; the
 * activity holds the two ends' filters and applies what this says.
 *
 * <ul>
 *   <li>At rest the dock measures its even-out live, over the ground under it, and may keep
 *       what it measured as the look's own.</li>
 *   <li>A crossing begins with both ends known: the one up now, and the arriving page's worked out
 *       over that page's ground there and then (never only from what an earlier rest kept). The
 *       glass blends between them on the crossing's share; nothing is measured.</li>
 *   <li>Landed, the glass stands on the end it landed on until a pass can measure where it rests:
 *       no crossing, the even-out no longer held from the travel, and the stack wearing the
 *       landed look. Before that a pass would measure the held or the left page's even-out and
 *       put it up for a frame or two (pong 2026-11-20: a 2-frame dip of about 5 luma).</li>
 * </ul>
 */
public final class DockFilterPlan {

    /** No look: nothing stands on the plan, or the blend is between its ends. */
    public static final int NONE = Integer.MIN_VALUE;

    public enum Phase { REST, CROSSING, LANDED }

    @NonNull private Phase mPhase = Phase.REST;
    private int mHereLook = NONE;
    private int mThereLook = NONE;
    private boolean mEnds;
    private float mShare;
    private boolean mOnThere;

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    /**
     * A crossing from the page whose look is {@code hereLook} toward {@code thereLook} begins;
     * {@code bothEnds} says the filter up now and the arriving page's are both known.
     */
    public void begin(int hereLook, int thereLook, boolean bothEnds) {
        mPhase = Phase.CROSSING;
        mHereLook = hereLook;
        mThereLook = thereLook;
        mEnds = bothEnds;
        mShare = 0f;
        mOnThere = false;
    }

    /**
     * The crossing's share moved to {@code share} (0 here, 1 there): whether the glass blends to
     * it, which is once per step and only while a crossing with both ends runs.
     */
    public boolean step(float share) {
        if (mPhase != Phase.CROSSING || !mEnds || share == mShare) return false;
        mShare = share;
        return true;
    }

    /** The share the glass stands at: the blend's in a crossing, the landed end's once landed. */
    public float share() {
        if (mPhase == Phase.LANDED) return mOnThere ? 1f : 0f;
        return mShare;
    }

    /** The crossing landed, on the arriving page ({@code onThere}) or back where it began. */
    public void land(boolean onThere) {
        if (mPhase != Phase.CROSSING) return;
        mPhase = Phase.LANDED;
        mOnThere = onThere;
    }

    /**
     * A pass asks for the dock's frost: true when it measures live, false when it puts up the
     * plan's (the blend's, or the landed end's). Landed, the first pass that can measure where the
     * glass rests ends the landing.
     */
    public boolean measures(int stackLook, boolean crossing, boolean evenOutHeld) {
        switch (mPhase) {
            case CROSSING:
                return !mEnds;
            case LANDED:
                if (!mEnds) {
                    mPhase = Phase.REST;
                    return true;
                }
                if (crossing || evenOutHeld || stackLook != landedLook()) return false;
                mPhase = Phase.REST;
                return true;
            default:
                return true;
        }
    }

    /**
     * After the landing's frame, the travel's hold let go: whatever look the stack reads, the
     * glass measures from the next pass. A fail-safe, so a landing never outlives its settle.
     */
    public void settle(boolean crossing) {
        if (mPhase == Phase.LANDED && !crossing) mPhase = Phase.REST;
    }

    /** Whether a live measure may be kept as the stack look's own, for a later crossing. */
    public boolean keepsAtRest(boolean crossing) {
        return mPhase == Phase.REST && !crossing;
    }

    /** Whether the glass stands on the plan's ends now rather than on a live measure. */
    public boolean standsOnEnds() {
        return mEnds && (mPhase == Phase.CROSSING || mPhase == Phase.LANDED);
    }

    /**
     * The look whose even-out the glass wears now: the stack's at rest (measured live), an end's
     * when the blend stands on it, the landed end's once landed; {@link #NONE} between the ends.
     */
    public int standingLook(int stackLook) {
        if (mPhase == Phase.REST || !mEnds) return stackLook;
        if (mPhase == Phase.LANDED) return landedLook();
        if (mShare <= 0f) return mHereLook;
        if (mShare >= 1f) return mThereLook;
        return NONE;
    }

    private int landedLook() {
        return mOnThere ? mThereLook : mHereLook;
    }
}
