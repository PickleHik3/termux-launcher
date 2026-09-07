package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.surfaces.SurfaceEditorScene.Handle;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

/**
 * What the surface editor offers for the arrangement on screen, and the room it parks in.
 *
 * <p>The band is the regression the fix is about: the editor was measuring the free room from the
 * status bar wherever it stood, so a bar along the bottom edge put the band's ceiling below its
 * floor and a bar standing in a column put it at the foot of the display. The band collapsed to
 * nothing, the card and the resting pill were parked there, and the editor opened onto nothing the
 * user could see.
 */
public class SurfaceEditorSceneTest {

    private static final int HOST = 2400;
    private static final int INSET_TOP = 96;
    private static final int MIN_BAND = 360;

    private static PlaceLayout layout(Edge edge, RowPlacement apps, RowPlacement keys) {
        return new PlaceLayout(edge, apps, false, keys, KeyboardMode.RESIZE, 4, 4);
    }

    /** Portrait as it ships: bar along the top, apps and keys on the dock. */
    private static SurfaceEditorScene portrait() {
        return SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM), false, true);
    }

    /** Landscape as the device review ran it: bar in a column, apps a rail, keys a column. */
    private static SurfaceEditorScene landscapeColumns() {
        return SurfaceEditorScene.of(
            layout(Edge.LEFT, RowPlacement.LEFT, RowPlacement.RIGHT), false, true);
    }

    // -------------------------------------------------------------------------------- the band

    @Test
    public void aTopBarIsTheCeilingAndTheDockIsTheFloor() {
        int[] band = portrait().freeBandPx(INSET_TOP, 0, 300, true, 2100, HOST, MIN_BAND);

        assertArrayEquals(new int[] {300, 2100}, band);
    }

    /**
     * The bug the user hit: a bar along the bottom sits straight on the dock, so its lower edge is
     * below the floor. Read as a ceiling it collapsed the band to nothing at the foot of the
     * screen; it is the floor instead, and the ceiling stays the system inset.
     */
    @Test
    public void aBottomBarIsTheFloorRatherThanTheCeiling() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.BOTTOM, RowPlacement.BOTTOM, RowPlacement.BOTTOM), false, true);

        int[] band = scene.freeBandPx(INSET_TOP, 1800, 2100, true, 2100, HOST, MIN_BAND);

        assertArrayEquals(new int[] {INSET_TOP, 1800}, band);
    }

    /** A column runs the display's whole length, and takes width from the card rather than room. */
    @Test
    public void aColumnBarLeavesTheBandAlone() {
        int[] band = landscapeColumns().freeBandPx(INSET_TOP, 0, HOST, true, HOST, HOST, MIN_BAND);

        assertArrayEquals(new int[] {INSET_TOP, HOST}, band);
    }

    /** Nothing on the accessory stack: the band runs to the foot of the host. */
    @Test
    public void anEmptyStackLeavesTheBandRunningToTheFoot() {
        int[] band = portrait().freeBandPx(INSET_TOP, 0, 300, true, HOST, HOST, MIN_BAND);

        assertArrayEquals(new int[] {300, HOST}, band);
    }

    /**
     * Chrome can leave less than the editor can use — a bar on the bottom edge with a keyboard up
     * under it. The card then overlaps a surface; it is never parked off the screen.
     */
    @Test
    public void aBandTooShortToUseIsGrownRatherThanCollapsed() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.BOTTOM, RowPlacement.BOTTOM, RowPlacement.BOTTOM), true, true);

        int[] band = scene.freeBandPx(1200, 1300, 1400, true, 1400, HOST, MIN_BAND);

        assertEquals(MIN_BAND, band[1] - band[0]);
        assertTrue("the band starts inside the host", band[0] >= 0);
        assertTrue("and ends inside it", band[1] <= HOST);
    }

    @Test
    public void aBandIsNeverInvertedAndNeverLeavesTheHost() {
        for (Edge edge : Edge.values()) {
            SurfaceEditorScene scene = SurfaceEditorScene.of(
                layout(edge, RowPlacement.BOTTOM, RowPlacement.BOTTOM), true, false);
            for (int barTop : new int[] {-500, 0, 900, HOST, HOST + 500}) {
                for (int stackTop : new int[] {-10, 0, 1200, HOST, HOST + 900}) {
                    int[] band = scene.freeBandPx(INSET_TOP, barTop, barTop + 200, true,
                        stackTop, HOST, MIN_BAND);
                    String where = edge + " bar at " + barTop + ", stack at " + stackTop;
                    assertTrue(where, band[0] >= 0);
                    assertTrue(where, band[1] <= HOST);
                    assertTrue(where, band[1] >= band[0]);
                }
            }
        }
    }

    /** A host with no height at all — before the first layout — still answers a usable band. */
    @Test
    public void anUnmeasuredHostAnswersAnEmptyBand() {
        int[] band = portrait().freeBandPx(INSET_TOP, 0, 300, true, 0, 0, MIN_BAND);

        assertArrayEquals(new int[] {0, 0}, band);
    }

    // ---------------------------------------------------------------------------- the surfaces

    @Test
    public void portraitOffersTheStatusBarTheDockAndTheTerminalButNotAClosedKeyboard() {
        SurfaceEditorScene scene = portrait();

        assertTrue(scene.offersSurface(null));
        assertTrue(scene.offersSurface(SurfaceSlot.STATUS));
        assertTrue(scene.offersSurface(SurfaceSlot.DOCK));
        assertTrue(scene.offersSurface(SurfaceSlot.CANVAS));
        assertFalse(scene.offersSurface(SurfaceSlot.KEYBOARD));
    }

    @Test
    public void aRaisedKeyboardIsASurfaceAndCarriesItsTwoHandles() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM), true, true);

        assertTrue(scene.offersSurface(SurfaceSlot.KEYBOARD));
        assertTrue(scene.offersHandle(Handle.KEYBOARD_HEIGHT));
        assertTrue(scene.offersHandle(Handle.KEYBOARD_CHIN));
    }

    @Test
    public void aClosedKeyboardCarriesNoHandles() {
        SurfaceEditorScene scene = portrait();

        assertFalse(scene.offersHandle(Handle.KEYBOARD_HEIGHT));
        assertFalse(scene.offersHandle(Handle.KEYBOARD_CHIN));
    }

    /** Landscape with everything in a column: there is no dock band, so there is no dock surface. */
    @Test
    public void aPlaceWithNothingOnTheDockDoesNotOfferTheDock() {
        SurfaceEditorScene scene = landscapeColumns();

        assertFalse(scene.offersSurface(SurfaceSlot.DOCK));
        assertFalse(scene.offersHandle(Handle.DOCK_HEIGHT));
        assertTrue(scene.offersSurface(SurfaceSlot.STATUS));
        assertTrue(scene.offersSurface(SurfaceSlot.CANVAS));
    }

    /**
     * The extra keys on the dock with the apps in a rail: the dock is a surface again, but the two
     * rows and the grip that move the pinned apps row's height and paging have nothing to move.
     */
    @Test
    public void aDockCarryingOnlyTheExtraKeysDropsTheAppsRowsAndItsGrip() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.LEFT, RowPlacement.LEFT, RowPlacement.BOTTOM), false, true);

        assertTrue(scene.offersSurface(SurfaceSlot.DOCK));
        assertFalse(scene.offersRow(SurfaceSlot.DOCK, SurfaceEditorProperties.ID_SIZE));
        assertFalse(scene.offersRow(SurfaceSlot.DOCK, SurfaceEditorProperties.ID_APPS));
        assertFalse(scene.offersHandle(Handle.DOCK_HEIGHT));
        // Its material and its shape are still the dock's own.
        for (String row : new String[] {SurfaceEditorProperties.ID_OPACITY,
            SurfaceEditorProperties.ID_BLUR, SurfaceEditorProperties.ID_GRAIN,
            SurfaceEditorProperties.ID_CORNERS, SurfaceEditorProperties.ID_MARGIN})
            assertTrue(row, scene.offersRow(SurfaceSlot.DOCK, row));
    }

    @Test
    public void aDockWithItsAppsRowKeepsEveryRowAndItsGrip() {
        SurfaceEditorScene scene = portrait();

        for (SurfaceEditorProperties.Control control
                : SurfaceEditorProperties.panel(SurfaceSlot.DOCK))
            assertTrue(control.id, scene.offersRow(SurfaceSlot.DOCK, control.id));
        assertTrue(scene.offersHandle(Handle.DOCK_HEIGHT));
    }

    /** No other surface's card is curated by the arrangement; their own state decides. */
    @Test
    public void theOtherSurfacesKeepEveryRowInEveryArrangement() {
        for (SurfaceEditorScene scene : new SurfaceEditorScene[] {portrait(), landscapeColumns()}) {
            for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.STATUS, SurfaceSlot.KEYBOARD,
                SurfaceSlot.CANVAS}) {
                for (SurfaceEditorProperties.Control control
                        : SurfaceEditorProperties.panel(slot))
                    assertTrue(slot + "/" + control.id, scene.offersRow(slot, control.id));
            }
            for (SurfaceEditorProperties.Control control : SurfaceEditorProperties.global())
                assertTrue(control.id, scene.offersRow(null, control.id));
        }
    }

    // ------------------------------------------------------------------------ where a card parks

    @Test
    public void onlyABarAlongTheTopIsStoodOffDownward() {
        assertTrue(portrait().surfaceIsAtTop(SurfaceSlot.STATUS));
        assertFalse(portrait().surfaceIsAtTop(SurfaceSlot.DOCK));
        assertFalse(portrait().surfaceIsAtTop(SurfaceSlot.KEYBOARD));
        assertFalse(portrait().surfaceIsAtTop(null));

        for (Edge edge : new Edge[] {Edge.BOTTOM, Edge.LEFT, Edge.RIGHT}) {
            SurfaceEditorScene scene = SurfaceEditorScene.of(
                layout(edge, RowPlacement.BOTTOM, RowPlacement.BOTTOM), false, true);
            assertFalse(edge.toString(), scene.surfaceIsAtTop(SurfaceSlot.STATUS));
        }
    }

    @Test
    public void aColumnBarIsRecognisedAsAColumn() {
        assertTrue(landscapeColumns().statusBarStandsInAColumn());
        assertFalse(portrait().statusBarStandsInAColumn());
    }

    // -------------------------------------------------------------------------- the signature

    @Test
    public void theSignatureMovesWithEveryInputItReads() {
        long portrait = portrait().signature();

        assertEquals(portrait, portrait().signature());
        assertTrue(portrait != landscapeColumns().signature());
        assertTrue(portrait != SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM), true, true).signature());
        assertTrue(portrait != SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM), false, false).signature());
        assertTrue(portrait != SurfaceEditorScene.of(
            layout(Edge.BOTTOM, RowPlacement.BOTTOM, RowPlacement.BOTTOM), false, true)
            .signature());
        assertTrue(portrait != SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.HIDDEN), false, true).signature());
    }
}
