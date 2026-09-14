package com.termux.app.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic, pixel-only layout. No Android types or retained state. */
public final class HelpLeaderRouter {
    public enum Side { ABOVE, BELOW, LEFT, RIGHT, INSIDE }

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

    public static final class Target {
        public final String id;
        public final Box box;
        public final Side side;
        public final float cardWidth, cardHeight;
        public Target(String id, Box box, Side side, float cardWidth, float cardHeight) {
            this.id = id; this.box = box; this.side = side;
            this.cardWidth = cardWidth; this.cardHeight = cardHeight;
        }
    }

    public static final class Segment {
        public final float x1, y1, x2, y2;
        Segment(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        /** Boundary contact counts for lines, so collinear lanes cannot be shared. */
        public boolean intersects(Segment s) {
            return Math.max(Math.min(x1, x2), Math.min(s.x1, s.x2))
                    <= Math.min(Math.max(x1, x2), Math.max(s.x1, s.x2))
                && Math.max(Math.min(y1, y2), Math.min(s.y1, s.y2))
                    <= Math.min(Math.max(y1, y2), Math.max(s.y1, s.y2));
        }
        /** Endpoints may touch their own box/card boundary, never enter its interior. */
        public boolean enters(Box b) {
            if (x1 == x2) return x1 > b.left && x1 < b.right
                && Math.max(y1, y2) > b.top && Math.min(y1, y2) < b.bottom;
            return y1 > b.top && y1 < b.bottom
                && Math.max(x1, x2) > b.left && Math.min(x1, x2) < b.right;
        }
    }

    public static final class Placement {
        public final Target target;
        public final Box card;
        public final List<Segment> lines;
        public final int page, column, lane;
        Placement(Target target, Box card, List<Segment> lines, int page, int column, int lane) {
            this.target = target; this.card = card;
            this.lines = Collections.unmodifiableList(lines);
            this.page = page; this.column = column; this.lane = lane;
        }
    }

    public static final class Result {
        public final List<Placement> placements;
        /** Geometrically impossible targets are explicit; callers must still present their copy. */
        public final List<Target> unplaced;
        public final int pages;
        Result(List<Placement> placements, List<Target> unplaced, int pages) {
            this.placements = Collections.unmodifiableList(placements);
            this.unplaced = Collections.unmodifiableList(unplaced);
            this.pages = pages;
        }
    }

    public static Result route(float width, float height, Box band, float gutter,
                               float gap, List<Target> input) {
        List<Target> targets = new ArrayList<>();
        for (Target t : input) if (t != null && t.box != null
                && t.box.width() > 0 && t.box.height() > 0) targets.add(t);
        targets.sort(Comparator.comparingDouble((Target t) -> t.box.top)
            .thenComparingDouble(t -> t.box.left).thenComparing(t -> t.id));
        List<Placement> placed = new ArrayList<>();
        List<Target> unplaced = new ArrayList<>();
        int pages = 1;
        for (Target t : targets) {
            Placement found = null;
            for (int page = 0; page <= pages && found == null; page++) {
                int preferred = t.box.cx() < width / 2 ? 0 : 1;
                for (int attempt = 0; attempt < 2 && found == null; attempt++) {
                    int column = attempt == 0 ? preferred : 1 - preferred;
                    float columnLeft = column == 0 ? band.left + gutter : band.cx() + gap / 2;
                    float columnRight = column == 0 ? band.cx() - gap / 2 : band.right - gutter;
                    if (t.cardWidth > columnRight - columnLeft || t.cardHeight > band.height()) continue;
                    float x = column == 0 ? columnLeft : columnRight - t.cardWidth;
                    for (float y = band.top; y + t.cardHeight <= band.bottom; y += Math.max(1, gap)) {
                        Box card = new Box(x, y, x + t.cardWidth, y + t.cardHeight);
                        int lane = t.side == Side.BELOW ? column : -1;
                        for (List<Segment> path : paths(t, card, column, width, gutter)) {
                            Placement p = new Placement(t, card, path, page, column, lane);
                            if (valid(p, placed, width, height)) { found = p; break; }
                        }
                        if (found != null) break;
                    }
                }
                // One empty page proves whether pagination can help this target.
                if (page >= pages) break;
            }
            if (found == null) unplaced.add(t);
            else { placed.add(found); pages = Math.max(pages, found.page + 1); }
        }
        return new Result(placed, unplaced, pages);
    }

    private static List<List<Segment>> paths(Target t, Box c, int column, float width, float gutter) {
        List<List<Segment>> paths = new ArrayList<>();
        Box b = t.box;
        if (t.side == Side.ABOVE) {
            float l = Math.max(b.left, c.left), r = Math.min(b.right, c.right);
            if (l < r && b.bottom <= c.top) paths.add(path((l+r)/2, b.bottom, (l+r)/2, c.top));
        } else if (t.side == Side.BELOW) {
            float lane = column == 0 ? gutter / 2 : width - gutter / 2;
            float start = column == 0 ? b.left : b.right;
            float end = column == 0 ? c.left : c.right;
            if (c.bottom <= b.top) paths.add(path(start, b.cy(), lane, b.cy(), lane, c.cy(), end, c.cy()));
        } else {
            boolean left = b.cx() < c.cx();
            float start = left ? b.right : b.left, end = left ? c.left : c.right;
            float low = Math.max(b.top, c.top), high = Math.min(b.bottom, c.bottom);
            if (low < high) paths.add(path(start, (low+high)/2, end, (low+high)/2));
            float middle = (start + end) / 2;
            paths.add(path(start, b.cy(), middle, b.cy(), middle, c.cy(), end, c.cy()));
            float sy = b.cy() < c.cy() ? b.bottom : b.top;
            float ey = b.cy() < c.cy() ? c.top : c.bottom;
            paths.add(path(b.cx(), sy, b.cx(), (sy+ey)/2, c.cx(), (sy+ey)/2, c.cx(), ey));
        }
        return paths;
    }

    private static List<Segment> path(float... points) {
        List<Segment> lines = new ArrayList<>();
        for (int i = 2; i < points.length; i += 2)
            if (points[i-2] != points[i] || points[i-1] != points[i+1])
                lines.add(new Segment(points[i-2], points[i-1], points[i], points[i+1]));
        return lines;
    }

    private static boolean valid(Placement p, List<Placement> placed, float width, float height) {
        if (p.card.overlaps(p.target.box) || p.lines.isEmpty()) return false;
        for (Segment s : p.lines) {
            if (Math.min(s.x1, s.x2) < 0 || Math.max(s.x1, s.x2) > width
                || Math.min(s.y1, s.y2) < 0 || Math.max(s.y1, s.y2) > height
                || s.enters(p.target.box) || s.enters(p.card)) return false;
        }
        for (Placement other : placed) {
            if (other.page != p.page) continue;
            if (p.lane >= 0 && p.lane == other.lane) return false;
            if (p.card.overlaps(other.card) || p.card.overlaps(other.target.box)
                || p.target.box.overlaps(other.card)) return false;
            if (p.column == other.column && p.card.top < other.card.bottom) return false;
            for (Segment s : p.lines) {
                if (s.enters(other.card) || s.enters(other.target.box)) return false;
                for (Segment os : other.lines) if (s.intersects(os)) return false;
            }
            for (Segment s : other.lines)
                if (s.enters(p.card) || s.enters(p.target.box)) return false;
        }
        return true;
    }
    private HelpLeaderRouter() {}
}
