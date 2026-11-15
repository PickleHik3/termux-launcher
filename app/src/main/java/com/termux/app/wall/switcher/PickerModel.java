package com.termux.app.wall.switcher;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the picker shows and where each cell goes: four page-kind columns (Home, Terminal,
 * Display, Assistant, always in that order), each with its page-kind glyph at the bottom and, above
 * it, that page kind's windows. Pure: the activity fills an {@link Input} from the wall and the
 * windows, the view draws the answer, and the geometry of where a cell stands is
 * {@link PickerLayout}'s.
 *
 * <p>Home and Assistant carry no window column. Terminal's is its windows, labelled as the window
 * bar labels them. Display's is its running apps, or one "Start" cell while no display
 * runs. A column that holds more than the picker is tall enough for gives its top cell to "+N",
 * which opens that page kind's full list.
 */
public final class PickerModel {

    /** The four page kinds, in the order the row shows them. */
    public enum Kind { HOME, TERMINAL, DISPLAY, ASSISTANT }

    public enum CellKind {
        /** A page kind's glyph, the bottom of its column. */
        GLYPH,
        /** One terminal window or one display app. */
        WINDOW,
        /** The Display column's only cell while no display runs. */
        START_DISPLAY,
        /** The top cell of a column with more windows than fit; its target opens the full list. */
        OVERFLOW
    }

    /** What releasing on a cell does. */
    public enum Action {
        /** Close the picker: the cell is where the user already is. */
        CLOSE,
        /** Go to the page {@link Target#pageId}. */
        GO_PAGE,
        /** Go to the Home page the wall has no better answer for (no Home page is known). */
        GO_HOME,
        /** Go to the Terminal page at window {@link Target#index}; -1 is its current window. */
        GO_TERMINAL,
        /** Go to the Display page {@link Target#pageId} and raise the app {@link Target#index}. */
        GO_DISPLAY_APP,
        /** Start the display, the user having asked for it. */
        START_DISPLAY,
        /** Add the Assistant page (the entry's own path, with its Undo). */
        ADD_ASSISTANT,
        /** Add a Display page that shows its Start screen without starting the display. */
        ADD_DISPLAY,
        /** Open the session browser. */
        OPEN_TERMINAL_LIST,
        /** Open the list of the apps on the display. */
        OPEN_DISPLAY_LIST,
        /** Open the page overview, which lists every Home page. */
        OPEN_PAGE_OVERVIEW
    }

    public static final class Target {
        @NonNull public final Action action;
        public final int pageId;
        public final int index;

        Target(@NonNull Action action, int pageId, int index) {
            this.action = action;
            this.pageId = pageId;
            this.index = index;
        }

        @Override public String toString() {
            return action + "(page=" + pageId + ",index=" + index + ")";
        }
    }

    public static final class Cell {
        @NonNull public final CellKind kind;
        @NonNull public final Kind pageKind;
        /** The name shown above the finger and spoken by TalkBack. */
        @NonNull public final String label;
        /** The page kind's glyph lit, or the window underlined: where the user is. */
        public final boolean current;
        /** A page kind the wall has no page for: drawn dimmed, and releasing on it adds the page. */
        public final boolean dimmed;
        /** The number {@code +N} shows; 0 for any other cell. */
        public final int overflowCount;
        @NonNull public final Target target;

        Cell(@NonNull CellKind kind, @NonNull Kind pageKind, @NonNull String label,
             boolean current, boolean dimmed, int overflowCount, @NonNull Target target) {
            this.kind = kind;
            this.pageKind = pageKind;
            this.label = label;
            this.current = current;
            this.dimmed = dimmed;
            this.overflowCount = overflowCount;
            this.target = target;
        }

        @Override public String toString() {
            return kind + ":" + label + (current ? "*" : "") + (dimmed ? "~" : "") + "->" + target;
        }
    }

    public static final class Column {
        @NonNull public final Kind kind;
        @NonNull public final Cell glyph;
        /** Above the glyph, nearest first: the last one is the column's top. */
        @NonNull public final List<Cell> cells;

        Column(@NonNull Kind kind, @NonNull Cell glyph, @NonNull List<Cell> cells) {
            this.kind = kind;
            this.glyph = glyph;
            this.cells = Collections.unmodifiableList(cells);
        }
    }

    /** What the picker is built from. Ids are wall page ids; -1 is none. */
    public static final class Input {
        /** The wall's Home pages, in order. */
        @NonNull public List<Integer> homePageIds = Collections.emptyList();
        /** The Home pages' names, in step with {@link #homePageIds}; a missing one is numbered. */
        @NonNull public List<String> homePageLabels = Collections.emptyList();
        /** The page the app starts on, whatever kind it is; -1 when there is none. */
        public int defaultStartPageId = -1;
        public int terminalPageId = -1;
        /** The Display page the Display glyph goes to (the last used); -1 when the wall has none. */
        public int displayPageId = -1;
        public int assistantPageId = -1;
        /** Whether the display can be used at all on this install. */
        public boolean displayAvailable = true;
        /** The page kind the wall is on, and its page. */
        @NonNull public Kind currentKind = Kind.HOME;
        public int currentPageId = -1;

        /** The Terminal page's windows, by the window bar's labels. */
        @NonNull public List<String> terminalWindowLabels = Collections.emptyList();
        public int currentTerminalWindow = -1;

        public boolean displayRunning;
        /** The display's apps: their names and their window ids, in step. */
        @NonNull public List<String> displayAppLabels = Collections.emptyList();
        @NonNull public List<Integer> displayAppIds = Collections.emptyList();
        /** The window id of the app in front, or -1. */
        public int currentDisplayApp = -1;

        /** The most cells a column may hold above its glyph, "+N" included. */
        public int capacity = Integer.MAX_VALUE;

        /** Page-kind names in {@link Kind} order, and the "Start display" cell's text. */
        @NonNull public String[] kindNames =
            {"Home", "Terminal", "Display", "Assistant"};
        @NonNull public String startDisplayLabel = "Start";
    }

    @NonNull private final List<Column> mColumns;

    private PickerModel(@NonNull List<Column> columns) {
        mColumns = Collections.unmodifiableList(columns);
    }

    /** The columns in {@link Kind} order. */
    @NonNull public List<Column> columns() {
        return mColumns;
    }

    @NonNull public Column column(@NonNull Kind kind) {
        return mColumns.get(kind.ordinal());
    }

    /** The most cells any column holds above its glyph; what the picker's height has to fit. */
    public int tallestColumn() {
        int tallest = 0;
        for (Column column : mColumns) tallest = Math.max(tallest, column.cells.size());
        return tallest;
    }

    /**
     * The Home page the Home glyph goes to: the page the app starts on if it is a Home page, else
     * the only Home page, else the first one; -1 when the wall has no Home page.
     */
    public static int homeTarget(@NonNull List<Integer> homePageIds, int defaultStartPageId) {
        if (homePageIds.isEmpty()) return -1;
        if (homePageIds.contains(defaultStartPageId)) return defaultStartPageId;
        return homePageIds.get(0);
    }

    /**
     * A window's name for the picker: the window bar's label without its icon code points, which
     * need the symbol font the picker's text is not drawn in. Whatever is left, trimmed; the label
     * itself when nothing is.
     */
    @NonNull
    public static String plainLabel(@NonNull String label) {
        StringBuilder out = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); ) {
            int cp = label.codePointAt(i);
            i += Character.charCount(cp);
            boolean icon = (cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000
                || (cp >= 0xFE00 && cp <= 0xFE0F) || cp == 0x200D;
            if (!icon) out.appendCodePoint(cp);
        }
        String plain = out.toString().trim();
        return plain.isEmpty() ? label : plain;
    }

    @NonNull
    public static PickerModel build(@NonNull Input in) {
        List<Column> columns = new ArrayList<>(4);
        columns.add(homeColumn(in));
        columns.add(terminalColumn(in));
        columns.add(displayColumn(in));
        columns.add(assistantColumn(in));
        return new PickerModel(columns);
    }

    // ---- columns --------------------------------------------------------------------------

    private static Column homeColumn(Input in) {
        int home = homeTarget(in.homePageIds, in.defaultStartPageId);
        Target target = home < 0
            ? new Target(Action.GO_HOME, -1, -1) : pageTarget(in, home);
        Cell glyph = glyph(in, Kind.HOME, false, target);
        int count = in.homePageIds.size();
        if (count <= 1) return new Column(Kind.HOME, glyph, new ArrayList<>());
        List<Cell> pages = new ArrayList<>(count);
        int currentIndex = -1;
        for (int i = 0; i < count; i++) {
            int id = in.homePageIds.get(i);
            boolean current = in.currentKind == Kind.HOME && id == in.currentPageId;
            if (current) currentIndex = i;
            String name = i < in.homePageLabels.size() ? in.homePageLabels.get(i) : null;
            String label = name == null || name.isEmpty()
                ? in.kindNames[Kind.HOME.ordinal()] + " " + (i + 1) : name;
            pages.add(new Cell(CellKind.WINDOW, Kind.HOME, label, current, false, 0,
                current ? new Target(Action.CLOSE, id, -1)
                    : new Target(Action.GO_PAGE, id, -1)));
        }
        return new Column(Kind.HOME, glyph,
            fit(pages, currentIndex, in.capacity, Kind.HOME, in,
                new Target(Action.OPEN_PAGE_OVERVIEW, -1, -1)));
    }

    private static Column terminalColumn(Input in) {
        Target target = in.currentKind == Kind.TERMINAL
            || (in.terminalPageId >= 0 && in.terminalPageId == in.currentPageId)
            ? new Target(Action.CLOSE, in.terminalPageId, -1)
            : new Target(Action.GO_TERMINAL, in.terminalPageId, -1);
        Cell glyph = glyph(in, Kind.TERMINAL, false, target);
        int count = in.terminalWindowLabels.size();
        List<Cell> windows = new ArrayList<>(count);
        String[] names = new String[count];
        for (int i = 0; i < count; i++) names[i] = plainLabel(in.terminalWindowLabels.get(i));
        for (int i = 0; i < count; i++) {
            boolean current = in.currentKind == Kind.TERMINAL && i == in.currentTerminalWindow;
            boolean twin = false;
            for (int j = 0; j < count && !twin; j++) twin = j != i && names[j].equals(names[i]);
            windows.add(new Cell(CellKind.WINDOW, Kind.TERMINAL,
                // Windows sharing a name carry their number, as the window bar counts them.
                twin ? names[i] + " " + (i + 1) : names[i],
                current, false, 0,
                current ? new Target(Action.CLOSE, in.terminalPageId, i)
                    : new Target(Action.GO_TERMINAL, in.terminalPageId, i)));
        }
        int currentIndex = in.currentKind == Kind.TERMINAL ? in.currentTerminalWindow : -1;
        return new Column(Kind.TERMINAL, glyph,
            fit(windows, currentIndex, in.capacity, Kind.TERMINAL, in,
                new Target(Action.OPEN_TERMINAL_LIST, in.terminalPageId, -1)));
    }

    private static Column displayColumn(Input in) {
        boolean missing = in.displayPageId < 0;
        Target target;
        if (!in.displayAvailable) target = new Target(Action.CLOSE, -1, -1);
        else if (missing) target = new Target(Action.ADD_DISPLAY, -1, -1);
        else target = pageTarget(in, in.displayPageId);
        Cell glyph = glyph(in, Kind.DISPLAY, missing || !in.displayAvailable, target);
        List<Cell> cells = new ArrayList<>();
        if (!in.displayAvailable) {
            return new Column(Kind.DISPLAY, glyph, cells);
        }
        if (!in.displayRunning) {
            if (in.capacity >= 1) {
                cells.add(new Cell(CellKind.START_DISPLAY, Kind.DISPLAY, in.startDisplayLabel,
                    false, false, 0, new Target(Action.START_DISPLAY, in.displayPageId, -1)));
            }
            return new Column(Kind.DISPLAY, glyph, cells);
        }
        int count = Math.min(in.displayAppLabels.size(), in.displayAppIds.size());
        List<Cell> apps = new ArrayList<>(count);
        int currentIndex = -1;
        for (int i = 0; i < count; i++) {
            int id = in.displayAppIds.get(i);
            boolean current = in.currentKind == Kind.DISPLAY && id == in.currentDisplayApp;
            if (current) currentIndex = i;
            apps.add(new Cell(CellKind.WINDOW, Kind.DISPLAY,
                plainLabel(in.displayAppLabels.get(i)),
                current, false, 0,
                current ? new Target(Action.CLOSE, in.displayPageId, id)
                    : new Target(Action.GO_DISPLAY_APP, in.displayPageId, id)));
        }
        return new Column(Kind.DISPLAY, glyph,
            fit(apps, currentIndex, in.capacity, Kind.DISPLAY, in,
                new Target(Action.OPEN_DISPLAY_LIST, in.displayPageId, -1)));
    }

    private static Column assistantColumn(Input in) {
        boolean missing = in.assistantPageId < 0;
        Target target = missing
            ? new Target(Action.ADD_ASSISTANT, -1, -1) : pageTarget(in, in.assistantPageId);
        return new Column(Kind.ASSISTANT, glyph(in, Kind.ASSISTANT, missing, target),
            new ArrayList<>());
    }

    // ---- pieces ---------------------------------------------------------------------------

    /** A page: where the user already is closes the picker; anywhere else goes there. */
    private static Target pageTarget(Input in, int pageId) {
        return pageId == in.currentPageId
            ? new Target(Action.CLOSE, pageId, -1) : new Target(Action.GO_PAGE, pageId, -1);
    }

    private static Cell glyph(Input in, Kind kind, boolean dimmed, Target target) {
        return new Cell(CellKind.GLYPH, kind, in.kindNames[kind.ordinal()],
            in.currentKind == kind, dimmed, 0, target);
    }

    /**
     * The cells a column of {@code all} windows shows within {@code capacity}: all of them when
     * they fit, else the top cell becomes "+N" and the rest are a run of windows that keeps the
     * current one in view (the first run, unless the current window lies beyond it).
     */
    private static List<Cell> fit(List<Cell> all, int currentIndex, int capacity, Kind kind,
                                  Input in, Target overflowTarget) {
        int n = all.size();
        if (n <= capacity) return new ArrayList<>(all);
        List<Cell> out = new ArrayList<>(Math.max(capacity, 0));
        if (capacity <= 0) return out;
        int shown = capacity - 1;
        int start = 0;
        if (currentIndex >= shown) start = currentIndex - shown + 1;
        start = Math.max(0, Math.min(start, n - shown));
        for (int i = 0; i < shown; i++) out.add(all.get(start + i));
        int hidden = n - shown;
        out.add(new Cell(CellKind.OVERFLOW, kind, "+" + hidden, false, false, hidden,
            overflowTarget));
        return out;
    }
}
