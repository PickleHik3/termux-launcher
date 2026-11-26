package com.termux.app.overview;

import androidx.annotation.NonNull;

/**
 * How large Overview's cards are so the whole screen fits without scrolling (spec
 * overview-2026-11-24, "The screen", 2026-11-27): the wall as a grid that wraps, then each row
 * block (a Workspace's sessions with its + tile, then the Saved row) as a smaller grid under it.
 * Pure: the board fills a {@link Spec} when it is measured and applies the {@link Fit}.
 *
 * <p>The rule: a card has the screen's shape, its longer side at most {@link Spec#maxLong}, and a
 * wall grid takes the fewest columns that let everything fit; a row card is
 * {@link Spec#rowScale} of a wall card. Row blocks share a line while they fit side by side and
 * wrap to the next line when they do not. A card's picture is never shorter than its caption (or
 * {@link Spec#minHeight}), whatever the screen's shape. When even {@link Spec#minWidth} wide
 * cards do not fit, the cards keep that width and get shorter down to that floor; only below it
 * does the board report that it does not fit.
 */
public final class OverviewGrid {

    private OverviewGrid() { }

    /** What the board knows, in pixels; reused between measures. */
    public static final class Spec {
        /** The room across, the side gutters already taken off. */
        public float width;
        /** The room down for the wall and the row blocks. */
        public float height;
        /** A card's height over its width: the screen's. */
        public float aspect;
        /** Wall cards shown. */
        public int pages;
        /** Each row block's cells (a session row counts its + tile), in order. */
        @NonNull public int[] rows = new int[0];
        /** How many entries of {@link #rows} are in use. */
        public int rowCount;
        /** Between two cells, across and down. */
        public float gap;
        /** A caption under a card's picture, its gap included. */
        public float caption;
        /** A row block's title line, with its gap to the cells. */
        public float head;
        /** Between the wall and the first row block, and between row blocks. */
        public float blockGap;
        /** A card's longer side at most. */
        public float maxLong;
        /** A wall card's width at least, while that fits across. */
        public float minWidth;
        /** A wall card's picture height at least. */
        public float minHeight;
        /** A row card against a wall card. */
        public float rowScale;
        /** A row block's narrowest, so its title stays readable beside another block. */
        public float minBlockWidth;
    }

    /** The answer. */
    public static final class Fit {
        public int columns;
        public float cardWidth;
        public float cardHeight;
        public int rowColumns;
        public float rowCardWidth;
        public float rowCardHeight;
        /** The height all of it takes. */
        public float height;
        /** False only when the floor itself does not fit. */
        public boolean fits;
        /** Per row block: where its title line starts, from the wall's left and the wall's top. */
        @NonNull public float[] blockLeft = new float[0];
        @NonNull public float[] blockTop = new float[0];
        @NonNull public float[] blockWidth = new float[0];
        /** Picture heights stacked (row cards at their scale) and the height that is not picture. */
        float pictureRows;
        float fixed;
    }

    /** The shortest a wall card's picture gets: its caption or the floor, for a row card too. */
    static float floorHeight(@NonNull Spec s) {
        float scale = s.rowScale > 0f ? s.rowScale : 1f;
        return Math.max(s.minHeight, s.caption / scale);
    }

    /** Fills {@code out} for {@code s} and returns it. */
    @NonNull
    public static Fit fit(@NonNull Spec s, @NonNull Fit out) {
        float width = Math.max(1f, s.width);
        float aspect = s.aspect > 0f ? s.aspect : 1f;
        float floorH = floorHeight(s);
        float maxWidth = s.maxLong / Math.max(1f, aspect);
        int first = Math.max(1, (int) Math.ceil((width + s.gap) / (maxWidth + s.gap) - 1e-4f));
        int last = Math.max(first, (int) Math.floor((width + s.gap) / (s.minWidth + s.gap)));
        for (int c = first; c <= last; c++) {
            float w = Math.min(maxWidth, across(width, c, s.gap));
            float natural = w * aspect;
            if (natural < floorH) {
                // The screen's shape would make cards shorter than the floor: not a candidate.
                continue;
            }
            set(s, out, c, w, natural);
            if (out.height <= s.height) {
                out.fits = true;
                return out;
            }
        }
        // The floor: the most columns a legible card allows, its picture shortened to fit, but
        // never below the caption's height whatever the screen's shape.
        float w = Math.min(maxWidth, across(width, last, s.gap));
        set(s, out, last, w, 0f);
        float per = out.pictureRows;
        float h = per > 0f ? (s.height - out.fixed) / per : w * aspect;
        h = Math.min(Math.max(w * aspect, floorH), h);
        boolean fits = h >= floorH - 1e-3f;
        set(s, out, last, w, Math.max(h, floorH));
        out.fits = fits && out.height <= s.height + 1e-3f;
        return out;
    }

    /** A cell's width when {@code columns} share {@code width}. */
    static float across(float width, int columns, float gap) {
        return Math.max(0f, (width - (columns - 1) * gap) / columns);
    }

    /** Grid rows {@code count} cells take in {@code columns}. */
    static int gridRows(int count, int columns) {
        return count <= 0 ? 0 : (count + columns - 1) / columns;
    }

    private static void set(@NonNull Spec s, @NonNull Fit out, int columns, float w, float h) {
        out.columns = columns;
        out.cardWidth = w;
        out.cardHeight = h;
        out.rowCardWidth = w * s.rowScale;
        out.rowCardHeight = h * s.rowScale;
        out.rowColumns = Math.max(1,
            (int) Math.floor((s.width + s.gap) / (out.rowCardWidth + s.gap) + 1e-4f));
        place(s, out);
    }

    /**
     * Stands the wall, then the row blocks in lines (a block joins the line while it fits beside
     * the last), and records the height, the stacked picture rows and each block's place.
     */
    private static void place(@NonNull Spec s, @NonNull Fit f) {
        if (f.blockLeft.length < s.rowCount) {
            f.blockLeft = new float[s.rowCount];
            f.blockTop = new float[s.rowCount];
            f.blockWidth = new float[s.rowCount];
        }
        float width = Math.max(1f, s.width);
        float y = 0f;
        float pic = 0f;
        float fixed = 0f;
        int wallRows = gridRows(s.pages, f.columns);
        if (wallRows > 0) {
            y = wallRows * (f.cardHeight + s.caption) + (wallRows - 1) * s.gap;
            pic = wallRows;
            fixed = wallRows * s.caption + (wallRows - 1) * s.gap;
        }
        boolean any = wallRows > 0;
        float lineX = 0f;
        float lineTop = y;
        int lineRows = 0;
        for (int i = 0; i < s.rowCount; i++) {
            int n = s.rows[i];
            f.blockLeft[i] = 0f;
            f.blockTop[i] = y;
            f.blockWidth[i] = 0f;
            if (n <= 0) continue;
            int cols = Math.min(n, f.rowColumns);
            float w = Math.min(width, Math.max(cols * f.rowCardWidth + (cols - 1) * s.gap,
                s.minBlockWidth));
            int rows = gridRows(n, f.rowColumns);
            if (lineRows > 0 && lineX + s.blockGap + w <= width + 1e-3f) {
                lineX += s.blockGap;
            } else {
                if (lineRows > 0) {
                    y = lineTop + lineHeight(s, f, lineRows);
                    pic += lineRows * s.rowScale;
                    fixed += s.head + lineRows * s.caption + (lineRows - 1) * s.gap;
                }
                if (any) {
                    y += s.blockGap;
                    fixed += s.blockGap;
                }
                lineTop = y;
                lineX = 0f;
                lineRows = 0;
            }
            f.blockLeft[i] = lineX;
            f.blockTop[i] = lineTop;
            f.blockWidth[i] = w;
            lineX += w;
            lineRows = Math.max(lineRows, rows);
            any = true;
        }
        if (lineRows > 0) {
            y = lineTop + lineHeight(s, f, lineRows);
            pic += lineRows * s.rowScale;
            fixed += s.head + lineRows * s.caption + (lineRows - 1) * s.gap;
        }
        f.height = y;
        f.pictureRows = pic;
        f.fixed = fixed;
    }

    private static float lineHeight(@NonNull Spec s, @NonNull Fit f, int rows) {
        return s.head + rows * (f.rowCardHeight + s.caption) + (rows - 1) * s.gap;
    }
}
