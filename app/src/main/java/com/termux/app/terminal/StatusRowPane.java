package com.termux.app.terminal;

import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/**
 * Which panes of a window hold the status bar's thin row: every pane along the window's top, the
 * row lying from the one on screen at the top-left to the one at the top-right
 * (PaneRowPolicy.splitRow). Pure.
 *
 * <p>A split lays its first child out first: on top when stacked, and at the start when side by
 * side, which is the left in a left-to-right layout and the right in a right-to-left one (a
 * horizontal LinearLayout mirrors under RTL). So the walk down the tree takes the first child of
 * every split but a side-by-side one in RTL, where the left pane is the second.
 */
public final class StatusRowPane {

    private StatusRowPane() {}

    /**
     * Whether the top-left pane lies under a split's first child.
     *
     * @param sideBySide whether the split lays its children side by side (horizontal)
     * @param rtl        whether the window lays out right to left
     */
    public static boolean takesFirst(boolean sideBySide, boolean rtl) {
        return !(sideBySide && rtl);
    }

    /**
     * How many panes of the tree shown from {@code root} touch its top: one for a pane alone or
     * panes in rows only, one per column along the top otherwise (a column split into rows counts
     * its top pane). PaneRowPolicy.splitRow reads it.
     */
    static int panesAlongTop(@Nullable TerminalPaneController.Node root) {
        if (!(root instanceof TerminalPaneController.Split)) return root == null ? 0 : 1;
        TerminalPaneController.Split split = (TerminalPaneController.Split) root;
        if (split.orientation != LinearLayout.HORIZONTAL) return panesAlongTop(split.a);
        return panesAlongTop(split.a) + panesAlongTop(split.b);
    }

    /** Adds the panes along the top of the tree shown from {@code root} to {@code out}, in tree order. */
    static void collectAlongTop(@Nullable TerminalPaneController.Node root,
                                @androidx.annotation.NonNull
                                java.util.List<TerminalPaneController.Leaf> out) {
        if (root instanceof TerminalPaneController.Leaf) {
            out.add((TerminalPaneController.Leaf) root);
        } else if (root instanceof TerminalPaneController.Split) {
            TerminalPaneController.Split split = (TerminalPaneController.Split) root;
            collectAlongTop(split.a, out);
            if (split.orientation == LinearLayout.HORIZONTAL) collectAlongTop(split.b, out);
        }
    }

    /**
     * Whether {@code node} touches the top of the tree shown from {@code root}: it lies under
     * {@code root}, and no stacked split on the way up has it as its lower child.
     */
    static boolean alongTop(@Nullable TerminalPaneController.Node node,
                            @Nullable TerminalPaneController.Node root) {
        if (node == null || root == null) return false;
        while (node != root) {
            TerminalPaneController.Split parent = node.parent;
            if (parent == null) return false;
            if (parent.orientation != LinearLayout.HORIZONTAL && parent.b == node) return false;
            node = parent;
        }
        return true;
    }

    /**
     * The pane along the top of the tree shown from {@code root} at the screen's left
     * ({@code right} false) or right end: the side-by-side child on that side of every split, the
     * upper child of every stacked one.
     */
    @Nullable
    static TerminalPaneController.Leaf endAlongTop(@Nullable TerminalPaneController.Node root,
                                                   boolean right, boolean rtl) {
        TerminalPaneController.Node node = root;
        while (node instanceof TerminalPaneController.Split) {
            TerminalPaneController.Split split = (TerminalPaneController.Split) node;
            boolean sideBySide = split.orientation == LinearLayout.HORIZONTAL;
            boolean first = takesFirst(sideBySide, rtl) != (sideBySide && right);
            node = first ? split.a : split.b;
        }
        return (TerminalPaneController.Leaf) node;
    }
}
