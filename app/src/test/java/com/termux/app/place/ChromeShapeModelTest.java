package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.ChromeShapeModel.SplitAxis;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.EnumMap;

/**
 * The chrome shape model on a 400 x 800 frame: the pack's three previews, then each rule of SPEC
 * 3.7 on its own. Corners 12, Margin 8, screen radius 28, a 40-high status bar, a 60-high apps
 * row, a 30-high A-Z row, 50-high extra keys and a 200-high keyboard, so every rect below can be
 * added up by hand.
 */
public class ChromeShapeModelTest {

    private static final float D = 0.01f;
    private static final float W = 400f;
    private static final float H = 800f;
    private static final float CORNERS = 12f;
    private static final float MARGIN = 8f;
    private static final float SCREEN = 28f;

    private static final ChromeShapeModel.Thickness THICKNESS =
        ChromeShapeModel.Thickness.of(40f, 50f, 60f, 70f, 30f, 24f, 50f, 45f, 200f);

    private static final Slot GONE = Slot.hiddenFrom(Edge.BOTTOM, 0);

    // ---------------------------------------------------------------- fixtures

    private static PlaceLayout layout(Slot status, Slot apps, Slot az, Slot keys,
                                      KeyboardForm form, boolean keyboardShown) {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, status);
        slots.put(Element.APPS, apps);
        slots.put(Element.AZ, az);
        slots.put(Element.EXTRA_KEYS, keys);
        return new PlaceLayout(slots, KeyboardMode.RESIZE, form, keyboardShown, 4, 6);
    }

    private static ChromeShapeModel.Input.Builder input(PlaceLayout layout, LayoutStyle style) {
        return ChromeShapeModel.input(layout, style, W, H, THICKNESS)
            .corners(CORNERS).margin(MARGIN).screenRadius(SCREEN).paneGapCap(24f);
    }

    private static ChromeShape shape(PlaceLayout layout, LayoutStyle style) {
        return ChromeShapeModel.shape(input(layout, style).build());
    }

    /** Status on top; extra keys, A-Z and apps over a docked keyboard: the pack's bottom stack. */
    private static PlaceLayout bottomStack() {
        return layout(Slot.on(Edge.TOP, Element.STATUS), Slot.on(Edge.BOTTOM, Element.APPS),
            Slot.on(Edge.BOTTOM, Element.AZ), Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS),
            KeyboardForm.DOCKED, true);
    }

    /** The keyboard alone, in one form. */
    private static PlaceLayout keyboardOnly(KeyboardForm form, boolean shown) {
        return layout(GONE, GONE, GONE, GONE, form, shown);
    }

    private static void assertBox(Box box, float l, float t, float r, float b) {
        assertEquals("left " + box, l, box.left, D);
        assertEquals("top " + box, t, box.top, D);
        assertEquals("right " + box, r, box.right, D);
        assertEquals("bottom " + box, b, box.bottom, D);
    }

    private static void assertCorners(Piece piece, float tl, float tr, float br, float bl) {
        assertEquals(piece + " top left", tl, piece.corners.topLeft, D);
        assertEquals(piece + " top right", tr, piece.corners.topRight, D);
        assertEquals(piece + " bottom right", br, piece.corners.bottomRight, D);
        assertEquals(piece + " bottom left", bl, piece.corners.bottomLeft, D);
    }

    private static Piece piece(ChromeShape shape, PieceId id) {
        Piece piece = shape.piece(id);
        assertNotNull(id.toString(), piece);
        return piece;
    }

    // ---------------------------------------------------------------- the pack's previews

    @Test
    public void floatingBottomStackIsFourCards() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.FLOATING);

        Piece status = piece(shape, PieceId.STATUS);
        Piece keyboard = piece(shape, PieceId.KEYBOARD);
        Piece keys = piece(shape, PieceId.EXTRA_KEYS);
        Piece az = piece(shape, PieceId.AZ);
        Piece apps = piece(shape, PieceId.APPS);

        // The status bar and the keyboard are cards alone; the dock's three rows share one.
        assertBox(status.box, 8, 8, 392, 48);
        assertBox(keyboard.box, 8, 592, 392, 792);
        assertBox(keys.box, 8, 534, 392, 584);
        assertBox(az.box, 8, 504, 392, 534);
        assertBox(apps.box, 8, 444, 392, 504);
        assertEquals(keys.groupId, az.groupId);
        assertEquals(az.groupId, apps.groupId);
        assertNotEquals(status.groupId, apps.groupId);
        assertNotEquals(keyboard.groupId, apps.groupId);
        assertEquals(3, shape.cards().size());
        assertBox(shape.cardOf(apps).box, 8, 444, 392, 584);

        // Margin is the air between and around the cards: the pane card fills what is left.
        assertBox(shape.opening().box, 8, 56, 392, 436);
        assertEquals(1, shape.panes().size());
        assertBox(shape.panes().get(0).box, 8, 56, 392, 436);
        assertTrue(shape.panes().get(0).drawsRim);
        assertTrue(shape.opening().ownsRim);
        assertTrue(shape.dividers().isEmpty());

        // Corners round every card, and only the card's own outer corners.
        assertCorners(status, 12, 12, 12, 12);
        assertCorners(keyboard, 12, 12, 12, 12);
        assertCorners(keys, 0, 0, 12, 12);
        assertCorners(az, 0, 0, 0, 0);
        assertCorners(apps, 12, 12, 0, 0);
        assertEquals(12, shape.panes().get(0).corners.topLeft, D);

        // Rim on the card's boundary; the seam between joined rows draws nothing.
        assertEquals(EdgeKind.RIM, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.JOIN, az.kind(Edge.TOP));
        assertEquals(EdgeKind.RIM, keys.kind(Edge.BOTTOM));
        assertTrue(keys.drawsRim(Edge.LEFT));
        assertFalse(keys.drawsRim(Edge.TOP));
    }

    @Test
    public void dockedBottomBarsAreOneFlushFrame() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.DOCKED);

        Piece status = piece(shape, PieceId.STATUS);
        Piece keyboard = piece(shape, PieceId.KEYBOARD);
        Piece keys = piece(shape, PieceId.EXTRA_KEYS);
        Piece az = piece(shape, PieceId.AZ);
        Piece apps = piece(shape, PieceId.APPS);

        // No air anywhere: the bars run the full width and touch.
        assertBox(status.box, 0, 0, 400, 40);
        assertBox(keyboard.box, 0, 600, 400, 800);
        assertBox(keys.box, 0, 550, 400, 600);
        assertBox(az.box, 0, 520, 400, 550);
        assertBox(apps.box, 0, 460, 400, 520);
        // The insert stands a gutter of Margin inside the bars.
        assertBox(shape.opening().box, 8, 48, 392, 452);

        // One frame, clipped once, with the opening cut out of it.
        assertEquals(1, shape.cards().size());
        ChromeShape.Card frame = shape.cards().get(0);
        assertTrue(frame.frame);
        assertBox(frame.box, 0, 0, 400, 800);
        assertBox(frame.hole, 8, 48, 392, 452);
        assertEquals(1, frame.holes.size());
        assertEquals(CORNERS, frame.holeCorners.topLeft, D);
        assertEquals(CORNERS, frame.holeCorners.bottomRight, D);
        assertEquals(SCREEN, frame.corners.topLeft, D);
        for (Piece piece : shape.pieces()) assertEquals(frame.id, piece.groupId);

        // Only the frame's exposed outer corners round, at the screen radius.
        assertCorners(status, 28, 28, 0, 0);
        assertCorners(keyboard, 0, 0, 28, 28);
        assertCorners(keys, 0, 0, 0, 0);
        assertCorners(apps, 0, 0, 0, 0);

        // Joins, the screen and the bars' edges facing the gutter are all plain.
        assertEquals(EdgeKind.SCREEN, status.kind(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, status.kind(Edge.LEFT));
        assertEquals(EdgeKind.GUTTER, status.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.GUTTER, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.JOIN, keyboard.kind(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, keyboard.kind(Edge.BOTTOM));
        assertFalse(status.drawsRim(Edge.BOTTOM));
        assertFalse(apps.drawsRim(Edge.TOP));
        assertFalse(apps.drawsRim(Edge.BOTTOM));
        assertFalse(status.drawsRim(Edge.TOP));
        assertFalse(keyboard.drawsRim(Edge.TOP));

        // The pane is a rounded insert: its own rounded edge carries the rim.
        assertTrue(shape.opening().ownsRim);
        assertTrue(shape.panes().get(0).drawsRim);
        assertBox(shape.panes().get(0).box, 8, 48, 392, 452);
        assertEquals(CORNERS, shape.panes().get(0).corners.topLeft, D);
        assertEquals(CORNERS, shape.panes().get(0).corners.bottomLeft, D);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, shape.opening().kind(edge));
        assertTrue(shape.dividers().isEmpty());
    }

    /** O2: with side bars the insert is the area between all four bars, a gutter inside them. */
    @Test
    public void theInsertIsTheAreaBetweenTheBarsInsetByMarginWithAndWithoutSideBars() {
        PlaceLayout sides = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.LEFT, Element.APPS), Slot.on(Edge.RIGHT, Element.AZ),
            Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS), KeyboardForm.DOCKED, true);
        ChromeShape with = shape(sides, LayoutStyle.DOCKED);
        // Bars: top 0-40, bottom 550-600 (keyboard under it), left 0-70, right 376-400.
        assertBox(with.opening().box, 78, 48, 368, 542);
        assertBox(with.cards().get(0).hole, 78, 48, 368, 542);
        assertEquals(CORNERS, with.opening().corners.topLeft, D);
        assertEquals(CORNERS, with.panes().get(0).corners.bottomRight, D);
        assertBox(with.panes().get(0).box, 78, 48, 368, 542);
        // The side bars' edges facing the gutter are plain; so are the bars' joins.
        assertEquals(EdgeKind.GUTTER, piece(with, PieceId.APPS).kind(Edge.RIGHT));
        assertFalse(piece(with, PieceId.APPS).drawsRim(Edge.RIGHT));
        assertEquals(EdgeKind.GUTTER, piece(with, PieceId.AZ).kind(Edge.LEFT));

        PlaceLayout bars = layout(Slot.on(Edge.TOP, Element.STATUS), GONE, GONE,
            Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS), KeyboardForm.DOCKED, false);
        ChromeShape without = shape(bars, LayoutStyle.DOCKED);
        // Without side bars the gutter is also Margin from the screen's own left and right edge.
        assertBox(without.opening().box, 8, 48, 392, 742);
        assertBox(without.cards().get(0).hole, 8, 48, 392, 742);
        assertEquals(CORNERS, without.cards().get(0).holeCorners.topLeft, D);

        // The gutter follows Margin; Margin 0 leaves the insert filling the opening.
        ChromeShape flush = ChromeShapeModel.shape(input(bars, LayoutStyle.DOCKED).margin(0f)
            .build());
        assertBox(flush.opening().box, 0, 40, 400, 750);
        assertEquals(CORNERS, flush.opening().corners.topLeft, D);
    }

    /** F3: with every bar put away the frame stays, as a gutter of glass around the insert. */
    @Test
    public void aDockedLayoutWithTheBarsPutAwayStillHasAFrameAroundTheInsert() {
        ChromeShape shape = shape(keyboardOnly(KeyboardForm.DOCKED, false), LayoutStyle.DOCKED);
        assertTrue(shape.pieces().isEmpty());
        assertEquals(1, shape.cards().size());
        ChromeShape.Card frame = shape.cards().get(0);
        assertTrue(frame.frame);
        assertBox(frame.box, 0, 0, 400, 800);
        assertBox(frame.hole, 8, 8, 392, 792);
        assertEquals(CORNERS, frame.holeCorners.topLeft, D);
        assertEquals(SCREEN, frame.corners.topLeft, D);
        assertBox(shape.panes().get(0).box, 8, 8, 392, 792);

        // The docked keyboard is that frame's bottom, and the pane is still the insert above it.
        ChromeShape withKeyboard = shape(keyboardOnly(KeyboardForm.DOCKED, true),
            LayoutStyle.DOCKED);
        assertBox(piece(withKeyboard, PieceId.KEYBOARD).box, 0, 600, 400, 800);
        assertBox(withKeyboard.cards().get(0).hole, 8, 8, 392, 592);
        assertBox(withKeyboard.panes().get(0).box, 8, 8, 392, 592);

        // Floating has no frame to keep.
        assertTrue(shape(keyboardOnly(KeyboardForm.DOCKED, false), LayoutStyle.FLOATING)
            .cards().isEmpty());
    }

    /** F3 as the launcher reaches it: minimal mode puts every bar away, status bar included. */
    @Test
    public void minimalModeUnderDockedKeepsAThinFrameWithTheKeyboardAsItsBottom() {
        PlaceLayout minimal = MinimalMode.apply(bottomStack());
        ChromeShape docked = shape(minimal, LayoutStyle.DOCKED);
        assertEquals("only the keyboard stands", 1, docked.pieces().size());
        assertBox(piece(docked, PieceId.KEYBOARD).box, 0, 600, 400, 800);
        ChromeShape.Card frame = docked.cards().get(0);
        assertTrue(frame.frame);
        assertEquals(1, docked.cards().size());
        // A gutter of Margin on the top and both sides, the keyboard below, and the rounded insert.
        assertBox(frame.hole, 8, 8, 392, 592);
        assertEquals(CORNERS, docked.panes().get(0).corners.topLeft, D);
        assertEquals(CORNERS, docked.panes().get(0).corners.bottomRight, D);

        // Floating keeps its cards and no frame.
        assertTrue(shape(MinimalMode.apply(bottomStack()).withKeyboardShown(false),
            LayoutStyle.FLOATING).cards().isEmpty());
    }

    @Test
    public void dockedSideBarsStandBetweenTheTopAndBottomBars() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.LEFT, Element.APPS), Slot.on(Edge.RIGHT, Element.AZ),
            Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS), KeyboardForm.DOCKED, true);
        ChromeShape shape = shape(layout, LayoutStyle.DOCKED);

        Piece status = piece(shape, PieceId.STATUS);
        Piece apps = piece(shape, PieceId.APPS);
        Piece az = piece(shape, PieceId.AZ);
        Piece keys = piece(shape, PieceId.EXTRA_KEYS);

        assertBox(status.box, 0, 0, 400, 40);
        assertBox(keys.box, 0, 550, 400, 600);
        assertBox(apps.box, 0, 40, 70, 550);
        assertBox(az.box, 376, 40, 400, 550);
        assertBox(shape.opening().box, 78, 48, 368, 542);

        // The side bars touch the bars above and below: no corner of theirs is a screen corner.
        assertCorners(apps, 0, 0, 0, 0);
        assertCorners(az, 0, 0, 0, 0);
        assertEquals(EdgeKind.SCREEN, apps.kind(Edge.LEFT));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.GUTTER, apps.kind(Edge.RIGHT));
        assertEquals(EdgeKind.GUTTER, az.kind(Edge.LEFT));

        // The status bar's inner edge runs over the left bar, the gap between the bars and the
        // right bar: joins and a plain edge facing the gutter.
        assertEquals(3, status.runs(Edge.BOTTOM).size());
        assertEquals(EdgeKind.JOIN, status.runs(Edge.BOTTOM).get(0).kind);
        assertEquals(EdgeKind.GUTTER, status.runs(Edge.BOTTOM).get(1).kind);
        assertEquals(70, status.runs(Edge.BOTTOM).get(1).from, D);
        assertEquals(376, status.runs(Edge.BOTTOM).get(1).to, D);
        assertEquals(EdgeKind.JOIN, status.runs(Edge.BOTTOM).get(2).kind);
        assertEquals(EdgeKind.GUTTER, status.kind(Edge.BOTTOM));
        assertFalse(status.drawsRim(Edge.BOTTOM));

        // The insert's own rounded edge is what carries the rim.
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, shape.opening().kind(edge));
    }

    // ---------------------------------------------------------------- grouping

    @Test
    public void anAppsRowOnTheTopEdgeBesideTheStatusBar() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.TOP, Element.APPS), GONE, GONE, KeyboardForm.DOCKED, false);

        ChromeShape floating = shape(layout, LayoutStyle.FLOATING);
        Piece status = piece(floating, PieceId.STATUS);
        Piece apps = piece(floating, PieceId.APPS);
        // The status bar is always its own card, and an element alone on an edge is its own.
        assertNotEquals(status.groupId, apps.groupId);
        assertBox(status.box, 8, 8, 392, 48);
        assertBox(apps.box, 8, 56, 392, 116);
        assertCorners(apps, 12, 12, 12, 12);
        assertEquals(EdgeKind.RIM, status.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.RIM, apps.kind(Edge.TOP));

        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        status = piece(docked, PieceId.STATUS);
        apps = piece(docked, PieceId.APPS);
        assertEquals(status.groupId, apps.groupId);
        assertBox(apps.box, 0, 40, 400, 100);
        assertEquals(EdgeKind.JOIN, status.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.GUTTER, apps.kind(Edge.BOTTOM));
        assertCorners(status, 28, 28, 0, 0);
        assertCorners(apps, 0, 0, 0, 0);
    }

    @Test
    public void neighbouringRowsOnTheTopEdgeShareACardAndTheStatusBarKeepsItsOwn() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.TOP, Element.APPS), Slot.on(Edge.TOP, Element.AZ), GONE,
            KeyboardForm.DOCKED, false);
        ChromeShape shape = shape(layout, LayoutStyle.FLOATING);

        Piece status = piece(shape, PieceId.STATUS);
        Piece az = piece(shape, PieceId.AZ);
        Piece apps = piece(shape, PieceId.APPS);
        assertEquals(az.groupId, apps.groupId);
        assertNotEquals(status.groupId, az.groupId);
        assertBox(az.box, 8, 56, 392, 86);
        assertBox(apps.box, 8, 86, 392, 146);
        assertCorners(az, 12, 12, 0, 0);
        assertCorners(apps, 0, 0, 12, 12);
    }

    @Test
    public void aLoneAzOnASideEdgeIsItsOwnFloatingCard() {
        PlaceLayout layout = layout(GONE, GONE, Slot.on(Edge.LEFT, Element.AZ), GONE,
            KeyboardForm.DOCKED, false);
        ChromeShape shape = shape(layout, LayoutStyle.FLOATING);

        Piece az = piece(shape, PieceId.AZ);
        assertBox(az.box, 8, 8, 32, 792);
        assertCorners(az, 12, 12, 12, 12);
        assertEquals(1, shape.cards().size());
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, az.kind(edge));
        assertBox(shape.opening().box, 40, 8, 392, 792);

        // Docked, the same column is the frame's left side from corner to corner.
        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        az = piece(docked, PieceId.AZ);
        assertBox(az.box, 0, 0, 24, 800);
        assertCorners(az, 28, 0, 0, 28);
        assertEquals(EdgeKind.GUTTER, az.kind(Edge.RIGHT));
        assertEquals(EdgeKind.SCREEN, az.kind(Edge.LEFT));
        assertEquals(EdgeKind.SCREEN, az.kind(Edge.TOP));
    }

    @Test
    public void aDockedFrameWithNothingInItIsAGutterAroundTheInsert() {
        ChromeShape shape = shape(keyboardOnly(KeyboardForm.DOCKED, false), LayoutStyle.DOCKED);
        assertTrue(shape.pieces().isEmpty());
        assertEquals(1, shape.cards().size());
        assertBox(shape.opening().box, 8, 8, 392, 792);
        assertEquals(CORNERS, shape.opening().corners.topLeft, D);
        assertEquals(EdgeKind.RIM, shape.opening().kind(Edge.TOP));
    }

    // ---------------------------------------------------------------- keyboard form by Style

    @Test
    public void dockedKeyboardJoinsTheFrameUnderDockedAndIsACardUnderFloating() {
        PlaceLayout layout = keyboardOnly(KeyboardForm.DOCKED, true);

        Piece docked = piece(shape(layout, LayoutStyle.DOCKED), PieceId.KEYBOARD);
        assertBox(docked.box, 0, 600, 400, 800);
        assertCorners(docked, 0, 0, 28, 28);
        assertEquals(EdgeKind.GUTTER, docked.kind(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, docked.kind(Edge.BOTTOM));
        assertFalse(docked.overlay);

        ChromeShape floatingShape = shape(layout, LayoutStyle.FLOATING);
        Piece floating = piece(floatingShape, PieceId.KEYBOARD);
        assertBox(floating.box, 8, 592, 392, 792);
        assertCorners(floating, 12, 12, 12, 12);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, floating.kind(edge));
        assertFalse(floating.overlay);
        assertBox(floatingShape.opening().box, 8, 8, 392, 584);
    }

    @Test
    public void floatingKeyboardIsARoundedCardOverTheContentUnderEitherStyle() {
        PlaceLayout layout = keyboardOnly(KeyboardForm.FLOATING, true);

        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        Piece kb = piece(docked, PieceId.KEYBOARD);
        assertTrue(kb.overlay);
        assertBox(kb.box, 20, 600, 380, 800);
        assertCorners(kb, 12, 12, 12, 12);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, kb.kind(edge));
        // It takes no band: the insert still fills the frame, a gutter in. The frame is the first
        // card and the keyboard a card of its own over it.
        assertBox(docked.opening().box, 8, 8, 392, 792);
        assertEquals(2, docked.cards().size());
        assertTrue(docked.cards().get(0).frame);
        assertFalse(docked.cards().get(1).frame);

        ChromeShape floating = shape(layout, LayoutStyle.FLOATING);
        kb = piece(floating, PieceId.KEYBOARD);
        assertTrue(kb.overlay);
        // Nine tenths of the 384-wide opening, centred, resting a Margin above its bottom.
        assertBox(kb.box, 27.2f, 584, 372.8f, 784);
        assertCorners(kb, 12, 12, 12, 12);
        assertBox(floating.opening().box, 8, 8, 392, 792);
    }

    @Test
    public void aFloatingKeyboardTheUserMovedKeepsItsRect() {
        Box moved = new Box(40, 300, 300, 500);
        ChromeShape shape = ChromeShapeModel.shape(
            input(keyboardOnly(KeyboardForm.FLOATING, true), LayoutStyle.FLOATING)
                .floatingKeyboardBox(moved).build());
        assertEquals(moved, piece(shape, PieceId.KEYBOARD).box);
    }

    @Test
    public void splitKeyboardIsTwoHalvesJoinedToTheirEdgesUnderDocked() {
        ChromeShape shape = shape(keyboardOnly(KeyboardForm.SPLIT, true), LayoutStyle.DOCKED);

        assertNull(shape.piece(PieceId.KEYBOARD));
        Piece left = piece(shape, PieceId.KEYBOARD_LEFT);
        Piece right = piece(shape, PieceId.KEYBOARD_RIGHT);
        assertBox(left.box, 0, 600, 160, 800);
        assertBox(right.box, 240, 600, 400, 800);
        assertEquals(left.groupId, right.groupId);
        // Each half rounds the screen corner it stands in; the corners on the gap stay square.
        assertCorners(left, 0, 0, 0, 28);
        assertCorners(right, 0, 0, 28, 0);
        assertEquals(EdgeKind.SCREEN, left.kind(Edge.LEFT));
        assertEquals(EdgeKind.SCREEN, left.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.GUTTER, left.kind(Edge.RIGHT));
        assertEquals(EdgeKind.GUTTER, left.kind(Edge.TOP));
        assertBox(shape.opening().box, 8, 8, 392, 592);
    }

    @Test
    public void splitKeyboardIsTwoCardsUnderFloating() {
        ChromeShape shape = shape(keyboardOnly(KeyboardForm.SPLIT, true), LayoutStyle.FLOATING);

        Piece left = piece(shape, PieceId.KEYBOARD_LEFT);
        Piece right = piece(shape, PieceId.KEYBOARD_RIGHT);
        assertBox(left.box, 8, 592, 168, 792);
        assertBox(right.box, 232, 592, 392, 792);
        assertNotEquals(left.groupId, right.groupId);
        assertCorners(left, 12, 12, 12, 12);
        assertCorners(right, 12, 12, 12, 12);
        assertEquals(2, shape.cards().size());
        assertBox(shape.opening().box, 8, 8, 392, 584);
    }

    @Test
    public void aHiddenKeyboardTakesNoBandAndTheRowsAroundItReadAsOneStack() {
        PlaceLayout layout = layout(GONE, Slot.on(Edge.BOTTOM, Element.APPS), GONE,
            new Slot(false, Edge.BOTTOM, 0, true), KeyboardForm.DOCKED, false);

        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        assertNull(docked.piece(PieceId.KEYBOARD));
        Piece keys = piece(docked, PieceId.EXTRA_KEYS);
        Piece apps = piece(docked, PieceId.APPS);
        assertBox(keys.box, 0, 750, 400, 800);
        assertBox(apps.box, 0, 690, 400, 750);
        assertEquals(EdgeKind.JOIN, keys.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertBox(docked.opening().box, 8, 8, 392, 682);

        // Nothing stands between the two groups, so they are neighbours and share a card.
        ChromeShape floating = shape(layout, LayoutStyle.FLOATING);
        assertEquals(piece(floating, PieceId.EXTRA_KEYS).groupId,
            piece(floating, PieceId.APPS).groupId);
        assertEquals(1, floating.cards().size());
    }

    @Test
    public void rowsUnderTheKeyboardJoinFlushUnderDockedAndFormACardUnderFloating() {
        PlaceLayout layout = layout(GONE, Slot.on(Edge.BOTTOM, Element.APPS), GONE,
            new Slot(false, Edge.BOTTOM, 0, true), KeyboardForm.DOCKED, true);

        ChromeShape docked = shape(layout, LayoutStyle.DOCKED);
        Piece keys = piece(docked, PieceId.EXTRA_KEYS);
        Piece keyboard = piece(docked, PieceId.KEYBOARD);
        Piece apps = piece(docked, PieceId.APPS);
        assertBox(keys.box, 0, 750, 400, 800);
        assertBox(keyboard.box, 0, 550, 400, 750);
        assertBox(apps.box, 0, 490, 400, 550);
        assertEquals(EdgeKind.JOIN, keys.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, keyboard.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, keyboard.kind(Edge.BOTTOM));
        assertCorners(keys, 0, 0, 28, 28);
        assertCorners(keyboard, 0, 0, 0, 0);
        assertEquals(1, docked.cards().size());

        ChromeShape floating = shape(layout, LayoutStyle.FLOATING);
        keys = piece(floating, PieceId.EXTRA_KEYS);
        keyboard = piece(floating, PieceId.KEYBOARD);
        apps = piece(floating, PieceId.APPS);
        assertBox(keys.box, 8, 742, 392, 792);
        assertBox(keyboard.box, 8, 534, 392, 734);
        assertBox(apps.box, 8, 466, 392, 526);
        assertCorners(keys, 12, 12, 12, 12);
        assertEquals(3, floating.cards().size());
        assertNotEquals(keys.groupId, keyboard.groupId);
        assertNotEquals(keyboard.groupId, apps.groupId);
    }

    // ---------------------------------------------------------------- split panes

    @Test
    public void aTwoPaneSplitIsTwoInsertsWithAGutterBetweenUnderDocked() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS), GONE, GONE, GONE,
            KeyboardForm.DOCKED, false);
        ChromeShape shape = ChromeShapeModel.shape(input(layout, LayoutStyle.DOCKED)
            .panes(2, SplitAxis.SIDE_BY_SIDE).build());

        // The area under the status bar, a gutter of 8 inside it, split with 8 between.
        assertBox(shape.opening().box, 8, 48, 392, 792);
        assertEquals(2, shape.panes().size());
        assertBox(shape.panes().get(0).box, 8, 48, 196, 792);
        assertBox(shape.panes().get(1).box, 204, 48, 392, 792);
        assertNotEquals(shape.panes().get(0).groupId, shape.panes().get(1).groupId);
        assertTrue(shape.panes().get(0).drawsRim);
        // No shared opening and no divider line; each pane is rounded on all four corners.
        assertTrue(shape.dividers().isEmpty());
        assertEquals(CORNERS, shape.panes().get(0).corners.topRight, D);
        assertEquals(CORNERS, shape.panes().get(1).corners.bottomLeft, D);
        // The frame has one hole per insert, so the gutter between them is frame glass.
        ChromeShape.Card frame = shape.cards().get(0);
        assertEquals(2, frame.holes.size());
        assertBox(frame.holes.get(0), 8, 48, 196, 792);
        assertBox(frame.holes.get(1), 204, 48, 392, 792);
        assertBox(frame.hole, 8, 48, 392, 792);

        // The gutter between panes stops at the cap, as Floating's does.
        ChromeShape capped = ChromeShapeModel.shape(input(layout, LayoutStyle.DOCKED).margin(40f)
            .panes(2, SplitAxis.STACKED).build());
        assertBox(capped.panes().get(0).box, 40, 80, 360, 408);
        assertBox(capped.panes().get(1).box, 40, 432, 360, 760);
    }

    @Test
    public void aTwoPaneSplitIsTwoCardsWithMarginBetweenUnderFloating() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS), GONE, GONE, GONE,
            KeyboardForm.DOCKED, false);
        ChromeShape shape = ChromeShapeModel.shape(input(layout, LayoutStyle.FLOATING)
            .panes(2, SplitAxis.SIDE_BY_SIDE).build());

        assertBox(shape.opening().box, 8, 56, 392, 792);
        assertBox(shape.panes().get(0).box, 8, 56, 196, 792);
        assertBox(shape.panes().get(1).box, 204, 56, 392, 792);
        assertNotEquals(shape.panes().get(0).groupId, shape.panes().get(1).groupId);
        assertTrue(shape.panes().get(0).drawsRim);
        assertEquals(12, shape.panes().get(1).corners.bottomLeft, D);
        assertTrue(shape.dividers().isEmpty());
    }

    @Test
    public void thePaneGapIsCappedAndPanesCanBeStacked() {
        PlaceLayout layout = keyboardOnly(KeyboardForm.DOCKED, false);
        ChromeShape shape = ChromeShapeModel.shape(
            input(layout, LayoutStyle.FLOATING).margin(40f)
                .panes(2, SplitAxis.STACKED).build());

        // Margin 40 surrounds the cards, but the gap between panes stops at 24.
        assertBox(shape.opening().box, 40, 40, 360, 760);
        assertBox(shape.panes().get(0).box, 40, 40, 360, 388);
        assertBox(shape.panes().get(1).box, 40, 412, 360, 760);
    }

    // ---------------------------------------------------------------- the drop query

    @Test
    public void theShapeAnElementWouldHaveIfDroppedOnAnEdge() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.BOTTOM, Element.APPS), Slot.hiddenFrom(Edge.TOP, 1), GONE,
            KeyboardForm.DOCKED, false);

        // The hidden A-Z dropped under the status bar: its own card under Floating...
        Piece floating = ChromeShapeModel.shapeIfDropped(input(layout, LayoutStyle.FLOATING)
            .build(), Element.AZ, Edge.TOP, 1, false);
        assertEquals(PieceId.AZ, floating.id);
        assertBox(floating.box, 8, 56, 392, 86);
        assertCorners(floating, 12, 12, 12, 12);
        assertEquals(EdgeKind.RIM, floating.kind(Edge.TOP));

        // ...and joined to the status bar under Docked: square, with only its inner edge on rim.
        Piece docked = ChromeShapeModel.shapeIfDropped(input(layout, LayoutStyle.DOCKED)
            .build(), Element.AZ, Edge.TOP, 1, false);
        assertBox(docked.box, 0, 40, 400, 70);
        assertCorners(docked, 0, 0, 0, 0);
        assertEquals(EdgeKind.JOIN, docked.kind(Edge.TOP));
        assertEquals(EdgeKind.GUTTER, docked.kind(Edge.BOTTOM));
    }

    @Test
    public void aDropNextToARowSharesItsCardUnderFloatingAndTakesTheScreenCornersUnderDocked() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS),
            Slot.on(Edge.BOTTOM, Element.APPS), Slot.hiddenFrom(Edge.TOP, 1), GONE,
            KeyboardForm.DOCKED, false);

        // The A-Z dropped beside the apps row, farther in: the row below it keeps the corners.
        Piece floating = ChromeShapeModel.shapeIfDropped(input(layout, LayoutStyle.FLOATING)
            .build(), Element.AZ, Edge.BOTTOM, 1, false);
        assertBox(floating.box, 8, 702, 392, 732);
        assertCorners(floating, 12, 12, 0, 0);

        // The status bar dropped on the bottom edge's outermost position takes the screen corners.
        Piece status = ChromeShapeModel.shapeIfDropped(input(layout, LayoutStyle.DOCKED)
            .build(), Element.STATUS, Edge.BOTTOM, 0, false);
        assertBox(status.box, 0, 760, 400, 800);
        assertCorners(status, 0, 0, 28, 28);
        assertEquals(EdgeKind.SCREEN, status.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.JOIN, status.kind(Edge.TOP));
    }

    @Test
    public void theDropQueryLeavesTheInputAlone() {
        PlaceLayout layout = bottomStack();
        ChromeShapeModel.Input in = input(layout, LayoutStyle.DOCKED).build();
        ChromeShape before = ChromeShapeModel.shape(in);
        ChromeShapeModel.shapeIfDropped(in, Element.STATUS, Edge.LEFT, 0, false);
        ChromeShape after = ChromeShapeModel.shape(in);
        assertEquals(before.piece(PieceId.STATUS).box, after.piece(PieceId.STATUS).box);
        assertEquals(layout, in.layout());
    }

    // ---------------------------------------------------------------- the shipped arrangement

    @Test
    public void everyStyleGivesEveryShownPieceARectInsideTheFrame() {
        PlaceLayout layout = bottomStack();
        for (LayoutStyle style : LayoutStyle.values()) {
            ChromeShape shape = shape(layout, style);
            assertEquals(5, shape.pieces().size());
            for (Piece piece : shape.pieces()) {
                assertTrue(piece.toString(), piece.box.left >= 0 && piece.box.top >= 0
                    && piece.box.right <= W && piece.box.bottom <= H);
                assertNotNull(shape.cardOf(piece));
            }
        }
    }
}
