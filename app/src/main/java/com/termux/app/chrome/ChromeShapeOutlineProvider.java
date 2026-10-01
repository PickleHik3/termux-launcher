package com.termux.app.chrome;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The outline of one chrome view, taken from the shape model ({@link LiveChromeShape#outlineOf}):
 * the card's rounded rect in the view's coordinates, which the view's own bounds then cut. Allocation
 * free, and a plain convex round rect, so {@code setClipToOutline} and the elevation shadow keep
 * working. A view that has no clip yet clips to its bounds.
 *
 * <p>The outline is built from the view's size when it is asked for, and a view only asks again on
 * its own for a size change when it draws a background of its own. A chrome host that draws none
 * (the status bar's) kept the outline of the size it had when the clip was set, which is the size
 * from before the layout pass that follows a Style change: a Floating card's width stayed the
 * outline of a Docked bar. {@link #follow} is what keeps the outline in step with the view.
 */
public final class ChromeShapeOutlineProvider extends ViewOutlineProvider {
    @Nullable private LiveChromeShape.Clip mClip;
    /** The outline's own scratch, so a build allocates nothing. */
    @NonNull private final int[] mRect = new int[4];
    /** The views this provider is kept in step with; weak, so a recreated view costs nothing. */
    @NonNull private final Map<View, Boolean> mFollowed = new WeakHashMap<>();
    @NonNull private final View.OnLayoutChangeListener mOnLayout =
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                view.invalidateOutline();
        };

    /**
     * @return true when the clip changed, so the caller knows to invalidate the outline
     */
    public boolean setClip(@Nullable LiveChromeShape.Clip clip) {
        if (sameClip(mClip, clip)) return false;
        mClip = clip;
        return true;
    }

    /**
     * Rebuilds {@code view}'s outline whenever a layout pass changes its size, from then on. The
     * clip's reaches are laid over the view's live bounds, so an outline built before the pass
     * that resizes the view cuts the view to the size it used to be. Idempotent.
     */
    public void follow(@NonNull View view) {
        if (mFollowed.put(view, Boolean.TRUE) == null) view.addOnLayoutChangeListener(mOnLayout);
    }

    /**
     * The rect the clip's card covers, in the coordinates of a view {@code width} by
     * {@code height}: the view's bounds grown by the clip's reaches; the bounds with no clip.
     *
     * @param out {@code {left, top, right, bottom}}
     */
    static void cardRect(@Nullable LiveChromeShape.Clip clip, int width, int height,
                         @NonNull int[] out) {
        out[0] = clip == null ? 0 : -clip.reachLeft;
        out[1] = clip == null ? 0 : -clip.reachTop;
        out[2] = width + (clip == null ? 0 : clip.reachRight);
        out[3] = height + (clip == null ? 0 : clip.reachBottom);
    }

    /** The clip's radius, 0 with none. */
    public float radiusPx() {
        return mClip == null ? 0f : mClip.radius;
    }

    /** Whether any corner of the view is cut at all. */
    public boolean clipsCorners() {
        return mClip != null && mClip.radius > 0f;
    }

    @Override
    public void getOutline(@NonNull View view, @NonNull Outline outline) {
        LiveChromeShape.Clip clip = mClip;
        int[] rect = mRect;
        cardRect(clip, view.getWidth(), view.getHeight(), rect);
        if (clip == null) {
            outline.setRect(rect[0], rect[1], rect[2], rect[3]);
            return;
        }
        // A round rect has one radius, so a card square on a side (an edge card's screen side) is
        // grown by that radius past the square side: the view's bounds cut the excess off.
        float r = clip.radius;
        int grow = Math.round(r);
        if (r > 0f) {
            if (clip.topLeft == 0f && clip.topRight == 0f) rect[1] -= grow;
            else if (clip.bottomLeft == 0f && clip.bottomRight == 0f) rect[3] += grow;
            if (clip.topLeft == 0f && clip.bottomLeft == 0f) rect[0] -= grow;
            else if (clip.topRight == 0f && clip.bottomRight == 0f) rect[2] += grow;
        }
        outline.setRoundRect(rect[0], rect[1], rect[2], rect[3], r);
    }

    private static boolean sameClip(@Nullable LiveChromeShape.Clip a,
                                    @Nullable LiveChromeShape.Clip b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.reachLeft == b.reachLeft && a.reachTop == b.reachTop
            && a.reachRight == b.reachRight && a.reachBottom == b.reachBottom
            && a.topLeft == b.topLeft && a.topRight == b.topRight
            && a.bottomRight == b.bottomRight && a.bottomLeft == b.bottomLeft;
    }
}
