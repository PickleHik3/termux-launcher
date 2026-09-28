package com.termux.app.terminal.inappkeyboard;

/**
 * The hint that the keyboard can be swiped away, played when its background is tapped: a
 * grabber pill fades in at the keyboard's top edge while the keys dip a few dp and spring back,
 * then the pill fades out. Pure time, so the shape can be tested without a view;
 * {@link KeyboardHideSwipeGesture} drives it from one animator and draws what it answers.
 *
 * <p>Under reduced motion the keys never move and the pill simply stands for the hint's length.
 */
public final class KeyboardHideHint {

    /** How far the keys dip. */
    public static final float NUDGE_DP = 6f;

    /** The pill's fade in, its stand, and its fade out; the three make the hint's length. */
    static final long PILL_IN_MS = 120L;
    static final long PILL_HOLD_MS = 560L;
    static final long PILL_OUT_MS = 240L;
    /** The dip: down quickly, back with a soft landing, all inside the pill's stand. */
    static final long NUDGE_DOWN_MS = 140L;
    static final long NUDGE_BACK_MS = 320L;

    private final float mNudgePx;
    private final boolean mReducedMotion;

    /**
     * @param nudgePx how far the keys dip at the deepest point
     * @param reducedMotion true when the phone plays no animations: the keys stay put
     */
    public KeyboardHideHint(float nudgePx, boolean reducedMotion) {
        mNudgePx = Math.max(0f, nudgePx);
        mReducedMotion = reducedMotion;
    }

    /** The hint's whole length: after this, nothing of it is on screen. */
    public static long durationMs() {
        return PILL_IN_MS + PILL_HOLD_MS + PILL_OUT_MS;
    }

    /**
     * Whether the hint draws its pill for this keyboard form. The floating card already carries a
     * grab handle along its top; a second pill under it would read as one more thing to drag, so
     * the floating keyboard gets the dip alone.
     */
    public static boolean showsPill(boolean floating) {
        return !floating;
    }

    /** The pill's opacity, 0..1, at {@code elapsedMs}: a linear fade in, a stand, a linear fade out. */
    public float pillAlpha(long elapsedMs) {
        if (elapsedMs < 0L || elapsedMs >= durationMs())
            return 0f;
        if (mReducedMotion)
            return 1f;
        if (elapsedMs < PILL_IN_MS)
            return elapsedMs / (float) PILL_IN_MS;
        long outStart = PILL_IN_MS + PILL_HOLD_MS;
        if (elapsedMs < outStart)
            return 1f;
        return 1f - (elapsedMs - outStart) / (float) PILL_OUT_MS;
    }

    /**
     * How far below their rest the keys sit at {@code elapsedMs}: an eased dip to the nudge over
     * {@link #NUDGE_DOWN_MS}, then a decelerating return over {@link #NUDGE_BACK_MS}; zero before,
     * after, and always under reduced motion.
     */
    public float nudgePx(long elapsedMs) {
        if (mReducedMotion || mNudgePx <= 0f || elapsedMs < 0L)
            return 0f;
        if (elapsedMs < NUDGE_DOWN_MS) {
            float t = elapsedMs / (float) NUDGE_DOWN_MS;
            float inv = 1f - t;
            return mNudgePx * (1f - inv * inv);
        }
        long back = elapsedMs - NUDGE_DOWN_MS;
        if (back >= NUDGE_BACK_MS)
            return 0f;
        float inv = 1f - back / (float) NUDGE_BACK_MS;
        return mNudgePx * inv * inv * inv;
    }
}
