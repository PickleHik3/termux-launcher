package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;

import java.util.List;

/**
 * Where a held card of Overview may land and what a drop does (spec overview-2026-11-24, "Cards").
 * Pure: it reads the wall as a list of {@link Stop}s in wall order and a <em>gap</em>, a place
 * between two cards (gap 0 before the first, gap n after the last), which {@link #gridGap} finds
 * from the finger on the grid the cards wrap in.
 *
 * <p>On today's wall a Home page goes to any gap that is not inside a Workspace's windows, and a
 * Workspace window goes to any gap among its own row's windows; the other cards stand where they
 * are. A gap beside the held card, or a card with nowhere to go, lands nowhere: a drop there
 * changes nothing. Free ordering of every kind arrives with the three-kind wall.
 *
 * <p>Under the Terl switch Overview shows the stack ({@link StackOverviewModel}) and a held window
 * card lands by {@link #stackDrop}: onto another card it joins it as a tab, onto another row it
 * moves there.
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

    /**
     * The gap the finger at ({@code x}, {@code y}) points at in a grid of {@code count} cells that
     * wraps at {@code columns}, cells {@code cellWidth} x {@code cellHeight} with {@code gap}
     * between them across and down, its first cell's corner at (0, 0): the finger's grid row (the
     * nearest), then how many of that row's cells have their middle before it. The gap at the end
     * of a row and the start of the next is one gap.
     */
    public static int gridGap(float x, float y, int count, int columns, float cellWidth,
                              float cellHeight, float gap) {
        if (count <= 0 || columns <= 0) return 0;
        int row = gridRow(y, count, columns, cellHeight, gap);
        int first = row * columns;
        int inRow = Math.min(columns, count - first);
        float pitch = cellWidth + gap;
        int before = 0;
        for (int i = 0; i < inRow; i++) {
            if (i * pitch + cellWidth / 2f < x) before = i + 1;
        }
        return first + before;
    }

    /** The grid row nearest {@code y}, clamped to the rows {@code count} cells take. */
    public static int gridRow(float y, int count, int columns, float cellHeight, float gap) {
        if (count <= 0 || columns <= 0) return 0;
        int rows = (count + columns - 1) / columns;
        float pitch = cellHeight + gap;
        int row = pitch <= 0f ? 0 : (int) Math.floor((y + gap / 2f) / pitch);
        return Math.max(0, Math.min(rows - 1, row));
    }

    /**
     * Whether the landing mark for {@code landing} stands after the cell before it rather than
     * before the cell at it: at the end of everything, or at a row's start when the finger is
     * still on the row above.
     */
    public static boolean markAfter(int landing, int count, int columns, int fingerRow) {
        if (landing <= 0) return false;
        if (landing >= count) return true;
        return columns > 0 && landing % columns == 0 && fingerRow == (landing - 1) / columns;
    }

    // ---- The stack's Overview (#430) -------------------------------------------------------------

    /** What letting go of a held window card on the stack's Overview does. */
    public static final class StackDrop {
        public enum Type {
            /** Nothing changes. */
            NONE,
            /** {@link #windowId} moves to {@link #place}, last in it. */
            MOVE,
            /** {@link #windowId} joins {@link #targetId}'s tile as a tab. */
            TAB
        }

        @NonNull public final Type type;
        public final long windowId;
        /** Where a move goes; null for the other types. */
        @Nullable public final Place place;
        /** The window a tab joins; {@link TilingStack#NO_WINDOW} for the other types. */
        public final long targetId;

        private StackDrop(@NonNull Type type, long windowId, @Nullable Place place, long targetId) {
            this.type = type;
            this.windowId = windowId;
            this.place = place;
            this.targetId = targetId;
        }

        static final StackDrop NOTHING = new StackDrop(Type.NONE, TilingStack.NO_WINDOW, null,
            TilingStack.NO_WINDOW);
    }

    /**
     * What dropping window {@code held} does with the finger over row {@code row} (-1 for none) and
     * over the card of window {@code onCard} ({@link TilingStack#NO_WINDOW} for none):
     * <ul>
     *   <li>onto another tiled window's card: it joins that window's tile as a tab, unless they are
     *   tabs of one tile already;</li>
     *   <li>onto a Float's card: never a tab (a Float has no tile); it moves to that Float's place,
     *   and in the same place nothing happens;</li>
     *   <li>onto a row's free room: it moves to that row's place, last, unless it is there already.</li>
     * </ul>
     * On Home's row, or onto a Home Float's card, it becomes a Float of the Home page in front.
     */
    @NonNull
    public static StackDrop stackDrop(@NonNull StackOverviewModel model, long held, int row, long onCard) {
        int from = model.rowOf(held);
        if (from < 0) return StackDrop.NOTHING;
        StackOverviewModel.Card target = onCard == held ? null : model.card(onCard);
        if (target == null) return moveTo(model, held, from, row);
        int targetRow = model.rowOf(onCard);
        if (target.floating) return moveTo(model, held, from, targetRow);
        if (!model.rows().get(targetRow).takesWindows) return StackDrop.NOTHING;
        StackOverviewModel.Card heldCard = model.card(held);
        boolean sameTabs = targetRow == from && heldCard != null && heldCard.group >= 0
            && heldCard.group == target.group && target.tabs;
        return sameTabs ? StackDrop.NOTHING : new StackDrop(StackDrop.Type.TAB, held, null, onCard);
    }

    /** {@code held}, standing in row {@code from}, moved to row {@code to}'s place; nothing for its own row. */
    @NonNull
    private static StackDrop moveTo(@NonNull StackOverviewModel model, long held, int from, int to) {
        if (to < 0 || to >= model.rows().size() || to == from) return StackDrop.NOTHING;
        StackOverviewModel.Row row = model.rows().get(to);
        if (!row.takesWindows) return StackDrop.NOTHING;
        return new StackDrop(StackDrop.Type.MOVE, held, row.place, TilingStack.NO_WINDOW);
    }

    /**
     * How far a scroller moves this frame while a held card's finger stands at {@code finger}
     * along it, its visible span {@code start} to {@code end}: toward the start (negative) or the
     * end within {@code band} of that edge, faster the deeper in, up to {@code maxStep} at the edge
     * and past it; 0 elsewhere.
     */
    public static float edgeScrollPx(float finger, float start, float end, float band, float maxStep) {
        if (band <= 0f || end - start <= 2f * band) {
            band = Math.max(0f, (end - start) / 4f);
            if (band <= 0f) return 0f;
        }
        if (finger < start + band) return -maxStep * Math.min(1f, (start + band - finger) / band);
        if (finger > end - band) return maxStep * Math.min(1f, (finger - (end - band)) / band);
        return 0f;
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
