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

    /** Whether "Too heavy at this quality" shows above the knobs. */
    public static boolean showsTooHeavy(@Nullable String heldReason) {
        return ShaderSafety.REASON_TOO_HEAVY.equals(heldReason);
    }
}
