package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import com.termux.app.DockGlassRendering;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The one glass builder every surface goes through: what its layers are, and how the slice, the
 * foot, the rim and the corner radius land on them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassSurfaceFactoryTest {

    private FakeChromeSurfaces surfaces;
    private GlassSurfaceFactory glass;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        glass = new GlassSurfaceFactory(surfaces);
    }

    @Test
    public void aPlainSurfaceIsBaseAndLightOnly() {
        LayerDrawable surface = (LayerDrawable) glass.surface(0.5f, 0f, 1f, true, 0);

        assertEquals(2, surface.getNumberOfLayers());
        GradientDrawable base = (GradientDrawable) surface.getDrawable(0);
        int baseColor = base.getColor().getDefaultColor();
        assertEquals(ChromePolicy.dockGlassBaseAlpha(0.5f), Color.alpha(baseColor));
        assertEquals("the base keeps the glass colour, only its alpha follows opacity",
            surfaces.glassBaseColor() & 0x00FFFFFF, baseColor & 0x00FFFFFF);
        assertEquals(0f, base.getCornerRadius(), 0f);
    }

    @Test
    public void grainAndRimAreExtraLayersInThatOrder() {
        LayerDrawable surface = (LayerDrawable) glass.surface(0.5f, 0f, 1f, true, 30, 24f, true);

        assertEquals(4, surface.getNumberOfLayers());
        GradientDrawable rim = (GradientDrawable) surface.getDrawable(3);
        assertEquals(24f, rim.getCornerRadius(), 0f);
        assertEquals(Color.TRANSPARENT, rim.getColor().getDefaultColor());
    }

    @Test
    public void theRadiusRoundsTheBaseAndTheLightTogether() {
        LayerDrawable surface = (LayerDrawable) glass.surface(0.5f, 0f, 1f, true, 0, 18f, false);

        assertEquals(18f, ((GradientDrawable) surface.getDrawable(0)).getCornerRadius(), 0f);
        assertEquals(18f, ((GradientDrawable) surface.getDrawable(1)).getCornerRadius(), 0f);
    }

    /** The keyboard and the strip render adjacent slices of one model, so the foot lands once. */
    @Test
    public void theLightLayerIsTheRequestedSliceOfTheModel() {
        float alpha = 0.5f;
        int top = Math.round(16f * alpha);
        int mid = Math.round(8f * alpha);
        int foot = Math.round(20f * alpha);

        GradientDrawable upper = lightLayer(glass.surface(alpha, 0f, 0.6f, true, 0));
        GradientDrawable lower = lightLayer(glass.surface(alpha, 0.6f, 1f, true, 0));

        assertArrayEquals(DockGlassRendering.lightModelSlice(surfaces.accentColor(), top, mid, foot,
            0f, 0.6f), upper.getColors());
        assertArrayEquals(DockGlassRendering.lightModelSlice(surfaces.accentColor(), top, mid, foot,
            0.6f, 1f), lower.getColors());
    }

    @Test
    public void withoutAFootTheModelEndsClear() {
        int[] colors = lightLayer(glass.surface(1f, 0f, 1f, false, 0)).getColors();

        assertEquals(Color.TRANSPARENT, colors[colors.length - 1]);
        int[] footed = lightLayer(glass.surface(1f, 0f, 1f, true, 0)).getColors();
        assertEquals(20, Color.alpha(footed[footed.length - 1]));
    }

    @Test
    public void opacityIsClampedToTheUnitRange() {
        GradientDrawable over = (GradientDrawable) ((LayerDrawable) glass.surface(3f, 0f, 1f, true, 0))
            .getDrawable(0);
        GradientDrawable full = (GradientDrawable) ((LayerDrawable) glass.surface(1f, 0f, 1f, true, 0))
            .getDrawable(0);

        assertEquals(full.getColor().getDefaultColor(), over.getColor().getDefaultColor());
    }

    private static GradientDrawable lightLayer(android.graphics.drawable.Drawable surface) {
        return (GradientDrawable) ((LayerDrawable) surface).getDrawable(1);
    }
}
