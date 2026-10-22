package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;

import com.termux.app.TermuxActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.settings.LauncherRestartPolicy;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

/**
 * General: the launcher-wide switches (vibration, Recents), the way to Battery &amp; performance
 * ({@link PerformancePreferencesFragment}, which holds idle activity and the Low-memory preset), the
 * app's own pages (backup, permissions, diagnostics, about, help) and setting the launcher as the
 * home app. It grew out of App behavior and adds no preference of its own: every row writes the key
 * it always wrote, through the store that already owned it, so a value set here or on any older
 * page is the same.
 */
@Keep
public class GeneralPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setPreferenceDataStore(new BehaviorDataStore(context));
        setPreferencesFromResource(R.xml.general_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference setHome = findPreference("app_launcher_set_home");
        // The same chooser the tour's last card offers, so both doors behave the same way.
        if (setHome != null) setHome.setOnPreferenceClickListener(row -> {
            com.termux.app.HomeAppChooser.open(context);
            return true;
        });
        Preference restart = findPreference("app_launcher_restart");
        if (restart != null) restart.setOnPreferenceClickListener(row -> {
            confirmRestart(context);
            return true;
        });
        Preference help = findPreference("app_launcher_help");
        // An ordinary screen on top of this one: Back comes back to Settings.
        if (help != null) help.setOnPreferenceClickListener(row -> {
            startActivity(com.termux.app.help.HelpActivity.intent(context, null, null, null));
            return true;
        });
    }

    /**
     * Asks first, then owes the launcher a recreate on its next resume and steps out of Settings so
     * that resume is now. The process, and the shells in it, are not touched
     * ({@link LauncherRestartPolicy}).
     */
    private void confirmRestart(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_restart_launcher_title)
            .setMessage(R.string.settings_restart_launcher_confirm)
            .setPositiveButton(R.string.settings_restart_launcher_action, (dialog, which) -> {
                long now = SystemClock.elapsedRealtime();
                if (!LauncherRestartPolicy.accept(now, sLastRestartAtMs)) return;
                sLastRestartAtMs = now;
                TermuxActivity.requestTermuxActivityStylingOnNextResume(context.getApplicationContext(), true);
                if (getActivity() != null) getActivity().finish();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static long sLastRestartAtMs = -1L;

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_section_general);
        Context context = getContext();
        Preference transition = context == null ? null
            : findPreference(com.termux.app.launcher.home.HomeSettings.KEY_PAGE_TRANSITION);
        if (transition != null) transition.setSummary(HomePageTransitionFragment.titleOf(
            com.termux.app.launcher.home.HomeSettings.pageTransition(context)));
    }

    /** Keyboard vibration belongs to the keyboard's store; everything else to the launcher's. */
    private static final class BehaviorDataStore extends PreferenceDataStore {
        private static final String KEY_KEYBOARD_HAPTICS = "in_app_keyboard_haptics_enabled";
        @NonNull private final Context context;
        @NonNull private final TermuxStylePreferencesDataStore style;
        @NonNull private final KeyboardPreferencesDataStore keyboard;

        BehaviorDataStore(@NonNull Context context) {
            this.context = context;
            style = TermuxStylePreferencesDataStore.getInstance(context);
            keyboard = KeyboardPreferencesDataStore.getInstance(context);
        }

        @Override
        public void putBoolean(@Nullable String key, boolean value) {
            if (KEY_KEYBOARD_HAPTICS.equals(key)) keyboard.putBoolean(key, value);
            else style.putBoolean(key, value);
        }

        @Override
        public void putString(@Nullable String key, @Nullable String value) {
            if (com.termux.app.AnimationSpeed.KEY.equals(key)) com.termux.app.AnimationSpeed.set(context, value);
            else style.putString(key, value);
        }

        @Nullable
        @Override
        public String getString(@Nullable String key, @Nullable String defValue) {
            return com.termux.app.AnimationSpeed.KEY.equals(key) ? com.termux.app.AnimationSpeed.get(context)
                : style.getString(key, defValue);
        }

        @Override
        public boolean getBoolean(@Nullable String key, boolean defValue) {
            return KEY_KEYBOARD_HAPTICS.equals(key) ? keyboard.getBoolean(key, defValue)
                : style.getBoolean(key, defValue);
        }
    }
}
