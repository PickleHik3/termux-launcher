package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * A recording of what is beneath a surface starts with the window's ground: the window background
 * and the backgrounds above the recorder's root, so a capture over nothing else is as opaque as
 * the screen there, not a hole.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveBackdropGroundTest {

    private Activity mActivity;
    private ImageView mSurface;
    private LiveBackdrop mRecorder;

    @Before
    public void setUp() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).create();
        mActivity = controller.get();
        LiveBackdropFrameLayout root = new LiveBackdropFrameLayout(mActivity);
        // Nothing beneath the surface inside the root: only the ground can show.
        mSurface = new ImageView(mActivity);
        root.addView(mSurface, new FrameLayout.LayoutParams(40, 40));
        mActivity.setContentView(root);
        mRecorder = new LiveBackdrop(root);
        controller.start().resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private int capturedCentre(boolean windowBeneath) {
        // One frame of the window first, as on a phone: a view sizes its background as it draws.
        android.view.View decor = mActivity.getWindow().getDecorView();
        decor.draw(new Canvas(Bitmap.createBitmap(Math.max(1, decor.getWidth()),
            Math.max(1, decor.getHeight()), Bitmap.Config.ARGB_8888)));
        Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        assertTrue(windowBeneath ? mRecorder.drawWindowBeneath(mSurface, canvas, 0)
            : mRecorder.drawBelow(mSurface, canvas, 0));
        return bitmap.getPixel(20, 20);
    }

    @Test
    public void theWindowBackgroundIsTheGroundOfACapture() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLUE));
        assertEquals(Color.BLUE, capturedCentre(false));
    }

    @Test
    public void aMenuCaptureHasTheSameGround() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLUE));
        assertEquals(Color.BLUE, capturedCentre(true));
    }

    @Test
    public void aBackgroundBetweenTheDecorAndTheRootIsOverTheWindowBackground() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLUE));
        ((android.view.View) mSurface.getParent().getParent()).setBackgroundColor(Color.RED);
        assertEquals(Color.RED, capturedCentre(false));
    }

    @Test
    public void aTransparentWindowAddsNothing() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        assertEquals(0, Color.alpha(capturedCentre(false)));
    }
}
