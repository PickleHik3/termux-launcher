package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Pure geometry for the A-Z rope column: how wide the strip is, where the letters sit in it, how big
 * a glyph may be, and the two functions of transition progress that drive the rope.
 *
 * <p>Density arrives as a number and every dp constant is multiplied by it here, so the class never
 * sees a {@code Context} and its test runs under bare JUnit. Instances are the results of
 * {@link #resolve}; the column re-resolves its own one in place for a new visible height.
 *
 * <p><b>The clamps are the point.</b> The same 27 letters have to fit a ~1500px track on a 1080p
 * phone and a ~2100px one at 1440p, where an unclamped slot would put a 24dp letter on screen, and
 * still be legible on a short track — a landscape plane, or a split-screen one — where the honest
 * answer is a slot smaller than the glyph would like. The slots always share the whole track, so
 * the letters run from the grid's top to the card's bottom at every card height; the glyph is
 * clamped from both sides and then held inside its slot, which keeps adjacent letters from
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

    // Written only by set(): by resolve() for a new instance, and by resizeTo() for the column's
    // own one while the plane's bottom moves over it, so that path allocates nothing per frame.
    /** Letters actually drawn, always in {@code 1..}{@link AppDrawerRopeModel#MAX_LETTERS}. */
    public int letterCount;
    /** Width of the strip, which is also the right margin the grid must carry. */
    public float columnWidthPx;
    /** Top of the first slot. */
    public float trackTopPx;
    /** {@link #slotHeightPx} times {@link #letterCount}: the whole space given, less the insets. */
    public float trackHeightPx;
    public float slotHeightPx;
    /** Text size for a glyph, never larger than its slot. */
    public float glyphTextSizePx;
    /** {@link #ENTRY_OFFSET_DP} in pixels: the anchor's position at {@code p = 0}. */
    public float entryOffsetPx;
    /** The height these metrics were resolved for. */
    public float availableHeightPx;
    private float mDensity;

    private AppDrawerRopeMetrics() {}

    /** The strip width alone, for the controller sizing the grid before any letters are known. */
    public static float resolveColumnWidthPx(float density) {
        return COLUMN_WIDTH_DP * (density > 0f ? density : 1f);
    }

    /**
     * The height the track spans: the column's laid-out height, or less while the plane's visible
     * bottom stands above the column's bottom (the card's bottom following the keyboard's reveal),
     * so the letters are spaced over the part of the strip the card shows.
     *
     * @param columnHeightPx  the column view's laid-out height
     * @param columnTopPx     the column's top, in the coordinates {@code visibleBottomPx} is in
     * @param visibleBottomPx the plane's visible bottom; {@code +∞} when it does not cut the strip
     */
    public static float visibleTrackHeightPx(float columnHeightPx, float columnTopPx,
                                             float visibleBottomPx) {
        return Math.max(0f, Math.min(columnHeightPx, visibleBottomPx - columnTopPx));
    }

    /**
     * @param availableHeightPx the column view's height
     * @param letterCount       visible letters, clamped into range
     * @param density           {@code DisplayMetrics.density}
     */
    @NonNull
    public static AppDrawerRopeMetrics resolve(float availableHeightPx, int letterCount,
                                               float density) {
        AppDrawerRopeMetrics metrics = new AppDrawerRopeMetrics();
        metrics.set(availableHeightPx, letterCount, density);
        return metrics;
    }

    /** These metrics again for another height, same letters and density; nothing is allocated. */
    void resizeTo(float availableHeightPx) {
        if (availableHeightPx == this.availableHeightPx) return;
        set(availableHeightPx, letterCount, mDensity);
    }

    private void set(float availableHeightPx, int letterCount, float density) {
        // A degenerate density would take every derived number to NaN or zero; the drawer is rebuilt
        // on configuration change, so a bad frame must degrade rather than poison the column.
        float d = density > 0f ? density : 1f;
        int count = Math.max(1, Math.min(AppDrawerRopeModel.MAX_LETTERS, letterCount));
        float height = Math.max(0f, availableHeightPx);
        // On a very short track the fixed inset would eat the whole thing, so it gives way first.
        float insetPx = Math.min(TRACK_INSET_DP * d, height * 0.25f);
        float usablePx = Math.max(0f, height - (insetPx * 2f));
        // Evenly over the whole track, A at its top and the last letter at its bottom.
        float slotHeightPx = usablePx / count;
        float trackHeightPx = usablePx;
        float trackTopPx = insetPx;
        float glyphTextSizePx = Math.min(MAX_GLYPH_DP * d,
            Math.max(MIN_GLYPH_DP * d, slotHeightPx * GLYPH_SLOT_FRACTION));
        // The floor must never win by enough to overlap the neighbouring letter.
        glyphTextSizePx = Math.min(glyphTextSizePx, slotHeightPx);
        this.letterCount = count;
        this.columnWidthPx = COLUMN_WIDTH_DP * d;
        this.trackTopPx = trackTopPx;
        this.trackHeightPx = trackHeightPx;
        this.slotHeightPx = slotHeightPx;
        this.glyphTextSizePx = glyphTextSizePx;
        this.entryOffsetPx = ENTRY_OFFSET_DP * d;
        this.availableHeightPx = availableHeightPx;
        this.mDensity = density;
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
