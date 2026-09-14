package com.termux.app.tour;

import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;

/**
 * The run's debug trail, under one tag.
 *
 * <p>A card that cleared itself is almost impossible to report from a phone — by the time the user
 * has noticed, the card that did it is gone. Every card shown, every signal the chrome sent, and
 * every reason a glow could not be measured is written here instead, so a device pass can say
 * which signal cleared which card rather than that some of them did.
 *
 * <p>Debug builds only: this is chatty by design and a release build carries none of it.
 */
public final class TourLog {

    public static final String TAG = "TermuxTour";

    public static boolean enabled() {
        return BuildConfig.DEBUG;
    }

    public static void d(@NonNull String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }

    /** A rect as the log wants it: short, and honest about being absent. */
    @NonNull
    public static String describe(@Nullable Rect rect) {
        if (rect == null) return "none";
        return rect.left + "," + rect.top + " " + rect.width() + "x" + rect.height();
    }

    private TourLog() {}
}
