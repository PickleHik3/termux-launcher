package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The pane style is a live view onto the activity's state, so two reads of it can only be told
 * apart by what they answer. The key is that answer, and the chrome passes skip re-dressing the
 * panes while it holds.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneStyleKeyTest {

    private static final class Style implements PaneSurfaceStyle {
        boolean glass = true;
        Bitmap frame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        final Rect frameRect = new Rect(0, 0, 1080, 2412);
        int tint = 0x80102030;
        int grain = 12;
        float radiusPx = 26f;
        int radiusDp = -1;
        int gap = 6;
        Bitmap wallBehind;
        int wallBehindColor = 0x40000000;
        int grainLayersBuilt;

        @Override public boolean isPaneGlassActive() { return glass; }
        @Nullable @Override public Bitmap paneGlassBlurFrame() { return frame; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return frameRect; }
        @Nullable @Override public ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return tint; }
        @Nullable @Override public Drawable paneGlassGrainLayer() {
            grainLayersBuilt++;
            return null;
        }
        @Override public int paneGlassGrainStrength() { return grain; }
        @Override public float paneGlassCornerRadiusPx() { return radiusPx; }
        @Override public int paneCornerRadiusDp() { return radiusDp; }
        @Override public int paneGapDp() { return gap; }
        @Nullable @Override public Bitmap wallBehindFrame() { return wallBehind; }
        @Override public int wallBehindColor() { return wallBehindColor; }
    }

    @Test
    public void twoReadsThatAnswerTheSameAreTheSameKey() {
        Style style = new Style();
        PaneStyleKey first = PaneStyleKey.of(style);
        assertEquals(first, PaneStyleKey.of(style));
        assertEquals(first.hashCode(), PaneStyleKey.of(style).hashCode());
    }

    @Test
    public void theKeyIsAValueNotALiveView() {
        Style style = new Style();
        PaneStyleKey before = PaneStyleKey.of(style);
        // The frame rect is handed out by reference and moved in place by the blur cache.
        style.frameRect.offset(0, 40);
        assertNotEquals(before, PaneStyleKey.of(style));
    }

    @Test
    public void everyAnswerAPaneIsDressedWithMovesTheKey() {
        Style style = new Style();
        PaneStyleKey base = PaneStyleKey.of(style);

        style.frame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        assertNotEquals("a new blur frame", base, PaneStyleKey.of(style));
        base = PaneStyleKey.of(style);

        style.glass = false;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.glass = true;

        style.tint = 0x00000000;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.tint = 0x80102030;

        style.grain = 0;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.grain = 12;

        style.radiusPx = 0f;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.radiusPx = 26f;

        style.radiusDp = 8;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.radiusDp = -1;

        style.gap = 0;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.gap = 6;

        style.wallBehind = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        assertNotEquals(base, PaneStyleKey.of(style));
        style.wallBehind = null;

        style.wallBehindColor = 0;
        assertNotEquals(base, PaneStyleKey.of(style));
        style.wallBehindColor = 0x40000000;

        assertEquals("and back where it started", base, PaneStyleKey.of(style));
    }

    @Test
    public void takingTheKeyBuildsNoGrainLayer() {
        // The grain layer is a fresh drawable per call; its strength stands in for it.
        Style style = new Style();
        PaneStyleKey.of(style);
        assertEquals(0, style.grainLayersBuilt);
    }
}
