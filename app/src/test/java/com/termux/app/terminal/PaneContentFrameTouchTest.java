package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The clearance the pane's shape owes the terminal is still the terminal's to touch.
 *
 * <p>The margin belongs to no view, so a press on the pane's outermost pixels used to reach
 * nothing — the defect only a pane corner escaped, because the interaction overlay forwards there.
 * The frame forwards the rest of the band the same way: the same offset, no clamp, so the terminal
 * hears the edge cell rather than the middle one a {@code TouchDelegate} would have reported.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneContentFrameTouchTest {

    private static final int FRAME_WIDTH = 200;
    private static final int FRAME_HEIGHT = 400;
    private static final float RADIUS_PX = 30f;

    private PaneContentFrame mFrame;
    private RecordingContent mContent;
    private int mInset;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mFrame = new PaneContentFrame(context);
        mContent = new RecordingContent(context);
        mFrame.addView(mContent, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mFrame.setPaneContent(mContent);
        mFrame.setPaneShape(RADIUS_PX, true);
        mFrame.measure(View.MeasureSpec.makeMeasureSpec(FRAME_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, View.MeasureSpec.EXACTLY));
        mFrame.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT);
        mInset = PaneShape.contentInsetForBounds(RADIUS_PX, FRAME_WIDTH, FRAME_HEIGHT);
        assertTrue("the shape has to owe a clearance for there to be a band to test", mInset > 0);
        assertEquals(mInset, mContent.getLeft());
    }

    @Test
    public void aPressInTheSideBandReachesTheTerminalPastItsOwnEdge() {
        dispatch(MotionEvent.ACTION_DOWN, 3f, 200f);

        assertEquals(1, mContent.events.size());
        Touch touch = mContent.events.get(0);
        assertEquals(MotionEvent.ACTION_DOWN, touch.action);
        assertTrue("the first column is reached from outside it, not from the middle of the view",
            touch.x < 0f);
        assertEquals("nothing is re-centred: the point only moves into the child's space",
            3f - mInset, touch.x, 0.001f);
        assertEquals(200f - mInset, touch.y, 0.001f);
    }

    @Test
    public void aPressInTheBottomBandReachesTheTerminalBelowItsLastRow() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, FRAME_HEIGHT - 2f);

        assertEquals(1, mContent.events.size());
        Touch touch = mContent.events.get(0);
        assertTrue("past the last row, which the terminal pins to it",
            touch.y >= mContent.getHeight());
        assertEquals(100f - mInset, touch.x, 0.001f);
    }

    @Test
    public void aPressOnTheTerminalItselfIsDeliveredOnceAndUntouched() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 200f);

        assertEquals("delivered by the ordinary dispatch, and not a second time by ours",
            1, mContent.events.size());
        Touch touch = mContent.events.get(0);
        assertEquals(100f - mInset, touch.x, 0.001f);
        assertEquals(200f - mInset, touch.y, 0.001f);
    }

    @Test
    public void aGestureStartedInTheBandKeepsItsMoveAndItsLift() {
        dispatch(MotionEvent.ACTION_DOWN, 3f, 200f);
        dispatch(MotionEvent.ACTION_MOVE, 3f, 260f);
        dispatch(MotionEvent.ACTION_UP, 3f, 300f);

        assertEquals(3, mContent.events.size());
        assertEquals(MotionEvent.ACTION_DOWN, mContent.events.get(0).action);
        assertEquals(MotionEvent.ACTION_MOVE, mContent.events.get(1).action);
        assertEquals(MotionEvent.ACTION_UP, mContent.events.get(2).action);
        assertEquals(260f - mInset, mContent.events.get(1).y, 0.001f);
        assertEquals(300f - mInset, mContent.events.get(2).y, 0.001f);

        // And the next gesture is judged on its own landing, not on the last one's.
        mContent.events.clear();
        dispatch(MotionEvent.ACTION_DOWN, 100f, 200f);
        assertEquals(1, mContent.events.size());
    }

    @Test
    public void aCancelledGestureIsCancelledOnTheTerminalToo() {
        dispatch(MotionEvent.ACTION_DOWN, 3f, 200f);
        dispatch(MotionEvent.ACTION_CANCEL, 3f, 200f);

        assertEquals(2, mContent.events.size());
        assertEquals(MotionEvent.ACTION_CANCEL, mContent.events.get(1).action);
    }

    private void dispatch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            mFrame.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** One event as the content saw it, since the event itself is recycled behind us. */
    private static final class Touch {
        final int action;
        final float x;
        final float y;

        Touch(@NonNull MotionEvent event) {
            action = event.getActionMasked();
            x = event.getX();
            y = event.getY();
        }
    }

    /** Stands in for the terminal: keeps every gesture and remembers where it was told it was. */
    private static final class RecordingContent extends View {
        final List<Touch> events = new ArrayList<>();

        RecordingContent(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            events.add(new Touch(event));
            return true;
        }
    }
}
