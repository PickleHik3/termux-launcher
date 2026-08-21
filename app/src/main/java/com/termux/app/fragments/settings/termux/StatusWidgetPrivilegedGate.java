package com.termux.app.fragments.settings.termux;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;
import com.termux.privileged.PrivilegedBackendManager;

/**
 * The check the CPU and RAM status widgets pass through before they can be switched on.
 *
 * <p>Both widgets are far better with the privileged backend connected: it is what supplies
 * per-core CPU, detailed memory and the top-process list. Without it the CPU figure falls back to a
 * direct {@code /proc} read that a number of ROMs — Nothing OS among them — deny outright, which is
 * how the widget ends up switched on and permanently blank. So rather than let the user find that
 * out by looking at an empty readout, turning either widget on asks about Shizuku first, and points
 * at the guide when it is not there.
 *
 * <p>It is a checkpoint, not a wall. The user can still turn the widget on unprivileged from the
 * same dialog — RAM in particular works fine that way — they just do it knowing what they will get.
 */
final class StatusWidgetPrivilegedGate {

    /** The Permissions page of the project wiki, which is where Shizuku setup is written up. */
    private static final String SHIZUKU_GUIDE_URL =
        "https://picklehik3.github.io/termux-launcher-site/#wiki/launcherctl";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private StatusWidgetPrivilegedGate() {}

    /**
     * Wires the gate onto a status-widget switch.
     *
     * <p>Only enabling is gated; switching the widget back off is always allowed and never asks
     * anything.
     */
    static void attach(@NonNull Context context, SwitchPreferenceCompat preference) {
        if (preference == null) return;
        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            if (!(newValue instanceof Boolean) || !((Boolean) newValue)) return true;
            if (PrivilegedBackendManager.getInstance().isPrivilegedAvailable()) return true;
            // The backend is not initialised on app start, so "not available" this early may only
            // mean "not asked yet". Bring it up before deciding, and flip the switch from the
            // answer rather than from this call.
            PrivilegedBackendManager.getInstance().initializeIfNeeded(context)
                .thenAccept(ignored -> MAIN.post(() -> {
                    if (PrivilegedBackendManager.getInstance().isPrivilegedAvailable()) {
                        preference.setChecked(true);
                    } else {
                        promptForShizuku(context, preference);
                    }
                }));
            return false;
        });
    }

    private static void promptForShizuku(@NonNull Context context,
                                         @NonNull SwitchPreferenceCompat preference) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.status_widget_needs_shizuku_title)
            .setMessage(context.getString(R.string.status_widget_needs_shizuku_message,
                preference.getTitle()))
            .setPositiveButton(R.string.status_widget_needs_shizuku_guide,
                (dialog, which) -> openGuide(context))
            .setNeutralButton(R.string.status_widget_needs_shizuku_connect,
                (dialog, which) -> openPrivilegedAccessSettings(context))
            .setNegativeButton(R.string.status_widget_needs_shizuku_enable_anyway,
                (dialog, which) -> preference.setChecked(true))
            .show();
    }

    private static void openGuide(@NonNull Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_GUIDE_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppNotice.show(context, SHIZUKU_GUIDE_URL, true);
        }
    }

    private static void openPrivilegedAccessSettings(@NonNull Context context) {
        Intent intent = SettingsActivity.createFragmentIntent(context,
            PrivilegedAccessPreferencesFragment.class,
            R.string.termux_privileged_access_preferences_title);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
