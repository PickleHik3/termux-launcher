package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.app.ReducedMotion;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The {@link GlassMotionLight.Gate} every glass pane asks as a motion or a press starts: the rule
 * every Fancier Glass motion follows (the phone's animations on, no reduced motion, no lazy mode, no
 * battery saver). Living glass itself is always on: its switch is hidden, and a stored "off" from
 * before is not honoured, since nothing would let the user turn it back on. pre has no Animation speed setting, so
 * the springs run at 1x. Fancier Glass itself is the caller's: only a surface drawing through the
 * program has glass to move.
 */
public final class GlassMotionGate {

    private GlassMotionGate() {}

    /** The gate for surfaces in {@code context}'s app. */
    @NonNull
    public static GlassMotionLight.Gate of(@NonNull Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return () -> {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app);
            PowerManager power = app.getSystemService(PowerManager.class);
            return multiplier(ValueAnimator.areAnimatorsEnabled(), ReducedMotion.isEnabled(app),
                true,
                prefs != null && prefs.isLazyModeEnabled(),
                power != null && power.isPowerSaveMode(),
                1f);
        };
    }

    /** The gate's answer from its facts: 0 for still glass, else {@code speedMultiplier}. */
    @VisibleForTesting
    static float multiplier(boolean animatorsEnabled, boolean reducedMotion, boolean settingOn,
                            boolean lazyMode, boolean powerSave, float speedMultiplier) {
        if (!animatorsEnabled || reducedMotion || !settingOn || lazyMode || powerSave) return 0f;
        return Math.max(0f, speedMultiplier);
    }
}
