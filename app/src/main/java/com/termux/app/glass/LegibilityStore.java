package com.termux.app.glass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.window.Counters;

/**
 * The glass service's store of legibility corrections (ADR 0029 section 9): one per page and
 * band (dock, keyboard, strip, panes), measured at that page's rest position and kept. It is
 * measured again only when the wallpaper, the palette, light or dark, or the band's rest rectangle
 * changes ({@link #onWallpaperChanged}, {@link #onPaletteChanged}, {@link #onSchemeChanged},
 * {@link #needsMeasure}); the band's look (its opacity and tint) is the one input beyond the
 * ADR's, since a face is the glass over the wallpaper. Over a live scene the caller measures from
 * the scene's frame at rest and never chases it frame by frame.
 *
 * <p>Keying on the page covers parallax: the wall pans the wallpaper under the dock, so each page
 * keeps what it measured at its own rest. A crossing reads two stored values ({@link #peek}) and
 * blends them ({@link LegibilityBlend}); between {@link #beginCrossing} and {@link #endCrossing}
 * the store asks for no measurement ({@link #needsMeasure} is false), refuses one that arrives
 * ({@link #record} returns false, counted and logged as a guard), and counts any wallpaper sample
 * that is read ({@link #noteSample}). A page never yet visited is asked for once, at rest, before
 * its first crossing: {@link #needsMeasure} is true for it whenever nothing is moving.
 *
 * <p>Main thread only. Bounded: {@link #MAX_PAGES} pages by the four bands, the least recently
 * written page giving way; steady state allocates nothing, since an entry is rewritten in place
 * and a one-row correction is only rebuilt when its face changed.
 */
public final class LegibilityStore {

    /** Why every correction went stale. */
    public enum Reason { WALLPAPER, PALETTE, SCHEME }

    /** Counted and logged when a correction arrives during a crossing. */
    public static final String GUARD_MEASURED_IN_CROSSING = "legibility.measuredInCrossing";
    /** Counted and logged when the wallpaper is sampled during a crossing. */
    public static final String GUARD_SAMPLED_IN_CROSSING = "legibility.sampledInCrossing";

    /** Pages kept at once: a wall's Home pages and windows, and then some. */
    public static final int MAX_PAGES = 24;

    private static final int BANDS = LegibilityBand.ALL.length;

    /** One page and band's measurement and everything it was measured under. */
    private static final class Entry {
        @Nullable LegibilityCorrection correction;
        int epoch;
        int left, top, right, bottom;
        int look;
    }

    private final long[] mPages = new long[MAX_PAGES];
    private final Entry[][] mEntries = new Entry[MAX_PAGES][BANDS];
    /** When a page last wrote: the one that gives way is the least recent. */
    private final int[] mWritten = new int[MAX_PAGES];
    private int mPageCount;
    private int mClock;

    private int mEpoch;
    private boolean mDarkKnown;
    private boolean mDark;
    private boolean mCrossing;

    private int mMeasurements;
    private int mMeasuredInCrossing;
    private int mSampledInCrossing;
    private final int[] mInvalidations = new int[Reason.values().length];

    /**
     * The key of a page: its kind's ordinal and its page id (-1 where the launcher has not said
     * which). A value, so two Home pages are two keys; the glass package knows no page class.
     */
    public static long pageKey(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------------- triggers

    /** The wallpaper changed (or its frames were cleared): every correction is stale. */
    public void onWallpaperChanged() {
        stale(Reason.WALLPAPER);
    }

    /** The palette (the theme's colours, the dim, the legibility level) changed. */
    public void onPaletteChanged() {
        stale(Reason.PALETTE);
    }

    /** The scheme as reported now; light to dark or back makes every correction stale. */
    public void onSchemeChanged(boolean dark) {
        boolean changed = mDarkKnown && mDark != dark;
        mDarkKnown = true;
        mDark = dark;
        if (changed) stale(Reason.SCHEME);
    }

    private void stale(Reason reason) {
        mEpoch++;
        mInvalidations[reason.ordinal()]++;
    }

    // ---------------------------------------------------------------------- crossing

    /** A crossing began: nothing is measured until {@link #endCrossing}. */
    public void beginCrossing() {
        mCrossing = true;
    }

    public void endCrossing() {
        mCrossing = false;
    }

    public boolean crossing() {
        return mCrossing;
    }

    /**
     * The wallpaper was sampled for a legibility answer. In a crossing that is a measurement the
     * design forbids: it is counted, and left as a guard line while the motion log is on.
     */
    public void noteSample() {
        if (!mCrossing) return;
        mSampledInCrossing++;
        if (Counters.enabled) Counters.violation(GUARD_SAMPLED_IN_CROSSING, "sample");
    }

    // ---------------------------------------------------------------------- read

    /**
     * Whether {@code band} of {@code page} has to be measured now: nothing fresh is kept for the
     * page at this rest rectangle and look, and no crossing is running. True for a page never
     * visited, which is how it gets its measurement before its first crossing.
     */
    public boolean needsMeasure(long page, @NonNull LegibilityBand band,
                                int left, int top, int right, int bottom, int look) {
        if (mCrossing) return false;
        return get(page, band, left, top, right, bottom, look) == null;
    }

    /**
     * The correction kept for {@code band} of {@code page} if it was measured under the present
     * wallpaper, palette and scheme, at this rest rectangle and look; else null.
     */
    @Nullable
    public LegibilityCorrection get(long page, @NonNull LegibilityBand band,
                                    int left, int top, int right, int bottom, int look) {
        Entry entry = fresh(page, band);
        if (entry == null || entry.left != left || entry.top != top || entry.right != right
                || entry.bottom != bottom || entry.look != look) {
            return null;
        }
        return entry.correction;
    }

    /**
     * The correction last measured for {@code band} of {@code page} under the present wallpaper,
     * palette and scheme, whatever rectangle and look it was measured at: what a crossing blends,
     * which cannot ask the arriving page where it rests. Null when none is kept.
     */
    @Nullable
    public LegibilityCorrection peek(long page, @NonNull LegibilityBand band) {
        Entry entry = fresh(page, band);
        return entry == null ? null : entry.correction;
    }

    @Nullable
    private Entry fresh(long page, @NonNull LegibilityBand band) {
        int index = indexOf(page);
        if (index < 0) return null;
        Entry entry = mEntries[index][band.ordinal()];
        if (entry == null || entry.correction == null || entry.epoch != mEpoch) return null;
        return entry;
    }

    // ---------------------------------------------------------------------- write

    /**
     * Keeps a one-row correction (the dock's face) for {@code band} of {@code page}, measured at
     * rest under the given rectangle and look. Says whether it was kept: false in a crossing,
     * where it is refused, counted and logged.
     */
    public boolean recordFace(long page, @NonNull LegibilityBand band,
                              int left, int top, int right, int bottom, int look, int face) {
        Entry held = fresh(page, band);
        if (held != null && held.correction != null && held.correction.isFace(face)) {
            return record(page, band, left, top, right, bottom, look, held.correction);
        }
        return record(page, band, left, top, right, bottom, look, LegibilityCorrection.ofFace(face));
    }

    /** {@link #recordFace} for a correction of several rows and a tone (the keyboard's). */
    public boolean record(long page, @NonNull LegibilityBand band,
                          int left, int top, int right, int bottom, int look,
                          @NonNull LegibilityCorrection correction) {
        if (mCrossing) {
            mMeasuredInCrossing++;
            if (Counters.enabled) Counters.violation(GUARD_MEASURED_IN_CROSSING, band.name());
            return false;
        }
        int index = indexOf(page);
        if (index < 0) index = claim(page);
        mWritten[index] = ++mClock;
        Entry entry = mEntries[index][band.ordinal()];
        if (entry == null) {
            entry = new Entry();
            mEntries[index][band.ordinal()] = entry;
        }
        boolean same = entry.correction != null && entry.epoch == mEpoch
            && entry.left == left && entry.top == top && entry.right == right
            && entry.bottom == bottom && entry.look == look && entry.correction.equals(correction);
        if (!same) mMeasurements++;
        entry.correction = same ? entry.correction : correction;
        entry.epoch = mEpoch;
        entry.left = left;
        entry.top = top;
        entry.right = right;
        entry.bottom = bottom;
        entry.look = look;
        return true;
    }

    private int indexOf(long page) {
        for (int i = 0; i < mPageCount; i++) {
            if (mPages[i] == page) return i;
        }
        return -1;
    }

    /** A row for {@code page}: the next free one, else the least recently written page's. */
    private int claim(long page) {
        int index;
        if (mPageCount < MAX_PAGES) {
            index = mPageCount++;
        } else {
            index = 0;
            for (int i = 1; i < MAX_PAGES; i++) {
                if (mWritten[i] < mWritten[index]) index = i;
            }
        }
        mPages[index] = page;
        for (int band = 0; band < BANDS; band++) {
            Entry entry = mEntries[index][band];
            if (entry != null) entry.correction = null;
        }
        return index;
    }

    // ---------------------------------------------------------------------- counters

    /** Corrections kept that were not the one already held: what a measurement cost the store. */
    public int measurements() {
        return mMeasurements;
    }

    public int measuredInCrossing() {
        return mMeasuredInCrossing;
    }

    public int sampledInCrossing() {
        return mSampledInCrossing;
    }

    public int invalidations(@NonNull Reason reason) {
        return mInvalidations[reason.ordinal()];
    }
}
