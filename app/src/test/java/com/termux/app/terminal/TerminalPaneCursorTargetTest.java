package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import com.termux.terminal.TerminalEmulator;

import org.junit.Test;

/**
 * The cursor trail's target is the pixels the renderer paints the cursor on: one cell, or the
 * whole of a kitty text sizing block when the cursor stands in one. A target that stayed one cell
 * would leave a big heading cursor with a trail landing on its top-left corner only.
 */
public class TerminalPaneCursorTargetTest {

    private static final float CELL_WIDTH = 10f;
    private static final float CELL_HEIGHT = 24f;
    private static final float EPSILON = 0.001f;

    private static PaneMotionOverlayView.CursorTarget shape(int style, float left, float top,
                                                            int columns, int rows) {
        PaneMotionOverlayView.CursorTarget out = new PaneMotionOverlayView.CursorTarget();
        TerminalPaneController.cursorShapeRect(style, left, top, CELL_WIDTH * columns,
            CELL_HEIGHT * rows, CELL_WIDTH, CELL_HEIGHT, out);
        return out;
    }

    private static void assertRect(float left, float top, float right, float bottom,
                                   PaneMotionOverlayView.CursorTarget out) {
        assertEquals(left, out.left, EPSILON);
        assertEquals(top, out.top, EPSILON);
        assertEquals(right, out.right, EPSILON);
        assertEquals(bottom, out.bottom, EPSILON);
    }

    @Test
    public void aBlockCursorOnAPlainCellFillsTheCell() {
        assertRect(30f, 48f, 40f, 72f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK, 30f, 48f, 1, 1));
    }

    @Test
    public void aBlockCursorInATextSizingBlockFillsTheBlock() {
        // A scale-2 heading of three characters: six columns, two rows.
        assertRect(30f, 48f, 90f, 96f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK, 30f, 48f, 6, 2));
    }

    @Test
    public void aBarRunsTheBlocksFullHeightAtAPlainCellsThickness() {
        assertRect(30f, 48f, 32.5f, 72f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, 30f, 48f, 1, 1));
        assertRect(30f, 48f, 32.5f, 120f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, 30f, 48f, 6, 3));
    }

    @Test
    public void anUnderlineRunsAlongTheBlocksBottomAtAPlainCellsThickness() {
        assertRect(30f, 66f, 40f, 72f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE, 30f, 48f, 1, 1));
        assertRect(30f, 90f, 90f, 96f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE, 30f, 48f, 6, 2));
    }

    @Test
    public void aBlockWhoseTopScrolledAboveThePaneStillReachesItsBottom() {
        // The anchor row is in the transcript, so its top lies above the pane's own top.
        assertRect(0f, -24f, 40f, 24f,
            shape(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK, 0f, -24f, 4, 2));
    }
}
