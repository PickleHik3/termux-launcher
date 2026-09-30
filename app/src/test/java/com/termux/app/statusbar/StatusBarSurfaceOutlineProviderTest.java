package com.termux.app.statusbar;

import android.app.Application;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Docked inner edge.
 *
 * <p>A Docked surface is flush with the screen on three sides, so only the edge facing the terminal
 * carries corners. Android outlines have one radius for all four, so the two that must stay square
 * are pushed outside the view — these cases pin that the overshoot goes on the correct side, since
 * getting it backwards rounds the screen edge and squares the visible one. (The cases the old
 * {@code InnerEdgeOutlineProvider} carried, on the one provider left.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarSurfaceOutlineProviderTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 200;

    private View view() {
        View view = new View(RuntimeEnvironment.getApplication());
        view.layout(0, 0, WIDTH, HEIGHT);
        return view;
    }

    private Rect outlineRect(StatusBarSurfaceOutlineProvider provider) {
        Outline outline = new Outline();
        provider.getOutline(view(), outline);
        Rect rect = new Rect();
        outline.getRect(rect);
        return rect;
    }

    private StatusBarSurfaceOutlineProvider innerEdge(Edge standsOn) {
        StatusBarSurfaceOutlineProvider provider = new StatusBarSurfaceOutlineProvider();
        provider.setEdge(standsOn);
        provider.setInnerEdgeOnly(true);
        return provider;
    }

    @Test
    public void aSurfaceOnTheBottom_overshootsBelowSoOnlyTheTopCornersLand() {
        StatusBarSurfaceOutlineProvider provider = innerEdge(Edge.BOTTOM);
        assertTrue(provider.setFrame(24f));

        Rect rect = outlineRect(provider);
        assertEquals("top stays on the surface", 0, rect.top);
        assertEquals("bottom runs past it by the radius", HEIGHT + 24, rect.bottom);
    }

    @Test
    public void aSurfaceOnTheTop_overshootsAboveSoOnlyTheBottomCornersLand() {
        StatusBarSurfaceOutlineProvider provider = innerEdge(Edge.TOP);
        provider.setFrame(18f);

        Rect rect = outlineRect(provider);
        assertEquals("top runs past it by the radius", -18, rect.top);
        assertEquals("bottom stays on the surface", HEIGHT, rect.bottom);
    }

    @Test
    public void allFourCornersWhenNotInnerEdgeOnly() {
        StatusBarSurfaceOutlineProvider provider = new StatusBarSurfaceOutlineProvider();
        provider.setEdge(Edge.TOP);
        provider.setFrame(18f);

        Rect rect = outlineRect(provider);
        assertEquals(0, rect.top);
        assertEquals(HEIGHT, rect.bottom);
    }

    @Test
    public void zeroRadius_isAPlainRectWithNoOvershoot() {
        StatusBarSurfaceOutlineProvider provider = innerEdge(Edge.BOTTOM);
        assertFalse(provider.clipsCorners());

        Rect rect = outlineRect(provider);
        assertEquals(0, rect.top);
        assertEquals(HEIGHT, rect.bottom);
        assertEquals(WIDTH, rect.right);
    }

    @Test
    public void setFrame_reportsOnlyRealChangesSoTheOutlineIsNotInvalidatedForNothing() {
        StatusBarSurfaceOutlineProvider provider = innerEdge(Edge.BOTTOM);
        assertTrue(provider.setFrame(12f));
        assertFalse(provider.setFrame(12f));
        assertTrue(provider.setFrame(13f));
    }

    @Test
    public void negativeAndNonFiniteRadii_collapseToSquare() {
        StatusBarSurfaceOutlineProvider provider = innerEdge(Edge.BOTTOM);
        provider.setFrame(-9f);
        assertEquals(0f, provider.radiusPx(), 0f);
        provider.setFrame(Float.NaN);
        assertEquals(0f, provider.radiusPx(), 0f);
    }
}
