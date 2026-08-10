package com.termux.app.fragments.settings.termux;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiSettings;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.StatusActionPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;

/** Connected-service health and all user-facing permissions in one destination. */
@Keep
public final class ServicesPermissionsPreferencesFragment extends MaterialPreferenceFragment {
    private static final String STORAGE = "app_launcher_storage_access";
    private static final String WALLPAPER = "app_launcher_wallpaper_access";
    private static final int REQUEST_WALLPAPER_READ = 4713;
    private static final String NOTIFICATION_ACCESS = "app_launcher_notification_access";
    private static final String ACCESSIBILITY = "app_launcher_accessibility_lock_access";
    private static final String NOTIFICATIONS = "app_launcher_notification_settings";
    private static final String OTHER = "app_launcher_app_permissions";

    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.services_permissions_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        bindPermissionActions(context);
        Preference other = findPreference(OTHER);
        if (other instanceof StatusActionPreference) ((StatusActionPreference) other).setState(
            getString(R.string.settings_status_available), getString(R.string.settings_manage_action),
            StatusActionPreference.Tone.NEUTRAL);
        configureTermuxApi(context);
        refresh(context);
    }

    @Override public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_services_permissions);
        Context context = getContext();
        if (context != null) { configureTermuxApi(context); refresh(context); }
    }

    private void refresh(@NonNull Context context) {
        refreshTai(context);
        refreshShizuku();
        setPermission(STORAGE, PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(context, -1, true, false));
        refreshWallpaperRead(context);
        setPermission(NOTIFICATION_ACCESS, LauncherNotificationAccess.isEnabled(context));
        setPermission(ACCESSIBILITY, LauncherLockAccessibilityAccess.isEnabled(context));
        setPermission(NOTIFICATIONS, NotificationManagerCompat.from(context).areNotificationsEnabled());
    }

    private void refreshTai(@NonNull Context context) {
        Preference row = findPreference("service_tai");
        if (!(row instanceof StatusActionPreference)) return;
        TaiManager manager = TaiManager.getInstance(context);
        String model = new TaiSettings(context).getDefaultAssistantModel();
        boolean configured = model != null && !model.isEmpty() && manager.isModelAvailable(model);
        ((StatusActionPreference) row).setState(getString(configured ? R.string.settings_status_stopped
            : R.string.settings_status_not_configured), getString(R.string.settings_manage_action),
            StatusActionPreference.Tone.NEUTRAL);
        new Thread(() -> {
            boolean running = manager.getRuntimeState().loaded;
            if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() -> {
                Preference current = findPreference("service_tai");
                if (current instanceof StatusActionPreference) ((StatusActionPreference) current).setState(
                    getString(running ? R.string.settings_status_running : configured
                        ? R.string.settings_status_stopped : R.string.settings_status_not_configured),
                    getString(R.string.settings_manage_action), running
                        ? StatusActionPreference.Tone.POSITIVE : StatusActionPreference.Tone.NEUTRAL);
            });
        }).start();
    }

    private void refreshShizuku() {
        Preference row = findPreference("service_shizuku");
        if (!(row instanceof StatusActionPreference)) return;
        PrivilegedBackendManager.BackendState state = PrivilegedBackendManager.getInstance().getBackendState();
        boolean connected = state == PrivilegedBackendManager.BackendState.READY
            || state == PrivilegedBackendManager.BackendState.FALLBACK_SHELL;
        boolean permission = state == PrivilegedBackendManager.BackendState.PERMISSION_DENIED;
        ((StatusActionPreference) row).setState(getString(connected ? R.string.settings_status_connected
            : permission ? R.string.settings_status_permission_required : R.string.settings_status_unavailable),
            getString(connected ? R.string.settings_manage_action : R.string.settings_connect_action),
            connected ? StatusActionPreference.Tone.POSITIVE : permission
                ? StatusActionPreference.Tone.WARNING : StatusActionPreference.Tone.ERROR);
    }

    private void configureTermuxApi(@NonNull Context context) {
        Preference row = findPreference("service_termux_api");
        if (!(row instanceof StatusActionPreference)) return;
        boolean installed = TermuxAPIAppSharedPreferences.build(context, false) != null;
        ((StatusActionPreference) row).setState(getString(installed ? R.string.settings_status_installed
            : R.string.settings_status_not_installed), getString(installed
                ? R.string.settings_configure_action : R.string.settings_install_action),
            installed ? StatusActionPreference.Tone.POSITIVE : StatusActionPreference.Tone.WARNING);
        if (installed) {
            row.setFragment("com.termux.app.fragments.settings.TermuxAPIPreferencesFragment");
            row.setSummary(R.string.settings_service_configure_summary);
            row.setOnPreferenceClickListener(null);
        } else {
            row.setFragment(null);
            row.setSummary(R.string.settings_termux_api_install_summary);
            row.setOnPreferenceClickListener(preference -> {
                ShareUtils.openUrl(context, TermuxConstants.TERMUX_API_FDROID_PACKAGE_URL);
                return true;
            });
        }
    }

    private void bindPermissionActions(@NonNull Context context) {
        click(STORAGE, preference -> { startActivity(new Intent(context, TermuxActivity.class)
            .setAction(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS)); return true; });
        click(WALLPAPER, preference -> {
            // Once granted, the only way back out is the system screen — the app cannot revoke it.
            if (PermissionUtils.checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE))
                start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.getPackageName())));
            else
                requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_WALLPAPER_READ);
            return true;
        });
        // The per-app detail screen only exists from API 30; older versions need the list.
        click(NOTIFICATION_ACCESS, preference -> {
            if (!start(LauncherNotificationAccess.detailSettingsIntent(context)))
                start(LauncherNotificationAccess.listSettingsIntent());
            return true;
        });
        click(ACCESSIBILITY, preference -> { start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return true; });
        click(NOTIFICATIONS, preference -> { start(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())); return true; });
        click(OTHER, preference -> { start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.getPackageName()))); return true; });
    }

    /**
     * The glass bands blur a crop of the system wallpaper, and on devices that still gate the
     * {@code WallpaperManager} read behind legacy storage access the read fails without this
     * permission. "Files and media" above prioritizes all-files access, which does not imply it —
     * so an install can read "Allowed" there while the blur has nothing to work with. Own row.
     */
    private void refreshWallpaperRead(@NonNull Context context) {
        boolean allowed = PermissionUtils.checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        setPermission(WALLPAPER, allowed);
        Preference row = findPreference(WALLPAPER);
        if (row != null) row.setSummary(allowed ? R.string.settings_wallpaper_access_allowed_summary
            : R.string.settings_wallpaper_access_fix_summary);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Context context = getContext();
        if (requestCode == REQUEST_WALLPAPER_READ && context != null) refreshWallpaperRead(context);
    }

    private void click(String key, Preference.OnPreferenceClickListener listener) {
        Preference preference = findPreference(key);
        if (preference != null) preference.setOnPreferenceClickListener(listener);
    }

    private void setPermission(String key, boolean allowed) {
        Preference row = findPreference(key);
        if (!(row instanceof StatusActionPreference)) return;
        ((StatusActionPreference) row).setState(getString(allowed ? R.string.settings_status_allowed
            : R.string.settings_status_action_needed), getString(allowed
                ? R.string.settings_manage_action : R.string.settings_fix_action),
            allowed ? StatusActionPreference.Tone.POSITIVE : StatusActionPreference.Tone.WARNING);
        row.setSummary(allowed ? R.string.settings_permission_allowed_summary : R.string.settings_permission_fix_summary);
    }

    private boolean start(Intent intent) {
        try { startActivity(intent); return true; }
        catch (ActivityNotFoundException | SecurityException ignored) { return false; }
    }
}
