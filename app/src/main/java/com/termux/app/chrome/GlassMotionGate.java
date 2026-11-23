package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.PowerManager;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.AnimationSpeed;
import com.termux.app.ReducedMotion;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Whether glass may answer its page's pose ({@link GlassPose}), asked as a slab leaves rest,
 * never per frame: the rule every Fancier Glass motion follows (the phone's animations on, no
 * reduced motion, no lazy mode, no battery saver) and Animation speed, whose Off keeps the glass
 * flat. Fancier Glass itself is the caller's: only a surface drawing through the program has
 * glass to move.
 */
public final class GlassMotionGate {

    private GlassMotionGate() {}

    /** Whether the glass may move, asked as a pose leaves rest. */
    public interface Gate {
        boolean allowed();
    }

    @Nullable private static Shared sShared;

    /**
     * The gate for surfaces in {@code context}'s app: one per process, whose answer is shared by
     * every slab that asks within one frame, so a page of glass leaving rest together reads the
     * settings once.
     */
    @NonNull
    public static Gate of(@NonNull Context context) {
        Shared shared = sShared;
        if (shared == null) {
            Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
            shared = new Shared(app);
            sShared = shared;
        }
        return shared;
    }

    private static final class Shared implements Gate {
        @NonNull private final Context mApp;
        private long mAskedAtMs = Long.MIN_VALUE;
        private boolean mAnswer;

        Shared(@NonNull Context app) {
            mApp = app;
        }

        @Override
        public boolean allowed() {
            long now = AnimationUtils.currentAnimationTimeMillis();
            if (now == mAskedAtMs) return mAnswer;
            mAskedAtMs = now;
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mApp);
            PowerManager power = mApp.getSystemService(PowerManager.class);
            mAnswer = GlassMotionGate.allowed(ValueAnimator.areAnimatorsEnabled(), ReducedMotion.isEnabled(mApp),
                prefs != null && prefs.isLazyModeEnabled(),
                power != null && power.isPowerSaveMode(),
                AnimationSpeed.multiplier(mApp));
            return mAnswer;
        }
    }

    /** The gate's answer from its facts: false keeps the glass flat. */
    @VisibleForTesting
    static boolean allowed(boolean animatorsEnabled, boolean reducedMotion, boolean lazyMode,
                           boolean powerSave, float speedMultiplier) {
        if (!animatorsEnabled || reducedMotion || lazyMode || powerSave) return false;
        return speedMultiplier > 0f;
    }
}
