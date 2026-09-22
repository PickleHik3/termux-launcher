package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.az.AzScrubGesture.Bounds;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/**
 * The edge transform, on all four edges: what the canonical frame does to a point, a rectangle and
 * a direction, that the round trip comes back where it started, and that the strip's paging ends
 * land where a thumb would look for them.
 */
public class AzBarFrameTest {

    private static final float W = 1080f;
    private static final float H = 2400f;
    private static final float EPS = 0.001f;

    private static AzBarFrame frame(Edge edge) {
        return AzBarFrame.of(edge, W, H);
    }

    @Test
    public void aBottomBarIsTheCanonicalFrameItself() {
        AzBarFrame bottom = frame(Edge.BOTTOM);
        assertTrue(bottom.isCanonical());
        assertFalse(bottom.isVertical());
        assertEquals(300f, bottom.canonicalX(300f, 2350f), EPS);
        assertEquals(2350f, bottom.canonicalY(300f, 2350f), EPS);
        assertEquals(300f, bottom.screenX(300f, 2350f), EPS);
        assertEquals(2350f, bottom.screenY(300f, 2350f), EPS);
        assertEquals(W, bottom.alongLengthPx(), EPS);
        assertEquals(H, bottom.awayLengthPx(), EPS);
        // Away from a bottom bar is straight up the screen; the strip rises the same way.
        assertEquals(0f, bottom.awayDirectionX(), EPS);
        assertEquals(1f, bottom.awayDirectionY(), EPS);
    }

    @Test
    public void aTopBarMirrorsAwayFromTheBarWithoutTurningTheLetters() {
        AzBarFrame top = frame(Edge.TOP);
        assertFalse(top.isVertical());
        assertFalse(top.isCanonical());
        // The letters still read left to right.
        assertEquals(300f, top.canonicalX(300f, 40f), EPS);
        // A point near the top of the screen is near the bottom of the canonical frame, which is
        // where the bar is; the content below it is "above" the bar canonically.
        assertEquals(H - 40f, top.canonicalY(300f, 40f), EPS);
        assertEquals(H - 400f, top.canonicalY(300f, 400f), EPS);
        assertTrue(top.canonicalY(300f, 40f) > top.canonicalY(300f, 400f));
        assertEquals(W, top.alongLengthPx(), EPS);
        assertEquals(H, top.awayLengthPx(), EPS);
        assertEquals(0f, top.awayDirectionX(), EPS);
        assertEquals(-1f, top.awayDirectionY(), EPS);
    }

    @Test
    public void aLeftBarReadsDownTheColumnAndPointsAwayIntoTheContent() {
        AzBarFrame left = frame(Edge.LEFT);
        assertTrue(left.isVertical());
        // Along the bar is down the screen: a lower point is further along the letters.
        assertEquals(600f, left.canonicalX(30f, 600f), EPS);
        assertTrue(left.canonicalX(30f, 900f) > left.canonicalX(30f, 600f));
        // Away from the bar is to the right, so a point further right is smaller canonically.
        assertEquals(W - 30f, left.canonicalY(30f, 600f), EPS);
        assertTrue(left.canonicalY(400f, 600f) < left.canonicalY(30f, 600f));
        // The bar's own axis is the screen's height.
        assertEquals(H, left.alongLengthPx(), EPS);
        assertEquals(W, left.awayLengthPx(), EPS);
        assertEquals(-1f, left.awayDirectionX(), EPS);
        assertEquals(0f, left.awayDirectionY(), EPS);
    }

    @Test
    public void aRightBarReadsDownTheColumnAndPointsAwayTheOtherWay() {
        AzBarFrame right = frame(Edge.RIGHT);
        assertTrue(right.isVertical());
        assertEquals(600f, right.canonicalX(1050f, 600f), EPS);
        assertEquals(1050f, right.canonicalY(1050f, 600f), EPS);
        // Away from a right bar is to the left: a point further left is smaller canonically.
        assertTrue(right.canonicalY(700f, 600f) < right.canonicalY(1050f, 600f));
        assertEquals(H, right.alongLengthPx(), EPS);
        assertEquals(W, right.awayLengthPx(), EPS);
        assertEquals(1f, right.awayDirectionX(), EPS);
        assertEquals(0f, right.awayDirectionY(), EPS);
    }

    @Test
    public void everyEdgeRoundTripsAPoint() {
        float[][] points = {{0f, 0f}, {17f, 3f}, {540f, 1200f}, {W, H}, {1079f, 41f}};
        for (Edge edge : Edge.values()) {
            AzBarFrame frame = frame(edge);
            for (float[] point : points) {
                float cx = frame.canonicalX(point[0], point[1]);
                float cy = frame.canonicalY(point[0], point[1]);
                assertEquals(edge + " x", point[0], frame.screenX(cx, cy), EPS);
                assertEquals(edge + " y", point[1], frame.screenY(cx, cy), EPS);
            }
        }
    }

    @Test
    public void everyEdgeRoundTripsARectangleAndKeepsItSorted() {
        Bounds screen = new Bounds(120f, 1800f, 960f, 1900f);
        for (Edge edge : Edge.values()) {
            AzBarFrame frame = frame(edge);
            Bounds canonical = frame.toCanonical(screen);
            assertTrue(edge + " sorted", canonical.left <= canonical.right
                && canonical.top <= canonical.bottom);
            // A turn swaps which axis is which, so the sides trade lengths; a mirror keeps them.
            if (frame.isVertical()) {
                assertEquals(edge + " width", screen.height(), canonical.width(), EPS);
                assertEquals(edge + " height", screen.width(), canonical.height(), EPS);
            } else {
                assertEquals(edge + " width", screen.width(), canonical.width(), EPS);
                assertEquals(edge + " height", screen.height(), canonical.height(), EPS);
            }
            Bounds back = frame.toScreen(canonical);
            assertEquals(edge + " left", screen.left, back.left, EPS);
            assertEquals(edge + " top", screen.top, back.top, EPS);
            assertEquals(edge + " right", screen.right, back.right, EPS);
            assertEquals(edge + " bottom", screen.bottom, back.bottom, EPS);
        }
    }

    @Test
    public void anEmptyRectangleStaysEmptyBothWays() {
        for (Edge edge : Edge.values()) {
            AzBarFrame frame = frame(edge);
            assertTrue(frame.toCanonical(Bounds.EMPTY).isEmpty());
            assertTrue(frame.toScreen(Bounds.EMPTY).isEmpty());
        }
    }

    @Test
    public void theBarSitsAtTheCanonicalBottomWhereverItStands() {
        // The bar's own rectangle, as the screen has it on each edge, and its away-side face: the
        // gesture's thresholds are fractions of the row measured from the track towards the bar,
        // so the bar has to come out of the transform lying along the bottom of the frame.
        Bounds[] screenBars = {
            new Bounds(0f, H - 80f, W, H),          // BOTTOM
            new Bounds(0f, 0f, W, 80f),             // TOP
            new Bounds(0f, 0f, 80f, H),             // LEFT
            new Bounds(W - 80f, 0f, W, H),          // RIGHT
        };
        Edge[] edges = {Edge.BOTTOM, Edge.TOP, Edge.LEFT, Edge.RIGHT};
        for (int i = 0; i < edges.length; i++) {
            AzBarFrame frame = frame(edges[i]);
            Bounds bar = frame.toCanonical(screenBars[i]);
            assertEquals(edges[i] + " spans the frame", frame.alongLengthPx(), bar.width(), EPS);
            assertEquals(edges[i] + " thickness", 80f, bar.height(), EPS);
            assertEquals(edges[i] + " rests on the bottom", frame.awayLengthPx(), bar.bottom, EPS);
        }
    }

    @Test
    public void theStripLandsBesideTheBarOnEveryEdge() {
        // The strip is laid out in the canonical frame from the bar's rectangle; where it ends up
        // on screen is the transform's business. 40px of icons, 12px clear of the letters.
        Bounds[] screenBars = {
            new Bounds(0f, H - 80f, W, H),
            new Bounds(0f, 0f, W, 80f),
            new Bounds(0f, 0f, 80f, H),
            new Bounds(W - 80f, 0f, W, H),
        };
        Edge[] edges = {Edge.BOTTOM, Edge.TOP, Edge.LEFT, Edge.RIGHT};
        for (int i = 0; i < edges.length; i++) {
            AzBarFrame frame = frame(edges[i]);
            Bounds bar = frame.toCanonical(screenBars[i]);
            Bounds strip = frame.toScreen(new Bounds(200f, bar.top - 12f - 40f, 600f, bar.top - 12f));
            switch (edges[i]) {
                case BOTTOM:
                    assertEquals(H - 80f - 12f, strip.bottom, EPS);
                    break;
                case TOP:
                    assertEquals(80f + 12f, strip.top, EPS);
                    break;
                case LEFT:
                    assertEquals(80f + 12f, strip.left, EPS);
                    break;
                case RIGHT:
                default:
                    assertEquals(W - 80f - 12f, strip.right, EPS);
                    break;
            }
        }
    }

    @Test
    public void pagingLeftAndRightBecomesUpAndDownOnASideBar() {
        // AzFloatingStripPolicy.edgeAt reads canonical x, so the "left" end of a strip is the end
        // a thumb reaches by sliding up a side bar's column and the "right" end by sliding down.
        Bounds canonicalStrip = new Bounds(400f, 1000f, 900f, 1110f);
        AzBarFrame left = frame(Edge.LEFT);
        Bounds onScreen = left.toScreen(canonicalStrip);
        // Canonical x is screen y for a column, so the low-x (paging-left) end is the top one.
        assertEquals(400f, onScreen.top, EPS);
        assertEquals(900f, onScreen.bottom, EPS);
        assertEquals(400f, left.canonicalX(onScreen.left, onScreen.top), EPS);
        assertEquals(900f, left.canonicalX(onScreen.left, onScreen.bottom), EPS);

        AzBarFrame right = frame(Edge.RIGHT);
        Bounds onRight = right.toScreen(canonicalStrip);
        assertEquals(400f, onRight.top, EPS);
        assertEquals(900f, onRight.bottom, EPS);

        // A horizontal bar keeps left and right where they are.
        AzBarFrame top = frame(Edge.TOP);
        Bounds onTop = top.toScreen(canonicalStrip);
        assertEquals(400f, onTop.left, EPS);
        assertEquals(900f, onTop.right, EPS);
    }

    @Test
    public void aFrameBuiltOnAViewsOwnBoxMapsLocalTouchesToo() {
        // The row view maps its own touch points with a frame built on its own width and height:
        // "along" is the letters' axis and "away" is measured from the face the strip is on.
        AzBarFrame column = AzBarFrame.of(Edge.LEFT, 100f, 900f);
        assertEquals(900f, column.alongLengthPx(), EPS);
        assertEquals(100f, column.awayLengthPx(), EPS);
        // Touching the inner (content-facing) face of a left column is the top of the row
        // canonically, which is where the gesture's upward lock lives.
        assertEquals(0f, column.canonicalY(100f, 450f), EPS);
        assertEquals(100f, column.canonicalY(0f, 450f), EPS);
        assertEquals(450f, column.canonicalX(100f, 450f), EPS);

        AzBarFrame topRow = AzBarFrame.of(Edge.TOP, 1080f, 30f);
        assertEquals(0f, topRow.canonicalY(0f, 30f), EPS);
        assertEquals(30f, topRow.canonicalY(0f, 0f), EPS);
    }
}
