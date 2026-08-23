package com.termux.app.surfaces;

import android.app.Application;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

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
 * getting it backwards rounds the screen edge and squares the visible one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class InnerEdgeOutlineProviderTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 200;

    private View view() {
        View view = new View(RuntimeEnvironment.getApplication());
        view.layout(0, 0, WIDTH, HEIGHT);
        return view;
    }

    private Rect outlineRect(InnerEdgeOutlineProvider provider) {
        Outline outline = new Outline();
        provider.getOutline(view(), outline);
        Rect rect = new Rect();
        outline.getRect(rect);
        return rect;
    }

    @Test
    public void topEdgeProvider_overshootsBelowSoOnlyTheTopCornersLand() {
        InnerEdgeOutlineProvider provider =
            new InnerEdgeOutlineProvider(InnerEdgeOutlineProvider.Edge.TOP);
        assertTrue(provider.setRadiusPx(24f));

        Rect rect = outlineRect(provider);
        assertEquals("top stays on the surface", 0, rect.top);
        assertEquals("bottom runs past it by the radius", HEIGHT + 24, rect.bottom);
    }

    @Test
    public void bottomEdgeProvider_overshootsAboveSoOnlyTheBottomCornersLand() {
        InnerEdgeOutlineProvider provider =
            new InnerEdgeOutlineProvider(InnerEdgeOutlineProvider.Edge.BOTTOM);
        provider.setRadiusPx(18f);

        Rect rect = outlineRect(provider);
        assertEquals("top runs past it by the radius", -18, rect.top);
        assertEquals("bottom stays on the surface", HEIGHT, rect.bottom);
    }

    @Test
    public void zeroRadius_isAPlainRectWithNoOvershoot() {
        InnerEdgeOutlineProvider provider =
            new InnerEdgeOutlineProvider(InnerEdgeOutlineProvider.Edge.TOP);
        assertFalse(provider.roundsCorners());

        Rect rect = outlineRect(provider);
        assertEquals(0, rect.top);
        assertEquals(HEIGHT, rect.bottom);
        assertEquals(WIDTH, rect.right);
    }

    @Test
    public void setRadius_reportsOnlyRealChangesSoTheOutlineIsNotInvalidatedForNothing() {
        InnerEdgeOutlineProvider provider =
            new InnerEdgeOutlineProvider(InnerEdgeOutlineProvider.Edge.TOP);
        assertTrue(provider.setRadiusPx(12f));
        assertFalse(provider.setRadiusPx(12f));
        assertTrue(provider.setRadiusPx(13f));
    }

    @Test
    public void negativeAndNonFiniteRadii_collapseToSquare() {
        InnerEdgeOutlineProvider provider =
            new InnerEdgeOutlineProvider(InnerEdgeOutlineProvider.Edge.TOP);
        provider.setRadiusPx(-9f);
        assertEquals(0f, provider.radiusPx(), 0f);
        provider.setRadiusPx(Float.NaN);
        assertEquals(0f, provider.radiusPx(), 0f);
    }
}
