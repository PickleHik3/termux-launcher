package com.termux.app.launcher.az;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * The edge the alphabets bar stands on, as arithmetic: one affine map between the screen and the
 * <em>canonical frame</em> the whole A–Z gesture is written in.
 *
 * <p>The canonical frame is the bar the gesture was built for: a horizontal row along the bottom
 * with the icon track above it. "Along" the bar is canonical x, growing the way the letters read;
 * "away" from the bar is canonical −y, so the icon track sits at a smaller y than the letters do,
 * exactly as it does for a bottom bar. Every positional decision in {@link AzScrubGesture} and
 * {@link AzFloatingStripPolicy} — the upward lock, the capture wedge, the return band, the strip's
 * slots, the paging ends — is therefore written once and holds on all four edges.
 *
 * <p>What each edge does to the screen:
 *
 * <ul>
 *   <li><b>Bottom</b> is the canonical frame; the map is the identity.
 *   <li><b>Top</b> mirrors y, so "away from the bar" becomes down the screen and the letters still
 *       read left to right.
 *   <li><b>Left</b> and <b>right</b> turn the screen on its side: canonical x is screen y, so the
 *       letters read down the column, and "away from the bar" points into the content — right of a
 *       left bar, left of a right bar. Paging, which is canonical left/right, therefore becomes up
 *       and down on screen.
 * </ul>
 *
 * <p>Pure: no {@code View}, no {@code Context}, no {@code RectF}. Rectangles travel as
 * {@link AzScrubGesture.Bounds}, the same immutable rectangle the gesture already speaks.
 */
public final class AzBarFrame {

    @NonNull private final Edge mEdge;
    private final float mScreenWidthPx;
    private final float mScreenHeightPx;

    private AzBarFrame(@NonNull Edge edge, float screenWidthPx, float screenHeightPx) {
        mEdge = edge;
        mScreenWidthPx = Math.max(0f, screenWidthPx);
        mScreenHeightPx = Math.max(0f, screenHeightPx);
    }

    /**
     * The frame for a bar on {@code edge}, measured against the space its coordinates are given in
     * — the screen for raw touch points and view positions, one view's own box for local ones.
     * Forward and inverse must be built from the same two lengths or the round trip drifts.
     */
    @NonNull
    public static AzBarFrame of(@NonNull Edge edge, float widthPx, float heightPx) {
        return new AzBarFrame(edge, widthPx, heightPx);
    }

    /** The identity frame, for a bar where the gesture was written: along the bottom. */
    @NonNull
    public static AzBarFrame bottom(float widthPx, float heightPx) {
        return new AzBarFrame(Edge.BOTTOM, widthPx, heightPx);
    }

    @NonNull
    public Edge edge() {
        return mEdge;
    }

    /** True when the bar is a column down one side rather than a row along the top or bottom. */
    public boolean isVertical() {
        return mEdge.isOnSide();
    }

    /** True when the map is the identity, i.e. nothing needs mapping at all. */
    public boolean isCanonical() {
        return mEdge == Edge.BOTTOM;
    }

    /** How long the bar's own axis is: the letters run along this. */
    public float alongLengthPx() {
        return isVertical() ? mScreenHeightPx : mScreenWidthPx;
    }

    /** How far the frame reaches away from the bar: the strip and the terminal live along this. */
    public float awayLengthPx() {
        return isVertical() ? mScreenWidthPx : mScreenHeightPx;
    }

    public float canonicalX(float screenX, float screenY) {
        switch (mEdge) {
            case LEFT:
            case RIGHT:
                return screenY;
            case TOP:
            case BOTTOM:
            default:
                return screenX;
        }
    }

    public float canonicalY(float screenX, float screenY) {
        switch (mEdge) {
            case TOP:
                return mScreenHeightPx - screenY;
            case LEFT:
                return mScreenWidthPx - screenX;
            case RIGHT:
                return screenX;
            case BOTTOM:
            default:
                return screenY;
        }
    }

    public float screenX(float canonicalX, float canonicalY) {
        switch (mEdge) {
            case LEFT:
                return mScreenWidthPx - canonicalY;
            case RIGHT:
                return canonicalY;
            case TOP:
            case BOTTOM:
            default:
                return canonicalX;
        }
    }

    public float screenY(float canonicalX, float canonicalY) {
        switch (mEdge) {
            case TOP:
                return mScreenHeightPx - canonicalY;
            case LEFT:
            case RIGHT:
                return canonicalX;
            case BOTTOM:
            default:
                return canonicalY;
        }
    }

    /**
     * The same rectangle in the canonical frame. Two opposite corners are mapped and re-sorted,
     * because a turn or a mirror swaps which corner is which.
     */
    @NonNull
    public AzScrubGesture.Bounds toCanonical(@NonNull AzScrubGesture.Bounds screen) {
        if (screen.isEmpty()) {
            return AzScrubGesture.Bounds.EMPTY;
        }
        return sorted(
            canonicalX(screen.left, screen.top), canonicalY(screen.left, screen.top),
            canonicalX(screen.right, screen.bottom), canonicalY(screen.right, screen.bottom));
    }

    /** The inverse of {@link #toCanonical}. */
    @NonNull
    public AzScrubGesture.Bounds toScreen(@NonNull AzScrubGesture.Bounds canonical) {
        if (canonical.isEmpty()) {
            return AzScrubGesture.Bounds.EMPTY;
        }
        return sorted(
            screenX(canonical.left, canonical.top), screenY(canonical.left, canonical.top),
            screenX(canonical.right, canonical.bottom), screenY(canonical.right, canonical.bottom));
    }

    /**
     * Where canonical {@code +y} points on screen, as a unit vector: towards the bar and past it.
     * Anything the strip does along the away axis — the few pixels it rises into place, the air it
     * keeps from the letters — is drawn with this rather than with a hardcoded "down".
     */
    public float awayDirectionX() {
        switch (mEdge) {
            case LEFT:
                return -1f;
            case RIGHT:
                return 1f;
            default:
                return 0f;
        }
    }

    public float awayDirectionY() {
        switch (mEdge) {
            case TOP:
                return -1f;
            case BOTTOM:
                return 1f;
            default:
                return 0f;
        }
    }

    @NonNull
    private static AzScrubGesture.Bounds sorted(float x1, float y1, float x2, float y2) {
        return new AzScrubGesture.Bounds(Math.min(x1, x2), Math.min(y1, y2),
            Math.max(x1, x2), Math.max(y1, y2));
    }
}
