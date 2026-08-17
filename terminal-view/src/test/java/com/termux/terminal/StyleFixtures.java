package com.termux.terminal;

/**
 * Test-only access to {@link TextStyle}'s package-private encoder, so renderer tests in
 * {@code com.termux.view} can build the same packed styles the emulator stores in a row.
 */
public final class StyleFixtures {

    private StyleFixtures() {
    }

    public static long style(int foreColor, int backColor, int effect) {
        return TextStyle.encode(foreColor, backColor, effect);
    }

    public static long style(int foreColor, int backColor, int effect, int underlineStyle) {
        return TextStyle.encode(foreColor, backColor, effect, underlineStyle);
    }
}
