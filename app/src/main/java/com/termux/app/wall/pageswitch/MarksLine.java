package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The Marks line's layout along its axis, pure: the wall's entries ({@link PageBarModel}) folded
 * into as many slots as the line's length holds ({@link PageBarFold}, {@link PageBarLayout#maxSlots}),
 * each slot a cell of {@link #pitchPx()} along the line, the marks centred in their cells from the
 * line's start. The view draws the answer and routes a tap through {@link #targetAt}; it builds a
 * new one only when the entries, the attention or the length change, never per draw.
 *
 * <p>The same numbers serve a row (the axis is x) and a column on a side edge (the axis is y).
 * Sliding along the line and the bubble (#329) read {@link #slotAt} and {@link #centreAt}.
 */
public final class MarksLine {

    /** A mark's box, in dp. */
    public static final float MARK_DP = 20f;
    /** The widest a slot's cell grows, in dp: a short list sits together, not spread over the bar. */
    public static final float MAX_PITCH_DP = 32f;

    @NonNull private final List<PageBarModel.Entry> mEntries;
    @NonNull private final PageBarFold.Result mFold;
    @Nullable private final boolean[] mAttention;
    private final float mPitch;
    private final float mMark;

    /**
     * @param attention per entry: it wants the user (null for none). Read at construction only.
     * @param lengthPx the line's length along its axis
     */
    public MarksLine(@NonNull List<PageBarModel.Entry> entries, @Nullable boolean[] attention,
                     float density, float lengthPx) {
        mEntries = entries;
        mAttention = attention == null ? null : attention.clone();
        mFold = PageBarFold.fold(entries, PageBarLayout.maxSlots(density, lengthPx));
        int slots = mFold.slots.size();
        float cap = MAX_PITCH_DP * density;
        mPitch = slots == 0 ? cap : Math.max(0f, Math.min(cap, lengthPx / slots));
        mMark = Math.min(MARK_DP * density, mPitch);
    }

    /** The folded slots, in order. */
    @NonNull public List<PageBarFold.Slot> slots() {
        return mFold.slots;
    }

    public int slotCount() {
        return mFold.slots.size();
    }

    /** A slot's cell along the line. */
    public float pitchPx() {
        return mPitch;
    }

    /** A mark's box. */
    public float markSizePx() {
        return mMark;
    }

    /** The length the slots take along the line. */
    public float contentLengthPx() {
        return mPitch * mFold.slots.size();
    }

    /** Where slot {@code slot}'s mark is centred along the line. */
    public float centreAt(int slot) {
        return mPitch * (slot + 0.5f);
    }

    /** The slot whose cell holds {@code pos} along the line, or -1 past either end. */
    public int slotAt(float pos) {
        if (mPitch <= 0f || pos < 0f || pos >= contentLengthPx()) return -1;
        return Math.min(mFold.slots.size() - 1, (int) (pos / mPitch));
    }

    /** Whether any entry of slot {@code slot} wants the user. */
    public boolean attention(int slot) {
        if (mAttention == null) return false;
        PageBarFold.Slot s = mFold.slots.get(slot);
        for (int m = 0; m < s.count; m++) {
            int i = s.first + m;
            if (i < mAttention.length && mAttention[i]) return true;
        }
        return false;
    }

    /**
     * What a tap at {@code pos} does: the target of the page it lands on, or null when it lands on
     * no mark or on the current page (a tap there does nothing). A folded slot goes to its current
     * member, else the window you would return to, else its first, as the page bar does.
     */
    @Nullable
    public PageBarModel.Target targetAt(float pos) {
        int slot = slotAt(pos);
        if (slot < 0) return null;
        PageBarModel.Target target = mEntries.get(tapEntry(slot)).target;
        return target.action == PageBarModel.Action.CLOSE ? null : target;
    }

    private int tapEntry(int slotIndex) {
        PageBarFold.Slot s = mFold.slots.get(slotIndex);
        if (!s.folded()) return s.first;
        if (s.currentMember >= 0) return s.first + s.currentMember;
        for (int m = 0; m < s.count; m++) {
            if (mEntries.get(s.first + m).returnTo) return s.first + m;
        }
        return s.first;
    }
}
