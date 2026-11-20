package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The page layer capture's picture crosses from the reader thread to the main thread exactly once:
 * taken by the main thread's poll, or released by whoever holds it last after a cancel. Nothing is
 * waited for on the main thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class PageLayerCaptureHandoffTest {

    private static Bitmap picture() {
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    }

    @Test
    public void nothingLandedYetIsAnEmptyPollNotAWait() {
        PageLayerCapture.Handoff handoff = new PageLayerCapture.Handoff();
        assertNull(handoff.take());
        assertFalse(handoff.failed());
    }

    @Test
    public void aLandedPictureIsTakenOnce() {
        PageLayerCapture.Handoff handoff = new PageLayerCapture.Handoff();
        Bitmap bitmap = picture();
        assertTrue(handoff.land(bitmap));
        assertSame(bitmap, handoff.take());
        assertNull(handoff.take());
        assertNull("taken, so the cancel has nothing to release", handoff.drop());
    }

    @Test
    public void aPictureNeverTakenIsHandedBackByTheDrop() {
        PageLayerCapture.Handoff handoff = new PageLayerCapture.Handoff();
        Bitmap bitmap = picture();
        assertTrue(handoff.land(bitmap));
        assertSame(bitmap, handoff.drop());
        assertNull(handoff.take());
    }

    @Test
    public void aPictureLandingAfterTheDropIsRefused() {
        PageLayerCapture.Handoff handoff = new PageLayerCapture.Handoff();
        assertNull(handoff.drop());
        assertTrue(handoff.dropped());
        assertFalse(handoff.land(picture()));
        assertNull(handoff.take());
    }

    @Test
    public void aFailedRenderIsSeenByThePoll() {
        PageLayerCapture.Handoff handoff = new PageLayerCapture.Handoff();
        handoff.fail();
        assertTrue(handoff.failed());
        assertNull(handoff.take());
    }
}
