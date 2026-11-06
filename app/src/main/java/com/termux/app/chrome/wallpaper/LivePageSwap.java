package com.termux.app.chrome.wallpaper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.EditorMotion;

/**
 * Swaps two sibling pages of the Live picker (the scene grid and a scene's page) with a short
 * cross-fade: the incoming page is VISIBLE at alpha 0 on the first frame and rises to 1 while the
 * outgoing one falls to 0 and is then GONE with its alpha put back. Alpha only, so nothing lays
 * out per frame. A new swap, or {@link #cancel}, settles a running one at once.
 */
final class LivePageSwap {

    @Nullable private ValueAnimator mRunning;

    /** How long the cross-fade runs; 0 means the pages flip. */
    static long durationMs(boolean reducedMotion) {
        return reducedMotion ? 0L : EditorMotion.Use.PICKER_PAGE_FADE.durationMs(false);
    }

    /** Makes {@code in} the page and {@code out} not, with no fade. */
    void show(@NonNull View in, @NonNull View out) {
        cancel();
        settle(in, out);
    }

    /** Cross-fades from {@code out} to {@code in}; a flip under reduced motion. */
    void swap(@NonNull View out, @NonNull View in, boolean reducedMotion) {
        cancel();
        long duration = durationMs(reducedMotion);
        if (duration == 0L) {
            settle(in, out);
            return;
        }
        in.setAlpha(0f);
        in.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(EditorMotion.Use.PICKER_PAGE_FADE.curve(false));
        animator.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            in.setAlpha(t);
            out.setAlpha(1f - t);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                // Runs on cancel too, so a cut-short fade still ends with one page up.
                if (mRunning == a) mRunning = null;
                settle(in, out);
            }
        });
        mRunning = animator;
        animator.start();
    }

    /** Ends a running fade where it is going: the incoming page up, the outgoing one gone. */
    void cancel() {
        ValueAnimator running = mRunning;
        if (running != null) running.cancel();
    }

    private static void settle(@NonNull View in, @NonNull View out) {
        in.setAlpha(1f);
        in.setVisibility(View.VISIBLE);
        out.setVisibility(View.GONE);
        out.setAlpha(1f);
    }
}
