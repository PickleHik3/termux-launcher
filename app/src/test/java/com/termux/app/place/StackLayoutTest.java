package com.termux.app.place;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Side;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stack layout's seam, crossed the way the activity crosses it: one snapshot in, the whole bottom
 * stack out, asserted as the numbers the user sees (a height, a margin, how far the content
 * reaches under the stack). The cases the Robolectric bottom-stack tests held about the stack's
 * arithmetic live here; those tests keep the views' order.
 */
public class StackLayoutTest {

    private static final float DENSITY = 2.625f;
    private static final int WIDTH = Math.round(411 * DENSITY);
    private static final int TOOLBAR = 150;
    private static final int KEYBOARD = 700;
    private static final int LIFT = 40;

    /** A window: the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Home layout: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static Half half(PlaceLayout layout, boolean searchField) {
        return Half.builder(layout).floating(false).dockHeightScale(1.95f)
            .design(DockStyle.GLASS_TRAY).searchField(searchField).build();
    }

    private static Snapshot.Builder device(Half home, Half workspace) {
        return Snapshot.builder(home, workspace)
            .density(DENSITY).widthPx(WIDTH)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .pinnedSlotCount(7)
            .preferencesAvailable(true);
    }

    /** The Workspace at rest, a window with its extra keys row. */
    private static StackLayout.Input window(boolean keyboardShown) {
        Snapshot s = device(half(HOME, false), half(WINDOW, false))
            .rows(Side.WORKSPACE).arrangement(WINDOW).build();
        StackLayout.Input in = new StackLayout.Input(s);
        in.toolbarShown = true;
        in.extraKeysRowEnabled = true;
        in.toolbarHeightPx = TOOLBAR;
        in.keyboardShown = keyboardShown;
        in.keyboardHeightPx = keyboardShown ? KEYBOARD : 0;
        in.imeLiftPx = LIFT;
        return in;
    }

    private static StackLayout.Result run(StackLayout.Input in) {
        return StackLayout.layout(in, new DockGeometry());
    }

    // ---- bottom-stack order ----

    @Test
    public void theStackIsTheDocksRowsThenTheKeyboardAndTheMarginStandsUnderAllOfIt() {
        StackLayout.Input in = window(true);
        in.statusBandPx = 33;
        StackLayout.Result r = run(in);
        DockLayout dock = r.dock;
        assertEquals("the rows, with the status band", dock.combinedHeight(TOOLBAR, true) + 33,
            r.dockContentHeightPx);
        assertEquals("the stack is the rows plus the keyboard", r.dockContentHeightPx + KEYBOARD,
            r.stackHeightPx);
        assertEquals("the margin is the shared edge gap and the IME's lift",
            r.stackEdgeGapPx + LIFT, r.bottomMarginPx);
        assertTrue(r.stackShown);
    }

    @Test
    public void aStackWithNoRowsAndNoKeyboardIsAwayAndTheContentKeepsTheEdgeGap() {
        StackLayout.Input in = window(false);
        in.toolbarShown = false;
        StackLayout.Result r = run(in);
        assertEquals(0, r.dockContentHeightPx);
        assertEquals(0, r.stackHeightPx);
        assertEquals(false, r.stackShown);
        assertEquals("the content carries the gap the dock would", r.bottomMarginPx,
            r.contentEdgeGapPx);
    }

    @Test
    public void aBottomStatusBarKeepsTheStackUpOnItsOwn() {
        StackLayout.Input in = window(false);
        in.toolbarShown = false;
        in.statusBandPx = 50;
        StackLayout.Result r = run(in);
        assertTrue(r.stackShown);
        assertEquals(50, r.dockContentHeightPx);
        assertEquals(0, r.contentEdgeGapPx);
    }

    // ---- keyboard up and down ----

    @Test
    public void theKeyboardAddsItsHeightAndTheSameMarginHoldsUpAndDown() {
        StackLayout.Result up = run(window(true));
        StackLayout.Result down = run(window(false));
        assertEquals(KEYBOARD, up.stackHeightPx - down.stackHeightPx);
        assertEquals("the dock's rows are the same either way", down.dockContentHeightPx,
            up.dockContentHeightPx);
        assertEquals(down.bottomMarginPx, up.bottomMarginPx);
    }

    @Test
    public void theTerminalsDockPaddingIsLaidWithTheKeyboardDownAndStandsDownWithItUp() {
        StackLayout.Input down = window(false);
        down.flushAllowed = true;
        down.flush = (content, margin) -> 11;
        StackLayout.Result d = run(down);
        assertEquals(11, d.flushPaddingPx);
        assertEquals("the padding is the stack's, split around the rows", 5, d.flushBottomInsetPx);
        assertEquals(d.dockContentHeightPx + 11, d.stackHeightPx);

        StackLayout.Input up = window(true);
        up.flushAllowed = true;
        up.flush = (content, margin) -> 11;
        assertEquals(0, run(up).flushPaddingPx);

        StackLayout.Input notAllowed = window(false);
        notAllowed.flush = (content, margin) -> 11;
        assertEquals(0, run(notAllowed).flushPaddingPx);
    }

    @Test
    public void theFlushRuleIsAskedForTheRowsAndTheMarginItStandsAbove() {
        StackLayout.Input in = window(false);
        in.flushAllowed = true;
        int[] asked = new int[2];
        in.flush = (content, margin) -> {
            asked[0] = content;
            asked[1] = margin;
            return 0;
        };
        StackLayout.Result r = run(in);
        assertEquals(r.dockContentHeightPx, asked[0]);
        assertEquals(r.bottomMarginPx, asked[1]);
    }

    // ---- the squeeze against the ceiling ----

    @Test
    public void aStackThatFitsSheddsNothing() {
        StackLayout.Input in = window(true);
        in.ceilingBeforeMarginPx = 10_000;
        StackLayout.Result r = run(in);
        assertEquals(0, r.squeezePx);
        assertEquals(new DockGeometry().layout(in.dock.toBuilder().squeezePx(0).build())
            .combinedHeight(TOOLBAR, true), r.dock.combinedHeight(TOOLBAR, true));
    }

    @Test
    public void aStackOverTheCeilingShedsTheOverflowFromTheRowsAndNeverFromTheIcon() {
        StackLayout.Input roomy = window(true);
        StackLayout.Result free = run(roomy);

        StackLayout.Input tight = window(true);
        // The stack is 40 px over what the window leaves: the margin comes off the ceiling first.
        tight.ceilingBeforeMarginPx = free.stackHeightPx + free.bottomMarginPx - 40;
        StackLayout.Result r = run(tight);
        assertEquals(40, r.squeezePx);
        // The policy clamps a negative extra row height to zero (DockLayoutPolicy
        // iconBaselinePx), as it did before Dock geometry: the squeeze reaches the snapshot and
        // changes neither the icon nor the row's width.
        assertEquals("the icon does not give", free.dock.slotIconPx, r.dock.slotIconPx);
        assertEquals(free.dock.appsRowUsableWidthPx, r.dock.appsRowUsableWidthPx);
    }

    @Test
    public void aFloatingKeyboardTakesNoneOfTheContentsSliceSoOnlyWhatItCostsIsMeasured() {
        StackLayout.Input in = window(true);
        StackLayout.Result free = run(in);
        int ceiling = free.stackHeightPx + free.bottomMarginPx - 40;
        in.ceilingBeforeMarginPx = ceiling;

        StackLayout.Input floating = window(true);
        floating.ceilingBeforeMarginPx = ceiling;
        floating.keyboardOverlays = true;
        StackLayout.Result r = run(floating);
        assertEquals("the keyboard's own height is handed back before measuring", 0, r.squeezePx);
        assertEquals(KEYBOARD, r.contentOverlapPx);
    }

    @Test
    public void aSqueezeAsTheCallerSnapshotCarriesItIsWorkedOutAgainNotTrusted() {
        StackLayout.Input in = window(true);
        in.ceilingBeforeMarginPx = 10_000;
        StackLayout.Result free = run(in);
        StackLayout.Input stale = new StackLayout.Input(
            in.dock.toBuilder().squeezePx(90).build());
        stale.toolbarShown = true;
        stale.extraKeysRowEnabled = true;
        stale.toolbarHeightPx = TOOLBAR;
        stale.keyboardShown = true;
        stale.keyboardHeightPx = KEYBOARD;
        stale.imeLiftPx = LIFT;
        stale.ceilingBeforeMarginPx = 10_000;
        assertEquals(free.stackHeightPx, run(stale).stackHeightPx);
    }

    // ---- overlap ----

    @Test
    public void onTheWorkspaceTheKeyboardIsTheOnlyThingTheContentReachesUnder() {
        StackLayout.Input in = window(true);
        assertEquals("a keyboard that resizes the content takes its room", 0,
            run(in).contentOverlapPx);
        in.keyboardOverlays = true;
        assertEquals("one that floats is reached under by its height", KEYBOARD,
            run(in).contentOverlapPx);
    }

    @Test
    public void onHomeTheContentReachesUnderTheWholeStackAndItsMargin() {
        StackLayout.Input in = window(true);
        in.dockOverlays = true;
        in.keyboardOverlays = true;
        in.systemImeTakePx = 25;
        StackLayout.Result r = run(in);
        assertEquals(r.stackHeightPx + r.bottomMarginPx + 25, r.contentOverlapPx);
        assertEquals(25, r.homeImeReachPx);
        assertEquals("the side rail ends above the dock as on the Workspace", r.contentOverlapPx,
            r.sideStackOverlapPx);
        assertEquals("a Home page's chrome takes no room", 0, r.contentReservationPx);
        assertEquals("the grid ends above the rows and the gap under them",
            r.dockContentHeightPx + r.stackEdgeGapPx, r.homeGridDockReservePx);
        assertEquals(r.dockContentHeightPx + r.flushPaddingPx, r.homeFloatDockPx);
        assertEquals(25 + LIFT, r.homeFloatImePushPx);
    }

    @Test
    public void theWorkspaceReservesTheStackAndItsMarginLessWhatTheKeyboardHandsBack() {
        StackLayout.Input in = window(true);
        StackLayout.Result r = run(in);
        assertEquals(r.stackHeightPx + r.bottomMarginPx, r.contentReservationPx);
        in.keyboardOverlays = true;
        StackLayout.Result floated = run(in);
        assertEquals(r.contentReservationPx - KEYBOARD, floated.contentReservationPx);
    }

    // ---- reservation during travel ----

    @Test
    public void whileASlideHoldsTheContentItKeepsExactlyTheRoomItWasCommittedWith() {
        StackLayout.Input in = window(true);
        in.travelHoldsContent = true;
        in.heldReservationPx = 300;
        in.dockOverlays = true;
        in.systemImeTakePx = 25;
        StackLayout.Result r = run(in);
        assertEquals("the stack grew; the content reaches under it by the difference",
            r.stackHeightPx + r.bottomMarginPx - 300, r.contentOverlapPx);
        assertEquals(300, r.contentReservationPx);
        assertEquals("a slide holds no system keyboard reach", 0, r.homeImeReachPx);
        assertEquals("the side rail still ends above the dock",
            r.stackHeightPx + r.bottomMarginPx + 25, r.sideStackOverlapPx);
    }

    // ---- a half's rest, and a window not shown during a slide ----

    private static StackLayout.RestInput rest(Snapshot s, Side side) {
        StackLayout.RestInput in = new StackLayout.RestInput(s, side);
        in.toolbarShown = true;
        in.extraKeysRowEnabled = true;
        in.toolbarHeightPx = TOOLBAR;
        in.imeLiftPx = LIFT;
        in.keyboardHeightPx = KEYBOARD;
        return in;
    }

    /** The two halves, Home carrying a Search field, committed to {@code committed}. */
    private static Snapshot committedTo(Side committed, float fieldReveal) {
        PlaceLayout arrangement = committed == Side.HOME ? HOME : WINDOW;
        return device(half(HOME, true), half(WINDOW, false))
            .rows(committed).arrangement(arrangement).fieldReveal(fieldReveal).build();
    }

    @Test
    public void aHalfWithASearchFieldRestsTheSameWhicheverHalfIsCommitted() {
        // The live field ease is the committed half's: 1 on Home, 0 in the window.
        int measuredFromTheWindow = StackLayout.restDockRowsPx(committedTo(Side.WORKSPACE, 0f),
            Side.HOME);
        int measuredFromHome = StackLayout.restDockRowsPx(committedTo(Side.HOME, 1f), Side.HOME);
        assertEquals(measuredFromHome, measuredFromTheWindow);
        // The field is part of that rest: the same half without it is shorter.
        Snapshot noField = device(half(HOME, false), half(WINDOW, false))
            .rows(Side.WORKSPACE).arrangement(WINDOW).build();
        assertTrue(measuredFromHome > StackLayout.restDockRowsPx(noField, Side.HOME));
    }

    @Test
    public void theWindowRestsTheSameWhileHomeIsCommittedWithItsFieldUp() {
        int fromWindow = StackLayout.restDockRowsPx(committedTo(Side.WORKSPACE, 0f),
            Side.WORKSPACE);
        int fromHome = StackLayout.restDockRowsPx(committedTo(Side.HOME, 1f), Side.WORKSPACE);
        assertEquals("a window the slide does not show has no field under it", fromWindow,
            fromHome);
    }

    @Test
    public void aWindowNotShownDuringASlideMeasuresTheSameAsTheOneShown() {
        // The pane gap: the window's room was read off the stack Home's rows made, 213 px short.
        StackLayout.RestInput shown = rest(committedTo(Side.WORKSPACE, 0f), Side.WORKSPACE);
        StackLayout.RestInput notShown = rest(committedTo(Side.HOME, 1f), Side.WORKSPACE);
        assertTrue(StackLayout.restReservationPx(shown) > 0);
        assertEquals(StackLayout.restReservationPx(shown),
            StackLayout.restReservationPx(notShown));
        shown.keyboardShown = true;
        notShown.keyboardShown = true;
        assertEquals(StackLayout.restReservationPx(shown),
            StackLayout.restReservationPx(notShown));
    }

    @Test
    public void aRestReservationIsTheStackAPassLaysOutAtRest() {
        StackLayout.RestInput in = rest(committedTo(Side.WORKSPACE, 0f), Side.WORKSPACE);
        StackLayout.Result laid = run(window(false));
        assertEquals(laid.contentReservationPx, StackLayout.restReservationPx(in));
        in.keyboardShown = true;
        assertEquals(run(window(true)).contentReservationPx, StackLayout.restReservationPx(in));
    }

    @Test
    public void homesChromeTakesNoRoomAtRest() {
        assertEquals(0, StackLayout.restReservationPx(
            rest(committedTo(Side.HOME, 1f), Side.HOME)));
    }
}
