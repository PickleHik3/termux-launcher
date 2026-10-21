package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.termux.PerformanceRowPolicy.Reason;
import com.termux.app.settings.LowMemoryPreset;
import com.termux.app.terminal.PaneRetroEffect;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Battery &amp; performance: the two presets, and the motion a weak phone can do without. It adds
 * no preference of its own: every row writes the key it always wrote, through the store that
 * already owned it, so a value set here, on Appearance or on Terminal is the same value.
 *
 * <p>A row a preset owns, or that the phone or the wallpaper cannot use, is greyed with the reason
 * under it ({@link PerformanceRowPolicy}). Idle reduction shows the glass off without writing it:
 * the launcher turns the glass off at runtime and the stored choice comes back with it.
 */
@Keep
public class PerformancePreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_LAZY_MODE = "lazy_mode";
    private static final String KEY_LOW_MEMORY_PRESET = "low_memory_preset";
    private static final String KEY_GLASS_REFRACTION = "fancier_glass";
    private static final String KEY_LIVING_GLASS = TermuxStylePreferencesDataStore.KEY_MOTION_LIGHT;
    private static final String KEY_WALLPAPER_PARALLAX = "wallpaper_parallax";
    /** The trail's on/off, which the preset and the Appearance editor write; not Terminal's style list. */
    private static final String KEY_CURSOR_TRAIL = "terminal_cursor_trail";
    private static final String KEY_TERMINAL_EFFECT = "terminal_retro_effect";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** Run after a switch has stored its new value, so the rows read what was written. */
    private final Runnable mRefreshRows = this::refreshRows;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setPreferenceDataStore(new PerformanceDataStore(context));
        setPreferencesFromResource(R.xml.performance_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference effect = findPreference(KEY_TERMINAL_EFFECT);
        if (effect != null) effect.setVisible(PaneRetroEffect.available());
        SwitchPreferenceCompat preset = findPreference(KEY_LOW_MEMORY_PRESET);
        if (preset != null) {
            preset.setOnPreferenceChangeListener((row, newValue) -> {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs == null) return false;
                if (Boolean.TRUE.equals(newValue)) LowMemoryPreset.turnOn(prefs);
                else LowMemoryPreset.turnOff(prefs);
                SwitchPreferenceCompat lazy = findPreference(KEY_LAZY_MODE);
                if (lazy != null) lazy.setChecked(prefs.isLazyModeEnabled());
                // The launcher restyles itself when the user returns to it.
                TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
                scheduleRefresh();
                return true;
            });
        }
        // The rows that other rows answer to.
        for (String key : new String[]{KEY_LAZY_MODE, KEY_GLASS_REFRACTION}) {
            Preference row = findPreference(key);
            if (row != null) row.setOnPreferenceChangeListener((changed, newValue) -> {
                scheduleRefresh();
                return true;
            });
        }
        refreshRows();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_performance);
        // The wallpaper and wallpaper mode are chosen on Appearance; the effect on Terminal.
        refreshRows();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mRefreshRows);
        super.onDestroy();
    }

    private void scheduleRefresh() {
        mHandler.removeCallbacks(mRefreshRows);
        mHandler.post(mRefreshRows);
    }

    /** Every row against what is stored now, with the reason any of them is greyed. */
    private void refreshRows() {
        Context context = getContext();
        if (context == null) return;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, true);
        if (prefs == null) return;
        PerformanceRowPolicy.Facts facts = new PerformanceRowPolicy.Facts(Build.VERSION.SDK_INT,
            prefs.isLazyModeEnabled(), prefs.isLowMemoryPresetEnabled(),
            WallpaperPictureReader.managedPictureOnScreen(context, prefs),
            prefs.isFancierGlassEnabled(), prefs.isUseSystemWallpaperEnabled());
        showSwitch(KEY_LAZY_MODE, prefs.isLazyModeEnabled(), Reason.NONE,
            R.string.settings_behavior_idle_summary);
        showSwitch(KEY_LOW_MEMORY_PRESET, prefs.isLowMemoryPresetEnabled(), Reason.NONE,
            R.string.settings_behavior_low_memory_summary);
        showSwitch(KEY_GLASS_REFRACTION, prefs.isFancierGlassEnabled(),
            PerformanceRowPolicy.glassRefraction(facts),
            R.string.settings_performance_glass_refraction_summary);
        showSwitch(KEY_LIVING_GLASS, prefs.isFancierGlassMotionLightEnabled(),
            PerformanceRowPolicy.livingGlass(facts), R.string.settings_performance_living_glass_summary);
        showSwitch(KEY_WALLPAPER_PARALLAX, prefs.isWallpaperParallaxEnabled(),
            PerformanceRowPolicy.wallpaperParallax(facts), R.string.settings_performance_parallax_summary);
        showSwitch(KEY_CURSOR_TRAIL, prefs.isTerminalCursorTrailEnabled(),
            PerformanceRowPolicy.cursorTrail(facts), R.string.settings_performance_cursor_trail_summary);
        ListPreference effect = findPreference(KEY_TERMINAL_EFFECT);
        if (effect != null) {
            Reason reason = PerformanceRowPolicy.terminalEffect(facts);
            effect.setEnabled(reason.enabled());
            String stored = prefs.getTerminalRetroEffect();
            if (stored != null && !stored.equals(effect.getValue())) effect.setValue(stored);
            // A list shows no value of its own, so a greyed one names the choice the preset wrote.
            CharSequence entry = effect.getEntry();
            effect.setSummary(reason == Reason.SET_BY_LOW_MEMORY_PRESET && entry != null
                ? getString(R.string.settings_performance_set_by_preset_value, entry)
                : summary(reason, R.string.settings_performance_terminal_effect_summary));
        }
    }

    private void showSwitch(@NonNull String key, boolean stored, @NonNull Reason reason,
                            @StringRes int normalSummary) {
        SwitchPreferenceCompat row = findPreference(key);
        if (row == null) return;
        row.setEnabled(reason.enabled());
        row.setSummary(summary(reason, normalSummary));
        // A row shown off is not persisted while it is, so the stored choice is left alone; once
        // persisting again, setting it back writes only the value that is already stored.
        row.setPersistent(!reason.shownOff());
        boolean shown = !reason.shownOff() && stored;
        if (row.isChecked() != shown) row.setChecked(shown);
    }

    @NonNull
    private String summary(@NonNull Reason reason, @StringRes int normalSummary) {
        switch (reason) {
            case SET_BY_LOW_MEMORY_PRESET: return getString(R.string.settings_performance_set_by_preset);
            case OFF_WHILE_IDLE_REDUCED: return getString(R.string.settings_performance_off_while_idle);
            case NEEDS_ANDROID_13: return getString(R.string.settings_performance_needs_android_13);
            case NEEDS_PICKED_WALLPAPER: return getString(R.string.settings_performance_needs_picked_wallpaper);
            case NEEDS_GLASS_REFRACTION: return getString(R.string.settings_performance_needs_refraction);
            case NEEDS_WALLPAPER: return getString(R.string.settings_performance_needs_wallpaper);
            default: return getString(normalSummary);
        }
    }

    /**
     * Idle activity and Glass refraction belong to the terminal I/O store, the effect to the
     * terminal view's, the preset's switch to the preferences themselves (its change listener has
     * already applied or undone it), the trail's switch to the preferences too (no store owns it:
     * the Appearance editor writes it in the launcher), and the rest to the launcher's.
     */
    private static final class PerformanceDataStore extends PreferenceDataStore {
        @NonNull private final Context context;
        @NonNull private final TerminalIOPreferencesDataStore io;
        @NonNull private final TerminalViewPreferencesDataStore view;
        @NonNull private final TermuxStylePreferencesDataStore style;

        PerformanceDataStore(@NonNull Context context) {
            this.context = context;
            io = TerminalIOPreferencesDataStore.getInstance(context);
            view = TerminalViewPreferencesDataStore.getInstance(context);
            style = TermuxStylePreferencesDataStore.getInstance(context);
        }

        private static boolean isIoKey(@Nullable String key) {
            return KEY_LAZY_MODE.equals(key) || KEY_GLASS_REFRACTION.equals(key);
        }

        @Override
        public void putBoolean(@Nullable String key, boolean value) {
            if (isIoKey(key)) io.putBoolean(key, value);
            else if (KEY_LOW_MEMORY_PRESET.equals(key)) {
                // turnOn and turnOff, run by the switch's change listener, have set it already.
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs != null) prefs.setLowMemoryPresetEnabled(value);
            } else if (KEY_CURSOR_TRAIL.equals(key)) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs == null) return;
                prefs.setTerminalCursorTrailEnabled(value);
                // The launcher re-reads the trail into its focused pane when it resumes.
                TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
            } else style.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(@Nullable String key, boolean defValue) {
            if (isIoKey(key)) return io.getBoolean(key, defValue);
            if (KEY_LOW_MEMORY_PRESET.equals(key)) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                return prefs == null ? defValue : prefs.isLowMemoryPresetEnabled();
            }
            if (KEY_CURSOR_TRAIL.equals(key)) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                return prefs == null ? defValue : prefs.isTerminalCursorTrailEnabled();
            }
            return style.getBoolean(key, defValue);
        }

        @Override
        public void putString(@Nullable String key, @Nullable String value) {
            if (KEY_TERMINAL_EFFECT.equals(key)) view.putString(key, value);
        }

        @Override
        public String getString(@Nullable String key, @Nullable String defValue) {
            return KEY_TERMINAL_EFFECT.equals(key) ? view.getString(key, defValue) : defValue;
        }
    }
}
