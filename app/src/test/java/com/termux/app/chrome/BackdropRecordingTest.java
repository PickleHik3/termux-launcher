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

    /**
     * A wall page holding a Workspace window with the Display in it (pong 2026-11-23: the drawer
     * flashed while herdr ran): drawing the page whole drew the window by its display list, the
     * SurfaceView's node with it, which no {@code skips} check sees. {@code drawWhole} descends
     * every container that holds the surface and draws everything else.
     */
    @Test
    public void drawingAPageWholeNeverDrawsASurfaceNestedUnderAPlainContainer() {
        Context context = RuntimeEnvironment.getApplication();
        android.widget.FrameLayout page = new android.widget.FrameLayout(context);
        android.widget.FrameLayout window = new android.widget.FrameLayout(context);
        CountingSurface surface = new CountingSurface(context);
        CountingView label = new CountingView(context);
        CountingView beside = new CountingView(context);
        page.addView(window);
        page.addView(beside);
        window.addView(surface);
        window.addView(label);
        page.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 100, 100);
        android.graphics.Canvas canvas = new android.graphics.Canvas(
            android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888));

        // Unregistered, the page draws as it always did: the surface with it.
        BackdropRecording.drawWhole(canvas, page);
        assertTrue("before: " + surface.draws + "," + label.draws + "," + beside.draws,
            surface.draws == 1 && label.draws == 1 && beside.draws == 1);

        BackdropRecording.register(surface);
        try {
            BackdropRecording.drawWhole(canvas, page);
        } finally {
            BackdropRecording.unregister(surface);
        }
        assertTrue("the surface is left out", surface.draws == 1);
        assertTrue("its siblings still draw", label.draws == 2);
        assertTrue("the page's other children still draw", beside.draws == 2);
    }

    private static final class CountingSurface extends SurfaceView {
        int draws;

        CountingSurface(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override public void draw(android.graphics.Canvas canvas) {
            draws++;
            super.draw(canvas);
        }
    }

    private static final class CountingView extends View {
        int draws;

        CountingView(Context context) {
            super(context);
        }

        @Override public void draw(android.graphics.Canvas canvas) {
            draws++;
            super.draw(canvas);
        }
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
