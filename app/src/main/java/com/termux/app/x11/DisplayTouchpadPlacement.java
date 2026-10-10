package com.termux.app.x11;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where the touchpad stands inside the keyboard frame it borrows in mouse mode.
 *
 * <p>It takes the whole frame, as it always has, except over a split keyboard: there it stands in
 * the parting between the two key runs as one more key — the seat a key standing there would
 * take — and both runs keep typing. A parting too narrow to point in is no use, so under
 * {@link #MIN_GAP_DP} the pad takes the whole frame again — which a keyboard wide enough to part
 * never reaches, because it is asked for {@link #minimumGapPx} while the pad is up.
 */
public final class DisplayTouchpadPlacement {

    /** A parting narrower than this is not worth pointing in. */
    public static final float MIN_GAP_DP = 160f;

    private DisplayTouchpadPlacement() {}

    /**
     * The parting the pad asks a split keyboard to widen to, in the frame's own pixels. Zero
     * for a screen whose density is not known yet, which asks for nothing.
     */
    public static int minimumGapPx(float density) {
        return density > 0f ? Math.round(MIN_GAP_DP * density) : 0;
    }

    /** Whether [gapPx], the parting in the frame's own pixels, is wide enough to point in. */
    public static boolean fitsGap(@Nullable Rect gapPx, float density) {
        int minimum = minimumGapPx(density);
        return gapPx != null && minimum > 0 && gapPx.width() >= minimum;
    }

    /** Whether the pad stands in the parting: it fits there, and the keyboard has a seat in it. */
    public static boolean standsInGap(@Nullable Rect gapPx, @Nullable Rect seatPx, float density) {
        return fitsGap(gapPx, density) && seatPx != null && !seatPx.isEmpty();
    }

    /**
     * The pad's layout inside the keyboard's host: the seat in the parting when it fits, the
     * whole frame otherwise. [gapPx] is the strip between the key runs, which decides whether the
     * pad fits; [seatPx] is where a key standing in it would be drawn, which is where the pad
     * goes. A frame of no measured height leaves the pad wrapping its content, which is what it
     * did before the keyboard had been laid out once.
     */
    @NonNull
    public static FrameLayout.LayoutParams padParams(@Nullable Rect gapPx, @Nullable Rect seatPx,
                                                     int frameHeightPx, float density) {
        if (!standsInGap(gapPx, seatPx, density)) {
            int height = frameHeightPx > 0 ? frameHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT;
            return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height,
                Gravity.TOP);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(seatPx.width(),
            seatPx.height(), Gravity.TOP | Gravity.START);
        params.leftMargin = seatPx.left;
        params.topMargin = seatPx.top;
        return params;
    }

    /** Whether [current] already says what [wanted] says, so nothing has to be laid out again. */
    public static boolean describes(@Nullable ViewGroup.LayoutParams current,
                                    @NonNull FrameLayout.LayoutParams wanted) {
        if (!(current instanceof FrameLayout.LayoutParams)) {
            return false;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) current;
        return params.width == wanted.width && params.height == wanted.height
            && params.leftMargin == wanted.leftMargin && params.topMargin == wanted.topMargin
            && params.gravity == wanted.gravity;
    }
}
