package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Below Android 12 a panel's Snapshot is drawn in software: one when it opens, none while it
 * moves, one when it comes to rest; a failing blur leaves the panel on the stored frame, never a
 * crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PanelBackdropSoftwareSnapshotTest {

    private ImageView mSurface;
    private PanelBackdrop mBackdrop;
    private int mBlurs;
    private boolean mBlurThrows;

    @Before
    public void setUp() {
        PanelBackdrop.setSoftwareBlurFactoryForTest(() -> new PanelBackdrop.SoftwareBlur() {
            @Override
            public boolean blur(@NonNull Context context, @NonNull Bitmap bitmap, float radius) {
                if (mBlurThrows) throw new RuntimeException("no RenderScript");
                mBlurs++;
                return true;
            }

            @Override
            public void release() {}
        });
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).create();
        Activity activity = controller.get();
        LiveBackdropFrameLayout root = new LiveBackdropFrameLayout(activity);
        View beneath = new View(activity);
        beneath.setBackgroundColor(Color.RED);
        root.addView(beneath, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mSurface = new ImageView(activity);
        root.addView(mSurface, new FrameLayout.LayoutParams(200, 300));
        activity.setContentView(root);
        LiveBackdrop recorder = new LiveBackdrop(root);
        PanelBackdrop.install(activity.getWindow().getDecorView(), new PanelBackdrop.Source() {
            @Nullable @Override public LiveBackdrop recorder() {
                return recorder;
            }

            @Override public boolean liveBlur() {
                return true;
            }
        });
        controller.start().resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        mBackdrop = new PanelBackdrop(mSurface, mSurface);
    }

    @After
    public void tearDown() {
        mBackdrop.close();
        PanelBackdrop.setSoftwareBlurFactoryForTest(null);
    }

    @Test
    public void openingTakesOneSnapshot() {
        assertTrue(mSurface.isAttachedToWindow());
        assertTrue(mSurface.getWidth() > 0);
        assertTrue(mBackdrop.open(20f));
        // Live blur is on, but there is no Live below Android 12.
        assertEquals(PanelBackdropPolicy.Kind.SNAPSHOT, mBackdrop.kind());
        assertEquals(1, mBackdrop.snapshotsTaken());
        assertEquals(1, mBlurs);
        assertNotNull(mSurface.getDrawable());
    }

    @Test
    public void movingTakesNoneAndComingToRestTakesOne() {
        assertTrue(mBackdrop.open(20f));
        mBackdrop.setMoving(true);
        mBackdrop.setMoving(true);
        assertEquals(1, mBackdrop.snapshotsTaken());
        mBackdrop.setMoving(false);
        assertEquals(2, mBackdrop.snapshotsTaken());
        mBackdrop.setMoving(false);
        assertEquals(2, mBackdrop.snapshotsTaken());
    }

    @Test
    public void aFailingBlurLeavesTheStoredFrameAndNoPicture() {
        mBlurThrows = true;
        assertFalse(mBackdrop.open(20f));
        assertEquals(PanelBackdropPolicy.Kind.STORED, mBackdrop.kind());
        assertEquals(0, mBackdrop.snapshotsTaken());
        assertNull(mSurface.getDrawable());
        // From here on panels go straight to the stored frame.
        assertFalse(PanelBackdrop.pictured());
        mBlurThrows = false;
        assertFalse(mBackdrop.open(20f));
        assertEquals(0, mBlurs);
    }

    @Test
    public void closingLetsThePictureGo() {
        assertTrue(mBackdrop.open(20f));
        mBackdrop.close();
        assertNull(mSurface.getDrawable());
        assertNull(mBackdrop.kind());
    }
}
