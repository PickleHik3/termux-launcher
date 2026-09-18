package com.termux.app.help;

/**
 * The pixel vocabulary help lays itself out in: a box, a line between two of them, and which edge
 * of the wall a control is past. Deterministic, pixel-only, no Android types and no retained state.
 *
 * <p>{@link HelpExplorePlacement} seats the one card of "Explore this screen" in these terms, and
 * the extra keys' own cards are arranged against them. The many-cards-at-once router that used to
 * live here went with the overview it drew.
 */
public final class HelpLeaderRouter {
    /**
     * Which edge of the wall a control lies past, if any. The dock, the A-Z row, the extra keys and
     * the status bar can each live on any edge, and are treated alike: a control past an edge takes
     * its card between itself and the wall, facing itself, and a control {@code INSIDE} the wall
     * takes its card beside itself.
     */
    public enum Side { ABOVE, BELOW, LEFT, RIGHT, INSIDE, UNDER }

    public static final class Box {
        public final float left, top, right, bottom;
        public Box(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public float cx() { return (left + right) / 2; }
        public float cy() { return (top + bottom) / 2; }
        public boolean overlaps(Box b) {
            return left < b.right && right > b.left && top < b.bottom && bottom > b.top;
        }
    }

    /** One leg of a leader; help's leaders are axis-aligned, so a segment is its own box. */
    public static final class Segment {
        public final float x1, y1, x2, y2;
        Segment(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        /**
         * Boundary contact counts for lines, so collinear lanes cannot be shared: a crossing, a
         * shared endpoint and an overlapping run along one line are all the same conflict.
         */
        public boolean intersects(Segment s) { return separation(s) <= 0; }
        /** Two lines in one lane must clear each other, not merely miss: parallel runs need room. */
        public boolean tooClose(Segment s, float clearance) { return separation(s) < clearance; }
        /**
         * The distance between the two lines. Leaders are axis-aligned, so each segment is its
         * own bounding box and the gap between those boxes is the exact distance; zero means they
         * cross, touch or share a stretch of one line.
         */
        public float separation(Segment s) {
            float dx = Math.max(0, Math.max(Math.min(x1, x2) - Math.max(s.x1, s.x2),
                Math.min(s.x1, s.x2) - Math.max(x1, x2)));
            float dy = Math.max(0, Math.max(Math.min(y1, y2) - Math.max(s.y1, s.y2),
                Math.min(s.y1, s.y2) - Math.max(y1, y2)));
            return dx == 0 ? dy : dy == 0 ? dx : (float) Math.hypot(dx, dy);
        }
        /** Endpoints may touch their own box/card boundary, never enter its interior. */
        public boolean enters(Box b) {
            if (x1 == x2) return x1 > b.left && x1 < b.right
                && Math.max(y1, y2) > b.top && Math.min(y1, y2) < b.bottom;
            return y1 > b.top && y1 < b.bottom
                && Math.max(x1, x2) > b.left && Math.min(x1, x2) < b.right;
        }
    }

    private HelpLeaderRouter() {}
}
