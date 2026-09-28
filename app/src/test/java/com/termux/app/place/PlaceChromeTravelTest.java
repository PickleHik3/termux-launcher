package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PaneWallPage;

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

    private static final List<PaneWallPage> RING =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);
    private static final List<PaneWallPage> LINE =
        Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    /** Home is always down; the terminal was left with its keyboard up; the display without. */
    private static PlaceChromeTravel.States states(boolean terminalKeyboard,
                                                   boolean displayMinimal) {
        Map<PaneWallPage, Rest> rest = new EnumMap<>(PaneWallPage.class);
        rest.put(PaneWallPage.WIDGETS, new Rest(false, false));
        rest.put(PaneWallPage.TERMINAL, new Rest(terminalKeyboard, false));
        rest.put(PaneWallPage.DISPLAY, new Rest(false, displayMinimal));
        return new PlaceChromeTravel.States() {
            @NonNull @Override public Rest restOf(@NonNull PaneWallPage place) {
                return rest.get(place);
            }
        };
    }

    private static Frame at(PaneWallPage current, float offsetPx, PlaceChromeTravel.States s) {
        return PlaceChromeTravel.at(RING, current, offsetPx, WIDTH, s);
    }

    /** Where the chrome is when the wall is {@code fraction} of the way: see PlaceChromeTravel.LANDING. */
    private static float chrome(float fraction) {
        return PlaceChromeTravel.chromeFraction(fraction);
    }

    @Test
    public void atRestAPlaceShowsItsOwnState() {
        PlaceChromeTravel.States s = states(true, false);
        Frame terminal = at(PaneWallPage.TERMINAL, 0f, s);
        assertEquals(1f, terminal.keyboardReveal, EPSILON);
        assertEquals(1f, terminal.dockReveal, EPSILON);
        assertEquals(1f, terminal.statusReveal, EPSILON);
        assertSame(PaneWallPage.TERMINAL, terminal.from);
        assertSame(PaneWallPage.TERMINAL, terminal.toward);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(terminal, KEYBOARD, DOCK), EPSILON);

        Frame home = at(PaneWallPage.WIDGETS, 0f, s);
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
            Frame frame = at(PaneWallPage.WIDGETS, offset, s);
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
        Frame quarter = at(PaneWallPage.TERMINAL, WIDTH * 0.25f, s);
        assertSame(PaneWallPage.WIDGETS, quarter.from);
        assertSame(PaneWallPage.TERMINAL, quarter.toward);
        assertEquals(chrome(0.75f), quarter.keyboardReveal, EPSILON);
        assertEquals(KEYBOARD * (1f - chrome(0.75f)),
            PlaceChromeTravel.stackTranslationPx(quarter, KEYBOARD, DOCK), EPSILON);
        Frame arrived = at(PaneWallPage.WIDGETS, 0f, s);
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
        Frame before = at(PaneWallPage.TERMINAL, WIDTH * 0.25f, s);
        Frame after = at(PaneWallPage.WIDGETS, WIDTH * 0.25f - WIDTH, s);
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
            last = at(PaneWallPage.TERMINAL, offset, s);
            assertTrue(last.keyboardReveal <= 1f && last.keyboardReveal >= 0f);
        }
        assertEquals(1f, last.keyboardReveal, EPSILON);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(last, KEYBOARD, DOCK), EPSILON);
    }

    @Test
    public void aReversalBlendsTowardWhicheverPlaceIsComingIn() {
        PlaceChromeTravel.States s = states(true, true);
        // Toward Home on the left: the keyboard goes down, the dock stays.
        Frame left = at(PaneWallPage.TERMINAL, WIDTH * 0.5f, s);
        assertSame(PaneWallPage.WIDGETS, left.from);
        assertEquals(0.5f, left.keyboardReveal, EPSILON);
        assertEquals(1f, left.dockReveal, EPSILON);
        // Past rest the other way, toward a minimal display on the right: the dock goes too.
        Frame right = at(PaneWallPage.TERMINAL, -WIDTH * 0.5f, s);
        assertSame(PaneWallPage.TERMINAL, right.from);
        assertSame(PaneWallPage.DISPLAY, right.toward);
        assertEquals(0.5f, right.keyboardReveal, EPSILON);
        assertEquals(0.5f, right.dockReveal, EPSILON);
        assertEquals(0.5f, right.statusReveal, EPSILON);
        // Crossing rest is continuous: an offset either side of zero is the terminal's own state.
        assertEquals(1f, at(PaneWallPage.TERMINAL, 0.01f, s).keyboardReveal, 1e-3f);
        assertEquals(1f, at(PaneWallPage.TERMINAL, -0.01f, s).keyboardReveal, 1e-3f);
    }

    @Test
    public void aMinimalPlaceSlidesTheWholeStackOffTheScreen() {
        PlaceChromeTravel.States s = states(true, true);
        Frame half = at(PaneWallPage.TERMINAL, -WIDTH * 0.5f, s);
        assertEquals((KEYBOARD + DOCK) * 0.5f,
            PlaceChromeTravel.stackTranslationPx(half, KEYBOARD, DOCK), EPSILON);
        Frame arrived = at(PaneWallPage.DISPLAY, 0f, s);
        assertEquals(0f, arrived.dockReveal, EPSILON);
        assertEquals(0f, arrived.statusReveal, EPSILON);
        assertEquals(KEYBOARD + DOCK,
            PlaceChromeTravel.stackTranslationPx(arrived, KEYBOARD, DOCK), EPSILON);
    }

    @Test
    public void leavingAMinimalPlaceAsksForTheDockToBePreRolled() {
        PlaceChromeTravel.States s = states(true, true);
        Frame atRest = at(PaneWallPage.DISPLAY, 0f, s);
        assertFalse(PlaceChromeTravel.needsDockPreRoll(atRest, false));
        assertFalse(PlaceChromeTravel.needsKeyboardPreRoll(atRest, false));
        // The first pixel toward the terminal already shows some dock and some keyboard.
        Frame first = at(PaneWallPage.DISPLAY, 4f, s);
        assertTrue(PlaceChromeTravel.needsDockPreRoll(first, false));
        assertTrue(PlaceChromeTravel.needsKeyboardPreRoll(first, false));
        assertFalse(PlaceChromeTravel.needsDockPreRoll(first, true));
        assertFalse(PlaceChromeTravel.needsKeyboardPreRoll(first, true));
        // Pre-rolled at full height, both start all the way below the screen.
        assertEquals(KEYBOARD + DOCK,
            PlaceChromeTravel.stackTranslationPx(first, KEYBOARD, DOCK), 3f);
    }

    @Test
    public void twoPlacesThatLookAlikeMoveNothing() {
        PlaceChromeTravel.States s = states(false, false);
        for (float offset : new float[] {-900f, -500f, -1f, 1f, 500f, 900f}) {
            Frame frame = at(PaneWallPage.TERMINAL, offset, s);
            assertEquals(0f, PlaceChromeTravel.stackTranslationPx(frame, 0, DOCK), EPSILON);
            assertEquals(1f, frame.statusReveal, EPSILON);
        }
    }

    @Test
    public void aDragIntoALinesOuterEdgeResistsAtThePagesOwnState() {
        PlaceChromeTravel.States s = states(true, false);
        // The terminal is the first page of a two-page line: there is nothing to its left.
        Frame edge = PlaceChromeTravel.at(LINE, PaneWallPage.TERMINAL, 200f, WIDTH, s);
        assertSame(PaneWallPage.TERMINAL, edge.from);
        assertSame(PaneWallPage.TERMINAL, edge.toward);
        assertEquals(1f, edge.keyboardReveal, EPSILON);
        Frame inward = PlaceChromeTravel.at(LINE, PaneWallPage.TERMINAL, -250f, WIDTH, s);
        assertSame(PaneWallPage.DISPLAY, inward.toward);
        assertEquals(1f - chrome(0.25f), inward.keyboardReveal, EPSILON);
    }

    @Test
    public void noWidthYetMeansRest() {
        PlaceChromeTravel.States s = states(true, false);
        Frame frame = PlaceChromeTravel.at(RING, PaneWallPage.TERMINAL, 300f, 0, s);
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
    public void theArrivingPlaceIsWhicheverSideOfTheFrameIsNotBeingLeft() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home on the left: the frame reads Home -> Terminal.
        Frame left = at(PaneWallPage.TERMINAL, 300f, s);
        assertSame(PaneWallPage.WIDGETS, PlaceChromeTravel.arriving(left, PaneWallPage.TERMINAL));
        // And toward the display on the right, where the terminal is the near side.
        Frame right = at(PaneWallPage.TERMINAL, -300f, s);
        assertSame(PaneWallPage.DISPLAY, PlaceChromeTravel.arriving(right, PaneWallPage.TERMINAL));
        // Past the commit the wall's page moved on, but the chrome is still the terminal's.
        Frame committed = at(PaneWallPage.WIDGETS, -700f, s);
        assertSame(PaneWallPage.WIDGETS, PlaceChromeTravel.arriving(committed, PaneWallPage.TERMINAL));
        // At rest, or pressed into a line's edge, nothing is arriving.
        assertNull(PlaceChromeTravel.arriving(at(PaneWallPage.TERMINAL, 0f, s), PaneWallPage.TERMINAL));
        Frame edge = PlaceChromeTravel.at(LINE, PaneWallPage.TERMINAL, 200f, WIDTH, s);
        assertNull(PlaceChromeTravel.arriving(edge, PaneWallPage.TERMINAL));
    }

    @Test
    public void progressTowardAPlaceReadsTheSameWhicheverSideItIsOn() {
        PlaceChromeTravel.States s = states(true, false);
        // Home is the near side of a leftward frame: three tenths of the way there, on the
        // chrome's shortened way.
        Frame left = at(PaneWallPage.TERMINAL, 300f, s);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(left, PaneWallPage.WIDGETS),
            EPSILON);
        assertEquals(1f - chrome(0.3f),
            PlaceChromeTravel.progressToward(left, PaneWallPage.TERMINAL), EPSILON);
        // The display is the far side of a rightward one.
        Frame right = at(PaneWallPage.TERMINAL, -300f, s);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(right, PaneWallPage.DISPLAY),
            EPSILON);
        // A place the frame does not touch, and a frame at rest, are at 0.
        assertEquals(0f, PlaceChromeTravel.progressToward(right, PaneWallPage.WIDGETS), EPSILON);
        assertEquals(0f, PlaceChromeTravel.progressToward(at(PaneWallPage.TERMINAL, 0f, s),
            PaneWallPage.TERMINAL), EPSILON);
        // Springing back reads down through the same number, and rests over the landing.
        assertEquals(0f, PlaceChromeTravel.progressToward(at(PaneWallPage.TERMINAL, 50f, s),
            PaneWallPage.WIDGETS), EPSILON);
        assertEquals(1f, PlaceChromeTravel.progressToward(at(PaneWallPage.TERMINAL, 950f, s),
            PaneWallPage.WIDGETS), EPSILON);
    }

    @Test
    public void theChromeLandsBeforeTheWallDoesAndLeavesAfterIt() {
        PlaceChromeTravel.States s = states(true, false);
        // Over the first stretch of the way the keyboard has not moved; over the last, it has
        // landed - so it is not still creeping while the page's edge creeps at the screen's.
        assertEquals(1f, at(PaneWallPage.TERMINAL, WIDTH * 0.05f, s).keyboardReveal, EPSILON);
        assertEquals(0f, at(PaneWallPage.TERMINAL, WIDTH * 0.95f, s).keyboardReveal, EPSILON);
        // Half way is half way, so a reversal at the midpoint reads the same either way round.
        assertEquals(0.5f, at(PaneWallPage.TERMINAL, WIDTH * 0.5f, s).keyboardReveal, EPSILON);
        assertEquals(0.5f, PlaceChromeTravel.chromeFraction(0.5f), EPSILON);
        assertEquals(0f, PlaceChromeTravel.chromeFraction(PlaceChromeTravel.LANDING), EPSILON);
        assertEquals(1f, PlaceChromeTravel.chromeFraction(1f - PlaceChromeTravel.LANDING),
            EPSILON);
        // The pre-roll still belongs to the first frame, before the reveal has moved.
        PlaceChromeTravel.States display = states(true, true);
        Frame first = at(PaneWallPage.DISPLAY, 4f, display);
        assertEquals(0f, first.keyboardReveal, EPSILON);
        assertTrue(PlaceChromeTravel.needsKeyboardPreRoll(first, false));
    }

    /** The frame as the activity reads it: with the place the chrome is committed to named. */
    private static Frame leaving(PaneWallPage committed, PaneWallPage current, float offsetPx,
                                 PlaceChromeTravel.States s) {
        return PlaceChromeTravel.at(RING, current, offsetPx, WIDTH, s, committed);
    }

    @Test
    public void aKeyboardBeingPutAwayIsGoneByHalfWayOnEitherSide() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home on the left: the terminal is the frame's far side.
        assertEquals(1f, leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, WIDTH * 0.05f, s)
            .keyboardReveal, EPSILON);
        assertEquals(1f - PlaceChromeTravel.keyboardHideFraction(0.3f),
            leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, WIDTH * 0.3f, s).keyboardReveal,
            EPSILON);
        assertEquals(0f, leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL,
            WIDTH * PlaceChromeTravel.KEYBOARD_HIDE_END, s).keyboardReveal, EPSILON);
        assertEquals(0f, leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, WIDTH * 0.7f, s)
            .keyboardReveal, EPSILON);
        // And toward the display on the right, where the terminal is the near side: the same
        // function of the distance from the terminal's rest.
        assertEquals(1f - PlaceChromeTravel.keyboardHideFraction(0.3f),
            leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, -WIDTH * 0.3f, s).keyboardReveal,
            EPSILON);
        assertEquals(0f, leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, -WIDTH * 0.6f, s)
            .keyboardReveal, EPSILON);
        // The window itself: nothing over the landing, all of it by the half-way mark.
        assertEquals(0f, PlaceChromeTravel.keyboardHideFraction(PlaceChromeTravel.LANDING), EPSILON);
        assertEquals(1f, PlaceChromeTravel.keyboardHideFraction(PlaceChromeTravel.KEYBOARD_HIDE_END),
            EPSILON);
        assertEquals(0.5f, PlaceChromeTravel.keyboardHideFraction(
            (PlaceChromeTravel.LANDING + PlaceChromeTravel.KEYBOARD_HIDE_END) / 2f), EPSILON);
    }

    @Test
    public void aKeyboardRisingStillLandsWithThePage() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving Home for the terminal: the committed place has no keyboard, so the rise reads
        // the chrome's way, exactly as it did before the hide had a window of its own.
        for (int step = 0; step <= 10; step++) {
            Frame frame = leaving(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS, -WIDTH * step / 10f, s);
            assertEquals(chrome(step / 10f), frame.keyboardReveal, EPSILON);
            assertEquals(frame.chromeFraction, frame.keyboardFraction, EPSILON);
        }
        // From the display too, where the terminal comes in from the left.
        assertEquals(chrome(0.3f), leaving(PaneWallPage.DISPLAY, PaneWallPage.DISPLAY, WIDTH * 0.3f, s)
            .keyboardReveal, EPSILON);
    }

    @Test
    public void theCommitAndAReversalKeepTheEarlyHideOnOneFunctionOfPosition() {
        PlaceChromeTravel.States s = states(true, false);
        // The wall commits to Home a third of the way there; the chrome is still the terminal's.
        Frame before = leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, WIDTH * 0.35f, s);
        Frame after = leaving(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, WIDTH * 0.35f - WIDTH, s);
        assertEquals(before.keyboardReveal, after.keyboardReveal, EPSILON);
        assertEquals(before.keyboardFraction, after.keyboardFraction, EPSILON);
        // A drag that comes back reads up through the same numbers, and rests where it began.
        float[] springBack = {600f, 450f, 300f, 150f, 40f, 0f};
        float previous = -1f;
        for (float offset : springBack) {
            float reveal = leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, offset, s)
                .keyboardReveal;
            assertTrue("the keyboard only ever comes back on the way back", reveal >= previous);
            previous = reveal;
        }
        assertEquals(1f, previous, EPSILON);
    }

    @Test
    public void theRowsFollowTheKeyboardWhenItIsWhatMoves() {
        PlaceChromeTravel.States s = states(true, false);
        // Leaving the terminal toward Home: the room the keyboard gives back is taken as the
        // keyboard goes, on its own way, not on the chrome's.
        Frame left = leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, WIDTH * 0.3f, s);
        assertEquals(PlaceChromeTravel.keyboardHideFraction(0.3f),
            PlaceChromeTravel.progressToward(left, PaneWallPage.WIDGETS), EPSILON);
        assertEquals(1f - PlaceChromeTravel.keyboardHideFraction(0.3f),
            PlaceChromeTravel.progressToward(left, PaneWallPage.TERMINAL), EPSILON);
        // Between two places that agree on the keyboard, the chrome's way is the rows' way.
        PlaceChromeTravel.States same = states(false, true);
        Frame minimal = leaving(PaneWallPage.TERMINAL, PaneWallPage.TERMINAL, -WIDTH * 0.3f, same);
        assertEquals(chrome(0.3f), PlaceChromeTravel.progressToward(minimal, PaneWallPage.DISPLAY),
            EPSILON);
    }
}
