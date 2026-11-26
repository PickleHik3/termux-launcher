package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CardGeometryTest {

    private static final float W = 100f;
    private static final float H = 200f;
    private static final float GAP = 4f;
    private static final float LINE = 10f;
    private static final float PAD = 2f;
    private static final float MIN_W = 30f;

    private static CardFacts.Pane term(String... lines) {
        return new CardFacts.Pane(false, false, lines);
    }

    private static List<CardGeometry.PaneBox> tiles(CardFacts.Node root) {
        return CardGeometry.workspace(new CardFacts.Workspace(root, null), W, H, GAP, LINE, PAD,
            MIN_W);
    }

    @Test public void onePaneFillsTheCard() {
        List<CardGeometry.PaneBox> boxes = tiles(term("a", "b", "c"));
        assertEquals(1, boxes.size());
        CardGeometry.PaneBox box = boxes.get(0);
        assertEquals(0f, box.left, 0f);
        assertEquals(W, box.right, 0f);
        assertEquals(H, box.bottom, 0f);
        assertEquals(3, box.textLines);
    }

    @Test public void sideBySideSplitsByWeightAroundTheGap() {
        List<CardGeometry.PaneBox> boxes = tiles(
            new CardFacts.Split(true, 1f, 3f, term("a"), term("b")));
        assertEquals(2, boxes.size());
        assertEquals(0f, boxes.get(0).left, 0f);
        assertEquals((W - GAP) * 0.25f, boxes.get(0).right, 0.001f);
        assertEquals(boxes.get(0).right + GAP, boxes.get(1).left, 0.001f);
        assertEquals(W, boxes.get(1).right, 0.001f);
    }

    @Test public void stackedSplitHalvesTheHeight() {
        List<CardGeometry.PaneBox> boxes = tiles(
            new CardFacts.Split(false, 1f, 1f, term("a"), term("b")));
        assertEquals((H - GAP) / 2f, boxes.get(0).bottom, 0.001f);
        assertEquals(boxes.get(0).bottom + GAP, boxes.get(1).top, 0.001f);
        assertEquals(H, boxes.get(1).bottom, 0.001f);
    }

    @Test public void zeroWeightsShareEqually() {
        List<CardGeometry.PaneBox> boxes = tiles(
            new CardFacts.Split(true, 0f, 0f, term("a"), term("b")));
        assertEquals((W - GAP) / 2f, boxes.get(0).right, 0.001f);
    }

    @Test public void aNarrowPaneFallsBackToABlock() {
        List<CardGeometry.PaneBox> boxes = tiles(
            new CardFacts.Split(true, 1f, 4f, term("a", "b", "c"), term("a", "b", "c")));
        assertEquals(0, boxes.get(0).textLines);
        assertTrue(boxes.get(1).textLines > 0);
    }

    @Test public void aShortPaneFallsBackToABlock() {
        // Room for one row only: fewer than MIN_TEXT_LINES.
        CardFacts.Pane pane = term("a", "b", "c");
        assertEquals(0, CardGeometry.textLines(pane, 80f, LINE + 2 * PAD, LINE, PAD, MIN_W));
        assertEquals(2, CardGeometry.textLines(pane, 80f, 2 * LINE + 2 * PAD, LINE, PAD, MIN_W));
    }

    @Test public void aLinuxPaneIsAlwaysABlock() {
        CardFacts.Pane linux = new CardFacts.Pane(true, false, new String[] {"x", "y", "z"});
        assertEquals(0, tiles(linux).get(0).textLines);
    }

    @Test public void aPaneWithNoTextIsABlock() {
        assertEquals(0, tiles(term()).get(0).textLines);
    }

    @Test public void textLinesNeverExceedWhatThePaneHolds() {
        assertEquals(2, tiles(term("a", "b")).get(0).textLines);
    }

    @Test public void floatsDrawAfterTilesAtTheirFractions() {
        CardFacts.Workspace facts = new CardFacts.Workspace(term("a", "b"),
            Collections.singletonList(
                new CardFacts.Floating(term("f", "g"), 0.5f, 0.25f, 1f, 0.75f)));
        List<CardGeometry.PaneBox> boxes = CardGeometry.workspace(facts, W, H, GAP, LINE, PAD,
            MIN_W);
        assertEquals(2, boxes.size());
        assertTrue(boxes.get(1).floating);
        assertEquals(50f, boxes.get(1).left, 0.001f);
        assertEquals(50f, boxes.get(1).top, 0.001f);
        assertEquals(150f, boxes.get(1).bottom, 0.001f);
    }

    @Test public void aWindowWithNoPanesHasNoBoxes() {
        assertTrue(tiles(null).isEmpty());
    }

    @Test public void homeCellsSpanTheirGridCells() {
        CardFacts.Home home = new CardFacts.Home(5, 4, Arrays.asList(
            new CardFacts.Cell(0, 0, 4, 1, CardFacts.CellKind.WIDGET),
            new CardFacts.Cell(1, 2, 2, 3, CardFacts.CellKind.ICON)));
        List<CardGeometry.CellBox> boxes = CardGeometry.home(home, 100f, 200f, 2f, 3f);
        assertEquals(2, boxes.size());
        // The widget spans the width, inset by the gap only.
        assertEquals(2f, boxes.get(0).left, 0.001f);
        assertEquals(98f, boxes.get(0).right, 0.001f);
        assertEquals(2f, boxes.get(0).top, 0.001f);
        // The icon sits in cell (1,2): 25..50 by 80..120, inset by gap plus the icon inset.
        assertEquals(25f + 5f, boxes.get(1).left, 0.001f);
        assertEquals(50f - 5f, boxes.get(1).right, 0.001f);
        assertEquals(80f + 5f, boxes.get(1).top, 0.001f);
    }

    @Test public void homeCellsOutsideTheGridAreClipped() {
        CardFacts.Home home = new CardFacts.Home(2, 2, Arrays.asList(
            new CardFacts.Cell(0, 0, 5, 1, CardFacts.CellKind.WIDGET),
            new CardFacts.Cell(3, 0, 4, 1, CardFacts.CellKind.ICON)));
        List<CardGeometry.CellBox> boxes = CardGeometry.home(home, 100f, 100f, 0f, 0f);
        assertEquals(1, boxes.size());
        assertEquals(100f, boxes.get(0).right, 0.001f);
    }

    @Test public void emptyHomeHasNoBoxes() {
        assertTrue(CardGeometry.home(new CardFacts.Home(5, 4, new ArrayList<>()), W, H, 1f, 1f)
            .isEmpty());
    }

    @Test public void bubblesStackAtTheBottomNewestLast() {
        CardFacts.Assistant facts = new CardFacts.Assistant(new boolean[] {true, false},
            new float[] {1f, 0.5f});
        List<CardGeometry.BubbleBox> boxes = CardGeometry.assistant(facts, 100f, 100f, 20f, 10f,
            0.8f, 0.3f);
        assertEquals(2, boxes.size());
        assertEquals(100f, boxes.get(1).bottom, 0.001f);
        assertEquals(50f, boxes.get(0).top, 0.001f);
        // The user's bubble is flush right at its fill of the max; the answer flush left.
        assertEquals(100f, boxes.get(0).right, 0.001f);
        assertEquals(20f, boxes.get(0).left, 0.001f);
        assertEquals(0f, boxes.get(1).left, 0.001f);
        assertEquals(40f, boxes.get(1).right, 0.001f);
    }

    @Test public void bubblesThatDoNotFitAreDroppedOldestFirst() {
        CardFacts.Assistant facts = new CardFacts.Assistant(new boolean[] {true, false, true},
            new float[] {1f, 1f, 1f});
        // Room for two bubbles of 20 with a gap of 10: 50.
        List<CardGeometry.BubbleBox> boxes = CardGeometry.assistant(facts, 100f, 50f, 20f, 10f,
            1f, 0.3f);
        assertEquals(2, boxes.size());
        assertFalse(boxes.get(0).fromUser);
        assertTrue(boxes.get(1).fromUser);
    }

    @Test public void shortBubblesKeepAMinimumWidth() {
        CardFacts.Assistant facts = new CardFacts.Assistant(new boolean[] {false},
            new float[] {0f});
        List<CardGeometry.BubbleBox> boxes = CardGeometry.assistant(facts, 100f, 100f, 20f, 10f,
            0.8f, 0.3f);
        assertEquals(30f, boxes.get(0).width(), 0.001f);
    }
}
