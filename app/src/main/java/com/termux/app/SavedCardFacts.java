package com.termux.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.overview.CardFacts;
import com.termux.app.terminal.SavedSessions;
import com.termux.app.terminal.TerminalWorkspace;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a saved session's card (#359): the saved pane tree as Overview's CardFacts. Lives beside
 * the activity because it joins the terminal's saved layout to Overview's card facts, which
 * neither package may import from the other.
 */
public final class SavedCardFacts {

    private SavedCardFacts() {}

    /** The card of a saved session: its front window's saved layout; null with no window. */
    @Nullable
    public static CardFacts.Workspace facts(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> windows = SavedSessions.windows(saved);
        return windows.isEmpty() ? null : facts(windows.get(0));
    }

    /** A window's card, drawn from the saved tree: its panes' shape and what runs in each. */
    @NonNull
    public static CardFacts.Workspace facts(@NonNull TerminalWorkspace.Window window) {
        List<CardFacts.Floating> floats = new ArrayList<>(window.floats.size());
        for (TerminalWorkspace.FloatingPane floating : window.floats) {
            floats.add(new CardFacts.Floating(pane(floating.pane, false), floating.left,
                floating.top, floating.left + floating.width, floating.top + floating.height));
        }
        // The active pane is the window's activePane-th leaf of the tree; floats follow the tree.
        int[] leaf = {0};
        return new CardFacts.Workspace(node(window.root, window.activePane, leaf), floats);
    }

    @NonNull
    private static CardFacts.Node node(@NonNull TerminalWorkspace.Node node, int active,
                                       @NonNull int[] leaf) {
        if (node instanceof TerminalWorkspace.Pane) {
            return pane((TerminalWorkspace.Pane) node, leaf[0]++ == active);
        }
        TerminalWorkspace.Split split = (TerminalWorkspace.Split) node;
        CardFacts.Node a = node(split.a, active, leaf);
        CardFacts.Node b = node(split.b, active, leaf);
        return new CardFacts.Split(TerminalWorkspace.Split.HORIZONTAL.equals(split.orientation),
            split.weightA, split.weightB, a, b);
    }

    @NonNull
    private static CardFacts.Pane pane(@NonNull TerminalWorkspace.Pane pane, boolean active) {
        // What ran there is the only text a file keeps; the program, else the folder's name.
        String line = pane.command.isEmpty() ? lastSegment(pane.cwd) : lastSegment(pane.command.get(0));
        String[] lines = line == null || line.trim().isEmpty() ? new String[0]
            : new String[] {clip(line)};
        return new CardFacts.Pane(false, active, lines);
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
        return text.length() <= CardFacts.MAX_CHARS ? text : text.substring(0, CardFacts.MAX_CHARS);
    }
}
