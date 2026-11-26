package com.termux.app.overview;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

import java.util.List;

/**
 * Where a held card of Overview may land and what a drop does (spec overview-2026-11-24, "Cards").
 * Pure: it reads the strip as a list of {@link Stop}s and a <em>gap</em>, a place between two
 * cards (gap 0 before the first, gap n after the last), which the view finds from the finger.
 *
 * <p>On today's wall a Home page goes to any gap that is not inside a Workspace's windows, and a
 * Workspace window goes to any gap among its own row's windows; the other cards stand where they
 * are. A gap beside the held card, or a card with nowhere to go, lands nowhere: a drop there
 * changes nothing. Free ordering of every kind arrives with the three-kind wall.
 */
public final class OverviewDropPolicy {

    private OverviewDropPolicy() { }

    /** No landing place. */
    public static final int NONE = -1;

    /** One card of the strip, as the policy needs it. */
    public static final class Stop {
        @NonNull public final PageKind kind;
        /** The wall page the card stands for. */
        public final int pageId;
        /** A window card's place in the Workspace's row; -1 for every other card. */
        public final int window;

        public Stop(@NonNull PageKind kind, int pageId, int window) {
            this.kind = kind;
            this.pageId = pageId;
            this.window = window;
        }

        boolean isWindow() {
            return window >= 0 && (kind == PageKind.TERMINAL || kind == PageKind.LINUX);
        }
    }

    /** What a drop does. */
    public enum Type {
        /** Nothing changes. */
        NONE,
        /** A window moves within its row: {@link Drop#from} to {@link Drop#to}, final places. */
        WINDOW,
        /** A wall page moves to stand just before or after {@link Drop#refPageId}. */
        PAGE
    }

    /** The outcome of a drop. */
    public static final class Drop {
        @NonNull public final Type type;
        public final int from;
        public final int to;
        public final int pageId;
        public final int refPageId;
        /** The page lands after {@link #refPageId}, not before it. */
        public final boolean after;

        Drop(@NonNull Type type, int from, int to, int pageId, int refPageId, boolean after) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.pageId = pageId;
            this.refPageId = refPageId;
            this.after = after;
        }

        static final Drop NOTHING = new Drop(Type.NONE, -1, -1, -1, -1, false);
    }

    /** Whether the card at {@code from} can be dragged at all. */
    public static boolean canDrag(@NonNull List<Stop> strip, int from) {
        if (from < 0 || from >= strip.size()) return false;
        Stop held = strip.get(from);
        return held.kind == PageKind.HOME || held.isWindow();
    }

    /**
     * The gap a card held from {@code from} would land in when the finger is at {@code gap}, or
     * {@link #NONE}: a gap inside a Workspace's windows moves to the nearer end for a Home page,
     * and is the only kind of gap a window may take.
     */
    public static int landing(@NonNull List<Stop> strip, int from, int gap) {
        int n = strip.size();
        if (!canDrag(strip, from) || gap < 0 || gap > n) return NONE;
        int landing = gap;
        if (strip.get(from).isWindow()) {
            if (gap < runStart(strip, from) || gap > runEnd(strip, from)) return NONE;
        } else if (gap > 0 && gap < n && sameRow(strip, gap - 1, gap)) {
            // Strictly inside a row of windows is not a place for a page: the nearer end.
            int start = runStart(strip, gap);
            int end = runEnd(strip, gap);
            landing = gap - start <= end - gap ? start : end;
        }
        return landing == from || landing == from + 1 ? NONE : landing;
    }

    /** What dropping the card held from {@code from} at {@code gap} does. */
    @NonNull
    public static Drop drop(@NonNull List<Stop> strip, int from, int gap) {
        int landing = landing(strip, from, gap);
        if (landing == NONE) return Drop.NOTHING;
        Stop held = strip.get(from);
        int placed = landing > from ? landing - 1 : landing;
        if (held.isWindow()) {
            int start = runStart(strip, from);
            return new Drop(Type.WINDOW, from - start, placed - start, held.pageId, -1, false);
        }
        int n = strip.size();
        boolean after = landing >= n;
        int ref = strip.get(after ? n - 1 : landing).pageId;
        return new Drop(Type.PAGE, from, placed, held.pageId, ref, after);
    }

    /**
     * The gap a session held from {@code from} lands in within its row of {@code count} sessions
     * when the finger is at {@code gap}, or {@link #NONE}: any gap but the two beside itself.
     */
    public static int sessionLanding(int count, int from, int gap) {
        if (from < 0 || from >= count || gap < 0 || gap > count) return NONE;
        return gap == from || gap == from + 1 ? NONE : gap;
    }

    /**
     * Where a session held from {@code from} stands in its row once dropped at {@code gap}: its
     * final place, or {@link #NONE} when the drop changes nothing.
     */
    public static int sessionDrop(int count, int from, int gap) {
        int landing = sessionLanding(count, from, gap);
        if (landing == NONE) return NONE;
        return landing > from ? landing - 1 : landing;
    }

    private static boolean sameRow(@NonNull List<Stop> strip, int a, int b) {
        return strip.get(a).isWindow() && strip.get(b).isWindow()
            && strip.get(a).pageId == strip.get(b).pageId;
    }

    /** The first card of the row of windows that card {@code at} is in. */
    private static int runStart(@NonNull List<Stop> strip, int at) {
        int i = at;
        while (i > 0 && sameRow(strip, i - 1, at)) i--;
        return i;
    }

    /** One past the last card of the row of windows that card {@code at} is in. */
    private static int runEnd(@NonNull List<Stop> strip, int at) {
        int i = at + 1;
        while (i < strip.size() && sameRow(strip, i, at)) i++;
        return i;
    }
}
