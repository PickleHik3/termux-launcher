package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassRefraction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * A slab cut into tiles (the Home Clock's Flip) is dressed as every slab is: it keeps the
 * Look's refraction, which the live route needs to draw Live blur's picture at all (a slab with
 * no look falls back to the stored frame), and each tile takes the rim at its own corner.
 */
@RunWith(RobolectricTestRunner.class)
public class PaneGlassTilesTest {

    private static final float[] RECTS = {0f, 0f, 190f, 120f, 210f, 0f, 400f, 120f};
    private static final float[] RADII = {24f, 24f};

    @Test
    public void aTiledSlabKeepsTheLookTheLiveRouteNeeds() {
        FrameLayout frame = new FrameLayout(RuntimeEnvironment.getApplication());
        frame.layout(0, 0, 400, 300);
        PaneGlassBackdropView tiled = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        PaneGlassBackdropView card = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        Style style = new Style();

        PaneGlass.apply(style, frame, tiled, 0f);
        tiled.setTiles(RECTS, RADII, style);
        PaneGlass.apply(style, frame, card, 12f);

        assertSame("the tiles bend Live blur's picture with the Look", style.look,
            tiled.refraction());
        assertSame("a whole card keeps its own", style.look, card.refraction());
        assertArrayEquals(RECTS, tiled.tiles(), 0f);
        assertNull(card.tiles());
        assertEquals("one rim per tile, at its corner", 2, style.rims);
    }

    @Test
    public void tilesGoBackToTheWholeSlab() {
        PaneGlassBackdropView slab = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        Style style = new Style();
        slab.setTiles(RECTS, RADII, style);
        slab.setTiles(null, null, style);
        assertNull(slab.tiles());

        // Four numbers to a tile, or no tiles.
        slab.setTiles(new float[] {0f, 0f, 10f}, new float[] {4f}, style);
        assertNull(slab.tiles());
    }

    private static final class Style implements PaneSurfaceStyle {
        final GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        int rims;

        @Override public boolean isPaneGlassActive() { return true; }
        @Nullable @Override public Bitmap paneGlassBlurFrame() { return null; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(); }
        @Nullable @Override public ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return 0; }
        @Nullable @Override public Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneGapDp() { return 0; }
        @Nullable @Override public GlassRefraction.Look paneGlassRefraction() { return look; }
        @Override public int paneGlassVeil(@NonNull Rect rootRect) { return 0; }
        @Nullable @Override public Drawable paneGlassRim(float radiusPx) {
            rims++;
            return new ColorDrawable(0);
        }
    }
}
