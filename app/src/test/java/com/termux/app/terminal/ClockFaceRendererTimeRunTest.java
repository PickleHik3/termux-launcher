package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The fit measures the time run the way the text faces draw it, a glyph at a time at each glyph's
 * own advance, so a platform whose run shapes differently from its glyphs alone cannot make the
 * face draw wider than the fit allowed for.
 */
public class ClockFaceRendererTimeRunTest {

    /**
     * A platform that keeps letter spacing off a run's ends: a glyph alone is 10 wide, and a run
     * of n carries {@code spacing} between its glyphs only.
     */
    private static final class EdgelessSpacing implements ClockFaceRenderer.TextWidths.Measurer {
        final float spacing;

        EdgelessSpacing(float spacing) {
            this.spacing = spacing;
        }

        @Override public float measure(String text) {
            return text.length() * 10f + Math.max(0, text.length() - 1) * spacing;
        }
    }

    @Test
    public void theRunIsTheSumOfTheAdvancesTheDrawStepsBy() {
        EdgelessSpacing measurer = new EdgelessSpacing(-2f);
        ClockFaceRenderer.CharAdvances advances = new ClockFaceRenderer.CharAdvances();
        Object face = new Object();
        float drawn = 0f;
        for (char c : "09:20".toCharArray()) drawn += advances.advance(c, face, 30f, -.045f,
            measurer);
        float fitted = ClockFaceRenderer.glyphRunWidth("09:20", face, 30f, -.045f, advances,
            measurer);
        assertEquals(drawn, fitted, 0f);
        // Measured whole, the run reads narrower than it draws: what cut the strip's clock.
        assertTrue(measurer.measure("09:20") < fitted);
    }

    @Test
    public void anEmptyRunIsNothing() {
        assertEquals(0f, ClockFaceRenderer.glyphRunWidth("", null, 30f, 0f,
            new ClockFaceRenderer.CharAdvances(), new EdgelessSpacing(0f)), 0f);
    }
}
