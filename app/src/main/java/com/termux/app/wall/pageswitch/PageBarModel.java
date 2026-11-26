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
 * answer and hands a chosen entry's {@link Target} to
 * {@link PageSwitchTargets#carryOut}.
 */
public final class PageBarModel {

    /** What releasing on a cell does. */
    public enum Action {
        /** Close the bar: the cell is where the user already is. */
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

    /** One page of the wall, in wall order. */
    public static final class Page {
        public final int id;
        @NonNull public final PageKind kind;
        /** The page's own name; null or empty is unnamed. */
        @Nullable public final String name;
        /** The Linux app a Display page is bound to; null when unbound or not a Display page. */
        @Nullable public final String appId;

        public Page(int id, @NonNull PageKind kind, @Nullable String name) {
            this(id, kind, name, null);
        }

        public Page(int id, @NonNull PageKind kind, @Nullable String name,
                    @Nullable String appId) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.appId = appId;
        }
    }

    /** What the bar is built from. Ids are wall page ids; -1 is none. */
    public static final class Input {
        /** Every wall page, in wall order. */
        @NonNull public List<Page> pages = Collections.emptyList();
        /** The Terminal page's windows, by the window bar's labels; null or empty is unnamed. */
        @NonNull public List<String> windowNames = Collections.emptyList();
        /**
         * Which of {@link #windowNames} are Linux windows (#392); they wear the Linux glyph. Null
         * or shorter than the names means the rest are terminal windows.
         */
        @Nullable public boolean[] linuxWindows;
        /**
         * Per window, parallel to {@link #windowNames}: a Linux window's app id, null for a
         * terminal window. A list shorter than the names means null for the rest.
         */
        @NonNull public List<String> windowAppIds = Collections.emptyList();
        /**
         * Per window, parallel to {@link #windowNames}: the program glyph of a terminal window
         * whose focused pane runs a full-screen program, else null. A list shorter than the names
         * means null for the rest.
         */
        @NonNull public List<String> windowGlyphs = Collections.emptyList();
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
        @NonNull public final Target target;
        /** The Linux app whose icon marks this entry (a Linux window, a bound Display page); else null. */
        @Nullable public final String iconKey;
        /** The full-screen program's glyph that marks this terminal window; else null. */
        @Nullable public final String glyph;

        Entry(@NonNull PageKind kind, int number, @NonNull String label, boolean current,
              boolean returnTo, @NonNull Target target) {
            this(kind, number, label, current, returnTo, target, null, null);
        }

        Entry(@NonNull PageKind kind, int number, @NonNull String label, boolean current,
              boolean returnTo, @NonNull Target target, @Nullable String iconKey,
              @Nullable String glyph) {
            this.kind = kind;
            this.number = number;
            this.label = label;
            this.current = current;
            this.returnTo = returnTo;
            this.target = target;
            this.iconKey = iconKey;
            this.glyph = glyph;
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

    /**
     * A window's name for the bar: the window bar's label without its icon code points, which
     * need the symbol font the bar's text is not drawn in. Whatever is left, trimmed; the label
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

    /**
     * The kind the page switcher key's cap shows: the page the wall is on, or the Terminal when
     * it is on none of the entries.
     */
    @NonNull public PageKind capKind() {
        return mCurrentIndex < 0 ? PageKind.TERMINAL : mEntries.get(mCurrentIndex).kind;
    }

    /**
     * The number on the cap. Home: the current Home page's place. Terminal: the window you would
     * return to, whichever page is in front; 0 with no windows. 0 for Assistant and Display.
     */
    public int capNumber() {
        PageKind kind = capKind();
        if (kind == PageKind.HOME) return mEntries.get(mCurrentIndex).number;
        if (kind != PageKind.TERMINAL) return 0;
        for (Entry e : mEntries) {
            if (e.kind == PageKind.TERMINAL && e.returnTo) return e.number;
        }
        return 0;
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
                        int number = displays > 1 ? display : 0;
                        out.add(new Entry(p.kind, number, label(in, p.kind, number, p.name),
                            onPage, false, pageTarget(p.id, onPage), p.appId, null));
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
                ? new Target(Action.CLOSE, p.id, -1)
                : new Target(Action.GO_TERMINAL, p.id, -1)));
            return;
        }
        for (int w = 0; w < count; w++) {
            boolean current = onPage && w == in.currentWindow;
            String name = in.windowNames.get(w);
            String plain = name == null || name.isEmpty() ? null : plainLabel(name);
            boolean linux = in.linuxWindows != null && w < in.linuxWindows.length && in.linuxWindows[w];
            PageKind kind = linux ? PageKind.LINUX : PageKind.TERMINAL;
            out.add(new Entry(kind, linux ? 0 : w + 1,
                linux ? (plain != null ? plain : in.kindNames[PageKind.DISPLAY.ordinal()])
                    : label(in, PageKind.TERMINAL, w + 1, plain),
                current, w == in.currentWindow,
                new Target(current ? Action.CLOSE
                    : Action.GO_TERMINAL, p.id, w),
                linux ? at(in.windowAppIds, w) : null,
                linux ? null : at(in.windowGlyphs, w)));
        }
    }

    /** {@code list}'s item at {@code i}; null when the list is shorter. */
    @Nullable
    private static String at(@NonNull List<String> list, int i) {
        return i < list.size() ? list.get(i) : null;
    }

    private static Target pageTarget(int id, boolean current) {
        return new Target(
            current ? Action.CLOSE : Action.GO_PAGE, id, -1);
    }

    private static Entry entry(Input in, Page p, int number, boolean current, boolean returnTo,
                               Target target) {
        return new Entry(p.kind, number, label(in, p.kind, number, p.name), current, returnTo, target);
    }

    private static String label(Input in, PageKind kind, int number, @Nullable String own) {
        if (own != null && !own.isEmpty()) return own;
        String name = in.kindNames[kind.ordinal()];
        return number > 0 ? name + " " + number : name;
    }
}
