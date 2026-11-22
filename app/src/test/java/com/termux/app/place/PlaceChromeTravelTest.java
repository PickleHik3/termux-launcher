package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The dock and the keyboard as a function of the wall's live offset: what each frame shows
 * between two places' states, that the commit at release does not move it, that a drag which
 * springs back or reverses ends exactly where the wall does, and the translation the accessory
 * stack is drawn at for it.
 */
public class PlaceChromeTravelTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;
    /** The keyboard's laid-out height, and the dock's rows plus the margin under them. */
    private static final int KEYBOARD = 300;
    private static final int DOCK = 120;

    private static final List<PageKind> RING =
        Arrays.asList(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY);
    private static final List<PageKind> LINE =
        Arrays.asList(PageKind.TERMINAL, PageKind.DISPLAY);

    /** Home is always down; the terminal was left with its keyboard up; the display without. */
    private static PlaceChromeTravel.States states(boolean terminalKeyboard,
                                                   boolean displayMinimal) {
        Map<PageKind, Rest> rest = new EnumMap<>(PageKind.class);
        rest.put(PageKind.HOME, new Rest(false, false));
        rest.put(PageKind.TERMINAL, new Rest(terminalKeyboard, false));
        rest.put(PageKind.DISPLAY, new Rest(false, displayMinimal));
        return new PlaceChromeTravel.States() {
            @NonNull @Override public Rest restOf(@NonNull PageKind place) {
                return rest.get(place);
            }
        };
    }

    private static Frame at(PageKind current, float offsetPx, PlaceChromeTravel.States s) {
        return PlaceChromeTravel.at(RING, current, offsetPx, WIDTH, s);
    }

    /** Where the chrome is when the wall is {@code fraction} of the way: the look's curve. */
    private static float chrome(float fraction) {
        return StackScene.materialShare(fraction);
    }

    @Test
    public void atRestAPlaceShowsItsOwnState() {
        PlaceChromeTravel.States s = states(true, false);
        Frame terminal = at(PageKind.TERMINAL, 0f, s);
        assertEquals(1f, terminal.keyboardReveal, EPSILON);
        assertEquals(1f, terminal.dockReveal, EPSILON);
        assertEquals(1f, terminal.statusReveal, EPSILON);
        assertSame(PageKind.TERMINAL, terminal.from);
        assertSame(PageKind.TERMINAL, terminal.toward);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(terminal, KEYBOARD, DOCK), EPSILON);

        Frame home = at(PageKind.HOME, 0f, s);
        assertEquals(0f, home.keyboardReveal, EPSILON);
        assertEquals(1f, home.dockReveal, EPSILON);
    }

    @Test
    public void homeToTheTerminalRaisesTheKeyboardAndTheDockRidesOnIt() {
        PlaceChromeTravel.States s = states(true, false);
        // On Home, dragging left brings the terminal in from the right: negative offsets.
        float previous = -1f;
        for (int step = 0; step <= 10; step++) {
            float offset = -WIDTH * step / 10f;
            Frame frame = at(PageKind.HOME, offset, s);
            assertEquals(chrome(step / 10f), frame.keyboardReveal, EPSILON);
            assertEquals(1f, frame.dockReveal, EPSILON);
            assertTrue("the keyboard only ever rises on the way in",
                frame.keyboardReveal >= previous);
            previous = frame.keyboardReveal;
            // Laid out at its full height below the screen, the keyboard is revealed by sliding
            // the whole stack up: the dock sits on the keys the whole way.
            assertEquals(KEYBOARD * (1f - chrome(step / 10f)),
                PlaceChromeTravel.stackTranslationPx(frame, KEYBOARD, DOCK), EPSILON);
        }
    }

    @Test
    public void theTerminalToHomeLowersTheKeyboardAndPutsTheDockAtTheBottom() {
        PlaceChromeTravel.States s = states(true, false);
        // On the terminal, dragging right brings Home in from the left: positive offsets.
        Frame quarter = at(PageKind.TERMINAL, WIDTH * 0.25f, s);
        assertSame(PageKind.HOME, quarter.from);
        assertSame(PageKind.TERMINAL, quarter.toward);
        assertEquals(chrome(0.75f), quarter.keyboardReveal, EPSILON);
        assertEquals(KEYBOARD * (1f - chrome(0.75f)),
            PlaceChromeTravel.stackTranslationPx(quarter, KEYBOARD, DOCK), EPSILON);
        Frame arrived = at(PageKind.HOME, 0f, s);
        // The keys are all the way below the screen and the dock is where Home keeps it; settle
        // then takes the keyboard out of the layout and the translation back to 0 together.
        assertEquals(KEYBOARD, PlaceChromeTravel.stackTranslationPx(arrived, KEYBOARD, DOCK),
            EPSILON);
    }

    @Test
    public void theCommitAtReleaseDoesNotMoveTheChrome() {
        PlaceChromeTravel.States s = states(true, false);
        // A quarter of the way from the terminal toward Home, then the wall commits to Home: the
        // current page moves by one and the offset by one width, as PaneWallLayout#goTo does.
        Frame before = at(PageKind.TERMINAL, WIDTH * 0.25f, s);
        Frame after = at(PageKind.HOME, WIDTH * 0.25f - WIDTH, s);
        assertEquals(before.keyboardReveal, after.keyboardReveal, EPSILON);
        assertEquals(before.dockReveal, after.dockReveal, EPSILON);
        assertEquals(PlaceChromeTravel.stackTranslationPx(before, KEYBOARD, DOCK),
            PlaceChromeTravel.stackTranslationPx(after, KEYBOARD, DOCK), EPSILON);
    }

    @Test
    public void aDragThatSpringsBackEndsWhereItStarted() {
        PlaceChromeTravel.States s = states(true, false);
        float[] springBack = {0f, 120f, 280f, 340f, 200f, 60f, 5f, 0f};
        Frame last = null;
        for (float offset : springBack) {
            last = at(PageKind.TERMINAL, offset, s);
            assertTrue(last.keyboardReveal <= 1f && last.keyboardReveal >= 0f);
        }
        assertEquals(1f, last.keyboardReveal, EPSILON);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(last, KEYBOARD, DOCK), EPSILON);
    }

    @Test
    public void aReversalBlendsTowardWhicheverPlaceIsComingIn() {
        PlaceChromeTravel.States s = states(true, true);
        // Toward Home on the left: the keyboard goes down, the dock stays.
        Frame left = at(PageKind.TERMINAL, WIDTH * 0.5f, s);
        assertSame(PageKind.HOME, left.from);
        assertEquals(0.5f, left.keyboardReveal, EPSILON);
        assertEquals(1f, left.dockReveal, EPSILON);
        // Past rest the other way, toward a minimal display on the right: the dock goes too.
        Frame right = at(PageKind.TERMINAL, -WIDTH * 0.5f, s);
        assertSame(PageKind.TERMINAL, right.from);
        assertSame(PageKind.DISPLAY, right.toward);
        assertEquals(0.5f, right.keyboardReveal, EPSILON);
        assertEquals(0.5f, right.dockReveal, EPSILON);
        assertEquals(0.5f, right.statusReveal, EPSILON);
        // Crossing rest is continuous: an offset either side of zero is the terminal's own state.
        assertEquals(1f, at(PageKind.TERMINAL, 0.01f, s).keyboardReveal, 1e-3f);
        assertEquals(1f, at(PageKind.TERMINAL, -0.01f, s).keyboardReveal, 1e-3f);
    }

    @Test
    public void aMinimalPlaceSlidesTheWholeStackOffTheScreen() {
        PlaceChromeTravel.States s = states(true, true);
        Frame half = at(PageKind.TERMINAL, -WIDTH * 0.5f, s);
        assertEquals((KEYBOARD + DOCK) * 0.5f,
            PlaceChromeTravel.stackTranslationPx(half, KEYBOARD, DOCK), EPSILON);
        Frame arrived = at(PageKind.DISPLAY, 0f, s);
        assertEquals(0f, arrived.dockReveal, EPSILON);
        assertEquals(0f, arrived.statusReveal, EPSILON);
        assertEquals(KEYBOARD + DOCK,
            PlaceChromeTravel.stackTranslationPx(arrived, KEYBOARD, DOCK), EPSILON);
    }

    @Test
    public void aKeyboardLaidOutButDownNeedsNoPreRoll() {
        PlaceChromeTravel.States s = states(true, true);
        Frame first = at(PageKind.DISPLAY, 4f, s);
        assertTrue(first.keyboardInPlay);
        // The in-app keyboard stays laid out while it is down: its show flag is false,
        // yet it is there for the crossing to bring up.
        assertTrue(PlaceChromeTravel.keyboardLaidOut(false, 340));
        assertFalse(PlaceChromeTravel.keyboardLaidOut(false, 0));
        assertTrue(PlaceChromeTravel.keyboardLaidOut(true, 0));
    }

    @Test
    public void theKeyboardsTravelHeightIsItsLaidOutHeightShownOrDown() {
        assertEquals(380, PlaceChromeTravel.keyboardLaidOutPx(true, 380, 0));
        assertEquals(340, PlaceChromeTravel.keyboardLaidOutPx(false, 0, 340));
        assertEquals(0, PlaceChromeTravel.keyboardLaidOutPx(false, 0, 0));
        assertEquals(0, PlaceChromeTravel.keyboardLaidOutPx(false, 380, -5));
    }

    @Test
    public void theKeyboardsTravelIsBetweenTheEndsAndContinuousAcrossTheSeam() {
        PlaceChromeTravel.States s = states(true, false);
        float last = Float.NaN;
        for (int px = 0; px <= 400; px += 4) {
            Frame f = at(PageKind.DISPLAY, px, s);
            float t = StackScene.keyboardTranslationPx(f.keyboardReveal, 340);
            assertTrue(t >= 0f && t <= 340f);
            if (!Float.isNaN(last)) assertTrue("a step at " + px, Math.abs(t - last) < 12f);
            last = t;
        }
    }

    @Test
    public void twoPlacesThatLookAlikeMoveNothing() {
        PlaceChromeTravel.States s = states(false, false);
        for (float offset : new float[] {-900f, -500f, -1f, 1f, 500f, 900f}) {
            Frame frame = at(PageKind.TERMINAL, offset, s);
            assertEquals(0f, PlaceChromeTravel.stackTranslationPx(frame, 0, DOCK), EPSILON);
            assertEquals(1f, frame.statusReveal, EPSILON);
        }
    }

    @Test
    public void aDragIntoALinesOuterEdgeResistsAtThePagesOwnState() {
        PlaceChromeTravel.States s = states(true, false);
        // The terminal is the first page of a two-page line: there is nothing to its left.
        Frame edge = PlaceChromeTravel.at(LINE, PageKind.TERMINAL, 200f, WIDTH, s);
        assertSame(PageKind.TERMINAL, edge.from);
        assertSame(PageKind.TERMINAL, edge.toward);
        assertEquals(1f, edge.keyboardReveal, EPSILON);
        Frame inward = PlaceChromeTravel.at(LINE, PageKind.TERMINAL, -250f, WIDTH, s);
        assertSame(PageKind.DISPLAY, inward.toward);
        assertEquals(1f - chrome(0.25f), inward.keyboardReveal, EPSILON);
    }

    @Test
    public void noWidthYetMeansRest() {
        PlaceChromeTravel.States s = states(true, false);
        Frame frame = PlaceChromeTravel.at(RING, PageKind.TERMINAL, 300f, 0, s);
        assertEquals(1f, frame.keyboardReveal, EPSILON);
    }

    @Test
    public void theHoldGivesTheContentBackExactlyWhatTheStackGrewBy() {
        // Committed at Home: a 120px dock over a 16px margin.
        int held = DOCK + 16;
        assertEquals(0, PlaceChromeTravel.heldOverlapPx(DOCK, 16, held));
        // The keyboard is pre-rolled into the stack: the content reaches under it by its height.
        assertEquals(KEYBOARD, PlaceChromeTravel.heldOverlapPx(DOCK + KEYBOARD, 16, held));
        // Leaving a minimal place, whose stack was empty: all of it floats over the content.
        assertEquals(DOCK + 16, PlaceChromeTravel.heldOverlapPx(DOCK, 16, 0));
        // Never a negative reach, which would shrink the content instead.
        assertEquals(0, PlaceChromeTravel.heldOverlapPx(40, 0, held));
        // The reservation the content keeps is the held one.
        int overlap = PlaceChromeTravel.heldOverlapPx(DOCK + KEYBOARD, 16, held);
        assertEquals(held, KeyboardOverlayPolicy.contentReservationPx(DOCK + KEYBOARD, 16,
            overlap));
    }

    @Test
    public void thePaneIsCoveredByExactlyWhatTheChromeHasRisenThroughItsRoom() {
        // Home's room held while the keyboard is pre-rolled: the content reaches under the stack
        // by the keyboard's height, and the stack starts slid all the way down past it.
        int overlap = PlaceChromeTravel.heldOverlapPx(DOCK + KEYBOARD, 16, DOCK + 16);
        assertEquals(KEYBOARD, overlap);
        assertEquals(0, PlaceChromeTravel.coveredRoomPx(overlap, KEYBOARD));
        // Half way up, half the room is under the keyboard; landed, all of it.
        assertEquals(KEYBOARD / 2, PlaceChromeTravel.coveredRoomPx(overlap, KEYBOARD / 2f));
        assertEquals(KEYBOARD, PlaceChromeTravel.coveredRoomPx(overlap, 0f));
        // The same arithmetic on every frame of the slide: what the frame's translation leaves
        // over the room is what the pane is clipped by, so the pane's edge rides the keyboard's.
        PlaceChromeTravel.States s = states(true, false);
        Frame quarter = at(PageKind.HOME, -WIDTH * 0.25f, s);
        float translation = PlaceChromeTravel.stackTranslationPx(quarter, KEYBOARD, DOCK);
        assertEquals(KEYBOARD - translation, PlaceChromeTravel.coveredRoomPx(overlap, translation),
            1f);
        // A stack slid further than the room reaches, or no room at all, covers nothing.
        assertEquals(0, PlaceChromeTravel.coveredRoomPx(overlap, KEYBOARD + DOCK));
        assertEquals(0, PlaceChromeTravel.coveredRoomPx(0, 0f));
        assertEquals(0, PlaceChromeTravel.coveredRoomPx(-5, 0f));
        assertEquals(overlap, PlaceChromeTravel.coveredRoomPx(overlap, Float.NaN));
    }

    @Test
    public void theArrivingPlaceIsWhicheverSideOfTheFrameIsNotBeingLeft() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home on the left: the frame reads Home -> Terminal.
        Frame left = at(PageKind.TERMINAL, 300f, s);
        assertSame(PageKind.HOME, PlaceChromeTravel.arriving(left, PageKind.TERMINAL));
        // And toward the display on the right, where the terminal is the near side.
        Frame right = at(PageKind.TERMINAL, -300f, s);
        assertSame(PageKind.DISPLAY, PlaceChromeTravel.arriving(right, PageKind.TERMINAL));
        // Past the commit the wall's page moved on, but the chrome is still the terminal's.
        Frame committed = at(PageKind.HOME, -700f, s);
        assertSame(PageKind.HOME, PlaceChromeTravel.arriving(committed, PageKind.TERMINAL));
        // At rest, or pressed into a line's edge, nothing is arriving.
        assertNull(PlaceChromeTravel.arriving(at(PageKind.TERMINAL, 0f, s), PageKind.TERMINAL));
        Frame edge = PlaceChromeTravel.at(LINE, PageKind.TERMINAL, 200f, WIDTH, s);
        assertNull(PlaceChromeTravel.arriving(edge, PageKind.TERMINAL));
    }

    @Test
    public void progressTowardAPlaceReadsTheSameWhicheverSideItIsOn() {
        PlaceChromeTravel.States s = states(true, false);
        // Home is the near side of a leftward frame: three tenths of the way there, on the
        // chrome's shortened way.
        Frame left = at(PageKind.TERMINAL, 300f, s);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(left, PageKind.HOME),
            EPSILON);
        assertEquals(1f - chrome(0.3f),
            PlaceChromeTravel.progressToward(left, PageKind.TERMINAL), EPSILON);
        // The display is the far side of a rightward one.
        Frame right = at(PageKind.TERMINAL, -300f, s);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(right, PageKind.DISPLAY),
            EPSILON);
        // A place the frame does not touch, and a frame at rest, are at 0.
        assertEquals(0f, PlaceChromeTravel.progressToward(right, PageKind.HOME), EPSILON);
        assertEquals(0f, PlaceChromeTravel.progressToward(at(PageKind.TERMINAL, 0f, s),
            PageKind.TERMINAL), EPSILON);
        // Springing back reads down through the same number, and rests over the landing.
        assertEquals(0f, PlaceChromeTravel.progressToward(at(PageKind.TERMINAL, 50f, s),
            PageKind.HOME), EPSILON);
        assertEquals(1f, PlaceChromeTravel.progressToward(at(PageKind.TERMINAL, 950f, s),
            PageKind.HOME), EPSILON);
    }

    @Test
    public void theChromeIsOneFunctionOfPositionOnTheLooksCurve() {
        PlaceChromeTravel.States s = states(true, false);
        // The curve is exactly 0 and 1 at the ends and half at the middle, so a reversal at the
        // midpoint reads the same either way round.
        assertEquals(0f, chrome(0f), 0f);
        assertEquals(0.5f, chrome(0.5f), EPSILON);
        assertEquals(1f, chrome(1f), 0f);
        assertEquals(0.5f, at(PageKind.TERMINAL, WIDTH * 0.5f, s).keyboardReveal, EPSILON);
        // Frame's own number is the curve, and the keyboard, the dock and the status bar read it.
        for (int step = 1; step < 10; step++) {
            Frame frame = at(PageKind.TERMINAL, WIDTH * step / 10f, s);
            assertEquals(chrome(1f - step / 10f), frame.chromeFraction, EPSILON);
            assertEquals(chrome(1f - step / 10f), frame.keyboardReveal, EPSILON);
        }
        // The first frame has not moved the reveal yet.
        PlaceChromeTravel.States display = states(true, true);
        Frame first = at(PageKind.DISPLAY, 4f, display);
        assertEquals(0f, first.keyboardReveal, EPSILON);
    }

    @Test
    public void aKeyboardBeingPutAwayFollowsTheSeamAsOneRisingDoes() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home on the left, the screen 0.3 of the way: the keyboard
        // has gone as far as the curve says, with no window of its own and no head start.
        assertEquals(chrome(0.7f), at(PageKind.TERMINAL, WIDTH * 0.3f, s).keyboardReveal,
            EPSILON);
        assertEquals(1f, at(PageKind.TERMINAL, WIDTH * 0.05f, s).keyboardReveal, EPSILON);
        // And toward the display on the right, where the terminal is the near side: the same
        // function of the distance from the terminal's rest.
        PlaceChromeTravel.States display = states(true, false);
        assertEquals(1f - chrome(0.3f), at(PageKind.TERMINAL, -WIDTH * 0.3f, display)
            .keyboardReveal, EPSILON);
        // Half way the keyboard is half away: it is not gone by half way, nor on the way in.
        assertEquals(0.5f, at(PageKind.TERMINAL, -WIDTH * 0.5f, display).keyboardReveal,
            EPSILON);
        // A keyboard rising reads the same curve: Home toward the terminal.
        for (int step = 0; step <= 10; step++) {
            Frame frame = at(PageKind.HOME, -WIDTH * step / 10f, s);
            assertEquals(chrome(step / 10f), frame.keyboardReveal, EPSILON);
        }
    }

    @Test
    public void theCommitAndAReversalKeepTheKeyboardOnOneFunctionOfPosition() {
        PlaceChromeTravel.States s = states(true, false);
        // The wall commits to Home a third of the way there: the same reveal either side of it.
        Frame before = at(PageKind.TERMINAL, WIDTH * 0.35f, s);
        Frame after = at(PageKind.HOME, WIDTH * 0.35f - WIDTH, s);
        assertEquals(before.keyboardReveal, after.keyboardReveal, EPSILON);
        assertEquals(before.chromeFraction, after.chromeFraction, EPSILON);
        // A drag that comes back reads up through the same numbers, and rests where it began.
        float[] springBack = {600f, 450f, 300f, 150f, 40f, 0f};
        float previous = -1f;
        for (float offset : springBack) {
            float reveal = at(PageKind.TERMINAL, offset, s).keyboardReveal;
            assertTrue("the keyboard only ever comes back on the way back", reveal >= previous);
            previous = reveal;
        }
        assertEquals(1f, previous, EPSILON);
    }

    @Test
    public void theRowsFollowTheChromesWayWhoseverKeyboardMoves() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home: the room the keyboard gives back is taken as the
        // keyboard goes, on the one curve.
        Frame left = at(PageKind.TERMINAL, WIDTH * 0.3f, s);
        assertEquals(1f - chrome(0.7f),
            PlaceChromeTravel.progressToward(left, PageKind.HOME), EPSILON);
        assertEquals(chrome(0.7f),
            PlaceChromeTravel.progressToward(left, PageKind.TERMINAL), EPSILON);
        // Between two places that agree on the keyboard it is the same number.
        PlaceChromeTravel.States same = states(false, true);
        Frame minimal = at(PageKind.TERMINAL, -WIDTH * 0.3f, same);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(minimal, PageKind.DISPLAY),
            EPSILON);
    }

    /**
     * Moved from the Home chrome policy's test. The dock rides the keyboard as a transform: the
     * stack is laid out once with the keyboard in it, and while the keyboard is part way up the
     * whole stack, dock included, is slid down by the share of the keyboard not yet revealed.
     * Nothing is laid out per frame.
     */
    @Test
    public void theDockRidesTheKeyboardsRevealAsATranslation() {
        Rest down = new Rest(false, false, false);
        Rest up = new Rest(true, false, false);
        int keyboard = 600;
        int dock = 128;
        assertEquals(keyboard, PlaceChromeTravel.stackTranslationPx(
            PlaceChromeTravel.rest(PageKind.HOME, down), keyboard, dock), 0f);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(
            PlaceChromeTravel.rest(PageKind.HOME, up), keyboard, dock), 0f);
    }

    /** The window's status bar fades on the way to Home, and arrives with the window. */
    @Test
    public void theStatusBarFadesOutTowardHomeAndArrivesWithTheWindow() {
        List<PageKind> row = Arrays.asList(PageKind.HOME, PageKind.TERMINAL);
        PlaceChromeTravel.States states = page -> new Rest(false, false,
            page != PageKind.HOME);
        // Resting on the window, the wall half way back toward Home.
        Frame halfway = PlaceChromeTravel.at(row, PageKind.TERMINAL, 500f, 1000, states);
        assertEquals(0.5f, halfway.statusReveal, 1e-4f);
        assertEquals("the dock is on both halves", 1f, halfway.dockReveal, 0f);
        Frame home = PlaceChromeTravel.at(row, PageKind.HOME, 0f, 1000, states);
        assertEquals(0f, home.statusReveal, 0f);
        Frame window = PlaceChromeTravel.at(row, PageKind.TERMINAL, 0f, 1000, states);
        assertEquals(1f, window.statusReveal, 0f);
    }

    /**
     * Motion audit pair 5: the reveals a crossing draws are its places' rests at both ends, on
     * every layout fixture's width: past the curve's end ({@link SeamFade#END}) the chrome already
     * reads the arriving rest, the status bar's reveal included, so the settle finds nothing
     * left to move.
     */
    @Test
    public void aCrossingsRevealsLandOnTheRestOnEveryLayout() {
        List<PageKind> row = Arrays.asList(PageKind.HOME, PageKind.TERMINAL);
        PlaceChromeTravel.States states = page -> new Rest(page != PageKind.HOME, false,
            page != PageKind.HOME);
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL) {
            int width = f.widthPx;
            Rest window = states.restOf(PageKind.TERMINAL);
            Rest home = states.restOf(PageKind.HOME);
            // Resting on Home, the wall all but landed on the window: the window's rest.
            float landing = -width * (SeamFade.END + (1f - SeamFade.END) / 2f);
            Frame there = PlaceChromeTravel.at(row, PageKind.HOME, landing, width, states);
            assertEquals(f + " status", window.statusReveal(), there.statusReveal, 0f);
            assertEquals(f + " dock", window.dockReveal(), there.dockReveal, 0f);
            assertEquals(f + " keyboard", window.keyboardReveal(), there.keyboardReveal, 0f);
            // And back: resting on the window, the wall all but landed on Home.
            Frame back = PlaceChromeTravel.at(row, PageKind.TERMINAL, -landing, width,
                states);
            assertEquals(f + " status back", home.statusReveal(), back.statusReveal, 0f);
            assertEquals(f + " keyboard back", home.keyboardReveal(), back.keyboardReveal, 0f);
        }
    }

    /**
     * Motion audit pair 8: the overlap a slide holds the content with
     * ({@link PlaceChromeTravel#heldOverlapPx}) is the one Stack layout lays out under the hold,
     * and it leaves the content exactly the room it was committed with, on every layout fixture:
     * the Terminal committed with its keyboard down while the stack grows by the keyboard.
     */
    @Test
    public void theHeldOverlapKeepsTheCommittedRoomOnEveryLayout() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL) {
            for (boolean dockFloating : new boolean[] {false, true}) {
                String what = f + (dockFloating ? ", Floating" : ", Docked");
                int held = f.settle(com.termux.app.wall.PageRef.TERMINAL, false, dockFloating)
                    .contentReservationPx;
                StackLayout.Input in = f.pass(com.termux.app.wall.PageRef.TERMINAL, true,
                    dockFloating);
                in.travelHoldsContent = true;
                in.heldReservationPx = held;
                StackLayout.Result r = StackLayout.layout(in,
                    new com.termux.app.dock.DockGeometry());
                assertEquals(what, PlaceChromeTravel.heldOverlapPx(r.stackHeightPx,
                    r.bottomMarginPx, held), r.contentOverlapPx);
                assertEquals(what, held, r.contentReservationPx);
            }
        }
    }
}
