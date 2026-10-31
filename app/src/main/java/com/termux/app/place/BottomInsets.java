package com.termux.app.place;

/**
 * The one owner of the navigation inset at the screen's bottom (ADR 0018 §1, D3, D8): how much of
 * it the content keeps clear and how much the dock's surface takes.
 *
 * <p>The root no longer pads its children by the navigation inset. With the system keyboard
 * visible it fits the window exactly as before (nothing reaches under the keyboard, D8). With it
 * away the root pads nothing at the bottom and the content container keeps the band clear for
 * every child but the stack's layout, which reaches the screen's bottom edge; in it the dock's
 * surface alone continues down into the band while the dock stands on the edge with the in-app
 * keyboard down ({@code dockFoot}). Everything the stack's rows and the keyboard stand on stays
 * where it was: {@link #rowsBottomFromScreenBottomPx} is the same with or without the foot.
 */
public final class BottomInsets {

    /** The root's own bottom padding. */
    public final int rootPaddingPx;
    /** The band the content container keeps clear below every child but the stack's layout. */
    public final int keptPx;
    /** The stack container's bottom margin, the height added to it, and its bottom padding. */
    public final int stackMarginPx;
    public final int stackExtraHeightPx;
    public final int stackPaddingPx;
    /** The bottom margin of the keyboard column, which the rows stand above. */
    public final int keyboardColumnMarginPx;
    /** The dock surface's foot: how far it runs below the rows' bottom. */
    public final int dockFootPx;

    private BottomInsets(int rootPaddingPx, int keptPx, int stackMarginPx,
                         int stackExtraHeightPx, int stackPaddingPx, int keyboardColumnMarginPx,
                         int dockFootPx) {
        this.rootPaddingPx = rootPaddingPx;
        this.keptPx = keptPx;
        this.stackMarginPx = stackMarginPx;
        this.stackExtraHeightPx = stackExtraHeightPx;
        this.stackPaddingPx = stackPaddingPx;
        this.keyboardColumnMarginPx = keyboardColumnMarginPx;
        this.dockFootPx = dockFootPx;
    }

    /**
     * The root's bottom padding for the legacy fit {@code fitPx} the window gives it, the bars'
     * own bottom inset {@code restPx}, and the system keyboard visible or not.
     */
    public static int rootPaddingPx(boolean imeVisible, int fitPx, int restPx) {
        if (imeVisible) return Math.max(0, fitPx);
        return Math.max(0, fitPx - Math.max(0, restPx));
    }

    /** What the content keeps clear below itself: the part of the fit the root no longer pads. */
    public static int keptPx(boolean imeVisible, int fitPx, int restPx) {
        return Math.max(0, fitPx) - rootPaddingPx(imeVisible, fitPx, restPx);
    }

    /**
     * The stack's placement for the fit, the stack pass's own margin and flush padding, and the
     * dock owning the foot or not ({@code DockSpec#foot}).
     */
    public static BottomInsets of(boolean imeVisible, int fitPx, int restPx, boolean dockFoot,
                                  int stackPassMarginPx, int flushPaddingPx) {
        return forKept(rootPaddingPx(imeVisible, fitPx, restPx), keptPx(imeVisible, fitPx, restPx),
            dockFoot, stackPassMarginPx, flushPaddingPx);
    }

    /** {@link #of} for a root padding and a kept band already worked out from the insets. */
    public static BottomInsets forKept(int root, int kept, boolean dockFoot,
                                       int stackPassMarginPx, int flushPaddingPx) {
        int flush = Math.max(0, flushPaddingPx);
        int margin = Math.max(0, stackPassMarginPx);
        if (!dockFoot || kept <= 0) return new BottomInsets(root, kept, margin + kept, 0, flush, 0, 0);
        return new BottomInsets(root, kept, margin, kept, 0, flush + kept, flush + kept);
    }

    /** Where the rows' bottom stands above the screen's bottom edge: the parity number. */
    public int rowsBottomFromScreenBottomPx() {
        return rootPaddingPx + stackMarginPx + stackPaddingPx + keyboardColumnMarginPx;
    }
}
