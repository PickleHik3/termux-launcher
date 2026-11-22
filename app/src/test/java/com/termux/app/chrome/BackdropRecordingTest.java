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

    @Test
    public void containerHoldingASurfaceIsDescendedNotRecordedWhole() {
        Context context = RuntimeEnvironment.getApplication();
        android.widget.FrameLayout outer = new android.widget.FrameLayout(context);
        android.widget.FrameLayout inner = new android.widget.FrameLayout(context);
        android.widget.FrameLayout other = new android.widget.FrameLayout(context);
        SurfaceView surface = new SurfaceView(context);
        outer.addView(inner);
        outer.addView(other);
        inner.addView(surface);
        assertFalse(BackdropRecording.holdsSurface(inner));
        BackdropRecording.register(surface);
        try {
            assertTrue(BackdropRecording.holdsSurface(inner));
            assertTrue(BackdropRecording.holdsSurface(outer));
            assertFalse(BackdropRecording.holdsSurface(other));
        } finally {
            BackdropRecording.unregister(surface);
        }
        assertFalse(BackdropRecording.holdsSurface(inner));
    }

    /** #316: the switch's frost stands in for the ground on screen only; a recording skips it. */
    @Test
    public void aViewLeftOutIsSkippedAndItsContainerDescendedUntilKeptIn() {
        Context context = RuntimeEnvironment.getApplication();
        android.widget.FrameLayout ground = new android.widget.FrameLayout(context);
        View frost = new View(context);
        ground.addView(frost);
        assertTrue(BackdropRecording.mayRecord(frost));
        assertFalse(BackdropRecording.holdsSurface(ground));
        BackdropRecording.leaveOut(frost);
        try {
            assertFalse(BackdropRecording.mayRecord(frost));
            assertTrue(BackdropRecording.holdsSurface(ground));
            BackdropRecording.begin();
            try {
                assertTrue(BackdropRecording.skips(frost));
            } finally {
                BackdropRecording.end();
            }
        } finally {
            BackdropRecording.keepIn(frost);
        }
        assertTrue(BackdropRecording.mayRecord(frost));
        assertFalse(BackdropRecording.holdsSurface(ground));
    }
}
