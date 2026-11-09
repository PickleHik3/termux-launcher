package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderSafety;

import java.util.List;

/** The pure parts of the shader editor: the error strip's text and what Apply does with a check. */
public final class ShaderEditorLogic {

    /** The error strip shows at most this many errors. */
    public static final int MAX_ERRORS = 3;
    /** A shader's name is at most this long. */
    public static final int MAX_NAME = 60;

    /** What Apply does after {@code ShaderCheck}. */
    public enum Decision {
        /** Errors: show them and do nothing else. */
        STAY,
        /** A pass: save the shader, make it the motion, then set the still. */
        SAVE_AND_APPLY,
    }

    private ShaderEditorLogic() {}

    @NonNull
    public static Decision applyDecision(@Nullable List<ShaderErrors.Error> errors) {
        return errors != null && errors.isEmpty() ? Decision.SAVE_AND_APPLY : Decision.STAY;
    }

    /**
     * The strip's text: up to {@link #MAX_ERRORS} errors, one per line, "Line N: message" (the
     * message alone when it has no line). Empty when there are none, and the strip is then hidden.
     */
    @NonNull
    public static String errorText(@Nullable List<ShaderErrors.Error> errors) {
        if (errors == null || errors.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        int n = Math.min(MAX_ERRORS, errors.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append('\n');
            b.append(errors.get(i).toString());
        }
        return b.toString();
    }

    /** The name to save: what was typed, trimmed and cut; {@code fallback} when it is blank. */
    @NonNull
    public static String nameOrDefault(@Nullable String typed, @NonNull String fallback) {
        String n = typed == null ? "" : typed.trim();
        if (n.isEmpty()) return fallback;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME).trim() : n;
    }

    /**
     * Whether Apply saves a new shader rather than updating the open one: an example is read-only,
     * so applying it always saves a copy under a fresh id.
     */
    public static boolean savesAsCopy(boolean isExample, boolean isNew) {
        return isExample || isNew;
    }

    /**
     * {@code name} when no saved shader has it (compared without case), else {@code name 2},
     * {@code name 3} and so on, cut so the result stays within {@link #MAX_NAME}.
     */
    @NonNull
    public static String uniqueName(@NonNull String name, @NonNull java.util.Collection<String> existing) {
        java.util.HashSet<String> taken = new java.util.HashSet<>();
        for (String e : existing) taken.add(e.toLowerCase(java.util.Locale.ROOT));
        if (!taken.contains(name.toLowerCase(java.util.Locale.ROOT))) return name;
        for (int n = 2; ; n++) {
            String suffix = " " + n;
            String base = name.length() + suffix.length() > MAX_NAME
                ? name.substring(0, MAX_NAME - suffix.length()).trim() : name;
            String candidate = base + suffix;
            if (!taken.contains(candidate.toLowerCase(java.util.Locale.ROOT))) return candidate;
        }
    }

    /** One tile slot of the Shader list. */
    public static final class Slot {
        public static final int USER = 0, EXAMPLES_HEADING = 1, EXAMPLE = 2;
        public final int kind;
        /** Index into the user shaders or the examples; -1 for the heading. */
        public final int index;

        Slot(int kind, int index) {
            this.kind = kind;
            this.index = index;
        }
    }

    /** The list's order: the user's shaders, then the "Examples" heading and the examples (none, no heading). */
    @NonNull
    public static List<Slot> listOrder(int userCount, int exampleCount) {
        List<Slot> out = new java.util.ArrayList<>();
        for (int i = 0; i < userCount; i++) out.add(new Slot(Slot.USER, i));
        if (exampleCount > 0) {
            out.add(new Slot(Slot.EXAMPLES_HEADING, -1));
            for (int i = 0; i < exampleCount; i++) out.add(new Slot(Slot.EXAMPLE, i));
        }
        return out;
    }

    /** Whether "Too heavy at this quality" shows above the knobs. */
    public static boolean showsTooHeavy(@Nullable String heldReason) {
        return ShaderSafety.REASON_TOO_HEAVY.equals(heldReason);
    }
}
