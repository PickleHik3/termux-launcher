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
}
