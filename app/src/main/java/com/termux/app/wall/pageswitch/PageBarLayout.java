package com.termux.app.wall.pageswitch;

import com.termux.app.ScrubWave;

/**
 * The page bar's geometry in px, for one pane width, density and slot count.
 *
 * <p>Two shapes. The <b>pill</b> is the touch row: {@link #PILL_HEIGHT_DP} tall, its slots
 * ({@link #MIN_SLOT_DP} on Home) end to end, centred on the window (in the pane when the pane is
 * centred); taps, slides and screen-reader buttons read it. The <b>slab</b> is what is drawn: the
 * glyphs with {@link #GAP_DP} above and below them, its bottom {@link #GAP_DP} above the touch row's,
 * so it hugs whatever stands below the bar. It is as wide as the touch row, so the end glyphs stand
 * half a slot from its ends: the same rhythm end to end as between glyphs.</p>
 *
 * <p>While a finger slides, each glyph lifts out of the slab on the A–Z bar's wave
 * ({@link ScrubWave}): the glyph under the finger rises to {@link #liftPeakPx} and grows to
 * {@link #RAISED_SCALE}, its neighbours by the wave's envelope, all scaled by the enter share so
 * nothing jumps as the finger crosses slots. Every number is declared once, in dp.</p>
 */
public final class PageBarLayout {

    /** The touch row's height: the band's height while dock rows stand below the bar. */
    public static final float PILL_HEIGHT_DP = 48f;
    public static final float GLYPH_DP = 16.5f;
    /** A slot's width on Home, and the least the fold counts with. */
    public static final float MIN_SLOT_DP = 40f;
    /** The pill is at most this far from the pane's edges. */
    public static final float SIDE_INSET_DP = 16f;
    /**
     * The bar's one small gap: the slab's padding around its glyphs on all four sides, the air
     * under the slab and the gap between a lifted glyph and the page's name above it.
     */
    public static final float GAP_DP = 4f;
    /** The glyph under the finger, lifted, against its rest size. */
    public static final float RAISED_SCALE = 1.5f;
    /** The finger leaving the touch row vertically by more than this cancels the slide. */
    public static final float CANCEL_DP = 32f;

    /** The slab's height: one glyph and the gap above and below it. */
    public static float slabHeightDp() {
        return GLYPH_DP + 2f * GAP_DP;
    }

    /** The key's pop-up band, the touch row alone; Home's band is {@link PageBarBand}'s. */
    public static float bandHeightDp() {
        return PILL_HEIGHT_DP;
    }

    /** How many slots fit a pane of this width before runs must fold; at least 1. */
    public static int maxSlots(float density, float paneWidthPx) {
        float area = paneWidthPx - 2f * SIDE_INSET_DP * density;
        return Math.max(1, (int) Math.floor(area / (MIN_SLOT_DP * density)));
    }

    private final float mDensity;
    private final float mPaneWidth;
    private final int mSlotCount;
    private final float mSlotWidth;
    private final float mPillLeft;

    /** Home's bar: the pill hugs its glyphs, each slot {@link #MIN_SLOT_DP}. */
    public PageBarLayout(float density, float paneWidthPx, int slotCount) {
        this(density, paneWidthPx, slotCount, false);
    }

    /**
     * @param fill the pill spans the pane less {@link #SIDE_INSET_DP} on each side, its slots equal
     *             and uncapped: the bar the page switcher key pops up on a workspace page
     */
    public PageBarLayout(float density, float paneWidthPx, int slotCount, boolean fill) {
        this(density, paneWidthPx, slotCount, fill, 0f, paneWidthPx);
    }

    /**
     * Home's bar in a pane that may stand off the window's centre: the hugged pill is centred on
     * the window ({@link PageBarBand#pillLeftPx}), clamped inside the pane. With the pane centred
     * in the window it is the pane-centred bar.
     *
     * @param paneLeftPx    the pane's left in the window
     * @param windowWidthPx the window's width; 0 or less centres the pill in the pane
     */
    public PageBarLayout(float density, float paneWidthPx, int slotCount, float paneLeftPx,
                         float windowWidthPx) {
        this(density, paneWidthPx, slotCount, false, paneLeftPx, windowWidthPx);
    }

    private PageBarLayout(float density, float paneWidthPx, int slotCount, boolean fill,
                          float paneLeftPx, float windowWidthPx) {
        mDensity = density;
        mPaneWidth = paneWidthPx;
        mSlotCount = Math.max(1, slotCount);
        float area = Math.max(0f, paneWidthPx - 2f * SIDE_INSET_DP * density);
        mSlotWidth = fill ? area / mSlotCount : Math.min(MIN_SLOT_DP * density, area / mSlotCount);
        float pillWidth = mSlotWidth * mSlotCount;
        mPillLeft = fill ? (paneWidthPx - pillWidth) / 2f
            : PageBarBand.pillLeftPx(paneLeftPx, paneWidthPx, windowWidthPx, pillWidth, density);
    }

    public int slotCount() {
        return mSlotCount;
    }

    public float slotWidthPx() {
        return mSlotWidth;
    }

    public float paneWidthPx() {
        return mPaneWidth;
    }

    // ---- The touch row ----------------------------------------------------------------------

    public float pillLeftPx() {
        return mPillLeft;
    }

    public float pillWidthPx() {
        return mSlotWidth * mSlotCount;
    }

    public float pillHeightPx() {
        return PILL_HEIGHT_DP * mDensity;
    }

    public float bandHeightPx() {
        return bandHeightDp() * mDensity;
    }

    /** The touch row's top within the band: the band's top. */
    public float pillTopPx() {
        return 0f;
    }

    public float pillBottomPx() {
        return pillTopPx() + pillHeightPx();
    }

    public float cancelPx() {
        return CANCEL_DP * mDensity;
    }

    // ---- The slab ---------------------------------------------------------------------------

    /** The slab's left: the touch row's, so the end glyph stands half a slot inside it. */
    public float slabLeftPx() {
        return mPillLeft;
    }

    public float slabWidthPx() {
        return pillWidthPx();
    }

    public float slabHeightPx() {
        return slabHeightDp() * mDensity;
    }

    public float slabTopPx() {
        return slabBottomPx() - slabHeightPx();
    }

    public float slabBottomPx() {
        return pillBottomPx() - GAP_DP * mDensity;
    }

    /** Where every glyph's centre stands at rest. */
    public float slabCenterYPx() {
        return slabBottomPx() - slabHeightPx() / 2f;
    }

    public float glyphPx() {
        return GLYPH_DP * mDensity;
    }

    public float raisedGlyphPx() {
        return glyphPx() * RAISED_SCALE;
    }

    // ---- Slots ------------------------------------------------------------------------------

    public float restCenterX(int slot) {
        return mPillLeft + mSlotWidth * (slot + 0.5f);
    }

    /** The slot under {@code x}, from the rest slots only; left of the pill is 0, right of it the last. */
    public int slotAt(float x) {
        int slot = (int) Math.floor((x - mPillLeft) / mSlotWidth);
        return Math.max(0, Math.min(mSlotCount - 1, slot));
    }

    /** Which of a folded slot's {@code members} is under {@code x}: its width split equally. */
    public int subIndexAt(float x, int slot, int members) {
        if (members <= 1) return 0;
        float within = (x - (mPillLeft + mSlotWidth * slot)) / mSlotWidth;
        return Math.max(0, Math.min(members - 1, (int) Math.floor(within * members)));
    }

    /** The finger as a fractional slot, 0..slotCount-1, for the lift. */
    public float focusAt(float x) {
        float f = (x - mPillLeft) / mSlotWidth - 0.5f;
        return Math.max(0f, Math.min(mSlotCount - 1, f));
    }

    // ---- The lift ---------------------------------------------------------------------------

    /**
     * How far the lifted glyph's bottom stands above the slab's top: the A–Z bar's lift for the
     * letter under the finger, so the glyph clears the fingertip as the letter does.
     */
    public float liftClearPx() {
        return ScrubWave.LIFT_DP * ScrubWave.FOCUS_LIFT_GAIN * mDensity;
    }

    /** The glyph under the finger, fully lifted: how far its centre rises from its rest centre. */
    public float liftPeakPx() {
        return slabHeightPx() / 2f + liftClearPx() + raisedGlyphPx() / 2f;
    }

    /** The A–Z wave's envelope for {@code slot} with the finger at {@code focus}, times {@code enter}. */
    public static float envelope(int slot, float focus, float enter) {
        if (enter <= 0f) return 0f;
        return ScrubWave.envelope(Math.abs(slot - focus)) * Math.min(1f, enter);
    }

    /** How far {@code slot}'s glyph rises above its rest centre; 0 at rest. */
    public float liftPx(int slot, float focus, float enter) {
        if (enter <= 0f) return 0f;
        return ScrubWave.lift(ScrubWave.envelope(Math.abs(slot - focus))) * liftPeakPx()
            * Math.min(1f, enter);
    }

    /** {@code slot}'s glyph scale against its rest size; 1 at rest. */
    public float scaleAt(int slot, float focus, float enter) {
        return 1f + (RAISED_SCALE - 1f) * envelope(slot, focus, enter);
    }

    /** {@code slot}'s glyph centre y while lifted. */
    public float liftedCenterYPx(int slot, float focus, float enter) {
        return slabCenterYPx() - liftPx(slot, focus, enter);
    }

    /**
     * How much of a lifted glyph has left the slab, 0 (inside it) to 1 (wholly above its top):
     * the share of the wallpaper's ink it takes over the slab's.
     */
    public float outOfSlab(int slot, float focus, float enter) {
        float reach = (slabHeightPx() + glyphPx() * scaleAt(slot, focus, enter)) / 2f;
        return Math.max(0f, Math.min(1f, liftPx(slot, focus, enter) / reach));
    }

    /**
     * The page's name's baseline over {@code slot}'s lifted glyph: {@link #GAP_DP} between the
     * glyph's top and the text's descent.
     */
    public float labelBaselinePx(int slot, float focus, float enter, float descentPx) {
        float half = glyphPx() * scaleAt(slot, focus, enter) / 2f;
        return liftedCenterYPx(slot, focus, enter) - half - GAP_DP * mDensity - descentPx;
    }

    /** The baseline the name stands on at the top of its rise, for measuring its ink. */
    public float labelBaselinePeakPx(float descentPx) {
        return slabCenterYPx() - liftPeakPx() - raisedGlyphPx() / 2f - GAP_DP * mDensity
            - descentPx;
    }

    /** The name's left: centred over {@code slot}, kept {@link #SIDE_INSET_DP} inside the pane. */
    public float labelLeftPx(int slot, float textWidthPx) {
        float inset = SIDE_INSET_DP * mDensity;
        float left = restCenterX(slot) - textWidthPx / 2f;
        return Math.max(inset, Math.min(mPaneWidth - inset - textWidthPx, left));
    }

    /** The room the name has: the pane less the side insets. */
    public float labelRoomPx() {
        return Math.max(0f, mPaneWidth - 2f * SIDE_INSET_DP * mDensity);
    }
}
