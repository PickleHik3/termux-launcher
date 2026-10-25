package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EditorRowFitTest {

    @Test
    public void aRowIsOneLineWhenLabelGapAndControlFit() {
        assertTrue(EditorRowFit.oneLine(300, 100f, 160f, 40));
        assertFalse(EditorRowFit.oneLine(299, 100f, 160f, 40));
    }

    @Test
    public void segmentsNeedTheirWordsPaddingAndGaps() {
        // Three words of 100px in all, 16px a side, 4px between.
        assertEquals(100f + 96f + 8f, EditorRowFit.segmentsMinPx(100f, 3, 16, 4, 1f), 0.01f);
        assertEquals(50f + 96f + 8f, EditorRowFit.segmentsMinPx(100f, 3, 16, 4, 0.5f), 0.01f);
    }

    @Test
    public void segmentsShrinkTheirWordsBeforeStacking() {
        // 204px of control at full size beside a 60px label and 40px gap needs 304px; less room leaves a
        // smaller text scale, and too little leaves none.
        float scale = EditorRowFit.segmentsScale(310, 60f, 40, 100f, 3, 16, 4, 0.75f, 0.05f);
        assertEquals(1f, scale, 0.001f);
        float tight = EditorRowFit.segmentsScale(280, 60f, 40, 100f, 3, 16, 4, 0.75f, 0.05f);
        assertTrue(tight < 1f && tight >= 0.75f);
        assertEquals(0f, EditorRowFit.segmentsScale(220, 60f, 40, 100f, 3, 16, 4, 0.75f, 0.05f),
            0f);
    }

    @Test
    public void aStripGapIsWhatTheColumnsLeaveUpToTheMaximum() {
        assertEquals(12, EditorRowFit.stripGapPx(379, 30, 7, 12));
        assertEquals(8, EditorRowFit.stripGapPx(379, 40, 8, 12));
        assertEquals(0, EditorRowFit.stripGapPx(300, 40, 8, 12));
        assertEquals(12, EditorRowFit.stripGapPx(379, 40, 1, 12));
    }
}
