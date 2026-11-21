package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * The page glyphs' geometry, in the Roundline family's 24-unit viewport: each kind's outline (one
 * {@link #STROKE}-unit stroke, round caps and joins), the Assistant's filled antenna dot, and the
 * box the page number is drawn in, {@link #NUMBER_AIR} clear of every stroke. The one place
 * these live: the strings are the {@code ic_rl_pg_*.xml} drawables' path data (a test holds them
 * equal), and every painter, the glow included, parses them from here.
 */
public final class PageGlyphSlots {

    public static final float VIEWPORT = 24f;
    /** The outline's stroke width in units. */
    public static final float STROKE = 2f;

    /** The digits' typeface: sans-serif Bold. */
    public static final String DIGIT_FAMILY = "sans-serif";
    public static final int DIGIT_WEIGHT = 700;

    static final String HOME_OUTLINE = "M3 10 L12 2.5 L21 10 M5 8.5 V20.5 H19 V8.5";
    static final String TERMINAL_OUTLINE = "M5 3 H19 Q22 3 22 6 V18 Q22 21 19 21 H5 Q2 21 2 18"
        + " V6 Q2 3 5 3 Z M5 9 L8 12 L5 15";
    static final String DISPLAY_OUTLINE = "M4.5 3 H19.5 Q22 3 22 5.5 V14.5 Q22 17 19.5 17 H4.5"
        + " Q2 17 2 14.5 V5.5 Q2 3 4.5 3 Z M12 17 V21 M7 21 H17";
    static final String ASSISTANT_OUTLINE = "M7.5 7 H16.5 Q20 7 20 10.5 V17.5 Q20 21 16.5 21 H7.5"
        + " Q4 21 4 17.5 V10.5 Q4 7 7.5 7 Z M2 12 V16 M22 12 V16 M12 5 V7";
    static final String ASSISTANT_DOT = "M10.5 3 A1.5 1.5 0 1 0 13.5 3 A1.5 1.5 0 1 0 10.5 3 Z";

    /** Air between a number's ink and the nearest stroke's edge, in units. */
    static final float NUMBER_AIR = 2f;
    /**
     * The number boxes (left, top, right, bottom): the room inside the outline where the number's
     * ink stands at least {@link #NUMBER_AIR} from every stroke's edge. Home's top clears the roof
     * slopes and its bottom the floor; Terminal's right the body's wall.
     */
    static final float[] HOME_BOX = {8f, 9.75f, 16f, 17.5f};
    static final float[] TERMINAL_BOX = {11f, 8f, 19f, 16f};
    static final float[] DISPLAY_BOX = {6f, 6f, 18f, 14f};
    static final float[] ASSISTANT_BOX = {8f, 10f, 16f, 18f};

    private PageGlyphSlots() {}

    @NonNull
    static String outline(@NonNull PageKind kind) {
        switch (kind) {
            case HOME: return HOME_OUTLINE;
            case TERMINAL: return TERMINAL_OUTLINE;
            case DISPLAY: return DISPLAY_OUTLINE;
            default: return ASSISTANT_OUTLINE;
        }
    }

    /** The kind's filled marks, or null when it has none. */
    @Nullable
    static String dot(@NonNull PageKind kind) {
        return kind == PageKind.ASSISTANT ? ASSISTANT_DOT : null;
    }

    @NonNull
    static float[] box(@NonNull PageKind kind) {
        switch (kind) {
            case HOME: return HOME_BOX;
            case TERMINAL: return TERMINAL_BOX;
            case DISPLAY: return DISPLAY_BOX;
            default: return ASSISTANT_BOX;
        }
    }

    /** Whether the kind's glyph shows its number: Home's page and Terminal's window do. */
    static boolean showsNumber(@NonNull PageKind kind) {
        return kind == PageKind.HOME || kind == PageKind.TERMINAL;
    }
}
