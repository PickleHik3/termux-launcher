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
 * Under Floating the keyboard is a card of its own in the live shape, which is why the live shape
 * has to be asked with the in-app keyboard shown: 400 x 800 frame, as {@link LiveChromeShapeTest}.
 */
public class KeyboardCardShapeTest {

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

    private static ChromeShape shape(PlaceLayout layout, LayoutStyle style) {
        return LiveChromeShape.of(layout, style, 400, 800, THICKNESS, CORNERS, MARGIN, SCREEN,
            DENSITY, 1, ChromeShapeModel.SplitAxis.SIDE_BY_SIDE, 0f, null);
    }

    private static boolean[] cutCorners(LiveChromeShape.Clip clip) {
        boolean round = clip.radius > 0f;
        return new boolean[] {
            round && clip.reachLeft == 0 && clip.reachTop == 0,
            round && clip.reachRight == 0 && clip.reachTop == 0,
            round && clip.reachRight == 0 && clip.reachBottom == 0,
            round && clip.reachLeft == 0 && clip.reachBottom == 0};
    }

    @Test
    public void underFloatingTheKeyboardIsACardOfItsOwnWithItsOwnRimAndMarginAirAround() {
        ChromeShape shape = shape(bottomStack(), LayoutStyle.FLOATING);
        Piece keyboard = shape.piece(PieceId.KEYBOARD);
        assertNotNull("a shown keyboard is a piece of the shape", keyboard);
        ChromeShape.Card card = shape.cardOf(keyboard);
        assertFalse("its own card, not a slice of the Docked frame", card.frame);
        assertEquals("not the dock's card", 1, card.members.size());
        ChromeShape.Card dockCard = shape.cardOf(shape.piece(PieceId.EXTRA_KEYS));
        assertFalse(dockCard == card);
        assertEquals("a rim all round", ChromeEdgeRule.ALL,
            LiveChromeShape.rimEdges(shape, Collections.singletonList(PieceId.KEYBOARD)));
        assertEquals(ChromeEdgeRule.NONE,
            LiveChromeShape.seamEdges(shape, Collections.singletonList(PieceId.KEYBOARD)));
        LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape,
            Collections.singletonList(PieceId.KEYBOARD));
        assertEquals("Corners", CORNERS, clip.radius, D);
        assertTrue("every corner is cut", cutCorners(clip)[0] && cutCorners(clip)[1]
            && cutCorners(clip)[2] && cutCorners(clip)[3]);
        assertEquals("Margin from the screen's sides", MARGIN, keyboard.box.left, D);
        assertEquals(MARGIN, 400f - keyboard.box.right, D);
        assertEquals("Margin of air from the dock card above", MARGIN,
            keyboard.box.top - dockCard.box.bottom, D);
    }

    @Test
    public void aKeyboardThatIsNotShownIsNoPieceSoTheLiveShapeMustSayWhenItIsUp() {
        // The live shape is asked for with the keyboard the app shows (the in-app one); asking
        // with the system IME's visibility, which the in-app keyboard never raises, leaves the
        // keyboard out of the shape and its card unread.
        PlaceLayout shown = bottomStack();
        assertNotNull(shape(shown, LayoutStyle.FLOATING).piece(PieceId.KEYBOARD));
        assertNull(shape(shown.withKeyboardShown(false), LayoutStyle.FLOATING)
            .piece(PieceId.KEYBOARD));
    }
}
