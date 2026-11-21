package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Built-in widgets relayout, never zoom (ADR 0020 §12, spec §8b): however tall the card a turn
 * of the screen gives it, the most it draws its design at is 1.
 */
public class BuiltinWidgetNoZoomTest {

    @Test
    public void aCardTallerThanItsDesignNeverDrawsItLarger() {
        float design = 300f;
        for (float height : new float[] {150f, 300f, 301f, 450f, 600f, 2000f}) {
            float most = BuiltinWidgetView.mostZoom(height, design);
            assertTrue(height + "px: " + most, most <= 1f);
        }
        assertTrue(BuiltinWidgetView.mostZoom(500f, 0f) <= 1f);
    }
}
