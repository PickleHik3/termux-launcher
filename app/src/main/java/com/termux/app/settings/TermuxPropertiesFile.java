package com.termux.app.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Single-property read/write against {@code termux.properties}, kept in one place so the settings
 * data store and the extra-keys row editor cannot drift on how they touch the user's file.
 *
 * <p>Writes replace the matching uncommented line in place and append only when the key is absent,
 * so comments, ordering and every unrelated setting survive an in-app edit.
 */
public final class TermuxPropertiesFile {

    private static final String LOG_TAG = "TermuxPropertiesFile";

    private TermuxPropertiesFile() {}

    @NonNull
    public static File resolveFile() {
        File file = SharedProperties.getPropertiesFileFromList(
            TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        return file == null ? TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE : file;
    }

    @NonNull
    public static Properties load(@NonNull Context context) {
        Properties properties = SharedProperties.getPropertiesFromFile(context, resolveFile(), null);
        return properties == null ? new Properties() : properties;
    }

    public static void write(@NonNull String propertyKey, @NonNull String propertyValue) {
        File propertiesFile = resolveFile();
        File parentDir = propertiesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        List<String> lines = new ArrayList<>();
        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(propertiesFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null)
                    lines.add(line);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read termux.properties", e);
            }
        }
        StringBuilder output = new StringBuilder();
        for (String line : withProperty(lines, propertyKey, propertyValue)) {
            output.append(line).append('\n');
        }
        FileUtils.writeTextToFile("termux.properties", propertiesFile.getAbsolutePath(),
            StandardCharsets.UTF_8, output.toString(), false);
    }

    /**
     * The file's lines with {@code propertyKey} set to {@code propertyValue}: every uncommented
     * line assigning that exact key is replaced in place, and the assignment is appended only when
     * none does. The key is matched literally, so a dot or bracket in it is not a regex.
     */
    @NonNull
    static List<String> withProperty(@NonNull List<String> lines, @NonNull String propertyKey,
                                     @NonNull String propertyValue) {
        String assignment = propertyKey + "=" + propertyValue;
        String assignsKey = Pattern.quote(propertyKey) + "\\s*=.*";
        List<String> out = new ArrayList<>(lines.size() + 1);
        boolean updated = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && trimmed.matches(assignsKey)) {
                out.add(assignment);
                updated = true;
            } else {
                out.add(line);
            }
        }
        if (!updated)
            out.add(assignment);
        return out;
    }
}
