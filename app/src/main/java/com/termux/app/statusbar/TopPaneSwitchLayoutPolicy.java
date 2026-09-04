package com.termux.app.statusbar;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * Where the clock and the wall's place switch sit inside the 68dp top slot, applied by
 * {@link TopPaneWidgetSlot#onLayout}.
 *
 * <p>The switch hugs its content and sits at the end opposite the clock's alignment (a centred
 * clock gives it the trailing end), vertically centred; the clock keeps the rest and aligns its
 * own face inside it, dropping to its compact face when the full one no longer fits. When even the
 * compact clock would not fit beside the switch, the switch stands down and the clock has the slot
 * to itself — the swipe remains the way across.
 */
public final class TopPaneSwitchLayoutPolicy {

    public static final class Result {
        @NonNull public final Rect clock;
        @NonNull public final Rect place;
        public final boolean clockCompact;

        private Result(Rect clock, Rect place, boolean clockCompact) {
            this.clock = clock;
            this.place = place;
            this.clockCompact = clockCompact;
        }
    }

    private TopPaneSwitchLayoutPolicy() {}

    /**
     * @param switchWidthPx  the switch's own measured width; 0 when it is not shown
     * @param switchHeightPx the switch's height, centred in the slot
     * @param clockMinWidthPx the least the clock can live with — its compact face
     */
    @NonNull
    public static Result calculate(int widthPx, int heightPx, int gutterPx, int gapPx,
                                    @Nullable String clockAlignment, int switchWidthPx,
                                    int switchHeightPx, int clockFullDesiredWidthPx,
                                    int clockMinWidthPx, boolean rtl) {
        int height = Math.max(0, heightPx);
        int usable = Math.max(0, widthPx - gutterPx * 2);
        Rect clock = new Rect(gutterPx, 0, gutterPx + usable, height);
        Rect place = new Rect();
        if (switchWidthPx > 0 && usable - switchWidthPx - gapPx >= clockMinWidthPx) {
            boolean clockRight = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT
                .equals(clockAlignment);
            int top = Math.max(0, (height - switchHeightPx) / 2);
            int bottom = Math.min(height, top + switchHeightPx);
            if (clockRight) {
                place.set(gutterPx, top, gutterPx + switchWidthPx, bottom);
                clock.set(place.right + gapPx, 0, gutterPx + usable, height);
            } else {
                place.set(gutterPx + usable - switchWidthPx, top, gutterPx + usable, bottom);
                clock.set(gutterPx, 0, place.left - gapPx, height);
            }
        }
        if (rtl) {
            clock = mirror(clock, widthPx);
            place = mirror(place, widthPx);
        }
        boolean clockCompact = clockFullDesiredWidthPx > clock.width();
        return new Result(clock, place, clockCompact);
    }

    private static Rect mirror(Rect value, int width) {
        if (value.isEmpty()) return new Rect();
        return new Rect(width - value.right, value.top, width - value.left, value.bottom);
    }
}
