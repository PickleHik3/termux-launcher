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
 * {@link Spec#rowScale} of a wall card. When even {@link Spec#minWidth} wide cards do not fit, the
 * cards keep that width and get shorter (fewer text rows inside) down to {@link Spec#minHeight};
 * only below that does the board report that it does not fit.
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
    }

    /** Fills {@code out} for {@code s} and returns it. */
    @NonNull
    public static Fit fit(@NonNull Spec s, @NonNull Fit out) {
        float width = Math.max(1f, s.width);
        float aspect = s.aspect > 0f ? s.aspect : 1f;
        float maxWidth = s.maxLong / Math.max(1f, aspect);
        int first = Math.max(1, (int) Math.ceil((width + s.gap) / (maxWidth + s.gap) - 1e-4f));
        int last = Math.max(first, (int) Math.floor((width + s.gap) / (s.minWidth + s.gap)));
        for (int c = first; c <= last; c++) {
            float w = Math.min(maxWidth, across(width, c, s.gap));
            set(s, out, c, w, w * aspect);
            if (out.height <= s.height) {
                out.fits = true;
                return out;
            }
        }
        // The floor: the most columns a legible card allows, its picture shortened to fit.
        float w = Math.min(maxWidth, across(width, last, s.gap));
        set(s, out, last, w, 0f);
        float fixed = out.height;
        float per = pictureRows(s, out);
        float h = per > 0f ? (s.height - fixed) / per : w * aspect;
        h = Math.min(w * aspect, h);
        out.fits = h >= s.minHeight;
        set(s, out, last, w, Math.max(h, Math.min(s.minHeight, w * aspect)));
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
        out.height = height(s, out);
    }

    /** How many picture heights the layout stacks, a row card's counting as its scale. */
    private static float pictureRows(@NonNull Spec s, @NonNull Fit f) {
        float rows = gridRows(s.pages, f.columns);
        for (int i = 0; i < s.rowCount; i++) rows += gridRows(s.rows[i], f.rowColumns) * s.rowScale;
        return rows;
    }

    /** The height of everything at {@code f}'s sizes. */
    static float height(@NonNull Spec s, @NonNull Fit f) {
        float total = 0f;
        int blocks = 0;
        int wallRows = gridRows(s.pages, f.columns);
        if (wallRows > 0) {
            total += wallRows * (f.cardHeight + s.caption) + (wallRows - 1) * s.gap;
            blocks++;
        }
        for (int i = 0; i < s.rowCount; i++) {
            int rows = gridRows(s.rows[i], f.rowColumns);
            if (rows == 0) continue;
            if (blocks > 0) total += s.blockGap;
            total += s.head + rows * (f.rowCardHeight + s.caption) + (rows - 1) * s.gap;
            blocks++;
        }
        return total;
    }
}
