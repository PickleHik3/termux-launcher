package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Pure geometry for the A-Z rope column: how wide the strip is, where the letters sit in it, how big
 * a glyph may be, and the two functions of transition progress that drive the rope.
 *
 * <p>Density arrives as a number and every dp constant is multiplied by it here, so the class never
 * sees a {@code Context} and its test runs under bare JUnit. Instances are immutable results of
 * {@link #resolve}.
 *
 * <p><b>The clamps are the point.</b> The same 27 letters have to fit a ~1500px track on a 1080p
 * phone and a ~2100px one at 1440p, where an unclamped slot would put a 24dp letter on screen, and
 * still be legible on a short track — a landscape plane, or a split-screen one — where the honest
 * answer is a slot smaller than the glyph would like. So the slot is capped from above and the glyph
 * is clamped from both sides and then held inside its slot, which keeps adjacent letters from
 * overlapping at the small end and keeps them from reading as a heading list at the large end.
 *
 * <p>{@link #indexForY} carries {@link #LETTER_SLOT_HYSTERESIS_RATIO}, the same value and the same
 * rule as the dock's {@code AzScrubRowView}: a finger parked on a boundary must not flicker between
 * two letters, because every letter change auto-scrolls the grid and ticks the haptic.
 */
public final class AppDrawerRopeMetrics {

    /** Width of the touchable strip. Narrower than a 48dp target on purpose — it is a scrubber, and
     * a stream that lands on it is a scrub for its whole life, so there is nothing to miss. */
    public static final float COLUMN_WIDTH_DP = 30f;
    /** Padding above and below the track, so the first and last letters are not against the edges. */
    public static final float TRACK_INSET_DP = 10f;
    /** Ceiling on a slot: 27 letters over a 1440p plane would otherwise get 24dp each. */
    public static final float MAX_SLOT_DP = 26f;
    /** Share of a slot a glyph may take before the letters read as one block. */
    public static final float GLYPH_SLOT_FRACTION = 0.62f;
    /** Glyph bounds. The floor is the smallest legible letter; the ceiling matches the dock's row. */
    public static final float MIN_GLYPH_DP = 8f;
    public static final float MAX_GLYPH_DP = 13f;

    /** Same value, same rule as the dock scrub row: a boundary needs overshooting to cross. */
    public static final float LETTER_SLOT_HYSTERESIS_RATIO = 0.22f;

    /** How far out the anchor starts. Bigger reads as a slap, smaller is invisible at the head. */
    public static final float ENTRY_OFFSET_DP = 26f;
    /** The column arrives after the content fade has begun — it is the last thing to appear… */
    public static final float COLUMN_IN_START = 0.34f;
    /** …and the anchor is home before {@code p = 1}, so the tail is still settling when the plane
     * finishes. */
    public static final float COLUMN_IN_END = 0.86f;
    /** Alpha is done well before the anchor is, so the settle happens in full view. */
    public static final float COLUMN_ALPHA_END = 0.60f;

    /** Letters actually drawn, always in {@code 1..}{@link AppDrawerRopeModel#MAX_LETTERS}. */
    public final int letterCount;
    /** Width of the strip, which is also the right margin the grid must carry. */
    public final float columnWidthPx;
    /** Top of the first slot. */
    public final float trackTopPx;
    /** {@link #slotHeightPx} times {@link #letterCount}; the track is centred in the space given. */
    public final float trackHeightPx;
    public final float slotHeightPx;
    /** Text size for a glyph, never larger than its slot. */
    public final float glyphTextSizePx;
    /** {@link #ENTRY_OFFSET_DP} in pixels: the anchor's position at {@code p = 0}. */
    public final float entryOffsetPx;

    private AppDrawerRopeMetrics(int letterCount, float columnWidthPx, float trackTopPx,
                                 float trackHeightPx, float slotHeightPx, float glyphTextSizePx,
                                 float entryOffsetPx) {
        this.letterCount = letterCount;
        this.columnWidthPx = columnWidthPx;
        this.trackTopPx = trackTopPx;
        this.trackHeightPx = trackHeightPx;
        this.slotHeightPx = slotHeightPx;
        this.glyphTextSizePx = glyphTextSizePx;
        this.entryOffsetPx = entryOffsetPx;
    }

    /** The strip width alone, for the controller sizing the grid before any letters are known. */
    public static float resolveColumnWidthPx(float density) {
        return COLUMN_WIDTH_DP * (density > 0f ? density : 1f);
    }

    /**
     * @param availableHeightPx the column view's height
     * @param letterCount       visible letters, clamped into range
     * @param density           {@code DisplayMetrics.density}
     */
    @NonNull
    public static AppDrawerRopeMetrics resolve(float availableHeightPx, int letterCount,
                                               float density) {
        // A degenerate density would take every derived number to NaN or zero; the drawer is rebuilt
        // on configuration change, so a bad frame must degrade rather than poison the column.
        float d = density > 0f ? density : 1f;
        int count = Math.max(1, Math.min(AppDrawerRopeModel.MAX_LETTERS, letterCount));
        float height = Math.max(0f, availableHeightPx);
        // On a very short track the fixed inset would eat the whole thing, so it gives way first.
        float insetPx = Math.min(TRACK_INSET_DP * d, height * 0.25f);
        float usablePx = Math.max(0f, height - (insetPx * 2f));
        float slotHeightPx = Math.min(MAX_SLOT_DP * d, usablePx / count);
        float trackHeightPx = slotHeightPx * count;
        float trackTopPx = insetPx + ((usablePx - trackHeightPx) * 0.5f);
        float glyphTextSizePx = Math.min(MAX_GLYPH_DP * d,
            Math.max(MIN_GLYPH_DP * d, slotHeightPx * GLYPH_SLOT_FRACTION));
        // The floor must never win by enough to overlap the neighbouring letter.
        glyphTextSizePx = Math.min(glyphTextSizePx, slotHeightPx);
        return new AppDrawerRopeMetrics(count, COLUMN_WIDTH_DP * d, trackTopPx, trackHeightPx,
            slotHeightPx, glyphTextSizePx, ENTRY_OFFSET_DP * d);
    }

    /** Centre of a letter's slot, in the column view's own coordinates. */
    public float centerYForIndex(int index) {
        int i = Math.max(0, Math.min(letterCount - 1, index));
        return trackTopPx + (slotHeightPx * (i + 0.5f));
    }

    /**
     * The letter under a finger.
     *
     * <p>Y is clamped rather than rejected at both ends: a scrub that runs off the top or the bottom
     * of the track stays on the first or last letter instead of dropping out, which is what makes
     * "drag to the end of the alphabet" work without hitting a pixel exactly.
     *
     * @param previousIndex the index this stream last reported, or -1 at the start of one, in which
     *                      case there is nothing to be sticky about and the raw slot wins
     */
    public int indexForY(float y, int previousIndex) {
        if (slotHeightPx <= 0f) return 0;
        int raw = (int) Math.floor((y - trackTopPx) / slotHeightPx);
        raw = Math.max(0, Math.min(letterCount - 1, raw));
        if (previousIndex < 0 || previousIndex >= letterCount) return raw;
        if (raw == previousIndex) return previousIndex;
        // A jump of more than one slot is a finger that moved, not a finger on a boundary.
        if (Math.abs(raw - previousIndex) > 1) return raw;
        float boundary = trackTopPx + (Math.max(raw, previousIndex) * slotHeightPx);
        float hysteresis = slotHeightPx * LETTER_SLOT_HYSTERESIS_RATIO;
        if (raw > previousIndex) {
            return y >= (boundary + hysteresis) ? raw : previousIndex;
        }
        return y <= (boundary - hysteresis) ? raw : previousIndex;
    }

    /**
     * The rope's driver: where {@code x[-1]} sits at this transition progress.
     *
     * <p>Because this is a pure function of {@code p}, the anchor's velocity is the finger's, which
     * is why a release never has to inject one into the chain.
     */
    public float anchorPx(float p) {
        return entryOffsetPx
            * (1f - AppDrawerTransitionGeometry.ramp(p, COLUMN_IN_START, COLUMN_IN_END));
    }

    /**
     * Column opacity at this progress. Zero below {@link #COLUMN_IN_START}, which is what makes the
     * last third of a close a rope nobody sees.
     */
    public static float alpha(float p) {
        return AppDrawerTransitionGeometry.ramp(p, COLUMN_IN_START, COLUMN_ALPHA_END);
    }

    @NonNull
    @Override
    public String toString() {
        return "AppDrawerRopeMetrics(letters=" + letterCount + ", width=" + columnWidthPx
            + ", trackTop=" + trackTopPx + ", trackHeight=" + trackHeightPx
            + ", slot=" + slotHeightPx + ", glyph=" + glyphTextSizePx + ")";
    }
}
