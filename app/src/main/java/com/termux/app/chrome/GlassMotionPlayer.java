package com.termux.app.chrome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Plays a {@link GlassMotion} on a glass card: the one place its values become animators, so a
 * surface that arrives or leaves in glass asks for it here instead of carrying its own numbers.
 *
 * <p>{@link GlassMotion#CLASSIC} takes the plain {@code ViewPropertyAnimator} path with the
 * values the sheet card always used. A springy profile (Mist) fades on a tween while the scale and
 * the blur settle on springs; the blur is a {@code RenderEffect}, so it exists from Android 12 and
 * is simply skipped below. The caller has already handled "remove animations": it does not call
 * here then.</p>
 */
public final class GlassMotionPlayer {

    private GlassMotionPlayer() {}

    /** The card's spring animators, so a leave or a re-enter can stop what is still settling. */
    private static final WeakHashMap<View, List<Animator>> RUNNING = new WeakHashMap<>();

    /**
     * Brings a card in, from its own start state: transparent and shrunk (and blurred, for a
     * profile that asks). {@code onEnd} runs once, when everything has settled.
     */
    public static void enter(@NonNull View card, @NonNull GlassMotion motion,
                             @Nullable Runnable onEnd) {
        cancelSprings(card);
        card.setAlpha(0f);
        card.setScaleX(motion.enterScaleFrom);
        card.setScaleY(motion.enterScaleFrom);
        if (!motion.springy()) {
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(motion.enterAlphaMs)
                .setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
                .withEndAction(onEnd)
                .start();
            return;
        }
        int[] pending = {1};
        Runnable settled = () -> {
            if (--pending[0] == 0 && onEnd != null) onEnd.run();
        };
        card.animate().alpha(1f).setDuration(motion.enterAlphaMs)
            .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))   // fast-out-slow-in
            .withEndAction(settled)
            .start();
        pending[0]++;
        spring(card, motion.enterScaleSpring, fraction -> {
            float scale = motion.enterScaleFrom + (1f - motion.enterScaleFrom) * fraction;
            card.setScaleX(scale);
            card.setScaleY(scale);
        }, () -> {
            card.setScaleX(1f);
            card.setScaleY(1f);
            settled.run();
        });
        if (motion.enterBlurFromDp > 0f && motion.enterBlurSpring != null
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float density = card.getResources().getDisplayMetrics().density;
            pending[0]++;
            spring(card, motion.enterBlurSpring, fraction -> {
                float px = Math.max(0f, motion.enterBlurFromDp * (1f - fraction)) * density;
                card.setRenderEffect(px < 0.5f ? null
                    : RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
            }, () -> {
                card.setRenderEffect(null);
                settled.run();
            });
        }
    }

    /** Takes a card out: it fades (and shrinks, for a profile that says so), then {@code onEnd}. */
    public static void exit(@NonNull View card, @NonNull GlassMotion motion,
                            @Nullable Runnable onEnd) {
        cancelSprings(card);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) card.setRenderEffect(null);
        android.view.ViewPropertyAnimator animator = card.animate().alpha(0f)
            .scaleX(motion.exitScaleTo).scaleY(motion.exitScaleTo)
            .setDuration(motion.exitAlphaMs);
        if (motion.obsidianCurves)
            animator.setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f));   // fast-out-linear-in
        animator.withEndAction(onEnd).start();
    }

    private interface Progress {
        void apply(float fraction);
    }

    /** One spring, played on a ValueAnimator whose linear time is the spring's own clock. */
    private static void spring(@NonNull View card, @NonNull GlassMotion.Spring spring,
                               @NonNull Progress progress, @NonNull Runnable onSettled) {
        ValueAnimator animator = springAnimator(spring, progress, onSettled);
        List<Animator> running = RUNNING.get(card);
        if (running == null) {
            running = new ArrayList<>(2);
            RUNNING.put(card, running);
        }
        running.add(animator);
        animator.start();
    }

    /** The unstarted animator for {@link #spring}; {@code onSettled} runs only if it is not cancelled. */
    @NonNull
    private static ValueAnimator springAnimator(@NonNull GlassMotion.Spring spring,
                                                @NonNull Progress progress,
                                                @NonNull Runnable onSettled) {
        long duration = spring.settleMillis();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(t -> spring.valueAt(t * duration / 1000f));
        animator.addUpdateListener(a -> progress.apply((Float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (!mCancelled) onSettled.run();
            }
        });
        return animator;
    }

    /** The press scale each tile is settling on, so a release or a new press can stop it. */
    private static final WeakHashMap<View, Animator> PRESSING = new WeakHashMap<>();

    /**
     * Scales a tile to where a press or a release leaves it, from wherever it is now, on the
     * profile's own spring. Classic never scales, so it returns before touching the view. A new
     * press or release stops the one still settling.
     */
    public static void press(@NonNull View tile, @NonNull GlassMotion motion, boolean down) {
        Animator previous = PRESSING.remove(tile);
        if (!motion.pressable() && previous == null) return;
        if (previous != null) previous.cancel();
        float from = tile.getScaleX();
        // A profile that stopped pressing mid-press (a preset switched) still lets the tile go.
        float to = motion.pressable() ? motion.pressTarget(down) : 1f;
        ValueAnimator animator = springAnimator(motion.pressSpringFor(down), fraction -> {
            float scale = from + (to - from) * fraction;
            tile.setScaleX(scale);
            tile.setScaleY(scale);
        }, () -> {
            tile.setScaleX(to);
            tile.setScaleY(to);
            PRESSING.remove(tile);
        });
        PRESSING.put(tile, animator);
        animator.start();
    }

    /**
     * Fades a whole glass surface in or out without the card's scale: the classic path is the
     * caller's own {@code classicMs} tween, exactly as it ran; a springy profile takes its own
     * arrival and departure times and curves.
     */
    public static void fade(@NonNull View surface, @NonNull GlassMotion motion, boolean in,
                            long classicMs) {
        surface.animate().cancel();
        android.view.ViewPropertyAnimator animator = surface.animate().alpha(in ? 1f : 0f);
        if (!motion.springy()) {
            animator.setDuration(classicMs).start();
            return;
        }
        animator.setDuration(in ? motion.enterAlphaMs : motion.exitAlphaMs)
            .setInterpolator(in ? new PathInterpolator(0.4f, 0f, 0.2f, 1f)
                : new PathInterpolator(0.4f, 0f, 1f, 1f))
            .start();
    }

    private static void cancelSprings(@NonNull View card) {
        List<Animator> running = RUNNING.remove(card);
        if (running == null) return;
        for (Animator animator : running) animator.cancel();
    }
}
