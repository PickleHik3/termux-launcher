package com.termux.app.help;

import android.util.Log;
import com.termux.BuildConfig;

/** The help layer's debug-only measurement and routing trail. */
public final class HelpLog {
    public static final String TAG = "TermuxHelp";
    public static void d(String message) { if (BuildConfig.DEBUG) Log.d(TAG, message); }
    private HelpLog() {}
}
