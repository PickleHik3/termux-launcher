package com.termux.app.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic, pixel-only layout. No Android types or retained state. */
public final class HelpLeaderRouter {
    /**
     * Where a control is relative to the band, and so where its card belongs. A control outside
     * the band — above it, under it ({@code UNDER}, or {@code BELOW} in the older column layout),
     * or off to one side — takes its card on the nearest shelf between itself and the band, facing
     * itself; {@code INSIDE} the band a card sits beside its box. Nothing here names a layout: the
     * dock, the A-Z row, the keys and the status bar can live on any edge and are treated alike.
     */
    public enum Side { ABOVE, BELOW, LEFT, RIGHT, INSIDE, UNDER }

    /** How many shelves in from its own control a card may go before it gives up. */
    private static final int SHELVES = 4;

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
        return route(width, height, band, gutter, gap, input, Collections.emptyList());
    }

    /** Fixed labels and footer controls are obstacles on every page. */
    public static Result route(float width, float height, Box band, float gutter,
                               float gap, List<Target> input, List<Box> obstacles) {
        List<Target> targets = new ArrayList<>();
        for (Target t : input) if (t != null && t.box != null
                && t.box.width() > 0 && t.box.height() > 0) targets.add(t);
        targets.sort(Comparator.comparingDouble((Target t) -> t.box.top)
            .thenComparingDouble(t -> t.box.left).thenComparing(t -> t.id));
        List<Placement> placed = new ArrayList<>();
        List<Target> unplaced = new ArrayList<>();
        int pages = 0;
        for (Target t : targets) {
            Placement found = null;
            for (int page = 0; page <= pages && found == null; page++) {
                int preferred = t.box.cx() < width / 2 ? 0 : 1;
                for (int attempt = 0; attempt < 2 && found == null; attempt++) {
                    int column = attempt == 0 ? preferred : 1 - preferred;
                    float columnLeft = column == 0 ? band.left + gutter : band.cx() + gap / 2;
                    float columnRight = column == 0 ? band.cx() - gap / 2 : band.right - gutter;
                    if (t.cardWidth > columnRight - columnLeft || t.cardHeight > band.height()) continue;
                    float x = Math.max(columnLeft, Math.min(columnRight - t.cardWidth,
                        t.box.cx() - t.cardWidth / 2));
                    for (float y = band.top; y + t.cardHeight <= band.bottom; y += Math.max(1, gap)) {
                        Box card = new Box(x, y, x + t.cardWidth, y + t.cardHeight);
                        int lane = t.side == Side.BELOW ? column : -1;
                        for (List<Segment> path : paths(t, card, column, width, gutter)) {
                            Placement p = new Placement(t, card, path, page, column, lane);
                            if (valid(p, placed, width, height, obstacles)) { found = p; break; }
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

    /**
     * The colour-paired layout. Every card shares a colour with the box it explains, so the
     * layout's one job is to put each card where the eye looks for it. One rule covers every
     * control outside the band, whichever edge it lives on: its card sits on the first free shelf
     * between the control and the band, across from the control, and shares that shelf sideways
     * before it steps a shelf further in (see {@link #shelf}). A control inside the band gets its
     * card next to its box; a card that would land on another card or an obstacle slides away
     * from its control until it is clear, then tries the far side of the band. {@code hard}
     * obstacles (key labels, the footer, every control outside the band) always count,
     * {@code soft} ones — the boxes of controls inside the band — yield when nothing fits
     * otherwise, because a card over a dimmed pane beats a second page.
     * <p>No two leaders may touch: they cross nothing, share no lane, and keep {@code gap}
     * between parallel runs, so a line always reads as belonging to one card. Where two straight
     * leaders would coincide the later one takes another lane across its own card and box — one
     * elbow is allowed — and only when no lane is left does the card itself move. A card whose
     * leader cannot be kept off the other cards and boxes settles for a leader that only has to
     * clear the other leaders. A new page starts only when a card fits nowhere on the current one.
     */
    public static Result arrange(Box band, float gutter, float gap, List<Target> input,
                                 List<Box> hard, List<Box> soft) {
        List<Target> targets = new ArrayList<>();
        for (Target t : input) if (t != null && t.box != null
                && t.box.width() > 0 && t.box.height() > 0) targets.add(t);
        // The band's own controls in reading order, then the ones outside it, smallest first: a
        // control the size of a cog has one place its line can leave from, and a row the width of
        // the screen has the whole width, so the cog picks its shelf before the row does.
        targets.sort(Comparator.comparingInt((Target t) -> t.side == Side.INSIDE ? 0 : 1)
            .thenComparingDouble(t -> t.side != Side.INSIDE
                ? t.box.width() * (double) t.box.height() : t.box.top)
            .thenComparingDouble(t -> t.box.left).thenComparing(t -> t.id));
        List<Placement> placed = new ArrayList<>();
        List<Target> unplaced = new ArrayList<>();
        int page = 0, pages = 0;
        for (Target t : targets) {
            Placement found = onPage(t, page, band, gutter, gap, placed, hard, soft);
            if (found == null && pageHasCards(placed, page)) {
                page++;
                found = onPage(t, page, band, gutter, gap, placed, hard, soft);
            }
            if (found == null) { unplaced.add(t); continue; }
            placed.add(found);
            pages = Math.max(pages, found.page + 1);
        }
        return new Result(placed, unplaced, pages);
    }

    /**
     * Clear of everything first; failing that, clear of the other leaders at least; failing that,
     * the card goes where it belongs with no line at all, because the colour already pairs it with
     * its control and a line that runs into another one says less than no line.
     */
    private static Placement onPage(Target t, int page, Box band, float gutter, float gap,
                                    List<Placement> placed, List<Box> hard, List<Box> soft) {
        if (t.side != Side.INSIDE) return shelf(t, page, band, gutter, gap, placed, hard, soft);
        for (int mode = 0; mode < 3; mode++) {
            Placement found = arrangeOn(t, page, band, gutter, gap, placed, hard, soft, mode);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean pageHasCards(List<Placement> placed, int page) {
        for (Placement p : placed) if (p.page == page) return true;
        return false;
    }

    private static Placement arrangeOn(Target t, int page, Box band, float gutter, float gap,
                                       List<Placement> placed, List<Box> hard, List<Box> soft,
                                       int mode) {
        float left = band.left + gutter, right = band.right - gutter;
        float w = t.cardWidth, h = t.cardHeight;
        if (w > right - left + 0.5f || h > band.height() + 0.5f) return null;
        // Where the eye looks for it: beside the box, level with its middle.
        float x = clamp(t.box.cx() - w / 2, left, right - w);
        if (t.box.right + gap + w <= right) x = t.box.right + gap;
        else if (t.box.left - gap - w >= left) x = t.box.left - gap - w;
        float y = clamp(t.box.cy() - h / 2, band.top, band.bottom - h);
        int away = 1; // the direction that leads away from the control when the ideal spot is taken
        float farX = x + w / 2 < band.cx() ? right - w : left;
        List<Box> near = new ArrayList<>();
        for (Placement p : placed) if (p.page == page) { near.add(p.card); near.add(p.target.box); }
        // A leader that cannot be routed from here moves the card a little, never a hair.
        float step = Math.max(Math.max(1, gap), h / 2);
        for (int tier = 0; tier < 2; tier++) {
            List<Box> blocked = new ArrayList<>(hard);
            if (tier == 0) blocked.addAll(soft);
            for (Placement p : placed) if (p.page == page) blocked.add(p.card);
            for (float cx : new float[] {x, farX}) {
                for (int pass = 0; pass < 2; pass++) {
                    int d = pass == 0 ? away : -away;
                    float yy = y;
                    while (yy >= band.top - 0.5f && yy + h <= band.bottom + 0.5f) {
                        Box card = new Box(cx, yy, cx + w, yy + h);
                        Box hit = null;
                        for (Box b : blocked) if (card.overlaps(b)) {
                            if (hit == null) hit = b;
                            else if (d > 0 ? b.bottom < hit.bottom : b.top > hit.top) hit = b;
                        }
                        if (hit != null) {
                            yy = d > 0 ? Math.max(yy + 1, hit.bottom + gap)
                                       : Math.min(yy - 1, hit.top - gap - h);
                            continue;
                        }
                        int column = cx + w / 2 < band.cx() ? 0 : 1;
                        if (mode == 2) return new Placement(t, card, Collections.emptyList(),
                            page, column, -1);
                        for (List<Segment> path : leaders(t, card, gap, near)) {
                            // A control outside the band is only ever reached by a line, so an
                            // empty leader there is a last resort and not a first answer: a card
                            // that lands away from its box with nothing joining them reads as
                            // belonging to nothing. Inside the band the card touches its box and
                            // the absence of a line is the point.
                            if (path.isEmpty() && t.side != Side.INSIDE) continue;
                            if (clears(t, card, path, placed, page, gap, mode == 0))
                                return new Placement(t, card, path, page, column, -1);
                        }
                        yy += d > 0 ? step : -step;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A control outside the band — the dock, a bar along the top, a rail down one side, a key of
     * the keyboard — takes its card on the free spot nearest to straight across from itself, on
     * the shelves between itself and the band: the first shelf leans on the control, the next is a
     * card's thickness further in, and a spot is as near as its distance along the shelf plus its
     * distance in. So a card shares its shelf with a neighbour when the next slot along is closer
     * than the next shelf in, and steps in when it is not; either way the card stays about its
     * control, which is what {@link #SHELVES} bounds. Shelves run parallel to the edge the control
     * is on and are walked toward the band, so the same rule seats a status bar at the top, a dock
     * at the bottom and a column of keys down the right.
     * <p>A spot mostly on one of the band's own controls is kept for last, because a card over
     * a dimmed widget row hides the widgets; one that merely crosses a hairline divider is not,
     * and how much of the card lies on a control also counts against its distance. Within that,
     * the nearest spot with a line that clears everything wins; failing that, the nearest with a
     * line that clears the other lines; and when only covered spots are left, the same again on
     * those — a card over a widget grid beats one that has left its control behind. Last, a card
     * with no line on the nearest spot, paired with its control by colour alone.
     */
    private static Placement shelf(Target t, int page, Box band, float gutter, float gap,
                                   List<Placement> placed, List<Box> hard, List<Box> soft) {
        // Shelves run along x for a control above or under the band, along y for one beside it.
        boolean alongX = t.side == Side.ABOVE || t.side == Side.UNDER || t.side == Side.BELOW;
        float w = t.cardWidth, h = t.cardHeight;
        float length = alongX ? w : h, thickness = alongX ? h : w;
        float lo = (alongX ? band.left : band.top) + gutter;
        float hi = (alongX ? band.right : band.bottom) - gutter - length;
        if (hi < lo - 0.5f || thickness > (alongX ? band.height() : band.width()) + 0.5f) return null;
        List<Box> blocked = new ArrayList<>(hard);
        List<Box> near = new ArrayList<>();
        for (Placement p : placed) if (p.page == page) {
            blocked.add(p.card);
            near.add(p.card);
            near.add(p.target.box);
        }
        float ideal = clamp((alongX ? t.box.cx() : t.box.cy()) - length / 2, lo, hi);
        // The first shelf leans on the control; each step moves a shelf's thickness toward and
        // then across the band, and stops at the band's far edge.
        int inward = t.side == Side.ABOVE || t.side == Side.LEFT ? 1 : -1;
        float first = inward > 0 ? (alongX ? t.box.bottom : t.box.right) + gap
                                 : (alongX ? t.box.top : t.box.left) - gap;
        float limit = inward > 0 ? (alongX ? band.bottom : band.right) : (alongX ? band.top : band.left);
        List<Box> spots = new ArrayList<>();
        List<Float> distance = new ArrayList<>();
        List<Float> cover = new ArrayList<>();
        float edge = first;
        // Only shelves with a free spot count against the budget: a shelf the key cards or a dock
        // fill from end to end is walked past, not spent, or a control boxed in by them would have
        // no card at all.
        for (int shelf = 0, free = 0; free < SHELVES; shelf++, edge += inward * (thickness + gap)) {
            float a = inward > 0 ? edge : edge - thickness, b = a + thickness;
            if (inward > 0 ? b > limit + 0.5f : a < limit - 0.5f) break;
            boolean any = false;
            for (float x : shelfSlots(ideal, lo, hi, length, gap)) {
                Box card = alongX ? new Box(x, a, x + length, b) : new Box(a, x, b, x + length);
                if (card.overlaps(t.box) || hits(card, blocked)) continue;
                any = true;
                float away = Math.abs(x - ideal) + shelf * (thickness + gap);
                // Covering a dimmed control costs up to three shelves, in proportion to how much
                // of the card lies on it: a whole widget row is worth stepping past, a hairline
                // divider is not.
                float onSoft = covered(card, soft);
                away += onSoft * 3 * (thickness + gap);
                spots.add(card);
                distance.add(away);
                cover.add(onSoft);
            }
            if (any) free++;
        }
        Integer[] order = new Integer[spots.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) -> distance.get(i))
            .thenComparingInt(i -> i));
        // Spots mostly on a dimmed control wait until no other spot can take the card at all.
        for (int tier = 0; tier < 2; tier++) {
            for (int mode = 0; mode < 2; mode++) {
                for (int i : order) {
                    if (tier == 0 && cover.get(i) >= 0.5f) continue;
                    Box card = spots.get(i);
                    int column = card.cx() < band.cx() ? 0 : 1;
                    for (List<Segment> path : leaders(t, card, gap, near)) {
                        if (path.isEmpty()) continue;
                        if (clears(t, card, path, placed, page, gap, mode == 0))
                            return new Placement(t, card, path, page, column, -1);
                    }
                }
            }
        }
        for (int i : order) {
            Box card = spots.get(i);
            return new Placement(t, card, Collections.emptyList(), page, card.cx() < band.cx() ? 0 : 1, -1);
        }
        return null;
    }

    /**
     * Where on one shelf a card may sit: the shelf is divided into card-wide places from its left
     * edge, and the card takes the one nearest its control and then the next nearest. Places, not
     * free centring, because two cards centred on controls a hand's breadth apart leave a sliver
     * between them that a third card cannot use, and every card on one edge's shelves is the same
     * size.
     */
    private static List<Float> shelfSlots(float ideal, float lo, float hi, float w, float gap) {
        List<Float> out = new ArrayList<>();
        for (float x = lo; x <= hi + 0.5f; x += w + gap) addLane(out, lo, hi, x);
        addLane(out, lo, hi, hi);
        out.sort(Comparator.comparingDouble((Float v) -> Math.abs(v - ideal)));
        return out;
    }

    /** How much of {@code card} lies on the {@code boxes}, 0 to 1; the boxes rarely overlap each other. */
    private static float covered(Box card, List<Box> boxes) {
        float area = card.width() * card.height();
        if (area <= 0) return 0;
        float sum = 0;
        for (Box b : boxes) {
            float w = Math.min(card.right, b.right) - Math.max(card.left, b.left);
            float h = Math.min(card.bottom, b.bottom) - Math.max(card.top, b.top);
            if (w > 0 && h > 0) sum += w * h;
        }
        return Math.min(1f, sum / area);
    }

    private static boolean hits(Box card, List<Box> blocked) {
        for (Box b : blocked) if (card.overlaps(b)) return true;
        return false;
    }

    /**
     * Every leader worth trying for one card, the plain one first and then the ones that step
     * aside: a lane across the card's own width, met by a lane across the box's, joined by one
     * elbow when the two differ. Lanes that graze a neighbour's edge are offered too, because the
     * only way past a card is along it.
     */
    private static List<List<Segment>> leaders(Target t, Box card, float gap, List<Box> near) {
        List<List<Segment>> out = new ArrayList<>();
        List<Segment> plain = leader(t, card);
        out.add(plain);
        Box b = t.box;
        boolean vertical = b.bottom <= card.top || b.top >= card.bottom;
        boolean beside = b.right <= card.left || b.left >= card.right;
        if (plain.isEmpty() || !(vertical || beside)) return out;
        boolean first = vertical ? b.bottom <= card.top : b.right <= card.left;
        float cardEnd = vertical ? (first ? card.top : card.bottom) : (first ? card.left : card.right);
        float boxEnd = vertical ? (first ? b.bottom : b.top) : (first ? b.right : b.left);
        float cardLo = vertical ? card.left : card.top, cardHi = vertical ? card.right : card.bottom;
        float boxLo = vertical ? b.left : b.top, boxHi = vertical ? b.right : b.bottom;
        float ideal = vertical ? card.cx() : card.cy();
        float idealBox = clamp(ideal, boxLo, boxHi);
        List<Float> cardLanes = lanes(cardLo, cardHi, ideal, gap, near, vertical, 12);
        List<Float> boxLanes = lanes(boxLo, boxHi, idealBox, gap, Collections.emptyList(), vertical, 6);
        List<float[]> pairs = new ArrayList<>();
        for (float u : cardLanes) for (float v : boxLanes)
            if (u != ideal || v != idealBox) pairs.add(new float[] {u, v});
        pairs.sort(Comparator
            .comparingDouble((float[] p) -> Math.abs(p[0] - ideal) + Math.abs(p[1] - idealBox))
            .thenComparingDouble(p -> p[0]).thenComparingDouble(p -> p[1]));
        // Halfway first; then a quarter of the way from either end, so two cards on one shelf
        // whose boxes sit to the side do not both elbow along the same line.
        for (float at : new float[] {0.5f, 0.25f, 0.75f})
            for (float[] p : pairs) out.add(connect(p[0], cardEnd, p[1], boxEnd, vertical, at));
        return out;
    }

    /**
     * From the card's edge to the box's, along one lane each, with an elbow {@code at} of the way
     * from the card to the box.
     */
    private static List<Segment> connect(float lane, float from, float boxLane, float to,
                                         boolean vertical, float at) {
        if (lane == boxLane)
            return vertical ? path(lane, from, lane, to) : path(from, lane, to, lane);
        float mid = from + (to - from) * at;
        return vertical ? path(lane, from, lane, mid, boxLane, mid, boxLane, to)
                        : path(from, lane, mid, lane, mid, boxLane, to, boxLane);
    }

    /** The lanes one leader may run in, nearest the natural one first. */
    private static List<Float> lanes(float lo, float hi, float ideal, float gap, List<Box> near,
                                     boolean vertical, int limit) {
        List<Float> out = new ArrayList<>();
        float step = Math.max(1, gap);
        addLane(out, lo, hi, ideal);
        for (int k = 1; k <= 3; k++) {
            addLane(out, lo, hi, ideal + k * step);
            addLane(out, lo, hi, ideal - k * step);
        }
        addLane(out, lo, hi, lo);
        addLane(out, lo, hi, hi);
        for (Box n : near) {
            addLane(out, lo, hi, vertical ? n.left : n.top);
            addLane(out, lo, hi, vertical ? n.right : n.bottom);
        }
        out.sort(Comparator.comparingDouble((Float v) -> Math.abs(v - ideal)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static void addLane(List<Float> out, float lo, float hi, float v) {
        if (v >= lo && v <= hi && !out.contains(v)) out.add(v);
    }

    /**
     * A leader belongs to one card: it leaves its own card and box at their edges, keeps the
     * layout's gap from every other leader on the page, and — when it can — stays out of the
     * other cards and boxes as well.
     */
    private static boolean clears(Target t, Box card, List<Segment> path, List<Placement> placed,
                                  int page, float gap, boolean strict) {
        for (Segment s : path) if (s.enters(card) || s.enters(t.box)) return false;
        for (Placement p : placed) {
            if (p.page != page) continue;
            for (Segment s : path) {
                for (Segment o : p.lines) if (s.tooClose(o, gap)) return false;
                if (strict && (s.enters(p.card) || s.enters(p.target.box))) return false;
            }
            if (strict) for (Segment o : p.lines)
                if (o.enters(card) || o.enters(t.box)) return false;
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }

    /**
     * The line from a card to its box: straight when the card faces the box, one elbow when it
     * sits beside it, nothing when the two touch. Colour pairs the ends, so a line that passes
     * another card is legible; the layout only has to keep it short.
     */
    static List<Segment> leader(Target t, Box b) {
        Box c = t.box;
        if (c.overlaps(b)) return Collections.emptyList();
        boolean above = c.bottom <= b.top, below = c.top >= b.bottom;
        boolean left = c.right <= b.left, right = c.left >= b.right;
        if (above || below) {
            float y0 = above ? b.top : b.bottom, y1 = above ? c.bottom : c.top;
            float x0 = b.cx(), x1 = Math.max(c.left, Math.min(c.right, x0));
            if (x1 == x0) return path(x0, y0, x0, y1);
            float mid = (y0 + y1) / 2;
            return path(x0, y0, x0, mid, x1, mid, x1, y1);
        }
        if (left || right) {
            float x0 = left ? b.left : b.right, x1 = left ? c.right : c.left;
            float y0 = b.cy(), y1 = Math.max(c.top, Math.min(c.bottom, y0));
            if (y1 == y0) return path(x0, y0, x1, y0);
            float mid = (x0 + x1) / 2;
            return path(x0, y0, mid, y0, mid, y1, x1, y1);
        }
        return Collections.emptyList();
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
            if (c.bottom <= b.top) {
                // A flush row can reach the screen edge. Leave its upper end cap directly,
                // rather than taking a horizontal segment through the row's own interior.
                if (lane >= b.left && lane <= b.right)
                    paths.add(path(lane, b.top, lane, c.cy(), end, c.cy()));
                else paths.add(path(start, b.cy(), lane, b.cy(), lane, c.cy(), end, c.cy()));
            }
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

    private static boolean valid(Placement p, List<Placement> placed, float width, float height,
                                 List<Box> obstacles) {
        if (p.card.overlaps(p.target.box) || p.lines.isEmpty()) return false;
        for (Segment s : p.lines) {
            if (Math.min(s.x1, s.x2) < 0 || Math.max(s.x1, s.x2) > width
                || Math.min(s.y1, s.y2) < 0 || Math.max(s.y1, s.y2) > height
                || s.enters(p.target.box) || s.enters(p.card)) return false;
        }
        for (Box obstacle : obstacles) {
            if (p.card.overlaps(obstacle)) return false;
            for (Segment s : p.lines) if (s.enters(obstacle)) return false;
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
