package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The marks row while a finger is down, pure and made once at touch-down, held to the lift: every
 * entry has a slot of its own (folds open), all cells share one pitch, the kind gaps stay, and the
 * line is anchored so the touched mark stays under the finger. When even the narrowest pitch does
 * not hold every slot, runs fold again ({@link PageBarFold}) to the most slots that do, and a
 * folded slot's members split its cell equally.
 *
 * <p>Positions run along the line from its start, as {@link MarksRowLayout}'s do. The view draws
 * each mark at {@link #scaleAt}.
 */
public final class MarksTouchLayout {

    /** The narrowest a cell may be before runs fold, in dp. */
    public static final float MIN_TOUCH_PITCH_DP = 20f;
    /** The widest a cell grows, in dp. */
    public static final float MAX_TOUCH_PITCH_DP = 32f;

    /** The zoom of the mark under the finger. */
    public static final float ZOOM_AT_FINGER = 1.5f;
    /** The zoom one pitch from the finger. */
    public static final float ZOOM_AT_ONE = 1.18f;
    /** From this many pitches from the finger a mark is not zoomed. */
    public static final float ZOOM_REACH = 2f;

    /** Stands for "no entry" while looking for the anchor. */
    private static final int NO_ENTRY = MarksRowLayout.OVERVIEW_ENTRY - 1;

    @NonNull private final List<PageBarModel.Entry> mEntries;
    @Nullable private final boolean[] mAttention;
    private final boolean mOverview;
    @NonNull private final List<PageBarFold.Slot> mSlots;
    private final int mCount;
    private final float mPitch;
    private final float[] mStart;
    private final float[] mEnd;
    private final int mAnchorSlot;
    private final int mAnchorMember;
    private final float mContentStart;
    private final float mContentLength;

    /**
     * @param rest the layout at rest: its entries, attention, Overview, mark size and length
     * @param density display density, for the pitch's dp bounds
     * @param fingerPos the finger's position along the line
     * @param touchedSlot the rest layout's slot the finger came down on
     */
    public MarksTouchLayout(@NonNull MarksRowLayout rest, float density, float fingerPos,
                            int touchedSlot) {
        this(rest, density, fingerPos, touchedSlot, 0f, rest.lengthPx());
    }

    /**
     * The touch layout over a line that runs from {@code lineStartPx} to {@code lineEndPx} in the
     * rest layout's coordinates: the whole status row, which may start before the marks and run on
     * past them. The cells share that length and stay inside it.
     */
    public MarksTouchLayout(@NonNull MarksRowLayout rest, float density, float fingerPos,
                            int touchedSlot, float lineStartPx, float lineEndPx) {
        mEntries = rest.entries();
        mAttention = rest.attentionArray();
        mOverview = rest.hasOverview();
        float length = Math.max(0f, lineEndPx - lineStartPx);
        float gap = rest.markSizePx() * MarksRowLayout.KIND_GAP_SHARE;
        float minPitch = MIN_TOUCH_PITCH_DP * density;
        float maxPitch = MAX_TOUCH_PITCH_DP * density;

        int n = mEntries.size();
        PageBarFold.Result chosen = PageBarFold.fold(mEntries, n);
        boolean fits = fits(chosen.slots, gap, minPitch, length);
        for (int k = n - 1; !fits && k >= 1; k--) {
            chosen = PageBarFold.fold(mEntries, k);
            fits = fits(chosen.slots, gap, minPitch, length);
        }
        mSlots = chosen.slots;
        mCount = mSlots.size() + (mOverview ? 1 : 0);
        float gaps = gapsOf(mSlots, mOverview) * gap;
        float even = mCount == 0 ? maxPitch : (length - gaps) / mCount;
        mPitch = Math.max(minPitch, Math.min(maxPitch, even));

        mStart = new float[mCount];
        mEnd = new float[mCount];
        float at = 0f;
        int previousKind = 0;
        for (int s = 0; s < mCount; s++) {
            int kind = kindOf(s);
            if (s > 0 && kind != previousKind) at += gap;
            mStart[s] = at;
            at += mPitch;
            mEnd[s] = at;
            previousKind = kind;
        }
        mContentLength = mCount == 0 ? 0f : mEnd[mCount - 1];

        int entry = touchedSlot < 0 || touchedSlot >= rest.slotCount()
                ? NO_ENTRY : rest.tapEntryIndex(touchedSlot);
        int anchor = -1;
        int member = 0;
        for (int s = 0; s < mCount && anchor < 0; s++) {
            PageBarFold.Slot slot = slot(s);
            if (slot == null) {
                if (entry == MarksRowLayout.OVERVIEW_ENTRY) anchor = s;
            } else if (entry >= slot.first && entry < slot.first + slot.count) {
                anchor = s;
                member = entry - slot.first;
            }
        }
        mAnchorSlot = anchor;
        mAnchorMember = member;

        float start = anchor < 0 ? lineStartPx : fingerPos - (mStart[anchor] + mEnd[anchor]) * 0.5f;
        if (mContentLength >= length) start = lineStartPx;
        else start = Math.max(lineStartPx, Math.min(lineEndPx - mContentLength, start));
        mContentStart = start;
    }

    /** Whether every slot of this fold, at the narrowest pitch with the kind gaps, fits the line. */
    private boolean fits(List<PageBarFold.Slot> slots, float gap, float minPitch, float length) {
        int count = slots.size() + (mOverview ? 1 : 0);
        return count * minPitch + gapsOf(slots, mOverview) * gap <= length;
    }

    /** How many kind gaps a row of these slots (and a leading Overview) has. */
    private static int gapsOf(List<PageBarFold.Slot> slots, boolean overview) {
        int gaps = 0;
        int previous = overview ? MarksRowLayout.KIND_OVERVIEW : -2;
        for (int i = 0; i < slots.size(); i++) {
            int kind = slots.get(i).kind.ordinal();
            if (previous != -2 && kind != previous) gaps++;
            previous = kind;
        }
        return gaps;
    }

    private int kindOf(int slot) {
        return isOverview(slot) ? MarksRowLayout.KIND_OVERVIEW
                : mSlots.get(slot - (mOverview ? 1 : 0)).kind.ordinal();
    }

    /** The cell nearest {@code pos} among cells {@code start[i]..end[i]}; a gap goes to the nearer one. */
    static int nearest(float[] start, float[] end, int count, float pos) {
        for (int s = 0; s < count - 1; s++) {
            if (pos < (end[s] + start[s + 1]) * 0.5f) return s;
        }
        return count - 1;
    }

    static boolean attentionOf(@Nullable boolean[] attention, @Nullable PageBarFold.Slot s) {
        if (attention == null || s == null) return false;
        for (int m = 0; m < s.count; m++) {
            int i = s.first + m;
            if (i < attention.length && attention[i]) return true;
        }
        return false;
    }

    public int slotCount() {
        return mCount;
    }

    public boolean isOverview(int slot) {
        return mOverview && slot == 0;
    }

    /** The slot, or null for the Overview cell. */
    @Nullable
    public PageBarFold.Slot slot(int slot) {
        return isOverview(slot) ? null : mSlots.get(slot - (mOverview ? 1 : 0));
    }

    /** The slot the touch is anchored on (holding the entry a tap there goes to); -1 when empty. */
    public int anchorSlot() {
        return mAnchorSlot;
    }

    /** The member of {@link #anchorSlot()} the touch is anchored on. */
    public int anchorMember() {
        return mAnchorMember;
    }

    /** Every cell's width along the line. */
    public float pitchPx() {
        return mPitch;
    }

    /** Where the first cell starts, after the anchoring shift. */
    public float contentStartPx() {
        return mContentStart;
    }

    /** The length the cells and gaps take. */
    public float contentLengthPx() {
        return mContentLength;
    }

    public float cellStart(int slot) {
        return mContentStart + mStart[slot];
    }

    public float cellEnd(int slot) {
        return mContentStart + mEnd[slot];
    }

    public float centreAt(int slot) {
        return mContentStart + (mStart[slot] + mEnd[slot]) * 0.5f;
    }

    /** The cell holding {@code pos}, the nearer one in a gap, or -1 outside the content. */
    public int slotAt(float pos) {
        if (mCount == 0 || pos < mContentStart || pos >= mContentStart + mContentLength) return -1;
        return clampedSlotAt(pos);
    }

    /** The slot nearest {@code pos}: {@link #slotAt} with the ends held. */
    public int clampedSlotAt(float pos) {
        return mCount == 0 ? -1 : nearest(mStart, mEnd, mCount, pos - mContentStart);
    }

    /** Which of a folded slot's members is under {@code pos}: its cell split equally. */
    public int memberAt(float pos, int slot) {
        PageBarFold.Slot s = slot(slot);
        if (s == null || !s.folded()) return 0;
        float within = (pos - cellStart(slot)) / mPitch;
        return Math.max(0, Math.min(s.count - 1, (int) Math.floor(within * s.count)));
    }

    /** The entry member {@code member} of slot {@code slot} stands for; Overview answers its constant. */
    public int entryIndex(int slot, int member) {
        PageBarFold.Slot s = slot(slot);
        return s == null ? MarksRowLayout.OVERVIEW_ENTRY : s.first + member;
    }

    /** Whether any entry of slot {@code slot} wants the user; never for Overview. */
    public boolean attention(int slot) {
        return attentionOf(mAttention, slot(slot));
    }

    /**
     * The zoom of slot {@code slot}'s mark with the finger at {@code fingerPos}: 1.5 under it, 1.18
     * one pitch away, 1 from two pitches, linear between.
     */
    public float scaleAt(int slot, float fingerPos) {
        float d = Math.abs(centreAt(slot) - fingerPos) / mPitch;
        if (d >= ZOOM_REACH) return 1f;
        if (d <= 1f) return ZOOM_AT_FINGER + (ZOOM_AT_ONE - ZOOM_AT_FINGER) * d;
        return ZOOM_AT_ONE + (1f - ZOOM_AT_ONE) * (d - 1f) / (ZOOM_REACH - 1f);
    }
}
