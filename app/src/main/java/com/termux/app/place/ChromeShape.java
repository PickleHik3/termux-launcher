package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What {@link ChromeShapeModel} says about the chrome's shapes: every piece's rect, which card it
 * belongs to, its corners and what each of its edges is, plus the pane's opening, the panes in it
 * and the lines between them. Immutable.
 *
 * <p>Every number is in the space the caller's {@link ChromeShapeModel.Input} was given in, with
 * the frame's top-left at 0,0: pixels for the live chrome, canvas units for the layout canvas.
 * Nothing here knows which.
 *
 * <p>A <em>card</em> is one shape drawn and rimmed as a whole. Under Floating that is a run of
 * neighbouring pieces on one edge, a lone piece, the keyboard or a pane. Under Docked it is the
 * single frame all the pieces share, clipped once with the rounded insert cut out of it
 * ({@link Card#hole}); it exists even when every bar is put away.
 */
public final class ChromeShape {

    /** What lies across one stretch of a piece's edge. */
    public enum EdgeKind {
        /** Another piece of the same frame or card continues past it; no line, no rim. */
        JOIN,
        /** The edge of the screen. Docked only; plain, and the strip behind the system bars. */
        SCREEN,
        /** A bar's edge facing the gutter of frame glass around the insert. Docked only; plain. */
        GUTTER,
        /** A free card edge: Floating's cards. The insert's rim is the pane's, not a piece's. */
        RIM;

        /** Whether an edge of this kind draws rim light or refraction. */
        public boolean drawsRim() {
            return this == RIM;
        }
    }

    /** Which chrome piece a rect is. The split keyboard is two pieces. */
    public enum PieceId {
        STATUS, APPS, AZ, EXTRA_KEYS, KEYBOARD, KEYBOARD_LEFT, KEYBOARD_RIGHT;

        /** The dock-side element this piece is, or null for a keyboard piece. */
        @Nullable
        public Element element() {
            switch (this) {
                case STATUS: return Element.STATUS;
                case APPS: return Element.APPS;
                case AZ: return Element.AZ;
                case EXTRA_KEYS: return Element.EXTRA_KEYS;
                default: return null;
            }
        }

        @NonNull
        public static PieceId of(@NonNull Element element) {
            switch (element) {
                case STATUS: return STATUS;
                case APPS: return APPS;
                case AZ: return AZ;
                case EXTRA_KEYS:
                default: return EXTRA_KEYS;
            }
        }
    }

    /** A rect, left and top inclusive. Not Android's: this stays plain-JVM. */
    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return Math.max(0f, right - left);
        }

        public float height() {
            return Math.max(0f, bottom - top);
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Box)) return false;
            Box that = (Box) other;
            return left == that.left && top == that.top && right == that.right
                && bottom == that.bottom;
        }

        @Override public int hashCode() {
            int result = Float.hashCode(left);
            result = 31 * result + Float.hashCode(top);
            result = 31 * result + Float.hashCode(right);
            return 31 * result + Float.hashCode(bottom);
        }

        @NonNull @Override public String toString() {
            return "[" + left + "," + top + " - " + right + "," + bottom + "]";
        }
    }

    /** Four corner radii, clockwise from the top left. */
    public static final class Corners {
        public static final Corners SQUARE = new Corners(0f, 0f, 0f, 0f);

        public final float topLeft;
        public final float topRight;
        public final float bottomRight;
        public final float bottomLeft;

        public Corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
        }

        /** The radii as the eight-value array {@code Path.addRoundRect} and drawables take. */
        @NonNull
        public float[] toRadii() {
            return new float[] {topLeft, topLeft, topRight, topRight, bottomRight, bottomRight,
                bottomLeft, bottomLeft};
        }

        public boolean isSquare() {
            return topLeft == 0f && topRight == 0f && bottomRight == 0f && bottomLeft == 0f;
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Corners)) return false;
            Corners that = (Corners) other;
            return topLeft == that.topLeft && topRight == that.topRight
                && bottomRight == that.bottomRight && bottomLeft == that.bottomLeft;
        }

        @Override public int hashCode() {
            int result = Float.hashCode(topLeft);
            result = 31 * result + Float.hashCode(topRight);
            result = 31 * result + Float.hashCode(bottomRight);
            return 31 * result + Float.hashCode(bottomLeft);
        }

        @NonNull @Override public String toString() {
            return "(" + topLeft + "," + topRight + "," + bottomRight + "," + bottomLeft + ")";
        }
    }

    /**
     * One stretch of a piece's edge and what lies across it. An edge is one run when a single
     * thing is across all of it, and several where a top bar's inner edge runs over a side bar
     * and then the opening. {@code from} and {@code to} are x for a top or bottom edge and y for a
     * left or right one.
     */
    public static final class EdgeRun {
        @NonNull public final Edge edge;
        public final float from;
        public final float to;
        @NonNull public final EdgeKind kind;

        public EdgeRun(@NonNull Edge edge, float from, float to, @NonNull EdgeKind kind) {
            this.edge = edge;
            this.from = from;
            this.to = to;
            this.kind = kind;
        }

        @NonNull @Override public String toString() {
            return edge + "[" + from + ".." + to + "]=" + kind;
        }
    }

    /** One chrome piece: a bar, the status bar, the keyboard or one half of it. */
    public static final class Piece {
        @NonNull public final PieceId id;
        @NonNull public final Box box;
        /** The card it shares with the pieces holding the same id. */
        public final int groupId;
        /** Per-corner radii; square wherever the piece joins another or runs into a free edge. */
        @NonNull public final Corners corners;
        /** Whether it floats over the content instead of taking a band of the frame. */
        public final boolean overlay;
        @NonNull private final List<EdgeRun> mRuns;

        Piece(@NonNull PieceId id, @NonNull Box box, int groupId, @NonNull Corners corners,
              boolean overlay, @NonNull List<EdgeRun> runs) {
            this.id = id;
            this.box = box;
            this.groupId = groupId;
            this.corners = corners;
            this.overlay = overlay;
            mRuns = Collections.unmodifiableList(new ArrayList<>(runs));
        }

        /** Every run of every edge, in edge order top, bottom, left, right. */
        @NonNull
        public List<EdgeRun> runs() {
            return mRuns;
        }

        /** The runs of one edge, in order along it. */
        @NonNull
        public List<EdgeRun> runs(@NonNull Edge edge) {
            List<EdgeRun> on = new ArrayList<>(2);
            for (EdgeRun run : mRuns) if (run.edge == edge) on.add(run);
            return on;
        }

        /**
         * The one answer for an edge. Where it has several runs the one that matters most wins:
         * a free edge, then the screen or gutter, then a join.
         */
        @NonNull
        public EdgeKind kind(@NonNull Edge edge) {
            EdgeKind best = null;
            for (EdgeRun run : mRuns) {
                if (run.edge != edge) continue;
                if (best == null || rank(run.kind) > rank(best)) best = run.kind;
            }
            return best == null ? EdgeKind.JOIN : best;
        }

        /** Whether any of an edge draws rim light or refraction. */
        public boolean drawsRim(@NonNull Edge edge) {
            for (EdgeRun run : mRuns) if (run.edge == edge && run.kind.drawsRim()) return true;
            return false;
        }

        private static int rank(@NonNull EdgeKind kind) {
            switch (kind) {
                case RIM: return 3;
                case SCREEN:
                case GUTTER: return 1;
                default: return 0;
            }
        }

        @NonNull @Override public String toString() {
            return id + box.toString() + "#" + groupId + corners;
        }
    }

    /**
     * One shape drawn as a whole: under Docked the frame, clipped once with the opening cut out;
     * under Floating a rounded card. Pieces with its {@code id} as their group are its members.
     */
    public static final class Card {
        public final int id;
        /** The card's outline rect: the screen for the frame, the members' union for a card. */
        @NonNull public final Box box;
        @NonNull public final Corners corners;
        /**
         * The opening cut out of the frame, or null for a card that has none: the bounding box of
         * the rounded inserts, so the gutter is the frame glass between it and the bars.
         */
        @Nullable public final Box hole;
        /** The radius every insert is cut with; square for a card with no hole. */
        @NonNull public final Corners holeCorners;
        /** Each insert cut out: one per pane under Docked, so the gutter between panes is glass. */
        @NonNull public final List<Box> holes;
        /** The Docked frame rather than a Floating card. */
        public final boolean frame;
        @NonNull public final List<PieceId> members;

        Card(int id, @NonNull Box box, @NonNull Corners corners, @Nullable Box hole,
             boolean frame, @NonNull List<PieceId> members) {
            this(id, box, corners, hole, frame, members, Corners.SQUARE,
                hole == null ? Collections.<Box>emptyList() : Collections.singletonList(hole));
        }

        Card(int id, @NonNull Box box, @NonNull Corners corners, @Nullable Box hole,
             boolean frame, @NonNull List<PieceId> members, @NonNull Corners holeCorners,
             @NonNull List<Box> holes) {
            this.holeCorners = holeCorners;
            this.holes = Collections.unmodifiableList(new ArrayList<>(holes));
            this.id = id;
            this.box = box;
            this.corners = corners;
            this.hole = hole;
            this.frame = frame;
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
        }
    }

    /**
     * Where the pane lives: the space the pane shapes fill. Under Docked it is the rounded insert,
     * a gutter inside the bars, standing on the frame glass; under Floating the space the pane
     * cards fill, a Margin inside the cards. Either way it owns its rim, and every side is
     * {@link EdgeKind#RIM}: the pane's own edge, which is the border the border drag follows.
     */
    public static final class Opening {
        @NonNull public final Box box;
        @NonNull public final Corners corners;
        /** Whether the panes wear a rim of their own: true under both Styles. */
        public final boolean ownsRim;
        @NonNull private final EdgeKind[] mEdges;

        Opening(@NonNull Box box, @NonNull Corners corners, boolean ownsRim,
                @NonNull EdgeKind top, @NonNull EdgeKind bottom, @NonNull EdgeKind left,
                @NonNull EdgeKind right) {
            this.box = box;
            this.corners = corners;
            this.ownsRim = ownsRim;
            mEdges = new EdgeKind[] {top, bottom, left, right};
        }

        @NonNull
        public EdgeKind kind(@NonNull Edge edge) {
            return mEdges[edge.ordinal()];
        }
    }

    /** One pane inside the opening. */
    public static final class Pane {
        public final int index;
        @NonNull public final Box box;
        @NonNull public final Corners corners;
        /** The pane's own group, under either Style: each pane is its own shape. */
        public final int groupId;
        /** Whether the pane wears rim light itself: a Floating card and a Docked insert both do. */
        public final boolean drawsRim;

        Pane(int index, @NonNull Box box, @NonNull Corners corners, int groupId,
             boolean drawsRim) {
            this.index = index;
            this.box = box;
            this.corners = corners;
            this.groupId = groupId;
            this.drawsRim = drawsRim;
        }
    }

    /** A line dividing two panes: a segment and how thick it draws. Retired; the model gives none. */
    public static final class Divider {
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;
        public final float thickness;

        Divider(float x1, float y1, float x2, float y2, float thickness) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.thickness = thickness;
        }
    }

    @NonNull private final List<Piece> mPieces;
    @NonNull private final List<Card> mCards;
    @NonNull private final Opening mOpening;
    @NonNull private final List<Pane> mPanes;
    @NonNull private final List<Divider> mDividers;

    ChromeShape(@NonNull List<Piece> pieces, @NonNull List<Card> cards, @NonNull Opening opening,
                @NonNull List<Pane> panes, @NonNull List<Divider> dividers) {
        mPieces = Collections.unmodifiableList(new ArrayList<>(pieces));
        mCards = Collections.unmodifiableList(new ArrayList<>(cards));
        mOpening = opening;
        mPanes = Collections.unmodifiableList(new ArrayList<>(panes));
        mDividers = Collections.unmodifiableList(new ArrayList<>(dividers));
    }

    /** Every shown piece: the top stack, the bottom, the left, the right, then an overlay. */
    @NonNull
    public List<Piece> pieces() {
        return mPieces;
    }

    /** The piece with this id, or null when it is hidden or not in the arrangement. */
    @Nullable
    public Piece piece(@NonNull PieceId id) {
        for (Piece piece : mPieces) if (piece.id == id) return piece;
        return null;
    }

    /** The cards of the chrome: one frame under Docked, any number of cards under Floating. */
    @NonNull
    public List<Card> cards() {
        return mCards;
    }

    /** The card a piece belongs to. */
    @Nullable
    public Card cardOf(@NonNull Piece piece) {
        for (Card card : mCards) if (card.id == piece.groupId) return card;
        return null;
    }

    @NonNull
    public Opening opening() {
        return mOpening;
    }

    /** The panes, in reading order across the opening. */
    @NonNull
    public List<Pane> panes() {
        return mPanes;
    }

    /** Always empty since the rounded insert: a gutter of Margin separates panes under both Styles. */
    @NonNull
    public List<Divider> dividers() {
        return mDividers;
    }
}
