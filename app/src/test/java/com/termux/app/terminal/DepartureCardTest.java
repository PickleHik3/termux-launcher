package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;

/**
 * The window-switch card: its content, ground outline and position share one origin, and outside
 * the panes' rounded slabs it is transparent (no square plate).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@GraphicsMode(GraphicsMode.Mode.NATIVE) // Path clipping and bitmap pixels need the real canvas.
public class DepartureCardTest {

    private static final int HOST_INSET = 13;
    private static final int PANE_LEFT = 20;
    private static final int PANE_TOP = 30;
    private static final int PANE_RIGHT = 480;
    private static final int PANE_BOTTOM = 950;
    private static final float RADIUS = 60f;

    /** A surface, and inside it an inset pane host, as the wall's terminal page is. */
    private static final class Rig {
        final FrameLayout surface;
        final FrameLayout pane;

        Rig(Activity activity) {
            surface = new FrameLayout(activity);
            activity.setContentView(surface, new ViewGroup.LayoutParams(1000, 1000));
            FrameLayout paneHost = new FrameLayout(activity);
            FrameLayout.LayoutParams hostParams = new FrameLayout.LayoutParams(
                1000 - 2 * HOST_INSET, 1000 - 2 * HOST_INSET);
            hostParams.leftMargin = HOST_INSET;
            hostParams.topMargin = HOST_INSET;
            surface.addView(paneHost, hostParams);
            pane = new FrameLayout(activity);
            PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
            backdrop.setId(R.id.terminal_pane_glass);
            backdrop.setVisibility(View.VISIBLE);
            pane.addView(backdrop);
            // What the terminal view paints: an opaque, square fill over the whole frame.
            View fill = new View(activity);
            fill.setBackgroundColor(Color.RED);
            pane.addView(fill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                PANE_RIGHT - PANE_LEFT, PANE_BOTTOM - PANE_TOP);
            params.leftMargin = PANE_LEFT;
            params.topMargin = PANE_TOP;
            paneHost.addView(pane, params);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static Bitmap capture(Rig rig) {
        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        boolean drawn = DepartureCard.draw(new Canvas(bitmap), rig.surface,
            Collections.singletonList(rig.pane), RADIUS, (canvas, slabs) -> { });
        assertTrue(drawn);
        return bitmap;
    }

    @Test
    public void outlineAndContentShareOneOriginUnderAnInsetPaneHost() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Rig rig = new Rig(activity);

        Path outline = PaneGlass.slabOutline(Collections.singletonList(rig.pane), rig.surface,
            RADIUS, new Path());
        RectF bounds = new RectF();
        outline.computeBounds(bounds, true);
        // The pane's rect in the surface's coordinates: host inset plus the pane's own place.
        assertEquals(HOST_INSET + PANE_LEFT, bounds.left, 0.5f);
        assertEquals(HOST_INSET + PANE_TOP, bounds.top, 0.5f);
        assertEquals(HOST_INSET + PANE_RIGHT, bounds.right, 0.5f);
        assertEquals(HOST_INSET + PANE_BOTTOM, bounds.bottom, 0.5f);

        // The content sits on those same rects: opaque just inside the left edge at mid height,
        // clear just outside it.
        Bitmap card = capture(rig);
        int midY = (int) bounds.centerY();
        assertTrue(Color.alpha(card.getPixel((int) bounds.left + 2, midY)) > 0);
        assertEquals(0, Color.alpha(card.getPixel((int) bounds.left - 3, midY)));
        assertTrue(Color.alpha(card.getPixel((int) bounds.right - 3, midY)) > 0);
        assertEquals(0, Color.alpha(card.getPixel((int) bounds.right + 2, midY)));
    }

    @Test
    public void theCardIsTransparentOutsideARoundedCornerInsideTheHostRectangle() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Rig rig = new Rig(activity);

        Bitmap card = capture(rig);
        int left = HOST_INSET + PANE_LEFT;
        int top = HOST_INSET + PANE_TOP;
        // The square corner of the pane's rectangle is cut away by the slab radius.
        assertEquals(0, Color.alpha(card.getPixel(left + 2, top + 2)));
        // The host's own corner and the gap around the pane carry nothing.
        assertEquals(0, Color.alpha(card.getPixel(HOST_INSET + 2, HOST_INSET + 2)));
        assertEquals(0, Color.alpha(card.getPixel(900, 500)));
        // The slab itself is there.
        assertTrue(Color.alpha(card.getPixel(left + 200, top + 400)) > 0);
    }

    @Test
    public void aTranslucentCardViewHasNoBackgroundElevationOrOutline() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);

        ImageView card = DepartureCard.view(activity, bitmap, true, Color.BLACK, 30f);

        assertNull(card.getBackground());
        assertEquals(0f, card.getElevation(), 0f);
        assertNull(card.getOutlineProvider());
    }

    @Test
    public void anOpaqueGroundCardKeepsItsPlateAndShadow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);

        ImageView card = DepartureCard.view(activity, bitmap, false, Color.BLACK, 30f);

        assertTrue(card.getBackground() != null);
        assertEquals(30f, card.getElevation(), 0f);
    }
}
