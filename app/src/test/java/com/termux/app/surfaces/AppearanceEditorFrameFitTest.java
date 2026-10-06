package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppearanceEditorFrameFitTest {

    @Test
    public void shortSheetLetsTheFrameGrowToTheCap() {
        // 2000px container, 1500px of room: fits at 0.75; 1900px of room hits the 0.86 cap.
        assertEquals(0.75f, AppearanceEditorFrame.fitScale(2000, 100, 1600,
            AppearanceEditorFrame.MAX_SCALE), 1e-4f);
        assertEquals(AppearanceEditorFrame.MAX_SCALE, AppearanceEditorFrame.fitScale(2000, 100,
            2000, AppearanceEditorFrame.MAX_SCALE), 1e-4f);
    }

    @Test
    public void tallSheetShrinksTheFrameButNotPastTheMinimum() {
        assertEquals(0.6f, AppearanceEditorFrame.fitScale(2000, 100, 1300,
            AppearanceEditorFrame.MAX_SCALE), 1e-4f);
        assertEquals(AppearanceEditorFrame.MIN_SCALE, AppearanceEditorFrame.fitScale(2000, 100,
            300, AppearanceEditorFrame.MAX_SCALE), 1e-4f);
    }

    @Test
    public void defaultCapStaysAtThePreferredScale() {
        assertEquals(AppearanceEditorFrame.PREFERRED_SCALE,
            AppearanceEditorFrame.fitScale(2000, 0, 2000), 1e-4f);
    }

    @Test
    public void theOutlineReachesTheDisplayEdges() {
        float[] rect = AppearanceEditorFrame.outlineRect(1080, 2100, 0, 84, 0, 63);
        assertEquals(0f, rect[0], 0f);
        assertEquals(-84f, rect[1], 0f);
        assertEquals(1080f, rect[2], 0f);
        assertEquals(2163f, rect[3], 0f);
    }

    @Test
    public void unknownInsetsLeaveTheContainersOwnRect() {
        float[] rect = AppearanceEditorFrame.outlineRect(1080, 2100, 0, 0, 0, 0);
        assertEquals(0f, rect[1], 0f);
        assertEquals(2100f, rect[3], 0f);
        float[] negative = AppearanceEditorFrame.outlineRect(1080, 2100, -5, -5, -5, -5);
        assertEquals(0f, negative[0], 0f);
        assertEquals(1080f, negative[2], 0f);
    }

    @Test
    public void aCornerInsideTheArcIsNotRounded() {
        assertEquals(0f, AppearanceEditorFrame.visibleCornerRadiusPx(100f, 60, 60), 0f);
        assertEquals(0f, AppearanceEditorFrame.visibleCornerRadiusPx(100f, 0, 120), 0f);
        assertEquals(100f, AppearanceEditorFrame.visibleCornerRadiusPx(100f, 0, 0), 0f);
        assertEquals(90f, AppearanceEditorFrame.visibleCornerRadiusPx(100f, 10, 0), 0f);
    }
}
