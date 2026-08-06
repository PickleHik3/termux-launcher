package com.termux.view;

/**
 * Cell geometry for the code points a terminal is expected to draw itself.
 *
 * <p>Fonts disagree about box drawing. A face designed for prose leaves a background-coloured seam
 * between two adjacent {@code ─}, puts the crossbar of {@code ┼} off the centreline of {@code │},
 * and usually covers none of the block, braille or legacy-computing ranges at all, so a TUI drawn
 * with it looks perforated. Every glyph in those ranges is a handful of rectangles, so the renderer
 * computes them from the cell instead of asking for a glyph, snapped to the integer pixel
 * boundaries that neighbouring cells already share.
 *
 * <p>This class holds only arithmetic and classification — no Android graphics types — so the join,
 * thickness and tiling rules can be unit tested directly. The renderer owns one {@link Segments}
 * and hands it back for every cell, so a frame full of box drawing allocates nothing.
 */
public final class BoxGeometry {

    /** Line weight indices into the configured thickness scale table. */
    public static final int THIN = 0;
    public static final int LIGHT = 1;
    public static final int HEAVY = 2;
    public static final int VERY_HEAVY = 3;

    /**
     * Default multipliers for {@link #THIN}, {@link #LIGHT}, {@link #HEAVY} and
     * {@link #VERY_HEAVY}, applied to a base stroke derived from the cell height. {@code THIN} is
     * deliberately near zero: it names a hairline, which the one-pixel floor then produces at any
     * font size. {@code VERY_HEAVY} is exposed for configuration but no code point selects it yet.
     */
    public static final float[] DEFAULT_THICKNESS_SCALES = {0.001f, 1f, 1.5f, 2f};

    /** Arm directions, as indexed by {@link #BOX_ARMS}. */
    private static final int ARM_UP = 0;
    private static final int ARM_RIGHT = 1;
    private static final int ARM_DOWN = 2;
    private static final int ARM_LEFT = 3;

    /** Arm weights, in ascending ink order; {@code WEIGHT_NONE} means the arm is absent. */
    private static final int WEIGHT_NONE = 0;
    private static final int WEIGHT_LIGHT = 1;
    private static final int WEIGHT_HEAVY = 2;
    private static final int WEIGHT_DOUBLE = 3;

    /**
     * The four arms of every U+2500-U+257F glyph as up, right, down, left, four characters per code
     * point: {@code .} absent, {@code l} light, {@code h} heavy, {@code d} double. Dashed, arc,
     * diagonal and double-line glyphs carry their arms here for classification but are drawn by
     * their own cases in {@link #fillBoxDrawing}.
     */
    private static final String BOX_ARMS =
        ".l.l" + // 2500 ─ light horizontal
        ".h.h" + // 2501 ━ heavy horizontal
        "l.l." + // 2502 │ light vertical
        "h.h." + // 2503 ┃ heavy vertical
        ".l.l" + // 2504 ┄ light triple dash horizontal
        ".h.h" + // 2505 ┅ heavy triple dash horizontal
        "l.l." + // 2506 ┆ light triple dash vertical
        "h.h." + // 2507 ┇ heavy triple dash vertical
        ".l.l" + // 2508 ┈ light quadruple dash horizontal
        ".h.h" + // 2509 ┉ heavy quadruple dash horizontal
        "l.l." + // 250A ┊ light quadruple dash vertical
        "h.h." + // 250B ┋ heavy quadruple dash vertical
        ".ll." + // 250C ┌ light down and right
        ".hl." + // 250D ┍ down light and right heavy
        ".lh." + // 250E ┎ down heavy and right light
        ".hh." + // 250F ┏ heavy down and right
        "..ll" + // 2510 ┐ light down and left
        "..lh" + // 2511 ┑ down light and left heavy
        "..hl" + // 2512 ┒ down heavy and left light
        "..hh" + // 2513 ┓ heavy down and left
        "ll.." + // 2514 └ light up and right
        "lh.." + // 2515 ┕ up light and right heavy
        "hl.." + // 2516 ┖ up heavy and right light
        "hh.." + // 2517 ┗ heavy up and right
        "l..l" + // 2518 ┘ light up and left
        "l..h" + // 2519 ┙ up light and left heavy
        "h..l" + // 251A ┚ up heavy and left light
        "h..h" + // 251B ┛ heavy up and left
        "lll." + // 251C ├ light vertical and right
        "lhl." + // 251D ┝ vertical light and right heavy
        "hll." + // 251E ┞ up heavy and right down light
        "llh." + // 251F ┟ down heavy and right up light
        "hlh." + // 2520 ┠ vertical heavy and right light
        "hhl." + // 2521 ┡ down light and right up heavy
        "lhh." + // 2522 ┢ up light and right down heavy
        "hhh." + // 2523 ┣ heavy vertical and right
        "l.ll" + // 2524 ┤ light vertical and left
        "l.lh" + // 2525 ┥ vertical light and left heavy
        "h.ll" + // 2526 ┦ up heavy and left down light
        "l.hl" + // 2527 ┧ down heavy and left up light
        "h.hl" + // 2528 ┨ vertical heavy and left light
        "h.lh" + // 2529 ┩ down light and left up heavy
        "l.hh" + // 252A ┪ up light and left down heavy
        "h.hh" + // 252B ┫ heavy vertical and left
        ".lll" + // 252C ┬ light down and horizontal
        ".llh" + // 252D ┭ left heavy and right down light
        ".hll" + // 252E ┮ right heavy and left down light
        ".hlh" + // 252F ┯ down light and horizontal heavy
        ".lhl" + // 2530 ┰ down heavy and horizontal light
        ".lhh" + // 2531 ┱ right light and left down heavy
        ".hhl" + // 2532 ┲ left light and right down heavy
        ".hhh" + // 2533 ┳ heavy down and horizontal
        "ll.l" + // 2534 ┴ light up and horizontal
        "ll.h" + // 2535 ┵ left heavy and right up light
        "lh.l" + // 2536 ┶ right heavy and left up light
        "lh.h" + // 2537 ┷ up light and horizontal heavy
        "hl.l" + // 2538 ┸ up heavy and horizontal light
        "hl.h" + // 2539 ┹ right light and left up heavy
        "hh.l" + // 253A ┺ left light and right up heavy
        "hh.h" + // 253B ┻ heavy up and horizontal
        "llll" + // 253C ┼ light vertical and horizontal
        "lllh" + // 253D ┽ left heavy and right vertical light
        "lhll" + // 253E ┾ right heavy and left vertical light
        "lhlh" + // 253F ┿ vertical light and horizontal heavy
        "hlll" + // 2540 ╀ up heavy and down horizontal light
        "llhl" + // 2541 ╁ down heavy and up horizontal light
        "hlhl" + // 2542 ╂ vertical heavy and horizontal light
        "hllh" + // 2543 ╃ left up heavy and right down light
        "hhll" + // 2544 ╄ right up heavy and left down light
        "llhh" + // 2545 ╅ left down heavy and right up light
        "lhhl" + // 2546 ╆ right down heavy and left up light
        "hhlh" + // 2547 ╇ down light and up horizontal heavy
        "lhhh" + // 2548 ╈ up light and down horizontal heavy
        "hlhh" + // 2549 ╉ right light and left vertical heavy
        "hhhl" + // 254A ╊ left light and right vertical heavy
        "hhhh" + // 254B ╋ heavy vertical and horizontal
        ".l.l" + // 254C ╌ light double dash horizontal
        ".h.h" + // 254D ╍ heavy double dash horizontal
        "l.l." + // 254E ╎ light double dash vertical
        "h.h." + // 254F ╏ heavy double dash vertical
        ".d.d" + // 2550 ═ double horizontal
        "d.d." + // 2551 ║ double vertical
        ".dl." + // 2552 ╒ down single and right double
        ".ld." + // 2553 ╓ down double and right single
        ".dd." + // 2554 ╔ double down and right
        "..ld" + // 2555 ╕ down single and left double
        "..dl" + // 2556 ╖ down double and left single
        "..dd" + // 2557 ╗ double down and left
        "ld.." + // 2558 ╘ up single and right double
        "dl.." + // 2559 ╙ up double and right single
        "dd.." + // 255A ╚ double up and right
        "l..d" + // 255B ╛ up single and left double
        "d..l" + // 255C ╜ up double and left single
        "d..d" + // 255D ╝ double up and left
        "ldl." + // 255E ╞ vertical single and right double
        "dld." + // 255F ╟ vertical double and right single
        "ddd." + // 2560 ╠ double vertical and right
        "l.ld" + // 2561 ╡ vertical single and left double
        "d.dl" + // 2562 ╢ vertical double and left single
        "d.dd" + // 2563 ╣ double vertical and left
        ".dld" + // 2564 ╤ down single and horizontal double
        ".ldl" + // 2565 ╥ down double and horizontal single
        ".ddd" + // 2566 ╦ double down and horizontal
        "ld.d" + // 2567 ╧ up single and horizontal double
        "dl.l" + // 2568 ╨ up double and horizontal single
        "dd.d" + // 2569 ╩ double up and horizontal
        "ldld" + // 256A ╪ vertical single and horizontal double
        "dldl" + // 256B ╫ vertical double and horizontal single
        "dddd" + // 256C ╬ double vertical and horizontal
        ".ll." + // 256D ╭ light arc down and right
        "..ll" + // 256E ╮ light arc down and left
        "l..l" + // 256F ╯ light arc up and left
        "ll.." + // 2570 ╰ light arc up and right
        "...." + // 2571 ╱ light diagonal upper right to lower left
        "...." + // 2572 ╲ light diagonal upper left to lower right
        "...." + // 2573 ╳ light diagonal cross
        "...l" + // 2574 ╴ light left
        "l..." + // 2575 ╵ light up
        ".l.." + // 2576 ╶ light right
        "..l." + // 2577 ╷ light down
        "...h" + // 2578 ╸ heavy left
        "h..." + // 2579 ╹ heavy up
        ".h.." + // 257A ╺ heavy right
        "..h." + // 257B ╻ heavy down
        ".h.l" + // 257C ╼ light left and heavy right
        "l.h." + // 257D ╽ light up and heavy down
        ".l.h" + // 257E ╾ heavy left and light right
        "h.l.";  // 257F ╿ heavy up and light down

    private BoxGeometry() {
    }

    /**
     * The ink of one cell, as flat primitive arrays the renderer walks without allocating. Every
     * rectangle coordinate is a shared integer cell boundary; stroked and round forms keep float
     * coordinates because they are centred on a band rather than bounded by one.
     */
    public static final class Segments {

        private static final int MAX_RECTS = 12;
        private static final int MAX_SHADES = 1;
        private static final int MAX_DASHES = 1;
        private static final int MAX_DIAGONALS = 2;
        private static final int MAX_ARCS = 1;
        private static final int MAX_POLYGONS = 1;
        private static final int MAX_POLYGON_POINTS = 4;
        private static final int MAX_CAPS = 1;
        private static final int MAX_DOTS = 8;

        /** Number of valid entries in {@link #rects}. */
        public int rectCount;

        /** Solid rectangles as left, top, right, bottom quadruples. */
        public final int[] rects = new int[4 * MAX_RECTS];

        /** Number of valid entries in {@link #shadeRects} and {@link #shadeAlphaPercents}. */
        public int shadeCount;

        /** Rectangles filled with the foreground at a reduced alpha, as left, top, right, bottom. */
        public final int[] shadeRects = new int[4 * MAX_SHADES];

        /** Foreground alpha in percent for the matching entry of {@link #shadeRects}. */
        public final int[] shadeAlphaPercents = new int[MAX_SHADES];

        /** Number of valid entries in {@link #dashRuns}. */
        public int dashCount;

        /**
         * Dashed bands as left, top, right, bottom, period, onLength sextuples. Dashes advance
         * along the band's long axis from its low edge, the last one clipped to the band.
         */
        public final int[] dashRuns = new int[6 * MAX_DASHES];

        /** Number of valid entries in {@link #diagonals}. */
        public int diagonalCount;

        /** Diagonal line endpoints as x0, y0, x1, y1, stroked with {@link #strokeThickness}. */
        public final float[] diagonals = new float[4 * MAX_DIAGONALS];

        /** Number of valid entries in {@link #arcs}. */
        public int arcCount;

        /**
         * Quadratic arcs as x0, y0, controlX, controlY, x1, y1, stroked with
         * {@link #strokeThickness}. The endpoints sit on cell edges so a rounded corner joins the
         * straight line in its neighbour.
         */
        public final float[] arcs = new float[6 * MAX_ARCS];

        /** Number of valid entries in {@link #polygonSizes}. */
        public int polygonCount;

        /** Vertex count of each polygon, in order, indexing into {@link #polygonPoints}. */
        public final int[] polygonSizes = new int[MAX_POLYGONS];

        /** Filled polygon vertices as x, y pairs, one polygon after another. */
        public final float[] polygonPoints = new float[2 * MAX_POLYGON_POINTS * MAX_POLYGONS];

        /** Number of valid entries in {@link #caps}. */
        public int capCount;

        /**
         * Half-disc caps as ovalLeft, ovalTop, ovalRight, ovalBottom, startAngle, sweepAngle. The
         * oval is wider than the cell; only the swept half lands inside it.
         */
        public final float[] caps = new float[6 * MAX_CAPS];

        /** Whether {@link #caps} are filled rather than stroked with {@link #strokeThickness}. */
        public boolean capFilled;

        /** Number of valid dot centres in {@link #dots}. */
        public int dotCount;

        /** Braille dot centres as x, y pairs. */
        public final float[] dots = new float[2 * MAX_DOTS];

        /** Radius of every entry in {@link #dots}; at least one pixel. */
        public float dotRadius;

        /** Stroke width for {@link #diagonals}, {@link #arcs} and stroked {@link #caps}. */
        public float strokeThickness;

        /** Forget the previous cell without releasing any array. */
        public void reset() {
            rectCount = 0;
            shadeCount = 0;
            dashCount = 0;
            diagonalCount = 0;
            arcCount = 0;
            polygonCount = 0;
            capCount = 0;
            capFilled = false;
            dotCount = 0;
            dotRadius = 0f;
            strokeThickness = 0f;
        }
    }

    /**
     * The shared integer pixel boundary before cell {@code index} along one axis.
     *
     * <p>Both cells that meet at a boundary derive it from the same expression, so cell N's far
     * edge and cell N+1's near edge are the identical integer and no seam of background can appear
     * between them however the fractional cell size falls.
     */
    public static int edge(float origin, float cellSize, int index) {
        return Math.round(origin + index * cellSize);
    }

    /** The unscaled stroke width every line weight is derived from. */
    private static float baseStroke(int cellHeight) {
        return Math.max(1f, cellHeight / 16f);
    }

    /**
     * The quantized stroke width of one line weight, at least one pixel and never more than a
     * third of the cell's smaller dimension so that a heavy cross still leaves its cell readable.
     */
    public static int thickness(int cellWidth, int cellHeight, float[] scales, int weight) {
        final float[] table = table(scales);
        final int limit = Math.max(1, Math.min(cellWidth, cellHeight) / 3);
        return Math.max(1, Math.min(limit, Math.round(baseStroke(cellHeight) * table[weight])));
    }

    private static float[] table(float[] scales) {
        return scales == null || scales.length < 4 ? DEFAULT_THICKNESS_SCALES : scales;
    }

    /**
     * Whether {@code codePoint} is drawn from geometry rather than from a font glyph.
     *
     * <p>Only the sub-ranges {@link #fill} actually implements are claimed: within the
     * legacy-computing block that is the sextants and the eighth-block family, not the wedges,
     * inverse shades, pattern fills or segmented digits.
     */
    public static boolean isSynthesizable(int codePoint, boolean powerline) {
        if (codePoint >= 0x2500 && codePoint <= 0x259F) return true;
        if (codePoint >= 0x25E2 && codePoint <= 0x25E5) return true;
        if (codePoint >= 0x2800 && codePoint <= 0x28FF) return true;
        if (codePoint >= 0x1FB00 && codePoint <= 0x1FB3B) return true;
        if (codePoint >= 0x1FB70 && codePoint <= 0x1FB8F) return true;
        return powerline && ((codePoint >= 0xE0B0 && codePoint <= 0xE0B7)
            || (codePoint >= 0xE0BA && codePoint <= 0xE0BD));
    }

    /**
     * Describe the ink of {@code codePoint} in the cell bounded by the given integer pixel edges.
     *
     * @param scales     the four line-weight multipliers; null selects {@link #DEFAULT_THICKNESS_SCALES}.
     * @param powerline  whether the private-use separator ranges are claimed.
     * @param out        filled with this cell's ink; its previous contents are discarded.
     * @return whether the code point was handled. A handled cell can still be empty, as U+2800 is.
     */
    public static boolean fill(int codePoint, int left, int top, int right, int bottom,
                               float[] scales, boolean powerline, Segments out) {
        out.reset();
        if (right <= left || bottom <= top) return false;
        final float[] weights = table(scales);
        if (codePoint >= 0x2500 && codePoint <= 0x257F)
            return fillBoxDrawing(codePoint, left, top, right, bottom, weights, out);
        if (codePoint >= 0x2580 && codePoint <= 0x259F)
            return fillBlock(codePoint, left, top, right, bottom, out);
        if (codePoint >= 0x25E2 && codePoint <= 0x25E5)
            return fillCornerTriangle(codePoint, left, top, right, bottom, out);
        if (codePoint >= 0x2800 && codePoint <= 0x28FF)
            return fillBraille(codePoint, left, top, right, bottom, out);
        if (codePoint >= 0x1FB00 && codePoint <= 0x1FB3B)
            return fillSextant(codePoint, left, top, right, bottom, out);
        if (codePoint >= 0x1FB70 && codePoint <= 0x1FB8F)
            return fillLegacyBlock(codePoint, left, top, right, bottom, out);
        if (powerline && ((codePoint >= 0xE0B0 && codePoint <= 0xE0B7)
            || (codePoint >= 0xE0BA && codePoint <= 0xE0BD)))
            return fillPowerline(codePoint, left, top, right, bottom, weights, out);
        return false;
    }

    // ---------------------------------------------------------------- primitives

    private static void addRect(Segments out, int left, int top, int right, int bottom) {
        if (out.rectCount >= Segments.MAX_RECTS) return;
        final int offset = out.rectCount * 4;
        out.rects[offset] = left;
        out.rects[offset + 1] = top;
        out.rects[offset + 2] = right;
        out.rects[offset + 3] = bottom;
        out.rectCount++;
    }

    private static void addShade(Segments out, int left, int top, int right, int bottom,
                                int alphaPercent) {
        if (out.shadeCount >= Segments.MAX_SHADES) return;
        final int offset = out.shadeCount * 4;
        out.shadeRects[offset] = left;
        out.shadeRects[offset + 1] = top;
        out.shadeRects[offset + 2] = right;
        out.shadeRects[offset + 3] = bottom;
        out.shadeAlphaPercents[out.shadeCount] = alphaPercent;
        out.shadeCount++;
    }

    /**
     * A dashed band. The period is an integer division of the band's span, so every cell of a long
     * dashed rule starts its pattern at the same offset instead of drifting across the screen.
     */
    private static void addDash(Segments out, int left, int top, int right, int bottom, int dashes) {
        if (out.dashCount >= Segments.MAX_DASHES) return;
        final boolean horizontal = (right - left) >= (bottom - top);
        final int span = horizontal ? right - left : bottom - top;
        final int period = Math.max(2, span / Math.max(1, dashes));
        final int onLength = Math.max(1, period * 2 / 3);
        final int offset = out.dashCount * 6;
        out.dashRuns[offset] = left;
        out.dashRuns[offset + 1] = top;
        out.dashRuns[offset + 2] = right;
        out.dashRuns[offset + 3] = bottom;
        out.dashRuns[offset + 4] = period;
        out.dashRuns[offset + 5] = onLength;
        out.dashCount++;
    }

    private static void addDiagonal(Segments out, float x0, float y0, float x1, float y1) {
        if (out.diagonalCount >= Segments.MAX_DIAGONALS) return;
        final int offset = out.diagonalCount * 4;
        out.diagonals[offset] = x0;
        out.diagonals[offset + 1] = y0;
        out.diagonals[offset + 2] = x1;
        out.diagonals[offset + 3] = y1;
        out.diagonalCount++;
    }

    private static void addArc(Segments out, float x0, float y0, float controlX, float controlY,
                              float x1, float y1) {
        if (out.arcCount >= Segments.MAX_ARCS) return;
        final int offset = out.arcCount * 6;
        out.arcs[offset] = x0;
        out.arcs[offset + 1] = y0;
        out.arcs[offset + 2] = controlX;
        out.arcs[offset + 3] = controlY;
        out.arcs[offset + 4] = x1;
        out.arcs[offset + 5] = y1;
        out.arcCount++;
    }

    private static void addTriangle(Segments out, float x0, float y0, float x1, float y1,
                                   float x2, float y2) {
        if (out.polygonCount >= Segments.MAX_POLYGONS) return;
        int offset = 0;
        for (int i = 0; i < out.polygonCount; i++) offset += out.polygonSizes[i] * 2;
        out.polygonPoints[offset] = x0;
        out.polygonPoints[offset + 1] = y0;
        out.polygonPoints[offset + 2] = x1;
        out.polygonPoints[offset + 3] = y1;
        out.polygonPoints[offset + 4] = x2;
        out.polygonPoints[offset + 5] = y2;
        out.polygonSizes[out.polygonCount] = 3;
        out.polygonCount++;
    }

    private static void addCap(Segments out, float ovalLeft, float ovalTop, float ovalRight,
                               float ovalBottom, float startAngle, float sweepAngle) {
        if (out.capCount >= Segments.MAX_CAPS) return;
        final int offset = out.capCount * 6;
        out.caps[offset] = ovalLeft;
        out.caps[offset + 1] = ovalTop;
        out.caps[offset + 2] = ovalRight;
        out.caps[offset + 3] = ovalBottom;
        out.caps[offset + 4] = startAngle;
        out.caps[offset + 5] = sweepAngle;
        out.capCount++;
    }

    private static void addDot(Segments out, float x, float y) {
        if (out.dotCount >= Segments.MAX_DOTS) return;
        final int offset = out.dotCount * 2;
        out.dots[offset] = x;
        out.dots[offset + 1] = y;
        out.dotCount++;
    }

    /**
     * The low edge of a stroke of thickness {@code t} straddling {@code center}. Its high edge is
     * this value plus {@code t}, so both are integers and a cross's two halves share one centre.
     */
    private static int strokeStart(float center, int thickness) {
        return Math.round(center - thickness / 2f);
    }

    /** The integer boundary {@code numerator}/{@code denominator} of the way across a span. */
    private static int fraction(int start, int size, int numerator, int denominator) {
        return Math.round(start + size * (float) numerator / denominator);
    }

    // ---------------------------------------------------------------- U+2500-U+257F

    private static int armWeight(int codePoint, int direction) {
        switch (BOX_ARMS.charAt((codePoint - 0x2500) * 4 + direction)) {
            case 'l': return WEIGHT_LIGHT;
            case 'h': return WEIGHT_HEAVY;
            case 'd': return WEIGHT_DOUBLE;
            default: return WEIGHT_NONE;
        }
    }

    /** The number of dashes a dashed variant repeats across its cell, or zero for a solid line. */
    private static int dashes(int codePoint) {
        switch (codePoint) {
            case 0x2504: case 0x2505: case 0x2506: case 0x2507: return 3;
            case 0x2508: case 0x2509: case 0x250A: case 0x250B: return 4;
            case 0x254C: case 0x254D: case 0x254E: case 0x254F: return 2;
            default: return 0;
        }
    }

    private static boolean fillBoxDrawing(int codePoint, int left, int top, int right, int bottom,
                                          float[] scales, Segments out) {
        if (codePoint >= 0x2550 && codePoint <= 0x256C)
            return fillDouble(codePoint, left, top, right, bottom, scales, out);
        if (codePoint >= 0x256D && codePoint <= 0x2570)
            return fillArc(codePoint, left, top, right, bottom, scales, out);
        if (codePoint >= 0x2571 && codePoint <= 0x2573)
            return fillDiagonal(codePoint, left, top, right, bottom, scales, out);
        return fillArms(codePoint, left, top, right, bottom, scales, out);
    }

    /**
     * Light and heavy lines, corners, tees and crosses.
     *
     * <p>Each arm is its own rectangle of its own thickness, straddling the cell centre. An arm
     * that continues into a neighbour reaches the cell edge exactly; an arm that stops inside the
     * cell stops at the far side of the crossing band, which is where a corner would have turned.
     * Every arm of a junction is therefore placed from one shared centre, so {@code ┼} lines up
     * with the {@code ─} and {@code │} in the cells around it.
     */
    private static boolean fillArms(int codePoint, int left, int top, int right, int bottom,
                                    float[] scales, Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        final float centerX = (left + right) / 2f;
        final float centerY = (top + bottom) / 2f;
        final int up = armThickness(codePoint, ARM_UP, width, height, scales);
        final int rightArm = armThickness(codePoint, ARM_RIGHT, width, height, scales);
        final int down = armThickness(codePoint, ARM_DOWN, width, height, scales);
        final int leftArm = armThickness(codePoint, ARM_LEFT, width, height, scales);
        final int dashes = dashes(codePoint);
        if (dashes > 0) {
            if (leftArm > 0) {
                final int rowTop = strokeStart(centerY, leftArm);
                addDash(out, left, rowTop, right, rowTop + leftArm, dashes);
            } else {
                final int columnLeft = strokeStart(centerX, up);
                addDash(out, columnLeft, top, columnLeft + up, bottom, dashes);
            }
            return true;
        }
        // The crossing band of the perpendicular arms; a stub with no perpendicular arm falls back
        // to a notional band of its own thickness so that ╴ and ┌ terminate at the same place.
        final int verticalSpan = Math.max(up, down) > 0
            ? Math.max(up, down) : Math.max(leftArm, rightArm);
        final int horizontalSpan = Math.max(leftArm, rightArm) > 0
            ? Math.max(leftArm, rightArm) : Math.max(up, down);
        final int bandLeft = strokeStart(centerX, verticalSpan);
        final int bandRight = bandLeft + verticalSpan;
        final int bandTop = strokeStart(centerY, horizontalSpan);
        final int bandBottom = bandTop + horizontalSpan;
        if (leftArm > 0) {
            final int rowTop = strokeStart(centerY, leftArm);
            addRect(out, left, rowTop, bandRight, rowTop + leftArm);
        }
        if (rightArm > 0) {
            final int rowTop = strokeStart(centerY, rightArm);
            addRect(out, bandLeft, rowTop, right, rowTop + rightArm);
        }
        if (up > 0) {
            final int columnLeft = strokeStart(centerX, up);
            addRect(out, columnLeft, top, columnLeft + up, bandBottom);
        }
        if (down > 0) {
            final int columnLeft = strokeStart(centerX, down);
            addRect(out, columnLeft, bandTop, columnLeft + down, bottom);
        }
        return true;
    }

    private static int armThickness(int codePoint, int direction, int width, int height,
                                    float[] scales) {
        switch (armWeight(codePoint, direction)) {
            case WEIGHT_LIGHT: return thickness(width, height, scales, LIGHT);
            case WEIGHT_HEAVY: return thickness(width, height, scales, HEAVY);
            default: return 0;
        }
    }

    /**
     * Double lines U+2550-U+256C, as two rails of the light thickness with a gap between them.
     *
     * <p>The rails and the gap are integers summing to the heavy thickness wherever that is
     * possible, so a double rule and a heavy rule occupy the same band and a table drawn from a
     * mixture of the two stays aligned. Each junction is spelled out rather than derived, because
     * which rail turns and which one breaks is what distinguishes ╠ from ╬.
     */
    private static boolean fillDouble(int codePoint, int left, int top, int right, int bottom,
                                      float[] scales, Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        final float centerX = (left + right) / 2f;
        final float centerY = (top + bottom) / 2f;
        final int light = thickness(width, height, scales, LIGHT);
        final int heavy = thickness(width, height, scales, HEAVY);
        final int rail = Math.max(1, Math.min(light, (heavy - 1) / 2));
        final int gap = Math.max(1, heavy - 2 * rail);
        final int span = 2 * rail + gap;
        final int y1 = strokeStart(centerY, span);
        final int y1e = y1 + rail;
        final int y2 = y1 + rail + gap;
        final int y2e = y2 + rail;
        final int x1 = strokeStart(centerX, span);
        final int x1e = x1 + rail;
        final int x2 = x1 + rail + gap;
        final int x2e = x2 + rail;
        // Where one axis is single, its stroke is a normal light line on the shared centre.
        final int single = light;
        final int sy = strokeStart(centerY, single);
        final int sye = sy + single;
        final int sx = strokeStart(centerX, single);
        final int sxe = sx + single;
        switch (codePoint) {
            case 0x2550: // ═
                addRect(out, left, y1, right, y1e);
                addRect(out, left, y2, right, y2e);
                break;
            case 0x2551: // ║
                addRect(out, x1, top, x1e, bottom);
                addRect(out, x2, top, x2e, bottom);
                break;
            case 0x2552: // ╒ the single stem closes the open left end of the double band
                addRect(out, sx, y1, sxe, bottom);
                addRect(out, sx, y1, right, y1e);
                addRect(out, sx, y2, right, y2e);
                break;
            case 0x2553: // ╓
                addRect(out, x1, sy, x1e, bottom);
                addRect(out, x2, sy, x2e, bottom);
                addRect(out, x1, sy, right, sye);
                break;
            case 0x2554: // ╔
                addRect(out, x1, y1, x1e, bottom);
                addRect(out, x2, y2, x2e, bottom);
                addRect(out, x1, y1, right, y1e);
                addRect(out, x2, y2, right, y2e);
                break;
            case 0x2555: // ╕
                addRect(out, sx, y1, sxe, bottom);
                addRect(out, left, y1, sxe, y1e);
                addRect(out, left, y2, sxe, y2e);
                break;
            case 0x2556: // ╖
                addRect(out, x1, sy, x1e, bottom);
                addRect(out, x2, sy, x2e, bottom);
                addRect(out, left, sy, x2e, sye);
                break;
            case 0x2557: // ╗
                addRect(out, x2, y1, x2e, bottom);
                addRect(out, x1, y2, x1e, bottom);
                addRect(out, left, y1, x2e, y1e);
                addRect(out, left, y2, x1e, y2e);
                break;
            case 0x2558: // ╘
                addRect(out, sx, top, sxe, y2e);
                addRect(out, sx, y1, right, y1e);
                addRect(out, sx, y2, right, y2e);
                break;
            case 0x2559: // ╙
                addRect(out, x1, top, x1e, sye);
                addRect(out, x2, top, x2e, sye);
                addRect(out, x1, sy, right, sye);
                break;
            case 0x255A: // ╚
                addRect(out, x1, top, x1e, y2e);
                addRect(out, x2, top, x2e, y1e);
                addRect(out, x1, y2, right, y2e);
                addRect(out, x2, y1, right, y1e);
                break;
            case 0x255B: // ╛
                addRect(out, sx, top, sxe, y2e);
                addRect(out, left, y1, sxe, y1e);
                addRect(out, left, y2, sxe, y2e);
                break;
            case 0x255C: // ╜
                addRect(out, x1, top, x1e, sye);
                addRect(out, x2, top, x2e, sye);
                addRect(out, left, sy, x2e, sye);
                break;
            case 0x255D: // ╝
                addRect(out, x2, top, x2e, y2e);
                addRect(out, x1, top, x1e, y1e);
                addRect(out, left, y2, x2e, y2e);
                addRect(out, left, y1, x1e, y1e);
                break;
            case 0x255E: // ╞
                addRect(out, sx, top, sxe, bottom);
                addRect(out, sx, y1, right, y1e);
                addRect(out, sx, y2, right, y2e);
                break;
            case 0x255F: // ╟
                addRect(out, x1, top, x1e, bottom);
                addRect(out, x2, top, x2e, bottom);
                addRect(out, x2, sy, right, sye);
                break;
            case 0x2560: // ╠ the near rail breaks where the branch leaves it
                addRect(out, x1, top, x1e, bottom);
                addRect(out, x2, top, x2e, y1e);
                addRect(out, x2, y2, x2e, bottom);
                addRect(out, x2, y1, right, y1e);
                addRect(out, x2, y2, right, y2e);
                break;
            case 0x2561: // ╡
                addRect(out, sx, top, sxe, bottom);
                addRect(out, left, y1, sxe, y1e);
                addRect(out, left, y2, sxe, y2e);
                break;
            case 0x2562: // ╢
                addRect(out, x1, top, x1e, bottom);
                addRect(out, x2, top, x2e, bottom);
                addRect(out, left, sy, x1e, sye);
                break;
            case 0x2563: // ╣
                addRect(out, x2, top, x2e, bottom);
                addRect(out, x1, top, x1e, y1e);
                addRect(out, x1, y2, x1e, bottom);
                addRect(out, left, y1, x1e, y1e);
                addRect(out, left, y2, x1e, y2e);
                break;
            case 0x2564: // ╤
                addRect(out, left, y1, right, y1e);
                addRect(out, left, y2, right, y2e);
                addRect(out, sx, y2, sxe, bottom);
                break;
            case 0x2565: // ╥
                addRect(out, left, sy, right, sye);
                addRect(out, x1, sy, x1e, bottom);
                addRect(out, x2, sy, x2e, bottom);
                break;
            case 0x2566: // ╦
                addRect(out, left, y1, right, y1e);
                addRect(out, left, y2, x1e, y2e);
                addRect(out, x2, y2, right, y2e);
                addRect(out, x1, y2, x1e, bottom);
                addRect(out, x2, y2, x2e, bottom);
                break;
            case 0x2567: // ╧
                addRect(out, left, y1, right, y1e);
                addRect(out, left, y2, right, y2e);
                addRect(out, sx, top, sxe, y1e);
                break;
            case 0x2568: // ╨
                addRect(out, left, sy, right, sye);
                addRect(out, x1, top, x1e, sye);
                addRect(out, x2, top, x2e, sye);
                break;
            case 0x2569: // ╩
                addRect(out, left, y2, right, y2e);
                addRect(out, left, y1, x1e, y1e);
                addRect(out, x2, y1, right, y1e);
                addRect(out, x1, top, x1e, y1e);
                addRect(out, x2, top, x2e, y1e);
                break;
            case 0x256A: // ╪
                addRect(out, sx, top, sxe, bottom);
                addRect(out, left, y1, right, y1e);
                addRect(out, left, y2, right, y2e);
                break;
            case 0x256B: // ╫
                addRect(out, x1, top, x1e, bottom);
                addRect(out, x2, top, x2e, bottom);
                addRect(out, left, sy, right, sye);
                break;
            default: // 256C ╬ every rail breaks, leaving the four corners
                addRect(out, x1, top, x1e, y1e);
                addRect(out, x1, y2, x1e, bottom);
                addRect(out, x2, top, x2e, y1e);
                addRect(out, x2, y2, x2e, bottom);
                addRect(out, left, y1, x1e, y1e);
                addRect(out, x2, y1, right, y1e);
                addRect(out, left, y2, x1e, y2e);
                addRect(out, x2, y2, right, y2e);
                break;
        }
        return true;
    }

    /** Rounded corners U+256D-U+2570, centred on the same band as the straight light line. */
    private static boolean fillArc(int codePoint, int left, int top, int right, int bottom,
                                   float[] scales, Segments out) {
        final int stroke = thickness(right - left, bottom - top, scales, LIGHT);
        out.strokeThickness = stroke;
        final float centerX = strokeStart((left + right) / 2f, stroke) + stroke / 2f;
        final float centerY = strokeStart((top + bottom) / 2f, stroke) + stroke / 2f;
        switch (codePoint) {
            case 0x256D: // ╭
                addArc(out, right, centerY, centerX, centerY, centerX, bottom);
                break;
            case 0x256E: // ╮
                addArc(out, left, centerY, centerX, centerY, centerX, bottom);
                break;
            case 0x256F: // ╯
                addArc(out, left, centerY, centerX, centerY, centerX, top);
                break;
            default: // 2570 ╰
                addArc(out, right, centerY, centerX, centerY, centerX, top);
                break;
        }
        return true;
    }

    /** Diagonals U+2571-U+2573, drawn corner to corner so a run of them makes an unbroken line. */
    private static boolean fillDiagonal(int codePoint, int left, int top, int right, int bottom,
                                        float[] scales, Segments out) {
        out.strokeThickness = thickness(right - left, bottom - top, scales, LIGHT);
        if (codePoint != 0x2572) addDiagonal(out, left, bottom, right, top);
        if (codePoint != 0x2571) addDiagonal(out, left, top, right, bottom);
        return true;
    }

    // ---------------------------------------------------------------- blocks and shades

    /**
     * Blocks, quadrants and shades U+2580-U+259F.
     *
     * <p>Fractions are snapped with {@link #fraction}, so the boundary between the nth and (n+1)th
     * eighth is one integer both cells agree on: stacked partial blocks in adjacent rows tile with
     * neither a gap nor an overlapping double-drawn pixel.
     */
    private static boolean fillBlock(int codePoint, int left, int top, int right, int bottom,
                                     Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        final int midX = fraction(left, width, 4, 8);
        final int midY = fraction(top, height, 4, 8);
        if (codePoint >= 0x2581 && codePoint <= 0x2587) {
            addRect(out, left, fraction(top, height, 8 - (codePoint - 0x2580), 8), right, bottom);
            return true;
        }
        if (codePoint >= 0x2589 && codePoint <= 0x258F) {
            addRect(out, left, top, fraction(left, width, 8 - (codePoint - 0x2588), 8), bottom);
            return true;
        }
        switch (codePoint) {
            case 0x2580: addRect(out, left, top, right, midY); break;                  // ▀
            case 0x2588: addRect(out, left, top, right, bottom); break;                // █
            case 0x2590: addRect(out, midX, top, right, bottom); break;                // ▐
            case 0x2591: addShade(out, left, top, right, bottom, 25); break;           // ░
            case 0x2592: addShade(out, left, top, right, bottom, 50); break;           // ▒
            case 0x2593: addShade(out, left, top, right, bottom, 75); break;           // ▓
            case 0x2594: addRect(out, left, top, right, fraction(top, height, 1, 8)); break;   // ▔
            case 0x2595: addRect(out, fraction(left, width, 7, 8), top, right, bottom); break; // ▕
            case 0x2596: addRect(out, left, midY, midX, bottom); break;                // ▖
            case 0x2597: addRect(out, midX, midY, right, bottom); break;               // ▗
            case 0x2598: addRect(out, left, top, midX, midY); break;                   // ▘
            case 0x2599:                                                               // ▙
                addRect(out, left, top, midX, midY);
                addRect(out, left, midY, right, bottom);
                break;
            case 0x259A:                                                               // ▚
                addRect(out, left, top, midX, midY);
                addRect(out, midX, midY, right, bottom);
                break;
            case 0x259B:                                                               // ▛
                addRect(out, left, top, right, midY);
                addRect(out, left, midY, midX, bottom);
                break;
            case 0x259C:                                                               // ▜
                addRect(out, left, top, right, midY);
                addRect(out, midX, midY, right, bottom);
                break;
            case 0x259D: addRect(out, midX, top, right, midY); break;                  // ▝
            case 0x259E:                                                               // ▞
                addRect(out, midX, top, right, midY);
                addRect(out, left, midY, midX, bottom);
                break;
            default:                                                                   // 259F ▟
                addRect(out, midX, top, right, midY);
                addRect(out, left, midY, right, bottom);
                break;
        }
        return true;
    }

    /** Corner triangles U+25E2-U+25E5, filled to the cell corners. */
    private static boolean fillCornerTriangle(int codePoint, int left, int top, int right,
                                              int bottom, Segments out) {
        switch (codePoint) {
            case 0x25E2: addTriangle(out, right, top, right, bottom, left, bottom); break;  // ◢
            case 0x25E3: addTriangle(out, left, top, left, bottom, right, bottom); break;   // ◣
            case 0x25E4: addTriangle(out, left, top, right, top, left, bottom); break;      // ◤
            default: addTriangle(out, left, top, right, top, right, bottom); break;         // ◥
        }
        return true;
    }

    // ---------------------------------------------------------------- braille

    /**
     * Braille patterns U+2800-U+28FF on the standard 2x4 dot grid.
     *
     * <p>The low eight bits of the offset from U+2800 are the dots in Unicode order: bits 0-2 are
     * the left column's first three rows, bits 3-5 the right column's, and bits 6 and 7 the fourth
     * row's left and right dot. U+2800 itself is a legitimately empty cell.
     */
    private static boolean fillBraille(int codePoint, int left, int top, int right, int bottom,
                                       Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        final int pattern = codePoint - 0x2800;
        out.dotRadius = Math.max(1f, Math.min(width / 4f, height / 8f) * 0.75f);
        for (int bit = 0; bit < 8; bit++) {
            if ((pattern & (1 << bit)) == 0) continue;
            final int column;
            final int row;
            if (bit < 6) {
                column = bit / 3;
                row = bit % 3;
            } else {
                column = bit - 6;
                row = 3;
            }
            addDot(out, fraction(left, width, 2 * column + 1, 4),
                fraction(top, height, 2 * row + 1, 8));
        }
        return true;
    }

    // ---------------------------------------------------------------- legacy computing

    /**
     * Sextants U+1FB00-U+1FB3B on a 2x3 grid.
     *
     * <p>The block encodes the 60 patterns that are not already a character: the empty cell, the
     * left and right half blocks and the full block are all skipped, so the pattern is recovered by
     * stepping over the two skipped bit patterns in the middle of the run.
     */
    private static boolean fillSextant(int codePoint, int left, int top, int right, int bottom,
                                       Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        int pattern = codePoint - 0x1FB00 + 1;
        if (pattern >= 0b010101) pattern++;
        if (pattern >= 0b101010) pattern++;
        final int midX = fraction(left, width, 4, 8);
        for (int bit = 0; bit < 6; bit++) {
            if ((pattern & (1 << bit)) == 0) continue;
            final int column = bit % 2;
            final int row = bit / 2;
            addRect(out, column == 0 ? left : midX, fraction(top, height, row, 3),
                column == 0 ? midX : right, fraction(top, height, row + 1, 3));
        }
        return true;
    }

    /**
     * The eighth-block family U+1FB70-U+1FB8F: single eighth bars, the eighth corners, the
     * upper and right partial blocks that U+2580-U+259F leaves out, and the half medium shades.
     * Everything else in the legacy-computing block — wedges, inverse shades, pattern fills,
     * arrows and segmented digits — is left to the font.
     */
    private static boolean fillLegacyBlock(int codePoint, int left, int top, int right, int bottom,
                                           Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        if (codePoint >= 0x1FB70 && codePoint <= 0x1FB75) {
            final int column = codePoint - 0x1FB70 + 2;
            addRect(out, fraction(left, width, column - 1, 8), top,
                fraction(left, width, column, 8), bottom);
            return true;
        }
        if (codePoint >= 0x1FB76 && codePoint <= 0x1FB7B) {
            final int row = codePoint - 0x1FB76 + 2;
            addRect(out, left, fraction(top, height, row - 1, 8), right,
                fraction(top, height, row, 8));
            return true;
        }
        final int firstX = fraction(left, width, 1, 8);
        final int lastX = fraction(left, width, 7, 8);
        final int firstY = fraction(top, height, 1, 8);
        final int lastY = fraction(top, height, 7, 8);
        switch (codePoint) {
            case 0x1FB7C:
                addRect(out, left, top, firstX, bottom);
                addRect(out, left, lastY, right, bottom);
                break;
            case 0x1FB7D:
                addRect(out, left, top, firstX, bottom);
                addRect(out, left, top, right, firstY);
                break;
            case 0x1FB7E:
                addRect(out, lastX, top, right, bottom);
                addRect(out, left, top, right, firstY);
                break;
            case 0x1FB7F:
                addRect(out, lastX, top, right, bottom);
                addRect(out, left, lastY, right, bottom);
                break;
            case 0x1FB80:
                addRect(out, left, top, right, firstY);
                addRect(out, left, lastY, right, bottom);
                break;
            case 0x1FB81: // rows one, three, five and eight
                addRect(out, left, top, right, firstY);
                addRect(out, left, fraction(top, height, 2, 8), right, fraction(top, height, 3, 8));
                addRect(out, left, fraction(top, height, 4, 8), right, fraction(top, height, 5, 8));
                addRect(out, left, lastY, right, bottom);
                break;
            case 0x1FB82: addRect(out, left, top, right, fraction(top, height, 2, 8)); break;
            case 0x1FB83: addRect(out, left, top, right, fraction(top, height, 3, 8)); break;
            case 0x1FB84: addRect(out, left, top, right, fraction(top, height, 5, 8)); break;
            case 0x1FB85: addRect(out, left, top, right, fraction(top, height, 6, 8)); break;
            case 0x1FB86: addRect(out, left, top, right, lastY); break;
            case 0x1FB87: addRect(out, fraction(left, width, 6, 8), top, right, bottom); break;
            case 0x1FB88: addRect(out, fraction(left, width, 5, 8), top, right, bottom); break;
            case 0x1FB89: addRect(out, fraction(left, width, 3, 8), top, right, bottom); break;
            case 0x1FB8A: addRect(out, fraction(left, width, 2, 8), top, right, bottom); break;
            case 0x1FB8B: addRect(out, firstX, top, right, bottom); break;
            case 0x1FB8C: addShade(out, left, top, fraction(left, width, 4, 8), bottom, 50); break;
            case 0x1FB8D: addShade(out, fraction(left, width, 4, 8), top, right, bottom, 50); break;
            case 0x1FB8E: addShade(out, left, top, right, fraction(top, height, 4, 8), 50); break;
            default: addShade(out, left, fraction(top, height, 4, 8), right, bottom, 50); break;
        }
        return true;
    }

    // ---------------------------------------------------------------- powerline

    /**
     * Powerline separators U+E0B0-U+E0B7 and U+E0BA-U+E0BD, spanning the whole cell so that two
     * consecutive separators butt against each other without a sliver of background between them.
     */
    private static boolean fillPowerline(int codePoint, int left, int top, int right, int bottom,
                                         float[] scales, Segments out) {
        final int width = right - left;
        final int height = bottom - top;
        final float centerY = (top + bottom) / 2f;
        out.strokeThickness = thickness(width, height, scales, THIN);
        switch (codePoint) {
            case 0xE0B0: addTriangle(out, left, top, right, centerY, left, bottom); break;
            case 0xE0B1:
                addDiagonal(out, left, top, right, centerY);
                addDiagonal(out, right, centerY, left, bottom);
                break;
            case 0xE0B2: addTriangle(out, right, top, left, centerY, right, bottom); break;
            case 0xE0B3:
                addDiagonal(out, right, top, left, centerY);
                addDiagonal(out, left, centerY, right, bottom);
                break;
            case 0xE0B4: // filled semicircle bulging right, its flat side on the left cell edge
                out.capFilled = true;
                addCap(out, left - width, top, left + width, bottom, -90f, 180f);
                break;
            case 0xE0B5:
                addCap(out, left - width, top, left + width, bottom, -90f, 180f);
                break;
            case 0xE0B6:
                out.capFilled = true;
                addCap(out, right - width, top, right + width, bottom, 90f, 180f);
                break;
            case 0xE0B7:
                addCap(out, right - width, top, right + width, bottom, 90f, 180f);
                break;
            case 0xE0BA: addTriangle(out, right, top, right, bottom, left, bottom); break;
            case 0xE0BC: addTriangle(out, left, top, right, top, left, bottom); break;
            default: // E0BB and E0BD, the hairlines of the two triangles above
                addDiagonal(out, right, top, left, bottom);
                break;
        }
        return true;
    }
}
