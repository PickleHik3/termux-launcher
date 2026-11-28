package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * Where the palette stands when the Terl control bar's search opens it: in what the bar and the
 * keyboards leave free. Above a bar in a row (and above the keyboard and the extra keys when they
 * stand over it); beside a rail, on the side it does not take. The glass grows up from just above
 * them with its keycap strip under it, no wider than the sheets' measure and centred in the room.
 * Pure: host coordinates in, the room out.
 */
final class CommandPaletteBarRoom {

    /** The open glass's sides. */
    final float left;
    final float right;
    /** Where the glass ends: its keycap strip hangs below, above the bar and the keyboard. */
    final float anchor;
    /** The highest its top may rise. */
    final float top;
    /** The free room, in which taps belong to the palette; outside it they reach the bar and keys. */
    final float freeLeft;
    final float freeRight;
    final float freeBottom;

    private CommandPaletteBarRoom(float left, float right, float anchor, float top, float freeLeft,
                                  float freeRight, float freeBottom) {
        this.left = left;
        this.right = right;
        this.anchor = anchor;
        this.top = top;
        this.freeLeft = freeLeft;
        this.freeRight = freeRight;
        this.freeBottom = freeBottom;
    }

    /**
     * The room in a host {@code hostWidth} wide whose page runs from {@code roomTop} to
     * {@code roomBottom}. The bar's {@code barLeft}, {@code barTop} and {@code barRight} are NaN when
     * it is not shown, {@code keysTop} when no keyboard or extra keys stand up.
     */
    @NonNull
    static CommandPaletteBarRoom of(float hostWidth, float roomTop, float roomBottom, float barLeft,
                                    float barTop, float barRight, float keysTop, float insetPx,
                                    float stripReservePx, float maxWidthPx) {
        float freeLeft = 0f;
        float freeRight = hostWidth;
        float freeBottom = roomBottom;
        if (!Float.isNaN(barLeft) && !Float.isNaN(barRight) && !Float.isNaN(barTop)) {
            if (barRight - barLeft < hostWidth / 2f) {
                // A rail: the glass stands beside it.
                if ((barLeft + barRight) / 2f < hostWidth / 2f) freeLeft = Math.max(freeLeft, barRight);
                else freeRight = Math.min(freeRight, barLeft);
            } else {
                freeBottom = Math.min(freeBottom, barTop);
            }
        }
        if (!Float.isNaN(keysTop)) freeBottom = Math.min(freeBottom, keysTop);
        float left = freeLeft + insetPx;
        float right = freeRight - insetPx;
        if (right - left > maxWidthPx) {
            float middle = (freeLeft + freeRight) / 2f;
            left = middle - maxWidthPx / 2f;
            right = middle + maxWidthPx / 2f;
        }
        if (right <= left) right = left + 1f;
        float top = roomTop + insetPx;
        float anchor = Math.max(top + 1f, freeBottom - stripReservePx);
        return new CommandPaletteBarRoom(left, right, anchor, top, freeLeft, freeRight, freeBottom);
    }
}
