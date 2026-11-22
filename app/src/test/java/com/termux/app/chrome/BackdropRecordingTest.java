package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.SurfaceView;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BackdropRecordingTest {

    @Test
    public void surfaceViewsAreNeverRecorded() {
        Context context = RuntimeEnvironment.getApplication();
        assertFalse(BackdropRecording.mayRecord(new SurfaceView(context)));
        assertFalse(BackdropRecording.mayRecord(new SurfaceView(context) {}));
        assertTrue(BackdropRecording.mayRecord(new View(context)));
    }

    @Test
    public void onlyInsideARecordingAreSurfaceViewsSkipped() {
        Context context = RuntimeEnvironment.getApplication();
        SurfaceView surface = new SurfaceView(context);
        assertFalse(BackdropRecording.skips(surface));
        BackdropRecording.begin();
        try {
            assertTrue(BackdropRecording.skips(surface));
            assertFalse(BackdropRecording.skips(new View(context)));
        } finally {
            BackdropRecording.end();
        }
        assertFalse(BackdropRecording.skips(surface));
    }
}
