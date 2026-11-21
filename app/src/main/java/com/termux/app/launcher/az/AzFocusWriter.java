package com.termux.app.launcher.az;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Writes the A-Z gesture's focus onto the launch view it points at: the focused icon dims to a
 * rest alpha (with a short dip when {@code bounce} asks for one), and the view it leaves or the
 * gesture ends on is put back. The apps row decides which view is focused and hands it here; this
 * owns every property written on it.
 */
public final class AzFocusWriter {

    private static final float REST_ALPHA = 0.26f;

    @Nullable private View focused;
    @Nullable private Animator animator;

    /** {@code target} is the focused view now; the one focused before is put back. */
    public void focus(@NonNull View target, boolean bounce) {
        clear();
        focused = target;
        if (bounce) {
            animate(target);
        } else {
            applyRestState(target);
        }
    }

    /** Puts the focused view back, if there is one. */
    public void clear() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (focused != null) {
            focused.animate().cancel();
            focused.setScaleX(1f);
            focused.setScaleY(1f);
            focused.setTranslationY(0f);
            focused.animate()
                .alpha(1f)
                .setDuration(96L)
                .setInterpolator(new DecelerateInterpolator(1.45f))
                .setListener(null)
                .start();
        }
        focused = null;
    }

    private void animate(@NonNull View target) {
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        AnimatorSet bounce = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(target, View.ALPHA, target.getAlpha(), REST_ALPHA);
        bounce.playTogether(alpha);
        bounce.setDuration(86L);
        bounce.setInterpolator(new DecelerateInterpolator(1.55f));
        bounce.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (target == focused) {
                    applyRestState(target);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (target == focused) {
                    applyRestState(target);
                }
            }
        });
        animator = bounce;
        bounce.start();
    }

    private static void applyRestState(@NonNull View target) {
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        target.setAlpha(REST_ALPHA);
    }
}
