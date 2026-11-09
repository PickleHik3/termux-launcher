package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.SeamDress;
import com.termux.app.place.SeamTravel;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.place.Slot;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * The dock's placement: the Floating Style places it as the Floating card, except while the Peek
 * glass card stands, which rises flush from the screen's edge; Docked it is always flush. And the
 * card stands only where every fact it needs holds.
 */
public class DockPlacementTest {

    /** Home: the pinned apps alone along the bottom, the row the card stands for. */
    private static final PlaceLayout APPS_ALONE = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A window: the extra keys share the dock's glass with the apps. */
    private static final PlaceLayout WITH_EXTRA_KEYS = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.BOTTOM,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    /** The developer's Home: the apps row with the A–Z row under it on the dock (the default order). */
    private static final PlaceLayout APPS_OVER_AZ = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** The A–Z row ordered over the apps row. */
    private static final PlaceLayout AZ_OVER_APPS = APPS_OVER_AZ
        .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, 5));
    private static final PlaceLayout APPS_AZ_AND_KEYS = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    private static final PlaceLayout RAIL = new PlaceLayout(Edge.TOP, RowPlacement.LEFT,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    private static final PlaceLayout AZ_ON_TOP = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.TOP, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    @Test
    public void theCardStandsWithTheAzRowOnItUnderTheIcons() {
        assertTrue(stands(APPS_OVER_AZ));
        assertTrue(DockPlacement.peekCardCarriesAz(APPS_OVER_AZ, false));
        assertFalse(DockPlacement.peekCardCarriesAz(APPS_ALONE, false));
        // Hidden A–Z, or the A–Z row on another edge: as before, the row alone.
        assertTrue(stands(AZ_ON_TOP));
        assertFalse(DockPlacement.peekCardCarriesAz(AZ_ON_TOP, false));
    }

    @Test
    public void theCardGivesWayToAnAzRowOverTheIconsAndToEveryOtherBand() {
        assertFalse("the letters would stand outside the card", stands(AZ_OVER_APPS));
        assertFalse(DockPlacement.peekCardCarriesAz(AZ_OVER_APPS, false));
        assertFalse("extra keys on the bottom", stands(APPS_AZ_AND_KEYS));
        assertFalse("a rail", stands(RAIL));
        assertFalse("a joined frame", DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, true,
            false, false, false));
    }

    @Test
    public void withTheAzRowTheKeyboardIMEAndSearchFieldRuleStandsAsBefore() {
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, true, false,
            false));
        assertTrue("Home: the card stands on the keyboard", DockPlacement.peekCardStands(true,
            true, APPS_OVER_AZ, false, true, false, false, true));
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, false, true,
            false, true));
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, false, false,
            true, true));
    }

    @Test
    public void lensBeadsStillTakeTheAzRowsGlass() {
        assertFalse(LensBeadGeometry.rowWearsBeads(APPS_OVER_AZ, false, false));
        assertFalse(LensBeadGeometry.rowWearsBeads(AZ_OVER_APPS, false, false));
        assertTrue(LensBeadGeometry.rowWearsBeads(APPS_ALONE, false, false));
    }

    private static boolean stands(PlaceLayout layout) {
        return DockPlacement.peekCardStands(true, true, layout, false, false, false, false);
    }

    @Test
    public void theCardStandsForTheAppsRowAloneWithNothingInItsWay() {
        assertTrue(LensBeadGeometry.rowWearsBeads(APPS_ALONE, false, false));
        assertTrue(stands(APPS_ALONE));
    }

    @Test
    public void everyFactTheCardNeedsRefusesItOnItsOwn() {
        assertFalse("another design",
            DockPlacement.peekCardStands(false, true, APPS_ALONE, false, false, false, false));
        assertFalse("the dock away",
            DockPlacement.peekCardStands(true, false, APPS_ALONE, false, false, false, false));
        assertFalse("standing down on a joined frame",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, true, false, false, false));
        assertFalse("the keyboard up",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, true, false, false));
        assertFalse("lifted over the system keyboard",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, true, false));
        assertFalse("the Search field under the row",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, false, true));
        assertFalse("another band on the dock's glass", stands(WITH_EXTRA_KEYS));
    }

    @Test
    public void floatingTheDockIsTheFloatingCardUnlessThePeekCardStands() {
        assertTrue(DockPlacement.floats(true, false));
        assertFalse("the card rises from the screen's edge", DockPlacement.floats(true, true));
    }

    @Test
    public void dockedTheDockIsAlwaysFlush() {
        assertFalse(DockPlacement.floats(false, false));
        assertFalse(DockPlacement.floats(false, true));
    }

    @Test
    public void theKeyboardComingUpHandsTheFloatingTrayBack() {
        boolean down = DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, false,
            false);
        boolean up = DockPlacement.peekCardStands(true, true, APPS_ALONE, false, true, false,
            false);
        assertFalse(DockPlacement.floats(true, down));
        assertTrue(DockPlacement.floats(true, up));
    }

    // ---- The Peek answer a crossing travels with, and the one its settle commits ----

    /** The terminal's keyboard is up on the Workspace and stays up through the slide. */
    private static final boolean WORKSPACE_KEYBOARD_UP = true;

    /**
     * The Peek answer on a frame of a crossing from {@code committed} toward {@code other}, at
     * {@code progress} into the other half: the rows the stack stands there (SeamDress#stackPage,
     * the page the activity's stackDressPage reads), laid out by that page's rows, read for that
     * page. The keyboard is the one the activity reads, still up from the Workspace.
     */
    private static boolean travelAnswer(PaneWallPage committed, PlaceLayout committedLayout,
                                        PaneWallPage other, PlaceLayout otherLayout,
                                        float progress) {
        DockPlan plan = SeamTravel.dockPlan(committedLayout, otherLayout);
        PaneWallPage rowsPage = SeamDress.stackPage(committed, other, plan, progress);
        PlaceLayout standing = rowsPage == committed ? committedLayout
            : SeamTravel.withDockOf(committedLayout, otherLayout);
        return DockPlacement.peekCardStandsOn(rowsPage, true, true, standing, false,
            WORKSPACE_KEYBOARD_UP, false, false);
    }

    /** The answer the settle commits on {@code page}, at rest there with its own layout. */
    private static boolean settleAnswer(PaneWallPage page, PlaceLayout layout,
                                        boolean keyboardShown) {
        return DockPlacement.peekCardStandsOn(page, true, true, layout, false, keyboardShown,
            false, false);
    }

    @Test
    public void workspaceToHomeTheRowsADipBringsInWearHomesPeekBeforeTheLanding() {
        assertEquals(DockPlan.DIP, SeamTravel.dockPlan(WITH_EXTRA_KEYS, APPS_OVER_AZ));
        // The Workspace's tray up to the swap, where none of the dock shows.
        assertFalse(travelAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS, PaneWallPage.WIDGETS,
            APPS_OVER_AZ, 0.25f));
        // From the swap Home's rows rise already wearing Peek, though the Workspace's keyboard is
        // still up: Home's card stands on it (D11), and the landing on Home commits the same.
        boolean travel = travelAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS,
            PaneWallPage.WIDGETS, APPS_OVER_AZ, SeamTravel.DIP_SWAP);
        assertTrue(travel);
        assertEquals("one answer, travelled and settled", travel,
            settleAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, false));
        assertEquals(travel, settleAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, true));
        assertEquals(travel, travelAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS,
            PaneWallPage.WIDGETS, APPS_OVER_AZ, 1f));
        // Read off the committed page, as it was, the keyboard refused the card all the way.
        assertFalse(DockPlacement.peekCardStands(true, true,
            SeamTravel.withDockOf(WITH_EXTRA_KEYS, APPS_OVER_AZ), false, WORKSPACE_KEYBOARD_UP,
            false, false, false));
    }

    @Test
    public void homeToWorkspaceTheRowsADipBringsInWearTheWorkspacesTray() {
        assertEquals(DockPlan.DIP, SeamTravel.dockPlan(APPS_OVER_AZ, WITH_EXTRA_KEYS));
        // Home's Peek up to the swap, with the Workspace's keyboard pre-rolled under it or not.
        assertTrue(travelAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, PaneWallPage.TERMINAL,
            WITH_EXTRA_KEYS, 0.25f));
        boolean travel = travelAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, PaneWallPage.TERMINAL,
            WITH_EXTRA_KEYS, SeamTravel.DIP_SWAP);
        assertFalse(travel);
        assertEquals(travel, settleAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS,
            WORKSPACE_KEYBOARD_UP));
    }

    @Test
    public void aSlideThatFallsBackWearsTheLookItStartedWith() {
        // Past the swap and back under it: the committed rows again, the committed answer.
        assertTrue(travelAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS, PaneWallPage.WIDGETS,
            APPS_OVER_AZ, 0.7f));
        boolean back = travelAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS,
            PaneWallPage.WIDGETS, APPS_OVER_AZ, 0.3f);
        assertFalse(back);
        assertEquals(settleAnswer(PaneWallPage.TERMINAL, WITH_EXTRA_KEYS, WORKSPACE_KEYBOARD_UP),
            back);
        assertFalse(travelAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, PaneWallPage.TERMINAL,
            WITH_EXTRA_KEYS, 0.7f));
        assertEquals(settleAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, false),
            travelAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, PaneWallPage.TERMINAL,
                WITH_EXTRA_KEYS, 0f));
    }

    @Test
    public void oneLayoutOnBothHalvesKeepsTheCommittedAnswerToTheLanding() {
        // No rows travel, so nothing of the dock goes out of sight: the Workspace's keyboard
        // alone refuses the card there, and the look changes as the slide lands.
        assertEquals(DockPlan.NONE, SeamTravel.dockPlan(APPS_OVER_AZ, APPS_OVER_AZ));
        assertFalse(travelAnswer(PaneWallPage.TERMINAL, APPS_OVER_AZ, PaneWallPage.WIDGETS,
            APPS_OVER_AZ, 0.99f));
        assertTrue(settleAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, false));
    }

    @Test
    public void aFoldOutNeverStandsHomesRowsSoItsLookChangesAtTheLanding() {
        // Home's rows plus the extra keys on the Workspace: the extra keys fold away in sight and
        // the stack stands the Workspace's rows to the landing, which alone brings Peek.
        assertEquals(DockPlan.FOLD_OUT, SeamTravel.dockPlan(APPS_AZ_AND_KEYS, APPS_OVER_AZ));
        assertFalse(travelAnswer(PaneWallPage.TERMINAL, APPS_AZ_AND_KEYS, PaneWallPage.WIDGETS,
            APPS_OVER_AZ, 0.99f));
        assertTrue(settleAnswer(PaneWallPage.WIDGETS, APPS_OVER_AZ, false));
    }
}
