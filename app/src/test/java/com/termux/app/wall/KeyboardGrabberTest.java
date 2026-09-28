package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The keyboard swipe's grabber: a short pill centred on the page's bottom edge and wholly outside
 * it, so it never covers the page's text; a little wider and brighter under a finger, drawn a
 * short way along the swipe with resistance, and scaled with a sunk page.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class KeyboardGrabberTest {

    private static final float DENSITY = 2.75f;
    private static final float EPS = 0.01f;

    @Test
    public void atRestItIsAShortPillCentredOnTheBottomEdgeAndOutsideThePage() {
        RectF pill = new RectF();
        KeyboardGrabber.bounds(pill, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        assertEquals(540f, pill.centerX(), EPS);
        assertEquals(KeyboardGrabber.WIDTH_DP * DENSITY, pill.width(), EPS);
        assertEquals(KeyboardGrabber.THICKNESS_DP * DENSITY, pill.height(), EPS);
        assertTrue("never over the page's own text", pill.top >= 1800f);
        // Short: a sheet grabber, not a bar.
        assertTrue(pill.width() < 1080f * 0.1f);
    }

    @Test
    public void underAFingerItWidensAndBrightens() {
        RectF rest = new RectF();
        RectF pressed = new RectF();
        KeyboardGrabber.bounds(rest, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        KeyboardGrabber.bounds(pressed, 540f, 1800f, 1f, 0f, 1f, DENSITY);
        assertEquals(KeyboardGrabber.PRESSED_EXTRA_WIDTH_DP * DENSITY,
            pressed.width() - rest.width(), EPS);
        assertEquals(rest.centerX(), pressed.centerX(), EPS);
        assertEquals(KeyboardGrabber.REST_ALPHA, KeyboardGrabber.alpha(0f), EPS);
        assertEquals(KeyboardGrabber.PRESSED_ALPHA, KeyboardGrabber.alpha(1f), EPS);
        assertTrue("low emphasis at rest", KeyboardGrabber.alpha(0f) < 0.5f);
        assertTrue(KeyboardGrabber.alpha(1f) > KeyboardGrabber.alpha(0f));
    }

    @Test
    public void itFollowsTheSwipeAShortWayWithResistance() {
        assertEquals(-100f * KeyboardGrabber.TRACK_FRACTION,
            KeyboardGrabber.trackPx(-100f, DENSITY), EPS);
        float max = KeyboardGrabber.TRACK_MAX_DP * DENSITY;
        assertEquals(-max, KeyboardGrabber.trackPx(-5000f, DENSITY), EPS);
        assertEquals(max, KeyboardGrabber.trackPx(5000f, DENSITY), EPS);
        assertEquals(0f, KeyboardGrabber.trackPx(Float.NaN, DENSITY), EPS);
        RectF moved = new RectF();
        RectF rest = new RectF();
        KeyboardGrabber.bounds(rest, 540f, 1800f, 1f, 0f, 1f, DENSITY);
        KeyboardGrabber.bounds(moved, 540f, 1800f, 1f, -20f, 1f, DENSITY);
        assertEquals(-20f, moved.centerY() - rest.centerY(), EPS);
    }

    @Test
    public void itShrinksWithASunkPage() {
        RectF flush = new RectF();
        RectF sunk = new RectF();
        KeyboardGrabber.bounds(flush, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        KeyboardGrabber.bounds(sunk, 540f, 1746f, 0f, 0f, PageSink.SCALE, DENSITY);
        assertEquals(flush.width() * PageSink.SCALE, sunk.width(), EPS);
        assertTrue(sunk.top >= 1746f);
        // A nonsense scale draws it at full size rather than not at all.
        RectF broken = new RectF();
        KeyboardGrabber.bounds(broken, 540f, 1800f, 0f, 0f, Float.NaN, DENSITY);
        assertEquals(flush.width(), broken.width(), EPS);
    }
}
