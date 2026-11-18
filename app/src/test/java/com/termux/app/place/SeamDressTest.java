package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.SeamDress.StackDress;
import com.termux.app.place.SeamDress.Surface;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Which half's Style, Corners, Margin and sizes each surface wears on every frame of a slide
 * across the seam, and how the stack crosses when the halves dress it differently: the dress only
 * changes where none of the stack shows, and a slide that springs back gives the committed
 * half's dress back the same way.
 */
public class SeamDressTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;

    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    private static final PlaceChromeTravel.States STATES = place -> new Rest(false, false,
        place != PaneWallPage.WIDGETS);

    /** The frame {@code fraction} of the way from the terminal to Home, committed to the terminal. */
    private static Frame terminalToHome(float fraction) {
        return PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, fraction * WIDTH, WIDTH, STATES);
    }

    private static final PaneWallPage HOME = PaneWallPage.WIDGETS;
    private static final PaneWallPage TERMINAL = PaneWallPage.TERMINAL;

    // ---- the stack's dress follows its rows

    @Test
    public void homesRowsRisingFromAWorkspaceWithoutADockWearHomesDressFromTheFirstFrame() {
        // pong: the Workspace has no dock, Home's rows slide in over the whole way.
        assertSame(TERMINAL, SeamDress.stackPage(TERMINAL, HOME, DockPlan.SLIDE_IN, 0f));
        for (float fraction = 0.01f; fraction <= 1f; fraction += 0.01f) {
            float progress = SeamTravel.otherHalfProgress(terminalToHome(fraction), TERMINAL);
            float reveal = SeamTravel.dockReveal(DockPlan.SLIDE_IN, progress);
            // Wherever any of the rows shows, they wear Home's dress, never the Workspace's.
            if (reveal > 0f) {
                assertSame("fraction " + fraction, HOME,
                    SeamDress.stackPage(TERMINAL, HOME, DockPlan.SLIDE_IN, progress));
                assertTrue(SeamDress.wearsHome(Surface.STACK, TERMINAL,
                    SeamDress.stackPage(TERMINAL, HOME, DockPlan.SLIDE_IN, progress), true));
            }
        }
    }

    @Test
    public void rowsSlidingOutKeepTheirOwnDressUntilSettle() {
        for (float progress = 0f; progress <= 1f; progress += 0.05f)
            assertSame(HOME, SeamDress.stackPage(HOME, TERMINAL, DockPlan.SLIDE_OUT, progress));
    }


    @Test
    public void aSlideThatSpringsBackGivesTheCommittedDressBack() {
        // Out to past the swap and back to rest: the same function, read back down.
        float[] way = {0f, 0.3f, 0.6f, 0.9f, 0.6f, 0.3f, 0f};
        assertSame(TERMINAL, SeamDress.stackPage(TERMINAL, HOME, DockPlan.SLIDE_IN, 0f));
    }

    @Test
    public void withNoOtherSideTheStackWearsTheCommittedHalf() {
        assertSame(HOME, SeamDress.stackPage(HOME, null, DockPlan.SLIDE_IN, 1f));
    }

    // ---- each surface wears the half it belongs to

    @Test
    public void theStatusBarWearsTheWorkspacesDressThroughAnySlideAcrossTheSeam() {
        // From Home toward a window: the bar arrives with its page in the Workspace's dress.
        assertFalse(SeamDress.wearsHome(Surface.STATUS, HOME, HOME, true));
        assertFalse(SeamDress.wearsHome(Surface.STATUS, TERMINAL, HOME, true));
        // At rest it is the committed half's, as everything is.
        assertTrue(SeamDress.wearsHome(Surface.STATUS, HOME, HOME, false));
        assertFalse(SeamDress.wearsHome(Surface.STATUS, TERMINAL, TERMINAL, false));
    }

    @Test
    public void theFrameStaysWithTheCommittedHalf() {
        assertFalse(SeamDress.wearsHome(Surface.FRAME, TERMINAL, HOME, true));
        assertTrue(SeamDress.wearsHome(Surface.FRAME, HOME, TERMINAL, true));
    }

    @Test
    public void atRestEverySurfaceWearsTheCommittedHalf() {
        for (Surface surface : Surface.values()) {
            assertTrue(SeamDress.wearsHome(surface, HOME, HOME, false));
            assertFalse(SeamDress.wearsHome(surface, TERMINAL, TERMINAL, false));
        }
    }

    // ---- how the stack crosses when the halves dress it differently

    @Test
    public void halvesDressedAlikeKeepTheRowsPlan() {
    }







    // ---- the dress as a value

    @Test
    public void aStackDressDiffersByShapeOrByAnyOfItsSizes() {
        WallShape docked = WallShape.of(LayoutStyle.DOCKED, -1, 8);
        StackDress base = StackDress.of(docked, 1f, 1f, 0);
        assertEquals(base, StackDress.of(WallShape.of(LayoutStyle.DOCKED, -1, 8), 1f, 1f, 0));
        assertNotEquals(base, StackDress.of(WallShape.of(LayoutStyle.FLOATING, -1, 8),
            1f, 1f, 0));
        assertNotEquals(base, StackDress.of(WallShape.of(LayoutStyle.DOCKED, 12, 8),
            1f, 1f, 0));
        assertNotEquals(base, StackDress.of(WallShape.of(LayoutStyle.DOCKED, -1, 4),
            1f, 1f, 0));
        assertNotEquals(base, StackDress.of(docked, 1.2f, 1f, 0));
        assertNotEquals(base, StackDress.of(docked, 1f, 0.9f, 0));
        assertNotEquals(base, StackDress.of(docked, 1f, 1f, 6));
    }

    /** L1: each surface reads the page it stands on, not the wall or the Terminal's for all. */
    @Test
    public void theLookIsReadUnderThePageTheSurfaceStandsOn() {
        int wall = com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.PAGE_WALL;
        assertEquals(7, SeamDress.lookPageId(PaneWallPage.WIDGETS, 7, -1, 2));
        assertEquals(wall, SeamDress.lookPageId(PaneWallPage.WIDGETS, -1, -1, 2));
        assertEquals(5, SeamDress.lookPageId(PaneWallPage.TERMINAL, 7, 5, 2));
        assertEquals(2, SeamDress.lookPageId(PaneWallPage.TERMINAL, 7, -1, 2));
    }
}
