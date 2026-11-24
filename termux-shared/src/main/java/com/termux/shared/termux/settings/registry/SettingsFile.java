package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings file: one {@code id = value} line per exported setting, {@code #} comments and
 * blank lines allowed. It is a generated face of the {@link SettingsRegistry}, never the source of
 * truth and never watched. {@link #export} writes it; {@link #check} reads every line against the
 * registry without saving, so an apply changes nothing when any line is wrong.
 */
public final class SettingsFile {

    private SettingsFile() {}

    /** One valid line: the setting and the value to save. */
    public static final class Assignment {
        @NonNull public final String id;
        @NonNull public final String value;
        /** Where it stands in the file, counted from 1, for a failure that shows only on saving. */
        public final int line;

        Assignment(@NonNull String id, @NonNull String value, int line) {
            this.id = id;
            this.value = value;
            this.line = line;
        }
    }

    /** A checked file: its assignments in order, and one error per bad line ({@code line 12: ...}). */
    public static final class Result {
        @NonNull public final List<Assignment> assignments;
        @NonNull public final List<String> errors;

        Result(@NonNull List<Assignment> assignments, @NonNull List<String> errors) {
            this.assignments = Collections.unmodifiableList(assignments);
            this.errors = Collections.unmodifiableList(errors);
        }
    }

    /** Every exported setting as a file, without a trailing newline. */
    @NonNull
    public static String export(@NonNull SettingsRegistry registry) {
        StringBuilder text = new StringBuilder(
            "# launcherctl config export; apply it with launcherctl config apply FILE");
        for (SettingsRegistry.Entry entry : registry.exported()) {
            text.append('\n').append(entry.declaration.id).append(" = ").append(entry.value);
        }
        return text.toString();
    }

    /** Reads {@code text} against {@code registry}; saves nothing. */
    @NonNull
    public static Result check(@NonNull String text, @NonNull SettingsRegistry registry) {
        List<Assignment> assignments = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = "line " + (i + 1) + ": ";
            int equals = line.indexOf('=');
            if (equals <= 0) {
                errors.add(where + "expected id = value");
                continue;
            }
            String id = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            SettingDeclaration declaration = registry.declaration(id);
            if (declaration == null || !declaration.exported) {
                errors.add(where + SettingsRegistry.unknown(id).reason);
                continue;
            }
            SettingsRegistry.Refusal refusal = registry.check(id, value);
            if (refusal != null) {
                errors.add(where + refusal.reason);
                continue;
            }
            assignments.add(new Assignment(id, value, i + 1));
        }
        return new Result(assignments, errors);
    }
}
