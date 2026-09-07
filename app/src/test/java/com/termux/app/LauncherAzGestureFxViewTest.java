package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.launcher.az.AzFloatingStripPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

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

    private static List<Drawable> icons(int count) {
        Drawable[] icons = new Drawable[count];
        for (int i = 0; i < count; i++) {
            icons[i] = new ColorDrawable(Color.rgb(10 * (i + 1), 20, 30));
        }
        return Arrays.asList(icons);
    }
}
