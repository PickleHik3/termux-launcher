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

/**
 * Across the seam between Home and the Workspace the pages turn as Home's pages do, side by side
 * with the finger (decided 2026-11-02): Home's page leaves by the finger's distance while the
 * Workspace's comes in beside it, flat and off any layer, whatever the page transition. Between
 * the Workspace's own pages the chosen effect still turns them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
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
    public void homeLeavesWithTheFingerTowardTheWorkspaceUnderEveryEffect() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            build(effect);
            wall.goTo(PaneWallPage.WIDGETS, false);
            assertFalse(effect + " at rest", wall.slidesAcrossSeam());
            wall.beginDrag();
            wall.dragTo(-300f);
            String why = effect.name();
            assertTrue(why + " across the seam", wall.slidesAcrossSeam());
            // Home's page follows the finger, as a Home page does under a page drag.
            assertEquals(why + " home", -300f, widgets.getTranslationX(), SLOP_PX);
            assertEquals(why + " home is the wall", wall.offsetPx(), widgets.getTranslationX(), 0f);
            // The Workspace's page comes in right beside it.
            assertEquals(why + " workspace", WIDTH + wall.offsetPx(), terminal.getTranslationX(), 0f);
            assertFlat(why + " home", widgets);
            assertFlat(why + " workspace", terminal);
            assertTrue(why + " nothing posed", wall.posedPages().isEmpty());
            wall.cancelDrag();
            assertFalse(why + " back at rest", wall.slidesAcrossSeam());
            assertEquals(why + " home back", 0f, widgets.getTranslationX(), 0f);
        }
    }

    @Test
    public void theWorkspaceLeavesWithTheFingerTowardHome() {
        build(HomePageTransition.DEPTH);
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.beginDrag();
        wall.dragTo(300f);
        assertTrue(wall.slidesAcrossSeam());
        assertEquals(300f, terminal.getTranslationX(), SLOP_PX);
        assertEquals(wall.offsetPx() - WIDTH, widgets.getTranslationX(), 0f);
        assertFlat("workspace", terminal);
        assertFlat("home", widgets);
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
