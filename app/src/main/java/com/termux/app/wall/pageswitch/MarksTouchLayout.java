package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The marks row while a finger is down, pure and made once at touch-down, held to the lift. The
 * touched mark's cell stays centred exactly where it is at rest, so nothing under the finger moves.
 * The marks after it spread out at the touch pitch, folds open, with the kind gaps kept; when even
 * the narrowest pitch does not hold them, the longest run after it folds again
 * ({@link PageBarFold}). The marks before it keep the room they already had, from the row's start
 * to the touched cell, divided into equal cells; a stack there opens only while its members fit the
 * narrowest rest cell, else it stays a stack whose members split its cell.
 *
 * <p>Positions run along the line in the rest layout's coordinates. The view draws each mark at
 * {@link #scaleAt}.
 */
public final class MarksTouchLayout {

    /** The narrowest a cell after the touched one may be before runs fold, in dp. */
    public static final float MIN_TOUCH_PITCH_DP = 20f;
    /** The widest a cell after the touched one grows, in dp. */
    public static final float MAX_TOUCH_PITCH_DP = 32f;

    /** The zoom of the mark under the finger. */
    public static final float ZOOM_AT_FINGER = 1.5f;
    /** The zoom one pitch from the finger. */
    public static final float ZOOM_AT_ONE = 1.18f;
    /** From this many pitches from the finger a mark is not zoomed. */
    public static final float ZOOM_REACH = 2f;

    /** Stands for "no neighbour" when counting kind gaps. */
    private static final int NO_KIND = -2;

    @NonNull private final List<PageBarModel.Entry> mEntries;
    @Nullable private final boolean[] mAttention;
    private final boolean mOverview;
    @NonNull private final List<PageBarFold.Slot> mSlots;
    private final int mCount;
    private final float mPitch;
    private final float[] mStart;
    private final float[] mEnd;
    private final int mAnchorSlot;

    /**
     * @param rest the layout at rest: its entries, attention, Overview, mark size and cells
     * @param density display density, for the pitch's dp bounds
     * @param touchedSlot the rest layout's slot the finger came down on
     * @param lineEndPx where the row ends, in the rest layout's coordinates
     */
    public MarksTouchLayout(@NonNull MarksRowLayout rest, float density, int touchedSlot,
                            float lineEndPx) {
        mEntries = rest.entries();
        mAttention = rest.attentionArray();
        mOverview = rest.hasOverview();
        int restCount = rest.slotCount();
        if (restCount == 0) {
            mSlots = new ArrayList<>();
            mCount = 0;
            mPitch = MAX_TOUCH_PITCH_DP * density;
            mStart = new float[0];
            mEnd = new float[0];
            mAnchorSlot = -1;
            return;
        }
        float mark = rest.markSizePx();
        float gap = mark * MarksRowLayout.KIND_GAP_SHARE;
        float minPitch = MIN_TOUCH_PITCH_DP * density;
        float maxPitch = MAX_TOUCH_PITCH_DP * density;
        int ts = Math.max(0, Math.min(restCount - 1, touchedSlot));
        int entry = rest.tapEntryIndex(ts);
        boolean overviewAnchor = entry == MarksRowLayout.OVERVIEW_ENTRY;
        int n = mEntries.size();
        int anchorEntry = overviewAnchor ? -1 : Math.max(0, Math.min(n - 1, entry));
        int anchorKind = overviewAnchor ? MarksRowLayout.KIND_OVERVIEW
            : mEntries.get(anchorEntry).kind.ordinal();
        float centre = rest.centreAt(ts);
        float firstStart = rest.cellStart(0);

        // After the anchor: the touch pitch, refolded until the narrowest pitch holds.
        List<PageBarModel.Entry> afterEntries = mEntries.subList(overviewAnchor ? 0 : anchorEntry + 1, n);
        int afterOffset = overviewAnchor ? 0 : anchorEntry + 1;
        float roomAfter = Math.max(0f, lineEndPx - centre);
        List<PageBarFold.Slot> after = fold(afterEntries, afterEntries.size(), afterOffset);
        for (int k = afterEntries.size() - 1; k >= 1
                && (after.size() + 0.5f) * minPitch
                + gapsIn(anchorKind, after, NO_KIND) * gap > roomAfter; k--) {
            after = fold(afterEntries, k, afterOffset);
        }
        float afterGaps = gapsIn(anchorKind, after, NO_KIND) * gap;
        mPitch = Math.max(minPitch, Math.min(maxPitch,
            (roomAfter - afterGaps) / (after.size() + 0.5f)));

        // Before the anchor: the room the marks had, in equal cells.
        boolean beforeOverview = mOverview && !overviewAnchor;
        List<PageBarModel.Entry> beforeEntries = overviewAnchor ? mEntries.subList(0, 0)
            : mEntries.subList(0, anchorEntry);
        int overviewKind = beforeOverview ? MarksRowLayout.KIND_OVERVIEW : NO_KIND;
        float roomBefore = Math.max(0f, rest.cellStart(ts) - firstStart);
        float narrowest = mark * MarksRowLayout.HOME_CELL_SHARE;
        List<PageBarFold.Slot> before = fold(beforeEntries, beforeEntries.size(), 0);
        for (int k = beforeEntries.size() - 1; k >= 1
                && cellBefore(roomBefore, before, beforeOverview, overviewKind, anchorKind, gap)
                < narrowest; k--) {
            before = fold(beforeEntries, k, 0);
        }
        int beforeCount = before.size() + (beforeOverview ? 1 : 0);
        float cell = beforeCount == 0 ? 0f
            : cellBefore(roomBefore, before, beforeOverview, overviewKind, anchorKind, gap);

        List<PageBarFold.Slot> slots = new ArrayList<>(before.size() + 1 + after.size());
        slots.addAll(before);
        if (!overviewAnchor) {
            slots.add(fold(mEntries.subList(anchorEntry, anchorEntry + 1), 1, anchorEntry).get(0));
        }
        slots.addAll(after);
        mSlots = Collections.unmodifiableList(slots);
        mCount = slots.size() + (mOverview ? 1 : 0);
        mStart = new float[mCount];
        mEnd = new float[mCount];
        int anchor = overviewAnchor ? 0 : beforeCount;
        mAnchorSlot = anchor;
        float at = firstStart;
        int previousKind = NO_KIND;
        for (int s = 0; s < mCount; s++) {
            int kind = kindOf(s);
            if (s < anchor) {
                if (previousKind != NO_KIND && kind != previousKind) at += gap;
                mStart[s] = at;
                at += cell;
            } else if (s == anchor) {
                mStart[s] = centre - mPitch / 2f;
                at = centre + mPitch / 2f;
            } else {
                if (kind != previousKind) at += gap;
                mStart[s] = at;
                at += mPitch;
            }
            mEnd[s] = at;
            previousKind = kind;
        }
    }

    /** The slots of {@code entries} folded to at most {@code max}, their entry indices shifted by {@code offset}. */
    private static List<PageBarFold.Slot> fold(List<PageBarModel.Entry> entries, int max, int offset) {
        List<PageBarFold.Slot> out = new ArrayList<>();
        if (entries.isEmpty()) return out;
        for (PageBarFold.Slot s : PageBarFold.fold(entries, max).slots) {
            out.add(new PageBarFold.Slot(s.first + offset, s.count, s.kind, s.number, s.current,
                s.currentMember));
        }
        return out;
    }

    /** The equal cell the marks before the anchor get in {@code room}, after their kind gaps. */
    private float cellBefore(float room, List<PageBarFold.Slot> before, boolean overview,
                             int overviewKind, int anchorKind, float gap) {
        int count = before.size() + (overview ? 1 : 0);
        if (count == 0) return 0f;
        return Math.max(0f, (room - gapsIn(overviewKind, before, anchorKind) * gap) / count);
    }

    /** The kind gaps of {@code slots} that follow a mark of kind {@code previous} and precede {@code next}. */
    private static int gapsIn(int previous, List<PageBarFold.Slot> slots, int next) {
        int gaps = 0;
        int prev = previous;
        for (int i = 0; i < slots.size(); i++) {
            int kind = slots.get(i).kind.ordinal();
            if (prev != NO_KIND && kind != prev) gaps++;
            prev = kind;
        }
        if (next != NO_KIND && prev != NO_KIND && next != prev) gaps++;
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

    /** The cell whose centre is nearest {@code pos}; touch cells may overlap, so gaps do not decide. */
    private static int nearestCentre(float[] start, float[] end, int count, float pos) {
        for (int s = 0; s < count - 1; s++) {
            if (pos < (start[s] + end[s] + start[s + 1] + end[s + 1]) * 0.25f) return s;
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

    /** The cell width of the marks after the touched one, along the line. */
    public float pitchPx() {
        return mPitch;
    }

    /** Where the first cell starts. */
    public float contentStartPx() {
        return mCount == 0 ? 0f : mStart[0];
    }

    /** The length the cells and gaps take. */
    public float contentLengthPx() {
        return mCount == 0 ? 0f : mEnd[mCount - 1] - mStart[0];
    }

    public float cellStart(int slot) {
        return mStart[slot];
    }

    public float cellEnd(int slot) {
        return mEnd[slot];
    }

    public float centreAt(int slot) {
        return (mStart[slot] + mEnd[slot]) * 0.5f;
    }

    /** The centre of the first mark: where a finger before the row is held; 0 when empty. */
    public float firstCentre() {
        return mCount == 0 ? 0f : centreAt(0);
    }

    /** The centre of the last mark: where a finger past the row is held; 0 when empty. */
    public float lastCentre() {
        return mCount == 0 ? 0f : centreAt(mCount - 1);
    }

    /**
     * {@code pos} held to the span from the first mark's centre to the last mark's: what focus
     * and the marks' sizes follow, so a finger past either end is, to them, on the end mark.
     */
    public float clampedPos(float pos) {
        if (mCount == 0) return pos;
        return Math.max(firstCentre(), Math.min(lastCentre(), pos));
    }

    /**
     * Where {@code pos} stands against the marks: -1 before the first cell, 1 past the last, 0
     * along them; 0 when empty.
     */
    public int edgeAt(float pos) {
        if (mCount == 0) return 0;
        if (pos < mStart[0]) return -1;
        return pos > mEnd[mCount - 1] ? 1 : 0;
    }

    /** The slot nearest {@code pos}, the ends held; -1 when empty. */
    public int clampedSlotAt(float pos) {
        return mCount == 0 ? -1 : nearestCentre(mStart, mEnd, mCount, pos);
    }

    /** Which of a folded slot's members is under {@code pos}: its cell split equally. */
    public int memberAt(float pos, int slot) {
        PageBarFold.Slot s = slot(slot);
        if (s == null || !s.folded()) return 0;
        float within = (pos - mStart[slot]) / (mEnd[slot] - mStart[slot]);
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
