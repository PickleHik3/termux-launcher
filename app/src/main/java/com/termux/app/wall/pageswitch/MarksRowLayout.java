package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

import java.util.List;

/**
 * The marks row at rest, pure: the cells packed from the line's start, never stretched to fill it.
 * A Home dot's cell is narrow, every other mark's cell wider, kinds are set apart by a gap and the
 * current page's cell is as wide as any other of its kind. Runs fold ({@link PageBarFold}) only
 * when the packed content is longer than the line. A leading Overview cell stands before the
 * entries when asked for.
 *
 * <p>Sizes are shares of the mark's box ({@link #markSizePx()}), so the row scales with the mark.
 * Positions run along the line from its start; the view maps x, y and right-to-left. The view builds
 * a new one only when the entries, attention, mark size or length change, never per draw. While a
 * finger is down the row is {@link MarksTouchLayout}'s.
 */
public final class MarksRowLayout {

    /** The entry index {@link #tapEntryIndex} answers for the Overview cell. */
    public static final int OVERVIEW_ENTRY = -2;

    /** An unfolded Home dot's cell, as a share of the mark's box. */
    public static final float HOME_CELL_SHARE = 0.75f;
    /** Every other cell, as a share of the mark's box. */
    public static final float CELL_SHARE = 1.375f;
    /** The gap between neighbouring cells of different kinds, as a share of the mark's box. */
    public static final float KIND_GAP_SHARE = 0.4375f;

    /** The kind code of the Overview cell: no {@link PageKind} ordinal is negative. */
    static final int KIND_OVERVIEW = -1;

    @NonNull private final List<PageBarModel.Entry> mEntries;
    @Nullable private final boolean[] mAttention;
    private final boolean mOverview;
    private final float mMark;
    private final float mLength;
    @NonNull private final List<PageBarFold.Slot> mSlots;
    private final boolean mOverflow;
    private final int mCount;
    private final float[] mStart;
    private final float[] mEnd;

    /**
     * @param attention per entry: it wants the user (null for none). Read at construction only.
     * @param overview a leading Overview cell stands before the entries
     * @param markPx the mark's box
     * @param lengthPx the line's length along its axis
     */
    public MarksRowLayout(@NonNull List<PageBarModel.Entry> entries, @Nullable boolean[] attention,
                          boolean overview, float markPx, float lengthPx) {
        mEntries = entries;
        mAttention = attention == null ? null : attention.clone();
        mOverview = overview;
        mMark = markPx;
        mLength = lengthPx;

        int n = entries.size();
        PageBarFold.Result chosen = PageBarFold.fold(entries, n);
        boolean fits = packedLength(chosen.slots) <= lengthPx;
        for (int k = n - 1; !fits && k >= 1; k--) {
            chosen = PageBarFold.fold(entries, k);
            fits = packedLength(chosen.slots) <= lengthPx;
        }
        mSlots = chosen.slots;
        mOverflow = !fits;
        mCount = mSlots.size() + (overview ? 1 : 0);
        mStart = new float[mCount];
        mEnd = new float[mCount];
        float at = 0f;
        int previousKind = 0;
        for (int s = 0; s < mCount; s++) {
            int kind = kindOf(s);
            if (s > 0 && kind != previousKind) at += markPx * KIND_GAP_SHARE;
            mStart[s] = at;
            at += cellWidth(s);
            mEnd[s] = at;
            previousKind = kind;
        }
    }

    private float packedLength(List<PageBarFold.Slot> slots) {
        float at = 0f;
        int previousKind = 0;
        boolean first = true;
        if (mOverview) {
            at += mMark * CELL_SHARE;
            previousKind = KIND_OVERVIEW;
            first = false;
        }
        for (int i = 0; i < slots.size(); i++) {
            PageBarFold.Slot s = slots.get(i);
            int kind = s.kind.ordinal();
            if (!first && kind != previousKind) at += mMark * KIND_GAP_SHARE;
            at += cellOf(s);
            previousKind = kind;
            first = false;
        }
        return at;
    }

    private float cellOf(PageBarFold.Slot s) {
        return mMark * (s.kind == PageKind.HOME && !s.folded() ? HOME_CELL_SHARE : CELL_SHARE);
    }

    private float cellWidth(int slot) {
        return isOverview(slot) ? mMark * CELL_SHARE : cellOf(mSlots.get(slot - (mOverview ? 1 : 0)));
    }

    private int kindOf(int slot) {
        return isOverview(slot) ? KIND_OVERVIEW : mSlots.get(slot - (mOverview ? 1 : 0)).kind.ordinal();
    }

    public int slotCount() {
        return mCount;
    }

    /** Whether slot {@code slot} is the leading Overview cell. */
    public boolean isOverview(int slot) {
        return mOverview && slot == 0;
    }

    /** The folded slot, or null for the Overview cell. */
    @Nullable
    public PageBarFold.Slot slot(int slot) {
        return isOverview(slot) ? null : mSlots.get(slot - (mOverview ? 1 : 0));
    }

    /** Every run is folded and the packed row is still longer than the line: the view clips it. */
    public boolean overflow() {
        return mOverflow;
    }

    public float cellStart(int slot) {
        return mStart[slot];
    }

    public float cellEnd(int slot) {
        return mEnd[slot];
    }

    /** Where slot {@code slot}'s mark is centred along the line. */
    public float centreAt(int slot) {
        return (mStart[slot] + mEnd[slot]) * 0.5f;
    }

    /** The length the cells and gaps take from the line's start. */
    public float contentLengthPx() {
        return mCount == 0 ? 0f : mEnd[mCount - 1];
    }

    public float markSizePx() {
        return mMark;
    }

    public float lengthPx() {
        return mLength;
    }

    public boolean hasOverview() {
        return mOverview;
    }

    @NonNull
    public List<PageBarModel.Entry> entries() {
        return mEntries;
    }

    /** Whether any entry of slot {@code slot} wants the user; never for Overview. */
    public boolean attention(int slot) {
        return MarksTouchLayout.attentionOf(mAttention, slot(slot));
    }

    /** The cell holding {@code pos}, the nearer one in a gap, or -1 outside the content. */
    public int slotAt(float pos) {
        if (mCount == 0 || pos < 0f || pos >= contentLengthPx()) return -1;
        return MarksTouchLayout.nearest(mStart, mEnd, mCount, pos);
    }

    /** The slot nearest {@code pos}: {@link #slotAt} with the ends held. */
    public int clampedSlotAt(float pos) {
        return mCount == 0 ? -1 : MarksTouchLayout.nearest(mStart, mEnd, mCount, pos);
    }

    /**
     * The entry a tap on slot {@code slot} goes to: a folded slot's current member, else the one
     * flagged {@code returnTo}, else its first; {@link #OVERVIEW_ENTRY} for Overview.
     */
    public int tapEntryIndex(int slot) {
        PageBarFold.Slot s = slot(slot);
        if (s == null) return OVERVIEW_ENTRY;
        if (!s.folded()) return s.first;
        if (s.currentMember >= 0) return s.first + s.currentMember;
        for (int m = 0; m < s.count; m++) {
            if (mEntries.get(s.first + m).returnTo) return s.first + m;
        }
        return s.first;
    }

    /** The attention flags as given (never mutated), for the touch layout built over this row. */
    @Nullable boolean[] attentionArray() {
        return mAttention;
    }
}
