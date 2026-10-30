package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.dock.DockGeometry;
import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Pair 4 of the motion audit: the keyboard a crossing pre-rolls ({@code Crossing#preRollKeyboard},
 * under {@code hold(reservationPx)}) against the room the arriving place's rest gives its
 * keyboard ({@link PlaceChromeTravel.Rest#keyboardReveal} up, {@link StackLayout#restReservationPx}),
 * on every layout fixture and in both dock Styles:
 *
 * <ul>
 *   <li>the pass that lays the pre-rolled keyboard out under the hold leaves the content the
 *   room the hold took, whatever the stack grew by;</li>
 *   <li>the keyboard the pre-roll lays out and the travel reveals (the activity's
 *   {@code travelKeyboardLaidOutPx}: the keyboard's height docked, 0 floating) is the room the
 *   Terminal's rest gives its keyboard, and the room its settle gives it.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class KeyboardPreRollParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private final LayoutFixtures.Fixture f;

    public KeyboardPreRollParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private static String what(LayoutFixtures.Fixture f, boolean dockFloating) {
        return f + (dockFloating ? ", Floating" : ", Docked");
    }

    /** The pass on {@code committed} once the keyboard is pre-rolled under the hold. */
    private StackLayout.Result preRolled(PageRef committed, int heldReservationPx,
                                         boolean dockFloating) {
        StackLayout.Input in = f.pass(committed, false, dockFloating);
        in.keyboardShown = true;
        in.keyboardHeightPx = f.keyboardLaidOutPx();
        // While the slide holds the content, whatever the stack lays out floats over it.
        in.keyboardOverlays = true;
        in.travelHoldsContent = true;
        in.heldReservationPx = heldReservationPx;
        return StackLayout.layout(in, new DockGeometry());
    }

    private void assertTheHoldKeepsTheRoom(PageRef committed, boolean dockFloating) {
        int held = f.settle(committed, false, dockFloating).contentReservationPx;
        assertEquals(what(f, dockFloating) + " from " + committed, held,
            preRolled(committed, held, dockFloating).contentReservationPx);
    }

    @Test
    public void thePreRolledKeyboardLeavesTheContentTheRoomTheHoldTook() {
        for (boolean dockFloating : new boolean[] {false, true}) {
            assertTheHoldKeepsTheRoom(PageRef.HOME, dockFloating);
            assertTheHoldKeepsTheRoom(PageRef.TERMINAL, dockFloating);
        }
    }

    @Test
    public void theKeyboardThePreRollRevealsIsTheRoomTheRestGivesIt() {
        for (boolean dockFloating : new boolean[] {false, true}) {
            int restRoom = f.restReservationPx(PageRef.TERMINAL, true, dockFloating)
                - f.restReservationPx(PageRef.TERMINAL, false, dockFloating);
            assertEquals(what(f, dockFloating), f.keyboardLaidOutPx(), restRoom);
        }
    }

    @Test
    public void theKeyboardThePreRollRevealsIsTheRoomTheSettleGivesIt() {
        for (boolean dockFloating : new boolean[] {false, true}) {
            int settledRoom = f.settle(PageRef.TERMINAL, true, dockFloating).contentReservationPx
                - f.settle(PageRef.TERMINAL, false, dockFloating).contentReservationPx;
            assertEquals(what(f, dockFloating), f.keyboardLaidOutPx(), settledRoom);
        }
    }
}
