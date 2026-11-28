package com.termux.app.tiling.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Where a pan in navigation mode comes to rest (#433): the window nearest the view's centre. */
public class NavigationSettleTest {

    private static final long NONE = NavigationSettle.NO_WINDOW;

    private static NavigationSettle.Candidate window(int level, long id, float l, float t, float r, float b) {
        return new NavigationSettle.Candidate(level, id, l, t, r, b);
    }

    private static NavigationSettle.Candidate place(int level, float l, float t, float r, float b) {
        return new NavigationSettle.Candidate(level, NONE, l, t, r, b);
    }

    @Test
    public void theWindowUnderTheCentreWins() {
        // Two half columns side by side, the view's centre in the right one.
        List<NavigationSettle.Candidate> all = Arrays.asList(
            window(1, 10L, 0f, 0f, 490f, 1000f),
            window(1, 11L, 510f, 0f, 1000f, 1000f));
        NavigationSettle.Target target = NavigationSettle.nearest(all, 600f, 500f);
        assertEquals(1, target.level);
        assertEquals(11L, target.windowId);
    }

    @Test
    public void betweenWindowsTheNearerEdgeWins() {
        // The centre falls in the gap, nearer the left column's edge.
        List<NavigationSettle.Candidate> all = Arrays.asList(
            window(1, 10L, -400f, 0f, 495f, 1000f),
            window(1, 11L, 510f, 0f, 1400f, 1000f));
        assertEquals(10L, NavigationSettle.nearest(all, 500f, 500f).windowId);
    }

    @Test
    public void acrossPlacesTheNearestPlaceWins() {
        // Workspace 1 slid most of the way up: Workspace 2's window holds the centre.
        List<NavigationSettle.Candidate> all = Arrays.asList(
            window(1, 10L, 0f, -700f, 1000f, 300f),
            window(2, 20L, 0f, 340f, 1000f, 1340f));
        NavigationSettle.Target target = NavigationSettle.nearest(all, 500f, 500f);
        assertEquals(2, target.level);
        assertEquals(20L, target.windowId);
    }

    @Test
    public void homeHasNoWindowsAndSettlesOnItself() {
        // Home's whole screen is its candidate; Workspace 1 is below most of the screen.
        List<NavigationSettle.Candidate> all = Arrays.asList(
            place(0, 0f, -300f, 1000f, 700f),
            window(1, 10L, 0f, 730f, 1000f, 1700f));
        NavigationSettle.Target target = NavigationSettle.nearest(all, 500f, 500f);
        assertEquals(0, target.level);
        assertEquals(NONE, target.windowId);
    }

    @Test
    public void anEmptyWorkspaceSettlesOnItself() {
        List<NavigationSettle.Candidate> all = Arrays.asList(
            window(2, 20L, 0f, -900f, 1000f, 100f),
            place(3, 0f, 130f, 1000f, 1130f));
        NavigationSettle.Target target = NavigationSettle.nearest(all, 500f, 500f);
        assertEquals(3, target.level);
        assertEquals(NONE, target.windowId);
    }

    @Test
    public void twoWindowsHoldingTheCentreGoToTheOneWhoseMiddleIsNearer() {
        // A Float-sized overlap: both rectangles hold the centre.
        List<NavigationSettle.Candidate> all = Arrays.asList(
            window(1, 10L, 0f, 0f, 1000f, 1000f),
            window(1, 11L, 400f, 400f, 640f, 640f));
        assertEquals(11L, NavigationSettle.nearest(all, 530f, 530f).windowId);
    }

    @Test
    public void nothingToSettleOnIsNull() {
        assertNull(NavigationSettle.nearest(Collections.<NavigationSettle.Candidate>emptyList(), 0f, 0f));
    }
}
