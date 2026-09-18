package com.termux.app.help;

import com.termux.app.help.HelpLeaderRouter.Box;
import com.termux.app.help.HelpLeaderRouter.Segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the one card of "Explore this screen" may sit, and nothing else. Pixels in, a seat out;
 * no Android types, no views, no state.
 *
 * <p>The card is never allowed to cover the control it explains, the exploration toolbar or the
 * system bars: those are hard. The other measured controls are soft — a card may lie over one when
 * there is nowhere else, because only the selected control has to stay visible. A seat nearer the
 * control is preferred over a clear edge of the screen, and when nothing fits at the size asked for
 * the answer is {@link Result#NONE}: the caller reads the topic instead. Text is never shrunk to
 * make a seat.
 */
public final class HelpExplorePlacement {
    private HelpExplorePlacement() {}

    /** Where the seated card ended up, in the order the seats are tried. */
    public enum Seat { BELOW, ABOVE, RIGHT, LEFT, EDGE, NONE }

    /** One question: this card, this control, this much room. */
    public static final class Request {
        /** The room the card may use: the viewport less the system insets. */
        public final Box safe;
        /** The selected control; the card never touches it. */
        public final Box target;
        /** What the card may never touch either: the toolbar, and any key cards already placed. */
        public final List<Box> reserved;
        /** The other measured controls; covered only when there is no clear seat. */
        public final List<Box> others;
        public final float cardWidth, cardHeight;
        /** The clearance kept between the card and the control, and from the edges of the room. */
        public final float gap;

        public Request(Box safe, Box target, List<Box> reserved, List<Box> others,
                       float cardWidth, float cardHeight, float gap) {
            this.safe = safe;
            this.target = target;
            this.reserved = reserved == null ? Collections.<Box>emptyList() : reserved;
            this.others = others == null ? Collections.<Box>emptyList() : others;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
            this.gap = gap;
        }
    }

    /** The seat, or the explicit "it does not fit" the reading panel answers. */
    public static final class Result {
        public final Seat seat;
        /** Null only when nothing fits. */
        public final Box card;
        /** One short line from the card to the control, or null when the card needs none. */
        public final Segment leader;

        public static final Result NONE = new Result(Seat.NONE, null, null);

        Result(Seat seat, Box card, Segment leader) {
            this.seat = seat; this.card = card; this.leader = leader;
        }
        public boolean fits() { return seat != Seat.NONE; }
        @Override public String toString() {
            return seat + (card == null ? "" : "[" + card.left + "," + card.top + ","
                + card.right + "," + card.bottom + "]") + (leader == null ? "" : " with a leader");
        }
    }

    /**
     * The best seat for one card. Adjacent seats first, in reading order — under the control,
     * over it, then beside it — and then the clearest edge of the room; among seats that break no
     * hard rule the one covering least of the other controls wins, and ties keep that order.
     */
    public static Result place(Request r) {
        if (r == null || r.cardWidth <= 0 || r.cardHeight <= 0) return Result.NONE;
        Result best = null;
        float bestCovered = Float.MAX_VALUE;
        for (Result candidate : candidates(r)) {
            if (!legal(candidate.card, r)) continue;
            float covered = covered(candidate.card, r.others);
            if (best == null || covered < bestCovered) {
                best = candidate;
                bestCovered = covered;
                if (covered == 0) break;
            }
        }
        return best == null ? Result.NONE : best;
    }

    /** Every seat worth asking about, in preference order. */
    private static List<Result> candidates(Request r) {
        List<Result> out = new ArrayList<>();
        float w = r.cardWidth, h = r.cardHeight;
        Box t = r.target;
        float x = clamp(t.cx() - w / 2, r.safe.left + r.gap, r.safe.right - r.gap - w);
        float y = clamp(t.cy() - h / 2, r.safe.top + r.gap, r.safe.bottom - r.gap - h);
        out.add(seat(Seat.BELOW, new Box(x, t.bottom + r.gap, x + w, t.bottom + r.gap + h), t));
        out.add(seat(Seat.ABOVE, new Box(x, t.top - r.gap - h, x + w, t.top - r.gap), t));
        out.add(seat(Seat.RIGHT, new Box(t.right + r.gap, y, t.right + r.gap + w, y + h), t));
        out.add(seat(Seat.LEFT, new Box(t.left - r.gap - w, y, t.left - r.gap, y + h), t));
        // The clear edges of the room, farthest from the control first: a card there covers no
        // control at all, and the highlight on the control carries the pairing instead of a line.
        for (Box edge : edges(r)) out.add(new Result(Seat.EDGE, edge, null));
        return out;
    }

    /**
     * A card against each edge of the room, in the band the control does not reach into, ordered
     * by how much room that band has to spare.
     */
    private static List<Box> edges(Request r) {
        float w = r.cardWidth, h = r.cardHeight;
        Box s = r.safe, t = r.target;
        List<Box> out = new ArrayList<>();
        List<Float> room = new ArrayList<>();
        float x = clamp(t.cx() - w / 2, s.left + r.gap, s.right - r.gap - w);
        float y = clamp(t.cy() - h / 2, s.top + r.gap, s.bottom - r.gap - h);
        add(out, room, new Box(x, s.top + r.gap, x + w, s.top + r.gap + h), t.top - s.top);
        add(out, room, new Box(x, s.bottom - r.gap - h, x + w, s.bottom - r.gap), s.bottom - t.bottom);
        add(out, room, new Box(s.left + r.gap, y, s.left + r.gap + w, y + h), t.left - s.left);
        add(out, room, new Box(s.right - r.gap - w, y, s.right - r.gap, y + h), s.right - t.right);
        // Insertion sort on the room each band has: stable, so equal bands keep top-first order.
        for (int i = 1; i < out.size(); i++) {
            for (int j = i; j > 0 && room.get(j) > room.get(j - 1); j--) {
                Collections.swap(out, j, j - 1);
                Collections.swap(room, j, j - 1);
            }
        }
        return out;
    }

    private static void add(List<Box> out, List<Float> room, Box box, float band) {
        out.add(box);
        room.add(band);
    }

    /** An adjacent seat, with the one line that joins it to the control when the two line up. */
    private static Result seat(Seat seat, Box card, Box target) {
        return new Result(seat, card, leader(seat, card, target));
    }

    /**
     * The line from the card to the control: one straight segment across the gap, and nothing at
     * all when the control's centre line misses the card. A leader that has to bend reads as a
     * second thing on the screen, and exploration shows one thing at a time.
     */
    private static Segment leader(Seat seat, Box card, Box target) {
        switch (seat) {
            case BELOW:
            case ABOVE: {
                float x = target.cx();
                if (x <= card.left || x >= card.right) return null;
                return seat == Seat.BELOW ? new Segment(x, target.bottom, x, card.top)
                    : new Segment(x, card.bottom, x, target.top);
            }
            case RIGHT:
            case LEFT: {
                float y = target.cy();
                if (y <= card.top || y >= card.bottom) return null;
                return seat == Seat.RIGHT ? new Segment(target.right, y, card.left, y)
                    : new Segment(card.right, y, target.left, y);
            }
            default:
                return null;
        }
    }

    /** The hard rules: inside the room, off the control, off everything reserved. */
    private static boolean legal(Box card, Request r) {
        if (card == null) return false;
        if (card.left < r.safe.left || card.top < r.safe.top
            || card.right > r.safe.right || card.bottom > r.safe.bottom) return false;
        if (card.overlaps(r.target)) return false;
        for (Box box : r.reserved) if (card.overlaps(box)) return false;
        return true;
    }

    /** How much of the card lies on the other controls, in pixels; they rarely overlap each other. */
    private static float covered(Box card, List<Box> boxes) {
        float sum = 0;
        for (Box box : boxes) {
            float w = Math.min(card.right, box.right) - Math.max(card.left, box.left);
            float h = Math.min(card.bottom, box.bottom) - Math.max(card.top, box.top);
            if (w > 0 && h > 0) sum += w * h;
        }
        return sum;
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), value));
    }
}
