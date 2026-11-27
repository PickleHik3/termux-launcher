package com.termux.app.tiling;

/**
 * The one adapter between the stack's window ids ({@code long}, from 1) and the pane controller's
 * hosted-float ids ({@code int}): a tiling window's shell is drawn as a hosted float
 * ({@code TerminalPaneController.openHostedFloat}), the way a Terminal widget's is, under an id no
 * widget can have. AppWidget ids are 0 or more and the controller's "not hosted" mark is
 * {@code Integer.MIN_VALUE}, so a tiling window's hosted id is its window id negated.
 */
public final class TilingHostedIds {

    /** {@link #hostedId} for a window id the hosted range cannot hold. */
    public static final int NONE = 0;

    private TilingHostedIds() {}

    /** The hosted id of window {@code windowId}, or {@link #NONE} outside 1 … Integer.MAX_VALUE. */
    public static int hostedId(long windowId) {
        if (windowId < 1 || windowId > Integer.MAX_VALUE) return NONE;
        return (int) -windowId;
    }

    /** Whether {@code hostedId} names a tiling window rather than a widget's float. */
    public static boolean isTilingWindow(int hostedId) {
        return hostedId < 0 && hostedId != Integer.MIN_VALUE;
    }

    /** The window id behind {@code hostedId}, or {@link TilingStack#NO_WINDOW} for a widget's. */
    public static long windowId(int hostedId) {
        return isTilingWindow(hostedId) ? -(long) hostedId : TilingStack.NO_WINDOW;
    }
}
