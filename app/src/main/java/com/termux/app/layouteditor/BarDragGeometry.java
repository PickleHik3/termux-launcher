package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of dragging a bar on the editor's frame, with no views: a finger becomes a
 * {@link Hold} for {@link BarLandingPolicy#land}, and the {@link Landing} it gets back becomes the
 * line the frame draws. The view reports where every bar stands once, at the start of the hold
 * ({@link Frame}); nothing here measures, lays out or allocates per move beyond the Hold.
 *
 * <p>The gap a finger is nearest is counted the way {@code EdgeStackPolicy.Drop#index} counts: among
 * the bars standing on that edge without the held one, 0 outermost (nearest the screen edge). The
 * two sides of the keyboard along the bottom are separate groups.
 */
public final class BarDragGeometry {

    private BarDragGeometry() {}

    /** One bar standing on an edge, in the frame's coordinates. */
    private static final class Band {
        final Bar bar;
        final Edge edge;
        final boolean underKeyboard;
        final float left, top, right, bottom;

        Band(Bar bar, Edge edge, boolean underKeyboard, float left, float top, float right,
             float bottom) {
            this.bar = bar;
            this.edge = edge;
            this.underKeyboard = underKeyboard;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * The frame as it stands when a bar is picked up: its size, the bars on each edge, the
     * keyboard, the pane (the status bar's other place) and the Hidden column when they are on
     * screen. A bar that is not shown has no band.
     */
    public static final class Frame {
        final float width;
        final float height;
        private final List<Band> mBands = new ArrayList<>(8);
        @Nullable private float[] mKeyboard;
        @Nullable private float[] mPane;
        private float mPaneReach;
        @Nullable private float[] mHidden;
        /** Scratch for {@link #slotLine}: the group's outer and inner distances, sorted. */
        private final float[] mOuter = new float[8];
        private final float[] mInner = new float[8];

        public Frame(float width, float height) {
            this.width = width;
            this.height = height;
        }

        public void addBand(@NonNull Bar bar, @NonNull Edge edge, boolean underKeyboard,
                            float left, float top, float right, float bottom) {
            mBands.add(new Band(bar, edge, underKeyboard, left, top, right, bottom));
        }

        /** The keyboard's rect; with it, a finger below its middle is under the keyboard. */
        public void setKeyboard(float left, float top, float right, float bottom) {
            mKeyboard = new float[] {left, top, right, bottom};
        }

        /**
         * The pane's rect, where the status bar may stand as a thin row. A finger inside it and
         * more than {@code edgeReach} from every side of the frame is over the pane; nearer a
         * side it is over that edge.
         */
        public void setPane(float left, float top, float right, float bottom, float edgeReach) {
            mPane = new float[] {left, top, right, bottom};
            mPaneReach = edgeReach;
        }

        /** The Hidden column's rect (#339). */
        public void setHidden(float left, float top, float right, float bottom) {
            mHidden = new float[] {left, top, right, bottom};
        }
    }

    private static boolean contains(float[] rect, float x, float y) {
        return x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }

    /** How far from the screen edge {@code edge}'s point is. */
    private static float fromEdge(@NonNull Frame frame, @NonNull Edge edge, float x, float y) {
        switch (edge) {
            case TOP: return y;
            case BOTTOM: return frame.height - y;
            case LEFT: return x;
            default: return frame.width - x;
        }
    }

    /** What the finger at ({@code x}, {@code y}) holds {@code held} over. */
    @NonNull
    public static Hold holdAt(@NonNull Frame frame, @NonNull Bar held, float x, float y) {
        if (frame.mHidden != null && contains(frame.mHidden, x, y)) return Hold.hidden();
        Edge nearest = Edge.TOP;
        float best = Float.MAX_VALUE;
        for (Edge edge : Edge.values()) {
            float distance = fromEdge(frame, edge, x, y);
            if (distance < best) {
                best = distance;
                nearest = edge;
            }
        }
        if (frame.mPane != null && best > frame.mPaneReach && contains(frame.mPane, x, y))
            return Hold.pane();
        boolean under = nearest == Edge.BOTTOM && frame.mKeyboard != null
            && y >= (frame.mKeyboard[1] + frame.mKeyboard[3]) / 2f;
        float finger = fromEdge(frame, nearest, x, y);
        int index = 0;
        for (Band band : frame.mBands) {
            if (band.bar == held || band.edge != nearest || band.underKeyboard != under)
                continue;
            float centre = fromEdge(frame, nearest, (band.left + band.right) / 2f,
                (band.top + band.bottom) / 2f);
            if (centre < finger) index++;
        }
        return under ? Hold.underKeyboard(index) : Hold.edge(nearest, index);
    }

    /**
     * The line the frame draws for {@code landing}, as x1, y1, x2, y2 in {@code out}: across the
     * gap for an edge, along the pane's top for the pane, round the column for Hidden. False when
     * the landing is refused or its place is not on screen: nothing is drawn.
     */
    public static boolean slotLine(@NonNull Frame frame, @NonNull Bar held,
                                   @NonNull Landing landing, @NonNull float[] out) {
        if (!landing.allowed) return false;
        if (landing.zone == Zone.HIDDEN) {
            if (frame.mHidden == null) return false;
            System.arraycopy(frame.mHidden, 0, out, 0, 4);
            return true;
        }
        if (landing.zone == Zone.PANE) {
            if (frame.mPane == null) return false;
            out[0] = frame.mPane[0];
            out[1] = frame.mPane[1];
            out[2] = frame.mPane[2];
            out[3] = frame.mPane[1];
            return true;
        }
        Edge edge = landing.edge;
        if (edge == null) return false;
        // The group's bands by distance from the screen edge, outer first (insertion sort: <= 6).
        int count = 0;
        for (Band band : frame.mBands) {
            if (band.bar == held || band.edge != edge || band.underKeyboard != landing.underKeyboard
                || count >= frame.mOuter.length)
                continue;
            float outer = distanceOfSide(frame, edge, band, true);
            float inner = distanceOfSide(frame, edge, band, false);
            int at = count++;
            while (at > 0 && frame.mOuter[at - 1] > outer) {
                frame.mOuter[at] = frame.mOuter[at - 1];
                frame.mInner[at] = frame.mInner[at - 1];
                at--;
            }
            frame.mOuter[at] = outer;
            frame.mInner[at] = inner;
        }
        int gap = Math.max(0, Math.min(landing.index, count));
        float distance;
        if (count == 0) {
            distance = edge == Edge.BOTTOM && !landing.underKeyboard && frame.mKeyboard != null
                ? frame.height - frame.mKeyboard[1] : 0f;
        } else if (gap == 0) {
            distance = frame.mOuter[0];
        } else if (gap == count) {
            distance = frame.mInner[count - 1];
        } else {
            distance = (frame.mInner[gap - 1] + frame.mOuter[gap]) / 2f;
        }
        switch (edge) {
            case TOP:
                out[0] = 0; out[1] = distance; out[2] = frame.width; out[3] = distance;
                break;
            case BOTTOM:
                out[0] = 0; out[1] = frame.height - distance;
                out[2] = frame.width; out[3] = frame.height - distance;
                break;
            case LEFT:
                out[0] = distance; out[1] = 0; out[2] = distance; out[3] = frame.height;
                break;
            default:
                out[0] = frame.width - distance; out[1] = 0;
                out[2] = frame.width - distance; out[3] = frame.height;
                break;
        }
        return true;
    }

    private static float distanceOfSide(Frame frame, Edge edge, Band band, boolean outer) {
        switch (edge) {
            case TOP: return outer ? band.top : band.bottom;
            case BOTTOM: return frame.height - (outer ? band.bottom : band.top);
            case LEFT: return outer ? band.left : band.right;
            default: return frame.width - (outer ? band.right : band.left);
        }
    }
}
