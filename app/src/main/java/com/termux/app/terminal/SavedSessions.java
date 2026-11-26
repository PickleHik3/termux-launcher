package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a Saved session is, as pure policy (#359). A new save is a file of one session of one
 * window; an old save (Save sessions…) holds several windows, in several sessions. Either way a
 * saved session opens as one Workspace whose sessions are the file's windows in file order, the
 * first in front; windows saved as Home floats are left out. Nothing is converted on disk.
 */
public final class SavedSessions {

    private SavedSessions() {}

    /** The windows a saved file opens as sessions: every window but the Home floats, in order. */
    @NonNull
    public static List<TerminalWorkspace.Window> windows(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> out = new ArrayList<>();
        for (TerminalWorkspace.Session session : saved.sessions) {
            for (TerminalWorkspace.Window window : session.windows) {
                if (window.homeFloat == null) out.add(window);
            }
        }
        return out;
    }

    /** One session holding {@code window}: what Save writes for a session. */
    @NonNull
    public static TerminalWorkspace of(@NonNull String name, long savedAtEpochMs,
                                       @NonNull TerminalWorkspace.Window window) {
        return new TerminalWorkspace(name, savedAtEpochMs, 0, Collections.singletonList(
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window))));
    }

    /**
     * {@code saved} as it is opened: one session of {@link #windows}, the first in front; null
     * when the file holds no window to open.
     */
    @Nullable
    public static TerminalWorkspace forOpening(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> windows = windows(saved);
        if (windows.isEmpty()) return null;
        return new TerminalWorkspace(saved.name, saved.savedAtEpochMs, 0, Collections.singletonList(
            new TerminalWorkspace.Session(null, 0, windows)));
    }

    /** The card of a saved session: its front window's saved layout; null with no window. */
    @Nullable
    public static com.termux.app.overview.CardFacts.Workspace facts(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> windows = windows(saved);
        return windows.isEmpty() ? null : facts(windows.get(0));
    }

    /** A window's card, drawn from the saved tree: its panes' shape and what runs in each. */
    @NonNull
    public static com.termux.app.overview.CardFacts.Workspace facts(@NonNull TerminalWorkspace.Window window) {
        List<com.termux.app.overview.CardFacts.Floating> floats = new ArrayList<>(window.floats.size());
        for (TerminalWorkspace.FloatingPane floating : window.floats) {
            floats.add(new com.termux.app.overview.CardFacts.Floating(pane(floating.pane, false), floating.left,
                floating.top, floating.left + floating.width, floating.top + floating.height));
        }
        // The active pane is the window's activePane-th leaf of the tree; floats follow the tree.
        int[] leaf = {0};
        return new com.termux.app.overview.CardFacts.Workspace(node(window.root, window.activePane, leaf), floats);
    }

    @NonNull
    private static com.termux.app.overview.CardFacts.Node node(@NonNull TerminalWorkspace.Node node, int active,
                                       @NonNull int[] leaf) {
        if (node instanceof TerminalWorkspace.Pane) {
            return pane((TerminalWorkspace.Pane) node, leaf[0]++ == active);
        }
        TerminalWorkspace.Split split = (TerminalWorkspace.Split) node;
        com.termux.app.overview.CardFacts.Node a = node(split.a, active, leaf);
        com.termux.app.overview.CardFacts.Node b = node(split.b, active, leaf);
        return new com.termux.app.overview.CardFacts.Split(TerminalWorkspace.Split.HORIZONTAL.equals(split.orientation),
            split.weightA, split.weightB, a, b);
    }

    @NonNull
    private static com.termux.app.overview.CardFacts.Pane pane(@NonNull TerminalWorkspace.Pane pane, boolean active) {
        // What ran there is the only text a file keeps; the program, else the folder's name.
        String line = pane.command.isEmpty() ? lastSegment(pane.cwd) : lastSegment(pane.command.get(0));
        String[] lines = line == null || line.trim().isEmpty() ? new String[0]
            : new String[] {clip(line)};
        return new com.termux.app.overview.CardFacts.Pane(false, active, lines);
    }

    @Nullable
    private static String lastSegment(@Nullable String path) {
        if (path == null) return null;
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') end--;
        int slash = path.lastIndexOf('/', end - 1);
        return slash < 0 ? path.substring(0, end) : path.substring(slash + 1, end);
    }

    @NonNull
    private static String clip(@NonNull String text) {
        return text.length() <= com.termux.app.overview.CardFacts.MAX_CHARS ? text : text.substring(0, com.termux.app.overview.CardFacts.MAX_CHARS);
    }
}
