package com.termux.app.wall.switcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.shared.logger.Logger;

import java.util.function.BooleanSupplier;

/**
 * The feel of the page switcher: a crisp double click when the dot is lifted for a move (this
 * gesture's alone), a light tick on crossing onto a cell and a firm click on choosing one.
 *
 * <p>On API 30 and later a {@link VibrationEffect.Composition} of the phone's own primitives,
 * where it supports them; otherwise the predefined effects (API 29), and on API 26-28 short
 * one-shots. Everything is played as touch feedback, so the system's own haptics setting silences
 * it, and the app's master haptics switch is asked first.
 */
public final class PickerHaptics {

    private static final String LOG_TAG = "PickerHaptics";

    /** Strengths are 0..1. */
    static final float CROSS_SCALE = 0.35f;
    static final float OPEN_SCALE_FIRST = 0.7f;
    static final float OPEN_SCALE_SECOND = 1.0f;
    static final int OPEN_GAP_MS = 55;
    static final float COMMIT_SCALE = 1.0f;

    @NonNull private final Context mContext;
    @NonNull private final BooleanSupplier mEnabled;
    @Nullable private Vibrator mVibrator;
    private boolean mResolved;
    /** Whether the phone has every primitive the compositions use. Asked once. */
    private boolean mPrimitives;

    public PickerHaptics(@NonNull Context context, @NonNull BooleanSupplier enabled) {
        mContext = context.getApplicationContext();
        mEnabled = enabled;
    }

    /** The dot was lifted for a move: a double click nothing else in the app uses. */
    public void pickedUp() {
        play(Kind.OPEN);
    }

    /** The finger crossed onto another cell. */
    public void crossed() {
        play(Kind.CROSS);
    }

    /** A cell was chosen. */
    public void committed() {
        play(Kind.COMMIT);
    }

    private enum Kind { OPEN, CROSS, COMMIT }

    private void play(Kind kind) {
        if (!mEnabled.getAsBoolean() || !Haptics.isEnabled(mContext)) return;
        Vibrator vibrator = vibrator();
        if (vibrator == null) return;
        try {
            VibrationEffect effect = effect(vibrator, kind);
            if (effect == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_TOUCH));
            } else {
                vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            }
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "haptic failed: " + e.getMessage());
        }
    }

    @Nullable
    private VibrationEffect effect(@NonNull Vibrator vibrator, @NonNull Kind kind) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mPrimitives) {
            VibrationEffect.Composition c = VibrationEffect.startComposition();
            switch (kind) {
                case OPEN:
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, OPEN_SCALE_FIRST);
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK,
                        OPEN_SCALE_SECOND, OPEN_GAP_MS);
                    break;
                case CROSS:
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, CROSS_SCALE);
                    break;
                default:
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, COMMIT_SCALE);
                    break;
            }
            return c.compose();
        }
        return fallback(kind);
    }

    /** What a phone without the primitives plays: predefined effects, then short one-shots. */
    @Nullable
    private static VibrationEffect fallback(@NonNull Kind kind) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switch (kind) {
                case OPEN: return VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_DOUBLE_CLICK);
                case CROSS: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
                default: return VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_HEAVY_CLICK);
            }
        }
        switch (kind) {
            case OPEN:
                return VibrationEffect.createWaveform(new long[] {0, 14, 55, 18},
                    new int[] {0, 180, 0, 255}, -1);
            case CROSS:
                return VibrationEffect.createOneShot(8, 70);
            default:
                return VibrationEffect.createOneShot(20, 255);
        }
    }

    @Nullable
    private Vibrator vibrator() {
        if (mResolved) return mVibrator;
        mResolved = true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = mContext.getSystemService(VibratorManager.class);
                mVibrator = manager == null ? null : manager.getDefaultVibrator();
            } else {
                mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (mVibrator != null && !mVibrator.hasVibrator()) mVibrator = null;
            if (mVibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mPrimitives = mVibrator.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_CLICK);
            }
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "no vibrator: " + e.getMessage());
            mVibrator = null;
        }
        return mVibrator;
    }
}
