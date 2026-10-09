package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.launcher.LauncherUseCaseMode;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * What follows the Linux display switch wherever it is moved from — Settings → Display, the usage
 * mode picker, the first-run card, the Display place's own button.
 *
 * <p>The switch itself is one preference; what has to happen around it was spread over the
 * settings data store and the activity, and the settings copy was never reached because the
 * Display page keeps a data store of its own. One place now: the prefix commands go in with the
 * feature and come back out with it, the drawer re-lists Linux apps (it shows them only while
 * the display is on), and the usage mode follows between its two home presets. The page on the
 * wall and the server connection behind it are the activity's, attached or torn down when it
 * next reconciles; a running server is never stopped from here.
 */
public final class X11DisplaySwitch {

    private X11DisplaySwitch() {}

    /**
     * Writes the switch and everything that follows it.
     *
     * @return whether the switch actually moved
     */
    public static boolean write(@NonNull Context context,
                                @NonNull TermuxAppSharedPreferences preferences, boolean enabled) {
        if (!com.termux.BuildConfig.X11_SERVER && enabled) return false;
        if (preferences.isX11DisplayEnabled() == enabled) return false;
        preferences.setX11DisplayEnabled(enabled);
        onWritten(context, preferences, enabled);
        return true;
    }

    /** The side effects alone, for a caller that already wrote the switch (the mode preset). */
    public static void onWritten(@NonNull Context context,
                                 @NonNull TermuxAppSharedPreferences preferences, boolean enabled) {
        LauncherUseCaseMode.onDisplaySwitched(preferences, enabled,
            LauncherUseCaseMode.isDisplayOffered());
        if (enabled) X11Defaults.applyOnce(context);
        else X11CliInstaller.uninstallAsync(context);
        LauncherAppDataProvider.getInstance(context).refreshAsync(null, null);
    }
}
