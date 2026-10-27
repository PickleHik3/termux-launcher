package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import java.util.Arrays;

/**
 * The look values one half of the wall dresses its chrome in (ADR 0013): the blur, opacity, grain
 * and tint the dock, the keyboard and the status bar resolve to there, and whether each still
 * follows that half's Look. A value, read once where the chrome is arranged or settles, so two
 * halves are compared without asking the store per frame.
 *
 * <p>The stack's part (the dock and the keyboard) is part of what the stack wears
 * ({@link SeamDress.StackDress}), so a crossing between halves whose stack looks differ changes
 * it where none of the stack shows, as it does for a differing icon size (ADR 0013 §1). The status
 * bar's part is compared at settle.
 */
public final class HalfLook {

    /** The surfaces whose look is held per half, stack first. */
    private static final SurfaceSlot[] SLOTS = {SurfaceSlot.DOCK, SurfaceSlot.KEYBOARD,
        SurfaceSlot.STATUS};
    private static final SurfaceProperty[] PROPERTIES = {SurfaceProperty.BLUR,
        SurfaceProperty.OPACITY, SurfaceProperty.GRAIN, SurfaceProperty.TINT};
    /** Cells per slot: a value and a link for each property. */
    private static final int PER_SLOT = PROPERTIES.length * 2;
    /** Where the status bar's cells begin: after the dock's, the keyboard's and its backdrop. */
    private static final int STATUS_START = 2 * PER_SLOT + 1;
    private static final int CELLS = STATUS_START + PER_SLOT;

    /** A look with nothing read: the half of a launcher without preferences yet. */
    public static final HalfLook NONE = new HalfLook(new int[CELLS]);

    @NonNull private final int[] mCells;

    private HalfLook(@NonNull int[] cells) {
        mCells = cells;
    }

    /** The half's look as the preferences hold it now; {@link #NONE} without them. */
    @NonNull
    public static HalfLook read(@Nullable TermuxAppSharedPreferences preferences,
                                @NonNull WallHalf half) {
        if (preferences == null) return NONE;
        int[] cells = new int[CELLS];
        int i = 0;
        for (SurfaceSlot slot : SLOTS) {
            for (SurfaceProperty property : PROPERTIES) {
                cells[i++] = preferences.getSurfaceValue(slot, property, half);
                cells[i++] = preferences.isSurfaceInheriting(slot, property, half) ? 1 : 0;
            }
            // The keyboard's whole-stack fade, its Opacity once detached, sits with the stack.
            if (slot == SurfaceSlot.KEYBOARD)
                cells[i++] = preferences.getInAppKeyboardBackdropOpacity(half);
        }
        return new HalfLook(cells);
    }

    /** Whether the dock and the keyboard look alike in both. */
    public boolean stackEquals(@NonNull HalfLook other) {
        return rangeEquals(other, 0, STATUS_START);
    }

    /** Whether the status bar looks alike in both. */
    public boolean statusEquals(@NonNull HalfLook other) {
        return rangeEquals(other, STATUS_START, mCells.length);
    }

    private boolean rangeEquals(@NonNull HalfLook other, int from, int to) {
        for (int i = from; i < to; i++) {
            if (mCells[i] != other.mCells[i]) return false;
        }
        return true;
    }

    /** The stack's part alone, for a value that compares only that ({@link SeamDress.StackDress}). */
    int stackHashCode() {
        return Arrays.hashCode(Arrays.copyOfRange(mCells, 0, STATUS_START));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        return other instanceof HalfLook && Arrays.equals(mCells, ((HalfLook) other).mCells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mCells);
    }

    @NonNull
    @Override
    public String toString() {
        return "HalfLook" + Arrays.toString(mCells);
    }
}
