package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

/** What the Appearance editor offers as tap targets for the arrangement on screen. */
public class SurfaceEditorSceneTest {

    private static PlaceLayout layout(Edge edge, RowPlacement apps, RowPlacement keys) {
        return new PlaceLayout(edge, apps, false, Edge.BOTTOM, keys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 4);
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
    public void aRaisedKeyboardIsASurfaceOfItsOwn() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM), true, true);

        assertTrue(scene.offersSurface(SurfaceSlot.KEYBOARD));
    }

    /** Landscape with everything in a column: there is no dock band, so there is no dock surface. */
    @Test
    public void aPlaceWithNothingOnTheDockDoesNotOfferTheDock() {
        SurfaceEditorScene scene = landscapeColumns();

        assertFalse(scene.offersSurface(SurfaceSlot.DOCK));
        assertTrue(scene.offersSurface(SurfaceSlot.STATUS));
        assertTrue(scene.offersSurface(SurfaceSlot.CANVAS));
    }

    /** The extra keys on the dock with the apps in a rail: the dock is a surface again. */
    @Test
    public void aDockCarryingOnlyTheExtraKeysIsStillADock() {
        SurfaceEditorScene scene = SurfaceEditorScene.of(
            layout(Edge.LEFT, RowPlacement.LEFT, RowPlacement.BOTTOM), false, true);

        assertTrue(scene.offersSurface(SurfaceSlot.DOCK));
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
