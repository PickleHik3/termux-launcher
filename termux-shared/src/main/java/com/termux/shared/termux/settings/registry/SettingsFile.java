package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings file ({@code terl.conf}): one {@code id = value} line per exported setting, {@code #}
 * comments and blank lines allowed. The settings are the truth, not the file (ADR 0041): it is a
 * way in, and a patch, so an id it leaves out keeps its value. {@code follow} puts a surface's own
 * value back on Global and {@code default} resets a setting; neither is a plain value.
 * {@link #export} writes it; {@link #check} reads every line without saving; {@link #apply} saves
 * all of it or none: any line that cannot be read or is refused while saving rolls every earlier
 * line back.
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
            "# terl.conf: one id = value per line; follow puts a surface back on Global, default resets");
        for (SettingsRegistry.Entry entry : registry.exportedForFile()) {
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

    /** What an {@link #apply} did. */
    public static final class Applied {
        /** Whether every line landed; false means nothing changed. */
        public final boolean ok;
        /** Whether the file was unreadable or refused before anything was saved (false: refused while saving). */
        public final boolean beforeSaving;
        /** The saves that went through, in file order; empty when not {@link #ok}. */
        @NonNull public final List<SettingsRegistry.Change> changes;
        /** One {@code line N: ...} per bad line; empty when {@link #ok}. */
        @NonNull public final List<String> errors;

        Applied(boolean ok, boolean beforeSaving, @NonNull List<SettingsRegistry.Change> changes,
                @NonNull List<String> errors) {
            this.ok = ok;
            this.beforeSaving = beforeSaving;
            this.changes = Collections.unmodifiableList(changes);
            this.errors = Collections.unmodifiableList(errors);
        }
    }

    /**
     * Saves every line of {@code text} or none. Checks all lines first; then saves in order and,
     * if a save is refused, puts every setting already saved back as it was (a follower follows
     * again). The caller hands the changes of an ok result to its {@link SettingsApplier}.
     */
    @NonNull
    public static Applied apply(@NonNull String text, @NonNull SettingsRegistry registry) {
        Result result = check(text, registry);
        if (!result.errors.isEmpty()) {
            return new Applied(false, true, new ArrayList<>(), new ArrayList<>(result.errors));
        }
        List<SettingsRegistry.Change> done = new ArrayList<>();
        List<Boolean> followed = new ArrayList<>();
        List<Boolean> inheritanceMoved = new ArrayList<>();
        for (Assignment each : result.assignments) {
            boolean wasFollowing = registry.follows(each.id);
            SettingsRegistry.Refusal refusal;
            SettingsRegistry.Outcome outcome;
            try {
                outcome = registry.set(each.id, each.value);
                refusal = outcome.refusal();
            } catch (RuntimeException e) {
                outcome = null;
                refusal = new SettingsRegistry.Refusal(each.id + " could not be saved", null);
            }
            if (refusal == null) {
                done.add(outcome.change());
                followed.add(wasFollowing);
                inheritanceMoved.add(wasFollowing != registry.follows(each.id));
                continue;
            }
            List<String> errors = new ArrayList<>();
            errors.add("line " + each.line + ": " + refusal.reason);
            for (int i = done.size() - 1; i >= 0; i--) {
                SettingsRegistry.Change change = done.get(i);
                if (!change.changed() && !inheritanceMoved.get(i)) continue;
                SettingsRegistry.Outcome back = followed.get(i)
                    ? registry.set(change.id, SettingsRegistry.FOLLOW)
                    : registry.set(change.id, change.oldValue);
                if (back.refusal() != null) {
                    errors.add(change.id + " could not be put back: " + back.refusal().reason);
                }
            }
            return new Applied(false, false, new ArrayList<>(), errors);
        }
        return new Applied(true, false, done, new ArrayList<>());
    }
}
