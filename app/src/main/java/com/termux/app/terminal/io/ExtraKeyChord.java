package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a typed modifier combination such as "ctrl + m" or "Alt+Shift+Tab" into the macro the
 * extra keys row replays, and tidies a typed macro. Pure Java so the picker's rules are testable.
 */
public final class ExtraKeyChord {

    /** The macro the row stores (modifier tokens are the uppercase names the replay matches) and the cap text. */
    public static final class Parsed {
        @NonNull public final String macro;
        @NonNull public final String display;

        Parsed(@NonNull String macro, @NonNull String display) {
            this.macro = macro;
            this.display = display;
        }
    }

    private static final Pattern SEPARATOR = Pattern.compile("\\s*\\+\\s*|\\s+");
    private static final Pattern FUNCTION = Pattern.compile("(?i)f([1-9]|1[0-2])");

    private ExtraKeyChord() {}

    /**
     * The chord a typed text spells, or null when it is not one: at least one modifier
     * (ctrl, control, alt, shift, fn), then exactly one key, which is a named key, F1 to F12 or a
     * single character. Parts are separated by "+" or spaces.
     */
    @Nullable
    public static Parsed parse(@Nullable String typed) {
        if (typed == null) return null;
        String text = typed.trim();
        if (text.isEmpty() || text.startsWith("+") || text.endsWith("+")) return null;
        String[] parts = SEPARATOR.split(text);
        if (parts.length < 2) return null;
        List<String> mods = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String mod = modifier(parts[i]);
            if (mod == null || mods.contains(mod)) return null;
            mods.add(mod);
        }
        String last = parts[parts.length - 1];
        String keyToken;
        String keyDisplay;
        if (FUNCTION.matcher(last).matches()) {
            keyToken = last.toUpperCase(Locale.ROOT);
            keyDisplay = keyToken;
        } else if (last.codePointCount(0, last.length()) == 1) {
            keyToken = last.toLowerCase(Locale.ROOT);
            keyDisplay = last.toUpperCase(Locale.ROOT);
        } else {
            String upper = last.toUpperCase(Locale.ROOT);
            if (modifier(upper) != null
                || !ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(upper)
                || !ExtraKeyPickerCatalog.allKeys().contains(upper)) return null;
            keyToken = upper;
            keyDisplay = upper;
        }
        StringBuilder macro = new StringBuilder();
        StringBuilder display = new StringBuilder();
        for (String mod : mods) {
            macro.append(mod).append(' ');
            display.append(mod.charAt(0)).append(mod.substring(1).toLowerCase(Locale.ROOT)).append('+');
        }
        macro.append(keyToken);
        display.append(keyDisplay);
        return new Parsed(macro.toString(), display.toString());
    }

    /** CTRL, ALT, SHIFT or FN for a typed modifier name in any case, else null. */
    @Nullable
    private static String modifier(@NonNull String word) {
        switch (word.toLowerCase(Locale.ROOT)) {
            case "ctrl": case "control": return "CTRL";
            case "alt": return "ALT";
            case "shift": return "SHIFT";
            case "fn": return "FN";
            default: return null;
        }
    }

    /**
     * A typed macro the replay understands: a "+" standing alone between tokens is dropped, and a
     * token naming a modifier, a named key or F1 to F12 is upper-cased ("ctrl + m" becomes
     * "CTRL m"). Every other token stays as typed.
     */
    @NonNull
    public static String normaliseMacro(@NonNull String macro) {
        String[] tokens = macro.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (token.equals("+")) continue;
            if (out.length() > 0) out.append(' ');
            String upper = token.toUpperCase(Locale.ROOT);
            boolean named = token.length() > 1 && (modifier(token) != null
                || FUNCTION.matcher(token).matches()
                || (ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(upper)
                    && ExtraKeyPickerCatalog.allKeys().contains(upper)));
            if (named) {
                String mod = modifier(token);
                out.append(mod != null ? mod : upper);
            } else {
                out.append(token);
            }
        }
        return out.length() == 0 ? macro.trim() : out.toString();
    }
}
