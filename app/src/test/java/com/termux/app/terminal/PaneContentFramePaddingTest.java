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
import android.app.Application;

/**
 * The pane's content is held clear of the corner arc with the clearance traded toward the sides
 * ({@link PaneShape#verticalInsetPx}, {@link PaneShape#sideInsetPx}): the same gap off the top as
 * off the bottom, the same off both sides, and for a terminal the top's margin makes up only what the
 * renderer's own headroom above the first row has not already paid — so the first cell is as far
 * off the top as the last row's cells are off the bottom, in normal and minimal mode alike (the
 * frame does not know which it is in, which is the point).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
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

    private static int verticalInset() {
        return PaneShape.verticalInsetPx(
            PaneShape.radiusForBounds(RADIUS_PX, FRAME_WIDTH, FRAME_HEIGHT), 0);
    }

    private static int sideInset() {
        return PaneShape.sideInsetPx(
            PaneShape.radiusForBounds(RADIUS_PX, FRAME_WIDTH, FRAME_HEIGHT), verticalInset());
    }

    @Test
    public void plainContentSitsCloserToTheTopAndBottomThanToTheSides() {
        View content = new View(RuntimeEnvironment.getApplication());
        frameAround(content);
        int vertical = verticalInset();
        int side = sideInset();
        assertTrue(vertical > 0);
        assertTrue("the vertical gap is the one trimmed",
            vertical < PaneShape.contentInsetPx(RADIUS_PX));
        assertTrue(side > vertical);
        assertEquals(side, content.getLeft());
        assertEquals(vertical, content.getTop());
        assertEquals(side, FRAME_WIDTH - content.getRight());
        assertEquals(vertical, FRAME_HEIGHT - content.getBottom());
    }

    @Test
    public void aTerminalsFirstCellSitsAsFarOffTheTopAsItsLastRowOffTheBottom() {
        TerminalView terminal = new TerminalView(RuntimeEnvironment.getApplication(), null);
        terminal.setTextSize(30);
        frameAround(terminal);
        int vertical = verticalInset();
        int side = sideInset();
        int headroom = terminal.getFirstRowTopPx();
        assertEquals(side, terminal.getLeft());
        assertEquals(side, FRAME_WIDTH - terminal.getRight());
        assertEquals(vertical, FRAME_HEIGHT - terminal.getBottom());
        // The top margin plus the renderer's own headroom is the same band as the bottom's.
        assertEquals(vertical, terminal.getTop() + Math.min(headroom, vertical));
        assertTrue("the top margin never exceeds the bottom's", terminal.getTop() <= vertical);
    }
}
