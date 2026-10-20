package com.termux.app.dock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * Where the Taskbar's run strip stands in Home's float layer (and the minimised floats' chips,
 * which stand there on the other styles): above the edge that covers Home from below, the
 * keyboard's top while one is up (the dock riding on it), else the dock's top.
 *
 * <p>The edge and the layer are read as they are laid out ({@link #laidTopIn}), never where they
 * show on screen: the editor's frame scales the whole launcher (its Done grows it back), and a
 * page transition poses the page. A placement made while either stands transformed was computed
 * from on-screen tops mixed with laid-out heights, and stood that far off once the transform
 * went, since nothing places the strip again at rest (pass 4, 2026-10-21: the strip mid-screen
 * over the clock after a Done with Home's keyboard always on). The strip is a child of the layer,
 * so the layer's own transform carries it as it carries the page.
 */
public final class RunStripPlacement {

    /** What {@link #laidTopIn} answers for a view that is not under the ancestor. */
    public static final int NOT_UNDER = Integer.MIN_VALUE;

    private RunStripPlacement() {}

    /**
     * {@code view}'s top in {@code ancestor}'s coordinates as laid out: its own and every parent's
     * top, less the parents' scroll, with no translation, scale or rotation of anything on the
     * way. {@link #NOT_UNDER} when {@code view} is not under {@code ancestor}.
     */
    public static int laidTopIn(@NonNull ViewGroup ancestor, @NonNull View view) {
        int top = 0;
        View at = view;
        while (at != ancestor) {
            top += at.getTop();
            ViewParent parent = at.getParent();
            if (!(parent instanceof View)) return NOT_UNDER;
            View parentView = (View) parent;
            top -= parentView.getScrollY();
            at = parentView;
        }
        return top;
    }

    /**
     * The strip's translation in the layer: {@code bandPx} (the strip and the air under it) above
     * the keyboard's top while one is up ({@code keyboardTopPx} at or above 0), else above the
     * dock's top; both in the layer's pixels, as is {@code stripTopPx}, the strip's laid-out top.
     */
    public static float translationYPx(int dockTopPx, int keyboardTopPx, int bandPx,
                                       int stripTopPx) {
        int edge = keyboardTopPx >= 0 ? keyboardTopPx : dockTopPx;
        return edge - bandPx - stripTopPx;
    }
}
