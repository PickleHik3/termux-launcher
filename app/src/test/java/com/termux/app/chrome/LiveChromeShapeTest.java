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
        return new boolean[] {
            clip.topLeft > 0f && clip.reachLeft == 0 && clip.reachTop == 0,
            clip.topRight > 0f && clip.reachRight == 0 && clip.reachTop == 0,
            clip.bottomRight > 0f && clip.reachRight == 0 && clip.reachBottom == 0,
            clip.bottomLeft > 0f && clip.reachLeft == 0 && clip.reachBottom == 0};
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
    public void dockedOutlinesAreSlicesOfTheTwoEdgeCards() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);
        assertClipsEqualTheModel(shape);

        // The status bar is the whole top card: square at the top, Corners at the bottom, and
        // never the screen's radius.
        LiveChromeShape.Clip status = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.STATUS));
        assertEquals(CORNERS, status.radius, D);
        assertEquals(0f, status.topLeft, D);
        assertEquals(0f, status.topRight, D);
        assertEquals(CORNERS, status.bottomLeft, D);
        assertEquals(CORNERS, status.bottomRight, D);
        assertEquals("the card ends where the bar does", 0, status.reachBottom);
        boolean[] statusCut = cutCorners(status);
        assertTrue(statusCut[2] && statusCut[3]);
        assertFalse(statusCut[0] || statusCut[1]);

        // The apps row is the bottom card's top: its top corners cut, and the card runs on below.
        LiveChromeShape.Clip apps = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.APPS));
        boolean[] cut = cutCorners(apps);
        assertTrue(cut[0] && cut[1]);
        assertFalse(cut[2] || cut[3]);
        assertEquals(340 - 60, apps.reachBottom);
        // The keyboard is the card's flush bottom: no corner of it is cut.
        LiveChromeShape.Clip keyboard = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.KEYBOARD));
        for (boolean corner : cutCorners(keyboard)) assertFalse(corner);
        assertEquals(0, keyboard.reachBottom);
    }

    @Test
    public void dockedSideBarsAreSlicesOfTheSameFrame() {
        ChromeShape shape = shape(sideBars(), LayoutStyle.DOCKED);
        assertClipsEqualTheModel(shape);
        // One joined frame with square outer corners: no view's clip rounds anything.
        for (Piece piece : shape.pieces()) {
            LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape,
                Collections.singletonList(piece.id));
            assertEquals(piece.id.toString(), 0f, clip.radius, D);
        }
        // The side bars stand between the top and bottom bars and touch no screen corner.
        for (PieceId id : new PieceId[] {PieceId.APPS, PieceId.AZ}) {
            for (boolean corner : cutCorners(LiveChromeShape.outlineOf(shape,
                Collections.singletonList(id)))) assertFalse(id.toString(), corner);
        }
    }

    @Test
    public void dockedEdgeCardsRimTheEdgeFacingTheInsertAndNothingElse() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);
        assertEquals("the status bar's inner edge", ChromeEdgeRule.edgeBit(Edge.BOTTOM),
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.STATUS)));
        assertEquals("the dock rows' top, the bottom card's inner edge",
            ChromeEdgeRule.edgeBit(Edge.TOP),
            LiveChromeShape.rimEdges(shape, java.util.Arrays.asList(PieceId.APPS, PieceId.AZ,
                PieceId.EXTRA_KEYS)));
        assertEquals("a joined keyboard under the rows has none", ChromeEdgeRule.NONE,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.KEYBOARD)));
        assertEquals(ChromeEdgeRule.ALL & ~ChromeEdgeRule.edgeBit(Edge.BOTTOM),
            LiveChromeShape.seamEdges(shape, Collections.singletonList(PieceId.STATUS)));
        assertTrue(shape.opening().ownsRim);
        assertTrue(shape.panes().get(0).drawsRim);
    }

    @Test
    public void aJoinedDockedFrameDrawsNoRimBecauseTheInsertsOwnEdgeCarriesIt() {
        ChromeShape shape = shape(sideBars(), LayoutStyle.DOCKED);
        for (Piece piece : shape.pieces())
            assertEquals(piece.id.toString(), ChromeEdgeRule.NONE,
                LiveChromeShape.rimEdges(shape, Collections.singletonList(piece.id)));
        assertEquals(ChromeEdgeRule.ALL,
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
    public void dockedInsertKeepsAGutterOfMarginFromEveryBarAndScreenEdge() {
        for (PlaceLayout layout : new PlaceLayout[] {bottomStack(), sideBars()}) {
            ChromeShape shape = shape(layout, LayoutStyle.DOCKED);
            for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT})
                assertEquals(edge.toString(), MARGIN,
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
        assertEquals("the sheet is the whole bottom card", 0, clip.reachTop);
        assertEquals(CORNERS, clip.radius, D);
        boolean[] cut = cutCorners(clip);
        assertTrue("its top corners are the card's inner ones", cut[0] && cut[1]);
        assertFalse("the screen's bottom corners are the screen's to round", cut[2] || cut[3]);
        assertEquals("the dock's top faces the insert and wears the rim",
            ChromeEdgeRule.edgeBit(Edge.TOP), LiveChromeShape.rimEdges(shape, sheet));
        assertEquals(ChromeEdgeRule.ALL & ~ChromeEdgeRule.edgeBit(Edge.TOP),
            LiveChromeShape.seamEdges(shape, sheet));
    }

    @Test
    public void aStatusBarJoinedToTheRowUnderItRimsOnlyTheInnerRowsInnerEdge() {
        // Status and the apps row share the top card: whichever stands outer joins the other, so
        // its inner edge is a join; the inner one faces the insert and wears the rim.
        ChromeShape shape = shape(layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.TOP, Element.APPS), Slot.on(Edge.BOTTOM, Element.AZ),
            Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS)), LayoutStyle.DOCKED);
        Piece status = shape.piece(PieceId.STATUS);
        Piece apps = shape.piece(PieceId.APPS);
        Piece outer = status.box.top < apps.box.top ? status : apps;
        Piece inner = outer == status ? apps : status;
        assertEquals("the outer bar's every edge is a join or the screen", ChromeEdgeRule.NONE,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(outer.id)));
        assertEquals(ChromeEdgeRule.ALL,
            LiveChromeShape.seamEdges(shape, Collections.singletonList(outer.id)));
        assertEquals("the inner bar faces the insert", ChromeEdgeRule.edgeBit(Edge.BOTTOM),
            LiveChromeShape.rimEdges(shape, Collections.singletonList(inner.id)));
    }

    @Test
    public void everyJoinAndScreenEdgeIsAPlainSeamUnderDocked() {
        for (boolean joined : new boolean[] {false, true}) {
            ChromeShape shape = shape(joined ? sideBars() : bottomStack(), LayoutStyle.DOCKED);
            for (Piece piece : shape.pieces()) {
                int rim = LiveChromeShape.rimEdges(shape, Collections.singletonList(piece.id));
                for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT}) {
                    boolean carries = (rim & ChromeEdgeRule.edgeBit(edge)) != 0;
                    assertEquals(piece.id + " " + edge, piece.drawsRim(edge), carries);
                    // A rim is only ever where the model says RIM, which a joined frame never has.
                    if (joined) assertFalse(piece.id + " " + edge, carries);
                }
            }
        }
    }

    @Test
    public void theStatusBarSpansTheWholeWidthAgainAfterAFloatingRoundTrip() {
        // Floating is inset by Margin and clipped at Corners; Docked is the whole width again,
        // with nothing of the Floating card left in its clip.
        PlaceLayout layout = bottomStack();
        Piece floating = shape(layout, LayoutStyle.FLOATING).piece(PieceId.STATUS);
        assertEquals(MARGIN, floating.box.left, D);
        assertEquals(400f - MARGIN, floating.box.right, D);

        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        Piece status = docked.piece(PieceId.STATUS);
        assertEquals(0f, status.box.left, D);
        assertEquals(400f, status.box.right, D);
        LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(docked,
            Collections.singletonList(PieceId.STATUS));
        int[] rect = new int[4];
        ChromeShapeOutlineProvider.cardRect(clip, 400, 40, rect);
        assertEquals(0, rect[0]);
        assertEquals(400, rect[2]);
    }

    @Test
    public void everyChromeViewReachesTheFullWidthUnderDockedWhateverStyleCameBefore() {
        // The dock rows and the keyboard are the same slice of the frame: no side reach.
        for (PlaceLayout layout : new PlaceLayout[] {bottomStack(), sideBars()}) {
            shape(layout, LayoutStyle.FLOATING);
            ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
            for (PieceId id : new PieceId[] {PieceId.STATUS, PieceId.EXTRA_KEYS}) {
                LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(docked,
                    Collections.singletonList(id));
                assertNotNull(id.toString(), clip);
                assertEquals(id + " left", 0, clip.reachLeft);
                assertEquals(id + " right", 0, clip.reachRight);
            }
        }
    }
}
