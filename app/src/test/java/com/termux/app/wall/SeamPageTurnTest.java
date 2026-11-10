package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.app.launcher.home.HomePageTransition;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;

/**
 * Across the seam between Home and the Workspace the pages move by the page transition, as Home's
 * own pages do (ADR 0015 §4, amendment 2026-11-04, reversing 2026-11-02's flat slide): under Slide
 * Home's page leaves by the finger's distance with the Workspace's beside it, flat; under any
 * other effect they are turned, scaled or faded on a layer for those frames. The wall still says
 * it is moving across the seam, which the chrome eases on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class SeamPageTurnTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2300;
    /** The finger's distance, give or take the wall's drag slop. */
    private static final float SLOP_PX = 2f;

    private PaneWallLayout wall;
    private View widgets;
    private View terminal;
    private View display;

    @Before
    public void freshVelocityTrackers() {
        FreshVelocityTrackers.drainPool();
    }

    private void build(@NonNull HomePageTransition effect) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        widgets = new FrameLayout(activity);
        terminal = new FrameLayout(activity);
        display = new FrameLayout(activity);
        wall.addView(widgets);
        wall.addView(terminal);
        wall.addView(display);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(false, true, true));
        wall.setPageView(PaneWallPage.WIDGETS, widgets);
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        wall.setPageView(PaneWallPage.DISPLAY, display);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.setPageTransition(effect);
    }

    private static void assertFlat(String why, View page) {
        assertEquals(why + " scale", 1f, page.getScaleX(), 0f);
        assertEquals(why + " alpha", 1f, page.getAlpha(), 0f);
        assertEquals(why + " turn", 0f, page.getRotationY(), 0f);
        assertEquals(why + " layer", View.LAYER_TYPE_NONE, page.getLayerType());
    }

    @Test
    public void underSlideHomeLeavesWithTheFingerFlat() {
        build(HomePageTransition.SLIDE);
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertFalse("at rest", wall.slidesAcrossSeam());
        wall.beginDrag();
        wall.dragTo(-300f);
        assertTrue("across the seam", wall.slidesAcrossSeam());
        assertEquals("home", -300f, widgets.getTranslationX(), SLOP_PX);
        assertEquals("workspace", WIDTH + wall.offsetPx(), terminal.getTranslationX(), 0f);
        assertFlat("home", widgets);
        assertFlat("workspace", terminal);
        assertTrue("nothing posed", wall.posedPages().isEmpty());
    }

    @Test
    public void everyOtherEffectTurnsThePagesAcrossTheSeam() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            if (effect == HomePageTransition.SLIDE) continue;
            build(effect);
            wall.goTo(PaneWallPage.WIDGETS, false);
            wall.beginDrag();
            wall.dragTo(-300f);
            String why = effect.name();
            assertTrue(why + " across the seam", wall.slidesAcrossSeam());
            assertFalse(why + " posed", wall.posedPages().isEmpty());
            wall.cancelDrag();
            assertFalse(why + " back at rest", wall.slidesAcrossSeam());
            assertFlat(why + " home back", widgets);
            assertEquals(why + " home back", 0f, widgets.getTranslationX(), 0f);
        }
    }

    @Test
    public void theWorkspaceTurnsTowardHomeUnderCarousel() {
        build(HomePageTransition.CAROUSEL);
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.beginDrag();
        wall.dragTo(300f);
        assertTrue(wall.slidesAcrossSeam());
        assertFalse(wall.posedPages().isEmpty());
    }

    @Test
    public void betweenTheWorkspacesOwnPagesTheEffectStillTurnsThem() {
        build(HomePageTransition.DEPTH);
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertFalse(wall.slidesAcrossSeam());
        // Depth: the page leaving forward stays in place, smaller, on a layer.
        assertEquals(0f, terminal.getTranslationX(), 1e-3f);
        assertTrue(terminal.getScaleX() < 1f);
        assertEquals(View.LAYER_TYPE_HARDWARE, terminal.getLayerType());
    }
}
