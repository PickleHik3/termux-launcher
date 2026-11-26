package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What a card of Overview is drawn from: layout facts of its page (the pane tree and a few text
 * rows, the widget and icon cells, the shape of a conversation), never a view or a bitmap. Pure
 * data, filled once when Overview opens and dropped when it closes; the text it holds is bounded
 * by {@link #MAX_LINES}, {@link #MAX_CHARS} and the {@link TextBudget} the filler draws from.
 */
public abstract class CardFacts {

    /** Text rows kept per terminal pane: the ones a card can show before the pane is a block. */
    public static final int MAX_LINES = 16;
    /** Characters kept per row: a card's pane is about this many glyphs wide. */
    public static final int MAX_CHARS = 40;
    /** Bubbles an Assistant card draws, newest last. */
    public static final int MAX_BUBBLES = 6;

    private CardFacts() { }

    /** A node of a window's pane tree: a {@link Pane} or a {@link Split}. */
    public abstract static class Node { }

    /** One pane of a Workspace window. */
    public static final class Pane extends Node {
        /** A Linux app pane: drawn as a plain block, never with text. */
        public final boolean linux;
        /** The focused pane of its window. */
        public final boolean active;
        /** The last rows of the terminal's text, top to bottom, trimmed; empty for none. */
        @NonNull public final String[] lines;

        public Pane(boolean linux, boolean active, @Nullable String[] lines) {
            this.linux = linux;
            this.active = active;
            this.lines = lines == null || linux ? new String[0] : lines;
        }
    }

    /** Two children side by side or stacked, sharing the room by weight. */
    public static final class Split extends Node {
        /** Side by side (the first child on the left) when true, else stacked (first on top). */
        public final boolean sideBySide;
        public final float weightA;
        public final float weightB;
        @NonNull public final Node a;
        @NonNull public final Node b;

        public Split(boolean sideBySide, float weightA, float weightB, @NonNull Node a,
                     @NonNull Node b) {
            this.sideBySide = sideBySide;
            this.weightA = weightA;
            this.weightB = weightB;
            this.a = a;
            this.b = b;
        }
    }

    /** A pane floating over the tiles, by fractions (0..1) of the window. */
    public static final class Floating {
        @NonNull public final Pane pane;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Floating(@NonNull Pane pane, float left, float top, float right, float bottom) {
            this.pane = pane;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** A Workspace window: its tiled tree and its floats. A null root is a window with no panes. */
    public static final class Workspace extends CardFacts {
        @Nullable public final Node root;
        @NonNull public final List<Floating> floats;

        public Workspace(@Nullable Node root, @Nullable List<Floating> floats) {
            this.root = root;
            this.floats = floats == null ? Collections.emptyList() : floats;
        }
    }

    /** What a Home cell holds. */
    public enum CellKind { WIDGET, ICON, FOLDER }

    /** One item of a Home page in grid cells; the right and bottom edges are exclusive. */
    public static final class Cell {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        @NonNull public final CellKind kind;

        public Cell(int left, int top, int right, int bottom, @NonNull CellKind kind) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.kind = kind;
        }
    }

    /** A Home page: its grid and what stands in it. */
    public static final class Home extends CardFacts {
        public final int rows;
        public final int columns;
        @NonNull public final List<Cell> cells;

        public Home(int rows, int columns, @NonNull List<Cell> cells) {
            this.rows = Math.max(1, rows);
            this.columns = Math.max(1, columns);
            this.cells = cells;
        }
    }

    /**
     * An Assistant conversation as bubbles, oldest first: who said it and how much, as a fraction
     * (0..1) of a full line. No message text is held.
     */
    public static final class Assistant extends CardFacts {
        @NonNull public final boolean[] fromUser;
        @NonNull public final float[] fill;

        public Assistant(@NonNull boolean[] fromUser, @NonNull float[] fill) {
            int n = Math.min(fromUser.length, fill.length);
            this.fromUser = Arrays.copyOf(fromUser, n);
            this.fill = Arrays.copyOf(fill, n);
        }
    }
}
