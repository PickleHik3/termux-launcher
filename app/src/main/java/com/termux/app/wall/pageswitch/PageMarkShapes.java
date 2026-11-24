package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * The Marks look's shapes, as numbers. Every shape is given for a box {@code size} wide, relative
 * to the box's centre (y down), so the look only applies them and tests can pin them without a
 * canvas. Design units are a 24-unit box ({@link #VIEWPORT}); the answers are those units scaled
 * by {@code size / VIEWPORT}.
 *
 * <p>The marks: Home is a dot, a Terminal window a prompt with its underscore, a Display a screen,
 * the Assistant a spark. The current page is a solid disc ({@link #DISC_R}) with its number, or its
 * kind's sign when it has none, cut out of it; attention is a small burst beside the mark.
 * Overview's two-cards mark is a later kind and joins {@link #hasSign}.
 */
public final class PageMarkShapes {

    public static final float VIEWPORT = 24f;
    /** The dot's radius, in units. */
    public static final float DOT_R = 3.5f;
    /** The current page's disc radius, in units. */
    public static final float DISC_R = 10f;
    /** Stroke width of the prompt and the screen, in units. */
    public static final float STROKE = 2f;
    /** Floats in {@link #prompt}'s answer: the chevron's three points, then the underscore's two. */
    public static final int PROMPT_FLOATS = 10;
    /** Floats in {@link #screen}'s answer: the frame's l, t, r, b then the stand's x0, y0, x1, y1. */
    public static final int SCREEN_FLOATS = 8;
    /** The screen frame's corner radius, in units. */
    public static final float SCREEN_CORNER = 2.5f;
    /** Floats of the spark and the burst, both star polygons of 8 points, x then y. */
    public static final int STAR_FLOATS = 16;
    /** The spark's outer and inner radius, in units. */
    public static final float SPARK_OUTER = 9f;
    public static final float SPARK_INNER = 2.5f;
    /** The burst's outer and inner radius, in units, and where its centre sits from the box's centre. */
    public static final float BURST_OUTER = 4.5f;
    public static final float BURST_INNER = 2f;
    public static final float BURST_OFFSET = 7.4f;
    /** A sign cut out of the disc is its mark scaled by this about the centre. */
    public static final float SIGN_SCALE = 0.62f;
    /** A cut-out number's text size, as a share of the box. */
    public static final float NUMBER_TEXT = 0.55f;

    private PageMarkShapes() {
    }

    private static float unit(float size) {
        return size / VIEWPORT;
    }

    public static float dotRadius(float size) {
        return DOT_R * unit(size);
    }

    public static float discRadius(float size) {
        return DISC_R * unit(size);
    }

    public static float strokeWidth(float size) {
        return STROKE * unit(size);
    }

    public static float screenCorner(float size) {
        return SCREEN_CORNER * unit(size);
    }

    /** Whether {@code kind} has a sign of its own to cut out of its disc when it has no number. */
    public static boolean hasSign(@NonNull PageKind kind) {
        return kind == PageKind.TERMINAL || kind == PageKind.DISPLAY || kind == PageKind.ASSISTANT;
    }

    /**
     * Whether the current page's disc cuts out {@code number} (Home always; a window or a display
     * when it has one) rather than its kind's sign.
     */
    public static boolean cutsNumber(@NonNull PageKind kind, int number) {
        return kind == PageKind.HOME || (hasSign(kind) && number > 0);
    }

    /**
     * The prompt: a chevron's three points (x0, y0 .. x2, y2) then the underscore's two
     * (x3, y3, x4, y4), stroked {@link #strokeWidth} wide. {@code scale} shrinks it about the centre.
     */
    public static void prompt(float size, float scale, @NonNull float[] out) {
        float u = unit(size) * scale;
        out[0] = -6f * u; out[1] = -5f * u;
        out[2] = -1f * u; out[3] = 0f;
        out[4] = -6f * u; out[5] = 5f * u;
        out[6] = 2f * u; out[7] = 5f * u;
        out[8] = 8f * u; out[9] = 5f * u;
    }

    /** The screen: its frame (l, t, r, b), then its stand (x0, y0, x1, y1). */
    public static void screen(float size, float scale, @NonNull float[] out) {
        float u = unit(size) * scale;
        out[0] = -9f * u; out[1] = -7f * u; out[2] = 9f * u; out[3] = 4f * u;
        out[4] = -4f * u; out[5] = 8f * u; out[6] = 4f * u; out[7] = 8f * u;
    }

    /** The spark, a four-pointed star polygon: points alternate outer (N, E, S, W) and inner. */
    public static void spark(float size, float scale, @NonNull float[] out) {
        star(0f, 0f, SPARK_OUTER * unit(size) * scale, SPARK_INNER * unit(size) * scale, out);
    }

    /** The attention burst, a small star polygon at the box's top-right corner. */
    public static void burst(float size, @NonNull float[] out) {
        float u = unit(size);
        star(BURST_OFFSET * u, -BURST_OFFSET * u, BURST_OUTER * u, BURST_INNER * u, out);
    }

    private static void star(float cx, float cy, float outer, float inner, float[] out) {
        for (int i = 0; i < 8; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 4;
            float r = i % 2 == 0 ? outer : inner;
            out[2 * i] = cx + (float) (r * Math.cos(angle));
            out[2 * i + 1] = cy + (float) (r * Math.sin(angle));
        }
    }
}
