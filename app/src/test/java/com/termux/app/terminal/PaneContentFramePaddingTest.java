package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The pane's content sits the same distance off every edge of its frame: the arc's clearance on
 * all four sides for any content, and for a terminal the top's margin makes up only what the
 * renderer's own headroom above the first row has not already paid — so the first cell is as far
 * off the top as the last row's cells are off the bottom, in normal and minimal mode alike (the
 * frame does not know which it is in, which is the point).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneContentFramePaddingTest {

    private static final int FRAME_WIDTH = 600;
    private static final int FRAME_HEIGHT = 900;
    private static final float RADIUS_PX = 30f;

    private static PaneContentFrame frameAround(View content) {
        Context context = RuntimeEnvironment.getApplication();
        PaneContentFrame frame = new PaneContentFrame(context);
        frame.addView(content, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.setPaneContent(content);
        frame.setPaneShape(RADIUS_PX, true);
        frame.measure(View.MeasureSpec.makeMeasureSpec(FRAME_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT);
        return frame;
    }

    @Test
    public void plainContentSitsTheArcsClearanceOffEveryEdge() {
        View content = new View(RuntimeEnvironment.getApplication());
        PaneContentFrame frame = frameAround(content);
        int inset = PaneShape.contentInsetForBounds(RADIUS_PX, FRAME_WIDTH, FRAME_HEIGHT);
        assertTrue(inset > 0);
        assertEquals(inset, content.getLeft());
        assertEquals(inset, content.getTop());
        assertEquals(inset, FRAME_WIDTH - content.getRight());
        assertEquals(inset, FRAME_HEIGHT - content.getBottom());
    }

    @Test
    public void aTerminalsFirstCellSitsAsFarOffTheTopAsItsLastRowOffTheBottom() {
        TerminalView terminal = new TerminalView(RuntimeEnvironment.getApplication(), null);
        terminal.setTextSize(30);
        PaneContentFrame frame = frameAround(terminal);
        int inset = PaneShape.contentInsetForBounds(RADIUS_PX, FRAME_WIDTH, FRAME_HEIGHT);
        int headroom = terminal.getFirstRowTopPx();
        assertEquals(inset, terminal.getLeft());
        assertEquals(inset, FRAME_WIDTH - terminal.getRight());
        assertEquals(inset, FRAME_HEIGHT - terminal.getBottom());
        // The top margin plus the renderer's own headroom is the same band as the other edges.
        assertEquals(inset, terminal.getTop() + Math.min(headroom, inset));
        assertTrue("the top margin never exceeds the sides", terminal.getTop() <= inset);
    }
}
