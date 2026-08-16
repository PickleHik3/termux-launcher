package com.termux.app.theme;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The user's overrides for the derived token set, read from {@code ~/.termux/launcher-theme.properties}.
 *
 * <p>The derivation in {@link LauncherThemeTokens} is a default, not a verdict. A colorscheme author
 * in Neovim links most highlight groups to a handful of base ones and then hand-picks the two or
 * three that matter; the same file here does the same job:
 *
 * <pre>
 * primary          = color3            # accent from the scheme's yellow instead of its blue
 * surface_container_high = lighten(surface, 0.08)
 * outline_variant  = mix(on_surface, surface, 0.78)
 * scrollbar        = alpha(on_surface_variant, 0.3)
 * inverse_primary  = #d79921
 * </pre>
 *
 * <p>Values are a hex colour, a scheme key ({@code background}, {@code foreground}, {@code cursor},
 * {@code color0}-{@code color15}), another token name, or one of {@code lighten} / {@code darken} /
 * {@code mix} / {@code alpha} over any of those. References resolve against the derived defaults, so
 * overriding {@code surface} does not silently move every container that was derived from it — each
 * token is one independent statement, which is what makes the file readable a month later.
 */
public final class LauncherThemeOverrides {

    private static final String LOG_TAG = "LauncherThemeOverrides";
    private static final int MAX_DEPTH = 8;

    private LauncherThemeOverrides() {}

    /**
     * {@code tokens} with every recognised override in {@code overrides} applied, in place.
     *
     * <p>An unparsable value leaves its token at the derived default and logs; a theme file with one
     * typo in it must not cost the user the other thirty tokens.
     */
    public static void apply(@NonNull LinkedHashMap<String, Integer> tokens,
                             @NonNull SchemeColors scheme,
                             @Nullable Properties overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        LinkedHashMap<String, Integer> resolved = new LinkedHashMap<>();
        for (String name : LauncherThemeTokens.NAMES) {
            String expression = overrides.getProperty(name);
            if (TextUtils.isEmpty(expression)) continue;
            Integer value = evaluate(expression, scheme, tokens, new HashSet<>(), 0, overrides);
            if (value == null) {
                Logger.logWarn(LOG_TAG, "Ignoring unparsable override: " + name + " = " + expression);
                continue;
            }
            resolved.put(name, value);
        }
        tokens.putAll(resolved);
    }

    /** Names in {@code overrides} that are not tokens, for a settings-screen warning. */
    @NonNull
    public static List<String> unknownKeys(@Nullable Properties overrides) {
        List<String> unknown = new ArrayList<>();
        if (overrides == null) return unknown;
        for (Map.Entry<Object, Object> entry : overrides.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!LauncherThemeTokens.NAMES.contains(key)) unknown.add(key);
        }
        return unknown;
    }

    @Nullable
    private static Integer evaluate(@NonNull String expression,
                                    @NonNull SchemeColors scheme,
                                    @NonNull Map<String, Integer> derived,
                                    @NonNull Set<String> visiting,
                                    int depth,
                                    @NonNull Properties overrides) {
        if (depth > MAX_DEPTH) return null;
        String text = stripComment(expression).trim();
        if (text.isEmpty()) return null;

        int open = text.indexOf('(');
        if (open > 0 && text.endsWith(")")) {
            String function = text.substring(0, open).trim().toLowerCase(Locale.US);
            List<String> args = splitArguments(text.substring(open + 1, text.length() - 1));
            return applyFunction(function, args, scheme, derived, visiting, depth, overrides);
        }

        Integer literal = SchemeColors.parse(text);
        if (literal != null) return literal;

        Integer schemeColor = fromScheme(text, scheme);
        if (schemeColor != null) return schemeColor;

        if (LauncherThemeTokens.NAMES.contains(text)) {
            // A token referencing a token follows the user's own override for it when there is one,
            // so `outline = outline_variant` next to an overridden `outline_variant` means what it
            // reads like; the visiting set stops `a = b` / `b = a` from recursing forever.
            if (visiting.add(text)) {
                String chained = overrides.getProperty(text);
                if (!TextUtils.isEmpty(chained)) {
                    Integer value = evaluate(chained, scheme, derived, visiting, depth + 1, overrides);
                    visiting.remove(text);
                    if (value != null) return value;
                } else {
                    visiting.remove(text);
                }
            }
            return derived.get(text);
        }
        return null;
    }

    @Nullable
    private static Integer applyFunction(@NonNull String function,
                                         @NonNull List<String> args,
                                         @NonNull SchemeColors scheme,
                                         @NonNull Map<String, Integer> derived,
                                         @NonNull Set<String> visiting,
                                         int depth,
                                         @NonNull Properties overrides) {
        switch (function) {
            case "lighten":
            case "darken": {
                if (args.size() != 2) return null;
                Integer base = evaluate(args.get(0), scheme, derived, visiting, depth + 1, overrides);
                Double amount = number(args.get(1));
                if (base == null || amount == null) return null;
                // Amounts are fractions of the remaining tone range, so lighten(x, 0.1) means the
                // same visual step whether x starts near black or near white.
                double headroom = "lighten".equals(function)
                    ? 100d - SchemeTone.tone(base) : SchemeTone.tone(base);
                double delta = headroom * amount * ("lighten".equals(function) ? 1d : -1d);
                return SchemeTone.toneShift(base, delta);
            }
            case "mix": {
                if (args.size() != 3) return null;
                Integer from = evaluate(args.get(0), scheme, derived, visiting, depth + 1, overrides);
                Integer to = evaluate(args.get(1), scheme, derived, visiting, depth + 1, overrides);
                Double amount = number(args.get(2));
                if (from == null || to == null || amount == null) return null;
                return SchemeTone.blend(from, to, amount.floatValue());
            }
            case "alpha": {
                if (args.size() != 2) return null;
                Integer base = evaluate(args.get(0), scheme, derived, visiting, depth + 1, overrides);
                Double amount = number(args.get(1));
                if (base == null || amount == null) return null;
                return SchemeTone.withAlpha(base, amount.floatValue());
            }
            default:
                return null;
        }
    }

    @Nullable
    private static Integer fromScheme(@NonNull String name, @NonNull SchemeColors scheme) {
        switch (name) {
            case "background": return scheme.background;
            case "foreground": return scheme.foreground;
            case "cursor": return scheme.cursor;
            default: break;
        }
        if (!name.startsWith("color")) return null;
        try {
            int index = Integer.parseInt(name.substring("color".length()));
            if (index < 0 || index > 15) return null;
            return scheme.ansi(index);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static List<String> splitArguments(@NonNull String text) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                args.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) args.add(current.toString());
        for (int i = 0; i < args.size(); i++) {
            args.set(i, args.get(i).trim());
        }
        return args;
    }

    @Nullable
    private static Double number(@NonNull String text) {
        try {
            String value = text.trim();
            if (value.endsWith("%")) {
                return Double.parseDouble(value.substring(0, value.length() - 1)) / 100d;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The value with any trailing {@code #} comment removed.
     *
     * <p>{@code Properties} treats {@code #} as a comment only at the start of a line, so
     * {@code primary = color4 # the blue} arrives here with the comment still attached — and
     * {@code #d79921} must survive the same pass.
     */
    @NonNull
    private static String stripComment(@NonNull String value) {
        int first = value.indexOf('#');
        // A leading '#' is the colour, not a comment — start looking for the comment after it.
        int searchFrom = first >= 0 && value.substring(0, first).trim().isEmpty() ? first + 1 : 0;
        int index = value.indexOf('#', searchFrom);
        return index < 0 ? value : value.substring(0, index);
    }
}
