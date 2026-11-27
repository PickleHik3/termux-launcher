package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Float's place and size in its room (#421), through the public interface only. */
public class FloatGeometryTest {

    private static final float EPS = 0.001f;

    /** A phone's Workspace in portrait: 400 × 700 dp, a 30 dp cutout band on top, the bar's 80 dp below. */
    private static final FloatGeometry.Room PHONE = FloatGeometry.Room.of(400f, 700f, 0f, 30f, 0f, 80f);

    // ---- the room --------------------------------------------------------------------------------

    @Test
    public void theRoomIsTheAreaLessTheBandsKeptClear() {
        assertEquals(0f, PHONE.left, EPS);
        assertEquals(30f, PHONE.top, EPS);
        assertEquals(400f, PHONE.width, EPS);
        assertEquals(590f, PHONE.height, EPS);
    }

    @Test
    public void bandsWiderThanTheAreaLeaveAnEmptyRoomNotANegativeOne() {
        FloatGeometry.Room room = FloatGeometry.Room.of(100f, 100f, 80f, 80f, 80f, 80f);

        assertEquals(0f, room.width, EPS);
        assertEquals(0f, room.height, EPS);
    }

    // ---- a new Float -----------------------------------------------------------------------------

    @Test
    public void aNewFloatIsCentredAcrossAndStandsWhollyInTheRoom() {
        FloatGeometry.Box box = FloatGeometry.newFloat(PHONE, Collections.<FloatGeometry.Box>emptyList());

        assertEquals("centred across", PHONE.width - box.right(), box.left, EPS);
        assertInside(box, PHONE);
        assertTrue("narrower than the room", box.width < PHONE.width);
        assertTrue("at least the smallest a Float may be",
            box.width >= FloatGeometry.MIN_WIDTH_DP && box.height >= FloatGeometry.MIN_HEIGHT_DP);
    }

    @Test
    public void aNewFloatStepsClearOfOneAlreadyStandingWhereItWouldOpen() {
        FloatGeometry.Box first = FloatGeometry.newFloat(PHONE, Collections.<FloatGeometry.Box>emptyList());

        FloatGeometry.Box second = FloatGeometry.newFloat(PHONE, Collections.singletonList(first));

        assertEquals(first.left + FloatGeometry.CASCADE_DP, second.left, EPS);
        assertEquals(first.top + FloatGeometry.CASCADE_DP, second.top, EPS);
        assertInside(second, PHONE);
    }

    @Test
    public void manyNewFloatsAllStayInTheRoom() {
        List<FloatGeometry.Box> open = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            FloatGeometry.Box box = FloatGeometry.newFloat(PHONE, open);
            assertInside(box, PHONE);
            open.add(box);
        }
    }

    @Test
    public void aNewFloatInARoomSmallerThanTheSmallestFloatFillsTheRoom() {
        FloatGeometry.Room tiny = FloatGeometry.Room.of(100f, 90f, 0f, 0f, 0f, 0f);

        FloatGeometry.Box box = FloatGeometry.newFloat(tiny, Collections.<FloatGeometry.Box>emptyList());

        assertEquals(new FloatGeometry.Box(0f, 0f, 100f, 90f), box);
    }

    // ---- clamping --------------------------------------------------------------------------------

    @Test
    public void aBoxOffTheRoomIsPulledBackInWholeKeepingItsSize() {
        FloatGeometry.Box off = new FloatGeometry.Box(350f, 560f, 200f, 300f);

        FloatGeometry.Box box = FloatGeometry.clamp(off, PHONE);

        assertEquals(200f, box.width, EPS);
        assertEquals(300f, box.height, EPS);
        assertEquals(PHONE.width, box.right(), EPS);
        assertEquals(PHONE.height, box.bottom(), EPS);
    }

    @Test
    public void aBoxAboveOrLeftOfTheRoomComesBackToItsEdge() {
        FloatGeometry.Box box = FloatGeometry.clamp(new FloatGeometry.Box(-50f, -80f, 200f, 200f), PHONE);

        assertEquals(0f, box.left, EPS);
        assertEquals(0f, box.top, EPS);
    }

    @Test
    public void aBoxBiggerThanTheRoomShrinksToIt() {
        FloatGeometry.Box box = FloatGeometry.clamp(new FloatGeometry.Box(10f, 10f, 900f, 900f), PHONE);

        assertEquals(new FloatGeometry.Box(0f, 0f, PHONE.width, PHONE.height), box);
    }

    @Test
    public void aBoxSmallerThanTheSmallestFloatGrowsToIt() {
        FloatGeometry.Box box = FloatGeometry.clamp(new FloatGeometry.Box(10f, 10f, 20f, 20f), PHONE);

        assertEquals(FloatGeometry.MIN_WIDTH_DP, box.width, EPS);
        assertEquals(FloatGeometry.MIN_HEIGHT_DP, box.height, EPS);
    }

    @Test
    public void turningThePhoneKeepsAFloatInTheNewRoom() {
        FloatGeometry.Room landscape = FloatGeometry.Room.of(700f, 400f, 30f, 0f, 0f, 0f);
        FloatGeometry.Box portrait = new FloatGeometry.Box(40f, 300f, 320f, 260f);

        assertInside(FloatGeometry.clamp(portrait, landscape), landscape);
    }

    // ---- moving and resizing ---------------------------------------------------------------------

    @Test
    public void aMoveShiftsTheFloatAndStopsAtTheRoomsEdges() {
        FloatGeometry.Box start = new FloatGeometry.Box(100f, 100f, 200f, 200f);

        assertEquals(new FloatGeometry.Box(130f, 60f, 200f, 200f), FloatGeometry.moved(start, 30f, -40f, PHONE));
        FloatGeometry.Box far = FloatGeometry.moved(start, 1000f, 1000f, PHONE);
        assertEquals(PHONE.width, far.right(), EPS);
        assertEquals(PHONE.height, far.bottom(), EPS);
        assertEquals("size is kept", 200f, far.width, EPS);
    }

    @Test
    public void aResizeByTheCornerKeepsTheTopLeftAndStopsAtTheRoomAndTheSmallest() {
        FloatGeometry.Box start = new FloatGeometry.Box(100f, 100f, 200f, 200f);

        assertEquals(new FloatGeometry.Box(100f, 100f, 250f, 150f), FloatGeometry.resized(start, 50f, -50f, PHONE));
        FloatGeometry.Box big = FloatGeometry.resized(start, 1000f, 1000f, PHONE);
        assertEquals(100f, big.left, EPS);
        assertEquals(100f, big.top, EPS);
        assertEquals(PHONE.width, big.right(), EPS);
        assertEquals(PHONE.height, big.bottom(), EPS);
        FloatGeometry.Box small = FloatGeometry.resized(start, -1000f, -1000f, PHONE);
        assertEquals(FloatGeometry.MIN_WIDTH_DP, small.width, EPS);
        assertEquals(FloatGeometry.MIN_HEIGHT_DP, small.height, EPS);
    }

    // ---- the saved form --------------------------------------------------------------------------

    @Test
    public void aBoxRoundTripsThroughItsSavedForm() throws Exception {
        FloatGeometry.Box box = new FloatGeometry.Box(12.5f, 40f, 300f, 220.25f);

        JSONArray json = box.toJson();

        assertEquals(box, FloatGeometry.Box.fromJson(json));
    }

    @Test
    public void anUnreadableSavedBoxIsNone() throws Exception {
        assertNull(FloatGeometry.Box.fromJson(null));
        assertNull(FloatGeometry.Box.fromJson(new JSONArray("[1, 2, 3]")));
        assertNull(FloatGeometry.Box.fromJson(new JSONArray("[1, 2, 0, 50]")));
        assertNull(FloatGeometry.Box.fromJson(new JSONArray("[1, 2, \"x\", 50]")));
    }

    @Test
    public void boxesCompareByValue() {
        assertEquals(new FloatGeometry.Box(1f, 2f, 3f, 4f), new FloatGeometry.Box(1f, 2f, 3f, 4f));
        assertEquals(new FloatGeometry.Box(1f, 2f, 3f, 4f).hashCode(), new FloatGeometry.Box(1f, 2f, 3f, 4f).hashCode());
        assertNotEquals(new FloatGeometry.Box(1f, 2f, 3f, 4f), new FloatGeometry.Box(1f, 2f, 3f, 5f));
        assertFalse(new FloatGeometry.Box(1f, 2f, 3f, 4f).equals(null));
    }

    private static void assertInside(FloatGeometry.Box box, FloatGeometry.Room room) {
        assertTrue(box + " left of " + room, box.left >= -EPS);
        assertTrue(box + " above " + room, box.top >= -EPS);
        assertTrue(box + " right of " + room, box.right() <= room.width + EPS);
        assertTrue(box + " below " + room, box.bottom() <= room.height + EPS);
    }
}
