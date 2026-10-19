package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.app.AnimationSpeed;
import com.termux.app.ReducedMotion;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The {@link GlassMotionLight.Gate} every glass pane asks as a motion or a press starts: the
 * Motion light setting, and the rule every Fancier Glass motion follows (the phone's animations
 * on, no reduced motion, no lazy mode, no battery saver), then Animation speed's multiplier, whose
 * Off is no light at all. Fancier Glass itself is the caller's: only a surface drawing through the
 * program has a light to ask about.
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
                prefs == null || prefs.isFancierGlassMotionLightEnabled(),
                prefs != null && prefs.isLazyModeEnabled(),
                power != null && power.isPowerSaveMode(),
                AnimationSpeed.multiplier(app));
        };
    }

    /** The gate's answer from its facts: 0 for no light, else {@code speedMultiplier}. */
    @VisibleForTesting
    static float multiplier(boolean animatorsEnabled, boolean reducedMotion, boolean settingOn,
                            boolean lazyMode, boolean powerSave, float speedMultiplier) {
        if (!animatorsEnabled || reducedMotion || !settingOn || lazyMode || powerSave) return 0f;
        return Math.max(0f, speedMultiplier);
    }
}
