package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;

/**
 * The live chrome's outlines and rim edges are the shape model's answers, for the layout pack's
 * three previews on a 400 x 800 frame: Floating, Docked, and Docked with the side bars.
 */
public class LiveChromeShapeTest {

    private static final float D = 0.01f;
    private static final float DENSITY = 2f;
    private static final float CORNERS = 12f;
    private static final float MARGIN = 8f;
    private static final float SCREEN = 28f;

    private static final ChromeShapeModel.Thickness THICKNESS =
        ChromeShapeModel.Thickness.of(40f, 50f, 60f, 70f, 30f, 24f, 50f, 45f, 200f);

    private static PlaceLayout layout(Slot status, Slot apps, Slot az, Slot keys) {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, status);
        slots.put(Element.APPS, apps);
        slots.put(Element.AZ, az);
        slots.put(Element.EXTRA_KEYS, keys);
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, true, 4, 6);
    }

    private static PlaceLayout bottomStack() {
        return layout(Slot.on(Edge.TOP, Element.STATUS), Slot.on(Edge.BOTTOM, Element.APPS),
            Slot.on(Edge.BOTTOM, Element.AZ), Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
    }

    private static PlaceLayout sideBars() {
        return layout(Slot.on(Edge.TOP, Element.STATUS), Slot.on(Edge.LEFT, Element.APPS),
            Slot.on(Edge.RIGHT, Element.AZ), Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
    }

    private static ChromeShape shape(PlaceLayout layout, LayoutStyle style) {
        return LiveChromeShape.of(layout, style, 400, 800, THICKNESS, CORNERS, MARGIN, SCREEN,
            DENSITY, 1, ChromeShapeModel.SplitAxis.SIDE_BY_SIDE, 0f, null);
    }

    /**
     * Which corners of a view standing where the piece stands are cut by its clip: a corner is cut
     * where the clip's card reaches no further than the view on both sides meeting at it.
     */
    private static boolean[] cutCorners(LiveChromeShape.Clip clip) {
        boolean round = clip.radius > 0f;
        return new boolean[] {
            round && clip.reachLeft == 0 && clip.reachTop == 0,
            round && clip.reachRight == 0 && clip.reachTop == 0,
            round && clip.reachRight == 0 && clip.reachBottom == 0,
            round && clip.reachLeft == 0 && clip.reachBottom == 0};
    }

    /** Every piece's live clip cuts exactly the corners, at exactly the radius, the model gives it. */
    private static void assertClipsEqualTheModel(ChromeShape shape) {
        for (Piece piece : shape.pieces()) {
            LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape,
                Collections.singletonList(piece.id));
            assertNotNull(piece.id.toString(), clip);
            boolean[] cut = cutCorners(clip);
            assertEquals(piece + " top left", piece.corners.topLeft > 0f, cut[0]);
            assertEquals(piece + " top right", piece.corners.topRight > 0f, cut[1]);
            assertEquals(piece + " bottom right", piece.corners.bottomRight > 0f, cut[2]);
            assertEquals(piece + " bottom left", piece.corners.bottomLeft > 0f, cut[3]);
            float modelRadius = Math.max(Math.max(piece.corners.topLeft, piece.corners.topRight),
                Math.max(piece.corners.bottomRight, piece.corners.bottomLeft));
            if (modelRadius > 0f) assertEquals(piece.toString(), modelRadius, clip.radius, D);
        }
    }

    @Test
    public void floatingOutlinesAreTheModelsCards() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.FLOATING);
        assertClipsEqualTheModel(shape);

        // The status bar is a card of its own at Corners; the dock's rows share one card, so the
        // apps row's clip reaches down past its own bottom edge to the card's.
        LiveChromeShape.Clip status = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.STATUS));
        assertEquals(CORNERS, status.radius, D);
        assertEquals(0, status.reachBottom);
        LiveChromeShape.Clip apps = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.APPS));
        assertEquals(CORNERS, apps.radius, D);
        assertEquals(0, apps.reachTop);
        assertEquals("the card runs on over the A-Z row and the extra keys: 30 + 50", 80,
            apps.reachBottom);
    }

    @Test
    public void dockedOutlinesAreSlicesOfTheOneFrame() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);
        assertClipsEqualTheModel(shape);

        LiveChromeShape.Clip status = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.STATUS));
        assertEquals("the screen radius, not Corners", SCREEN, status.radius, D);
        assertEquals("the frame goes on below the bar, to the screen's bottom", 760,
            status.reachBottom);
        // Joined rows are square: their clip's corners all lie past the frame's.
        LiveChromeShape.Clip apps = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.APPS));
        boolean[] cut = cutCorners(apps);
        for (boolean corner : cut) assertFalse(corner);
        LiveChromeShape.Clip keyboard = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.KEYBOARD));
        boolean[] keyboardCut = cutCorners(keyboard);
        assertTrue(keyboardCut[2] && keyboardCut[3]);
        assertFalse(keyboardCut[0] || keyboardCut[1]);
    }

    @Test
    public void dockedSideBarsAreSlicesOfTheSameFrame() {
        ChromeShape shape = shape(sideBars(), LayoutStyle.DOCKED);
        assertClipsEqualTheModel(shape);
        // The side bars stand between the top and bottom bars and touch no screen corner.
        for (PieceId id : new PieceId[] {PieceId.APPS, PieceId.AZ}) {
            for (boolean corner : cutCorners(LiveChromeShape.outlineOf(shape,
                Collections.singletonList(id)))) assertFalse(id.toString(), corner);
        }
    }

    @Test
    public void dockedRimIsOnlyTheEdgeFacingTheOpening() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);
        assertEquals("the status bar's inner edge", ChromeEdgeRule.BOTTOM,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.STATUS)));
        assertEquals("the dock rows' top, which is the outermost piece's", ChromeEdgeRule.TOP,
            LiveChromeShape.rimEdges(shape, java.util.Arrays.asList(PieceId.APPS, PieceId.AZ,
                PieceId.EXTRA_KEYS)));
        assertEquals("a joined keyboard under the rows has none", ChromeEdgeRule.NONE,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.KEYBOARD)));
        assertEquals(ChromeEdgeRule.ALL & ~ChromeEdgeRule.BOTTOM,
            LiveChromeShape.seamEdges(shape, Collections.singletonList(PieceId.STATUS)));
    }

    @Test
    public void floatingRimIsEveryEdgeOfACardAndNoJoin() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.FLOATING);
        assertEquals(ChromeEdgeRule.ALL,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.STATUS)));
        // Over the rows' shared card the union's four edges are the card's, all rim.
        assertEquals(ChromeEdgeRule.ALL, LiveChromeShape.rimEdges(shape,
            java.util.Arrays.asList(PieceId.APPS, PieceId.AZ, PieceId.EXTRA_KEYS)));
    }

    @Test
    public void aHiddenPieceHasNoClip() {
        ChromeShape shape = shape(layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.hiddenFrom(Edge.BOTTOM, 0), Slot.hiddenFrom(Edge.BOTTOM, 1),
            Slot.hiddenFrom(Edge.BOTTOM, 2)), LayoutStyle.DOCKED);
        assertNull(LiveChromeShape.outlineOf(shape, Collections.singletonList(PieceId.APPS)));
    }

    @Test
    public void theScreenRadiusFallsBackToTwentyEightDp() {
        assertEquals(28f * 3f, LiveChromeShape.screenRadiusPx(0f, 3f), D);
        assertEquals(40f, LiveChromeShape.screenRadiusPx(40f, 3f), D);
    }

    @Test
    public void dockedOpeningKeepsNoAirFromAnythingAroundIt() {
        for (PlaceLayout layout : new PlaceLayout[] {bottomStack(), sideBars()}) {
            ChromeShape shape = shape(layout, LayoutStyle.DOCKED);
            for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT})
                assertEquals(edge.toString(), 0f,
                    LiveChromeShape.openingInsetPx(shape, edge, 400, 800), D);
        }
    }

    @Test
    public void floatingOpeningKeepsMarginFromEveryBarAndScreenEdge() {
        for (PlaceLayout layout : new PlaceLayout[] {bottomStack(), sideBars()}) {
            ChromeShape shape = shape(layout, LayoutStyle.FLOATING);
            for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT})
                assertEquals(edge.toString(), MARGIN,
                    LiveChromeShape.openingInsetPx(shape, edge, 400, 800), D);
        }
    }

    @Test
    public void theDockAndKeyboardOnOneGlassAreClippedAndRimmedAsOneSheet() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);
        java.util.List<PieceId> sheet = java.util.Arrays.asList(PieceId.APPS, PieceId.AZ,
            PieceId.EXTRA_KEYS, PieceId.KEYBOARD);
        LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape, sheet);
        assertEquals("the sheet reaches the screen's bottom, where the keyboard ends", 0,
            clip.reachBottom);
        assertEquals(SCREEN, clip.radius, D);
        boolean[] cut = cutCorners(clip);
        assertTrue("the screen's bottom corners are the keyboard's", cut[2] && cut[3]);
        assertFalse("its top corners are joins", cut[0] || cut[1]);
        assertEquals("only the dock's top faces the opening", ChromeEdgeRule.TOP,
            LiveChromeShape.rimEdges(shape, sheet));
        assertEquals(ChromeEdgeRule.ALL & ~ChromeEdgeRule.TOP,
            LiveChromeShape.seamEdges(shape, sheet));
    }

    @Test
    public void everyJoinAndScreenEdgeIsAPlainSeamUnderDocked() {
        for (PlaceLayout layout : new PlaceLayout[] {bottomStack(), sideBars()}) {
            ChromeShape shape = shape(layout, LayoutStyle.DOCKED);
            for (Piece piece : shape.pieces()) {
                int rim = LiveChromeShape.rimEdges(shape, Collections.singletonList(piece.id));
                for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT}) {
                    boolean carries = (rim & ChromeEdgeRule.edgeBit(edge)) != 0;
                    assertEquals(piece.id + " " + edge, piece.drawsRim(edge), carries);
                    if (carries) assertEquals(piece.id + " " + edge,
                        ChromeShape.EdgeKind.OPENING, piece.kind(edge));
                }
            }
        }
    }
}
