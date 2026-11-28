package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.DwindleTree;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.ScrollingLayoutPolicy;
import com.termux.app.tiling.TileGroup;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.app.tiling.WindowKind;
import com.termux.app.tiling.WorkspaceLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Overview under the Terl switch (#430, spec tiling-desktop-2026-11-27, "Overview"): the stack as
 * rows, one for Home and one for every Workspace, top to bottom, each window a card drawn from its
 * Workspace's layout with its own tile lit (never a picture of it). Pure: it reads a
 * {@link TilingStack}; the view only applies it, and {@link OverviewDropPolicy#stackDrop} reads it
 * to say what a drop does.
 *
 * <p>The Home row lists Home's Floats, from every Home page, and stands for the Home page in
 * front (or last in front): a window dropped on it becomes a Float of that page (#421;
 * {@link TilingStack#moveWindow} floats it). The last row is the waiting Workspace, empty: a window
 * dropped on it starts a new Workspace.
 */
public final class StackOverviewModel {

    /** What runs in each shell now, as its terminal titles it. */
    public interface Titles {
        /** Window {@code windowId}'s shell title; null or blank for none (and for other kinds). */
        @Nullable String titleOf(long windowId);
    }

    /** No titles: every card shows its window's own name. */
    public static final Titles NO_TITLES = id -> null;

    /** One window's card. */
    public static final class Card {
        public final long windowId;
        @NonNull public final WindowKind kind;
        /**
         * What the card is called: a terminal's live shell title, else the window's own short
         * name; null shows its kind's.
         */
        @Nullable public final String name;
        /** A Linux app's id, for its own icon; null for the other kinds. */
        @Nullable public final String appId;
        /** The window is a Float of its place, not a tile. */
        public final boolean floating;
        /** It is the focused window of the place in front: the card Overview was opened over. */
        public final boolean front;
        /**
         * The position of its tile group in its Workspace, the same for every window of a group
         * (tabs, or a stacked column); -1 for a Float.
         */
        public final int group;
        /** Its group shows its windows as tabs. */
        public final boolean tabs;
        /** Its Workspace's layout with its own tile lit. */
        @NonNull public final CardFacts.Workspace facts;

        Card(@NonNull TilingWindow window, @Nullable String title, boolean front, int group,
             boolean tabs, @NonNull CardFacts.Workspace facts) {
            this.windowId = window.id();
            this.kind = window.kind();
            String name = window.kind() == WindowKind.SHELL && !blank(title) ? title.trim() : window.name();
            this.name = blank(name) ? null : name.trim();
            this.appId = window.appId();
            this.floating = window.isFloat();
            this.front = front;
            this.group = group;
            this.tabs = tabs;
            this.facts = facts;
        }
    }

    /** One place: Home or a Workspace. */
    public static final class Row {
        /** The place a tap on the row's title goes to, and a drop moves a window to; Home is its page in front. */
        @NonNull public final Place place;
        /** A Workspace's name; null for none and for Home. */
        @Nullable public final String name;
        /** A Workspace's layout; null for Home. */
        @Nullable public final WorkspaceLayout layout;
        /** The place in front. */
        public final boolean front;
        /** The waiting Workspace at the end, always empty: a window dropped on it makes a new one. */
        public final boolean waiting;
        /** A window can be moved here from Overview: every row, Home's as a Float (see the class). */
        public final boolean takesWindows;
        @NonNull public final List<Card> cards;

        Row(@NonNull Place place, @Nullable String name, @Nullable WorkspaceLayout layout,
            boolean front, boolean waiting, @NonNull List<Card> cards) {
            this.place = place;
            this.name = name;
            this.layout = layout;
            this.front = front;
            this.waiting = waiting;
            this.takesWindows = true;
            this.cards = Collections.unmodifiableList(cards);
        }

        public boolean isHome() {
            return place.isHome();
        }
    }

    /** How far each Float of a card stands from the last, as a fraction of the card. */
    static final float FLOAT_STEP = 0.08f;
    /** Where the first Float of a card stands and how big each is, as fractions of the card. */
    static final float FLOAT_START = 0.18f;
    static final float FLOAT_SIZE = 0.5f;

    @NonNull private final List<Row> mRows;

    private StackOverviewModel(@NonNull List<Row> rows) {
        mRows = Collections.unmodifiableList(rows);
    }

    /** The rows of {@code stack}: Home (when it has Home), then every Workspace, top to bottom. */
    @NonNull
    public static StackOverviewModel of(@NonNull TilingStack stack) {
        return of(stack, NO_TITLES);
    }

    /** {@link #of(TilingStack)}, a terminal's card called by its shell's title from {@code titles}. */
    @NonNull
    public static StackOverviewModel of(@NonNull TilingStack stack, @NonNull Titles titles) {
        Place current = stack.current();
        long focused = stack.focusedWindowId();
        List<Row> rows = new ArrayList<>();
        if (stack.homePageCount() > 0) {
            List<Card> cards = new ArrayList<>();
            List<TilingWindow> floats = new ArrayList<>();
            for (int page = 0; page < stack.homePageCount(); page++) floats.addAll(stack.homeFloats(page));
            for (TilingWindow window : floats) {
                boolean front = current.isHome() && window.id() == focused;
                cards.add(new Card(window, titles.titleOf(window.id()), front, -1, false,
                    floatsOnly(floats, window.id())));
            }
            int page = Math.max(0, Math.min(stack.homePage(), stack.homePageCount() - 1));
            rows.add(new Row(Place.home(page), null, null, current.isHome(), false, cards));
        }
        int count = stack.workspaceCount();
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = stack.workspace(number);
            if (workspace == null) continue;
            Place place = Place.workspace(number);
            boolean inFront = place.equals(current);
            List<Card> cards = new ArrayList<>();
            List<TileGroup> groups = workspace.groups();
            for (int g = 0; g < groups.size(); g++) {
                TileGroup group = groups.get(g);
                boolean tabs = group.mode() == TileGroup.Mode.TABS && group.windows().size() > 1;
                for (TilingWindow window : group.windows()) {
                    cards.add(new Card(window, titles.titleOf(window.id()),
                        inFront && window.id() == focused, g, tabs, schematic(workspace, window.id())));
                }
            }
            for (TilingWindow window : workspace.floats()) {
                cards.add(new Card(window, titles.titleOf(window.id()),
                    inFront && window.id() == focused, -1, false, schematic(workspace, window.id())));
            }
            boolean waiting = number == count && workspace.isEmpty();
            rows.add(new Row(place, workspace.name(), workspace.layout(), inFront, waiting, cards));
        }
        return new StackOverviewModel(rows);
    }

    /** The rows, top to bottom. */
    @NonNull
    public List<Row> rows() {
        return mRows;
    }

    /** The row holding window {@code windowId}, or -1. */
    public int rowOf(long windowId) {
        for (int r = 0; r < mRows.size(); r++) {
            for (Card card : mRows.get(r).cards) if (card.windowId == windowId) return r;
        }
        return -1;
    }

    /** Window {@code windowId}'s card, or null. */
    @Nullable
    public Card card(long windowId) {
        int row = rowOf(windowId);
        if (row < 0) return null;
        for (Card card : mRows.get(row).cards) if (card.windowId == windowId) return card;
        return null;
    }

    /**
     * Row {@code row} of this model as it stands in {@code now}, a later reading of the stack:
     * found by what it holds, never by its number, since Workspaces renumber as one above them
     * empties. Home is Home and the waiting Workspace the waiting one; a Workspace is the row
     * holding most of its windows now. -1 when none of them is left (or {@code row} is none).
     */
    public int rowIn(@NonNull StackOverviewModel now, int row) {
        if (row < 0 || row >= mRows.size()) return -1;
        Row was = mRows.get(row);
        List<Row> rows = now.rows();
        for (int r = 0; r < rows.size(); r++) {
            if (was.isHome() && rows.get(r).isHome()) return r;
            if (was.waiting && rows.get(r).waiting) return r;
        }
        int best = -1;
        int most = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r).isHome()) continue;
            int held = 0;
            for (Card card : was.cards) if (now.rowOf(card.windowId) == r) held++;
            if (held > most) {
                most = held;
                best = r;
            }
        }
        return best;
    }

    /** The row of the place in front, or -1. */
    public int frontRow() {
        for (int r = 0; r < mRows.size(); r++) if (mRows.get(r).front) return r;
        return -1;
    }

    // ---- the card's picture ----------------------------------------------------------------------

    /** The room a Dwindle tree is laid out in for a card: a portrait phone's shape, any unit. */
    static final float ROOM_WIDTH = 1000f;
    static final float ROOM_HEIGHT = 1600f;

    /**
     * {@code workspace}'s layout as a card draws it, window {@code lit}'s tile (or Float) lit. Under
     * Scrolling every tile group is a column of equal width, a stacked column's windows one over
     * the other; under Dwindle the Workspace's own split tree (its layout state, as the desktop
     * keeps it) with its splits where they are. A group of tabs is one tile, lit when any of its
     * tabs is. Floats stand over the tiles, each a step down and right of the last. No text: the
     * panes are blocks. A minimised window is not drawn (its card stays, and a tap on it shows it);
     * a maximised window in focus fills the picture.
     */
    @NonNull
    public static CardFacts.Workspace schematic(@NonNull TilingWorkspace workspace, long lit) {
        List<TilingWindow> floats = new ArrayList<>();
        for (TilingWindow window : workspace.floats()) if (!window.isMinimised()) floats.add(window);
        // A maximised window in focus fills the Workspace, as the desktop shows it (#425).
        TilingWindow focused = workspace.focusedWindow();
        if (focused != null && focused.isMaximised() && !focused.isMinimised() && !focused.isFloat()) {
            return new CardFacts.Workspace(pane(focused.id() == lit), floating(floats, lit));
        }
        if (workspace.layout() == WorkspaceLayout.DWINDLE) {
            return new CardFacts.Workspace(dwindleTree(workspace, lit), floating(floats, lit));
        }
        List<CardFacts.Node> tiles = new ArrayList<>();
        for (TileGroup group : workspace.groups()) {
            List<TilingWindow> windows = group.windows();
            // A minimised window stands alone in its group and the desktop draws no column for it.
            if (group.active().isMinimised()) continue;
            if (group.mode() == TileGroup.Mode.TABS || windows.size() < 2) {
                tiles.add(pane(contains(windows, lit)));
                continue;
            }
            List<CardFacts.Node> stacked = new ArrayList<>();
            for (TilingWindow window : windows) stacked.add(pane(window.id() == lit));
            tiles.add(even(stacked, false));
        }
        CardFacts.Node root = tiles.isEmpty() ? null : even(tiles, true);
        return new CardFacts.Workspace(root, floating(floats, lit));
    }

    /** Only Floats (a Home row's card): {@code floats}, {@code lit}'s lit. */
    @NonNull
    private static CardFacts.Workspace floatsOnly(@NonNull List<TilingWindow> floats, long lit) {
        return new CardFacts.Workspace(null, floating(floats, lit));
    }

    @NonNull
    private static List<CardFacts.Floating> floating(@NonNull List<TilingWindow> floats, long lit) {
        List<CardFacts.Floating> out = new ArrayList<>(floats.size());
        for (int i = 0; i < floats.size(); i++) {
            // A cascade that stays on the card however many there are.
            float at = FLOAT_START + FLOAT_STEP * (i % 4);
            out.add(new CardFacts.Floating(pane(floats.get(i).id() == lit), at, at,
                at + FLOAT_SIZE, at + FLOAT_SIZE));
        }
        return out;
    }

    @NonNull
    private static CardFacts.Pane pane(boolean lit) {
        return new CardFacts.Pane(false, lit, null);
    }

    private static boolean contains(@NonNull List<TilingWindow> windows, long id) {
        for (TilingWindow window : windows) if (window.id() == id) return true;
        return false;
    }

    /** {@code nodes} sharing the room evenly, side by side or stacked. */
    @NonNull
    private static CardFacts.Node even(@NonNull List<CardFacts.Node> nodes, boolean sideBySide) {
        CardFacts.Node node = nodes.get(nodes.size() - 1);
        for (int i = nodes.size() - 2; i >= 0; i--) {
            int after = nodes.size() - 1 - i;
            node = new CardFacts.Split(sideBySide, 1f, after, nodes.get(i), node);
        }
        return node;
    }

    /**
     * A Dwindle Workspace's tree as panes: its saved tree brought to its tiles (a tile the tree has
     * not met yet splits the one before it, as the desktop would) and laid out with no gaps, then
     * read back into splits from the tiles' rooms.
     */
    @Nullable
    private static CardFacts.Node dwindleTree(@NonNull TilingWorkspace workspace, long lit) {
        List<DwindleTree.Tile> tiles = DwindleTree.tilesOf(workspace);
        if (tiles.isEmpty()) return null;
        DwindleTree saved = DwindleTree.decode(workspace.layoutState());
        DwindleTree tree = saved != null ? saved : DwindleTree.empty();
        tree.sync(tiles, ROOM_WIDTH, ROOM_HEIGHT, 0f);
        List<ScrollingLayoutPolicy.Placement> placements =
            tree.layout(ROOM_WIDTH, ROOM_HEIGHT, 0f, 0f).layout.placements;
        List<Room> rooms = new ArrayList<>(placements.size());
        for (ScrollingLayoutPolicy.Placement p : placements) {
            boolean on = false;
            for (DwindleTree.Tile tile : tiles) {
                if (tile.shown != p.windowId) continue;
                for (long id : tile.windows) on |= id == lit;
            }
            rooms.add(new Room(p.left, p.top, p.right, p.bottom, pane(on || p.windowId == lit)));
        }
        return guillotine(rooms, 0f, 0f, ROOM_WIDTH, ROOM_HEIGHT);
    }

    /** One tile's room in a laid-out tree. */
    static final class Room {
        final float left;
        final float top;
        final float right;
        final float bottom;
        @NonNull final CardFacts.Pane pane;

        Room(float left, float top, float right, float bottom, @NonNull CardFacts.Pane pane) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.pane = pane;
        }
    }

    /**
     * The splits that cut {@code rooms}, which tile ({@code l}, {@code t}, {@code r}, {@code b})
     * with no gaps: the first straight cut across the whole room that leaves every room on one side
     * (side by side before stacked), each side read the same way. Rooms no straight cut separates
     * share the room evenly, side by side.
     */
    @NonNull
    static CardFacts.Node guillotine(@NonNull List<Room> rooms, float l, float t, float r, float b) {
        if (rooms.size() == 1) return rooms.get(0).pane;
        for (int pass = 0; pass < 2; pass++) {
            boolean across = pass == 0;
            for (Room room : rooms) {
                float cut = across ? room.right : room.bottom;
                if (cut <= (across ? l : t) + EPSILON || cut >= (across ? r : b) - EPSILON) continue;
                List<Room> first = new ArrayList<>();
                List<Room> second = new ArrayList<>();
                boolean clean = true;
                for (Room other : rooms) {
                    float start = across ? other.left : other.top;
                    float end = across ? other.right : other.bottom;
                    if (end <= cut + EPSILON) first.add(other);
                    else if (start >= cut - EPSILON) second.add(other);
                    else {
                        clean = false;
                        break;
                    }
                }
                if (!clean || first.isEmpty() || second.isEmpty()) continue;
                CardFacts.Node a = across ? guillotine(first, l, t, cut, b) : guillotine(first, l, t, r, cut);
                CardFacts.Node c = across ? guillotine(second, cut, t, r, b) : guillotine(second, l, cut, r, b);
                return new CardFacts.Split(across, cut - (across ? l : t), (across ? r : b) - cut, a, c);
            }
        }
        List<CardFacts.Node> panes = new ArrayList<>(rooms.size());
        for (Room room : rooms) panes.add(room.pane);
        return even(panes, true);
    }

    private static final float EPSILON = 0.5f;

    private static boolean blank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }
}
