package com.termux.app.theme;

import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Properties;

/**
 * A parsed {@code ~/.termux/colors.properties} — the file Termux:Styling writes.
 *
 * <p>Only the keys the terminal itself understands are read: {@code background}, {@code foreground},
 * {@code cursor} and {@code color0}-{@code color15}. Anything else in the file is ignored here, the
 * same way {@code TerminalColorScheme} ignores it, so a hand-edited scheme with extra lines still
 * themes the chrome.
 *
 * <p>Missing entries fall back to values derived from what is present rather than to a fixed
 * palette: a scheme that only defines a background and a foreground is unusual but legal, and it
 * should still produce a usable launcher theme rather than half a stock blue one.
 */
public final class SchemeColors {

    private static final int ANSI_COUNT = 16;

    @ColorInt public final int background;
    @ColorInt public final int foreground;
    @ColorInt public final int cursor;
    @NonNull private final int[] mAnsi;

    private SchemeColors(int background, int foreground, int cursor, @NonNull int[] ansi) {
        this.background = background;
        this.foreground = foreground;
        this.cursor = cursor;
        this.mAnsi = ansi;
    }

    /** The ANSI colour at {@code index}, 0-15; out of range indices clamp. */
    @ColorInt
    public int ansi(int index) {
        return mAnsi[Math.max(0, Math.min(ANSI_COUNT - 1, index))];
    }

    /** Whether the scheme reads as a dark theme. */
    public boolean isDark() {
        return SchemeTone.isDark(background);
    }

    /**
     * Parses {@code props}, or returns null when it carries neither a background nor a foreground.
     *
     * <p>A scheme with no anchor at all cannot drive a theme — deriving one from the ANSI colours
     * alone guesses at which of them the author meant as the surface, and guessing wrong repaints
     * the whole launcher in a colour the user never picked.
     */
    @Nullable
    public static SchemeColors from(@Nullable Properties props) {
        if (props == null) return null;
        Integer background = parse(props.getProperty("background"));
        Integer foreground = parse(props.getProperty("foreground"));
        if (background == null && foreground == null) return null;

        int[] ansi = new int[ANSI_COUNT];
        boolean[] present = new boolean[ANSI_COUNT];
        for (int i = 0; i < ANSI_COUNT; i++) {
            Integer value = parse(props.getProperty("color" + i));
            if (value != null) {
                ansi[i] = value;
                present[i] = true;
            }
        }

        int resolvedBackground = background != null ? background
            : (present[0] ? ansi[0] : Color.BLACK);
        int resolvedForeground = foreground != null ? foreground
            : (present[7] ? ansi[7] : opposite(resolvedBackground));
        boolean dark = SchemeTone.isDark(resolvedBackground);

        for (int i = 0; i < ANSI_COUNT; i++) {
            if (present[i]) continue;
            // A bright slot with no entry mirrors its normal counterpart, lifted a little; a normal
            // slot with no entry sits between the background and the foreground so it stays visible.
            if (i >= 8 && present[i - 8]) {
                ansi[i] = SchemeTone.toneShift(ansi[i - 8], dark ? 8d : -8d);
            } else if (i == 0) {
                ansi[i] = SchemeTone.toneShift(resolvedBackground, dark ? 10d : -10d);
            } else if (i == 7 || i == 15) {
                ansi[i] = resolvedForeground;
            } else {
                ansi[i] = SchemeTone.blend(resolvedForeground, resolvedBackground, 0.4f);
            }
        }

        Integer cursor = parse(props.getProperty("cursor"));
        return new SchemeColors(resolvedBackground, resolvedForeground,
            cursor != null ? cursor : resolvedForeground, ansi);
    }

    /**
     * Parses {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB} and the bare-hex spellings of the
     * same, or returns null.
     *
     * <p>Hand-written scheme files in the wild use all of them, and {@code Color.parseColor} throws
     * on anything it does not like — a single bad line must not cost the user their theme.
     */
    @Nullable
    public static Integer parse(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return null;
        String text = value.trim();
        int comment = text.indexOf('#', 1);
        if (text.startsWith("#") && comment > 0) text = text.substring(0, comment).trim();
        if (!text.startsWith("#")) text = "#" + text;
        try {
            if (text.length() == 4) {
                // #RGB -> #RRGGBB
                StringBuilder expanded = new StringBuilder("#");
                for (int i = 1; i < 4; i++) {
                    expanded.append(text.charAt(i)).append(text.charAt(i));
                }
                text = expanded.toString();
            }
            if (text.length() != 7 && text.length() != 9) return null;
            long parsed = Long.parseLong(text.substring(1), 16);
            if (text.length() == 7) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code #RRGGBB} spelling, for logs and exported files. */
    @NonNull
    public static String hex(@ColorInt int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    @ColorInt
    private static int opposite(@ColorInt int color) {
        return SchemeTone.isDark(color) ? Color.WHITE : Color.BLACK;
    }
}
