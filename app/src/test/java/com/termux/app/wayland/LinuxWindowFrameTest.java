package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import com.termux.R;
import com.termux.view.HoldTiming;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Linux windows screen's corner hold: a window that scales gets the rail, opened on its stop,
 * and a stop chosen there reaches the host; an X window gets the shared-size line instead; the
 * window sees a cancel once the hold claims the finger.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowFrameTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    private static final class FakeHost implements LinuxWindowFrame.Host {
        boolean shown = true;
        boolean scales = true;
        int step = 250;
        String note;
        final List<Integer> chosen = new ArrayList<>();
        LinuxWindowFrame frame;

        @Override public boolean hasShownWindow() { return shown; }
        @Override public boolean shownWindowScales() { return scales; }
        @Override public int currentScaleStep() { return step; }
        @Override public void onScaleStepChosen(int s) { chosen.add(s); }
        @Override public void onScaleRailUsed() { frame.dismissRail(); }
        @Override public String shownWindowScaleNote() { return note; }
    }

    private final FakeHost host = new FakeHost();
    private final List<Integer> windowActions = new ArrayList<>();

    private LinuxWindowFrame frame() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        LinuxWindowFrame frame = new LinuxWindowFrame(activity, host);
        host.frame = frame;
        View window = new View(activity);
        window.setOnTouchListener((v, e) -> {
            windowActions.add(e.getActionMasked());
            return true;
        });
        frame.addWindowView(window);
        frame.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, WIDTH, HEIGHT);
        return frame;
    }

    private static void touch(LinuxWindowFrame frame, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        frame.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void holdCorner(LinuxWindowFrame frame) {
        touch(frame, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(frame, MotionEvent.ACTION_UP, WIDTH - 2f, 2f);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
    }

    private static float railY(LinuxWindowFrame frame, int fromY) {
        for (float y = fromY; y < HEIGHT; y += 4f) {
            if (frame.railView().hits(8f, y)) return y;
        }
        throw new AssertionError("the rail answers nowhere along the edge");
    }

    @Test
    public void aHeldCornerBringsTheRailOutOnTheWindowsStopAndTheWindowIsCancelled() {
        LinuxWindowFrame frame = frame();
        holdCorner(frame);
        assertTrue(frame.isRailShown());
        assertFalse(frame.isSharedLineShown());
        assertEquals(250, frame.railView().step());
        assertEquals(MotionEvent.ACTION_DOWN, (int) windowActions.get(0));
        assertTrue("the window was told to forget the touch", windowActions.contains(MotionEvent.ACTION_CANCEL));
        assertFalse("and never saw the lift", windowActions.contains(MotionEvent.ACTION_UP));
    }

    @Test
    public void aStopChosenOnTheRailReachesTheHostAndPutsTheRailAway() {
        LinuxWindowFrame frame = frame();
        holdCorner(frame);
        float top = railY(frame, 0);
        windowActions.clear();
        touch(frame, MotionEvent.ACTION_DOWN, 8f, top + 2f);
        touch(frame, MotionEvent.ACTION_UP, 8f, top + 2f);
        assertEquals(1, host.chosen.size());
        assertEquals(100, (int) host.chosen.get(0));
        assertFalse(frame.isRailShown());
        assertTrue("the window never saw the rail's finger", windowActions.isEmpty());
    }

    @Test
    public void anXWindowsHoldSaysItSharesOneSizeInsteadOfTheRail() {
        host.scales = false;
        LinuxWindowFrame frame = frame();
        holdCorner(frame);
        assertFalse(frame.isRailShown());
        assertTrue(frame.isSharedLineShown());
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS);
        assertFalse("the line goes by itself", frame.isSharedLineShown());
    }

    @Test
    public void aDesktopsRailSaysWhenTheNewSizeArrives() {
        host.note = "The desktop takes the new size the next time it opens.";
        LinuxWindowFrame frame = frame();
        holdCorner(frame);
        assertTrue(frame.isRailShown());
        assertTrue(frame.isSharedLineShown());
        assertEquals(host.note, frame.sharedLineText());
    }

    @Test
    public void aTapAwayFromTheCornersIsTheWindowsAlone() {
        LinuxWindowFrame frame = frame();
        touch(frame, MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT / 2f);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(frame, MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT / 2f);
        assertFalse(frame.isRailShown());
        assertEquals(2, windowActions.size());
        assertEquals(MotionEvent.ACTION_UP, (int) windowActions.get(1));
    }
}
