package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The frost's anchor. A pane is transformed constantly — the plank tilts it under a finger and the
 * FLIP movement animates its translation — and anchoring the wallpaper frost to a transformed
 * position baked those offsets in: the frost jumped when the pane was touched and stayed shifted
 * after the spring settled, while a strip of the pane showed sharp wallpaper.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneGlassBackdropViewTest {

    @Test
    public void theFrostAnchorIgnoresTransformsOnTheWayUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout paneFrame = new FrameLayout(activity);
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        paneFrame.addView(backdrop);
        root.addView(paneFrame);
        activity.setContentView(root);
        root.measure(0, 0);
        root.layout(0, 0, 1080, 2000);
        paneFrame.layout(40, 100, 1040, 1900);
        backdrop.layout(0, 0, 1000, 1800);

        int[] settled = new int[2];
        backdrop.layoutOriginOnScreen(settled);

        // Exactly what a press does: the plank tips and slides the pane frame.
        paneFrame.setTranslationX(24f);
        paneFrame.setTranslationY(-8f);
        paneFrame.setRotationY(1.1f);
        paneFrame.setScaleX(0.99f);
        int[] pressed = new int[2];
        backdrop.layoutOriginOnScreen(pressed);

        assertEquals("frost anchor moved with the tilt", settled[0], pressed[0]);
        assertEquals("frost anchor moved with the tilt", settled[1], pressed[1]);
    }

    /**
     * Every chrome apply re-dresses every pane, twice a frame. A re-dress with the glass the pane
     * is already wearing has to cost nothing: no shader, no invalidate, and the matrix it has.
     */
    @Test
    public void aReDressWithTheSameGlassChangesNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        Bitmap frame = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        Rect frameRect = new Rect(0, 0, 40, 60);
        ColorFilter frost = new ColorMatrixColorFilter(new ColorMatrix());

        assertTrue("the first dress is a dress",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, null, 0, 12f, frost));
        assertFalse("the same glass again is not",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, null, 0, 12f, frost));
        assertFalse("and a fresh rect holding the same bounds is still the same glass",
            backdrop.setGlass(frame, new Rect(0, 0, 40, 60), 0x40FF0000, null, 0, 12f, frost));
    }

    /** Everything a real change can move: each one has to reach the pane. */
    @Test
    public void everyRealChangeReDresses() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        Bitmap frame = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        Rect frameRect = new Rect(0, 0, 40, 60);
        Drawable grain = new ColorDrawable(0x11FFFFFF);
        backdrop.setGlass(frame, frameRect, 0x40FF0000, grain, 30, 12f, null);

        // A freshly blurred wallpaper frame is a new bitmap, which is why identity is enough.
        assertTrue("a new blur frame",
            backdrop.setGlass(Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888), frameRect,
                0x40FF0000, grain, 30, 12f, null));
        assertTrue("a wallpaper alignment change moves the frame's rect",
            backdrop.setGlass(frame, new Rect(0, 8, 40, 68), 0x40FF0000, grain, 30, 12f, null));
        assertTrue("a new tint",
            backdrop.setGlass(frame, frameRect, 0x4000FF00, grain, 30, 12f, null));
        // The grain layer is built fresh per call, so only its strength can tell the two apart.
        assertTrue("a moved grain slider",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, new ColorDrawable(0x22FFFFFF), 45,
                12f, null));
        assertTrue("grain switched off",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, null, 0, 12f, null));
        assertTrue("a new corner radius",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, grain, 30, 20f, null));
        assertTrue("a new frost filter",
            backdrop.setGlass(frame, frameRect, 0x40FF0000, grain, 30, 12f,
                new ColorMatrixColorFilter(new ColorMatrix())));
    }

    /** The anchor still has to follow a real layout move, or the frost stops tracking the pane. */
    @Test
    public void theFrostAnchorFollowsALayoutMove() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout paneFrame = new FrameLayout(activity);
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        paneFrame.addView(backdrop);
        root.addView(paneFrame);
        activity.setContentView(root);
        root.layout(0, 0, 1080, 2000);
        paneFrame.layout(0, 0, 540, 1800);
        backdrop.layout(0, 0, 540, 1800);
        int[] before = new int[2];
        backdrop.layoutOriginOnScreen(before);

        paneFrame.layout(540, 0, 1080, 1800);
        int[] after = new int[2];
        backdrop.layoutOriginOnScreen(after);

        assertEquals(540, after[0] - before[0]);
        assertEquals(0, after[1] - before[1]);
    }
}
