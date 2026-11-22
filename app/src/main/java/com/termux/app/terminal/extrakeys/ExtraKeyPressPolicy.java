package com.termux.app.terminal.extrakeys;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

/**
 * One touch on one key as a small state machine. The view owns the timers and the touch events and
 * asks this what to do; nothing here touches Android.
 */
public final class ExtraKeyPressPolicy {

    public enum Kind { PLAIN, REPEAT, MODIFIER, DEAD }

    public enum Action {
        NONE, FIRE_PRIMARY, FIRE_SECOND, REPEAT_FIRE, MODIFIER_TAP, MODIFIER_HOLD, HOLD_TO_HOST
    }

    public enum Move { NONE, PAGE_DRAG, SWIPE }

    public static final int REPEAT_INTERVAL_MS = 80;
    public static final float SWIPE_FIRE_DP = 24f;
    public static final int GLOW_FADE_MS = 220;
    public static final int PULSE_MS = 60;
    public static final float PULSE_SCALE = 0.92f;

    private final Kind mKind;
    private final boolean mHasSecond;
    private final boolean mHoldActsOnKey;

    private boolean mPressed;
    private boolean mPaging;
    private boolean mSwiping;
    private boolean mRepeating;
    private boolean mHeldModifier;
    private boolean mHandedToHost;
    private float mSwipeProgress;

    public ExtraKeyPressPolicy(Kind kind, boolean hasSecond, boolean holdActsOnKey) {
        mKind = kind == null ? Kind.DEAD : kind;
        mHasSecond = hasSecond;
        mHoldActsOnKey = holdActsOnKey;
    }

    public static Kind kindOf(String key, boolean usable) {
        if (!usable) return Kind.DEAD;
        if (ExtraKeyModifiers.isModifierKey(key)) return Kind.MODIFIER;
        if (key != null && ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS.contains(key)) return Kind.REPEAT;
        return Kind.PLAIN;
    }

    public Action down() {
        mPressed = true;
        mPaging = false;
        mSwiping = false;
        mRepeating = false;
        mHeldModifier = false;
        mHandedToHost = false;
        mSwipeProgress = 0f;
        return Action.NONE;
    }

    public Action holdElapsed() {
        if (!mPressed || mPaging || mSwiping || mHandedToHost) return Action.NONE;
        if (!mHoldActsOnKey) {
            mHandedToHost = true;
            return Action.HOLD_TO_HOST;
        }
        switch (mKind) {
            case REPEAT:
                mRepeating = true;
                return Action.REPEAT_FIRE;
            case MODIFIER:
                mHeldModifier = true;
                return Action.MODIFIER_HOLD;
            default:
                return Action.NONE;
        }
    }

    public Action repeatTick() {
        return mPressed && mRepeating ? Action.REPEAT_FIRE : Action.NONE;
    }

    public Move move(float dxPx, float dyPx, float touchSlopPx, float fireDistancePx) {
        if (!mPressed || mHandedToHost) return Move.NONE;
        if (mPaging) return Move.PAGE_DRAG;
        if (mSwiping) {
            mSwipeProgress = ExtraKeyGesturePolicy.swipeProgress(-dyPx, fireDistancePx);
            return Move.SWIPE;
        }
        float ax = Math.abs(dxPx);
        float ay = Math.abs(dyPx);
        if (ax > touchSlopPx && ax > ay) {
            mPaging = true;
            mRepeating = false;
            return Move.PAGE_DRAG;
        }
        if (mHasSecond && dyPx < 0 && ay > touchSlopPx && ay >= ax) {
            mSwiping = true;
            mRepeating = false;
            mSwipeProgress = ExtraKeyGesturePolicy.swipeProgress(-dyPx, fireDistancePx);
            return Move.SWIPE;
        }
        return Move.NONE;
    }

    public float swipeProgress() {
        return mSwipeProgress;
    }

    public Action up() {
        boolean wasPressed = mPressed;
        mPressed = false;
        boolean wasRepeating = mRepeating;
        mRepeating = false;
        if (!wasPressed || mPaging || mHandedToHost || wasRepeating || mHeldModifier) {
            return Action.NONE;
        }
        if (mSwiping) return mSwipeProgress >= 1f ? Action.FIRE_SECOND : Action.FIRE_PRIMARY;
        switch (mKind) {
            case MODIFIER:
                return Action.MODIFIER_TAP;
            case PLAIN:
            case REPEAT:
                return Action.FIRE_PRIMARY;
            default:
                return Action.NONE;
        }
    }

    public Action cancel() {
        mPressed = false;
        mRepeating = false;
        mSwiping = false;
        mPaging = false;
        mSwipeProgress = 0f;
        return Action.NONE;
    }

    public boolean isRepeating() {
        return mRepeating;
    }

    public boolean isPaging() {
        return mPaging;
    }

    public boolean isSwiping() {
        return mSwiping;
    }
}
