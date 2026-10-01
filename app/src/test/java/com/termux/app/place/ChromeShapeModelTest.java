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
        assertBox(shape.opening().box, 0, 40, 400, 460);

        // One frame, clipped once, with the opening cut out of it.
        assertEquals(1, shape.cards().size());
        ChromeShape.Card frame = shape.cards().get(0);
        assertTrue(frame.frame);
        assertBox(frame.box, 0, 0, 400, 800);
        assertBox(frame.hole, 0, 40, 400, 460);
        assertEquals(SCREEN, frame.corners.topLeft, D);
        for (Piece piece : shape.pieces()) assertEquals(frame.id, piece.groupId);

        // Only the frame's exposed outer corners round, at the screen radius.
        assertCorners(status, 28, 28, 0, 0);
        assertCorners(keyboard, 0, 0, 28, 28);
        assertCorners(keys, 0, 0, 0, 0);
        assertCorners(apps, 0, 0, 0, 0);

        // Joins are plain, the screen is plain, only the opening's edge carries rim.
        assertEquals(EdgeKind.SCREEN, status.kind(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, status.kind(Edge.LEFT));
        assertEquals(EdgeKind.OPENING, status.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.OPENING, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.JOIN, keyboard.kind(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, keyboard.kind(Edge.BOTTOM));
        assertTrue(status.drawsRim(Edge.BOTTOM));
        assertTrue(apps.drawsRim(Edge.TOP));
        assertFalse(apps.drawsRim(Edge.BOTTOM));
        assertFalse(status.drawsRim(Edge.TOP));
        assertFalse(keyboard.drawsRim(Edge.TOP));

        // The pane is an opening with no rim or slab of its own.
        assertFalse(shape.opening().ownsRim);
        assertFalse(shape.panes().get(0).drawsRim);
        assertEquals(EdgeKind.JOIN, shape.opening().kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, shape.opening().kind(Edge.BOTTOM));
        assertEquals(EdgeKind.SCREEN, shape.opening().kind(Edge.LEFT));
        assertEquals(EdgeKind.SCREEN, shape.opening().kind(Edge.RIGHT));
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
        assertBox(shape.opening().box, 70, 40, 376, 550);

        // The side bars touch the bars above and below: no corner of theirs is a screen corner.
        assertCorners(apps, 0, 0, 0, 0);
        assertCorners(az, 0, 0, 0, 0);
        assertEquals(EdgeKind.SCREEN, apps.kind(Edge.LEFT));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.TOP));
        assertEquals(EdgeKind.JOIN, apps.kind(Edge.BOTTOM));
        assertEquals(EdgeKind.OPENING, apps.kind(Edge.RIGHT));
        assertEquals(EdgeKind.OPENING, az.kind(Edge.LEFT));

        // The status bar's inner edge runs over the left bar, the opening and the right bar.
        assertEquals(3, status.runs(Edge.BOTTOM).size());
        assertEquals(EdgeKind.JOIN, status.runs(Edge.BOTTOM).get(0).kind);
        assertEquals(EdgeKind.OPENING, status.runs(Edge.BOTTOM).get(1).kind);
        assertEquals(70, status.runs(Edge.BOTTOM).get(1).from, D);
        assertEquals(376, status.runs(Edge.BOTTOM).get(1).to, D);
        assertEquals(EdgeKind.JOIN, status.runs(Edge.BOTTOM).get(2).kind);
        assertEquals(EdgeKind.OPENING, status.kind(Edge.BOTTOM));

        // A bar stands on every side of the opening.
        assertEquals(EdgeKind.JOIN, shape.opening().kind(Edge.LEFT));
        assertEquals(EdgeKind.JOIN, shape.opening().kind(Edge.RIGHT));
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
        assertEquals(EdgeKind.OPENING, apps.kind(Edge.BOTTOM));
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
        assertEquals(EdgeKind.OPENING, az.kind(Edge.RIGHT));
        assertEquals(EdgeKind.SCREEN, az.kind(Edge.LEFT));
        assertEquals(EdgeKind.SCREEN, az.kind(Edge.TOP));
    }

    @Test
    public void aDockedFrameWithNothingInItIsJustTheOpening() {
        ChromeShape shape = shape(keyboardOnly(KeyboardForm.DOCKED, false), LayoutStyle.DOCKED);
        assertTrue(shape.pieces().isEmpty());
        assertTrue(shape.cards().isEmpty());
        assertBox(shape.opening().box, 0, 0, 400, 800);
        assertEquals(28, shape.opening().corners.topLeft, D);
        assertEquals(EdgeKind.SCREEN, shape.opening().kind(Edge.TOP));
    }

    // ---------------------------------------------------------------- keyboard form by Style

    @Test
    public void dockedKeyboardJoinsTheFrameUnderDockedAndIsACardUnderFloating() {
        PlaceLayout layout = keyboardOnly(KeyboardForm.DOCKED, true);

        Piece docked = piece(shape(layout, LayoutStyle.DOCKED), PieceId.KEYBOARD);
        assertBox(docked.box, 0, 600, 400, 800);
        assertCorners(docked, 0, 0, 28, 28);
        assertEquals(EdgeKind.OPENING, docked.kind(Edge.TOP));
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
        // It takes no band: the opening still fills the frame.
        assertBox(docked.opening().box, 0, 0, 400, 800);
        assertEquals(1, docked.cards().size());
        assertFalse(docked.cards().get(0).frame);

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
        assertEquals(EdgeKind.OPENING, left.kind(Edge.RIGHT));
        assertEquals(EdgeKind.OPENING, left.kind(Edge.TOP));
        assertBox(shape.opening().box, 0, 0, 400, 600);
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
        assertBox(docked.opening().box, 0, 0, 400, 690);

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
    public void aTwoPaneSplitIsOneOpeningWithADividerUnderDocked() {
        PlaceLayout layout = layout(Slot.on(Edge.TOP, Element.STATUS), GONE, GONE, GONE,
            KeyboardForm.DOCKED, false);
        ChromeShape shape = ChromeShapeModel.shape(input(layout, LayoutStyle.DOCKED)
            .panes(2, SplitAxis.SIDE_BY_SIDE).dividerThickness(1f).build());

        assertBox(shape.opening().box, 0, 40, 400, 800);
        assertEquals(2, shape.panes().size());
        assertBox(shape.panes().get(0).box, 0, 40, 200, 800);
        assertBox(shape.panes().get(1).box, 200, 40, 400, 800);
        assertEquals(shape.panes().get(0).groupId, shape.panes().get(1).groupId);
        assertFalse(shape.panes().get(0).drawsRim);
        assertEquals(1, shape.dividers().size());
        ChromeShape.Divider line = shape.dividers().get(0);
        assertEquals(200, line.x1, D);
        assertEquals(200, line.x2, D);
        assertEquals(40, line.y1, D);
        assertEquals(800, line.y2, D);
        assertEquals(1, line.thickness, D);
        // Each pane rounds only a screen corner it stands in.
        assertEquals(0, shape.panes().get(0).corners.topLeft, D);
        assertEquals(28, shape.panes().get(0).corners.bottomLeft, D);
        assertEquals(28, shape.panes().get(1).corners.bottomRight, D);
        assertEquals(0, shape.panes().get(1).corners.bottomLeft, D);
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
        assertEquals(EdgeKind.OPENING, docked.kind(Edge.BOTTOM));
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
