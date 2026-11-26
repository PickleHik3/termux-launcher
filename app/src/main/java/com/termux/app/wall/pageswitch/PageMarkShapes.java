package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * The Marks look's shapes, as numbers. Every shape is given for a box {@code size} wide, relative
 * to the box's centre (y down), so the look only applies them and tests can pin them without a
 * canvas. Design units are a 24-unit box ({@link #VIEWPORT}); the answers are those units scaled
 * by {@code size / VIEWPORT}.
 *
 * <p>The marks: Home is a dot, a Terminal window a filled rounded square, a Display a filled
 * screen, the Assistant a spark, Overview two upright cards; a window's number is cut out of its
 * square or screen. The current page wears the same mark inside a capsule ring ({@link #ring}).
 * Attention is a small burst beside the mark. The squares, the screen and the cards are given on a
 * 16-unit box ({@link #BOX}); the dot, the spark, the prompt and the burst on the 24-unit one.
 */
public final class PageMarkShapes {

    public static final float VIEWPORT = 24f;
    /** The dot's radius, in units. */
    public static final float DOT_R = 3.5f;
    /** The units of the squares', screen's, cards' and ring's box; their figures are given in it. */
    public static final float BOX = 16f;
    /** Stroke width of the prompt, in units. */
    public static final float STROKE = 2f;
    /** Floats in {@link #prompt}'s answer: the chevron's three points, then the underscore's two. */
    public static final int PROMPT_FLOATS = 10;
    /** Floats in a rectangle's answer: l, t, r, b relative to the box's centre. */
    public static final int RECT_FLOATS = 4;
    /** The Terminal's square (x, y, w, h and corner, in {@link #BOX} units). */
    public static final float TERMINAL_X = 0.5f, TERMINAL_Y = 1.5f, TERMINAL_W = 15f,
        TERMINAL_H = 13f, TERMINAL_CORNER = 3.6f;
    /** The screen's frame and stand (x, y, w, h and corner, in {@link #BOX} units). */
    public static final float SCREEN_X = 0.5f, SCREEN_Y = 1f, SCREEN_W = 15f, SCREEN_H = 10.5f,
        SCREEN_CORNER = 2.6f;
    public static final float STAND_X = 5f, STAND_Y = 13f, STAND_W = 6f, STAND_H = 1.8f,
        STAND_CORNER = 0.9f;
    /** Overview's two cards (x, y, w, h and corner, in {@link #BOX} units); the second's x differs. */
    public static final int OVERVIEW_CARDS = 2;
    public static final float CARD_LEFT_X = 1.5f, CARD_RIGHT_X = 9f, CARD_Y = 3f, CARD_W = 5.5f,
        CARD_H = 10f, CARD_CORNER = 1.6f;
    /** The current page's ring: width and height as shares of the box, its stroke's share and floor. */
    public static final float RING_W = 1.625f, RING_H = 1.25f, RING_STROKE = 1f / 16f;
    public static final float RING_MIN_STROKE_PX = 1f;
    /** A cut-out number's text size as a share of the box, in a Terminal's square and a screen. */
    public static final float TERMINAL_NUMBER_TEXT = 9f / 16f;
    public static final float SCREEN_NUMBER_TEXT = 8f / 16f;
    /** A two-digit number's text is its one-digit size scaled by this. */
    public static final float TWO_DIGIT_SCALE = 0.8f;
    /** An app icon is fitted into a square, and a glyph is set at a text size, of this share. */
    public static final float ICON_SHARE = 0.875f;
    /** The cut-out layer reaches this far, in px, past its shape so the soft edge is not clipped. */
    public static final float LAYER_PAD_PX = 1f;
    /** Floats of the spark and the burst, both star polygons of 8 points, x then y. */
    public static final int STAR_FLOATS = 16;
    /** The spark's outer and inner radius, in units. */
    public static final float SPARK_OUTER = 9f;
    public static final float SPARK_INNER = 2.5f;
    /** The burst's outer and inner radius, in units, and where its centre sits from the box's centre. */
    public static final float BURST_OUTER = 4.5f;
    public static final float BURST_INNER = 2f;
    public static final float BURST_OFFSET = 7.4f;
    /** The Terminal's prompt, cut out of its square when it has no number, is scaled by this. */
    public static final float SIGN_SCALE = 0.62f;

    private PageMarkShapes() {
    }

    private static float unit(float size) {
        return size / VIEWPORT;
    }

    public static float dotRadius(float size) {
        return DOT_R * unit(size);
    }

    public static float strokeWidth(float size) {
        return STROKE * unit(size);
    }

    private static float box(float size) {
        return size / BOX;
    }

    public static float terminalCorner(float size) {
        return TERMINAL_CORNER * box(size);
    }

    public static float screenCorner(float size) {
        return SCREEN_CORNER * box(size);
    }

    public static float standCorner(float size) {
        return STAND_CORNER * box(size);
    }

    public static float cardCorner(float size) {
        return CARD_CORNER * box(size);
    }

    /** The ring's corner radius: half its height, so it is a capsule. */
    public static float ringCorner(float size) {
        return RING_H * size / 2f;
    }

    /** The ring's stroke width, never under one pixel. */
    public static float ringStroke(float size) {
        return Math.max(RING_MIN_STROKE_PX, RING_STROKE * size);
    }

    /** Whether {@code kind} shows its window's number cut out of its mark. */
    public static boolean cutsNumber(@NonNull PageKind kind, int number) {
        return number > 0 && (kind == PageKind.TERMINAL || kind == PageKind.DISPLAY
            || kind == PageKind.LINUX);
    }

    /** Whether {@code kind} with no number shows the prompt cut out of its square. */
    public static boolean cutsPrompt(@NonNull PageKind kind, int number) {
        return kind == PageKind.TERMINAL && number <= 0;
    }

    /** The text size of the number cut out of {@code kind}'s mark, by its digit count. */
    public static float numberTextSize(@NonNull PageKind kind, int digits, float size) {
        float share = kind == PageKind.TERMINAL ? TERMINAL_NUMBER_TEXT : SCREEN_NUMBER_TEXT;
        return size * share * (digits > 1 ? TWO_DIGIT_SCALE : 1f);
    }

    /** The Terminal's square, as l, t, r, b about the box's centre. */
    public static void terminal(float size, @NonNull float[] out) {
        rect(size, TERMINAL_X, TERMINAL_Y, TERMINAL_W, TERMINAL_H, out);
    }

    /** The screen's frame, as l, t, r, b about the box's centre. */
    public static void screenFrame(float size, @NonNull float[] out) {
        rect(size, SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, out);
    }

    /** The screen's stand, as l, t, r, b about the box's centre. */
    public static void screenStand(float size, @NonNull float[] out) {
        rect(size, STAND_X, STAND_Y, STAND_W, STAND_H, out);
    }

    /** Overview's card {@code index} (0 left, 1 right), as l, t, r, b about the box's centre. */
    public static void overviewCard(float size, int index, @NonNull float[] out) {
        rect(size, index == 0 ? CARD_LEFT_X : CARD_RIGHT_X, CARD_Y, CARD_W, CARD_H, out);
    }

    /** The current page's ring, as l, t, r, b about the box's centre. */
    public static void ring(float size, @NonNull float[] out) {
        out[0] = -RING_W * size / 2f;
        out[1] = -RING_H * size / 2f;
        out[2] = RING_W * size / 2f;
        out[3] = RING_H * size / 2f;
    }

    /** A rectangle given in {@link #BOX} units from its corner, as l, t, r, b about the centre. */
    private static void rect(float size, float x, float y, float w, float h, float[] out) {
        float u = box(size);
        out[0] = (x - BOX / 2f) * u;
        out[1] = (y - BOX / 2f) * u;
        out[2] = (x + w - BOX / 2f) * u;
        out[3] = (y + h - BOX / 2f) * u;
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
