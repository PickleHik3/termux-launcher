package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The Pane borders card's Custom colour field: {@code #RRGGBB} or {@code #AARRGGBB}, either case,
 * read to an ARGB and written back. Zero is the panes' "no colour" ({@code
 * PaneBorderStyle.NO_COLOUR}), so the field never answers it: {@code #000000} is opaque black,
 * and a fully transparent black is refused.
 */
public final class PaneBorderHex {

    private PaneBorderHex() {}

    /** The ARGB {@code text} names, or null for anything else (the field then keeps its last). */
    @Nullable
    public static Integer parse(@Nullable String text) {
        if (text == null)
            return null;
        String hex = text.trim();
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        if (hex.length() != 6 && hex.length() != 8)
            return null;
        long value = 0;
        for (int i = 0; i < hex.length(); i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0)
                return null;
            value = (value << 4) | digit;
        }
        int argb = hex.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
        return argb == 0 ? null : argb;
    }

    /** {@code argb} as the field shows it: {@code #RRGGBB} when opaque, else {@code #AARRGGBB}. */
    @NonNull
    public static String format(int argb) {
        if ((argb >>> 24) == 0xFF)
            return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
        return String.format(Locale.ROOT, "#%08X", argb);
    }
}
