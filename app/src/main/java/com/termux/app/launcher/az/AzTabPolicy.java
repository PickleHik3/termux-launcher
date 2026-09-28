package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.CornerZones;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where the minimised A&#8211;Z index's pull tab stands, what a finger has to land on to take it,
 * and where the letters slide out to while it is held. All of it measured in the canvas — the
 * content's own box, which is what the tab and the letters are laid over — so the view that draws
 * them only has to apply the answer.
 *
 * <p>The tab rests on the index's own edge at the leading end, just past the corner square the
 * corner tab keeps ({@link CornerZones#PANE_SIZE_DP}), so a hold on the corner is never the tab's.
 * Its visible pill is the design's 48 &times; 28 dp; the area that takes a touch is 56 dp along the
 * edge and 48 dp in from it, the platform's minimum target, reaching the canvas edge itself so a
 * thumb pressed against the rim still finds it. That area is exactly what the tab takes from the
 * content under it and nothing else.
 *
 * <p>The letters come out as the same bar they are on any edge — the letter band and its chin —
 * standing {@code marginPx} in from the edge, the air a bar off the dock keeps, and running the
 * canvas's length (less the dock's side inset for a row). Tucked away they sit one bar's travel
 * further out, past the canvas edge, which is what the slide springs between.
 *
 * <p>Pure: no views, only pixels and a density.
 */
public final class AzTabPolicy {

    private AzTabPolicy() {}

    /** The visible pill along its edge, and across it: the design's 48 &times; 28. */
    public static final float TAB_LENGTH_DP = 48f;
    public static final float TAB_THICKNESS_DP = 28f;
    /** The pill's corner radius, the design's 10. */
    public static final float TAB_RADIUS_DP = 10f;
    /** The pill's own air from the canvas edge. */
    public static final float TAB_EDGE_GAP_DP = 6f;
    /** What takes the touch: a little longer than the pill, and a full target deep. */
    public static final float TOUCH_LENGTH_DP = 56f;
    public static final float TOUCH_THICKNESS_DP = 48f;
    /** The air between the corner's square and the tab's touch area. */
    public static final float CORNER_GAP_DP = 8f;

    /**
     * The least opacity the tab and the letters' sheet are glazed at. Both stand over live
     * content — terminal text, a widget, an X window — not over the wallpaper the dock's own glass
     * is tuned against, and a letter over text has to read.
     */
    public static final float MIN_GLASS_OPACITY = 0.85f;

    /** An axis-aligned box in canvas pixels. */
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
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        public boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        public boolean contains(float x, float y) {
            return !isEmpty() && x >= left && x < right && y >= top && y < bottom;
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Box)) return false;
            Box that = (Box) other;
            return left == that.left && top == that.top && right == that.right
                && bottom == that.bottom;
        }

        @Override public int hashCode() {
            int result = Float.floatToIntBits(left);
            result = 31 * result + Float.floatToIntBits(top);
            result = 31 * result + Float.floatToIntBits(right);
            return 31 * result + Float.floatToIntBits(bottom);
        }

        @NonNull @Override public String toString() {
            return "Box{" + left + "," + top + "," + right + "," + bottom + "}";
        }
    }

    public static final Box EMPTY = new Box(0f, 0f, 0f, 0f);

    /** The tab on one canvas: the area that takes the touch, and the pill drawn inside it. */
    public static final class Placement {
        @NonNull public final Edge edge;
        /** The touch area, in canvas pixels. */
        @NonNull public final Box touch;
        /** The visible pill, in canvas pixels; always inside {@link #touch}. */
        @NonNull public final Box visual;

        Placement(@NonNull Edge edge, @NonNull Box touch, @NonNull Box visual) {
            this.edge = edge;
            this.touch = touch;
            this.visual = visual;
        }

        public boolean hit(float x, float y) {
            return touch.contains(x, y);
        }
    }

    /** How far along its edge the tab's touch area starts: past the corner square, then air. */
    public static float leadInPx(float density) {
        return (CornerZones.PANE_SIZE_DP + CORNER_GAP_DP) * Math.max(0f, density);
    }

    /**
     * Where the tab stands on a canvas {@code widthPx} &times; {@code heightPx}.
     *
     * <p>Along a row the leading end is the left, or the right in a right-to-left layout; down a
     * column it is the top, whichever way text runs. A canvas too short for the lead-in and the
     * tab together keeps the tab whole and gives up the lead-in first; one shorter than the tab
     * shrinks the tab to fit.
     */
    @NonNull
    public static Placement place(@NonNull Edge edge, boolean rtl, float widthPx, float heightPx,
                                  float density) {
        float d = Math.max(0f, density);
        float width = Math.max(0f, widthPx);
        float height = Math.max(0f, heightPx);
        boolean column = edge.isOnSide();
        float length = column ? height : width;
        float depth = column ? width : height;

        float touchLength = Math.min(TOUCH_LENGTH_DP * d, length);
        float touchDepth = Math.min(TOUCH_THICKNESS_DP * d, depth);
        float start = Math.max(0f, Math.min(leadInPx(d), length - touchLength));
        float visualLength = Math.min(TAB_LENGTH_DP * d, touchLength);
        float visualStart = start + (touchLength - visualLength) / 2f;
        float gap = Math.min(TAB_EDGE_GAP_DP * d, touchDepth);
        float visualDepth = Math.min(TAB_THICKNESS_DP * d, touchDepth - gap);

        // Along: measured from the leading end; a right-to-left row reads it from the right.
        boolean fromFarEnd = !column && rtl;
        float alongFrom = fromFarEnd ? length - start - touchLength : start;
        float visualAlongFrom = fromFarEnd ? length - visualStart - visualLength : visualStart;

        Box touch = box(edge, width, height, alongFrom, touchLength, 0f, touchDepth);
        Box visual = box(edge, width, height, visualAlongFrom, visualLength, gap, visualDepth);
        return new Placement(edge, touch, visual);
    }

    /**
     * Where the letters stand while they are out: the bar's own thickness, {@code marginPx} in
     * from the edge, along the whole canvas less {@code sideInsetPx} at each end of a row. A
     * column runs the canvas's height less the same margin top and bottom.
     */
    @NonNull
    public static Box revealBox(@NonNull Edge edge, float widthPx, float heightPx,
                                float thicknessPx, float marginPx, float sideInsetPx) {
        float width = Math.max(0f, widthPx);
        float height = Math.max(0f, heightPx);
        boolean column = edge.isOnSide();
        float length = column ? height : width;
        float depth = column ? width : height;
        float margin = Math.max(0f, Math.min(marginPx, depth));
        float thickness = Math.max(0f, Math.min(thicknessPx, depth - margin));
        float inset = column ? margin : Math.max(0f, sideInsetPx);
        inset = Math.min(inset, length / 2f);
        return box(edge, width, height, inset, length - 2f * inset, margin, thickness);
    }

    /**
     * How far the letters stand from where they rest out, at a reveal {@code progress} between 0
     * (tucked) and 1 (out): pushed back past the edge they stand on by {@code travelPx} times what
     * is left of the way. Returned as {@code {dx, dy}}, in screen directions.
     */
    @NonNull
    public static float[] slideOffset(@NonNull Edge edge, float progress, float travelPx) {
        float remaining = (1f - progress) * Math.max(0f, travelPx);
        switch (edge) {
            case TOP: return new float[] {0f, -remaining};
            case LEFT: return new float[] {-remaining, 0f};
            case RIGHT: return new float[] {remaining, 0f};
            case BOTTOM:
            default: return new float[] {0f, remaining};
        }
    }

    /** The whole way out: the bar and the air between it and the edge, so it leaves the canvas. */
    public static float travelPx(float thicknessPx, float marginPx) {
        return Math.max(0f, thicknessPx) + Math.max(0f, marginPx);
    }

    /**
     * A box {@code alongLength} long starting {@code alongFrom} along the edge, and
     * {@code depthLength} deep starting {@code depthFrom} in from it.
     */
    @NonNull
    private static Box box(@NonNull Edge edge, float width, float height, float alongFrom,
                           float alongLength, float depthFrom, float depthLength) {
        float alongTo = alongFrom + Math.max(0f, alongLength);
        float depthTo = depthFrom + Math.max(0f, depthLength);
        switch (edge) {
            case TOP: return new Box(alongFrom, depthFrom, alongTo, depthTo);
            case LEFT: return new Box(depthFrom, alongFrom, depthTo, alongTo);
            case RIGHT: return new Box(width - depthTo, alongFrom, width - depthFrom, alongTo);
            case BOTTOM:
            default: return new Box(alongFrom, height - depthTo, alongTo, height - depthFrom);
        }
    }
}
