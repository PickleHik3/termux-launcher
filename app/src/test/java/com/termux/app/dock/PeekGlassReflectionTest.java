package com.termux.app.dock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The live reflection's recording is the row flipped, plain: a view draws it only under the live
 * fade. On pong, after a Workspace -> Home2 landing, the views had lost the fade (their band not
 * placed) while the row was recorded again, and the dock's icons showed sharp and at full
 * strength under the A-Z letters for about 400 ms.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PeekGlassReflectionTest {

    @Test
    public void aViewUnderTheLiveFadeDrawsTheRecording() {
        assertTrue(PeekGlassReflection.drawsLiveRecording(PeekReflectionView.EFFECT_LIVE));
    }

    @Test
    public void aViewWithoutTheFadeNeverDrawsTheRecordingSharp() {
        assertFalse(PeekGlassReflection.drawsLiveRecording(PeekReflectionView.EFFECT_NONE));
        assertFalse(PeekGlassReflection.drawsLiveRecording(PeekReflectionView.EFFECT_UNSET));
        assertFalse(PeekGlassReflection.drawsLiveRecording(PeekReflectionView.EFFECT_STILL));
    }
}
