package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorMotion.Token;
import com.termux.app.surfaces.EditorMotion.Use;

import org.junit.Test;

/** The editor's four springs (spec §6) and every use named against one. */
public class EditorMotionTest {

    @Test
    public void theFourSpringsSettleWhereTheSpecSays() {
        // Spec §6: ≈ 235, ≈ 255, ≈ 95 and ≈ 145 ms to within 2%.
        assertNear(235, Token.SPATIAL_FAST.settleMs());
        assertNear(255, Token.SPATIAL_DEFAULT.settleMs());
        assertNear(95, Token.EFFECTS_FAST.settleMs());
        assertNear(145, Token.EFFECTS_DEFAULT.settleMs());
    }

    @Test
    public void spatialFastOvershootsAndTheFadesNever() {
        float peak = 0f;
        for (int ms = 0; ms < 400; ms++) peak = Math.max(peak, Token.SPATIAL_FAST.progressAt(ms));
        assertTrue("about 10% over: " + peak, peak > 1.05f && peak < 1.15f);
        for (int ms = 0; ms < 400; ms++)
            assertTrue(Token.EFFECTS_FAST.progressAt(ms) <= 1f + 1e-6f);
    }

    @Test
    public void everyUseNamesItsSpring() {
        assertEquals(Token.SPATIAL_DEFAULT, Use.SHEET_DETENT.token);
        assertEquals(Token.SPATIAL_DEFAULT, Use.SHEET_FALL.token);
        assertEquals(Token.SPATIAL_DEFAULT, Use.FRAME_POSE.token);
        assertEquals(Token.SPATIAL_DEFAULT, Use.EDITOR_ENTER.token);
        assertEquals(Token.SPATIAL_DEFAULT, Use.EDITOR_EXIT.token);
        assertEquals(Token.EFFECTS_DEFAULT, Use.REVEAL_IN.token);
        assertEquals(Token.EFFECTS_FAST, Use.REVEAL_OUT.token);
        assertEquals(Token.EFFECTS_DEFAULT, Use.OVERLAY_IN.token);
        assertEquals(Token.EFFECTS_FAST, Use.OVERLAY_OUT.token);
        assertEquals(Token.SPATIAL_FAST, Use.RING_MOVE.token);
        assertEquals(Token.SPATIAL_FAST, Use.BODY_SETTLE.token);
        assertEquals(Token.SPATIAL_FAST, Use.VALUE_SPRING.token);
        assertEquals(12, Use.values().length);
    }

    @Test
    public void reducedMotionJumpsTheMovesAndKeepsTheFades() {
        assertEquals(0L, Use.SHEET_DETENT.durationMs(true));
        assertNull(Use.RING_MOVE.tokenFor(true));
        assertEquals(Token.EFFECTS_DEFAULT, Use.EDITOR_ENTER.tokenFor(true));
        assertEquals(Token.EFFECTS_FAST.settleMs(), Use.REVEAL_OUT.durationMs(true));
        assertEquals(Token.SPATIAL_DEFAULT.settleMs(), Use.SHEET_DETENT.durationMs(false));
    }

    @Test
    public void aCurveStartsAtZeroAndEndsAtOne() {
        for (Use use : Use.values()) {
            assertEquals(use.name(), 0f, use.curve(false).getInterpolation(0f), 1e-4f);
            assertEquals(use.name(), 1f, use.curve(false).getInterpolation(1f), 1e-4f);
        }
    }

    private static void assertNear(long expected, long actual) {
        assertTrue("expected about " + expected + " ms, was " + actual,
            Math.abs(expected - actual) <= expected * 0.15);
    }
}
