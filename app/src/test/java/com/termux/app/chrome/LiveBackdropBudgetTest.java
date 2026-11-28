package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;

/**
 * Live blur's memory budget (docs/agents/performance.md rule 2): at most
 * {@link LiveBackdrop#MAX_LIVE_SURFACES} pictures, each one quarter-scale blur layer and a pages
 * layer of whole pages, under {@link LiveBackdrop#BUDGET_BYTES} in all, and every picture released when Live goes off or a surface hides.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class LiveBackdropBudgetTest {

    @Test
    public void theBudgetIsEightQuarterScaleScreens() {
        assertEquals(8, LiveBackdrop.MAX_LIVE_SURFACES);
        assertEquals(360L * 800L * 4L, LiveBackdrop.MAX_LAYER_BYTES);
        // The pages layers of every surface share one pool (GlassPagePool), sized by RAM.
        assertEquals(4, LiveBackdrop.PAGE_LAYER_SLOTS);
        // The worst case in all: eight blur layers and the quarter-scale pool, 8 x 1,152,000 +
        // 6,291,456 = 15,507,456 B (14.8 MiB). It replaced 82,944,000 B (eight full pages layers).
        assertEquals(8L * 1_152_000L + 6L * 1024L * 1024L, LiveBackdrop.BUDGET_BYTES);
        assertTrue("under 18 MiB", LiveBackdrop.BUDGET_BYTES < 18L * 1024L * 1024L);
    }

    /**
     * Review P2 (terl-integration-5): the control bar's refraction pass runs at
     * {@link TerlGlassWear#REFRACTION_SCALE} and counts in the bar picture's own share: its
     * quarter-scale blur layer and the pass's layer together under {@link LiveBackdrop#MAX_LAYER_BYTES}
     * at the worst bar on the budget's screen (1440 px wide, about 3.5 px a dp: two rows, the
     * largest corners' flush reach under it), so the eight pictures stay inside
     * {@link LiveBackdrop#BUDGET_BYTES}. Measured: a full-size pass would not fit.
     */
    @Test
    public void theBarsRefractionPassFitsInsideItsPicturesShare() {
        int width = LiveBackdrop.BUDGET_SCREEN_WIDTH;
        float density = 3.5f;
        int height = Math.round((com.termux.app.controlbar.ControlBarPlacement.HEIGHT_DP + 48f + 1f)
            * density);
        long blur = LiveBackdrop.layerBytes(width, height);
        long pass = TerlGlassWear.refractionBytes(width, height);
        assertEquals((width / 2L) * ((height + 1L) / 2L) * 4L, pass);
        assertTrue("blur " + blur + " B and pass " + pass + " B inside the share",
            blur + pass <= LiveBackdrop.MAX_LAYER_BYTES);
        assertTrue("a full-size pass would not fit", blur + (long) width * height * 4L
            > LiveBackdrop.MAX_LAYER_BYTES);
    }

    /**
     * The accounting gap (live window residency section 1.5): a Live surface's effect node is
     * drawn offscreen at full resolution, the surface and its blur margin each side, and
     * {@link LiveBackdrop#BUDGET_BYTES} never counted it. The design's table, on pong (1080 x 2412,
     * Windows Blur 20 dp = 53 px of margin).
     */
    @Test
    public void theEffectLayerIsFullSizeWithItsMarginEachSide() {
        assertEquals(9_179_456L, LiveBackdrop.effectLayerBytes(1038, 1900, 53));
        assertEquals(4_926_736L, LiveBackdrop.effectLayerBytes(508, 1900, 53));
        assertEquals(6_195_664L, LiveBackdrop.effectLayerBytes(1080, 1200, 53));
        assertEquals(1_997_224L, LiveBackdrop.effectLayerBytes(1080, 315, 53));
        // Its blur layer, a quarter on each side, is about a sixteenth of it.
        assertEquals(574_288L, LiveBackdrop.layerBytes(1038 + 106, 1900 + 106));
        assertEquals(0L, LiveBackdrop.effectAreaPx(0, 0, 0));
    }

    /** Not even a small Float's effect layer fits in a slot's share: it must be counted apart. */
    @Test
    public void aSlotsShareNeverCoveredTheEffectLayer() {
        assertTrue(LiveBackdrop.effectLayerBytes(700, 900, 53) > LiveBackdrop.MAX_LAYER_BYTES);
        // Eight Live Floats of 1080 x 1200 hold 49.6 MB of effect layers beside the 15.5 MB counted.
        assertTrue(8L * LiveBackdrop.effectLayerBytes(1080, 1200, 53) > 3L * LiveBackdrop.BUDGET_BYTES);
    }

    /**
     * The resident budget counts the effect layers by RAM tier: on the budget screen it is
     * {@link LiveBackdrop#BUDGET_BYTES} plus the tier's screens of 4-byte pixels.
     */
    @Test
    public void theResidentBudgetCountsTheEffectAreaByTier() {
        long screen = (long) LiveBackdrop.BUDGET_SCREEN_WIDTH * LiveBackdrop.BUDGET_SCREEN_HEIGHT * 4L;
        long gib = 1024L * 1024L * 1024L;
        assertEquals(LiveBackdrop.BUDGET_BYTES + Math.round(2.5 * screen),
            LiveBackdrop.residentBudgetBytes(com.termux.app.glass.GlassResidency.tier(12L * gib, false)));
        assertEquals(LiveBackdrop.BUDGET_BYTES + Math.round(0.5 * screen),
            LiveBackdrop.residentBudgetBytes(com.termux.app.glass.GlassResidency.tier(3L * gib, false)));
    }

    /**
     * The typical case at rest on pong (Scrolling, two half tiles, one Float, the bar) fits the
     * pong tier with the tiles on the parity route; the worst crossing with every window on the
     * effect route would not, which is why tiles over the ground draw the stored frame.
     */
    @Test
    public void tilesOnTheParityRouteKeepTheTypicalCaseInsidePongsTier() {
        long gib = 1024L * 1024L * 1024L;
        com.termux.app.glass.GlassResidency.Tier pong = com.termux.app.glass.GlassResidency.tier(12L * gib, false);
        long budget = com.termux.app.glass.GlassResidency.effectAreaBudgetPx(pong, 1080, 2412, 0L);
        long floatArea = LiveBackdrop.effectAreaPx(1080, 1200, 53);
        long barArea = LiveBackdrop.effectAreaPx(1080, 315, 53);
        long halfTile = LiveBackdrop.effectAreaPx(508, 1900, 53);
        assertTrue(floatArea + barArea <= budget);
        // Every window on the effect route in a crossing: 12 Dwindle sixths, 5 Floats and the bar.
        long worst = 12L * LiveBackdrop.effectAreaPx(508, 620, 53)
            + 5L * LiveBackdrop.effectAreaPx(700, 900, 53) + barArea;
        assertTrue(worst > budget);
        assertTrue(2L * halfTile + floatArea + barArea < worst);
    }

    @Test
    public void aLayerIsAQuarterOnEachSideRoundedUp() {
        assertEquals(1L * 1L * 4L, LiveBackdrop.layerBytes(1, 1));
        assertEquals(270L * 75L * 4L, LiveBackdrop.layerBytes(1080, 297));
    }

    /**
     * A swipe's page layer comes out of its picture's own share: the dock's blur layer and its
     * page layer, padded a width either way, together under {@link LiveBackdrop#MAX_LAYER_BYTES},
     * so eight pictures stay inside {@link LiveBackdrop#BUDGET_BYTES}. A surface as large as the
     * screen has no room for one and keeps one layer.
     */
    @Test
    public void aPageLayerFitsInsideItsPicturesShare() {
        int width = LiveBackdrop.BUDGET_SCREEN_WIDTH;
        int height = LiveBackdrop.BUDGET_SCREEN_HEIGHT;
        long dock = LiveBackdrop.layerBytes(width, 400);
        int padX = BackdropLayers.pad(dock, width, 400, true, width);
        assertEquals(width, padX);
        int padY = BackdropLayers.pad(dock, width + 2 * padX, 400, false, 400);
        assertTrue(padY >= 0);
        assertTrue(dock + LiveBackdrop.layerBytes(width + 2 * padX, 400 + 2 * padY)
            <= LiveBackdrop.MAX_LAYER_BYTES);
        long panel = LiveBackdrop.layerBytes(width, height);
        assertEquals(-1, BackdropLayers.pad(panel, width, height, true, width));
        assertFalse(BackdropLayers.fits(panel, 1, 1));
    }

    @Test
    public void aNinthSurfaceKeepsItsStoredFrame() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        LiveBackdrop backdrop = new LiveBackdrop(root);
        View[] surfaces = new View[LiveBackdrop.MAX_LIVE_SURFACES + 1];
        for (int i = 0; i < surfaces.length; i++) {
            surfaces[i] = new View(context);
            root.addView(surfaces[i]);
        }
        for (int i = 0; i < LiveBackdrop.MAX_LIVE_SURFACES; i++) {
            assertNotNull(backdrop.attach(surfaces[i], 40f));
        }
        assertNull(backdrop.attach(surfaces[LiveBackdrop.MAX_LIVE_SURFACES], 40f));
        assertEquals(LiveBackdrop.MAX_LIVE_SURFACES, backdrop.size());
    }

    @Test
    public void aSurfaceAttachedTwiceKeepsOnePicture() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        View surface = new View(context);
        root.addView(surface);
        LiveBackdrop backdrop = new LiveBackdrop(root);
        LivePicture first = backdrop.attach(surface, 40f);
        assertSame(first, backdrop.attach(surface, 60f));
        assertEquals(1, backdrop.size());
        assertTrue(backdrop.shows(first.drawable()));
    }

    @Test
    public void detachAndReleaseAllLetEveryPictureGo() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        View a = new View(context);
        View b = new View(context);
        root.addView(a);
        root.addView(b);
        LiveBackdrop backdrop = new LiveBackdrop(root);
        LivePicture pa = backdrop.attach(a, 40f);
        backdrop.attach(b, 40f);
        backdrop.detach(a);
        assertEquals(1, backdrop.size());
        assertFalse(backdrop.shows(pa.drawable()));
        assertFalse(pa.holdsLayer());
        backdrop.releaseAll();
        assertEquals(0, backdrop.size());
    }
}
