package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
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
