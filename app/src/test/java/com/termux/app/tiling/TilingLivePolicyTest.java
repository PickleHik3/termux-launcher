package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Which places and windows hold views, and where places stand (#418). */
public class TilingLivePolicyTest {

    private static final ScrollingLayoutPolicy.Viewport PORTRAIT =
        new ScrollingLayoutPolicy.Viewport(1000f, 2000f, 20f, false);

    @Test
    public void homeIsLevelZeroAndAWorkspaceItsNumber() {
        assertEquals(0, TilingLivePolicy.level(Place.home(0)));
        assertEquals(0, TilingLivePolicy.level(Place.home(3)));
        assertEquals(1, TilingLivePolicy.level(Place.workspace(1)));
        assertEquals(4, TilingLivePolicy.level(Place.workspace(4)));
    }

    @Test
    public void onlyThePlaceInFrontAndItsNeighboursAboveAndBelowAreLive() {
        // Home, Workspaces 1 to 4: Workspace 2 in front.
        assertFalse(TilingLivePolicy.isLiveLevel(0, 2, true, 4));
        assertTrue(TilingLivePolicy.isLiveLevel(1, 2, true, 4));
        assertTrue(TilingLivePolicy.isLiveLevel(2, 2, true, 4));
        assertTrue(TilingLivePolicy.isLiveLevel(3, 2, true, 4));
        assertFalse(TilingLivePolicy.isLiveLevel(4, 2, true, 4));
    }

    @Test
    public void fromHomeWorkspaceOneIsLiveBelowIt() {
        assertTrue(TilingLivePolicy.isLiveLevel(1, 0, true, 2));
        assertFalse(TilingLivePolicy.isLiveLevel(2, 0, true, 2));
    }

    @Test
    public void withoutHomeTheStackStartsAtOne() {
        assertFalse(TilingLivePolicy.isLiveLevel(0, 1, false, 3));
        assertTrue(TilingLivePolicy.isLiveLevel(2, 1, false, 3));
        assertFalse(TilingLivePolicy.isLiveLevel(3, 1, false, 3));
        assertFalse("past the last Workspace", TilingLivePolicy.isLiveLevel(4, 3, false, 3));
    }

    @Test
    public void aPlaceStandsOneHeightPerLevelFromThePosition() {
        assertEquals(0f, TilingLivePolicy.offsetPx(1, 1f, 800), 0f);
        assertEquals(800f, TilingLivePolicy.offsetPx(1, 0f, 800), 0f);
        assertEquals(-800f, TilingLivePolicy.offsetPx(1, 2f, 800), 0f);
        assertEquals(400f, TilingLivePolicy.offsetPx(2, 1.5f, 800), 0f);
    }

    @Test
    public void aPlaceIsOnScreenOnlyWhileAnyOfItShows() {
        assertTrue(TilingLivePolicy.onScreen(1, 1f));
        assertTrue(TilingLivePolicy.onScreen(1, 0.01f));
        assertFalse(TilingLivePolicy.onScreen(1, 0f));
        assertFalse(TilingLivePolicy.onScreen(3, 1f));
        assertTrue(TilingLivePolicy.onScreen(2, 1.5f));
    }

    @Test
    public void theWindowsOnScreenAndOneScreenEitherSideAreLive() {
        TilingStack stack = TilingStack.empty(1);
        for (int i = 0; i < 5; i++) stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        stack.focusWindow(3);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        Set<Long> live = TilingLivePolicy.liveWindows(state, PORTRAIT);

        assertEquals(new HashSet<>(Arrays.asList(2L, 3L, 4L)), live);
    }

    @Test
    public void halfColumnsBesideTheScreenAreLiveToo() {
        TilingStack stack = TilingStack.empty(1);
        for (int i = 0; i < 6; i++) stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        stack.focusWindow(1);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        for (long id = 1; id <= 6; id++) state = ScrollingLayoutPolicy.setWidthPreset(state, PORTRAIT, id, 0.5f);
        state = ScrollingLayoutPolicy.focusWindow(state, PORTRAIT, 1);

        Set<Long> live = TilingLivePolicy.liveWindows(state, PORTRAIT);

        assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L)), live);
    }

    @Test
    public void noWindowsNoLiveOnes() {
        assertTrue(TilingLivePolicy.liveWindows(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT).isEmpty());
    }

    @Test
    public void navigationModeKeepsThePlacesAroundTheNearestLevelLive() {
        // #433: the view slid most of the way from Workspace 1 to 2.
        assertEquals(2, TilingLivePolicy.navigationCentre(1.6f, true, 4));
        assertEquals(1, TilingLivePolicy.navigationCentre(1.4f, true, 4));
        // Clamped to the stack: Home or Workspace 1 at the top, the last Workspace at the bottom.
        assertEquals(0, TilingLivePolicy.navigationCentre(-0.4f, true, 4));
        assertEquals(1, TilingLivePolicy.navigationCentre(0.2f, false, 4));
        assertEquals(4, TilingLivePolicy.navigationCentre(7f, true, 4));
    }
}
