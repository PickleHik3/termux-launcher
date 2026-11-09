package com.termux.app.fragments.settings.termux;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;
import com.termux.app.TermuxActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

class TerminalViewPreferencesDataStore extends PreferenceDataStore {

    private static final String LOG_TAG = "TerminalViewPreferences";

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalViewPreferencesDataStore mInstance;

    private TerminalViewPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalViewPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalViewPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case TermuxPropertyConstants.KEY_USE_FULLSCREEN:
                writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_USE_FULLSCREEN, Boolean.toString(value));
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
                break;
            case "terminal_margin_adjustment":
                mPreferences.setTerminalMarginAdjustment(value);
                break;
            case "terminal_screen_events":
                // Read by each pane when an event fires; nothing to restyle.
                mPreferences.setTerminalScreenEventsEnabled(value);
                break;
            case "terminal_retro_composite":
                // Each pane re-reads it when its window shows again (PaneContentFrame#refreshAmbient).
                mPreferences.setTerminalRetroCompositeEnabled(value);
                break;
            case "idle_animations":
                // Each pane re-reads it when its window shows again (PaneContentFrame#refreshAmbient).
                mPreferences.setIdleAnimationsEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null || key == null) return;
        switch (key) {
            case "terminal_cursor_trail_style":
                mPreferences.setTerminalCursorTrailStyle(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
                break;
            case "terminal_engine":
                // Read when a pane is opened; open panes keep their engine.
                mPreferences.setTerminalEngine(value);
                break;
            case "terminal_retro_effect":
                mPreferences.setTerminalRetroEffect(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
                break;
            case "idle_tear_rate":
                // Each pane re-reads it when its ambient loop next starts (RetroEffectBinder).
                mPreferences.setIdleTearRate(value);
                break;
            default:
                break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null || key == null) return defValue;
        switch (key) {
            case "terminal_cursor_trail_style":
                return mPreferences.getTerminalCursorTrailStyle();
            case "terminal_engine":
                return mPreferences.getTerminalEngine();
            case "terminal_retro_effect":
                return mPreferences.getTerminalRetroEffect();
            case "idle_tear_rate":
                return mPreferences.getIdleTearRate();
            default:
                return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch(key) {
            case TermuxPropertyConstants.KEY_USE_FULLSCREEN:
                return Boolean.parseBoolean(loadTermuxProperties().getProperty(
                    TermuxPropertyConstants.KEY_USE_FULLSCREEN, Boolean.toString(defValue)));
            case "terminal_margin_adjustment":
                return mPreferences.isTerminalMarginAdjustmentEnabled();
            case "terminal_screen_events":
                return mPreferences.isTerminalScreenEventsEnabled();
            case "terminal_retro_composite":
                return mPreferences.isTerminalRetroCompositeEnabled();
            case "idle_animations":
                return mPreferences.isIdleAnimationsEnabled();
            default:
                return defValue;
        }
    }

    private Properties loadTermuxProperties() {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        Properties properties = SharedProperties.getPropertiesFromFile(mContext, propertiesFile, null);
        return properties == null ? new Properties() : properties;
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        File parentDir = propertiesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        List<String> lines = new ArrayList<>();
        boolean updated = false;
        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && trimmed.matches("^\\s*" + propertyKey + "\\s*=.*$")) {
                        lines.add(propertyKey + "=" + propertyValue);
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read termux.properties", e);
            }
        }
        if (!updated) {
            lines.add(propertyKey + "=" + propertyValue);
        }
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            output.append(line).append('\n');
        }
        FileUtils.writeTextToFile("termux.properties", propertiesFile.getAbsolutePath(), StandardCharsets.UTF_8, output.toString(), false);
    }
}
