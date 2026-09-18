package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.help.HelpExplorePlacement.Request;
import com.termux.app.help.HelpExplorePlacement.Result;
import com.termux.app.help.HelpExplorePlacement.Seat;
import com.termux.app.help.HelpLeaderRouter.Box;
import com.termux.app.help.HelpLeaderRouter.Segment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * The one card's seat: pixels in, a seat out. The hard rules are the spec's — the card never covers
 * the control, the toolbar or the system bars, and the answer is either a seat at the size asked
 * for or an explicit "nowhere".
 */
public class HelpExplorePlacementTest {

    /** A phone-shaped screen inside its system bars. */
    private static final Box SAFE = new Box(0, 60, 1080, 2280);
    /** The toolbar at the bottom edge, where it sits while the control is up at the top. */
    private static final Box BOTTOM_TOOLBAR = new Box(360, 2160, 720, 2270);
    private static final Box TOP_TOOLBAR = new Box(360, 70, 720, 180);

    private Request request(Box target, Box toolbar, List<Box> others, float w, float h) {
        return new Request(SAFE, target, toolbar == null ? Collections.<Box>emptyList()
            : Collections.singletonList(toolbar), others, w, h, 30);
    }

    private Request request(Box target, Box toolbar) {
        return request(target, toolbar, Collections.<Box>emptyList(), 600, 320);
    }

    /** Every hard rule, checked against the request rather than against the module's own helpers. */
    private void assertLegal(Request r, Result seat) {
        assertTrue("no seat", seat.fits());
        Box card = seat.card;
        assertTrue("card left the room", card.left >= SAFE.left && card.top >= SAFE.top
            && card.right <= SAFE.right && card.bottom <= SAFE.bottom);
        assertFalse("card on its own control", overlaps(card, r.target));
        for (Box box : r.reserved) assertFalse("card on a reserved rect", overlaps(card, box));
        assertEquals("card was resized", r.cardWidth, card.width(), 0.01f);
        assertEquals("card was resized", r.cardHeight, card.height(), 0.01f);
        if (seat.leader != null) {
            Segment line = seat.leader;
            assertTrue("the leader is not axis-aligned", line.x1 == line.x2 || line.y1 == line.y2);
            assertFalse("the leader enters the card", enters(line, card));
            assertFalse("the leader enters the control", enters(line, r.target));
        }
    }

    // Independent geometry: the test does not reuse the module's own overlap helpers.
    private boolean overlaps(Box a, Box b) {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
    }
    private boolean enters(Segment s, Box b) {
        float l = Math.min(s.x1, s.x2), r = Math.max(s.x1, s.x2);
        float t = Math.min(s.y1, s.y2), bottom = Math.max(s.y1, s.y2);
        return s.x1 == s.x2 ? l > b.left && l < b.right && bottom > b.top && t < b.bottom
            : t > b.top && t < b.bottom && r > b.left && l < b.right;
    }

    @Test public void aControlAtTheTopTakesItsCardBelowItselfOnOneStraightLine() {
        Box status = new Box(0, 60, 1080, 150);
        Request r = request(status, BOTTOM_TOOLBAR);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals(Seat.BELOW, seat.seat);
        assertLegal(r, seat);
        assertEquals(status.bottom + 30, seat.card.top, 0.01f);
        assertNotNull("a card right under its control says so with a line", seat.leader);
        assertEquals(status.cx(), seat.leader.x1, 0.01f);
        assertEquals(status.cx(), seat.leader.x2, 0.01f);
    }

    @Test public void aControlAtTheBottomTakesItsCardAboveItself() {
        Box dock = new Box(0, 2000, 1080, 2140);
        Request r = request(dock, TOP_TOOLBAR);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals(Seat.ABOVE, seat.seat);
        assertLegal(r, seat);
        assertEquals(dock.top - 30, seat.card.bottom, 0.01f);
    }

    @Test public void aControlDownOneSideTakesItsCardBesideItself() {
        Box rail = new Box(0, 700, 140, 1600);
        Request r = request(rail, BOTTOM_TOOLBAR, Collections.<Box>emptyList(), 600, 1400);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals(Seat.RIGHT, seat.seat);
        assertLegal(r, seat);
        assertEquals(rail.right + 30, seat.card.left, 0.01f);
    }

    /**
     * A control whose own neighbourhood is taken — the extra keys row, with its seven key cards in
     * the lanes above and below it — sends the card to the clearest edge of the screen instead.
     */
    @Test public void aControlWithNoRoomBesideItSendsTheCardToAClearEdge() {
        Box keys = new Box(0, 1000, 1080, 1120);
        List<Box> reserved = Arrays.asList(TOP_TOOLBAR,
            new Box(0, 780, 1080, 970), new Box(0, 1150, 1080, 1340));
        Request r = new Request(SAFE, keys, reserved, Collections.<Box>emptyList(), 600, 320, 30);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals(Seat.EDGE, seat.seat);
        assertLegal(r, seat);
        assertNull("an edge card is paired by its highlight, not by a long line", seat.leader);
        assertTrue("the card is clear of the key cards", seat.card.top >= 1340);
    }

    /** Every control of a phone screen, at the card sizes the overlay asks for. */
    @Test public void noSeatEverTouchesTheControlTheToolbarOrTheBars() {
        List<Box> screen = Arrays.asList(
            new Box(0, 60, 1080, 150),          // the status bar
            new Box(880, 66, 1000, 144),        // the sessions badge inside it
            new Box(0, 1500, 1080, 1620),       // the extra keys row
            new Box(0, 1620, 1080, 2280),       // the keyboard
            new Box(0, 150, 1080, 1500),        // the pane
            new Box(940, 1400, 1060, 1490),     // a small control in a corner
            new Box(0, 700, 140, 1600));        // a dock that is a rail
        for (Box target : screen) {
            for (float[] size : new float[][] {{600, 320}, {450, 240}, {540, 700}}) {
                List<Box> others = new ArrayList<>(screen);
                others.remove(target);
                Box toolbar = target.cy() > SAFE.cy() ? TOP_TOOLBAR : BOTTOM_TOOLBAR;
                Request r = request(target, toolbar, others, size[0], size[1]);
                Result seat = HelpExplorePlacement.place(r);
                if (!seat.fits()) continue;
                assertLegal(r, seat);
            }
        }
    }

    @Test public void aScreenWithNoRoomSaysSoRatherThanShrinkTheText() {
        Box safe = new Box(0, 0, 300, 300);
        Box target = new Box(0, 0, 300, 220);
        Request r = new Request(safe, target, Collections.singletonList(new Box(0, 240, 300, 300)),
            Collections.<Box>emptyList(), 280, 200, 20);
        Result seat = HelpExplorePlacement.place(r);
        assertFalse(seat.fits());
        assertSame(Result.NONE, seat);
        assertNull(seat.card);
    }

    @Test public void aCardIsNeverResizedToMakeItFit() {
        Request r = request(new Box(0, 60, 1080, 150), BOTTOM_TOOLBAR);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals(600, seat.card.width(), 0.01f);
        assertEquals(320, seat.card.height(), 0.01f);
    }

    @Test public void theSameQuestionAlwaysGetsTheSameSeat() {
        Request r = request(new Box(400, 900, 700, 1100), BOTTOM_TOOLBAR);
        Result first = HelpExplorePlacement.place(r);
        Result second = HelpExplorePlacement.place(r);
        assertEquals(first.seat, second.seat);
        assertEquals(first.card.left, second.card.left, 0.01f);
        assertEquals(first.card.top, second.card.top, 0.01f);
    }

    /** Another control is only covered when there is nowhere clear left to sit. */
    @Test public void aClearSeatBeatsOneOverAnotherControl() {
        Box target = new Box(0, 1000, 1080, 1120);
        Box below = new Box(0, 1150, 1080, 1500);
        Request r = request(target, BOTTOM_TOOLBAR, Collections.singletonList(below), 600, 320);
        Result seat = HelpExplorePlacement.place(r);
        assertEquals("under is taken, so over it is", Seat.ABOVE, seat.seat);
        assertLegal(r, seat);
        assertFalse("a clear seat was available", overlaps(seat.card, below));
    }

    @Test public void aCrowdedScreenStillSeatsTheCardSomewhere() {
        Box target = new Box(0, 60, 1080, 150);
        List<Box> others = Arrays.asList(new Box(0, 150, 1080, 2140));
        Request r = request(target, BOTTOM_TOOLBAR, others, 600, 320);
        Result seat = HelpExplorePlacement.place(r);
        assertLegal(r, seat);
        assertTrue("the card had to lie over another control", overlaps(seat.card, others.get(0)));
    }

    @Test public void nothingIsSeatedWithoutACardToSeat() {
        assertFalse(HelpExplorePlacement.place(null).fits());
        assertFalse(HelpExplorePlacement.place(
            request(new Box(0, 60, 1080, 150), BOTTOM_TOOLBAR, Collections.<Box>emptyList(), 0, 0))
            .fits());
    }
}
