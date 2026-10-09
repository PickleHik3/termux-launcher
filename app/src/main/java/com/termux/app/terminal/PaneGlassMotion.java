package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.chrome.GlassAnchor;

/**
 * Keeps a pane's glass on the wallpaper it is over while the pane's frame animates: the FLIP
 * move of a swap, rearrange or split, and the entry pop.
 *
 * <p>The frost is aimed in screen space from the laid-out position, and a translation never
 * redraws a child, so without this the frost rode along with a travelling pane like a pasted
 * picture and snapped into place when it landed. While {@link #follow}ed, every animation frame
 * publishes the frame's own translation (and the shift a scale about its pivot makes to its
 * top-left corner) to {@link GlassAnchor#setMotion} and re-aims the slab; when the animation ends
 * or is cancelled the motion is withdrawn and the listeners removed. The plank's press moves the
 * same frame by the same properties but never publishes, so it stays out of the aim. The backdrop
 * is still the cached wallpaper-only frame; nothing here samples live.</p>
 */
final class PaneGlassMotion {

    private PaneGlassMotion() {}

    /** Publish and re-aim on every frame of {@code animator}, withdrawing when it finishes. */
    static void follow(@NonNull FrameLayout frame, @NonNull ViewPropertyAnimator animator) {
        animator.setUpdateListener(a -> publish(frame));
        animator.setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { release(frame, animator); }
            @Override public void onAnimationCancel(Animator a) { release(frame, animator); }
        });
    }

    /** Where the frame is now, relative to where it is laid out, handed to the glass. */
    static void publish(@NonNull FrameLayout frame) {
        float dx = frame.getTranslationX() + frame.getPivotX() * (1f - frame.getScaleX());
        float dy = frame.getTranslationY() + frame.getPivotY() * (1f - frame.getScaleY());
        GlassAnchor.setMotion(frame, dx, dy);
        reaim(frame);
    }

    private static void release(FrameLayout frame, ViewPropertyAnimator animator) {
        animator.setUpdateListener(null);
        animator.setListener(null);
        GlassAnchor.clearMotion(frame);
        reaim(frame);
    }

    private static void reaim(FrameLayout frame) {
        View backdrop = frame.findViewById(R.id.terminal_pane_glass);
        if (backdrop instanceof PaneGlassBackdropView
                && backdrop.getVisibility() == View.VISIBLE) {
            ((PaneGlassBackdropView) backdrop).invalidateGlassPosition();
        }
    }
}
