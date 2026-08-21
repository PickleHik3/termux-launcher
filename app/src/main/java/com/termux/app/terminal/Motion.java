package com.termux.app.terminal;

import android.os.Build;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * The app's shared motion vocabulary (see the motion spec). Three easing classes cover
 * everything: {@link #settle()} for travel and arrivals, {@link
 * PaneMotionOverlayView#standardInterpolator()} for layout geometry and state crossfades, and
 * linear time for any alpha that rides along eased travel — easing shapes distance, never fades.
 */
public final class Motion {

    private Motion() { }

    /**
     * Travel/arrival curve, shaped like a critically damped spring (niri's motion): a gentle
     * take-off (~2% in the first 60 fps frame — the surface visibly accelerates rather than
     * teleporting), a fast middle, and a long soft landing. The original (0.16, 1, 0.3, 1)
     * dumped ~24% of the travel into the first frame, which read as a snap however long the
     * duration was.
     */
    public static Interpolator settle() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new android.view.animation.PathInterpolator(0.3f, 0.2f, 0.05f, 1f)
            : new DecelerateInterpolator(1.8f);
    }

    /** Float/dock depth ramp: bounds (FLIP) and elevation move together on this clock. */
    public static final long FLOAT_DEPTH_MS = 320L;
}
