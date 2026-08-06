package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Turns a key press into the stroke a binding file would spell, for the palette's capture overlay.
 *
 * <p>Every raw-key decision lives here rather than in the controller, because the controller needs a
 * live {@code TermuxActivity} and cannot be unit-tested; the controller is left holding only
 * routing.
 *
 * <p>Modifier-only presses need no special handling: {@link TerminalKeyBindingResolver#keyToken}
 * already returns null for the modifier key codes, so "wait for a non-modifier key" falls out of the
 * existing table rather than being a rule of its own.
 */
public final class CommandPaletteCaptureModel {

    private CommandPaletteCaptureModel() {}

    /**
     * The stroke for a hardware key press, or {@code null} when the key is not bindable at all —
     * an unmapped key code, or a modifier still waiting for the key it modifies.
     */
    @Nullable
    public static String strokeFor(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        String token = TerminalKeyBindingResolver.keyToken(keyCode);
        return token == null ? null : withModifiers(token, ctrl, alt, shift);
    }

    /**
     * The stroke for a character from the in-app keyboard. On a phone with no physical keyboard a
     * hardware-only overlay would be dead UI, and the in-app keyboard's interceptor already carries
     * the modifier flags.
     *
     * <p>A shifted letter arrives as an uppercase character rather than as a shift flag, so the
     * character is lowercased into the token and shift is taken from the flag alone — otherwise
     * {@code W} with ctrl+alt held would spell a stroke no key press could ever match.
     */
    @Nullable
    public static String strokeForChar(char c, boolean ctrl, boolean alt, boolean shift) {
        if (c == '\0' || Character.isISOControl(c)) return null;
        if (Character.isWhitespace(c)) return c == ' '
            ? withModifiers("space", ctrl, alt, shift) : null;
        return withModifiers(String.valueOf(Character.toLowerCase(c)), ctrl, alt, shift);
    }

    /**
     * Whether a captured stroke may be written to the binding file. An unmodified key is refused:
     * binding a bare {@code w} would swallow typing that character everywhere.
     */
    public static boolean isBindable(@Nullable String stroke) {
        if (stroke == null || stroke.isEmpty()) return false;
        return stroke.startsWith("ctrl+") || stroke.startsWith("alt+")
            || stroke.startsWith("shift+");
    }

    /** Modifiers in the canonical order the resolver and the config file both use. */
    @NonNull
    private static String withModifiers(@NonNull String token, boolean ctrl, boolean alt,
                                        boolean shift) {
        StringBuilder stroke = new StringBuilder();
        if (ctrl) stroke.append("ctrl+");
        if (alt) stroke.append("alt+");
        if (shift) stroke.append("shift+");
        return stroke.append(token).toString();
    }
}
