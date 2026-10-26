package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.robolectric.annotation.GraphicsMode;

/**
 * A pane under a terminal effect lays its terminal out inside the bezel: the grid's rect lies
 * inside the screen the effect is cut to, the bezel comes off with the effect, and only a change
 * of bezel asks for a layout. On API 33, the first that draws an effect at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.TIRAMISU})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaneContentFrameBezelTest {

    private static final int FRAME_WIDTH = 1080;
    private static final int FRAME_HEIGHT = 1800;
    private static final float RADIUS_PX = 30f;

    private static PaneContentFrame frameAround(View content) {
        Context context = RuntimeEnvironment.getApplication();
        PaneContentFrame frame = new PaneContentFrame(context);
        frame.addView(content, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.setPaneContent(content);
        frame.setPaneShape(RADIUS_PX, true);
        return frame;
    }

    private static void layOut(PaneContentFrame frame) {
        frame.measure(View.MeasureSpec.makeMeasureSpec(FRAME_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT);
    }

    private static TerminalView terminal() {
        TerminalView terminal = new TerminalView(RuntimeEnvironment.getApplication(), null);
        terminal.setTextSize(30);
        return terminal;
    }

    @Test
    public void theGridLiesInsideTheScreenTheEffectIsCutTo() {
        for (PaneRetroStyle style : new PaneRetroStyle[] {PaneRetroStyle.CRT, PaneRetroStyle.TFT}) {
            TerminalView terminal = terminal();
            PaneContentFrame frame = frameAround(terminal);
            frame.setRetroStyle(style);
            layOut(frame);
            assertTrue(style.name(), frame.bezelWorn());
            float[] screen = new float[4];
            frame.retroScreen(screen);
            float density = RuntimeEnvironment.getApplication().getResources()
                .getDisplayMetrics().density;
            assertEquals(PaneBezel.sidePx(true, density), screen[0], 0.5f);
            assertEquals(PaneBezel.topBottomPx(true, density), screen[1], 0.5f);
            // The grid: from the first row's top to the view's bottom, side to side.
            float gridTop = terminal.getTop() + terminal.getFirstRowTopPx();
            assertTrue(style + " left", terminal.getLeft() > screen[0]);
            assertTrue(style + " right", terminal.getRight() < screen[2]);
            assertTrue(style + " top", gridTop > screen[1]);
            assertTrue(style + " bottom", terminal.getBottom() < screen[3]);
        }
    }

    @Test
    public void theBezelComesOffWithTheEffectAndTheMarginsGoBack() {
        TerminalView terminal = terminal();
        PaneContentFrame frame = frameAround(terminal);
        layOut(frame);
        int plainLeft = terminal.getLeft();
        int plainBottom = FRAME_HEIGHT - terminal.getBottom();

        frame.setRetroStyle(PaneRetroStyle.CRT_AMBER);
        assertTrue("the effect coming on asks for the one relayout", frame.isLayoutRequested());
        layOut(frame);
        assertTrue(terminal.getLeft() > plainLeft);

        frame.setRetroStyle(PaneRetroStyle.CRT_GREEN);
        assertFalse("another CRT tint keeps the same bezel", frame.isLayoutRequested());

        frame.setRetroStyle(PaneRetroStyle.NONE);
        assertFalse(frame.bezelWorn());
        assertTrue(frame.isLayoutRequested());
        layOut(frame);
        assertEquals(plainLeft, terminal.getLeft());
        assertEquals(plainBottom, FRAME_HEIGHT - terminal.getBottom());
        float[] screen = new float[4];
        frame.retroScreen(screen);
        assertEquals(0f, screen[0], 0f);
        assertEquals(FRAME_HEIGHT, screen[3], 0f);
    }
}
