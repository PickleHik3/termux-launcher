package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A Home float's drag stays inside its room ({@link HomeFloatPolicy#clampInRoom}): the slice 4
 * pass found a full-width float in landscape dragged left until its titlebar and text were
 * clipped off the screen, because the window floats' clamp lets a float hang off sideways while
 * 48 dp of its handle stays on screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFloatRoomClampTest {

    private static final float EPS = 1e-4f;
    private static final float MIN_W = 240f;
    private static final float MIN_H = 180f;

    /** A portrait room and a landscape one, in px. */
    private static final float[][] ROOMS = {{1040f, 1700f}, {2200f, 860f}};

    private static RectF move(RectF frac, float dx, float dy, float[] room) {
        RectF candidate = new RectF(frac);
        candidate.offset(dx, dy);
        return HomeFloatPolicy.clampInRoom(candidate, room[0], room[1], MIN_W, MIN_H, false);
    }

    @Test
    public void aFloatAsWideAsItsRoomCannotLeaveItOnEitherSideInEitherOrientation() {
        RectF full = new RectF(0f, 0.1f, 1f, 0.8f);
        for (float[] room : ROOMS) {
            for (float dx : new float[]{-0.9f, -0.3f, -0.01f, 0.01f, 0.3f, 0.9f}) {
                RectF moved = move(full, dx, 0f, room);
                assertEquals("left edge stays at the room's, dx=" + dx, 0f, moved.left, EPS);
                assertEquals("right edge stays at the room's, dx=" + dx, 1f, moved.right, EPS);
                assertEquals("and the move keeps its height", 0.7f, moved.height(), EPS);
            }
        }
    }

    @Test
    public void aNarrowerFloatMovesToTheRoomsEdgesButNoFurther() {
        RectF half = new RectF(0.25f, 0.2f, 0.75f, 0.6f);
        for (float[] room : ROOMS) {
            RectF left = move(half, -2f, 0f, room);
            assertEquals(0f, left.left, EPS);
            assertEquals("its size kept", 0.5f, left.width(), EPS);
            RectF right = move(half, 2f, 0f, room);
            assertEquals(1f, right.right, EPS);
            assertEquals(0.5f, right.width(), EPS);
            RectF partWay = move(half, -0.1f, 0f, room);
            assertEquals("a move short of the edge is the hand's own", 0.15f, partWay.left, EPS);
            RectF up = move(half, 0f, -2f, room);
            assertEquals("not above the room's top either", 0f, up.top, EPS);
            RectF down = move(half, 0f, 2f, room);
            assertEquals("nor below its bottom", 1f, down.bottom, EPS);
        }
    }

    @Test
    public void theTitlebarAlwaysStaysWithinTheRoom() {
        RectF[] shapes = {new RectF(0f, 0f, 1f, 1f), new RectF(0.1f, 0.1f, 0.9f, 0.5f),
            new RectF(0.3f, 0.4f, 0.5f, 0.6f)};
        float[] steps = {-3f, -1f, -0.4f, 0f, 0.4f, 1f, 3f};
        for (float[] room : ROOMS) {
            for (RectF shape : shapes) {
                for (float dx : steps) {
                    for (float dy : steps) {
                        RectF moved = move(shape, dx, dy, room);
                        // The titlebar is the float's top strip, as wide as the float.
                        assertTrue(moved.left >= -EPS && moved.right <= 1f + EPS);
                        assertTrue(moved.top >= -EPS && moved.top < 1f);
                        assertTrue(moved.bottom <= 1f + EPS);
                    }
                }
            }
        }
    }

    @Test
    public void aResizePastTheEdgeStopsThereWithoutShovingTheFloat() {
        float[] landscape = ROOMS[1];
        RectF candidate = new RectF(0.3f, 0.2f, 1.4f, 1.3f);
        RectF resized = HomeFloatPolicy.clampInRoom(candidate, landscape[0], landscape[1],
            MIN_W, MIN_H, true);
        assertEquals("the corner it was not holding stays", 0.3f, resized.left, EPS);
        assertEquals(0.2f, resized.top, EPS);
        assertEquals(1f, resized.right, EPS);
        assertEquals(1f, resized.bottom, EPS);

        RectF tiny = HomeFloatPolicy.clampInRoom(new RectF(0.5f, 0.5f, 0.5f, 0.5f),
            landscape[0], landscape[1], MIN_W, MIN_H, true);
        assertEquals("never under the minimum size", MIN_W / landscape[0], tiny.width(), EPS);
        assertEquals(MIN_H / landscape[1], tiny.height(), EPS);
    }

    @Test
    public void aFloatLargerThanItsRoomIsTheRoom() {
        for (float[] room : ROOMS) {
            RectF moved = move(new RectF(-0.2f, -0.1f, 1.3f, 1.2f), 0f, 0f, room);
            assertEquals(0f, moved.left, EPS);
            assertEquals(0f, moved.top, EPS);
            assertEquals(1f, moved.right, EPS);
            assertEquals(1f, moved.bottom, EPS);
        }
    }
}
