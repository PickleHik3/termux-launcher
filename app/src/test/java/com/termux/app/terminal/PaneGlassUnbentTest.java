package com.termux.app.terminal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
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
 * A slab's edge is its own: dressing one slab without the edge program (a card cut to tiles)
 * leaves every other slab, dressed through {@link PaneGlass#apply}, bending and rimming its
 * edge round its own radius.
 */
@RunWith(RobolectricTestRunner.class)
public class PaneGlassUnbentTest {

    @Test
    public void aSlabDressedUnbentKeepsNoProgramAndItsNeighboursKeepTheirOwn() {
        FrameLayout frame = new FrameLayout(RuntimeEnvironment.getApplication());
        frame.layout(0, 0, 400, 300);
        PaneGlassBackdropView tiled = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        PaneGlassBackdropView pane = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        PaneGlassBackdropView card = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        Style style = new Style();

        PaneGlass.apply(style, frame, pane, 26f, true);
        PaneGlass.applyUnbent(style, frame, tiled, 0f);
        PaneGlass.apply(style, frame, card, 12f);

        assertNull("the tiles' slab draws no edge program", tiled.refraction());
        assertSame("a pane keeps its own", style.look, pane.refraction());
        assertSame("a card keeps its own", style.look, card.refraction());

        // The tiles go away and the slab is dressed the usual way: the edge comes back.
        PaneGlass.apply(style, frame, tiled, 12f);
        assertNotNull(tiled.refraction());
    }

    private static final class Style implements PaneSurfaceStyle {
        final GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;

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
    }
}
