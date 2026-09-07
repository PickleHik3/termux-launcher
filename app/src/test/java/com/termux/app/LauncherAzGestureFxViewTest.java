package com.termux.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.launcher.az.AzFloatingStripPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherAzGestureFxViewTest {

    @Test
    public void focusRingVisibility_tracksIconsOnlyAndDoesNotDependOnOverflow() {
        Context context = ApplicationProvider.getApplicationContext();
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.layout(0, 0, 1080, 240);
        view.setFocusedIconRingEnabled(true);
        RectF bounds = new RectF(100, 40, 180, 120);

        view.updateDrag(true, 140, bounds,
            LauncherAzGestureFxView.InteractionMode.LETTER_TRACK);
        assertEquals(View.GONE, view.getVisibility());

        view.updateDrag(true, 140, bounds,
            LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED);
        assertEquals(View.VISIBLE, view.getVisibility());

        view.updateDrag(true, 140, null,
            LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED);
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test
    public void aFloatingStripShowsTheLayerAndClearingItPutsItAwayAgain() {
        Context context = ApplicationProvider.getApplicationContext();
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
        view.layout(0, 0, 1080, 900);
        assertEquals(View.GONE, view.getVisibility());

        AzFloatingStripPolicy.Strip strip = AzFloatingStripPolicy.layout(0f, 1080f, 800f, 3,
            context.getResources().getDisplayMetrics().density);
        view.setFloatingStrip(strip, icons(3));
        assertEquals(View.VISIBLE, view.getVisibility());

        view.setFloatingStrip(null, null);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(View.GONE, view.getVisibility());

        // An empty page is no strip at all, so the layer stays away.
        view.setFloatingStrip(strip, Collections.emptyList());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test
    public void drawingAStripLeavesItsBorrowedArtworkFullyOpaque() {
        Context context = ApplicationProvider.getApplicationContext();
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
        view.layout(0, 0, 1080, 900);
        List<Drawable> artwork = icons(3);
        AzFloatingStripPolicy.Strip strip = AzFloatingStripPolicy.layout(0f, 1080f, 800f, 3,
            context.getResources().getDisplayMetrics().density);
        view.setFloatingStrip(strip, artwork);
        view.setFloatingStripFocusedSlot(1);
        view.draw(new Canvas());
        // The strip borrows the icon store's drawables rather than copying them, so it must hand
        // every one of them back the way it found it.
        for (Drawable icon : artwork) {
            assertEquals(255, icon.getAlpha());
        }
    }

    @Test
    public void aFocusedSlotOutsideThePageIsNoFocusAtAll() {
        Context context = ApplicationProvider.getApplicationContext();
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.layout(0, 0, 1080, 900);
        AzFloatingStripPolicy.Strip strip = AzFloatingStripPolicy.layout(0f, 1080f, 800f, 2,
            context.getResources().getDisplayMetrics().density);
        view.setFloatingStrip(strip, icons(2));
        view.setFloatingStripFocusedSlot(5);
        // Nothing to assert but that it neither throws nor draws off the end of the page.
        view.draw(new Canvas());
    }

    @Test
    public void floatingStripFocusUsesTheContourVisualWhenSuppliedAndTheRoundRectWhenNot() {
        Context context = ApplicationProvider.getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
        view.layout(0, 0, 1080, 900);

        AzFloatingStripPolicy.Strip strip = AzFloatingStripPolicy.layout(0f, 1080f, 800f, 1, density);
        int iconSize = Math.max(1, Math.round(strip.iconSizePx));
        // The exact bitmaps do not matter here — what matters is that these two specific instances
        // (not equal copies) are the ones LauncherAzGestureFxView is told to draw for the focused
        // slot, provably distinct from whatever the built-in rounded-rect fallback would draw.
        Bitmap crispMask = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap haloMask = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        FocusOutlineRenderer.Visual visual = new FocusOutlineRenderer.Visual(
            crispMask, haloMask, iconSize, iconSize, 8);
        float fallbackRadius = strip.iconSizePx * 0.28f;

        view.setFloatingStrip(strip, icons(1), Collections.singletonList(visual));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
        view.setFloatingStripFocusedSlot(0);
        RecordingCanvas withVisual = new RecordingCanvas(
            Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888));
        view.draw(withVisual);

        assertTrue("the focused slot's ring must draw exactly the visual it was handed",
            withVisual.bitmapsDrawn.contains(crispMask));
        assertFalse("a supplied visual means no fallback round rect for the ring",
            withVisual.drewRoundRectOfRadius(fallbackRadius));

        view.setFloatingStrip(null, null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
        view.setFloatingStrip(strip, icons(1), Collections.<FocusOutlineRenderer.Visual>singletonList(null));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
        view.setFloatingStripFocusedSlot(0);
        RecordingCanvas withoutVisual = new RecordingCanvas(
            Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888));
        view.draw(withoutVisual);

        assertFalse("with no visual there is nothing for the ring to draw as a bitmap",
            withoutVisual.bitmapsDrawn.contains(crispMask));
        assertTrue("with no visual to fall back to, the round rect wraps the icon instead",
            withoutVisual.drewRoundRectOfRadius(fallbackRadius));
    }

    private static List<Drawable> icons(int count) {
        Drawable[] icons = new Drawable[count];
        for (int i = 0; i < count; i++) {
            icons[i] = new ColorDrawable(Color.rgb(10 * (i + 1), 20, 30));
        }
        return Arrays.asList(icons);
    }

    /** Records which bitmaps and round-rect radii a draw pass asked the canvas to paint, so a test
     * can tell the contour path (draws a bitmap) apart from the fallback (draws a round rect)
     * without depending on Robolectric's pixel-level rasterisation. */
    private static final class RecordingCanvas extends Canvas {
        final List<Bitmap> bitmapsDrawn = new ArrayList<>();
        private final List<Float> roundRectRadiiDrawn = new ArrayList<>();

        RecordingCanvas(Bitmap target) {
            super(target);
        }

        @Override
        public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
            bitmapsDrawn.add(bitmap);
        }

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            roundRectRadiiDrawn.add(rx);
        }

        boolean drewRoundRectOfRadius(float radius) {
            for (float drawn : roundRectRadiiDrawn) {
                if (Math.abs(drawn - radius) < 0.01f) {
                    return true;
                }
            }
            return false;
        }
    }
}
