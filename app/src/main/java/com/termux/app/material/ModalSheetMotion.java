package com.termux.app.material;

/**
 * The numbers the app's two modal bottom sheets (the widget picker and Home's long-press sheet)
 * open and close by, from Launcher3's widget sheet: 267 ms, an emphasized decelerate in, an
 * emphasized accelerate out, 28 dp top corners. Both sheets read this one owner, and
 * {@link ModalSheetSlide} plays them. No Android types, so the numbers are pinned by a JVM test.
 */
public final class ModalSheetMotion {
    private ModalSheetMotion() { }

    /** How long a sheet takes to open or to close, from fully away to fully up. */
    public static final long OPEN_CLOSE_MS = 267L;

    /** The sheets' top corner radius: the M3 extra-large corner. */
    public static final float TOP_CORNER_DP = 28f;

    /** M3 emphasized decelerate (0.05, 0.7, 0.1, 1): the way in. */
    public static final float DECELERATE_X1 = 0.05f;
    public static final float DECELERATE_Y1 = 0.7f;
    public static final float DECELERATE_X2 = 0.1f;
    public static final float DECELERATE_Y2 = 1f;

    /** M3 emphasized accelerate (0.3, 0, 0.8, 0.15): the way out. */
    public static final float ACCELERATE_X1 = 0.3f;
    public static final float ACCELERATE_Y1 = 0f;
    public static final float ACCELERATE_X2 = 0.8f;
    public static final float ACCELERATE_Y2 = 0.15f;

    /**
     * The dim's alpha for a card pushed down by {@code translationY} of {@code travel} (the card
     * fully off the bottom): 1 at rest, 0 at full travel, clamped between. With no travel to speak
     * of there is nothing to fade, so the dim stays at 1.
     */
    public static float scrimAlpha(float translationY, float travel) {
        if (travel <= 0f) return 1f;
        float away = translationY / travel;
        if (away <= 0f) return 1f;
        if (away >= 1f) return 0f;
        return 1f - away;
    }

    /**
     * How long the rest of a slide takes when it starts part way, at the same speed as a whole one
     * (an open caught on its way, or a close taken over by an open). {@code fromFraction} is how
     * far the card is pushed down, 0 at rest and 1 fully away; opening has that much to go, closing
     * has the rest. Clamped, never negative.
     */
    public static long remainingDuration(float fromFraction, boolean opening) {
        float from = Math.max(0f, Math.min(1f, fromFraction));
        float remaining = opening ? from : 1f - from;
        return Math.round(OPEN_CLOSE_MS * remaining);
    }
}
