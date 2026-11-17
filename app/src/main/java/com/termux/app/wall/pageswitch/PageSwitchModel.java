package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the page switch panel shows and where each cell goes: one flat row with a cell per page,
 * grouped by kind in the order Home, Terminal (one per window), Assistant, Display; a kind the
 * wall has no page for is left out. Pure: the activity fills an {@link Input} from the wall and
 * the windows, the panel draws the answer.
 */
public final class PageSwitchModel {

    /** The four page kinds, in the order the row shows them. */
    public enum Kind { HOME, TERMINAL, ASSISTANT, DISPLAY }

    /** What releasing on a cell does. */
    public enum Action {
        /** Close the panel: the cell is where the user already is. */
        CLOSE,
        /** Go to the page {@link Target#pageId}. */
        GO_PAGE,
        /** Go to the Terminal page at window {@link Target#index}; -1 is its current window. */
        GO_TERMINAL
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
        @NonNull public final Kind kind;
        /** 1-based within its kind for Home and Terminal; 0 for Assistant and Display. */
        public final int number;
        /** A Home page's name, a terminal window's label, the display's title; null when none. */
        @Nullable public final String title;
        /** Kind, number and title as the panel names the cell: "Terminal 2 · htop". */
        @NonNull public final String fullName;
        /** Where the user is. */
        public final boolean current;
        @NonNull public final Target target;

        Cell(@NonNull Kind kind, int number, @Nullable String title, @NonNull String fullName,
             boolean current, @NonNull Target target) {
            this.kind = kind;
            this.number = number;
            this.title = title;
            this.fullName = fullName;
            this.current = current;
            this.target = target;
        }

        @Override public String toString() {
            return fullName + (current ? "*" : "") + "->" + target;
        }
    }

    /** What the panel is built from. Ids are wall page ids; -1 is none. */
    public static final class Input {
        /** The wall's Home pages, in order. */
        @NonNull public List<Integer> homePageIds = Collections.emptyList();
        /** The Home pages' names, in step with {@link #homePageIds}; null or empty is unnamed. */
        @NonNull public List<String> homePageLabels = Collections.emptyList();
        /** The page the app starts on, whatever kind it is; -1 when there is none. */
        public int defaultStartPageId = -1;
        /** False under Use as Terminal only. */
        public boolean homeOffered = true;
        public int terminalPageId = -1;
        /** The Terminal page's windows, by the window bar's labels. */
        @NonNull public List<String> terminalWindowLabels = Collections.emptyList();
        public int currentTerminalWindow = -1;
        /** -1: Assistant is off. */
        public int assistantPageId = -1;
        public int displayPageId = -1;
        public boolean displayRunning;
        @Nullable public String displayTitle;
        /** The page kind the wall is on, and its page. */
        @NonNull public Kind currentKind = Kind.HOME;
        public int currentPageId = -1;
        /** Page-kind names in {@link Kind} order. */
        @NonNull public String[] kindNames = {"Home", "Terminal", "Assistant", "Display"};
    }

    @NonNull private final Input mInput;
    @NonNull private final List<Cell> mCells;
    private final int mCurrentIndex;

    private PageSwitchModel(@NonNull Input in, @NonNull List<Cell> cells) {
        mInput = in;
        mCells = Collections.unmodifiableList(cells);
        int current = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).current) {
                current = i;
                break;
            }
        }
        mCurrentIndex = current;
    }

    /** The cells in row order: Home 1..n, Terminal 1..n, Assistant, Display. */
    @NonNull public List<Cell> cells() {
        return mCells;
    }

    /** The current page's cell, or -1 when the wall is on none of them. */
    public int currentIndex() {
        return mCurrentIndex;
    }

    /** Whether the row has a cell of this kind. */
    public boolean shows(@NonNull Kind kind) {
        switch (kind) {
            case HOME: return mInput.homeOffered && !mInput.homePageIds.isEmpty();
            case TERMINAL: return mInput.terminalPageId >= 0;
            case ASSISTANT: return mInput.assistantPageId >= 0;
            default: return mInput.displayRunning && mInput.displayPageId >= 0;
        }
    }

    /**
     * The number a kind's glyph holds. Home: the current Home page's place, else the place of
     * the Home page the glyph goes to. Terminal: the current window's place. 0 for none and for
     * Assistant and Display.
     */
    public int numberOf(@NonNull Kind kind) {
        if (kind == Kind.HOME) {
            int id = mInput.currentKind == Kind.HOME
                ? mInput.currentPageId : homeTarget(mInput.homePageIds, mInput.defaultStartPageId);
            return mInput.homePageIds.indexOf(id) + 1;
        }
        // The window you would return to, whichever page is in front; 0 only with no windows.
        if (kind == Kind.TERMINAL) return Math.max(0, mInput.currentTerminalWindow + 1);
        return 0;
    }

    /**
     * The Home page the Home glyph goes to: the page the app starts on if it is a Home page, else
     * the first one; -1 when the wall has no Home page.
     */
    public static int homeTarget(@NonNull List<Integer> homePageIds, int defaultStartPageId) {
        if (homePageIds.isEmpty()) return -1;
        if (homePageIds.contains(defaultStartPageId)) return defaultStartPageId;
        return homePageIds.get(0);
    }

    /**
     * A window's name for the panel: the window bar's label without its icon code points, which
     * need the symbol font the panel's text is not drawn in. Whatever is left, trimmed; the label
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
    public static PageSwitchModel build(@NonNull Input in) {
        List<Cell> cells = new ArrayList<>();
        if (in.homeOffered) {
            for (int i = 0; i < in.homePageIds.size(); i++) {
                int id = in.homePageIds.get(i);
                String name = i < in.homePageLabels.size() ? in.homePageLabels.get(i) : null;
                boolean current = in.currentKind == Kind.HOME && id == in.currentPageId;
                cells.add(cell(in, Kind.HOME, i + 1, name, current,
                    current ? new Target(Action.CLOSE, id, -1)
                        : new Target(Action.GO_PAGE, id, -1)));
            }
        }
        if (in.terminalPageId >= 0) {
            int count = in.terminalWindowLabels.size();
            boolean onTerminal = in.currentKind == Kind.TERMINAL;
            if (count == 0) {
                cells.add(cell(in, Kind.TERMINAL, 1, null, onTerminal,
                    onTerminal ? new Target(Action.CLOSE, in.terminalPageId, -1)
                        : new Target(Action.GO_TERMINAL, in.terminalPageId, -1)));
            }
            for (int i = 0; i < count; i++) {
                boolean current = onTerminal && i == in.currentTerminalWindow;
                cells.add(cell(in, Kind.TERMINAL, i + 1,
                    plainLabel(in.terminalWindowLabels.get(i)), current,
                    current ? new Target(Action.CLOSE, in.terminalPageId, i)
                        : new Target(Action.GO_TERMINAL, in.terminalPageId, i)));
            }
        }
        if (in.assistantPageId >= 0) {
            cells.add(pageCell(in, Kind.ASSISTANT, in.assistantPageId, null));
        }
        if (in.displayRunning && in.displayPageId >= 0) {
            cells.add(pageCell(in, Kind.DISPLAY, in.displayPageId, in.displayTitle));
        }
        return new PageSwitchModel(in, cells);
    }

    private static Cell pageCell(Input in, Kind kind, int pageId, @Nullable String title) {
        boolean current = pageId == in.currentPageId;
        return cell(in, kind, 0, title, current, current
            ? new Target(Action.CLOSE, pageId, -1) : new Target(Action.GO_PAGE, pageId, -1));
    }

    private static Cell cell(Input in, Kind kind, int number, @Nullable String title,
                             boolean current, Target target) {
        String shownTitle = title == null || title.isEmpty() ? null : title;
        StringBuilder name = new StringBuilder(in.kindNames[kind.ordinal()]);
        if (number > 0) name.append(' ').append(number);
        if (shownTitle != null) name.append(" · ").append(shownTitle);
        return new Cell(kind, number, shownTitle, name.toString(), current, target);
    }
}
