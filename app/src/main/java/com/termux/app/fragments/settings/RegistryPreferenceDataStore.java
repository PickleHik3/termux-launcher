package com.termux.app.fragments.settings;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceScreen;

import com.termux.app.settings.LauncherSettings;
import com.termux.app.settings.LauncherSettingsAdapters;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A Settings screen's data store over the Settings registry. A row for a migrated key saves
 * through the registry, so the declaration clamps or refuses the value and the applier refreshes
 * the launcher as {@code launcherctl config set} does; any other key goes to the screen's old data
 * store unchanged. A key migrates by being in the row map and by deleting its case from the old
 * store. A refused value never reaches the store: {@link #bind} vetoes it on the row, so the row
 * keeps its old value, and the message says the allowed range.
 */
public final class RegistryPreferenceDataStore extends PreferenceDataStore {

    /** Where a refusal is shown. */
    public interface Refused {
        void show(@NonNull String message);
    }

    /** Settings row key to registry id, for every migrated row. Only ever grows with a screen. */
    @NonNull
    public static final Map<String, String> ROWS = Collections.emptyMap();

    @NonNull private final PreferenceDataStore mFallback;
    @NonNull private final SettingsRegistry mRegistry;
    @NonNull private final SettingsApplier mApplier;
    @NonNull private final Map<String, String> mRows;
    @NonNull private final Refused mRefused;

    public RegistryPreferenceDataStore(@NonNull PreferenceDataStore fallback,
                                       @NonNull SettingsRegistry registry,
                                       @NonNull SettingsApplier applier,
                                       @NonNull Map<String, String> rows,
                                       @NonNull Refused refused) {
        mFallback = fallback;
        mRegistry = registry;
        mApplier = applier;
        mRows = rows;
        mRefused = refused;
    }

    /** The device's wrapper over {@code fallback} for the rows in {@link #ROWS}. */
    @NonNull
    public static RegistryPreferenceDataStore forDevice(@NonNull Context context,
                                                        @NonNull PreferenceDataStore fallback) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        return new RegistryPreferenceDataStore(fallback,
            LauncherSettings.registry(app, c -> LauncherSettingsAdapters.forDevice(c,
                () -> TerminalActionDispatcher.getInstance().redressChrome())),
            LauncherSettings.applier(), ROWS,
            message -> Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Whether {@code newValue} may be saved to {@code key}: false (and the refusal shown) when the
     * registry refuses it, true for it and for every key the registry does not hold.
     */
    public boolean accepts(@NonNull String key, @Nullable Object newValue) {
        String id = mRows.get(key);
        if (id == null) return true;
        SettingsRegistry.Refusal refusal = mRegistry.check(id,
            newValue == null ? null : String.valueOf(newValue));
        if (refusal == null) return true;
        mRefused.show(refusal.reason);
        return false;
    }

    /** Puts {@link #accepts} on each migrated row of {@code screen}, in front of its own listener. */
    public void bind(@NonNull PreferenceScreen screen) {
        for (String key : mRows.keySet()) {
            Preference row = screen.findPreference(key);
            if (row == null) continue;
            Preference.OnPreferenceChangeListener own = row.getOnPreferenceChangeListener();
            row.setOnPreferenceChangeListener((preference, value) ->
                accepts(key, value) && (own == null || own.onPreferenceChange(preference, value)));
        }
    }

    private void save(@NonNull String id, @NonNull String value) {
        SettingsRegistry.Outcome outcome = mRegistry.set(id, value);
        SettingsRegistry.Refusal refusal = outcome.refusal();
        if (refusal != null) {
            mRefused.show(refusal.reason);
            return;
        }
        mApplier.apply(outcome.change());
    }

    @Override
    public void putString(String key, @Nullable String value) {
        String id = key == null ? null : mRows.get(key);
        if (id == null) mFallback.putString(key, value);
        else save(id, value == null ? "" : value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        String id = key == null ? null : mRows.get(key);
        if (id == null) return mFallback.getString(key, defValue);
        String value = mRegistry.get(id);
        return value == null ? defValue : value;
    }

    @Override
    public void putInt(String key, int value) {
        String id = key == null ? null : mRows.get(key);
        if (id == null) mFallback.putInt(key, value);
        else save(id, String.valueOf(value));
    }

    @Override
    public int getInt(String key, int defValue) {
        String id = key == null ? null : mRows.get(key);
        if (id == null) return mFallback.getInt(key, defValue);
        SettingDeclaration declaration = mRegistry.declaration(id);
        String value = mRegistry.get(id);
        if (declaration == null || value == null) return defValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        mFallback.putBoolean(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return mFallback.getBoolean(key, defValue);
    }

    @Override
    public void putLong(String key, long value) {
        mFallback.putLong(key, value);
    }

    @Override
    public long getLong(String key, long defValue) {
        return mFallback.getLong(key, defValue);
    }

    @Override
    public void putFloat(String key, float value) {
        mFallback.putFloat(key, value);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return mFallback.getFloat(key, defValue);
    }

    @Override
    public void putStringSet(String key, @Nullable Set<String> values) {
        mFallback.putStringSet(key, values);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return mFallback.getStringSet(key, defValues);
    }
}
