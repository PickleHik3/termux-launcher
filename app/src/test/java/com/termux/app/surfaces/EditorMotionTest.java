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
        // Spec §6: ≈ 235, ≈ 255, ≈ 95 and ≈ 145 ms to within 2%: ln(50)/(ζω) underdamped,
        // 5.83/ω critically damped.
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
        assertEquals(21, Use.values().length);
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

    // ------------------------------------------------------------------ §7, one case per rule

    private static void assertRule(EditorMotion.Rule rule, Object... useThenToken) {
        java.util.List<Use> uses = java.util.Arrays.asList(rule.uses());
        assertEquals(rule + " plays these uses", useThenToken.length / 2, uses.size());
        for (int i = 0; i < useThenToken.length; i += 2) {
            Use use = (Use) useThenToken[i];
            assertTrue(rule + " plays " + use, uses.contains(use));
            assertEquals(rule + ": " + use, useThenToken[i + 1], use.token);
        }
    }

    @Test
    public void ruleA_cardSwap_labelsAndBodyOutFastInDefault_riseAndRingSpatialFast() {
        assertRule(EditorMotion.Rule.CARD_SWAP, Use.LABEL_OUT, Token.EFFECTS_FAST,
            Use.LABEL_IN, Token.EFFECTS_DEFAULT, Use.BODY_OUT, Token.EFFECTS_FAST,
            Use.BODY_SETTLE, Token.SPATIAL_FAST, Use.RING_MOVE, Token.SPATIAL_FAST);
        assertEquals("the body fades in on effects-default", Token.EFFECTS_DEFAULT,
            Use.BODY_SETTLE.reduced);
    }

    @Test
    public void ruleB_appearanceSwap_isRuleA() {
        assertEquals(java.util.Arrays.asList(EditorMotion.Rule.CARD_SWAP.uses()),
            java.util.Arrays.asList(EditorMotion.Rule.APPEARANCE_SWAP.uses()));
    }

    @Test
    public void ruleC_pageSwipe_ringFadesOutFastAndInDefault_cardSwapsByA() {
        assertRule(EditorMotion.Rule.PAGE_SWIPE, Use.RING_FADE_OUT, Token.EFFECTS_FAST,
            Use.RING_FADE_IN, Token.EFFECTS_DEFAULT, Use.LABEL_OUT, Token.EFFECTS_FAST,
            Use.LABEL_IN, Token.EFFECTS_DEFAULT, Use.BODY_OUT, Token.EFFECTS_FAST,
            Use.BODY_SETTLE, Token.SPATIAL_FAST);
    }

    @Test
    public void ruleD_wallpaperPage_risesOnSpatialDefault_ringFades() {
        assertRule(EditorMotion.Rule.WALLPAPER_PAGE, Use.WALLPAPER_PAGE, Token.SPATIAL_DEFAULT,
            Use.RING_FADE_OUT, Token.EFFECTS_FAST, Use.RING_FADE_IN, Token.EFFECTS_DEFAULT);
        assertEquals("reduced motion: the page jumps", 0L, Use.WALLPAPER_PAGE.durationMs(true));
    }

    @Test
    public void ruleE_detent_spatialDefault_startsAtTheReleaseVelocity() {
        assertRule(EditorMotion.Rule.DETENT, Use.SHEET_DETENT, Token.SPATIAL_DEFAULT,
            Use.FRAME_POSE, Token.SPATIAL_DEFAULT);
        float early = 0.1f;
        float still = Use.SHEET_DETENT.curve(false, 0).getInterpolation(early);
        float flung = Use.SHEET_DETENT.curve(false, 10).getInterpolation(early);
        assertTrue("a release toward the detent is further along early", flung > still);
        assertEquals(1f, Use.SHEET_DETENT.curve(false, 10).getInterpolation(1f), 0f);
        assertEquals(0f, Use.SHEET_DETENT.curve(false, 10).getInterpolation(0f), 1e-6f);
        assertEquals("no distance, no share", 0.0, EditorCardSheet.releaseShare(100f, 100f, 900f),
            0.0);
        assertEquals("a fling is capped", EditorCardSheet.RELEASE_SHARE_MAX,
            EditorCardSheet.releaseShare(0f, 10f, 1e6f), 0.0);
    }

    @Test
    public void ruleF_editor_enterAndExitOnSpatialDefault_barDropsSpatialFast() {
        assertRule(EditorMotion.Rule.EDITOR, Use.EDITOR_ENTER, Token.SPATIAL_DEFAULT,
            Use.EDITOR_EXIT, Token.SPATIAL_DEFAULT, Use.BAR_DROP, Token.SPATIAL_FAST,
            Use.SHEET_FALL, Token.SPATIAL_DEFAULT);
        assertEquals("the exit fades on effects-fast", Token.EFFECTS_FAST, Use.EDITOR_EXIT.reduced);
    }

    @Test
    public void ruleG_resetRow_valueSpatialFast_dotOutFastInDefault() {
        assertRule(EditorMotion.Rule.RESET_ROW, Use.VALUE_SPRING, Token.SPATIAL_FAST,
            Use.DOT_OUT, Token.EFFECTS_FAST, Use.DOT_IN, Token.EFFECTS_DEFAULT);
        assertNull("reduced motion: the value jumps", Use.VALUE_SPRING.tokenFor(true));
        assertEquals("reduced motion: the dot still fades", Token.EFFECTS_FAST,
            Use.DOT_OUT.tokenFor(true));
    }

    private static void assertNear(long expected, long actual) {
        assertTrue("expected about " + expected + " ms, was " + actual,
            Math.abs(expected - actual) <= expected * 0.03);
    }
}
