package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Slot;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Targets;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a bar lifted off the Layout editor's miniature may be dropped: every bar, both
 * orientations, and both states of the pinned apps row, which is what decides whether the
 * A&#8211;Z index has an edge of its own to be dragged to at all.
 *
 * <p>The expected sets are the edge-stack model's own table, so a drift here is a drag that offers
 * a placement the picture does not draw — or withholds one it does. The gap counts are the same
 * question asked of one edge: a stack of bands has one more gap than it has bands.
 */
public class MiniatureDragPolicyTest {

    private static PlaceLayout layout(RowPlacement appsRow) {
        return layout(appsRow, true);
    }

    private static PlaceLayout layout(RowPlacement appsRow, boolean azShown) {
        return new PlaceLayout(Edge.TOP, appsRow, azShown, Edge.BOTTOM, RowPlacement.BOTTOM,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static Targets targets(Bar bar, PlaceOrientation orientation, PlaceLayout layout) {
        return MiniatureDragPolicy.targets(PaneWallPage.TERMINAL, orientation, layout, bar);
    }

    private static List<Edge> edges(Targets targets) {
        return new ArrayList<>(targets.edges());
    }

    private static final List<Edge> EVERY_EDGE =
        Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);

    @Test
    public void everyBarIsNamedForAnElementAndBackAgain() {
        for (Bar bar : Bar.values()) assertSame(bar, Bar.of(bar.element()));
        assertSame(Element.AZ, Bar.AZ_INDEX.element());
    }

    @Test
    public void theStatusBarMovesBetweenEdgesAndNeverHides() {
        for (RowPlacement appsRow : RowPlacement.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                Targets offered = targets(Bar.STATUS_BAR, orientation, layout(appsRow));
                assertEquals(orientation + ": a column down the side is allowed in both",
                    EVERY_EDGE, edges(offered));
                assertFalse("the status bar is never hidden", offered.tray);
            }
        }
    }

    @Test
    public void aRowStandsOnAnyEdgeInEitherOrientationAndMayHide() {
        for (Bar bar : new Bar[]{Bar.APPS_ROW, Bar.EXTRA_KEYS}) {
            for (RowPlacement appsRow : RowPlacement.values()) {
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    Targets offered = targets(bar, orientation, layout(appsRow));
                    assertEquals(bar + " " + orientation, EVERY_EDGE, edges(offered));
                    assertTrue(bar + " may hide", offered.tray);
                    assertFalse(offered.isEmpty());
                }
            }
        }
    }

    @Test
    public void anEdgeOffersOneMoreGapThanItHasBandsTheLiftedBarIsNot() {
        // The default arrangement: the status bar along the top, and the extra keys, the A-Z index
        // and the pinned apps stacked along the bottom.
        PlaceLayout layout = layout(RowPlacement.BOTTOM);

        Targets keys = targets(Bar.EXTRA_KEYS, PlaceOrientation.PORTRAIT, layout);
        assertEquals("lifting the keys leaves two bands down there, so three gaps",
            3, keys.gapsOn(Edge.BOTTOM));
        assertEquals("the status bar is the top's only band", 2, keys.gapsOn(Edge.TOP));
        assertEquals("both sides are bare", 1, keys.gapsOn(Edge.LEFT));
        assertEquals(1, keys.gapsOn(Edge.RIGHT));

        Targets status = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        assertEquals("three bands down there and the status bar is none of them",
            4, status.gapsOn(Edge.BOTTOM));
        assertEquals("its own edge holds only itself", 1, status.gapsOn(Edge.TOP));
        assertTrue(status.offers(Edge.LEFT));
    }

    @Test
    public void theAzIndexRidingThePinnedAppsCanOnlyBePutAway() {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            Targets riding = targets(Bar.AZ_INDEX, orientation, layout(RowPlacement.BOTTOM));
            assertTrue("it goes where the pinned apps go", riding.drops.isEmpty());
            assertTrue("hiding it is the one thing a drag can do", riding.tray);
            assertFalse(riding.isEmpty());
        }
    }

    @Test
    public void aHiddenAzIndexUnderThePinnedAppsComesBackToTheBottomOnly() {
        // Its chip in the tray must have somewhere to go; the bottom is where it rides.
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            Targets hidden = targets(Bar.AZ_INDEX, orientation, layout(RowPlacement.BOTTOM, false));
            assertEquals(orientation + ": back under the pinned apps",
                Arrays.asList(Edge.BOTTOM), edges(hidden));
            assertEquals(1, hidden.drops.size());
            assertTrue(hidden.tray);
        }
    }

    @Test
    public void theAzIndexGetsEdgesOfItsOwnOnceThePinnedAppsAreOffTheBottom() {
        for (RowPlacement standingAlone
            : new RowPlacement[]{RowPlacement.HIDDEN, RowPlacement.LEFT, RowPlacement.RIGHT}) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                Targets offered =
                    targets(Bar.AZ_INDEX, orientation, layout(standingAlone));
                assertEquals("apps row " + standingAlone + ", " + orientation,
                    EVERY_EDGE, edges(offered));
                assertTrue(offered.tray);
            }
        }
    }

    @Test
    public void anAlreadyHiddenAzIndexIsOfferedTheSameEdgesItWouldStandOn() {
        // A chip in the tray is lifted by the same grip and dropped on the same slots; the stored
        // shown/hidden switch is not what decides where it may go.
        Targets hidden = targets(Bar.AZ_INDEX, PlaceOrientation.LANDSCAPE,
            layout(RowPlacement.HIDDEN, false));
        assertEquals(EVERY_EDGE, edges(hidden));
        assertTrue(hidden.tray);
    }

    // ---- How much width the canvas keeps --------------------------------------------------------

    private static PlaceLayout sideStack(Edge statusEdge, RowPlacement appsRow, boolean azShown,
                                         Edge azEdge, RowPlacement extraKeys) {
        return new PlaceLayout(statusEdge, appsRow, azShown, azEdge, extraKeys,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    @Test
    public void aCanvasWithNothingDownItsSidesKeepsTheWholeWidth() {
        assertEquals(1f, MiniatureDragPolicy.canvasWidthFraction(layout(RowPlacement.BOTTOM)),
            0.0001f);
        assertFalse(MiniatureDragPolicy.warnsNarrowCanvas(layout(RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT));
    }

    @Test
    public void oneColumnDownEachSideIsStillRoomEnoughToSayNothing() {
        // Two rows standing as columns take 15% of what is beside them each: 72% of the width is
        // left, which is above the line the old portrait refusal is replaced by.
        PlaceLayout two = sideStack(Edge.TOP, RowPlacement.LEFT, false, Edge.BOTTOM,
            RowPlacement.RIGHT);
        assertEquals(0.7225f, MiniatureDragPolicy.canvasWidthFraction(two), 0.0001f);
        assertFalse(MiniatureDragPolicy.warnsNarrowCanvas(two, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aPortraitCanvasUnderThreeFifthsOfTheWidthIsWorthALine() {
        // The status bar, the pinned apps, the extra keys and the A-Z index all standing down the
        // left: 59% of the width left, and the editor says so.
        PlaceLayout four = sideStack(Edge.LEFT, RowPlacement.LEFT, true, Edge.LEFT,
            RowPlacement.LEFT);
        assertTrue(MiniatureDragPolicy.canvasWidthFraction(four)
            < MiniatureDragPolicy.NARROW_CANVAS_FRACTION);
        assertTrue(MiniatureDragPolicy.warnsNarrowCanvas(four, PlaceOrientation.PORTRAIT));
        assertFalse("landscape has the width to spare",
            MiniatureDragPolicy.warnsNarrowCanvas(four, PlaceOrientation.LANDSCAPE));

        // One band fewer and it is not worth a line.
        PlaceLayout three = sideStack(Edge.LEFT, RowPlacement.LEFT, false, Edge.BOTTOM,
            RowPlacement.LEFT);
        assertFalse(MiniatureDragPolicy.warnsNarrowCanvas(three, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aBandCostsTheSameWhicheverSideItStandsOn() {
        PlaceLayout left = sideStack(Edge.TOP, RowPlacement.LEFT, false, Edge.BOTTOM,
            RowPlacement.HIDDEN);
        PlaceLayout right = sideStack(Edge.TOP, RowPlacement.RIGHT, false, Edge.BOTTOM,
            RowPlacement.HIDDEN);
        assertEquals(MiniatureDragPolicy.canvasWidthFraction(left),
            MiniatureDragPolicy.canvasWidthFraction(right), 0.0001f);
    }

    // ---- Hit-testing ---------------------------------------------------------------------------

    private static final Slot TOP = new Slot(Edge.TOP, 0f, 0f, 100f, 20f);
    private static final Slot BOTTOM = new Slot(Edge.BOTTOM, 0f, 80f, 100f, 100f);
    private static final Slot LEFT = new Slot(Edge.LEFT, 0f, 0f, 20f, 100f);
    private static final Slot TRAY = new Slot(null, 0f, 120f, 100f, 150f);

    private static List<Slot> slots() {
        return Arrays.asList(TOP, BOTTOM, LEFT, TRAY);
    }

    @Test
    public void theSlotUnderTheFingerIsTheOneItIsInside() {
        assertSame(TOP, MiniatureDragPolicy.slotUnder(slots(), 60f, 10f));
        assertSame(BOTTOM, MiniatureDragPolicy.slotUnder(slots(), 60f, 90f));
        assertSame(LEFT, MiniatureDragPolicy.slotUnder(slots(), 10f, 50f));
        assertSame(TRAY, MiniatureDragPolicy.slotUnder(slots(), 50f, 130f));
        assertTrue("the tray is what hides a bar", TRAY.isTray());
        assertFalse(TOP.isTray());
    }

    @Test
    public void aFingerInTheCornerTwoSlotsShareTakesTheNearerOne() {
        // (10, 10) is inside both the top strip and the left column; the top strip's centre is
        // nearer, so that is what a drop there means.
        assertSame(TOP, MiniatureDragPolicy.slotUnder(slots(), 10f, 5f));
        assertSame(LEFT, MiniatureDragPolicy.slotUnder(slots(), 5f, 14f));
    }

    @Test
    public void aFingerOnNoSlotIsADropOnNothing() {
        assertNull(MiniatureDragPolicy.slotUnder(slots(), 60f, 50f));
        assertNull(MiniatureDragPolicy.slotUnder(slots(), 60f, 110f));
        assertNull(MiniatureDragPolicy.slotUnder(new ArrayList<>(), 60f, 10f));
    }

    @Test
    public void aGapKnowsWhichBandItWouldPutTheBarAbove() {
        // The rectangle is what a finger is tested against; the line is where the band would land.
        Slot gap = new Slot(Edge.BOTTOM, 2, 88f, 0f, 80f, 100f, 96f);
        assertEquals(2, gap.index);
        assertEquals(88f, gap.line, 0.0001f);
        assertFalse(gap.isTray());
    }
}
