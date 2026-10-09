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
 * The border swipes' grabber: a short pill centred on the page's bottom edge for the keyboard, or
 * its top edge for the status bar, and wholly outside it, so it never covers the page's text; a
 * little wider and brighter under a finger, drawn a short way along the swipe with resistance,
 * and scaled with a sunk page.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class BorderGrabberTest {

    private static final float DENSITY = 2.75f;
    private static final float EPS = 0.01f;

    @Test
    public void atRestItIsAShortPillCentredOnTheBottomEdgeAndOutsideThePage() {
        RectF pill = new RectF();
        BorderGrabber.bounds(pill, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        assertEquals(540f, pill.centerX(), EPS);
        assertEquals(BorderGrabber.WIDTH_DP * DENSITY, pill.width(), EPS);
        assertEquals(BorderGrabber.THICKNESS_DP * DENSITY, pill.height(), EPS);
        assertTrue("never over the page's own text", pill.top >= 1800f);
        // Short: a sheet grabber, not a bar.
        assertTrue(pill.width() < 1080f * 0.1f);
    }

    @Test
    public void underAFingerItWidensAndBrightens() {
        RectF rest = new RectF();
        RectF pressed = new RectF();
        BorderGrabber.bounds(rest, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        BorderGrabber.bounds(pressed, 540f, 1800f, 1f, 0f, 1f, DENSITY);
        assertEquals(BorderGrabber.PRESSED_EXTRA_WIDTH_DP * DENSITY,
            pressed.width() - rest.width(), EPS);
        assertEquals(rest.centerX(), pressed.centerX(), EPS);
        assertEquals(BorderGrabber.REST_ALPHA, BorderGrabber.alpha(0f), EPS);
        assertEquals(BorderGrabber.PRESSED_ALPHA, BorderGrabber.alpha(1f), EPS);
        assertTrue("low emphasis at rest", BorderGrabber.alpha(0f) < 0.5f);
        assertTrue(BorderGrabber.alpha(1f) > BorderGrabber.alpha(0f));
    }

    @Test
    public void itFollowsTheSwipeAShortWayWithResistance() {
        assertEquals(-100f * BorderGrabber.TRACK_FRACTION,
            BorderGrabber.trackPx(-100f, DENSITY), EPS);
        float max = BorderGrabber.TRACK_MAX_DP * DENSITY;
        assertEquals(-max, BorderGrabber.trackPx(-5000f, DENSITY), EPS);
        assertEquals(max, BorderGrabber.trackPx(5000f, DENSITY), EPS);
        assertEquals(0f, BorderGrabber.trackPx(Float.NaN, DENSITY), EPS);
        RectF moved = new RectF();
        RectF rest = new RectF();
        BorderGrabber.bounds(rest, 540f, 1800f, 1f, 0f, 1f, DENSITY);
        BorderGrabber.bounds(moved, 540f, 1800f, 1f, -20f, 1f, DENSITY);
        assertEquals(-20f, moved.centerY() - rest.centerY(), EPS);
    }

    @Test
    public void itShrinksWithASunkPage() {
        RectF flush = new RectF();
        RectF sunk = new RectF();
        BorderGrabber.bounds(flush, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        BorderGrabber.bounds(sunk, 540f, 1746f, 0f, 0f, PageSink.SCALE, DENSITY);
        assertEquals(flush.width() * PageSink.SCALE, sunk.width(), EPS);
        assertTrue(sunk.top >= 1746f);
        // A nonsense scale draws it at full size rather than not at all.
        RectF broken = new RectF();
        BorderGrabber.bounds(broken, 540f, 1800f, 0f, 0f, Float.NaN, DENSITY);
        assertEquals(flush.width(), broken.width(), EPS);
    }

    @Test
    public void onTheTopBorderItIsTheSamePillMirroredAboveTheEdge() {
        RectF bottom = new RectF();
        RectF top = new RectF();
        BorderGrabber.bounds(bottom, 540f, 1800f, 0f, 0f, 1f, DENSITY, false);
        BorderGrabber.bounds(top, 540f, 200f, 0f, 0f, 1f, DENSITY, true);
        assertEquals(540f, top.centerX(), EPS);
        assertEquals(bottom.width(), top.width(), EPS);
        assertEquals(bottom.height(), top.height(), EPS);
        assertTrue("never over the page's own text", top.bottom <= 200f);
        assertEquals("as far outside the top edge as the bottom one sits outside its own",
            bottom.centerY() - 1800f, 200f - top.centerY(), EPS);
        // The old signature is the bottom border's.
        RectF legacy = new RectF();
        BorderGrabber.bounds(legacy, 540f, 1800f, 0f, 0f, 1f, DENSITY);
        assertEquals(bottom, legacy);
    }

    @Test
    public void onTheTopBorderItFollowsTheSwipeAndShrinksWithASunkPage() {
        RectF rest = new RectF();
        RectF pulled = new RectF();
        BorderGrabber.bounds(rest, 540f, 200f, 1f, 0f, 1f, DENSITY, true);
        BorderGrabber.bounds(pulled, 540f, 200f, 1f, 20f, 1f, DENSITY, true);
        assertEquals("drawn down after a finger pulling the bar open", 20f,
            pulled.centerY() - rest.centerY(), EPS);
        RectF sunk = new RectF();
        BorderGrabber.bounds(sunk, 540f, 254f, 0f, 0f, PageSink.SCALE, DENSITY, true);
        RectF flush = new RectF();
        BorderGrabber.bounds(flush, 540f, 200f, 0f, 0f, 1f, DENSITY, true);
        assertEquals(flush.width() * PageSink.SCALE, sunk.width(), EPS);
        assertTrue(sunk.bottom <= 254f);
    }
}
