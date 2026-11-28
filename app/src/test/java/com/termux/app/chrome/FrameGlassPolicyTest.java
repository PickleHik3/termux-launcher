package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FrameGlassPolicyTest {

    @Test
    public void standDownOnlyWhenAllThreeHold() {
        assertTrue(FrameGlassPolicy.sheetsStandDown(true, true, false, true));
    }

    @Test
    public void opaqueModeKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(false, true, false, true));
    }

    @Test
    public void glassOffKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, false, false, true));
    }

    @Test
    public void floatingOrEdgeCardsKeepEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, true, false, false));
    }

    /**
     * The developer, 11-28: under Terl v1's Docked frame glass is not drawn at all (the wall's dim
     * is the page's Wallpaper dim, ADR 0031 section 4), keyboard up or down, joined card or not;
     * nothing stands down on it, so the keyboard keeps its own glass. Round 5's Terl and Docked
     * gate drew it.
     */
    @Test
    public void underTerlTheFrameGlassIsNeverDrawnAndTheKeyboardKeepsItsOwn() {
        for (boolean joined : new boolean[] {true, false}) {
            assertFalse("joined " + joined, FrameGlassPolicy.glassShows(true, true, joined));
            assertFalse("the keyboard keeps its own glass, joined " + joined,
                FrameGlassPolicy.sheetsStandDown(true, true, true, joined));
        }
    }

    @Test
    public void withoutTerlTheShapeModelDecides() {
        assertTrue(FrameGlassPolicy.glassShows(true, false, true));
        assertFalse(FrameGlassPolicy.glassShows(true, false, false));
        assertFalse("no pane glass, no frame glass", FrameGlassPolicy.glassShows(false, false, true));
    }

    /**
     * The activity goes through the gate with what it measures (review P2-4): both frame-glass
     * calls ask {@code frameGlassShows}, which hands the gate the control bar's presence and the
     * shape's own card; the sheets' stand-down reads the bar too; the shape's card reads no Terl
     * override.
     */
    @Test
    public void theActivityAsksTheGateWithTheShapesOwnCard() throws IOException {
        String activity = source();
        assertFalse("every frame-glass call goes through the gate",
            activity.contains("applyFrameGutterGlass(frameGlassHost(), glassPane && hasDockedFrameCard())")
                || activity.contains("isTerminalPaneGlassActive() && hasDockedFrameCard())"));
        assertTrue(activity.contains("applyFrameGutterGlass(frameGlassHost(), frameGlassShows(glassPane))"));
        assertTrue(activity.contains("applyFrameGutterGlass(gutterBody, frameGlassShows(isTerminalPaneGlassActive()))"));
        String gate = body(activity, "private boolean frameGlassShows(boolean paneGlassActive)");
        assertTrue(gate, gate.contains("FrameGlassPolicy.glassShows(paneGlassActive, mControlBar != null,")
            && gate.contains("hasDockedFrameCard())") && !gate.contains("isFloatingStyle"));
        String card = body(activity, "private boolean hasDockedFrameCard()");
        assertFalse("the shape's card reads no Terl override: " + card,
            card.contains("mControlBar") || card.contains("FrameGlassPolicy"));
        String standDown = body(activity, "private boolean frameStandsDown()");
        assertTrue("the stand-down reads the bar too: under Terl nothing stands down: " + standDown,
            standDown.contains("hasDockedFrameCard()") && standDown.contains("mControlBar != null"));
    }

    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(signature, at >= 0);
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        return source.substring(open);
    }

    private static String source() throws IOException {
        Path path = Paths.get("src/main/java/com/termux/app/TermuxActivity.java");
        if (!Files.exists(path)) path = Paths.get("app/src/main/java/com/termux/app/TermuxActivity.java");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
