package com.termux.x11;

import android.os.SystemClock;
import android.util.Log;

import java.util.function.BooleanSupplier;

/**
 * DIAG(x11-keys): one tagged line for every key or text that reaches the X server from Java, and
 * for every system-IME input-connection call that can turn into DEL, arrows or replacement text.
 * Read with {@code adb logcat -s X11Keys}. Gated by the app's motion log flag through {@link #gate};
 * with it off each call site costs one volatile read and allocates nothing.
 */
public final class X11KeysDiag {
    public static final String TAG = "X11Keys";

    /** Set once by the app to its motion log flag; null means off. */
    public static volatile BooleanSupplier gate; // DIAG(x11-keys)

    private X11KeysDiag() {}

    public static boolean on() { // DIAG(x11-keys)
        BooleanSupplier g = gate; // DIAG(x11-keys)
        return g != null && g.getAsBoolean(); // DIAG(x11-keys)
    }

    /** Callers guard with {@link #on()}; {@code what} and {@code detail} are plain strings. */
    public static void log(String what, String detail) { // DIAG(x11-keys)
        Log.d(TAG, SystemClock.uptimeMillis() + " " + what + " " + detail + " <" + callers() + ">"); // DIAG(x11-keys)
    }

    private static String callers() { // DIAG(x11-keys)
        StackTraceElement[] t = new Throwable().getStackTrace(); // DIAG(x11-keys)
        StringBuilder b = new StringBuilder(); // DIAG(x11-keys)
        // Frames 0-2 are this class; show the next six.
        for (int i = 3; i < Math.min(t.length, 9); i++) { // DIAG(x11-keys)
            if (b.length() > 0) b.append(" < "); // DIAG(x11-keys)
            String c = t[i].getClassName(); // DIAG(x11-keys)
            b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(t[i].getMethodName()) // DIAG(x11-keys)
                .append(':').append(t[i].getLineNumber()); // DIAG(x11-keys)
        } // DIAG(x11-keys)
        return b.toString(); // DIAG(x11-keys)
    } // DIAG(x11-keys)
}
