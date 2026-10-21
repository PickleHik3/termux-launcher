package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.InnerPages;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * A slide between two of the Workspace's windows moves the wall's offset exactly as a slide
 * toward the next place does. The chrome must read it as staying on the Workspace: Home's share
 * is 0 on every frame, and the dock and the keyboard do not move. Before this, a slide toward
 * the previous window read as a slide toward Home (the place on the left), and the dock and the
 * keyboard sank mid-swipe; toward the next window there was no place on the right, so it was clean.
 */
public class WindowSlideChromeTravelTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;
    private static final int KEYBOARD_PX = 600;
    private static final int DOCK_PX = 250;

    /** The row as it stands: Home on the left, the Workspace's windows on the right. */
    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);

    /** Home with its keyboard down and no status bar; the Workspace with its keyboard up. */
    private static final PlaceChromeTravel.States STATES = place -> HomeChromePolicy.isHome(place)
        ? new Rest(false, false, false) : new Rest(true, false, true);

    /** A window with windows on both sides. */
    private static final InnerPages MIDDLE_WINDOW = steps -> true;
    /** The last window: only a previous one, on the left. */
    private static final InnerPages LAST_WINDOW = steps -> steps < 0;
    /** The first window: only a next one, on the right; Home is on its left. */
    private static final InnerPages FIRST_WINDOW = steps -> steps > 0;

    private static Frame at(float offsetFraction, InnerPages inner) {
        return PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, offsetFraction * WIDTH, WIDTH,
            STATES, PaneWallPage.TERMINAL, inner);
    }

    private static void assertStaysOnWorkspace(Frame frame) {
        assertSame(PaneWallPage.TERMINAL, frame.from);
        assertSame(PaneWallPage.TERMINAL, frame.toward);
        assertEquals(0f, SeamTravel.otherHalfProgress(frame, PaneWallPage.TERMINAL), EPSILON);
        assertEquals(1f, frame.keyboardReveal, EPSILON);
        assertEquals(1f, frame.dockReveal, EPSILON);
        assertEquals(1f, frame.statusReveal, EPSILON);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(frame, KEYBOARD_PX, DOCK_PX),
            EPSILON);
        assertEquals(0f, SeamTravel.workspaceScreenXPx(frame, WIDTH), EPSILON);
    }

    @Test
    public void slideToThePreviousWindowGivesHomeNoShare() {
        // The finger drags right: the pages sit to the right, the previous window comes in.
        for (float f = 0.02f; f < 1f; f += 0.02f) {
            assertStaysOnWorkspace(at(f, LAST_WINDOW));
            assertStaysOnWorkspace(at(f, MIDDLE_WINDOW));
        }
    }

    @Test
    public void slideToTheNextWindowGivesHomeNoShare() {
        for (float f = 0.02f; f < 1f; f += 0.02f) {
            assertStaysOnWorkspace(at(-f, FIRST_WINDOW));
            assertStaysOnWorkspace(at(-f, MIDDLE_WINDOW));
        }
    }

    @Test
    public void slideFromTheFirstWindowStillCrossesTheSeamToHome() {
        // No window on the left of the first one: the drag brings Home in, as it always has.
        Frame frame = at(0.5f, FIRST_WINDOW);
        assertSame(PaneWallPage.WIDGETS, frame.from);
        assertSame(PaneWallPage.TERMINAL, frame.toward);
        assertEquals(0.5f, SeamTravel.otherHalfProgress(frame, PaneWallPage.TERMINAL), EPSILON);
        assertTrue(frame.keyboardReveal < 1f);
    }

    @Test
    public void withoutInnerPagesTheOffsetReadsAsThePlaceOnTheLeft() {
        // What the chrome did before: the previous window's slide was read as Home's.
        Frame frame = at(0.5f, null);
        assertSame(PaneWallPage.WIDGETS, frame.from);
        assertTrue(SeamTravel.otherHalfProgress(frame, PaneWallPage.TERMINAL) > 0f);
    }

    @Test
    public void atRestNothingIsAsked() {
        Frame frame = PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, 0f, WIDTH, STATES,
            PaneWallPage.TERMINAL, steps -> {
                throw new AssertionError("asked at rest");
            });
        assertStaysOnWorkspace(frame);
    }
}
