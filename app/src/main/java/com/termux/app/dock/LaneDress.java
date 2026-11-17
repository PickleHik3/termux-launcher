package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Side;

/**
 * Which side's layout dresses a row of the bottom stack (D1-A, ADR 0030). A row that stands for
 * both sides, or for the side in front, wears the front layout. A lane row of the side that is
 * not in front wears that side's own layout at rest, so a crossing's landing changes nothing in
 * the arriving lane's look: the answer for a row of one side is the same whichever side is in
 * front, so a page change alone redresses nothing.
 */
public final class LaneDress {

    private LaneDress() {}

    /**
     * The layout that dresses a row whose lane belongs to {@code rowSide} (null: the shared
     * stack's), with {@code frontSide} in front; {@code front} is the front side's layout and
     * {@code other} the other side's rest (null while there is no lane).
     */
    @NonNull
    public static DockLayout layoutFor(@Nullable Side rowSide, @NonNull Side frontSide,
                                       @NonNull DockLayout front, @Nullable DockLayout other) {
        if (rowSide == null || rowSide == frontSide || other == null) return front;
        return other;
    }
}
