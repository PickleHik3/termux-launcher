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
    /**
     * The keyboard's foot: the kept band the in-app keyboard's own host reaches down by, below its
     * keys, while it is laid out in the stack's column as a Docked slice (shown, or down and still
     * laid out). A keyboard that wears its own material then reaches the screen's bottom itself.
     * The foot is inside the keyboard's measured height; the stack's own numbers (its height, the
     * rooms, the keyboard's translation) all count the keys alone ({@link #keyboardKeysPx}).
     */
    public final int keyboardFootPx;

    private BottomInsets(int rootPaddingPx, int keptPx, int stackMarginPx,
                         int stackExtraHeightPx, int stackPaddingPx, int keyboardColumnMarginPx,
                         int dockFootPx, int keyboardFootPx) {
        this.keyboardFootPx = keyboardFootPx;
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

    /**
     * How far the stack's rows are lifted above where the root's padding leaves them, for a system
     * keyboard reaching {@code imeBottomPx} up from the screen's bottom: the band of it neither the
     * navigation bar {@code navBottomPx} nor the root's padding with the keyboard up
     * ({@link #rootPaddingPx}{@code (true, fitPx, restPx)}) already clears. A window resized for
     * the keyboard pads the root by all of it and lifts nothing; one that is not lifts the rows by
     * the whole keyboard. Both numbers come from the same insets, so the rows always stand on the
     * keyboard's top: {@code rootPaddingPx + systemImeLiftPx} is the keyboard's reach.
     */
    public static int systemImeLiftPx(int imeBottomPx, int navBottomPx, int fitPx, int restPx) {
        int cleared = Math.max(Math.max(0, navBottomPx), rootPaddingPx(true, fitPx, restPx));
        return Math.max(0, imeBottomPx - cleared);
    }

    /** What the content keeps clear below itself: the part of the fit the root no longer pads. */
    public static int keptPx(boolean imeVisible, int fitPx, int restPx) {
        return Math.max(0, fitPx) - rootPaddingPx(imeVisible, fitPx, restPx);
    }

    /**
     * The band at the screen's bottom the sheets layer reaches through, under the gesture pill:
     * the kept band {@code keptPx}. A surface in the layer runs its glass through it and keeps its
     * content above it. 0 while the system keyboard is up, when the layer stops at the root's
     * padding.
     */
    public static int sheetsBandPx(int keptPx) {
        return Math.max(0, keptPx);
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
        return forKept(root, kept, dockFoot, stackPassMarginPx, flushPaddingPx, false);
    }

    /**
     * The foot the keyboard's own host wears for a kept band: the whole band while the keyboard is
     * laid out in the column as a Docked slice ({@code keyboardFoot}), else none. The dress phase
     * asks this with the stack pass's inputs, so both put the same band in the same place.
     */
    public static int keyboardFootPx(int kept, boolean keyboardFoot) {
        return keyboardFoot && kept > 0 ? kept : 0;
    }

    /**
     * Whether the keyboard wears the kept band as its own foot: it is laid out in the stack's
     * column ({@code laidOut}: shown, or down and still laid out) as a Docked slice (not a
     * {@code capsule}, not in its floating frame), no bands stand under it and the system keyboard
     * is away. The one question the dress phase and the stack pass both ask.
     */
    public static boolean keyboardWearsFoot(boolean capsule, boolean floatingForm,
                                            boolean imeVisible, boolean underBandsShown,
                                            boolean laidOut) {
        if (capsule || floatingForm || imeVisible || underBandsShown) return false;
        return laidOut;
    }

    /** The keyboard's keys, without its foot, of a laid-out height. */
    public static int keyboardKeysPx(int laidOutPx, int footPx) {
        return Math.max(0, laidOutPx - Math.max(0, footPx));
    }

    /**
     * {@link #forKept(int, int, boolean, int, int)} with the keyboard wearing the band as its own
     * foot ({@code keyboardFoot}, {@link #keyboardFootPx(int, boolean)}). The band leaves the
     * column's margin for the keyboard's host, so the keys stand where they did.
     */
    public static BottomInsets forKept(int root, int kept, boolean dockFoot,
                                       int stackPassMarginPx, int flushPaddingPx,
                                       boolean keyboardFoot) {
        int flush = Math.max(0, flushPaddingPx);
        int margin = Math.max(0, stackPassMarginPx);
        if (kept <= 0) return new BottomInsets(root, kept, margin, 0, flush, 0, 0, 0);
        int foot = keyboardFootPx(kept, keyboardFoot);
        // The kept band is laid out the same with or without the foot, so the foot changing hands
        // at a landing moves no layout number: the container is the band taller and stands on the
        // screen's edge, the column one band up. Only the foot's surface (and where the flush
        // remainder sits) is the foot's. With the keyboard's own foot the band is inside its host
        // instead of under its column.
        if (!dockFoot) return new BottomInsets(root, kept, margin, kept - foot, flush,
            kept - foot, 0, foot);
        return new BottomInsets(root, kept, margin, kept - foot, 0, flush + kept - foot,
            flush + kept, foot);
    }

    /**
     * The band the container is laid out taller by than the stack and the keyboard's keys: the
     * kept band, whoever draws it. The container's height is the same with or without the
     * keyboard's foot.
     */
    public int containerExtraPx() {
        return stackExtraHeightPx + keyboardFootPx;
    }

    /**
     * How far a Home page's content column reaches below where it stands with the keyboard down
     * and no band kept, for the system keyboard's take on the root {@code systemImeTakePx}
     * ({@link KeyboardOverlayPolicy#systemImeTakePx}) and the band kept {@code keptPx}. The column
     * hangs above the accessory stack, which stands on the kept band (by its margin, or by its
     * foot's height), and the root's padding grows by the take plus the band whenever the system
     * keyboard comes up ({@link #rootPaddingPx}): both moves have to be answered together, or the
     * frame between this inset dispatch and the stack's pass lays the grid out a band short
     * (keyboard up) or a band tall (keyboard down). Only its change between two dispatches is
     * meaningful.
     */
    public static int homeContentReachPx(int systemImeTakePx, int keptPx) {
        return Math.max(0, systemImeTakePx) - Math.max(0, keptPx);
    }

    /**
     * How much of the in-app keyboard's bottom is cut while the wall's travel has the accessory
     * stack {@code travelPx} below where it is laid out: whatever has slid past the keyboard
     * column's laid-out bottom, which stands {@code belowKeyboardPx} under the keyboard (the bands
     * under it, their air, the column's padding) and {@link #stackPaddingPx} above the stack's
     * own laid-out bottom. The stack's clip cuts at its own bottom, which with the dock's foot
     * reaches the screen's edge; the column stands the foot's band higher
     * ({@link #keyboardColumnMarginPx}), so a keyboard slid down off a crossing to a keyboard-down
     * page would show its top row in that band under the gesture pill. Without a foot the column
     * margin is 0 and this cuts exactly where the stack's clip does. {@code keyboardSharePx} is
     * the keyboard's own share, which hides it behind bands held up under it; the larger cut wins.
     */
    public static int keyboardTravelHiddenPx(float travelPx, float keyboardSharePx,
                                             int belowKeyboardPx, int stackPaddingPx) {
        float slidPast = Math.max(0f, travelPx) - Math.max(0, belowKeyboardPx)
            - Math.max(0, stackPaddingPx);
        return Math.round(Math.max(Math.max(0f, keyboardSharePx), slidPast));
    }

    /**
     * {@link #keyboardTravelHiddenPx(float, float, int, int)} for a keyboard that wears a foot
     * ({@code footPx}) below its {@code keysPx}: the cut also eats the foot in proportion to the
     * share of the keys that has slid ({@code keyboardSharePx / keysPx}), so no top row shows in
     * the band under the pill at rest or on a crossing; at no share nothing is cut, and at the
     * keys' whole share the whole keyboard, foot included, is.
     */
    public static int keyboardTravelHiddenPx(float travelPx, float keyboardSharePx,
                                             int belowKeyboardPx, int stackPaddingPx, int footPx,
                                             int keysPx) {
        float share = Math.max(0f, keyboardSharePx);
        float withFoot = share;
        if (footPx > 0 && keysPx > 0) withFoot += footPx * Math.min(1f, share / keysPx);
        float slidPast = Math.max(0f, travelPx) - Math.max(0, belowKeyboardPx)
            - Math.max(0, stackPaddingPx);
        return Math.round(Math.max(withFoot, slidPast));
    }

    /**
     * Where the rows' bottom stands above the screen's bottom edge: the parity number. The
     * keyboard's foot stands under the keys, so it counts as much as the margin it replaced.
     */
    public int rowsBottomFromScreenBottomPx() {
        return rootPaddingPx + stackMarginPx + stackPaddingPx + keyboardColumnMarginPx
            + keyboardFootPx;
    }
}
