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
        assertTrue(FrameGlassPolicy.sheetsStandDown(true, true, true));
    }

    @Test
    public void opaqueModeKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(false, true, true));
    }

    @Test
    public void glassOffKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, false, true));
    }

    @Test
    public void floatingOrEdgeCardsKeepEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, true, false));
    }

    /**
     * Round 5: under Terl + Docked the keyboard up splits v1's shape into edge cards; the frame's
     * glass (the wall's dim) stays, and the keyboard does not stand down on it, so it keeps its own
     * Terl glass (review P1-1: holding the card itself stood the keyboard down and its Opacity and
     * Blur stopped reaching it).
     */
    @Test
    public void underTerlTheGlassStaysAndTheKeyboardKeepsItsOwn() {
        boolean joined = false; // the keyboard is up: edge cards
        assertTrue(FrameGlassPolicy.glassShows(true, true, true, joined));
        assertFalse("the keyboard keeps its own glass", FrameGlassPolicy.sheetsStandDown(true, true, joined));
    }

    @Test
    public void underTerlFloatingHasNoFrameToKeep() {
        assertFalse(FrameGlassPolicy.glassShows(true, true, false, false));
    }

    @Test
    public void withoutTerlTheShapeModelDecides() {
        assertTrue(FrameGlassPolicy.glassShows(true, false, true, true));
        assertFalse(FrameGlassPolicy.glassShows(true, false, true, false));
        assertFalse("no pane glass, no frame glass", FrameGlassPolicy.glassShows(false, true, true, true));
    }

    /**
     * The activity goes through the gate with what it measures (review P2-4): both frame-glass
     * calls ask {@code frameGlassShows}, which hands the gate the control bar's presence, the
     * Style and the shape's own card; the sheets' stand-down and the shape's card read no Terl
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
            && gate.contains("!isFloatingStyle(), hasDockedFrameCard())"));
        String card = body(activity, "private boolean hasDockedFrameCard()");
        assertFalse("the shape's card reads no Terl override: " + card,
            card.contains("mControlBar") || card.contains("FrameGlassPolicy"));
        String standDown = body(activity, "private boolean frameStandsDown()");
        assertTrue(standDown, standDown.contains("hasDockedFrameCard()") && !standDown.contains("mControlBar"));
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
