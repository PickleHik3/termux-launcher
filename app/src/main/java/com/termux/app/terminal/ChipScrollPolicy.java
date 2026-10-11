package com.termux.app.terminal;

/**
 * Where the window chip strip scrolls to bring a chip into view: as little as it takes, so the
 * rest of the row holds still, and far enough that the chip clears the faded edge it was under.
 */
final class ChipScrollPolicy {

    private ChipScrollPolicy() {}

    /**
     * The scroll that shows the chip spanning {@code chipLeft}..{@code chipRight} (in the strip's
     * own coordinates) whole inside a viewport of {@code viewport} pixels, clear of the
     * {@code edge}-wide fade at either end. Already in view, the scroll stays where it is. A chip
     * wider than the room between the fades shows its start. The answer is always within
     * 0..{@code maxScroll}, which also lets a chip at either end of the strip sit under no fade at
     * all: there is none drawn where the strip cannot scroll further.
     */
    static int scrollToShow(int chipLeft, int chipRight, int scrollX, int viewport, int edge,
                            int maxScroll) {
        int target = scrollX;
        if (chipRight + edge > target + viewport) target = chipRight + edge - viewport;
        if (chipLeft - edge < target) target = chipLeft - edge;
        return Math.max(0, Math.min(target, Math.max(0, maxScroll)));
    }
}
