package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Pair 1 of the motion audit: the room a crossing from Home pre-sizes the Terminal to
 * ({@link StackLayout#restReservationPx} over {@link StackLayout.RestInput#ownRows}) is the room
 * the landing's pass lays out ({@link StackLayout#layout}), on every layout fixture and in both
 * dock Styles, keyboard up and down. {@link StackLayoutOwnRowsTest} has the case that shipped.
 */
@RunWith(Parameterized.class)
public class TerminalRestParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private final LayoutFixtures.Fixture f;

    public TerminalRestParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private void assertRestIsTheLanding(boolean keyboardUp, boolean dockFloating) {
        String what = f + (dockFloating ? ", Floating" : ", Docked")
            + (keyboardUp ? ", keyboard up" : ", keyboard down");
        assertEquals(what,
            f.settle(PageRef.TERMINAL, keyboardUp, dockFloating).contentReservationPx,
            f.restReservationPx(PageRef.TERMINAL, keyboardUp, dockFloating));
    }

    @Test
    public void theTerminalsRestWithItsKeyboardUpIsTheRoomItsLandingLaysOut() {
        assertRestIsTheLanding(true, false);
        assertRestIsTheLanding(true, true);
    }

    @Test
    public void theTerminalsRestWithItsKeyboardDownIsTheRoomItsLandingLaysOut() {
        assertRestIsTheLanding(false, false);
        assertRestIsTheLanding(false, true);
    }

    @Test
    public void homesRestTakesNoneAndNeitherDoesItsLanding() {
        assertEquals(f.toString(), 0, f.restReservationPx(PageRef.HOME, false, false));
        assertEquals(f.toString(), 0,
            f.settle(PageRef.HOME, false, false).contentReservationPx);
    }
}
