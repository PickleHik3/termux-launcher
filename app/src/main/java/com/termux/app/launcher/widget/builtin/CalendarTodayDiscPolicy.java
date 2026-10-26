package com.termux.app.launcher.widget.builtin;

/**
 * The colours of the Calendar's today disc and its digit. The disc is the scheme's primary, but
 * a dark-navy primary on dark glass is invisible, so when primary is too close to the glass it
 * sits on the disc falls back to the widget's ink (85% over the glass); the digit then keeps
 * contrast against whichever disc was chosen. Pure, so it is tested without a view.
 */
final class CalendarTodayDiscPolicy {
    /** Least contrast ratio the disc keeps against the glass. */
    static final double DISC_FLOOR = 2.0;
    /** Least contrast ratio the digit keeps against the disc. */
    static final double DIGIT_FLOOR = 3.0;
    private static final int INK_ALPHA = 217; // ~85%

    private CalendarTodayDiscPolicy() {}

    /** @return {disc, digit}, both opaque ARGB. */
    static int[] todayDiscColours(int glass, int primary, int onPrimary, int ink) {
        int base = 0xFF000000 | glass;
        int disc = 0xFF000000 | primary;
        if (contrast(disc, base) < DISC_FLOOR) {
            disc = over(ink, INK_ALPHA, base);
        }
        int digit = 0xFF000000 | onPrimary;
        if (contrast(digit, disc) < DIGIT_FLOOR) {
            digit = contrast(0xFF000000, disc) >= contrast(0xFFFFFFFF, disc)
                ? 0xFF000000 : 0xFFFFFFFF;
        }
        return new int[]{disc, digit};
    }

    static int over(int colour, int alpha, int base) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (colour >> shift) & 0xFF;
            int b = (base >> shift) & 0xFF;
            out |= ((c * alpha + b * (255 - alpha) + 127) / 255) << shift;
        }
        return out;
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF);
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
