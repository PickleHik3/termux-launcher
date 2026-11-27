package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * The border hold (spec #384, "Paging"): a finger held still on a window's left or right border for
 * {@link #HOLD_MS} pages the row once, one step toward that side's neighbour, and shows a small
 * {@code <} or {@code >} at that border while it fills. The same rule serves every window and the
 * carry of a Linux window. Pure: the owner feeds it the finger and the clock, asks {@link #fill}
 * for the glyph and {@link #fires} for the page, and schedules its own timer for {@link #HOLD_MS}.
 *
 * <p>Whether a finger that moves is still a hold is the owner's say: the wall's border drag takes
 * a moving finger for good ({@link #moved} with {@code restart} false ends the hold), while a
 * carry lets the finger start a new hold where it settles ({@code restart} true). A hold fires
 * once; the next one starts only after the finger has moved.
 *
 * <p>The tiling desktop's hold ({@code new BorderHold(true)}, #418) also holds on the top and bottom
 * borders, which step one place up or down ({@link #verticalSteps}); the row's stays on the sides.
 */
public final class BorderHold {

    /** How long a still finger holds before the row pages, in ms. */
    public static final long HOLD_MS = 1500L;

    /**
     * How long a hold that has stepped holds on before it steps again, in ms ({@link #repeat}); the
     * tiling desktop's. Tuned on a device together with {@link #HOLD_MS}.
     */
    public static final long REPEAT_MS = 600L;

    /** The glyph shows only once the hold has filled this far, so a short rest draws nothing. */
    public static final float GLYPH_FROM = 0.2f;

    private final boolean mFourEdges;
    private BorderDrag.Border mSide = BorderDrag.Border.NONE;
    private float mAnchorX;
    private float mAnchorY;
    private long mStartMs;
    private boolean mFired;
    /** The beat the hold in progress fills over: {@link #HOLD_MS} first, {@link #REPEAT_MS} after a repeat. */
    private long mBeatMs = HOLD_MS;

    /** A hold on the left and right borders only: the row's. */
    public BorderHold() {
        this(false);
    }

    /** {@code fourEdges}: the top and bottom borders hold too. */
    public BorderHold(boolean fourEdges) {
        mFourEdges = fourEdges;
    }

    /** The steps a hold on {@code side} goes up or down: -1 at the top, +1 at the bottom, 0 elsewhere. */
    public static int verticalSteps(@NonNull BorderDrag.Border side) {
        return side == BorderDrag.Border.TOP ? -1 : side == BorderDrag.Border.BOTTOM ? 1 : 0;
    }

    /** The steps a hold on {@code side} pages: -1 on the left, +1 on the right, 0 elsewhere. */
    public static int steps(@NonNull BorderDrag.Border side) {
        return side == BorderDrag.Border.LEFT ? -1 : side == BorderDrag.Border.RIGHT ? 1 : 0;
    }

    /**
     * A finger is down on {@code side} at ({@code x}, {@code y}); any border but a side idles it,
     * and so do the top and bottom unless this hold takes all four.
     */
    public void begin(@NonNull BorderDrag.Border side, float x, float y, long nowMs) {
        boolean holds = steps(side) != 0 || (mFourEdges && verticalSteps(side) != 0);
        mSide = holds ? side : BorderDrag.Border.NONE;
        mAnchorX = x;
        mAnchorY = y;
        mStartMs = nowMs;
        mFired = false;
        mBeatMs = HOLD_MS;
    }

    /**
     * A hold that has stepped holds on: it fills again over {@link #REPEAT_MS} from {@code nowMs}
     * and steps again when that is spent. Nothing happens unless the hold has fired.
     */
    public void repeat(long nowMs) {
        if (mSide == BorderDrag.Border.NONE || !mFired) return;
        mStartMs = nowMs;
        mFired = false;
        mBeatMs = REPEAT_MS;
    }

    /** The beat the hold in progress fills over, in ms: when the owner's timer should fire. */
    public long beatMs() {
        return mBeatMs;
    }

    /** The finger moved to ({@code x}, {@code y}); see the class note for {@code restart}. */
    public void moved(float x, float y, float slopPx, boolean restart, long nowMs) {
        if (mSide == BorderDrag.Border.NONE) return;
        float dx = x - mAnchorX;
        float dy = y - mAnchorY;
        if (dx * dx + dy * dy <= slopPx * slopPx) return;
        if (restart) {
            mAnchorX = x;
            mAnchorY = y;
            mStartMs = nowMs;
            mFired = false;
            mBeatMs = HOLD_MS;
        } else {
            mSide = BorderDrag.Border.NONE;
        }
    }

    /** The finger left the border: a hold in progress ends; the next one begins with {@link #begin}. */
    public void end() {
        mSide = BorderDrag.Border.NONE;
        mFired = false;
    }

    /** Whether a hold is filling now: a finger on a side border that has not paged yet. */
    public boolean isFilling() {
        return mSide != BorderDrag.Border.NONE && !mFired;
    }

    /** When the hold in progress began, in the owner's clock; a restart moves it. */
    public long startMs() {
        return mStartMs;
    }

    @NonNull
    public BorderDrag.Border side() {
        return mSide;
    }

    /** How full the hold is at {@code nowMs}, 0..1; 0 when nothing is filling. */
    public float fill(long nowMs) {
        if (!isFilling()) return 0f;
        float f = (nowMs - mStartMs) / (float) mBeatMs;
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }

    /** How strongly the glyph shows at {@code nowMs}, 0..1: nothing before {@link #GLYPH_FROM}. */
    public float glyph(long nowMs) {
        float f = fill(nowMs);
        if (f <= GLYPH_FROM) return 0f;
        return (f - GLYPH_FROM) / (1f - GLYPH_FROM);
    }

    /**
     * Whether the hold has run its time at {@code nowMs} and pages now: true once per hold, and it
     * marks the hold as spent. {@code canStep} says the row has a window on that side; without one
     * the hold only fills and nothing pages.
     */
    public boolean fires(long nowMs, boolean canStep) {
        if (!isFilling() || nowMs - mStartMs < mBeatMs) return false;
        if (!canStep) return false;
        mFired = true;
        return true;
    }
}
