package com.termux.terminal;

import android.graphics.Color;

/**
 * Current terminal colors (if different from default).
 */
public final class TerminalColors {

    /**
     * Static data - a bit ugly but ok for now.
     */
    public static final TerminalColorScheme COLOR_SCHEME = new TerminalColorScheme();

    /**
     * The current terminal colors, which are normally set from the color theme, but may be set dynamically with the OSC
     * 4 control sequence.
     */
    public final int[] mCurrentColors = new int[TextStyle.NUM_INDEXED_COLORS];

    /**
     * How many palettes the color stack (XTPUSHCOLORS / XTPOPCOLORS) holds. xterm stores ten and so
     * does this; a push past the last slot overwrites it rather than growing without bound, which is
     * what keeps a program pushing in a loop from holding a palette per iteration.
     */
    public static final int MAX_COLOR_STACK_DEPTH = 10;

    /** Saved palettes, slot 0 at the bottom. A null slot was never written. */
    private final int[][] mColorStack = new int[MAX_COLOR_STACK_DEPTH][];

    /** How many slots hold a palette, counted from the bottom. */
    private int mColorStackCount;

    /** The slot last pushed to or popped from, 1-based; 0 while the stack is empty. */
    private int mColorStackIndex;

    /**
     * Create a new instance with default colors from the theme.
     */
    public TerminalColors() {
        reset();
    }

    /**
     * Save the whole palette — the 256 indexed colors plus the foreground, background and cursor
     * colors — for a later {@link #popPalette(int)}.
     *
     * @param slot the 1-based stack position to write, or 0 to push onto the top of the stack.
     */
    public void pushPalette(int slot) {
        int target;
        if (slot >= 1 && slot <= MAX_COLOR_STACK_DEPTH) {
            target = slot - 1;
            mColorStackCount = Math.max(mColorStackCount, slot);
        } else if (mColorStackCount < MAX_COLOR_STACK_DEPTH) {
            target = mColorStackCount;
            mColorStackCount++;
        } else {
            // Full: xterm overwrites the top entry rather than dropping the push.
            target = MAX_COLOR_STACK_DEPTH - 1;
        }
        mColorStack[target] = mCurrentColors.clone();
        mColorStackIndex = target + 1;
    }

    /**
     * Restore a saved palette.
     *
     * @param slot the 1-based stack position to restore, or 0 to pop the top of the stack.
     * @return true if any color actually changed, so the caller can skip telling the client about a
     * pop that repainted nothing.
     */
    public boolean popPalette(int slot) {
        int target;
        if (slot >= 1 && slot <= MAX_COLOR_STACK_DEPTH) {
            target = slot - 1;
        } else if (mColorStackCount > 0) {
            target = mColorStackCount - 1;
        } else {
            return false;
        }
        int[] saved = mColorStack[target];
        mColorStackCount = target;
        mColorStackIndex = mColorStackCount;
        if (saved == null)
            return false;
        mColorStack[target] = null;
        boolean changed = false;
        for (int i = 0; i < TextStyle.NUM_INDEXED_COLORS; i++) {
            if (mCurrentColors[i] != saved[i]) {
                mCurrentColors[i] = saved[i];
                changed = true;
            }
        }
        return changed;
    }

    /** The stack position last pushed or popped, for XTREPORTCOLORS. */
    public int getColorStackIndex() {
        return mColorStackIndex;
    }

    /** How many palettes the stack holds, for XTREPORTCOLORS. */
    public int getColorStackCount() {
        return mColorStackCount;
    }

    /** Drop every saved palette, as a full terminal reset does. */
    public void clearStack() {
        for (int i = 0; i < MAX_COLOR_STACK_DEPTH; i++) mColorStack[i] = null;
        mColorStackCount = 0;
        mColorStackIndex = 0;
    }

    /**
     * Reset a particular indexed color with the default color from the color theme.
     */
    public void reset(int index) {
        mCurrentColors[index] = COLOR_SCHEME.mDefaultColors[index];
    }

    /**
     * Reset all indexed colors with the default color from the color theme.
     */
    public void reset() {
        System.arraycopy(COLOR_SCHEME.mDefaultColors, 0, mCurrentColors, 0, TextStyle.NUM_INDEXED_COLORS);
    }

    /**
     * Parse color according to http://manpages.ubuntu.com/manpages/intrepid/man3/XQueryColor.3.html
     * <p/>
     * Highest bit is set if successful, so return value is 0xFF${R}${G}${B}. Return 0 if failed.
     */
    static int parse(String c) {
        try {
            int skipInitial, skipBetween;
            if (c.charAt(0) == '#') {
                // #RGB, #RRGGBB, #RRRGGGBBB or #RRRRGGGGBBBB. Most significant bits.
                skipInitial = 1;
                skipBetween = 0;
            } else if (c.startsWith("rgb:")) {
                // rgb:<red>/<green>/<blue> where <red>, <green>, <blue> := h | hh | hhh | hhhh. Scaled.
                skipInitial = 4;
                skipBetween = 1;
            } else {
                return 0;
            }
            int charsForColors = c.length() - skipInitial - 2 * skipBetween;
            // Unequal lengths.
            if (charsForColors % 3 != 0)
                return 0;
            int componentLength = charsForColors / 3;
            double mult = 255 / (Math.pow(2, componentLength * 4) - 1);
            int currentPosition = skipInitial;
            String rString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String gString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String bString = c.substring(currentPosition, currentPosition + componentLength);
            int r = (int) (Integer.parseInt(rString, 16) * mult);
            int g = (int) (Integer.parseInt(gString, 16) * mult);
            int b = (int) (Integer.parseInt(bString, 16) * mult);
            return 0xFF << 24 | r << 16 | g << 8 | b;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    /**
     * Try parse a color from a text parameter and into a specified index.
     */
    public void tryParseColor(int intoIndex, String textParameter) {
        int c = parse(textParameter);
        if (c != 0)
            mCurrentColors[intoIndex] = c;
    }

    /**
     * Get the perceived brightness of the color based on its RGB components.
     *
     * https://www.nbdtech.com/Blog/archive/2008/04/27/Calculating-the-Perceived-Brightness-of-a-Color.aspx
     * http://alienryderflex.com/hsp.html
     *
     * @param color The color code int.
     * @return Returns value between 0-255.
     */
    public static int getPerceivedBrightnessOfColor(int color) {
        return (int) Math.floor(Math.sqrt(Math.pow(Color.red(color), 2) * 0.241 + Math.pow(Color.green(color), 2) * 0.691 + Math.pow(Color.blue(color), 2) * 0.068));
    }
}
