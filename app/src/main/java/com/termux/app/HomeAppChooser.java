package com.termux.app;

import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.notice.AppNotice;

/**
 * The phone's own "which app is the home screen" chooser, opened the same way from wherever it is
 * offered — Settings, and the last card of the first run.
 *
 * <p>Three doors, because no two Android versions agree on which one exists: the home-screen
 * setting, the default-apps screen, and the role request. The user is told plainly when the phone
 * offers none of them.
 */
public final class HomeAppChooser {

    private HomeAppChooser() {}

    /** Opens whichever of the phone's doors to the home-app setting it has. */
    public static void open(@NonNull Context context) {
        if (start(context, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        if (start(context, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                && !roles.isRoleHeld(RoleManager.ROLE_HOME)
                && start(context, roles.createRequestRoleIntent(RoleManager.ROLE_HOME))) return;
        }
        AppNotice.show(context, R.string.termux_app_launcher_set_home_unavailable, false);
    }

    private static boolean start(@NonNull Context context, Intent intent) {
        if (intent == null) return false;
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
