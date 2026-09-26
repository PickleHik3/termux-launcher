package com.termux.app.fragments.settings.termux;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.CycleInterpolator;

import androidx.annotation.NonNull;

import com.termux.app.terminal.Motion;

/**
 * The Model centre's small motion vocabulary on top of {@link Motion#settle()}: an arrival, a
 * press-and-release pop, a no-shake, and a haptic tick. Every one of them animates a transform or
 * opacity only (never a size or a colour, which would relayout or repaint the list), and each is a
 * no-op when the person has turned animations off.
 */
final class TaiMotion {
    static final long ARRIVE_MS = 350L;
    static final long POP_MS = 260L;

    private TaiMotion() {}

    /**
     * Reduced motion, as Android spells it: "Remove animations" (or the developer option) sets the
     * animator duration scale to 0. The system then already skips ValueAnimators; checking here
     * too stops the view from being parked at an animation's start values for a frame.
     */
    static boolean reduced(@NonNull Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A row settling into place from a little below: slide and fade, on the spring-like curve. */
    static void arrive(@NonNull View view) {
        view.animate().cancel();
        if (reduced(view.getContext())) {
            view.setTranslationY(0f);
            view.setAlpha(1f);
            return;
        }
        view.setTranslationY(dp(view, 18));
        view.setAlpha(0f);
        // Alpha rides along linearly while the travel is eased, per the motion spec.
        view.animate().translationY(0f).alpha(1f).setDuration(ARRIVE_MS).setInterpolator(Motion.settle())
            .withLayer().start();
    }

    /** A glyph that just changed meaning (pause becoming play) grows back in with a quarter turn. */
    static void morph(@NonNull View view) {
        view.animate().cancel();
        if (reduced(view.getContext())) {
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setRotation(0f);
            return;
        }
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.setRotation(-90f);
        view.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(POP_MS).setInterpolator(Motion.settle()).start();
    }

    /** The "not enough space" refusal: a short horizontal shake, then still. */
    static void shake(@NonNull View view) {
        if (reduced(view.getContext())) return;
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, dp(view, 6));
        shake.setInterpolator(new CycleInterpolator(3f));
        shake.setDuration(360L);
        shake.start();
    }

    /**
     * A light tick for Install and Pause. {@link View#performHapticFeedback} follows the system's
     * touch-feedback setting, which is the only app-wide haptics switch there is (the keyboard's
     * key-haptics setting is about typing, not about settings screens).
     */
    static void tick(@NonNull View view) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private static float dp(@NonNull View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
