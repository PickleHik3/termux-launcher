package com.termux.app.haptics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;

import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The one place the app's haptics go through, so a single "Haptic feedback" setting can silence
 * all of them. Per-feature switches (row ticks, key haptics) stay as they are and are ANDed with
 * this master; callers that already have their own switch check it first and then call here.
 */
public final class Haptics {

    private static final String LOG_TAG = "Haptics";
    private static final long MIN_DURATION_MS = 1L;
    private static final long MAX_DURATION_MS = 5000L;

    private Haptics() {}

    /** Whether the master "haptic feedback" setting is on (default on). */
    public static boolean isEnabled(@Nullable Context context) {
        if (context == null) return true;
        SharedPreferences preferences = SharedPreferenceUtils.getPrivateSharedPreferences(context,
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION);
        return SharedPreferenceUtils.getBoolean(preferences,
            TERMUX_APP.KEY_APP_HAPTICS_ENABLED, TERMUX_APP.DEFAULT_APP_HAPTICS_ENABLED);
    }

    /** A view haptic; a no-op when the master setting is off. */
    public static void tick(@Nullable View view, int hapticFeedbackConstant) {
        if (view == null || !isEnabled(view.getContext())) return;
        view.performHapticFeedback(hapticFeedbackConstant);
    }

    /** A one-shot vibration; a no-op when the master setting is off. */
    public static void vibrate(Context context, long durationMs) {
        vibrate(context, durationMs, false);
    }

    /**
     * A one-shot vibration, clamped to 1..5000 ms. {@code force} ignores the master setting, for
     * vibrations a shell command asked for by name.
     */
    public static void vibrate(Context context, long durationMs, boolean force) {
        if (context == null || (!force && !isEnabled(context))) return;
        long clamped = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));
        try {
            Context appContext = context.getApplicationContext();
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = appContext.getSystemService(VibratorManager.class);
                vibrator = manager == null ? null : manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            vibrator.vibrate(VibrationEffect.createOneShot(clamped, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "vibrate failed: " + e.getMessage());
        }
    }
}
