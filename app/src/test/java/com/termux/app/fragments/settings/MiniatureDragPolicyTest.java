package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Slot;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Targets;
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
 * Where a bar lifted off the Layout page's miniature may be dropped: every bar, both orientations,
 * and both states of the pinned apps row, which is what decides whether the A&#8211;Z index has an
 * edge of its own to be dragged to at all.
 *
 * <p>The expected sets are the per-place layout model's own table, so a drift here is a drag that
 * offers a placement the chooser pills do not — or withholds one they do.
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
        return new ArrayList<>(targets.edges);
    }

    @Test
    public void theStatusBarMovesBetweenEdgesAndNeverHides() {
        for (RowPlacement appsRow : RowPlacement.values()) {
            Targets portrait = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout(appsRow));
            assertEquals("portrait has no width for a column",
                Arrays.asList(Edge.TOP, Edge.BOTTOM), edges(portrait));
            assertFalse("the status bar is never hidden", portrait.tray);

            Targets landscape =
                targets(Bar.STATUS_BAR, PlaceOrientation.LANDSCAPE, layout(appsRow));
            assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT),
                edges(landscape));
            assertFalse(landscape.tray);
        }
    }

    @Test
    public void aRowStandsAlongTheBottomOrDownASideAndMayHide() {
        for (Bar bar : new Bar[]{Bar.APPS_ROW, Bar.EXTRA_KEYS}) {
            for (RowPlacement appsRow : RowPlacement.values()) {
                Targets portrait = targets(bar, PlaceOrientation.PORTRAIT, layout(appsRow));
                assertEquals(bar + " portrait: the bottom, or away",
                    Arrays.asList(Edge.BOTTOM), edges(portrait));
                assertTrue(bar + " may hide", portrait.tray);

                Targets landscape = targets(bar, PlaceOrientation.LANDSCAPE, layout(appsRow));
                assertEquals(bar + " landscape gains both sides",
                    Arrays.asList(Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), edges(landscape));
                assertTrue(landscape.tray);
                assertFalse(landscape.isEmpty());
            }
        }
    }

    @Test
    public void theAzIndexRidingThePinnedAppsCanOnlyBePutAway() {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            Targets riding = targets(Bar.AZ_INDEX, orientation, layout(RowPlacement.BOTTOM));
            assertTrue("it goes where the pinned apps go", riding.edges.isEmpty());
            assertTrue("hiding it is the one thing a drag can do", riding.tray);
            assertFalse(riding.isEmpty());
        }
    }

    @Test
    public void theAzIndexGetsEdgesOfItsOwnOnceThePinnedAppsAreOffTheBottom() {
        for (RowPlacement standingAlone
            : new RowPlacement[]{RowPlacement.HIDDEN, RowPlacement.LEFT, RowPlacement.RIGHT}) {
            Targets portrait =
                targets(Bar.AZ_INDEX, PlaceOrientation.PORTRAIT, layout(standingAlone));
            assertEquals("apps row " + standingAlone + ": portrait edges",
                Arrays.asList(Edge.TOP, Edge.BOTTOM), edges(portrait));
            assertTrue(portrait.tray);

            Targets landscape =
                targets(Bar.AZ_INDEX, PlaceOrientation.LANDSCAPE, layout(standingAlone));
            assertEquals("apps row " + standingAlone + ": landscape edges",
                Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), edges(landscape));
            assertTrue(landscape.tray);
        }
    }

    @Test
    public void anAlreadyHiddenAzIndexIsOfferedTheSameEdgesItWouldStandOn() {
        // A chip in the tray is lifted by the same grip and dropped on the same slots; the stored
        // shown/hidden switch is not what decides where it may go.
        Targets hidden = targets(Bar.AZ_INDEX, PlaceOrientation.LANDSCAPE,
            layout(RowPlacement.HIDDEN, false));
        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), edges(hidden));
        assertTrue(hidden.tray);
    }

    @Test
    public void theOfferedEdgesAreTheOnesTheTargetsReportOffering() {
        Targets landscape =
            targets(Bar.APPS_ROW, PlaceOrientation.LANDSCAPE, layout(RowPlacement.BOTTOM));
        assertTrue(landscape.offers(Edge.LEFT));
        assertFalse("a row has no top position", landscape.offers(Edge.TOP));
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
}
