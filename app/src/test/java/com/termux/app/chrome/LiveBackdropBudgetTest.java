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

/**
 * Live blur's memory budget (docs/agents/performance.md rule 2): at most
 * {@link LiveBackdrop#MAX_LIVE_SURFACES} pictures, each one quarter-scale layer, under ten MiB in
 * all, and every picture released when Live goes off or a surface hides.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU)
public class LiveBackdropBudgetTest {

    @Test
    public void theBudgetIsEightQuarterScaleScreens() {
        assertEquals(8, LiveBackdrop.MAX_LIVE_SURFACES);
        assertEquals(360L * 800L * 4L, LiveBackdrop.MAX_LAYER_BYTES);
        assertEquals(8L * 360L * 800L * 4L, LiveBackdrop.BUDGET_BYTES);
        assertTrue("under ten MiB", LiveBackdrop.BUDGET_BYTES < 10L * 1024L * 1024L);
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
        int padX = WallSwipeGlassPolicy.pad(dock, width, 400, true, width);
        assertEquals(width, padX);
        int padY = WallSwipeGlassPolicy.pad(dock, width + 2 * padX, 400, false, 400);
        assertTrue(padY >= 0);
        assertTrue(dock + LiveBackdrop.layerBytes(width + 2 * padX, 400 + 2 * padY)
            <= LiveBackdrop.MAX_LAYER_BYTES);
        long panel = LiveBackdrop.layerBytes(width, height);
        assertEquals(-1, WallSwipeGlassPolicy.pad(panel, width, height, true, width));
        assertFalse(WallSwipeGlassPolicy.fits(panel, 1, 1));
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
