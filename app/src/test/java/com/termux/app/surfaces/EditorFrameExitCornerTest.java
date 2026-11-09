package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The frame's corner squares off as it grows back, so the clip comes off at full size unseen. */
public class EditorFrameExitCornerTest {

    @Test
    public void theCornerShrinksWithTheGrowthLeftAndIsSquareAtFullSize() {
        assertEquals(48f, AppearanceEditorFrame.exitCornerPx(48f, 0.6f, 0.6f), 1e-4f);
        assertEquals(24f, AppearanceEditorFrame.exitCornerPx(48f, 0.6f, 0.8f), 1e-4f);
        assertEquals(0f, AppearanceEditorFrame.exitCornerPx(48f, 0.6f, 1f), 0f);
        assertEquals("past full size on the spring, still square", 0f,
            AppearanceEditorFrame.exitCornerPx(48f, 0.6f, 1.01f), 0f);
        assertEquals("not scaled, nothing to round", 0f,
            AppearanceEditorFrame.exitCornerPx(48f, 1f, 1f), 0f);
    }
}
