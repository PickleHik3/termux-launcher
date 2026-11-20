package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the page bar shows and where each entry goes: one entry per wall stop, in the wall's swipe
 * order ({@code WallPages.stops}), so Home pages may sit between Terminal windows and Display
 * pages. The Terminal page is one entry per window, or one entry that starts a shell when it has
 * none. Pure: the activity fills an {@link Input} from the wall and the windows; the bar draws the
 * answer and hands a chosen entry's {@link PageSwitchModel.Target} to
 * {@link PageSwitchTargets#carryOut}.
 */
public final class PageBarModel {

    /** One page of the wall, in wall order. */
    public static final class Page {
        public final int id;
        @NonNull public final PageKind kind;
        /** The page's own name; null or empty is unnamed. */
        @Nullable public final String name;

        public Page(int id, @NonNull PageKind kind, @Nullable String name) {
            this.id = id;
            this.kind = kind;
            this.name = name;
        }
    }

    /** What the bar is built from. Ids are wall page ids; -1 is none. */
    public static final class Input {
        /** Every wall page, in wall order. */
        @NonNull public List<Page> pages = Collections.emptyList();
        /** The Terminal page's windows, by the window bar's labels; null or empty is unnamed. */
        @NonNull public List<String> windowNames = Collections.emptyList();
        /** The window you are on or would return to; -1 when there is none. */
        public int currentWindow = -1;
        /** The wall's current page; -1 when there is none. */
        public int currentPageId = -1;
        public boolean displayRunning;
        /** False under Use as Terminal only. */
        public boolean homeOffered = true;
        /** Page-kind names in {@link PageKind} ordinal order: Home, Terminal, Display, Assistant. */
        @NonNull public String[] kindNames = {"Home", "Terminal", "Display", "Assistant"};
    }

    public static final class Entry {
        @NonNull public final PageKind kind;
        /** 1-based within the kind for Home pages and windows; Display only when the wall has several; else 0. */
        public final int number;
        /** Own name, else the kind's name and number: "Home 1", "Terminal 2", "Assistant". */
        @NonNull public final String label;
        /** Where the user is. */
        public final boolean current;
        /** A window you would return to when the wall is on another page; a folded Terminal tap goes here. */
        public final boolean returnTo;
        @NonNull public final PageSwitchModel.Target target;

        Entry(@NonNull PageKind kind, int number, @NonNull String label, boolean current,
              boolean returnTo, @NonNull PageSwitchModel.Target target) {
            this.kind = kind;
            this.number = number;
            this.label = label;
            this.current = current;
            this.returnTo = returnTo;
            this.target = target;
        }

        @Override public String toString() {
            return label + (current ? "*" : "") + "->" + target;
        }
    }

    @NonNull private final List<Entry> mEntries;
    private final int mCurrentIndex;

    private PageBarModel(@NonNull List<Entry> entries) {
        mEntries = Collections.unmodifiableList(entries);
        int current = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).current) {
                current = i;
                break;
            }
        }
        mCurrentIndex = current;
    }

    /** The entries in wall stop order. */
    @NonNull public List<Entry> entries() {
        return mEntries;
    }

    /** The current entry, or -1 when the wall is on none of them. */
    public int currentIndex() {
        return mCurrentIndex;
    }

    @NonNull
    public static PageBarModel build(@NonNull Input in) {
        int displays = 0;
        for (Page p : in.pages) {
            if (p.kind == PageKind.DISPLAY) displays++;
        }
        List<Entry> out = new ArrayList<>();
        int home = 0;
        int display = 0;
        for (Page p : in.pages) {
            boolean onPage = p.id == in.currentPageId;
            switch (p.kind) {
                case HOME:
                    home++;
                    if (in.homeOffered) {
                        out.add(entry(in, p, home, onPage, false, pageTarget(p.id, onPage)));
                    }
                    break;
                case DISPLAY:
                    display++;
                    if (in.displayRunning) {
                        out.add(entry(in, p, displays > 1 ? display : 0, onPage, false,
                            pageTarget(p.id, onPage)));
                    }
                    break;
                case TERMINAL:
                    addTerminal(in, p, out);
                    break;
                default:
                    out.add(entry(in, p, 0, onPage, false, pageTarget(p.id, onPage)));
                    break;
            }
        }
        return new PageBarModel(out);
    }

    private static void addTerminal(Input in, Page p, List<Entry> out) {
        boolean onPage = p.id == in.currentPageId;
        int count = in.windowNames.size();
        if (count == 0) {
            out.add(entry(in, p, 0, onPage, false, onPage
                ? new PageSwitchModel.Target(PageSwitchModel.Action.CLOSE, p.id, -1)
                : new PageSwitchModel.Target(PageSwitchModel.Action.GO_TERMINAL, p.id, -1)));
            return;
        }
        for (int w = 0; w < count; w++) {
            boolean current = onPage && w == in.currentWindow;
            String name = in.windowNames.get(w);
            String plain = name == null || name.isEmpty() ? null : PageSwitchModel.plainLabel(name);
            out.add(new Entry(PageKind.TERMINAL, w + 1,
                label(in, PageKind.TERMINAL, w + 1, plain), current, w == in.currentWindow,
                new PageSwitchModel.Target(current ? PageSwitchModel.Action.CLOSE
                    : PageSwitchModel.Action.GO_TERMINAL, p.id, w)));
        }
    }

    private static PageSwitchModel.Target pageTarget(int id, boolean current) {
        return new PageSwitchModel.Target(
            current ? PageSwitchModel.Action.CLOSE : PageSwitchModel.Action.GO_PAGE, id, -1);
    }

    private static Entry entry(Input in, Page p, int number, boolean current, boolean returnTo,
                               PageSwitchModel.Target target) {
        return new Entry(p.kind, number, label(in, p.kind, number, p.name), current, returnTo, target);
    }

    private static String label(Input in, PageKind kind, int number, @Nullable String own) {
        if (own != null && !own.isEmpty()) return own;
        String name = in.kindNames[kind.ordinal()];
        return number > 0 ? name + " " + number : name;
    }
}
