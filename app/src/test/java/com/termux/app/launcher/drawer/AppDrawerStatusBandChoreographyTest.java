package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerStatusBandChoreography.Result;

import org.junit.Test;

/**
 * The top band's recipe.
 *
 * <p>Reference geometry is the same 1080x2400 panel at 3x: the default style's pane is 96dp
 * expanded (288px) and 32dp compact (96px).
 */
public class AppDrawerStatusBandChoreographyTest {

    private static final float EPS = 1e-4f;

    private static final float EXPANDED = 288f;
    private static final float COMPACT = 96f;

    @Test
    public void barIsUntouchedAtTheDockAndFullyGoneAtTheOpenDrawer() {
        Result dock = AppDrawerStatusBandChoreography.resolve(0f, EXPANDED, COMPACT);
        assertEquals(0f, dock.translationY, 0f);
        assertEquals(0f, dock.clipTopPx, 0f);
        assertEquals(1f, dock.alpha, 0f);

        Result open = AppDrawerStatusBandChoreography.resolve(1f, EXPANDED, COMPACT);
        assertEquals(-EXPANDED, open.translationY, EPS);
        assertEquals(EXPANDED, open.clipTopPx, EPS);
        assertEquals(0f, open.alpha, EPS);
    }

    @Test
    public void expandedPaneReadsAsCompactBeforeItStartsLeaving() {
        // At the end of the collapse ramp the clip has eaten exactly the expanded extras, and the
        // remnant is the compact pane — that is what makes the collapse legible rather than a slide
        // of a tall slab.
        Result r = AppDrawerStatusBandChoreography.resolve(0.28f, EXPANDED, COMPACT);
        assertTrue("collapse must dominate this early", r.clipTopPx >= EXPANDED - COMPACT - EPS);
        assertEquals(COMPACT, EXPANDED - r.clipTopPx, 1.5f);
    }

    @Test
    public void alreadyCompactPaneOnlyEverSlides() {
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            Result r = AppDrawerStatusBandChoreography.resolve(p, COMPACT, COMPACT);
            // No collapse channel to run: the clip is the slide's ceiling and nothing else.
            assertEquals("p=" + p, -r.translationY, r.clipTopPx, EPS);
        }
    }

    @Test
    public void nothingEverDrawsAboveTheBandsOwnTopEdge() {
        // The clip is the ceiling: every ancestor of the pane sets clipChildren="false", so a
        // translation the clip does not cover would paint into the system status-bar inset strip
        // the plane cannot reach.
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            for (float height : new float[] {EXPANDED, COMPACT, 300f}) {
                Result r = AppDrawerStatusBandChoreography.resolve(p, height, COMPACT);
                assertTrue("p=" + p + " h=" + height, r.clipTopPx >= -r.translationY - EPS);
                assertTrue(r.clipTopPx <= height + EPS);
                assertTrue(r.translationY <= 0f);
            }
        }
    }

    @Test
    public void channelsAreMonotonicAndBounded() {
        float lastClip = -1f, lastSlide = -1f, lastAlpha = 2f;
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            Result r = AppDrawerStatusBandChoreography.resolve(p, EXPANDED, COMPACT);
            assertTrue("clip p=" + p, r.clipTopPx >= lastClip - EPS);
            assertTrue("slide p=" + p, -r.translationY >= lastSlide - EPS);
            assertTrue("alpha p=" + p, r.alpha <= lastAlpha + EPS);
            assertTrue(r.alpha >= 0f && r.alpha <= 1f);
            lastClip = r.clipTopPx;
            lastSlide = -r.translationY;
            lastAlpha = r.alpha;
        }
    }

    @Test
    public void fadeIsHeldBackUntilTheBarIsActuallyMoving() {
        // A bar that dissolved in place would read as a glitch; it has to be seen leaving first.
        Result early = AppDrawerStatusBandChoreography.resolve(0.20f, EXPANDED, COMPACT);
        assertEquals(1f, early.alpha, 0f);
        assertTrue(early.translationY < 0f);
    }

    @Test
    public void degenerateBandsAreSafe() {
        Result zero = AppDrawerStatusBandChoreography.resolve(0.5f, 0f, COMPACT);
        assertEquals(0f, zero.translationY, 0f);
        assertEquals(0f, zero.clipTopPx, 0f);
        // A compact height larger than the band clamps rather than producing a negative collapse.
        Result clamped = AppDrawerStatusBandChoreography.resolve(0.5f, COMPACT, EXPANDED);
        assertEquals(-COMPACT * 0.8f, clamped.translationY, 1f);
        assertTrue(clamped.clipTopPx <= COMPACT + EPS);
    }
}
