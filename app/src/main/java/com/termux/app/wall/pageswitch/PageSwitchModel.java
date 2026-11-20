package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.termux.app.page.PageKind;

/**
 * What the page switch panel shows and where each cell goes: one flat row with a cell per page,
 * grouped by kind in the order Home, Terminal (one per window), Assistant, Display; a kind the
 * wall has no page for is left out. Pure: the activity fills an {@link Input} from the wall and
 * the windows, the panel draws the answer.
 */
public final class PageSwitchModel {

    /** The panel's row order; {@code kindNames} and the glyph row follow it. */
    public static final List<PageKind> ROW = Collections.unmodifiableList(
        java.util.Arrays.asList(PageKind.HOME, PageKind.TERMINAL, PageKind.ASSISTANT, PageKind.DISPLAY));

    /** Where a kind sits in the panel's row. */
    public static int rowIndex(@NonNull PageKind kind) {
        return ROW.indexOf(kind);
    }

    public static final class Cell {
        @NonNull public final PageKind kind;
        /** 1-based within its kind for Home and Terminal; 0 for Assistant and Display. */
        public final int number;
        /** A Home page's name, a terminal window's label, the display's title; null when none. */
        @Nullable public final String title;
        /** PageKind, number and title as the panel names the cell: "Terminal 2 · htop". */
        @NonNull public final String fullName;
        /** Where the user is. */
        public final boolean current;
        @NonNull public final PageBarModel.Target target;

        Cell(@NonNull PageKind kind, int number, @Nullable String title, @NonNull String fullName,
             boolean current, @NonNull PageBarModel.Target target) {
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
        @NonNull public PageKind currentKind = PageKind.HOME;
        public int currentPageId = -1;
        /** Page-kind names in {@link #ROW} order. */
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
    public boolean shows(@NonNull PageKind kind) {
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
    public int numberOf(@NonNull PageKind kind) {
        if (kind == PageKind.HOME) {
            int id = mInput.currentKind == PageKind.HOME
                ? mInput.currentPageId : homeTarget(mInput.homePageIds, mInput.defaultStartPageId);
            return mInput.homePageIds.indexOf(id) + 1;
        }
        // The window you would return to, whichever page is in front; 0 only with no windows.
        if (kind == PageKind.TERMINAL) return Math.max(0, mInput.currentTerminalWindow + 1);
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

    @NonNull
    public static PageSwitchModel build(@NonNull Input in) {
        List<Cell> cells = new ArrayList<>();
        if (in.homeOffered) {
            for (int i = 0; i < in.homePageIds.size(); i++) {
                int id = in.homePageIds.get(i);
                String name = i < in.homePageLabels.size() ? in.homePageLabels.get(i) : null;
                boolean current = in.currentKind == PageKind.HOME && id == in.currentPageId;
                cells.add(cell(in, PageKind.HOME, i + 1, name, current,
                    current ? new PageBarModel.Target(PageBarModel.Action.CLOSE, id, -1)
                        : new PageBarModel.Target(PageBarModel.Action.GO_PAGE, id, -1)));
            }
        }
        if (in.terminalPageId >= 0) {
            int count = in.terminalWindowLabels.size();
            boolean onTerminal = in.currentKind == PageKind.TERMINAL;
            if (count == 0) {
                cells.add(cell(in, PageKind.TERMINAL, 1, null, onTerminal,
                    onTerminal ? new PageBarModel.Target(PageBarModel.Action.CLOSE, in.terminalPageId, -1)
                        : new PageBarModel.Target(PageBarModel.Action.GO_TERMINAL, in.terminalPageId, -1)));
            }
            for (int i = 0; i < count; i++) {
                boolean current = onTerminal && i == in.currentTerminalWindow;
                cells.add(cell(in, PageKind.TERMINAL, i + 1,
                    PageBarModel.plainLabel(in.terminalWindowLabels.get(i)), current,
                    current ? new PageBarModel.Target(PageBarModel.Action.CLOSE, in.terminalPageId, i)
                        : new PageBarModel.Target(PageBarModel.Action.GO_TERMINAL, in.terminalPageId, i)));
            }
        }
        if (in.assistantPageId >= 0) {
            cells.add(pageCell(in, PageKind.ASSISTANT, in.assistantPageId, null));
        }
        if (in.displayRunning && in.displayPageId >= 0) {
            cells.add(pageCell(in, PageKind.DISPLAY, in.displayPageId, in.displayTitle));
        }
        return new PageSwitchModel(in, cells);
    }

    private static Cell pageCell(Input in, PageKind kind, int pageId, @Nullable String title) {
        boolean current = pageId == in.currentPageId;
        return cell(in, kind, 0, title, current, current
            ? new PageBarModel.Target(PageBarModel.Action.CLOSE, pageId, -1) : new PageBarModel.Target(PageBarModel.Action.GO_PAGE, pageId, -1));
    }

    private static Cell cell(Input in, PageKind kind, int number, @Nullable String title,
                             boolean current, PageBarModel.Target target) {
        String shownTitle = title == null || title.isEmpty() ? null : title;
        StringBuilder name = new StringBuilder(in.kindNames[rowIndex(kind)]);
        if (number > 0) name.append(' ').append(number);
        if (shownTitle != null) name.append(" · ").append(shownTitle);
        return new Cell(kind, number, shownTitle, name.toString(), current, target);
    }
}
