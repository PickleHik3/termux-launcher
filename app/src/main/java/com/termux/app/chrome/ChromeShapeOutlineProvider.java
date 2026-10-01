package com.termux.app.chrome;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The outline of one chrome view, taken from the shape model ({@link LiveChromeShape#outlineOf}):
 * the card's round rect in the view's coordinates, which the view's own bounds then cut. Allocation
 * free, and a plain convex round rect, so {@code setClipToOutline} and the elevation shadow keep
 * working. A view that has no clip yet clips to its bounds.
 */
public final class ChromeShapeOutlineProvider extends ViewOutlineProvider {
    @Nullable private LiveChromeShape.Clip mClip;

    /**
     * @return true when the clip changed, so the caller knows to invalidate the outline
     */
    public boolean setClip(@Nullable LiveChromeShape.Clip clip) {
        if (sameClip(mClip, clip)) return false;
        mClip = clip;
        return true;
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
        if (clip == null) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
            return;
        }
        outline.setRoundRect(-clip.reachLeft, -clip.reachTop, view.getWidth() + clip.reachRight,
            view.getHeight() + clip.reachBottom, clip.radius);
    }

    private static boolean sameClip(@Nullable LiveChromeShape.Clip a,
                                    @Nullable LiveChromeShape.Clip b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.reachLeft == b.reachLeft && a.reachTop == b.reachTop
            && a.reachRight == b.reachRight && a.reachBottom == b.reachBottom
            && a.radius == b.radius;
    }
}
