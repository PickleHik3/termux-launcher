package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Side;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockRows;
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
        /** The dock's snapshot; its squeeze is ignored, the module works that out. */
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
        public boolean roundedDockStyle;
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

    /** The whole bottom stack for one pass. */
    public static final class Result {
        /** The dock the pass applies, squeezed as the stack needed. */
        @NonNull public DockLayout dock;
        /** How much the stack sheds from the apps row to fit under its ceiling; 0 when it fits. */
        public int squeezePx;
        /** The margin under the stack: the shared edge gap plus the IME's lift. */
        public int bottomMarginPx;
        /** The edge gap alone, the part of the margin the stack keeps whatever it holds. */
        public int stackEdgeGapPx;
        public boolean bandsUnderKeyboard;
        public int underKeyboardAirPx;
        public int underKeyboardTopMarginPx;
        public int underKeyboardBottomMarginPx;
        public int statusBandPx;
        /** The dock's rows with the status band and the air under the keyboard. */
        public int dockContentHeightPx;
        /** The terminal's dock padding, and the half of it under the rows. */
        public int flushPaddingPx;
        public int flushBottomInsetPx;
        /** The stack's height as the container is laid out. */
        public int stackHeightPx;
        public boolean stackShown;
        /** How far the content reaches past the stack's top, after Home and a slide's hold. */
        public int contentOverlapPx;
        /** The same for a side rail on Home: the stack and its margin. */
        public int sideStackOverlapPx;
        /** How much of the system keyboard's take the content reaches past (Home only). */
        public int homeImeReachPx;
        /** The gap the content keeps from the edge while the stack is away. */
        public int contentEdgeGapPx;
        /** The band of the widget pane the dock takes at rest. */
        public int homeGridDockReservePx;
        /** The room the chrome takes from the content. */
        public int contentReservationPx;
        /** What Home's floats are told: the dock's rows with the padding, the stack, the IME push. */
        public int homeFloatDockPx;
        public int homeFloatStackPx;
        public int homeFloatImePushPx;

        private Result(@NonNull DockLayout dock) {
            this.dock = dock;
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
        DockGeometry.Snapshot unsqueezed = in.dock.toBuilder().squeezePx(0).build();
        DockLayout probe = geometry.layout(unsqueezed);
        int lift = in.toolbarShown || in.keyboardShown ? in.imeLiftPx : 0;
        int edgeGapPx = ChromePolicy.bottomEdgeGapPx(in.roundedDockStyle, probe.capsuleBottomGapPx);
        int marginPx = lift + edgeGapPx;
        int ceilingPx = in.ceilingBeforeMarginPx == NO_CEILING ? NO_CEILING
            : Math.max(0, in.ceilingBeforeMarginPx - Math.max(0, marginPx));
        boolean bandsUnderKeyboard = in.toolbarShown
            && !EdgeStackPolicy.underKeyboard(in.dock.arrangement).isEmpty();
        int airPx = UnderKeyboardBand.airPx(probe, edgeGapPx, bandsUnderKeyboard);
        int overlapPx = KeyboardOverlayPolicy.contentOverlapPx(
            in.keyboardOverlays, in.keyboardShown, in.keyboardHeightPx);
        int projectedPx = stackHeightPx(
            probe.combinedHeight(in.toolbarHeightPx, in.extraKeysRowEnabled)
                + in.statusBandPx + airPx, 0, in.keyboardHeightPx);
        // The ceiling exists to keep a usable slice of content under the stack. A floating
        // keyboard takes none of that slice, so only what the stack really costs the content is
        // measured against it.
        int squeezePx = 0;
        DockLayout dock = probe;
        if (projectedPx - overlapPx > ceilingPx) {
            squeezePx = projectedPx - overlapPx - ceilingPx;
            dock = geometry.layout(in.dock.toBuilder().squeezePx(squeezePx).build());
        }

        Result r = new Result(dock);
        r.squeezePx = squeezePx;
        r.bottomMarginPx = marginPx;
        r.stackEdgeGapPx = edgeGapPx;
        r.bandsUnderKeyboard = bandsUnderKeyboard;
        r.underKeyboardAirPx = airPx;
        r.underKeyboardTopMarginPx = bandsUnderKeyboard ? UnderKeyboardBand.topGapPx(dock) : 0;
        r.underKeyboardBottomMarginPx = bandsUnderKeyboard
            ? UnderKeyboardBand.bottomAirPx(dock, edgeGapPx) : 0;
        r.statusBandPx = in.statusBandPx;
        r.dockContentHeightPx = (in.toolbarShown
            ? dock.combinedHeight(in.toolbarHeightPx, in.extraKeysRowEnabled) : 0)
            + in.statusBandPx + airPx;
        int accessoryContentPx = stackHeightPx(r.dockContentHeightPx, 0, in.keyboardHeightPx);
        r.flushPaddingPx = in.keyboardShown || !in.toolbarShown || !in.flushAllowed ? 0
            : in.flush.paddingPx(accessoryContentPx, marginPx);
        r.flushBottomInsetPx = r.flushPaddingPx / 2;
        r.stackHeightPx = stackHeightPx(r.dockContentHeightPx, r.flushPaddingPx,
            in.keyboardHeightPx);
        r.homeFloatDockPx = r.dockContentHeightPx + r.flushPaddingPx;
        r.homeFloatStackPx = r.stackHeightPx;
        r.homeFloatImePushPx = in.systemImeTakePx
            + (in.toolbarShown || in.keyboardShown ? Math.max(0, in.imeLiftPx) : 0);

        // On a Home page the dock floats with the keyboard, either keyboard: the grid keeps its
        // whole height.
        int imeReachPx = in.dockOverlays ? in.systemImeTakePx : 0;
        overlapPx = KeyboardOverlayPolicy.overlapPx(in.dockOverlays, r.stackHeightPx, marginPx,
            overlapPx, imeReachPx);
        r.sideStackOverlapPx = KeyboardOverlayPolicy.overlapPx(true, r.stackHeightPx, marginPx, 0,
            imeReachPx);
        // While the wall travels with chrome pre-rolled, the content keeps exactly the room it was
        // committed with: whatever the stack grew by for the slide, the content reaches under it
        // by the same amount.
        if (in.travelHoldsContent) {
            overlapPx = PlaceChromeTravel.heldOverlapPx(r.stackHeightPx, marginPx,
                in.heldReservationPx);
            imeReachPx = 0;
        }
        r.contentOverlapPx = overlapPx;
        r.homeImeReachPx = imeReachPx;
        r.stackShown = DockRows.stackShown(in.toolbarShown, in.keyboardShown,
            in.statusBandPx > 0);
        // With the stack away the content is the surface on the bottom edge and keeps the gap the
        // dock would.
        r.contentEdgeGapPx = r.stackShown ? 0 : marginPx;
        r.homeGridDockReservePx = KeyboardOverlayPolicy.homeGridDockReservePx(
            in.dockOverlays && r.stackShown, r.dockContentHeightPx, edgeGapPx);
        r.contentReservationPx = KeyboardOverlayPolicy.contentReservationPx(
            r.stackHeightPx, marginPx, overlapPx);
        return r;
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

    /** What a half's rest reservation is read from, besides the snapshot. */
    public static final class RestInput {
        @NonNull public final DockGeometry.Snapshot dock;
        @NonNull public final Side side;
        public boolean toolbarShown;
        public boolean extraKeysRowEnabled;
        public int toolbarHeightPx;
        public int statusBandPx;
        public boolean roundedDockStyle;
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
     * way {@link #layout} builds them: its own dock rows, the terminal's padding, the keyboard
     * when it is up and the margin under all of that. Home's chrome takes none.
     */
    public static int restReservationPx(@NonNull RestInput in) {
        DockLayout rest = DockGeometry.compute(in.dock.restOf(in.side));
        int edgeGapPx = ChromePolicy.bottomEdgeGapPx(in.roundedDockStyle,
            rest.capsuleBottomGapPx);
        boolean bands = in.toolbarShown
            && !EdgeStackPolicy.underKeyboard(in.dock.half(in.side).layout).isEmpty();
        int dockContentPx = (in.toolbarShown
            ? rest.combinedHeight(in.toolbarHeightPx, in.extraKeysRowEnabled) : 0)
            + in.statusBandPx + UnderKeyboardBand.airPx(rest, edgeGapPx, bands);
        int lift = in.toolbarShown || in.keyboardShown ? in.imeLiftPx : 0;
        return KeyboardOverlayPolicy.restReservationPx(in.side == Side.HOME, dockContentPx,
            in.flushPaddingPx, in.keyboardShown, in.keyboardOverlays,
            Math.max(0, in.keyboardHeightPx), lift + edgeGapPx);
    }
}
