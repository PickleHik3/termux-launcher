package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.StatusRowKeepOut;

/**
 * What the screen takes from the window bar when Full screen lifts it to the screen's top: the
 * same answer the status row reads ({@link StatusRowKeepOut#answer}), fed the bar's own edges.
 * The corners become the bar's side padding, added to the trailing gap the status row asks for.
 * Pure arithmetic in window pixels.
 */
final class WindowBarKeepOut {

    private WindowBarKeepOut() {}

    /**
     * The answer for a bar standing at {@code [left, right) x [top, bottom)} of a window
     * {@code windowWidth x windowHeight}; {@link StatusRowKeepOut.Answer#NONE} with no screen
     * (outside Full screen the host hands none).
     */
    @NonNull
    static StatusRowKeepOut.Answer answer(@Nullable StatusRowKeepOut.Screen screen,
                                          int windowWidth, int windowHeight, int left, int top,
                                          int right, int bottom) {
        if (screen == null) return StatusRowKeepOut.Answer.NONE;
        return StatusRowKeepOut.answer(screen, windowWidth, windowHeight, left, top, right, bottom);
    }

    /**
     * The bar's physical {@code {left, right}} padding: each side's corner inset, plus the
     * status row's trailing gap on the end side ({@code rtl} puts the end on the left).
     */
    @NonNull
    static int[] sidePaddingPx(@NonNull StatusRowKeepOut.Answer answer, int trailingGapPx,
                               boolean rtl) {
        int trailing = Math.max(0, trailingGapPx);
        return new int[] {answer.leftInsetPx + (rtl ? trailing : 0),
            answer.rightInsetPx + (rtl ? 0 : trailing)};
    }
}
