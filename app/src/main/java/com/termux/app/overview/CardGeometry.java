package com.termux.app.overview;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a card's {@link CardFacts} land inside the card, in the card's own pixels. Pure geometry:
 * the view asks once per size, keeps the answer in plain lists and draws from them, so nothing is
 * measured or allocated while it draws and nothing is redrawn at rest.
 */
public final class CardGeometry {

    /** A terminal pane shows text only when at least this many rows fit; fewer is a block. */
    public static final int MIN_TEXT_LINES = 2;

    private CardGeometry() { }

    /** One rectangle of a card. */
    public static class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    /** A pane's rectangle and how many of its rows it shows. */
    public static final class PaneBox extends Box {
        @NonNull public final CardFacts.Pane pane;
        public final boolean floating;
        /** The rows shown (the last of the pane's), 0 for a block. */
        public final int textLines;

        PaneBox(float left, float top, float right, float bottom, @NonNull CardFacts.Pane pane,
                boolean floating, int textLines) {
            super(left, top, right, bottom);
            this.pane = pane;
            this.floating = floating;
            this.textLines = textLines;
        }
    }

    /** A Home cell's rectangle. */
    public static final class CellBox extends Box {
        @NonNull public final CardFacts.CellKind kind;

        CellBox(float left, float top, float right, float bottom, @NonNull CardFacts.CellKind kind) {
            super(left, top, right, bottom);
            this.kind = kind;
        }
    }

    /** A conversation bubble's rectangle. */
    public static final class BubbleBox extends Box {
        public final boolean fromUser;

        BubbleBox(float left, float top, float right, float bottom, boolean fromUser) {
            super(left, top, right, bottom);
            this.fromUser = fromUser;
        }
    }

    /**
     * How many rows a pane of {@code width} x {@code height} shows of the {@code available} it
     * holds: as many as fit between its padding, the last ones; 0 (a block) for a Linux pane, a
     * pane too narrow for {@code minTextWidth}, or one where fewer than {@link #MIN_TEXT_LINES}
     * fit.
     */
    public static int textLines(@NonNull CardFacts.Pane pane, float width, float height,
                                float lineHeight, float pad, float minTextWidth) {
        if (pane.linux || pane.lines.length == 0 || lineHeight <= 0f) return 0;
        if (width < minTextWidth) return 0;
        int fit = (int) Math.floor((height - 2f * pad) / lineHeight);
        if (fit < MIN_TEXT_LINES) return 0;
        return Math.min(fit, pane.lines.length);
    }

    /**
     * A Workspace window's panes inside a {@code width} x {@code height} area: the tiles of its
     * tree by weight with {@code gap} between them, then its floats over them.
     */
    @NonNull
    public static List<PaneBox> workspace(@NonNull CardFacts.Workspace facts, float width,
                                          float height, float gap, float lineHeight,
                                          float textPad, float minTextWidth) {
        List<PaneBox> out = new ArrayList<>();
        if (width <= 0f || height <= 0f) return out;
        if (facts.root != null) {
            tile(facts.root, 0f, 0f, width, height, gap, lineHeight, textPad, minTextWidth, out);
        }
        for (CardFacts.Floating f : facts.floats) {
            float l = clamp01(f.left) * width;
            float t = clamp01(f.top) * height;
            float r = Math.max(l, clamp01(f.right) * width);
            float b = Math.max(t, clamp01(f.bottom) * height);
            if (r - l < 1f || b - t < 1f) continue;
            out.add(new PaneBox(l, t, r, b, f.pane, true,
                textLines(f.pane, r - l, b - t, lineHeight, textPad, minTextWidth)));
        }
        return out;
    }

    private static void tile(@NonNull CardFacts.Node node, float l, float t, float r, float b,
                             float gap, float lineHeight, float textPad, float minTextWidth,
                             @NonNull List<PaneBox> out) {
        if (node instanceof CardFacts.Pane) {
            CardFacts.Pane pane = (CardFacts.Pane) node;
            out.add(new PaneBox(l, t, r, b, pane, false,
                textLines(pane, r - l, b - t, lineHeight, textPad, minTextWidth)));
            return;
        }
        CardFacts.Split split = (CardFacts.Split) node;
        float wa = split.weightA > 0f ? split.weightA : 0f;
        float wb = split.weightB > 0f ? split.weightB : 0f;
        float share = wa + wb <= 0f ? 0.5f : wa / (wa + wb);
        if (split.sideBySide) {
            float room = Math.max(0f, r - l - gap);
            float cut = l + room * share;
            tile(split.a, l, t, cut, b, gap, lineHeight, textPad, minTextWidth, out);
            tile(split.b, cut + gap, t, r, b, gap, lineHeight, textPad, minTextWidth, out);
        } else {
            float room = Math.max(0f, b - t - gap);
            float cut = t + room * share;
            tile(split.a, l, t, r, cut, gap, lineHeight, textPad, minTextWidth, out);
            tile(split.b, l, cut + gap, r, b, gap, lineHeight, textPad, minTextWidth, out);
        }
    }

    /**
     * A Home page's cells inside a {@code width} x {@code height} area: each cell's span of the
     * grid, inset by {@code gap}, and an icon or folder (one cell) inset a further
     * {@code iconInset} so it reads as an icon in its cell rather than a tile.
     */
    @NonNull
    public static List<CellBox> home(@NonNull CardFacts.Home facts, float width, float height,
                                     float gap, float iconInset) {
        List<CellBox> out = new ArrayList<>(facts.cells.size());
        if (width <= 0f || height <= 0f) return out;
        float cw = width / facts.columns;
        float ch = height / facts.rows;
        for (CardFacts.Cell c : facts.cells) {
            if (c.right <= c.left || c.bottom <= c.top) continue;
            if (c.left >= facts.columns || c.top >= facts.rows) continue;
            int right = Math.min(c.right, facts.columns);
            int bottom = Math.min(c.bottom, facts.rows);
            float inset = c.kind == CardFacts.CellKind.WIDGET ? gap : gap + iconInset;
            float l = c.left * cw + inset;
            float t = c.top * ch + inset;
            float r = right * cw - inset;
            float b = bottom * ch - inset;
            if (r - l < 1f || b - t < 1f) continue;
            out.add(new CellBox(l, t, r, b, c.kind));
        }
        return out;
    }

    /**
     * A conversation's bubbles, newest at the bottom: each {@code bubbleHeight} tall with
     * {@code gap} between, a user's flush right and an answer's flush left, each as wide as its
     * fill of {@code maxFraction} of the width. Bubbles that do not fit above are left out.
     */
    @NonNull
    public static List<BubbleBox> assistant(@NonNull CardFacts.Assistant facts, float width,
                                            float height, float bubbleHeight, float gap,
                                            float maxFraction, float minFraction) {
        List<BubbleBox> out = new ArrayList<>(facts.fill.length);
        if (width <= 0f || height <= 0f || bubbleHeight <= 0f) return out;
        int fit = (int) Math.floor((height + gap) / (bubbleHeight + gap));
        int count = Math.min(facts.fill.length, Math.max(0, fit));
        int first = facts.fill.length - count;
        // Oldest first in the list, so it draws top to bottom; the stack sits at the bottom.
        float y = height - count * bubbleHeight - Math.max(0, count - 1) * gap;
        for (int i = first; i < facts.fill.length; i++) {
            float f = Math.max(minFraction, Math.min(maxFraction, facts.fill[i] * maxFraction));
            float w = width * f;
            boolean user = facts.fromUser[i];
            float l = user ? width - w : 0f;
            out.add(new BubbleBox(l, y, l + w, y + bubbleHeight, user));
            y += bubbleHeight + gap;
        }
        return out;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
