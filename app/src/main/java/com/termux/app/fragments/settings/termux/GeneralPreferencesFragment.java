package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;

import com.termux.app.TermuxActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.settings.LauncherRestartPolicy;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

/**
 * General: Use as and Start in (the Launcher group, which Use as Terminal only cannot take away
 * as it takes Home's section), the launcher-wide switches (vibration, Recents), the way to
 * Battery &amp; performance
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
        // Start in's choices follow Use as the moment it moves: no Home under Terminal only.
        Preference useAs = findPreference(KEY_USE_AS);
        if (useAs != null) useAs.setOnPreferenceChangeListener((row, value) -> {
            offerStartIn(com.termux.app.wall.UseAs.read(value == null ? null : value.toString()));
            return true;
        });
        offerStartIn(com.termux.app.settings.UseAsSetting.read(context));
        Preference setHome = findPreference("app_launcher_set_home");
        // The same chooser the tour's last card offers, so both doors behave the same way.
        if (setHome != null) setHome.setOnPreferenceClickListener(row -> {
            com.termux.app.HomeAppChooser.open(context);
            return true;
        });
        Preference transition = findPreference(
            com.termux.app.launcher.home.HomeSettings.KEY_PAGE_TRANSITION);
        // The choice lives on the Appearance editor's Home card; this row opens it there.
        if (transition != null) transition.setOnPreferenceClickListener(row -> {
            SettingsEditorDoors.openEditor(this, row, com.termux.app.surfaces.EditorDoor.SETTINGS_HOME);
            return true;
        });
        Preference restart = findPreference("app_launcher_restart");
        if (restart != null) restart.setOnPreferenceClickListener(row -> {
            confirmRestart(context);
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

    /** The keys this page binds in code. */
    private static final String KEY_USE_AS = "use_as";
    private static final String KEY_START_IN = "app_launcher_start_in";

    /**
     * Start in's choices: Home only while Use as is Home screen ({@link
     * com.termux.app.wall.UseAs#startInChoices}), Display only on a build that has a display. The
     * shown value is Start in as the launcher reads it, so Terminal only shows Terminal where
     * Home was stored; the stored value is left for a return to Home screen.
     */
    private void offerStartIn(@NonNull com.termux.app.wall.UseAs useAs) {
        ListPreference startIn = findPreference(KEY_START_IN);
        Context context = getContext();
        if (startIn == null || context == null) return;
        CharSequence[] entries = context.getResources().getTextArray(R.array.settings_start_in_entries);
        CharSequence[] values = context.getResources().getTextArray(R.array.settings_start_in_values);
        int offered = Math.min(entries.length, values.length);
        // Display is the list's last entry, and only a build with a display offers it.
        if (!com.termux.app.x11.X11ServerSupport.isAvailable(context) && offered >= 3) offered = 2;
        java.util.List<String> valueList = new java.util.ArrayList<>(offered);
        for (int i = 0; i < offered; i++) valueList.add(values[i].toString());
        java.util.List<Integer> kept = useAs.startInChoices(valueList);
        CharSequence[] keptEntries = new CharSequence[kept.size()];
        CharSequence[] keptValues = new CharSequence[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            keptEntries[i] = entries[kept.get(i)];
            keptValues[i] = values[kept.get(i)];
        }
        startIn.setEntries(keptEntries);
        startIn.setEntryValues(keptValues);
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences prefs =
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(context);
        if (prefs == null) return;
        String shown = useAs.startIn(prefs.getLauncherStartIn());
        // setValue would write it; the stored Home stays for a return to Home screen. The list's
        // summary reads the value it holds, so it is set without persisting.
        boolean persistent = startIn.isPersistent();
        startIn.setPersistent(false);
        startIn.setValue(shown);
        startIn.setPersistent(persistent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_section_general);
        Context context = getContext();
        if (context != null) offerStartIn(com.termux.app.settings.UseAsSetting.read(context));
        Preference transition = context == null ? null
            : findPreference(com.termux.app.launcher.home.HomeSettings.KEY_PAGE_TRANSITION);
        if (transition != null) transition.setSummary(titleOfTransition(
            com.termux.app.launcher.home.HomeSettings.pageTransition(context)));
    }

    @androidx.annotation.StringRes
    private static int titleOfTransition(@NonNull com.termux.app.launcher.home.HomePageTransition effect) {
        switch (effect) {
            case DEPTH: return R.string.settings_home_page_transition_depth;
            case FADE: return R.string.settings_home_page_transition_fade;
            case CAROUSEL: return R.string.settings_home_page_transition_carousel;
            case STACK: return R.string.settings_home_page_transition_stack;
            case SLIDE:
            default: return R.string.settings_home_page_transition_slide;
        }
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
