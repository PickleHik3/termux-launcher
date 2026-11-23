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
     * assignment of that exact key, in any of the forms {@link Properties#load} reads, is replaced
     * in place together with its continuation lines, and the assignment is appended only when none
     * is found. The key is matched literally.
     */
    @NonNull
    static List<String> withProperty(@NonNull List<String> lines, @NonNull String propertyKey,
                                     @NonNull String propertyValue) {
        String assignment = propertyKey + "=" + propertyValue;
        List<String> out = new ArrayList<>(lines.size() + 1);
        if (!edit(lines, propertyKey, assignment, out))
            out.add(assignment);
        return out;
    }

    /**
     * The file's lines without any uncommented assignment of {@code propertyKey} (matched as in
     * {@link #withProperty}) or its continuation lines; comments and every other line stay.
     */
    @NonNull
    static List<String> withoutProperty(@NonNull List<String> lines, @NonNull String propertyKey) {
        List<String> out = new ArrayList<>(lines.size());
        edit(lines, propertyKey, null, out);
        return out;
    }

    /**
     * Copies {@code lines} to {@code out}, putting {@code replacement} (or nothing when null) in
     * place of each assignment of the key and the continuation lines it owns. True when any matched.
     */
    private static boolean edit(@NonNull List<String> lines, @NonNull String propertyKey,
                                String replacement, @NonNull List<String> out) {
        boolean matched = false;
        boolean continuing = false;
        boolean dropping = false;
        for (String line : lines) {
            if (continuing) {
                continuing = continues(line);
                if (!dropping)
                    out.add(line);
                continue;
            }
            dropping = assignsKey(line, propertyKey);
            continuing = !isComment(line) && continues(line);
            if (dropping) {
                matched = true;
                if (replacement != null)
                    out.add(replacement);
            } else {
                out.add(line);
            }
        }
        return matched;
    }

    private static boolean isComment(@NonNull String line) {
        String trimmed = stripLeadingWhitespace(line);
        return trimmed.startsWith("#") || trimmed.startsWith("!");
    }

    /** Whether the line is not a comment and its key, up to {@code = : or whitespace}, is the key. */
    private static boolean assignsKey(@NonNull String line, @NonNull String propertyKey) {
        String trimmed = stripLeadingWhitespace(line);
        if (trimmed.startsWith("#") || trimmed.startsWith("!") || !trimmed.startsWith(propertyKey))
            return false;
        if (trimmed.length() == propertyKey.length())
            return true;
        char next = trimmed.charAt(propertyKey.length());
        return next == '=' || next == ':' || next == ' ' || next == '\t' || next == '\f';
    }

    /** A line ending in an odd number of backslashes continues on the next one. */
    private static boolean continues(@NonNull String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--)
            count++;
        return count % 2 == 1;
    }

    @NonNull
    private static String stripLeadingWhitespace(@NonNull String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'
            || line.charAt(i) == '\f'))
            i++;
        return line.substring(i);
    }
}
