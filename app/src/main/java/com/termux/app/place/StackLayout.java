package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Side;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockPlacement;
import com.termux.app.dock.DockRows;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.dock.UnderKeyboardBand;

/**
 * Stack layout (ADR 0011): the whole bottom stack as a pure function of one snapshot. The dock's
 * rows (from Dock geometry), the status band, the air under the keyboard, the keyboard, the
 * margin under all of it, the ceiling the stack is squeezed against, the terminal's flush
 * padding, how far the content reaches under the stack, what a slide holds, and what Home's
 * floats and grid are told. The activity gathers the {@link Input}, calls {@link #layout} once per
 * geometry pass and writes the {@link Result}; it does no stack arithmetic of its own.
 *
 * <p>The squeeze is worked out here and flows into Dock geometry's snapshot, so a pass hands the
 * rows one size. The unsqueezed dock it is measured against is the one a pass at rest asks for
 * anyway, so when nothing overflows there is exactly one DockLayout per pass.
 *
 * <p>Travel asks the same module for a half's rest ({@link #restDockRowsPx},
 * {@link #restReservationPx}): each half's own rows, with its own Search field and Wall strip
 * wholly in or out, never the live eases of the half that is committed.
 */
public final class StackLayout {

    private StackLayout() {}

    /** No ceiling: before the first layout the window's height is not known. */
    public static final int NO_CEILING = Integer.MAX_VALUE;

    /**
     * The terminal's dock padding for a stack: a fraction of a text row measured against the
     * window, which no snapshot can carry, so it is a rule the activity hands in.
     */
    public interface FlushRule {
        int paddingPx(int accessoryContentHeightPx, int bottomMarginPx);
    }

    /** Everything one pass of the stack is a function of. Filled once, then read. */
    public static final class Input {
        /**
         * The dock's snapshot, both halves' resolved arrangements in it; its squeeze is ignored,
         * the module works that out. The stack's Style is its rows half's.
         */
        @NonNull public final DockGeometry.Snapshot dock;
        public boolean toolbarShown;
        public boolean keyboardShown;
        public boolean extraKeysRowEnabled;
        /** The extra-keys row's height, 0 while the row is off. */
        public int toolbarHeightPx;
        /** The in-app keyboard's reserved height, 0 while it is down. */
        public int keyboardHeightPx;
        /** A status bar standing along the bottom, with its margin; 0 elsewhere. */
        public int statusBandPx;
        /** The system keyboard's lift while it stands under the dock. */
        public int imeLiftPx;
        /** What the window holds for the stack before the margin comes off, {@link #NO_CEILING}. */
        public int ceilingBeforeMarginPx = NO_CEILING;
        /** An open keyboard floats over the place instead of shrinking it. */
        public boolean keyboardOverlays;
        /** The committed page is Home: the dock floats over its grid. */
        public boolean dockOverlays;
        public int systemImeTakePx;
        /** A slide holds the content, and the reservation it was committed with. */
        public boolean travelHoldsContent;
        public int heldReservationPx;
        /** Whether the terminal's dock padding is laid at all (one pane, on screen, no IME). */
        public boolean flushAllowed;
        public FlushRule flush = (content, margin) -> 0;

        public Input(@NonNull DockGeometry.Snapshot dock) {
            this.dock = dock;
        }
    }

    /** The whole bottom stack for one pass. Worked out once, at construction, then read. */
    public static final class Result {
        /** The dock the pass applies, squeezed as the stack needed. */
        @NonNull public final DockLayout dock;
        /** How much the stack sheds from the apps row to fit under its ceiling; 0 when it fits. */
        public final int squeezePx;
        /** The margin under the stack: the shared edge gap plus the IME's lift. */
        public final int bottomMarginPx;
        /** The edge gap alone, the part of the margin the stack keeps whatever it holds. */
        public final int stackEdgeGapPx;
        public final boolean bandsUnderKeyboard;
        public final int underKeyboardAirPx;
        public final int underKeyboardTopMarginPx;
        public final int underKeyboardBottomMarginPx;
        public final int statusBandPx;
        /** The dock's rows with the status band and the air under the keyboard. */
        public final int dockContentHeightPx;
        /** The terminal's dock padding, and the half of it under the rows. */
        public final int flushPaddingPx;
        public final int flushBottomInsetPx;
        /** The stack's height as the container is laid out; Home's floats are told this too. */
        public final int stackHeightPx;
        public final boolean stackShown;
        /** How far the content reaches past the stack's top, after Home and a slide's hold. */
        public final int contentOverlapPx;
        /** The same for a side rail on Home: the stack and its margin. */
        public final int sideStackOverlapPx;
        /** How much of the system keyboard's take the content reaches past (Home only). */
        public final int homeImeReachPx;
        /** The gap the content keeps from the edge while the stack is away. */
        public final int contentEdgeGapPx;
        /** The band of the widget pane the dock takes at rest. */
        public final int homeGridDockReservePx;
        /** The room the chrome takes from the content. */
        public final int contentReservationPx;
        /** What Home's floats are told: the dock's rows with the padding, and the IME's push. */
        public final int homeFloatDockPx;
        public final int homeFloatImePushPx;

        private Result(@NonNull Input in, @NonNull DockGeometry geometry) {
            // The caller's snapshot carries the last pass's squeeze; most passes have none.
            DockGeometry.Snapshot unsqueezed = in.dock.squeezePx == 0 ? in.dock
                : in.dock.toBuilder().squeezePx(0).build();
            DockLayout probe = geometry.layout(unsqueezed);
            // The stack wears the Style of the half whose rows it stands, from its arrangement.
            Base base = new Base(probe, in.dock.rowsHalf(), in.dock.arrangement,
                in.toolbarShown, in.keyboardShown, in.imeLiftPx);
            int ceilingPx = in.ceilingBeforeMarginPx == NO_CEILING ? NO_CEILING
                : Math.max(0, in.ceilingBeforeMarginPx - Math.max(0, base.marginPx));
            int keyboardOverlapPx = KeyboardOverlayPolicy.contentOverlapPx(
                in.keyboardOverlays, in.keyboardShown, in.keyboardHeightPx);
            int projectedPx = stackHeightPx(
                probe.combinedHeight(in.toolbarHeightPx, in.extraKeysRowEnabled)
                    + in.statusBandPx + base.airPx, 0, in.keyboardHeightPx);
            // The ceiling exists to keep a usable slice of content under the stack. A floating
            // keyboard takes none of that slice, so only what the stack really costs the content
            // is measured against it.
            int squeeze = 0;
            DockLayout squeezed = probe;
            if (projectedPx - keyboardOverlapPx > ceilingPx) {
                squeeze = projectedPx - keyboardOverlapPx - ceilingPx;
                // Two layouts for an overflowing pass: the policy clamps a negative extra row
                // height to zero, so today the two are equal, but the squeeze stays an input.
                squeezed = geometry.layout(in.dock.toBuilder().squeezePx(squeeze).build());
            }

            dock = squeezed;
            squeezePx = squeeze;
            bottomMarginPx = base.marginPx;
            stackEdgeGapPx = base.edgeGapPx;
            bandsUnderKeyboard = base.bandsUnderKeyboard;
            underKeyboardAirPx = base.airPx;
            underKeyboardTopMarginPx = bandsUnderKeyboard ? UnderKeyboardBand.topGapPx(dock) : 0;
            underKeyboardBottomMarginPx = bandsUnderKeyboard
                ? UnderKeyboardBand.bottomAirPx(dock, base.edgeGapPx) : 0;
            statusBandPx = in.statusBandPx;
            dockContentHeightPx = base.dockContentPx(dock, in.toolbarShown, in.toolbarHeightPx,
                in.extraKeysRowEnabled, in.statusBandPx);
            int accessoryContentPx = stackHeightPx(dockContentHeightPx, 0, in.keyboardHeightPx);
            flushPaddingPx = in.keyboardShown || !in.toolbarShown || !in.flushAllowed ? 0
                : in.flush.paddingPx(accessoryContentPx, base.marginPx);
            flushBottomInsetPx = flushPaddingPx / 2;
            stackHeightPx = stackHeightPx(dockContentHeightPx, flushPaddingPx,
                in.keyboardHeightPx);
            homeFloatDockPx = dockContentHeightPx + flushPaddingPx;
            homeFloatImePushPx = in.systemImeTakePx + (base.lifted ? Math.max(0, in.imeLiftPx) : 0);

            // On a Home page the dock floats with the keyboard, either keyboard: the grid keeps
            // its whole height.
            int imeReachPx = in.dockOverlays ? in.systemImeTakePx : 0;
            int overlapPx = KeyboardOverlayPolicy.overlapPx(in.dockOverlays, stackHeightPx,
                base.marginPx, keyboardOverlapPx, imeReachPx);
            sideStackOverlapPx = KeyboardOverlayPolicy.overlapPx(true, stackHeightPx,
                base.marginPx, 0, imeReachPx);
            // While the wall travels with chrome pre-rolled, the content keeps exactly the room it
            // was committed with: whatever the stack grew by for the slide, the content reaches
            // under it by the same amount.
            if (in.travelHoldsContent) {
                overlapPx = PlaceChromeTravel.heldOverlapPx(stackHeightPx, base.marginPx,
                    in.heldReservationPx);
                imeReachPx = 0;
            }
            contentOverlapPx = overlapPx;
            homeImeReachPx = imeReachPx;
            stackShown = DockRows.stackShown(in.toolbarShown, in.keyboardShown,
                in.statusBandPx > 0);
            // With the stack away the content is the surface on the bottom edge and keeps the gap
            // the dock would.
            contentEdgeGapPx = stackShown ? 0 : base.marginPx;
            homeGridDockReservePx = KeyboardOverlayPolicy.homeGridDockReservePx(
                in.dockOverlays && stackShown, dockContentHeightPx, base.edgeGapPx);
            contentReservationPx = KeyboardOverlayPolicy.contentReservationPx(
                stackHeightPx, base.marginPx, overlapPx);
        }
    }

    /**
     * What a pass and a half's rest build the same way, from the dock they measure: the shared
     * edge gap and the IME's lift under the stack, whether bands stand under the keyboard and the
     * air they keep there, and the dock's content (its rows, the status band and that air).
     *
     * <p>The edge gap is the dock's placement's ({@link DockPlacement#floats}): the half's Style,
     * except that a standing Peek glass card is flush with the screen's edge under Floating too.
     * Whether the card stands is read off the facts the activity reads it from: the half's
     * design, the layout the stack stands, the dock shown, the keyboard, the IME's lift and the
     * Search field in the dock measured here. A joined Docked frame is not asked: under Floating
     * none stands, and Docked the dock is flush whatever the card does.</p>
     */
    private static final class Base {
        final int edgeGapPx;
        /** Whether the IME's lift stands under the stack: only while it holds rows or a keyboard. */
        final boolean lifted;
        final int marginPx;
        final boolean bandsUnderKeyboard;
        final int airPx;

        Base(@NonNull DockLayout dock, @NonNull DockGeometry.Half half,
             @NonNull PlaceLayout arrangement, boolean toolbarShown, boolean keyboardShown,
             int imeLiftPx) {
            boolean peekCardStands = DockPlacement.peekCardStands(
                DockSurfaceSpec.forStyle(half.design).peek, toolbarShown, arrangement, false,
                keyboardShown, imeLiftPx > 0, dock.promptFieldBandPx > 0);
            edgeGapPx = ChromePolicy.bottomEdgeGapPx(
                DockPlacement.floats(half.floating, peekCardStands), dock.capsuleBottomGapPx);
            lifted = toolbarShown || keyboardShown;
            marginPx = (lifted ? imeLiftPx : 0) + edgeGapPx;
            bandsUnderKeyboard = toolbarShown
                && !EdgeStackPolicy.underKeyboard(arrangement).isEmpty();
            airPx = UnderKeyboardBand.airPx(dock, edgeGapPx, bandsUnderKeyboard);
        }

        /** The dock's rows (none while it is away), the status band and the air under the keyboard. */
        int dockContentPx(@NonNull DockLayout dock, boolean toolbarShown, int toolbarHeightPx,
                          boolean extraKeysRowEnabled, int statusBandPx) {
            return (toolbarShown ? dock.combinedHeight(toolbarHeightPx, extraKeysRowEnabled) : 0)
                + statusBandPx + airPx;
        }
    }

    /** The stack's height: its rows, the terminal's padding and the keyboard. */
    public static int stackHeightPx(int dockContentHeightPx, int flushPaddingPx,
                                    int keyboardHeightPx) {
        return Math.max(0, dockContentHeightPx)
            + Math.max(0, flushPaddingPx)
            + Math.max(0, keyboardHeightPx);
    }

    /** One pass of the bottom stack. {@code geometry} memoises the dock's layouts. */
    @NonNull
    public static Result layout(@NonNull Input in, @NonNull DockGeometry geometry) {
        return new Result(in, geometry);
    }

    // ---- A half at rest, for a slide ----

    /**
     * {@code side}'s dock rows at rest, less the extra keys row itself: its own rows in its own
     * arrangement, its own Search field and Wall strip wholly in or out as its dress has them,
     * nothing squeezed. Worked out apart from the memo, which keeps the frame's answers. Whichever
     * half is committed, the answer is the same: a half's rest is its own.
     */
    public static int restDockRowsPx(@NonNull DockGeometry.Snapshot snapshot, @NonNull Side side) {
        return DockGeometry.compute(snapshot.restOf(side)).combinedHeight(0, false);
    }

    /**
     * The band a status bar standing along the bottom claims in a Style: the bar and the air the
     * Style keeps under it, or 0 while the bar is switched off. A rule the activity hands in, since
     * the bar's own geometry is the status bar's.
     */
    public interface StatusBand {
        int px(boolean floating);
    }

    /**
     * What a half's rest reservation is read from, besides the snapshot. The half's Style and
     * whether its layout stands the status bar along the bottom are its own, read from the
     * snapshot's half, never from the half on screen.
     */
    public static final class RestInput {
        @NonNull public final DockGeometry.Snapshot dock;
        @NonNull public final Side side;
        public boolean toolbarShown;
        public boolean extraKeysRowEnabled;
        public int toolbarHeightPx;
        @NonNull public StatusBand statusBand = floating -> 0;
        public int imeLiftPx;
        /** Whether the half wants its keyboard up, and how tall it stands. */
        public boolean keyboardShown;
        public int keyboardHeightPx;
        public boolean keyboardOverlays;
        public int flushPaddingPx;

        public RestInput(@NonNull DockGeometry.Snapshot dock, @NonNull Side side) {
            this.dock = dock;
            this.side = side;
        }
    }

    /**
     * The room {@code side}'s chrome leaves the content at rest, built from the stack's parts the
     * way {@link #layout} builds them: its own dock rows in its own Style, its own status band,
     * the terminal's padding, the keyboard when it is up and the margin under all of that. Home's
     * chrome takes none.
     */
    public static int restReservationPx(@NonNull RestInput in) {
        DockGeometry.Half half = in.dock.half(in.side);
        DockLayout rest = DockGeometry.compute(in.dock.restOf(in.side));
        Base base = new Base(rest, half, half.layout, in.toolbarShown, in.keyboardShown,
            in.imeLiftPx);
        int statusBandPx = statusAlongTheBottom(half.layout) ? in.statusBand.px(half.floating) : 0;
        int dockContentPx = base.dockContentPx(rest, in.toolbarShown, in.toolbarHeightPx,
            in.extraKeysRowEnabled, statusBandPx);
        return KeyboardOverlayPolicy.restReservationPx(in.side == Side.HOME, dockContentPx,
            in.flushPaddingPx, in.keyboardShown, in.keyboardOverlays,
            Math.max(0, in.keyboardHeightPx), base.marginPx);
    }

    /**
     * The band of the widget pane {@code side}'s dock takes at rest, built the way {@link #layout}
     * builds {@link Result#homeGridDockReservePx}: its own dock rows in its own Style
     * ({@code rest}, {@code side}'s rest from {@code in}'s snapshot), its own status band and the
     * air under the keyboard, and the edge gap under them, while the stack shows. Only Home's dock
     * floats over a grid; every other side takes none.
     */
    public static int restGridDockReservePx(@NonNull RestInput in, @NonNull DockLayout rest) {
        DockGeometry.Half half = in.dock.half(in.side);
        Base base = new Base(rest, half, half.layout, in.toolbarShown, in.keyboardShown,
            in.imeLiftPx);
        int statusBandPx = statusAlongTheBottom(half.layout) ? in.statusBand.px(half.floating) : 0;
        int dockContentPx = base.dockContentPx(rest, in.toolbarShown, in.toolbarHeightPx,
            in.extraKeysRowEnabled, statusBandPx);
        boolean stackShown = DockRows.stackShown(in.toolbarShown, in.keyboardShown,
            statusBandPx > 0);
        return KeyboardOverlayPolicy.homeGridDockReservePx(in.side == Side.HOME && stackShown,
            dockContentPx, base.edgeGapPx);
    }

    /** Whether {@code layout} stands the status bar along the bottom, a band of the stack. */
    private static boolean statusAlongTheBottom(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.stack(layout, PlaceLayout.Edge.BOTTOM).contains(Element.STATUS);
    }
}
