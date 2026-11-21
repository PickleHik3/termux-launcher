package com.termux.app.statusbar;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/** The place strip's dissolve, and the end of it the window chips feather themselves. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceContentStripFadeTest {

    @Test public void atRestTheEndFacingTheReadingsIsTheChipsToFeather() {
        PlaceContentStrip strip = strip();
        assertEquals(1f, strip.getLeftFadingEdgeStrength(), 0f);
        assertEquals(0f, strip.getRightFadingEdgeStrength(), 0f);
    }

    @Test public void slidingThePlaceLeavesTheRightEndToTheSlotMask() {
        PlaceContentStrip strip = strip();
        int length = strip.getHorizontalFadingEdgeLength();
        strip.setTranslationX(length / 2f);
        assertEquals(0f, strip.getRightFadingEdgeStrength(), 0f);
        assertEquals(1f, strip.getLeftFadingEdgeStrength(), 0f);
    }

    @Test public void rightToLeftTheReadingsAreOnTheLeft() {
        PlaceContentStrip strip = strip();
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        assertEquals(0f, strip.getLeftFadingEdgeStrength(), 0f);
        assertEquals(1f, strip.getRightFadingEdgeStrength(), 0f);
    }

    @Test public void aTranslatedStripDrawsIntoALayerAndMasksTheEdge() {
        PlaceContentStrip strip = strip();
        strip.layout(0, 0, 200, 40);
        strip.setTranslationX(30f);
        CountingCanvas canvas = new CountingCanvas();
        strip.dispatchDraw(canvas);
        assertEquals(1, canvas.layers);
        assertEquals(1, canvas.masks);
    }

    @Test public void aStripSlidLeftMasksTheSlotsLeftEdgeOnce() {
        // Home's float chips leave through the slot's left edge.
        PlaceContentStrip strip = strip();
        strip.layout(0, 0, 200, 40);
        strip.setTranslationX(-30f);
        CountingCanvas canvas = new CountingCanvas();
        strip.dispatchDraw(canvas);
        assertEquals(1, canvas.layers);
        assertEquals(1, canvas.masks);
    }

    @Test public void aStripAtRestDrawsWithoutALayer() {
        PlaceContentStrip strip = strip();
        strip.layout(0, 0, 200, 40);
        CountingCanvas canvas = new CountingCanvas();
        strip.dispatchDraw(canvas);
        assertEquals(0, canvas.layers);
        assertEquals(0, canvas.masks);
    }

    private static final class CountingCanvas extends Canvas {
        int layers;
        int masks;

        CountingCanvas() { super(Bitmap.createBitmap(200, 40, Bitmap.Config.ARGB_8888)); }

        @Override public int saveLayer(float l, float t, float r, float b, Paint paint) {
            layers++;
            return super.saveLayer(l, t, r, b, paint);
        }

        @Override public void drawRect(float l, float t, float r, float b, Paint paint) {
            if (paint.getXfermode() != null) masks++;
            super.drawRect(l, t, r, b, paint);
        }
    }

    private static PlaceContentStrip strip() {
        return new PlaceContentStrip(ApplicationProvider.getApplicationContext(), null);
    }
}
