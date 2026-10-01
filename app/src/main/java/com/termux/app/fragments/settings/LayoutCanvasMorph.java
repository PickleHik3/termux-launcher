package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Corners;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the layout canvas draws while Style flips (SPEC section 3.7): the corners, gaps and joins
 * of the shape the canvas stood in, moved over {@link #DURATION_MS} to those of the shape it is
 * going to, each piece's rect and corners interpolated on their own. Pure and view-free, like the
 * shape model it reads, so the in-between frames can be tested on the JVM.
 *
 * <p>Both shapes are asked of the same arrangement, so every piece is in both. A piece in only
 * one of them (the arrangement changed under the animation) is taken as it is.
 */
final class LayoutCanvasMorph {

    private LayoutCanvasMorph() {}

    /** How long the canvas takes to change Style. With reduced motion it does not take any. */
    static final long DURATION_MS = 250L;

    /** One piece, or the pane, part-way between its two shapes. */
    static final class Part {
        /** The piece it is, or null for the pane. */
        @Nullable final PieceId id;
        @NonNull final Box box;
        @NonNull final Corners corners;
        final boolean overlay;

        Part(@Nullable PieceId id, @NonNull Box box, @NonNull Corners corners, boolean overlay) {
            this.id = id;
            this.box = box;
            this.corners = corners;
            this.overlay = overlay;
        }
    }

    /** Every piece and the pane at one moment of the change. */
    static final class Blend {
        @NonNull final List<Part> pieces;
        @Nullable final Part pane;

        Blend(@NonNull List<Part> pieces, @Nullable Part pane) {
            this.pieces = Collections.unmodifiableList(pieces);
            this.pane = pane;
        }

        /** The piece with this id, or null. */
        @Nullable
        Part piece(@NonNull PieceId id) {
            for (Part part : pieces) if (part.id == id) return part;
            return null;
        }
    }

    /** The change's pace: it starts fast and settles, the way the rest of the editor moves. */
    static float ease(float t) {
        float clamped = clamp01(t);
        float left = 1f - clamped;
        return 1f - left * left * left;
    }

    static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    /** {@code a} at 0 and {@code b} at 1 exactly, whatever the floats would round to between. */
    static float lerp(float a, float b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return a + (b - a) * t;
    }

    @NonNull
    static Box lerp(@NonNull Box a, @NonNull Box b, float t) {
        return new Box(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t),
            lerp(a.bottom, b.bottom, t));
    }

    @NonNull
    static Corners lerp(@NonNull Corners a, @NonNull Corners b, float t) {
        return new Corners(lerp(a.topLeft, b.topLeft, t), lerp(a.topRight, b.topRight, t),
            lerp(a.bottomRight, b.bottomRight, t), lerp(a.bottomLeft, b.bottomLeft, t));
    }

    /**
     * The shapes {@code t} of the way from {@code from} to {@code to}: {@code 0} is {@code from}
     * as it was, {@code 1} is {@code to} as it is.
     */
    @NonNull
    static Blend blend(@NonNull ChromeShape from, @NonNull ChromeShape to, float t) {
        float at = clamp01(t);
        List<Part> parts = new ArrayList<>(to.pieces().size());
        for (Piece piece : to.pieces()) {
            Piece before = from.piece(piece.id);
            if (before == null) {
                parts.add(new Part(piece.id, piece.box, piece.corners, piece.overlay));
            } else {
                parts.add(new Part(piece.id, lerp(before.box, piece.box, at),
                    lerp(before.corners, piece.corners, at), piece.overlay));
            }
        }
        Part pane = null;
        if (!to.panes().isEmpty()) {
            ChromeShape.Pane after = to.panes().get(0);
            if (from.panes().isEmpty()) {
                pane = new Part(null, after.box, after.corners, false);
            } else {
                ChromeShape.Pane before = from.panes().get(0);
                pane = new Part(null, lerp(before.box, after.box, at),
                    lerp(before.corners, after.corners, at), false);
            }
        }
        return new Blend(parts, pane);
    }
}
