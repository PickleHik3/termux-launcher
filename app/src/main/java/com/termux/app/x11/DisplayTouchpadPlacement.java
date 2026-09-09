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
 * <p>It takes the whole frame, as it always has, except over a split keyboard: there the parting
 * between the two halves is the touchpad and both halves keep typing. A parting too narrow to
 * point in is no use, so under {@link #MIN_GAP_DP} the pad takes the whole frame again.
 */
public final class DisplayTouchpadPlacement {

    /** A parting narrower than this is not worth pointing in. */
    public static final float MIN_GAP_DP = 160f;

    private DisplayTouchpadPlacement() {}

    /** Whether [gapPx], the parting in the frame's own pixels, is wide enough to point in. */
    public static boolean fitsGap(@Nullable Rect gapPx, float density) {
        return gapPx != null && density > 0f
            && gapPx.width() >= Math.round(MIN_GAP_DP * density);
    }

    /**
     * The pad's layout inside the keyboard's host: the parting when it fits, the whole frame
     * otherwise. A frame of no measured height leaves the pad wrapping its content, which is
     * what it did before the keyboard had been laid out once.
     */
    @NonNull
    public static FrameLayout.LayoutParams padParams(@Nullable Rect gapPx, int frameHeightPx,
                                                     float density) {
        int height = frameHeightPx > 0 ? frameHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (!fitsGap(gapPx, density)) {
            return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height,
                Gravity.TOP);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(gapPx.width(), height,
            Gravity.TOP | Gravity.START);
        params.leftMargin = gapPx.left;
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
            && params.leftMargin == wanted.leftMargin && params.gravity == wanted.gravity;
    }
}
