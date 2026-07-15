package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
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

@Keep
public class TerminalViewPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalViewPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
    }
}

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
            case "activity_finish_remove_task":
                mPreferences.setRemoveTaskOnActivityFinishEnabled(value);
                break;
            default:
                break;
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
            case "activity_finish_remove_task":
                return mPreferences.isRemoveTaskOnActivityFinishEnabled();
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
