package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
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
 * <p>The Home row lists Home's Floats, from every Home page. Home takes no window from Overview
 * ({@link Row#takesWindows}): the tiling desktop does not draw Home's Floats yet, so a window moved
 * there would vanish. The last row is the waiting Workspace, empty: a window dropped on it starts a
 * new Workspace.
 */
public final class StackOverviewModel {

    /** One window's card. */
    public static final class Card {
        public final long windowId;
        @NonNull public final WindowKind kind;
        /** The window's own short name; null shows its kind's. */
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

        Card(@NonNull TilingWindow window, boolean front, int group, boolean tabs,
             @NonNull CardFacts.Workspace facts) {
            this.windowId = window.id();
            this.kind = window.kind();
            String name = window.name();
            this.name = name == null || name.trim().isEmpty() ? null : name;
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
        /** The place a tap on the row's title goes to; Home is its first page. */
        @NonNull public final Place place;
        /** A Workspace's name; null for none and for Home. */
        @Nullable public final String name;
        /** A Workspace's layout; null for Home. */
        @Nullable public final WorkspaceLayout layout;
        /** The place in front. */
        public final boolean front;
        /** The waiting Workspace at the end, always empty: a window dropped on it makes a new one. */
        public final boolean waiting;
        /** A window can be moved here from Overview: every Workspace, not Home (see the class). */
        public final boolean takesWindows;
        @NonNull public final List<Card> cards;

        Row(@NonNull Place place, @Nullable String name, @Nullable WorkspaceLayout layout,
            boolean front, boolean waiting, @NonNull List<Card> cards) {
            this.place = place;
            this.name = name;
            this.layout = layout;
            this.front = front;
            this.waiting = waiting;
            this.takesWindows = !place.isHome();
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
        Place current = stack.current();
        long focused = stack.focusedWindowId();
        List<Row> rows = new ArrayList<>();
        if (stack.homePageCount() > 0) {
            List<Card> cards = new ArrayList<>();
            List<TilingWindow> floats = new ArrayList<>();
            for (int page = 0; page < stack.homePageCount(); page++) floats.addAll(stack.homeFloats(page));
            for (TilingWindow window : floats) {
                boolean front = current.isHome() && window.id() == focused;
                cards.add(new Card(window, front, -1, false, floatsOnly(floats, window.id())));
            }
            rows.add(new Row(Place.home(0), null, null, current.isHome(), false, cards));
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
                    cards.add(new Card(window, inFront && window.id() == focused, g, tabs,
                        schematic(workspace, window.id())));
                }
            }
            for (TilingWindow window : workspace.floats()) {
                cards.add(new Card(window, inFront && window.id() == focused, -1, false,
                    schematic(workspace, window.id())));
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

    /** The row of the place in front, or -1. */
    public int frontRow() {
        for (int r = 0; r < mRows.size(); r++) if (mRows.get(r).front) return r;
        return -1;
    }

    // ---- the card's picture ----------------------------------------------------------------------

    /**
     * {@code workspace}'s layout as a card draws it, window {@code lit}'s tile (or Float) lit. Under
     * Scrolling every tile group is a column of equal width, a stacked column's windows one over
     * the other; under Dwindle each tile after the first splits what the one before left, side by
     * side and stacked by turns. A group of tabs is one tile, lit when any of its tabs is. Floats
     * stand over the tiles, each a step down and right of the last. No text: the panes are blocks.
     */
    @NonNull
    public static CardFacts.Workspace schematic(@NonNull TilingWorkspace workspace, long lit) {
        boolean dwindle = workspace.layout() == WorkspaceLayout.DWINDLE;
        List<CardFacts.Node> tiles = new ArrayList<>();
        for (TileGroup group : workspace.groups()) {
            List<TilingWindow> windows = group.windows();
            if (group.mode() == TileGroup.Mode.TABS || windows.size() < 2) {
                tiles.add(pane(contains(windows, lit)));
                continue;
            }
            List<CardFacts.Node> stacked = new ArrayList<>();
            for (TilingWindow window : windows) stacked.add(pane(window.id() == lit));
            tiles.add(even(stacked, false));
        }
        CardFacts.Node root = tiles.isEmpty() ? null : dwindle ? dwindle(tiles, 0, true) : even(tiles, true);
        return new CardFacts.Workspace(root, floating(workspace.floats(), lit));
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

    /** Dwindle's spiral: the tile at {@code from} takes half, the rest split the other half. */
    @NonNull
    private static CardFacts.Node dwindle(@NonNull List<CardFacts.Node> tiles, int from, boolean sideBySide) {
        if (from == tiles.size() - 1) return tiles.get(from);
        return new CardFacts.Split(sideBySide, 1f, 1f, tiles.get(from), dwindle(tiles, from + 1, !sideBySide));
    }
}
