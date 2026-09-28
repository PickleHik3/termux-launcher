package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.CornerZones;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where the minimised A&#8211;Z index's pull tab stands, what a finger has to land on to take it,
 * and where the letters slide out to while it is held, so the view that draws them only has to
 * apply the answer.
 *
 * <p>The tab is a half-pill flush against the physical screen's edge, outside the pane's border
 * rather than over the content: flat against the edge, rounded only on its inner side. It hugs the
 * side the index stands on when that is a side, and the row's leading side — the left, or the
 * right in a right-to-left layout — when the index is a row along the top or the bottom, since
 * those two edges are the system bars' and the chrome's. Along that side it stands at the index's
 * leading end of the canvas — its top, or its bottom for a bottom row — just past the corner square
 * the corner tab keeps ({@link CornerZones#PANE_SIZE_DP}), so a hold on the corner is never the
 * tab's. Its visible half-pill is 48 &times; 20 dp; the area that takes a touch is 56 dp along the
 * side and 48 dp in from it, the platform's minimum target. That area is exactly what the tab
 * takes from whatever is under it, and nothing else.
 *
 * <p>The letters come out as the same bar they are on any edge — the letter band and its chin —
 * standing {@code marginPx} in from the canvas edge, the air a bar off the dock keeps, and running
 * the canvas's length (less the dock's side inset for a row). Tucked away they sit one bar's
 * travel further out, past the canvas edge, which is what the slide springs between. The finger
 * that lands on the tab is handed to them as if it had landed on their middle line
 * ({@link #shiftOntoLetters}), so touching the tab and sliding along is one scrub.
 *
 * <p>Pure: no views, only pixels and a density. The tab is measured in the screen-wide layer it is
 * drawn in, the letters in the canvas.
 */
public final class AzTabPolicy {

    private AzTabPolicy() {}

    /** The visible half-pill along the screen's side, and in from it. */
    public static final float TAB_LENGTH_DP = 48f;
    public static final float TAB_THICKNESS_DP = 20f;
    /**
     * The inner side's corner radius: the half-pill's own thickness, clamped to half its length,
     * so the inner side is one round end. The side against the screen's edge is square.
     */
    public static final float TAB_RADIUS_DP = TAB_THICKNESS_DP;
    /** What takes the touch: a little longer than the half-pill, and a full target deep. */
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

    /** An axis-aligned box, in the pixels of whichever frame its caller measures in. */
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

    /** The tab on the screen: the area that takes the touch, and the half-pill drawn inside it. */
    public static final class Placement {
        /** The index's own edge, which the letters come out of. */
        @NonNull public final Edge edge;
        /** The touch area, in the screen-wide layer's pixels. */
        @NonNull public final Box touch;
        /** The visible half-pill; always inside {@link #touch}. */
        @NonNull public final Box visual;
        /** The screen's side the tab is flush against: its flat side is this one. */
        @NonNull public final Edge side;

        Placement(@NonNull Edge edge, @NonNull Box touch, @NonNull Box visual,
                  @NonNull Edge side) {
            this.edge = edge;
            this.touch = touch;
            this.visual = visual;
            this.side = side;
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
     * The screen's side the tab hugs: the index's own for a column, and for a row its leading
     * side — the left, the right in a right-to-left layout.
     */
    @NonNull
    public static Edge screenSide(@NonNull Edge edge, boolean rtl) {
        if (edge.isOnSide()) return edge;
        return rtl ? Edge.RIGHT : Edge.LEFT;
    }

    /**
     * Where the tab stands in a layer {@code screenWidthPx} wide that reaches the screen's side,
     * over a canvas standing at {@code canvas} in that layer's pixels.
     *
     * <p>Flush against {@link #screenSide}, and along it at the index's leading end of the
     * canvas: past the lead-in from the canvas's top for a column or a top row, and from its
     * bottom for a bottom row, which leads from the dock's end. A canvas too short for the lead-in
     * and the tab together keeps the tab whole and gives up the lead-in first; one shorter than
     * the tab shrinks the tab to fit. Nothing is placed on an empty canvas.
     */
    @NonNull
    public static Placement placeOnScreen(@NonNull Edge edge, boolean rtl, @NonNull Box canvas,
                                          float screenWidthPx, float density) {
        Edge side = screenSide(edge, rtl);
        if (canvas.isEmpty() || screenWidthPx <= 0f) return new Placement(edge, EMPTY, EMPTY, side);
        float d = Math.max(0f, density);
        float length = canvas.height();
        float touchLength = Math.min(TOUCH_LENGTH_DP * d, length);
        float touchDepth = Math.min(TOUCH_THICKNESS_DP * d, screenWidthPx);
        float start = Math.max(0f, Math.min(leadInPx(d), length - touchLength));
        float visualLength = Math.min(TAB_LENGTH_DP * d, touchLength);
        float visualDepth = Math.min(TAB_THICKNESS_DP * d, touchDepth);

        // Along the side, from the index's leading end of the canvas.
        float touchTop = edge == Edge.BOTTOM
            ? canvas.bottom - start - touchLength : canvas.top + start;
        float visualTop = touchTop + (touchLength - visualLength) / 2f;

        // Across, from the screen's side inwards.
        boolean left = side == Edge.LEFT;
        Box touch = left
            ? new Box(0f, touchTop, touchDepth, touchTop + touchLength)
            : new Box(screenWidthPx - touchDepth, touchTop, screenWidthPx, touchTop + touchLength);
        Box visual = left
            ? new Box(0f, visualTop, visualDepth, visualTop + visualLength)
            : new Box(screenWidthPx - visualDepth, visualTop, screenWidthPx,
                visualTop + visualLength);
        return new Placement(edge, touch, visual, side);
    }

    /**
     * How far a finger that lands on the tab at ({@code downX}, {@code downY}) is moved for the
     * letters to hear it: across their edge onto their middle line, and not at all along it, so
     * it lands where the letters at that point along the edge will be. Fixed at the touch-down and
     * kept for the whole stream, which is what makes the slide along the tab a slide along the
     * letters. Returned as {@code {dx, dy}}; nothing for letters that have no box.
     *
     * @param letters where the letters rest while out, in the same pixels as the finger
     */
    @NonNull
    public static float[] shiftOntoLetters(@NonNull Edge edge, @NonNull Box letters, float downX,
                                           float downY) {
        if (letters.isEmpty()) return new float[] {0f, 0f};
        if (edge.isOnSide()) return new float[] {(letters.left + letters.right) / 2f - downX, 0f};
        return new float[] {0f, (letters.top + letters.bottom) / 2f - downY};
    }

    /**
     * What the tab is filled with under its glass: nothing where the dock's glass is available,
     * the glass itself being the material, and the glass's own base colour made solid where it is
     * not — the Solid material, or a wallpaper nothing can be blurred from.
     */
    @ColorInt
    public static int tabFill(boolean glassAvailable, @ColorInt int glassBase) {
        return glassAvailable ? 0 : OnGlass.opaque(glassBase);
    }

    /**
     * The "A"'s ink on the tab: the letters' own colour moved along the tone axis until it reads
     * as body text on the tab's base, which is what stands behind it at the opacity floor or
     * solid. The tab stands over live content and the wallpaper at once, so the letters' ink —
     * resolved for the dock's glass over the wallpaper — is only its seed.
     */
    @ColorInt
    public static int glyphInk(@ColorInt int glassBase, @ColorInt int lettersInk) {
        return GlassInk.legible(OnGlass.opaque(glassBase), lettersInk, OnGlass.TARGET_BODY_TEXT);
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
