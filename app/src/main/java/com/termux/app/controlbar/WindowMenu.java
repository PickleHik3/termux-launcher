package com.termux.app.controlbar;

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
 * A held mark's window menu (#425), pure: which rows a window's menu holds, from the stack as it
 * stands. The rows are Float or Tile (and Pin or Unpin for a Float), Full screen, then Minimise,
 * Maximise and Scale where they apply (a Linux app's window minimises and scales, a Linux app's
 * and the Display's maximise; a terminal has none of them), then Move to Workspace, Tab with and
 * Stack with (each a list of where it can go), Take out of its tabs or stack, and Close last.
 *
 * <p>A full-screen window's menu offers Leave full screen in Full screen's place. A minimised window's menu offers Restore in Minimise's place and nothing that lays it out
 * (Float, Full screen, Maximise, Scale, Tab with, Stack with) until it is shown again.
 */
public final class WindowMenu {

    /** What a row does to the window the menu was opened for. */
    public enum Action {
        FLOAT, TILE, PIN, UNPIN, FULL_SCREEN, LEAVE_FULL_SCREEN, MINIMISE, RESTORE, MAXIMISE, UNMAXIMISE, SCALE,
        /** Opens the list of places to move to; its rows are {@link #MOVE}. */
        MOVE_LIST,
        /** Opens the list of tiles to join as a tab; its rows are {@link #TAB}. */
        TAB_LIST,
        /** Opens the list of columns to stack into; its rows are {@link #STACK}. */
        STACK_LIST,
        MOVE, TAB, STACK,
        /** Takes the window out of its tabs or its stack, as a tile of its own. */
        SEPARATE,
        CLOSE;

        /** Whether the row opens a list of rows instead of acting. */
        public boolean opensList() {
            return this == MOVE_LIST || this == TAB_LIST || this == STACK_LIST;
        }
    }

    /** One row: what it does, and for a list's row where to (a place) or with whom (a window). */
    public static final class Row {
        @NonNull public final Action action;
        /** The window a Tab or Stack row joins; {@link TilingStack#NO_WINDOW} for the others. */
        public final long window;
        /** The window's kind, for its name when it has none; null for the others. */
        @Nullable public final WindowKind kind;
        /** The window's or the Workspace's own name; null for none. */
        @Nullable public final String name;
        /** The place a Move row goes to; null for the others. */
        @Nullable public final Place place;
        /** On a Move row: the waiting Workspace, a new one at the end. */
        public final boolean newWorkspace;
        /** Under Take out: the window shares a tile as a tab (else it is stacked). */
        public final boolean tabs;

        private Row(@NonNull Action action, long window, @Nullable WindowKind kind, @Nullable String name,
                    @Nullable Place place, boolean newWorkspace, boolean tabs) {
            this.action = action;
            this.window = window;
            this.kind = kind;
            this.name = name;
            this.place = place;
            this.newWorkspace = newWorkspace;
            this.tabs = tabs;
        }

        @NonNull
        static Row of(@NonNull Action action) {
            return new Row(action, TilingStack.NO_WINDOW, null, null, null, false, false);
        }

        @NonNull
        static Row separate(boolean tabs) {
            return new Row(Action.SEPARATE, TilingStack.NO_WINDOW, null, null, null, false, tabs);
        }

        @NonNull
        static Row join(@NonNull Action action, @NonNull TilingWindow with) {
            return new Row(action, with.id(), with.kind(), with.name(), null, false, false);
        }

        @NonNull
        static Row move(@NonNull Place place, @Nullable String name, boolean newWorkspace) {
            return new Row(Action.MOVE, TilingStack.NO_WINDOW, null, name, place, newWorkspace, false);
        }

        @Override
        public String toString() {
            return action + (place != null ? " " + place : "") + (window != TilingStack.NO_WINDOW ? " " + window : "");
        }
    }

    /** The window a menu is for, and every row it can hold, read once at the hold. */
    public static final class Subject {
        public final long window;
        @NonNull public final WindowKind kind;
        @NonNull public final List<Row> rows;
        @NonNull public final List<Row> moveRows;
        @NonNull public final List<Row> tabRows;
        @NonNull public final List<Row> stackRows;

        Subject(long window, @NonNull WindowKind kind, @NonNull List<Row> rows, @NonNull List<Row> moveRows,
                @NonNull List<Row> tabRows, @NonNull List<Row> stackRows) {
            this.window = window;
            this.kind = kind;
            this.rows = Collections.unmodifiableList(rows);
            this.moveRows = Collections.unmodifiableList(moveRows);
            this.tabRows = Collections.unmodifiableList(tabRows);
            this.stackRows = Collections.unmodifiableList(stackRows);
        }

        /** The rows {@code list} (a row that {@link Action#opensList}) opens. */
        @NonNull
        public List<Row> listOf(@NonNull Action list) {
            switch (list) {
                case MOVE_LIST: return moveRows;
                case TAB_LIST: return tabRows;
                case STACK_LIST: return stackRows;
                default: return Collections.emptyList();
            }
        }

        /** Every row that acts, the lists' rows in their list's place: what a screen reader offers. */
        @NonNull
        public List<Row> flat() {
            List<Row> flat = new ArrayList<>();
            for (Row row : rows) {
                if (row.action.opensList()) flat.addAll(listOf(row.action));
                else flat.add(row);
            }
            return flat;
        }
    }

    private WindowMenu() {}

    /**
     * The menu of window {@code windowId} in {@code stack}; {@code scales} whether its content takes
     * a scale of its own. Null when the stack has no such window.
     */
    @Nullable
    public static Subject subjectOf(@NonNull TilingStack stack, long windowId, boolean scales) {
        TilingWindow window = stack.window(windowId);
        Place place = stack.placeOf(windowId);
        if (window == null || place == null) return null;
        TilingWorkspace workspace = place.isHome() ? null : stack.workspace(place.workspaceNumber());
        WindowKind kind = window.kind();
        boolean minimised = window.isMinimised();
        boolean floating = window.isFloat();

        List<Row> moveRows = moveRows(stack, place, workspace);
        List<Row> tabRows = new ArrayList<>();
        List<Row> stackRows = new ArrayList<>();
        int own = -1;
        TileGroup group = null;
        if (workspace != null) {
            List<TileGroup> groups = workspace.groups();
            for (int i = 0; i < groups.size(); i++) if (groups.get(i).windows().contains(window)) own = i;
            group = own < 0 ? null : groups.get(own);
            if (!minimised) {
                for (int i = 0; i < groups.size(); i++) {
                    if (i == own) continue;
                    TilingWindow shown = groups.get(i).active();
                    if (!shown.isMinimised()) tabRows.add(Row.join(Action.TAB, shown));
                }
                // A column beside it with room for one more, under Scrolling; a Float stacks into any.
                if (workspace.layout() == WorkspaceLayout.SCROLLING) {
                    for (int i = 0; i < groups.size(); i++) {
                        if (i == own || (own >= 0 && Math.abs(i - own) != 1)) continue;
                        TileGroup beside = groups.get(i);
                        if (beside.mode() != TileGroup.Mode.STACK || beside.windows().size() >= TilingStack.MAX_STACKED
                            || beside.active().isMinimised()) continue;
                        stackRows.add(Row.join(Action.STACK, beside.windows().get(beside.windows().size() - 1)));
                    }
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        if (workspace != null && !minimised) rows.add(Row.of(floating ? Action.TILE : Action.FLOAT));
        if (floating && !minimised) rows.add(Row.of(window.isPinned() ? Action.UNPIN : Action.PIN));
        if (!minimised && workspace != null)
            rows.add(Row.of(window.isFullScreen() ? Action.LEAVE_FULL_SCREEN : Action.FULL_SCREEN));
        if (kind == WindowKind.LINUX_APP && workspace != null)
            rows.add(Row.of(minimised ? Action.RESTORE : Action.MINIMISE));
        if ((kind == WindowKind.LINUX_APP || kind == WindowKind.DISPLAY) && workspace != null && !floating && !minimised)
            rows.add(Row.of(window.isMaximised() ? Action.UNMAXIMISE : Action.MAXIMISE));
        if (kind == WindowKind.LINUX_APP && scales && !minimised) rows.add(Row.of(Action.SCALE));
        if (!moveRows.isEmpty()) rows.add(Row.of(Action.MOVE_LIST));
        if (!tabRows.isEmpty()) rows.add(Row.of(Action.TAB_LIST));
        if (!stackRows.isEmpty()) rows.add(Row.of(Action.STACK_LIST));
        if (group != null && group.windows().size() > 1)
            rows.add(Row.separate(group.mode() == TileGroup.Mode.TABS));
        rows.add(Row.of(Action.CLOSE));
        return new Subject(windowId, kind, rows, moveRows, tabRows, stackRows);
    }

    /**
     * Where the window can move: every other Workspace, the waiting one as a new Workspace (not
     * for a window alone in its Workspace, which would only renumber it), and Home's page in front
     * when there is Home and the window is not on it.
     */
    @NonNull
    private static List<Row> moveRows(@NonNull TilingStack stack, @NonNull Place place,
                                      @Nullable TilingWorkspace own) {
        List<Row> rows = new ArrayList<>();
        int count = stack.workspaceCount();
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = stack.workspace(number);
            if (workspace == null || workspace == own) continue;
            boolean waiting = number == count && workspace.isEmpty();
            if (waiting && own != null && own.windows().size() <= 1) continue;
            rows.add(Row.move(Place.workspace(number), waiting ? null : workspace.name(), waiting));
        }
        if (stack.homePageCount() > 0 && !place.isHome())
            rows.add(Row.move(Place.home(stack.homePage()), null, false));
        return rows;
    }
}
