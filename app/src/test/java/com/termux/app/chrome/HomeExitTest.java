package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeExitTest {

    /** A clock and frame source the test drives by hand. */
    private static final class FakeFrames implements HomeExit.Frames {
        long now;
        Runnable frame;
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> due = new ArrayList<>();

        @Override public long nowMs() { return now; }
        @Override public void requestFrame(Runnable cb) { frame = cb; }
        @Override public void removeFrame(Runnable cb) { if (frame == cb) frame = null; }
        @Override public void postDelayed(Runnable r, long ms) { delayed.add(r); due.add(now + ms); }
        @Override public void removeDelayed(Runnable r) {
            int i = delayed.indexOf(r);
            if (i >= 0) { delayed.remove(i); due.remove(i); }
        }

        /** Delivers the pending frame at {@code at}. */
        void frameAt(long at) {
            now = at;
            Runnable cb = frame;
            frame = null;
            if (cb != null) cb.run();
        }

        /** Advances to {@code at}, running every timer that came due. */
        void advanceTo(long at) {
            now = at;
            for (int i = 0; i < delayed.size(); ) {
                if (due.get(i) <= at) {
                    Runnable r = delayed.remove(i);
                    due.remove(i);
                    r.run();
                } else i++;
            }
        }
    }

    private FakeFrames frames;
    private HomeExit exit;
    private View view;
    private int ended;

    @Before public void setUp() {
        frames = new FakeFrames();
        exit = new HomeExit(frames);
        view = new View(ApplicationProvider.getApplicationContext());
        view.setAlpha(1f);
        ended = 0;
    }

    private List<View> views() {
        List<View> list = new ArrayList<>();
        list.add(view);
        return list;
    }

    private void assertAtRest() {
        assertEquals(1f, view.getAlpha(), 0f);
        assertEquals(1f, view.getScaleX(), 0f);
        assertEquals(1f, view.getScaleY(), 0f);
    }

    @Test public void runsAsOneGroupAndEndsOnceAtRest() {
        exit.start(views(), false, () -> ended++);
        assertTrue(exit.isRunning());
        frames.frameAt(0);
        frames.frameAt(125);
        assertTrue(view.getAlpha() < 1f && view.getAlpha() > 0f);
        assertTrue(view.getScaleX() < 1f && view.getScaleX() > 0.9f);
        assertEquals(view.getScaleX(), view.getScaleY(), 0f);
        frames.frameAt(HomeExit.DURATION_MS + 1);
        assertEquals(1, ended);
        assertFalse(exit.isRunning());
        assertAtRest();
    }

    @Test public void reducedMotionEndsAtOnceWithNoFrame() {
        exit.start(views(), true, () -> ended++);
        assertEquals(1, ended);
        assertFalse(exit.isRunning());
        assertAtRest();
    }

    @Test public void cancelRunsTheEndOnceAndLeavesViewsAtRest() {
        exit.start(views(), false, () -> ended++);
        frames.frameAt(0);
        frames.frameAt(100);
        exit.cancel();
        exit.cancel();
        assertEquals(1, ended);
        assertAtRest();
        assertTrue(frames.delayed.isEmpty());
    }

    @Test public void noFrameAtAllIsEndedByTheBackstop() {
        exit.start(views(), false, () -> ended++);
        frames.advanceTo(HomeExit.FIRST_FRAME_MS + 1);
        assertEquals(1, ended);
        assertFalse(exit.isRunning());
        assertAtRest();
    }

    @Test public void framesThatStallAreEndedByTheTimeBackstop() {
        exit.start(views(), false, () -> ended++);
        frames.frameAt(0);
        frames.frameAt(50);
        frames.advanceTo(HomeExit.DURATION_MS + HomeExit.BACKSTOP_SLACK_MS + 1);
        assertEquals(1, ended);
        assertAtRest();
    }

    @Test public void aSecondPressFinishesItAtOnce() {
        exit.start(views(), false, () -> ended++);
        frames.frameAt(0);
        frames.frameAt(60);
        exit.finishNow();
        assertEquals(1, ended);
        assertAtRest();
        frames.frameAt(200);
        assertEquals(1, ended);
    }

    @Test public void startingWhileRunningFinishesThePreviousFirst() {
        exit.start(views(), false, () -> ended++);
        frames.frameAt(0);
        exit.start(views(), false, () -> ended += 10);
        assertEquals(1, ended);
        exit.finishNow();
        assertEquals(11, ended);
    }

    @Test public void aMemberUnderItsOwnAnimatorIsNotFoughtOverOrReadAsRest() {
        view.setAlpha(0.4f);
        view.animate().alpha(1f).setDuration(1000L).start();
        exit.start(views(), false, () -> ended++);
        frames.frameAt(0);
        // Its own animator would have moved it by now; the exit is the only writer.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0.4f, view.getAlpha(), 0.001f);
        frames.frameAt(HomeExit.DURATION_MS + 1);
        assertEquals(1, ended);
        assertEquals(0.4f, view.getAlpha(), 0.001f);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0.4f, view.getAlpha(), 0.001f);
    }
}
