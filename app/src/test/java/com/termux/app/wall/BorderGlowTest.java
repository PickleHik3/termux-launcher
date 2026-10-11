package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The hold's glow: lit the moment a finger lands where its hold could page, brighter over the
 * hold, full from the claim to the drag's end, and quickly gone once the press is given up; at
 * once both ways under reduced motion. It asks for frames only while it rises or fades.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class BorderGlowTest {

    private static final float EPS = 0.001f;
    private static final long HOLD = 400L;
    private static final float DENSITY = 3f;

    /** A host that counts the frames the glow asks for. */
    private static final class Host extends View {
        int frames;

        Host(Context context) {
            super(context);
        }

        @Override
        public void postInvalidateOnAnimation() {
            frames++;
        }
    }

    private static Host host() {
        return new Host(Robolectric.buildActivity(Activity.class).setup().get());
    }

    private static Canvas canvas() {
        return new Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888));
    }

    // ---- The timing ----------------------------------------------------------------------------

    @Test
    public void itRisesFromLitAtOnceToFullOverTheHold() {
        float first = BorderGlow.riseLevel(0L, HOLD);
        assertTrue("lit the moment the finger lands", first > 0f);
        assertEquals(BorderGlow.START_LEVEL, first, EPS);
        float mid = BorderGlow.riseLevel(HOLD / 2, HOLD);
        assertTrue(mid > first && mid < 1f);
        assertEquals(1f, BorderGlow.riseLevel(HOLD, HOLD), EPS);
        assertEquals("never past full", 1f, BorderGlow.riseLevel(HOLD * 3, HOLD), EPS);
        assertEquals("no hold: full", 1f, BorderGlow.riseLevel(0L, 0L), EPS);
    }

    @Test
    public void aFadeRunsFromWhereItStoodToNothing() {
        assertEquals(0.6f, BorderGlow.fadeLevel(0.6f, 0L), EPS);
        assertTrue(BorderGlow.fadeLevel(0.6f, BorderGlow.FADE_MS / 2) < 0.6f);
        assertEquals(0f, BorderGlow.fadeLevel(0.6f, BorderGlow.FADE_MS), EPS);
        assertEquals(0f, BorderGlow.fadeLevel(1f, BorderGlow.FADE_MS * 2), EPS);
        assertTrue("quick", BorderGlow.FADE_MS <= 200L);
    }

    @Test
    public void itsAlphaFollowsTheLevelAndThePagesOutline() {
        assertEquals(BorderGlow.FULL_ALPHA, BorderGlow.alpha(1f, 1f), EPS);
        assertEquals(BorderGlow.FULL_ALPHA / 2f, BorderGlow.alpha(1f, 0.5f), EPS);
        assertEquals(0f, BorderGlow.alpha(0f, 1f), EPS);
        assertEquals(0f, BorderGlow.alpha(1f, 0f), EPS);
    }

    // ---- The press -----------------------------------------------------------------------------

    @Test
    public void aHoldThatTakesIsFullFromTheClaimUntilTheDragEnds() {
        BorderGlow glow = new BorderGlow(host());
        glow.press(BorderDrag.Border.LEFT, 1000L, HOLD, false);
        assertTrue(glow.isHeld());
        assertEquals(BorderDrag.Border.LEFT, glow.side());
        assertEquals(BorderGlow.START_LEVEL, glow.level(1000L), EPS);
        assertTrue(glow.level(1200L) > glow.level(1000L));
        assertTrue("rising wants frames", glow.needsFrame(1200L));
        glow.claim();
        assertEquals(1f, glow.level(1300L), EPS);
        assertEquals("full stays full", 1f, glow.level(5000L), EPS);
        assertFalse("and still: no frames", glow.needsFrame(1300L));
        // The drag ends: a quick fade from full.
        glow.release(6000L, false);
        assertFalse(glow.isHeld());
        assertTrue(glow.isShowing());
        assertEquals(1f, glow.level(6000L), EPS);
        assertTrue(glow.needsFrame(6000L));
        assertEquals(0f, glow.level(6000L + BorderGlow.FADE_MS), EPS);
        assertFalse(glow.needsFrame(6000L + BorderGlow.FADE_MS));
    }

    @Test
    public void aPressWhereASlidePagesIsFullFromTheTouchWithNoRise() {
        BorderGlow glow = new BorderGlow(host());
        glow.light(BorderDrag.Border.BOTTOM);
        assertTrue(glow.isHeld());
        assertEquals(BorderDrag.Border.BOTTOM, glow.side());
        assertEquals("full at once", 1f, glow.level(0L), EPS);
        assertFalse("and still: no frames", glow.needsFrame(0L));
        // The slide claims: nothing changes.
        glow.claim();
        assertEquals(1f, glow.level(500L), EPS);
        // Handed to a swipe, or the drag ended: a quick fade from full.
        glow.release(600L, false);
        assertFalse(glow.isHeld());
        assertEquals(1f, glow.level(600L), EPS);
        assertTrue(glow.needsFrame(600L));
        assertEquals(0f, glow.level(600L + BorderGlow.FADE_MS), EPS);
        // Nothing to light off a border.
        BorderGlow none = new BorderGlow(host());
        none.light(BorderDrag.Border.NONE);
        assertFalse(none.isShowing());
    }

    @Test
    public void aPressGivenUpFadesFromWhereItHadRisenTo() {
        BorderGlow glow = new BorderGlow(host());
        glow.press(BorderDrag.Border.BOTTOM, 0L, HOLD, false);
        float at = glow.level(100L);
        glow.release(100L, false);
        assertEquals(at, glow.level(100L), EPS);
        assertEquals(0f, glow.level(100L + BorderGlow.FADE_MS), EPS);
        // A release of a glow no press holds changes nothing.
        glow.release(100L + BorderGlow.FADE_MS, false);
        assertEquals(0f, glow.level(100L + BorderGlow.FADE_MS), EPS);
    }

    @Test
    public void underReducedMotionItIsFullAtOnceAndGoneAtOnce() {
        BorderGlow glow = new BorderGlow(host());
        glow.press(BorderDrag.Border.TOP, 0L, HOLD, true);
        assertEquals("no ramp", 1f, glow.level(0L), EPS);
        assertFalse(glow.needsFrame(0L));
        glow.release(10L, true);
        assertFalse("no fade", glow.isShowing());
        assertEquals(0f, glow.level(10L), EPS);
        assertFalse(glow.needsFrame(10L));
    }

    @Test
    public void itAsksForFramesOnlyWhileItRisesOrFades() {
        Host host = host();
        BorderGlow glow = new BorderGlow(host);
        Canvas canvas = canvas();
        assertFalse("at rest: nothing", glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, 0L));
        assertEquals(0, host.frames);

        glow.press(BorderDrag.Border.RIGHT, 0L, HOLD, false);
        assertTrue(glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, 100L));
        assertEquals(1, host.frames);
        glow.claim();
        assertFalse("full and still", glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, 500L));
        assertEquals(1, host.frames);

        glow.release(600L, false);
        assertTrue(glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, 650L));
        assertEquals(2, host.frames);
        long done = 600L + BorderGlow.FADE_MS;
        assertFalse(glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, done));
        assertFalse("back at rest", glow.isShowing());
        assertFalse(glow.draw(canvas, 0f, 0f, 1000f, 2000f, 1f, 1f, done + 1000L));
        assertEquals("no frame once at rest", 2, host.frames);
    }

    // ---- Where it is drawn -----------------------------------------------------------------------

    @Test
    public void itLiesAlongTheWholeHeldEdgeFromTheClearanceOutIntoTheAir() {
        RectF band = new RectF();
        float inset = BorderGlow.INSET_DP * DENSITY;
        float spread = BorderGlow.SPREAD_DP * DENSITY;
        assertTrue(BorderGlow.band(band, BorderDrag.Border.BOTTOM, 100f, 200f, 1000f, 1800f, 1f,
            DENSITY));
        assertEquals(new RectF(100f, 1800f - inset, 1000f, 1800f + spread), band);
        assertTrue(BorderGlow.band(band, BorderDrag.Border.TOP, 100f, 200f, 1000f, 1800f, 1f,
            DENSITY));
        assertEquals(new RectF(100f, 200f - spread, 1000f, 200f + inset), band);
        assertTrue(BorderGlow.band(band, BorderDrag.Border.LEFT, 100f, 200f, 1000f, 1800f, 1f,
            DENSITY));
        assertEquals(new RectF(100f - spread, 200f, 100f + inset, 1800f), band);
        assertTrue(BorderGlow.band(band, BorderDrag.Border.RIGHT, 100f, 200f, 1000f, 1800f, 1f,
            DENSITY));
        assertEquals(new RectF(1000f - inset, 200f, 1000f + spread, 1800f), band);
        assertFalse(BorderGlow.band(band, BorderDrag.Border.NONE, 100f, 200f, 1000f, 1800f, 1f,
            DENSITY));
    }

    @Test
    public void itShrinksWithASunkPage() {
        RectF band = new RectF();
        float scale = PageSink.SCALE;
        assertTrue(BorderGlow.band(band, BorderDrag.Border.LEFT, 100f, 200f, 1000f, 1800f, scale,
            DENSITY));
        assertEquals(100f - BorderGlow.SPREAD_DP * DENSITY * scale, band.left, EPS);
        assertEquals(100f + BorderGlow.INSET_DP * DENSITY * scale, band.right, EPS);
        assertEquals(200f, band.top, EPS);
        assertEquals(1800f, band.bottom, EPS);
    }

    @Test
    public void itsEndsFadeOverTheCornerRadiusAndAShortEdgeStillPeaksOnce() {
        assertEquals("lit only through the clearance the side borders take",
            BorderDrag.SIDE_REACH_DP, BorderGlow.INSET_DP, EPS);
        float taper = BorderGlow.TAPER_DP * DENSITY;
        assertEquals(taper / 1600f, BorderGlow.taperStop(taper, 1600f), EPS);
        assertEquals("never past the middle", 0.5f, BorderGlow.taperStop(taper, taper), EPS);
        assertEquals(0.5f, BorderGlow.taperStop(taper, 0f), EPS);
    }
}
