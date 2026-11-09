package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Build;
import android.widget.FrameLayout;

import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.LiveBackdropFrameLayout;
import com.termux.app.chrome.LivePicture;
import com.termux.app.chrome.LivePictureTestAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * The hand-off between a slab's stored frame and Live blur's picture: the stored frame stays up
 * until the picture is recorded and blurred whole, and comes back the moment the picture goes, so
 * no frame shows the glass empty in between.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.TIRAMISU})
public class LiveGlassHandoffTest {

    private PaneGlassBackdropView mSlab;
    private LiveBackdrop mRecorder;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new LiveBackdropFrameLayout(activity);
        mSlab = new PaneGlassBackdropView(activity);
        root.addView(mSlab, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(root);
        mSlab.setRefraction(GlassRefraction.Look.DEFAULT);
        mRecorder = new LiveBackdrop(root);
    }

    @Test
    public void aPictureNotYetRecordedLeavesTheStoredFrameUp() {
        LivePicture picture = mRecorder.attach(mSlab, 20f);
        assertNotNull(picture);
        mSlab.setLivePicture(picture);

        assertTrue("the slab is on the live route", mSlab.drawsLive());
        assertFalse("not recorded yet", picture.ready());
        assertFalse("so the stored frame is drawn", mSlab.showsLive());
    }

    @Test
    public void theFirstRecordingSwapsThePictureInAndRedrawsTheSlab() {
        LivePicture picture = mRecorder.attach(mSlab, 20f);
        mSlab.setLivePicture(picture);
        shadowOf(mSlab).clearWasInvalidated();

        LivePictureTestAccess.recorded(picture);

        assertTrue(picture.ready());
        assertTrue(mSlab.showsLive());
        assertTrue("the slab redraws in the frame being drawn", shadowOf(mSlab).wasInvalidated());

        // A later recording of a picture already shown asks for no redraw.
        shadowOf(mSlab).clearWasInvalidated();
        LivePictureTestAccess.recorded(picture);
        assertFalse(shadowOf(mSlab).wasInvalidated());
    }

    @Test
    public void aSurfaceThatHidWaitsForItsNextRecording() {
        LivePicture picture = mRecorder.attach(mSlab, 20f);
        mSlab.setLivePicture(picture);
        LivePictureTestAccess.recorded(picture);

        LivePictureTestAccess.trim(picture);

        assertFalse("its nodes were emptied", picture.ready());
        assertFalse(mSlab.showsLive());
    }

    @Test
    public void lettingThePictureGoPutsTheStoredFrameStraightBack() {
        LivePicture picture = mRecorder.attach(mSlab, 20f);
        mSlab.setLivePicture(picture);
        LivePictureTestAccess.recorded(picture);
        assertTrue(mSlab.showsLive());
        shadowOf(mSlab).clearWasInvalidated();

        mSlab.setLivePicture(null);

        assertFalse(mSlab.showsLive());
        assertTrue("the slab redraws with its stored frame", shadowOf(mSlab).wasInvalidated());
    }

    @Test
    public void aReleasedPictureKeepsItsNodesUntilTheNextFrame() {
        LivePicture picture = mRecorder.attach(mSlab, 20f);
        LivePictureTestAccess.makeNodes(picture, 240, 140);
        mSlab.setLivePicture(picture);
        LivePictureTestAccess.recorded(picture);

        // The budget hands its place to another surface, or Live goes off.
        mRecorder.releaseAll();

        assertFalse("the stored frame is drawn from the next draw on", mSlab.showsLive());
        assertTrue("the frame already recorded may still draw the nodes",
            LivePictureTestAccess.retiring(picture));

        ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS);
        assertFalse("emptied once the next frame has come", LivePictureTestAccess.retiring(picture));
    }
}
