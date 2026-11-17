package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import com.termux.app.place.LayoutFixtures.Fixture;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * In a crossing the keyboard follows the seam (ADR 0030 section 5): its reveal, and the dock's
 * and the status bar's read from the same frame, are one function of the wall's position between
 * the two places, on the curve the stack's look blends on. A drag held halfway holds it halfway,
 * reversing reverses it, and position 1 is the arriving place's rest, so the landing has nothing
 * left to write.
 */
@RunWith(Parameterized.class)
public class KeyboardFollowsSeamTest {

    private static final float EPSILON = 1e-5f;
    private static final int WIDTH = 1080;
    private static final float[] INTERIOR = {0.1f, 0.25f, 0.5f, 0.75f, 0.9f};
    private static final boolean[] BOOLS = {false, true};

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> parameters() {
        return LayoutFixtures.parameters();
    }

    private final Fixture mFixture;

    public KeyboardFollowsSeamTest(Fixture fixture) {
        mFixture = fixture;
    }

    /** Home with its keyboard up or down, the window with its own; both docks shown. */
    private static PlaceChromeTravel.States states(boolean homeKeyboard, boolean windowKeyboard) {
        Map<PaneWallPage, Rest> rest = new EnumMap<>(PaneWallPage.class);
        rest.put(PaneWallPage.WIDGETS, new Rest(homeKeyboard, false, false));
        rest.put(PaneWallPage.TERMINAL, new Rest(windowKeyboard, false, true));
        return new PlaceChromeTravel.States() {
            @NonNull @Override public Rest restOf(@NonNull PaneWallPage place) {
                return rest.get(place);
            }
        };
    }

    /** The frame {@code fraction} of the way from {@code a} to {@code b} on a row of the two. */
    private static Frame between(PaneWallPage a, PaneWallPage b, float fraction,
                                 PlaceChromeTravel.States s) {
        return PlaceChromeTravel.at(Arrays.asList(a, b), a, -fraction * WIDTH, WIDTH, s);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Test
    public void theEndsAreExactlyTheTwoRests() {
        for (boolean homeUp : BOOLS) for (boolean windowUp : BOOLS) {
            PlaceChromeTravel.States s = states(homeUp, windowUp);
            Rest home = s.restOf(PaneWallPage.WIDGETS);
            Rest window = s.restOf(PaneWallPage.TERMINAL);
            String what = mFixture + " home=" + homeUp + " window=" + windowUp;
            Frame start = between(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, 0f, s);
            assertEquals(what, home.keyboardReveal(), start.keyboardReveal, 0f);
            assertEquals(what, home.dockReveal(), start.dockReveal, 0f);
            assertEquals(what, home.statusReveal(), start.statusReveal, 0f);
            // Position 1 is the next page's own rest: the wall stands on it.
            Frame end = PlaceChromeTravel.at(Arrays.asList(PaneWallPage.WIDGETS,
                PaneWallPage.TERMINAL), PaneWallPage.TERMINAL, 0f, WIDTH, s);
            assertEquals(what, window.keyboardReveal(), end.keyboardReveal, 0f);
            assertEquals(what, window.dockReveal(), end.dockReveal, 0f);
            assertEquals(what, window.statusReveal(), end.statusReveal, 0f);
            // And the curve itself is exactly 0 and 1 at the ends.
            assertEquals(what, 0f, StackScene.materialShare(0f), 0f);
            assertEquals(what, 1f, StackScene.materialShare(1f), 0f);
        }
    }

    @Test
    public void theKeyboardIsTheLerpOnTheLooksCurve() {
        for (boolean homeUp : BOOLS) for (boolean windowUp : BOOLS) {
            PlaceChromeTravel.States s = states(homeUp, windowUp);
            Rest home = s.restOf(PaneWallPage.WIDGETS);
            Rest window = s.restOf(PaneWallPage.TERMINAL);
            for (float f : INTERIOR) {
                Frame frame = between(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, f, s);
                float curve = StackScene.materialShare(f);
                String what = mFixture + " home=" + homeUp + " window=" + windowUp + " f=" + f;
                assertEquals(what, lerp(home.keyboardReveal(), window.keyboardReveal(), curve),
                    frame.keyboardReveal, EPSILON);
                assertEquals(what, lerp(home.dockReveal(), window.dockReveal(), curve),
                    frame.dockReveal, EPSILON);
                assertEquals(what, lerp(home.statusReveal(), window.statusReveal(), curve),
                    frame.statusReveal, EPSILON);
                assertEquals(what, curve, frame.chromeFraction, EPSILON);
            }
        }
    }

    @Test
    public void reversingReadsTheSameNumbersBack() {
        for (boolean homeUp : BOOLS) for (boolean windowUp : BOOLS) {
            PlaceChromeTravel.States s = states(homeUp, windowUp);
            for (float f : INTERIOR) {
                // From Home toward the window at f, and from the window toward Home at 1 - f.
                Frame forward = between(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, f, s);
                Frame back = between(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, 1f - f, s);
                String what = mFixture + " home=" + homeUp + " window=" + windowUp + " f=" + f;
                assertEquals(what, forward.keyboardReveal, back.keyboardReveal, EPSILON);
                assertEquals(what, forward.dockReveal, back.dockReveal, EPSILON);
                assertEquals(what, forward.statusReveal, back.statusReveal, EPSILON);
            }
        }
    }

    @Test
    public void aHeldFrameHoldsTheReveal() {
        for (boolean homeUp : BOOLS) for (boolean windowUp : BOOLS) {
            PlaceChromeTravel.States s = states(homeUp, windowUp);
            for (float f : INTERIOR) {
                Frame first = between(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, f, s);
                Frame again = between(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, f, s);
                String what = mFixture + " home=" + homeUp + " window=" + windowUp + " f=" + f;
                assertEquals(what, first.keyboardReveal, again.keyboardReveal, 0f);
                assertEquals(what, first.dockReveal, again.dockReveal, 0f);
                assertEquals(what, first.statusReveal, again.statusReveal, 0f);
            }
        }
    }

    @Test
    public void theLandingFrameLeavesNothingToTravel() {
        for (boolean homeUp : BOOLS) for (boolean windowUp : BOOLS) {
            PlaceChromeTravel.States s = states(homeUp, windowUp);
            for (PaneWallPage arriving : new PaneWallPage[] {PaneWallPage.WIDGETS,
                PaneWallPage.TERMINAL}) {
                Rest rest = s.restOf(arriving);
                PaneWallPage leaving = arriving == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL
                    : PaneWallPage.WIDGETS;
                Frame frame = between(leaving, arriving, 1f, s);
                // The stack is laid out for the arriving rest at the landing.
                int keyboardPx = rest.keyboardReveal() > 0f ? mFixture.keyboardLaidOutPx() : 0;
                StackScene.Motion motion = new StackScene.Motion();
                StackScene.travel(false, false, frame.keyboardReveal, frame.dockReveal,
                    keyboardPx, 200, 120, motion);
                String what = mFixture + " home=" + homeUp + " window=" + windowUp + " into "
                    + arriving;
                assertEquals(what, 0f, motion.dockTravelPx, 0f);
                assertEquals(what, 0f, motion.keyboardSharePx, 0f);
            }
        }
    }
}
