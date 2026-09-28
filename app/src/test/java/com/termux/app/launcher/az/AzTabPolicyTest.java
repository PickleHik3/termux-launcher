package com.termux.app.launcher.az;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.CornerZones;
import com.termux.app.chrome.OnGlass;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Where the minimised index's pull tab stands, what takes it, and where the letters slide.
 * Robolectric only for the colour arithmetic the tab's letter is made legible with.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AzTabPolicyTest {

    private static final float D = 2f;
    private static final float SCREEN_W = 400f * D;
    /** The canvas inside the screen: a side gap each side, the status bar over, the dock under. */
    private static final AzTabPolicy.Box CANVAS =
        new AzTabPolicy.Box(8f * D, 60f * D, 392f * D, 640f * D);
    private static final float W = CANVAS.width();
    private static final float H = CANVAS.height();

    @Test
    public void aSideIndexHugsItsOwnSideOfTheScreenPastTheCanvasCorner() {
        AzTabPolicy.Placement right = AzTabPolicy.placeOnScreen(Edge.RIGHT, false, CANVAS,
            SCREEN_W, D);
        assertSame(Edge.RIGHT, right.side);
        assertEquals("flush against the screen's edge", SCREEN_W, right.visual.right, 0f);
        assertEquals(SCREEN_W, right.touch.right, 0f);
        assertEquals(AzTabPolicy.TAB_THICKNESS_DP * D, right.visual.width(), 0f);
        assertEquals(AzTabPolicy.TAB_LENGTH_DP * D, right.visual.height(), 0f);
        assertEquals(CANVAS.top + AzTabPolicy.leadInPx(D), right.touch.top, 0f);
        assertTrue(right.touch.top <= right.visual.top
            && right.visual.bottom <= right.touch.bottom);

        AzTabPolicy.Placement left = AzTabPolicy.placeOnScreen(Edge.LEFT, true, CANVAS,
            SCREEN_W, D);
        assertSame("a column keeps its own side whichever way text runs", Edge.LEFT, left.side);
        assertEquals(0f, left.visual.left, 0f);
        assertEquals(0f, left.touch.left, 0f);
    }

    @Test
    public void aRowIndexHugsItsLeadingSideAtItsOwnEndOfTheCanvas() {
        AzTabPolicy.Placement bottom = AzTabPolicy.placeOnScreen(Edge.BOTTOM, false, CANVAS,
            SCREEN_W, D);
        assertSame(Edge.LEFT, bottom.side);
        assertEquals(0f, bottom.visual.left, 0f);
        assertEquals("a bottom row leads from the dock's end",
            CANVAS.bottom - AzTabPolicy.leadInPx(D), bottom.touch.bottom, 0f);

        AzTabPolicy.Placement top = AzTabPolicy.placeOnScreen(Edge.TOP, true, CANVAS,
            SCREEN_W, D);
        assertSame("a right-to-left row leads from the right", Edge.RIGHT, top.side);
        assertEquals(SCREEN_W, top.visual.right, 0f);
        assertEquals(CANVAS.top + AzTabPolicy.leadInPx(D), top.touch.top, 0f);
    }

    @Test
    public void theTabStandsOutsideTheCanvasAsFarAsItsSideGapAllows() {
        // The side gap is 8dp and the half-pill 20dp: it reaches 12dp over the pane and no more.
        AzTabPolicy.Placement right = AzTabPolicy.placeOnScreen(Edge.RIGHT, false, CANVAS,
            SCREEN_W, D);
        assertEquals((AzTabPolicy.TAB_THICKNESS_DP - 8f) * D,
            CANVAS.right - right.visual.left, 1e-3f);
    }

    @Test
    public void aTopRowsTabIsASliverOverThePaneWhereTheTextBegins() {
        // The side gap is 8dp: the top row's half-pill reaches 4dp past it, not the 12dp a side
        // index's does, and the touch area stays a full target.
        for (boolean rtl : new boolean[] {false, true}) {
            AzTabPolicy.Placement top = AzTabPolicy.placeOnScreen(Edge.TOP, rtl, CANVAS,
                SCREEN_W, D);
            assertEquals("rtl=" + rtl, (8f + AzTabPolicy.TOP_TAB_REACH_DP) * D,
                top.visual.width(), 1e-3f);
            float overPane = rtl ? CANVAS.right - top.visual.left
                : top.visual.right - CANVAS.left;
            assertEquals(AzTabPolicy.TOP_TAB_REACH_DP * D, overPane, 1e-3f);
            assertEquals(AzTabPolicy.TOUCH_THICKNESS_DP * D, top.touch.width(), 0f);
            assertEquals(AzTabPolicy.TAB_LENGTH_DP * D, top.visual.height(), 0f);
        }
        AzTabPolicy.Placement bottom = AzTabPolicy.placeOnScreen(Edge.BOTTOM, false, CANVAS,
            SCREEN_W, D);
        assertEquals("the bottom row's tab is unchanged", AzTabPolicy.TAB_THICKNESS_DP * D,
            bottom.visual.width(), 0f);
    }

    @Test
    public void aTopRowsTabKeepsALegibleFloorAndNeverPassesTheFullTab() {
        AzTabPolicy.Box flush = new AzTabPolicy.Box(0f, 60f * D, SCREEN_W, 640f * D);
        assertEquals(AzTabPolicy.TOP_TAB_MIN_THICKNESS_DP * D,
            AzTabPolicy.visualThicknessPx(Edge.TOP, Edge.LEFT, flush, SCREEN_W, D), 1e-3f);
        AzTabPolicy.Box wide = new AzTabPolicy.Box(30f * D, 60f * D, SCREEN_W, 640f * D);
        assertEquals(AzTabPolicy.TAB_THICKNESS_DP * D,
            AzTabPolicy.visualThicknessPx(Edge.TOP, Edge.LEFT, wide, SCREEN_W, D), 1e-3f);
        assertEquals(AzTabPolicy.TAB_THICKNESS_DP * D,
            AzTabPolicy.visualThicknessPx(Edge.LEFT, Edge.LEFT, flush, SCREEN_W, D), 1e-3f);
    }

    @Test
    public void theTouchAreaIsAFullTargetEveryWay() {
        for (Edge edge : Edge.values()) {
            for (boolean rtl : new boolean[] {false, true}) {
                AzTabPolicy.Placement tab = AzTabPolicy.placeOnScreen(edge, rtl, CANVAS,
                    SCREEN_W, D);
                assertTrue(edge + " is 48dp at least",
                    Math.min(tab.touch.width(), tab.touch.height()) >= 48f * D);
            }
        }
    }

    @Test
    public void theTabNeverTouchesTheCanvasCornerSquares() {
        float corner = CornerZones.paneSizePx(D);
        for (Edge edge : Edge.values()) {
            for (boolean rtl : new boolean[] {false, true}) {
                AzTabPolicy.Placement tab = AzTabPolicy.placeOnScreen(edge, rtl, CANVAS,
                    SCREEN_W, D);
                assertTrue(edge + " rtl=" + rtl + " clear of the top corners",
                    tab.touch.top >= CANVAS.top + corner);
                assertTrue(edge + " rtl=" + rtl + " clear of the bottom corners",
                    tab.touch.bottom <= CANVAS.bottom - corner);
            }
        }
    }

    @Test
    public void aShortCanvasGivesUpTheLeadInBeforeTheTab() {
        AzTabPolicy.Box shortCanvas = new AzTabPolicy.Box(0f, 100f * D, W, 170f * D);
        AzTabPolicy.Placement tab = AzTabPolicy.placeOnScreen(Edge.LEFT, false, shortCanvas,
            SCREEN_W, D);
        assertEquals(AzTabPolicy.TOUCH_LENGTH_DP * D, tab.touch.height(), 0f);
        assertEquals(shortCanvas.bottom, tab.touch.bottom, 0f);

        AzTabPolicy.Box tiny = new AzTabPolicy.Box(0f, 100f * D, W, 130f * D);
        AzTabPolicy.Placement small = AzTabPolicy.placeOnScreen(Edge.LEFT, false, tiny,
            SCREEN_W, D);
        assertEquals(tiny.top, small.touch.top, 0f);
        assertEquals(tiny.bottom, small.touch.bottom, 0f);
        assertTrue(small.visual.height() <= 30f * D);
    }

    @Test
    public void anEmptyCanvasHasNothingToHit() {
        AzTabPolicy.Placement tab = AzTabPolicy.placeOnScreen(Edge.BOTTOM, false,
            AzTabPolicy.EMPTY, SCREEN_W, D);
        assertTrue(tab.touch.isEmpty());
        assertFalse(tab.hit(0f, 0f));
    }

    @Test
    public void aFingerOnTheTabIsHandedToTheLettersMiddleLine() {
        float thickness = 29f * D;
        float margin = 10f * D;
        for (Edge edge : Edge.values()) {
            AzTabPolicy.Placement tab = AzTabPolicy.placeOnScreen(edge, false, CANVAS,
                SCREEN_W, D);
            AzTabPolicy.Box inCanvas = AzTabPolicy.revealBox(edge, W, H, thickness, margin,
                12f * D);
            AzTabPolicy.Box letters = new AzTabPolicy.Box(CANVAS.left + inCanvas.left,
                CANVAS.top + inCanvas.top, CANVAS.left + inCanvas.right,
                CANVAS.top + inCanvas.bottom);
            float x = (tab.visual.left + tab.visual.right) / 2f;
            float y = (tab.visual.top + tab.visual.bottom) / 2f;
            float[] shift = AzTabPolicy.shiftOntoLetters(edge, letters, x, y);
            float heardX = x + shift[0];
            float heardY = y + shift[1];
            if (edge.isOnSide()) {
                assertEquals(edge + ": along the column the finger stays put", y, heardY, 0f);
                assertEquals((letters.left + letters.right) / 2f, heardX, 1e-3f);
            } else {
                assertEquals(edge + ": along the row the finger stays put", x, heardX, 0f);
                assertEquals((letters.top + letters.bottom) / 2f, heardY, 1e-3f);
            }
        }
        assertArrayEquals(new float[] {0f, 0f},
            AzTabPolicy.shiftOntoLetters(Edge.BOTTOM, AzTabPolicy.EMPTY, 3f, 4f), 0f);
    }

    @Test
    public void withoutGlassTheTabIsItsBaseMadeSolid() {
        int base = Color.argb(120, 30, 40, 50);
        assertEquals("glass is the material, nothing under it", 0,
            AzTabPolicy.tabFill(true, base));
        assertEquals(OnGlass.opaque(base), AzTabPolicy.tabFill(false, base));
        assertEquals(255, Color.alpha(AzTabPolicy.tabFill(false, base)));
    }

    @Test
    public void theTabsLetterReadsAsBodyTextOnItsBase() {
        int[] bases = {Color.rgb(20, 22, 28), Color.rgb(240, 240, 236), Color.rgb(90, 110, 140)};
        // Seeds the letters might have been given for the dock's glass over a wallpaper: some of
        // them all but invisible on the tab's own base.
        int[] seeds = {Color.rgb(30, 30, 30), Color.rgb(235, 235, 235), Color.rgb(100, 120, 150)};
        for (int base : bases) {
            for (int seed : seeds) {
                int ink = AzTabPolicy.glyphInk(base, seed);
                assertTrue(Integer.toHexString(base) + "/" + Integer.toHexString(seed),
                    OnGlass.ratio(OnGlass.opaque(ink), base) >= OnGlass.TARGET_BODY_TEXT - 0.05d);
            }
        }
    }

    @Test
    public void theLettersComeOutAlongTheWholeEdgeInTheBarsOwnAir() {
        float thickness = 29f * D;
        float margin = 10f * D;
        float inset = 12f * D;
        AzTabPolicy.Box row = AzTabPolicy.revealBox(Edge.BOTTOM, W, H, thickness, margin, inset);
        assertEquals(new AzTabPolicy.Box(inset, H - margin - thickness, W - inset, H - margin),
            row);

        AzTabPolicy.Box top = AzTabPolicy.revealBox(Edge.TOP, W, H, thickness, margin, inset);
        assertEquals(margin, top.top, 0f);
        assertEquals(margin + thickness, top.bottom, 0f);

        AzTabPolicy.Box column = AzTabPolicy.revealBox(Edge.RIGHT, W, H, thickness, margin, inset);
        assertEquals(new AzTabPolicy.Box(W - margin - thickness, margin, W - margin, H - margin),
            column);
    }

    @Test
    public void tuckedTheLettersStandOneTravelPastTheirEdge() {
        float travel = AzTabPolicy.travelPx(29f, 10f);
        assertEquals(39f, travel, 0f);
        assertArrayEquals(new float[] {0f, 39f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 0f, travel), 0f);
        assertArrayEquals(new float[] {0f, -39f},
            AzTabPolicy.slideOffset(Edge.TOP, 0f, travel), 0f);
        assertArrayEquals(new float[] {-39f, 0f},
            AzTabPolicy.slideOffset(Edge.LEFT, 0f, travel), 0f);
        assertArrayEquals(new float[] {39f, 0f},
            AzTabPolicy.slideOffset(Edge.RIGHT, 0f, travel), 0f);
        assertArrayEquals(new float[] {0f, 0f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 1f, travel), 0f);
        assertArrayEquals(new float[] {0f, 19.5f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 0.5f, travel), 1e-4f);
    }
}
