package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * Where the clock and whatever shares the widget slot with it stand along the bar, in px from the
 * slot's start. This is the one place the clock alignment turns into a position; the slot measures
 * and lays out what it answers. Pure, so it tests on the JVM. {@link TopPaneSlotBudget} is the
 * same slot's other axis: how tall the cards and the media row are and where they stand.
 *
 * <p>The run is the bar less the place icon's leading cell and the trailing gutter. The widget
 * paints its face inside its own bounds at the same alignment, so these bounds decide where the
 * face lands.</p>
 *
 * <ul>
 *   <li><b>Alone</b>, Left and Right give the clock the whole run and the widget sets the face
 *   against the edge asked for. Centre mirrors the leading cell on the trailing side, so the face
 *   lands on the bar's own centre line however wide the place icons grow.</li>
 *   <li><b>Shared</b> with the media row or pinned cards, the clock is as wide as its face and the
 *   side content takes the rest of the run, on the far side of the clock. Left keeps the clock
 *   after the place icon and the content after it, which is where every card and media fix was
 *   made. Right sets the clock against the gutter and the content before it. Centre stands where
 *   Left does: pulled to the middle it left the media row too little room for its album art,
 *   so a centred clock steps aside for whatever shares the bar.</li>
 * </ul>
 */
public final class TopPaneSlotRow {

    public final int clockStart;
    public final int clockEnd;
    /** The run the media row or the pinned cards take; empty ({@code sideStart == sideEnd}) alone. */
    public final int sideStart;
    public final int sideEnd;

    private TopPaneSlotRow(int clockStart, int clockEnd, int sideStart, int sideEnd) {
        this.clockStart = clockStart;
        this.clockEnd = Math.max(clockStart, clockEnd);
        this.sideStart = sideStart;
        this.sideEnd = Math.max(sideStart, sideEnd);
    }

    public int clockWidth() {
        return clockEnd - clockStart;
    }

    public int sideWidth() {
        return sideEnd - sideStart;
    }

    /** The clock with the slot to itself: its cell, which the face aligns itself inside. */
    @NonNull
    public static TopPaneSlotRow alone(int width, int leadingCell, int gutter,
                                       @Nullable String alignment) {
        int start = leadingCell;
        int end = width - gutter;
        if (isCenter(alignment)) {
            int inset = Math.max(leadingCell, gutter);
            start = inset;
            end = width - inset;
        }
        return new TopPaneSlotRow(start, end, start, start);
    }

    /**
     * The clock beside the media row or pinned cards.
     *
     * @param clockWidth the width the face paints at the slot's fit, already capped to the run
     */
    @NonNull
    public static TopPaneSlotRow shared(int width, int leadingCell, int gutter, int gap,
                                        int clockWidth, @Nullable String alignment) {
        int runStart = leadingCell;
        int runEnd = width - gutter;
        int clock = Math.max(0, Math.min(clockWidth, runEnd - runStart));
        if (isRight(alignment)) {
            int clockStart = runEnd - clock;
            return new TopPaneSlotRow(clockStart, runEnd, runStart, clockStart - gap);
        }
        // Left, and Centre with it: the clock after the place icon, the content after it.
        return new TopPaneSlotRow(runStart, runStart + clock, runStart + clock + gap, runEnd);
    }

    private static boolean isCenter(@Nullable String alignment) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(alignment);
    }

    private static boolean isRight(@Nullable String alignment) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(alignment);
    }
}
