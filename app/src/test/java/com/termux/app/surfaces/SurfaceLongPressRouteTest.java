package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.surfaces.SurfaceLongPressRoute.Answer;
import com.termux.app.surfaces.SurfaceLongPressRoute.Box;
import com.termux.app.surfaces.SurfaceLongPressRoute.Target;
import com.termux.app.surfaces.SurfaceLongPressRoute.Zone;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The long-press popup's rule table (editor-redesign §3): where a hold opens it, and, for each
 * target, that the long-press it already had still wins (§9).
 */
public class SurfaceLongPressRouteTest {

    // A 1000 x 2000 phone: the status bar on top, a keyboard over the dock's three bars at the
    // bottom, the A–Z host standing on the right, the terminal in between.
    private static final Box STATUS = new Box(0, 0, 1000, 100);
    private static final Box AZ_HOST = new Box(940, 100, 1000, 1200);
    private static final Box KEYBOARD = new Box(0, 1200, 1000, 1700);
    private static final Box EXTRA_KEYS = new Box(0, 1700, 1000, 1780);
    private static final Box APPS = new Box(0, 1780, 1000, 1920);
    private static final Box DOCK_FRAME = new Box(0, 1700, 1000, 2000);

    private static List<Zone> zones() {
        return Arrays.asList(
            new Zone(Target.KEYBOARD, null, KEYBOARD),
            new Zone(Target.STATUS, null, STATUS),
            new Zone(Target.DOCK, Element.AZ, AZ_HOST),
            new Zone(Target.DOCK, Element.EXTRA_KEYS, EXTRA_KEYS),
            new Zone(Target.DOCK, Element.APPS, APPS),
            new Zone(Target.DOCK, null, DOCK_FRAME));
    }

    private static Answer press(float x, float y, Box... owners) {
        return SurfaceLongPressRoute.route(x, y, zones(), Arrays.asList(owners));
    }

    @Test
    public void bareDockStatusAndKeyboardFrameOpenThePopupOnTheirOwnTarget() {
        Answer apps = press(500, 1850);
        assertTrue(apps.opensPopup());
        assertEquals(Target.DOCK, apps.target());
        assertEquals(Element.APPS, apps.bar());

        Answer between = press(500, 1960);
        assertEquals("the frame between bars is the dock's", Target.DOCK, between.target());
        assertNull(between.bar());

        assertEquals(Target.STATUS, press(300, 50).target());
        assertEquals(Element.EXTRA_KEYS, press(10, 1740).bar());
        assertEquals("the A–Z host's margin", Element.AZ, press(945, 600).bar());

        Answer frame = press(4, 1205);
        assertTrue("the keyboard's top edge", frame.opensPopup());
        assertEquals(Target.KEYBOARD, frame.target());
    }

    @Test
    public void theKeyboardWinsOverTheDockStackItStandsIn() {
        List<Zone> nested = Arrays.asList(new Zone(Target.KEYBOARD, null, KEYBOARD),
            new Zone(Target.DOCK, null, new Box(0, 1200, 1000, 2000)));
        assertEquals(Target.KEYBOARD,
            SurfaceLongPressRoute.route(500, 1300, nested, Collections.emptyList()).target());
    }

    @Test
    public void theTerminalAndHomeAreNoTarget() {
        Answer terminal = press(500, 600);
        assertFalse(terminal.opensPopup());
        assertNull(terminal.target());
        assertSame(Answer.NONE, terminal);
    }

    @Test
    public void anAppIconKeepsItsAppMenu() {
        Box icon = new Box(100, 1790, 200, 1910);
        Answer answer = press(150, 1850, icon);
        assertTrue(answer.childOwns);
        assertFalse(answer.opensPopup());
        assertTrue("beside the icon is between items", press(260, 1850, icon).opensPopup());
    }

    @Test
    public void anExtraKeyKeepsItsLongPress() {
        Box key = new Box(0, 1700, 120, 1780);
        assertFalse(press(60, 1740, key).opensPopup());
    }

    @Test
    public void theAzLettersKeepTheScrub() {
        Box letters = new Box(950, 110, 990, 1190);
        assertFalse("a press on the letters is already a scrub",
            press(970, 600, letters).opensPopup());
    }

    @Test
    public void aKeyboardKeyKeepsItsAlternates() {
        Box keys = new Box(0, 1240, 1000, 1660);
        assertFalse(press(500, 1400, keys).opensPopup());
        assertTrue("the frame above the keys", press(500, 1220, keys).opensPopup());
    }

    /**
     * A Docked keyboard at density 3 (the emulator's xxhdpi), no extra padding. A hold on the thin
     * band, a gap or a margin is the frame's; a hold on a drawn key is the key's.
     */
    @Test
    public void theTopBandOpensThePopupAndAKeyKeepsItsHold() {
        // No extra padding: the frame above the top row is the keys' own 3 dp (9 px) margin.
        float keysTop = 1200 + 9;
        Box q = new Box(9, keysTop, 109, keysTop + 150);

        Answer band = press(59, 1200 + 4, q);
        assertTrue("the thin band above the top row", band.opensPopup());
        assertEquals(Target.KEYBOARD, band.target());

        // A gap between keys: the keyboard (hasKeyAt) answers no owner there.
        Answer gap = press(59, keysTop + 150);
        assertTrue("a gap between keys opens the popup", gap.opensPopup());

        Answer key = press(59, keysTop + 75, q);
        assertEquals(Target.KEYBOARD, key.target());
        assertTrue("a drawn key keeps its alternates", key.childOwns);
        assertFalse(key.opensPopup());

        assertTrue("the side margin beside the rows", press(4, keysTop + 60).opensPopup());
    }

    @Test
    public void aWindowChipOnTheStatusBarKeepsItsLift() {
        Box chip = new Box(40, 60, 160, 96);
        assertFalse(press(100, 80, chip).opensPopup());
        assertTrue(press(600, 80, chip).opensPopup());
    }

    @Test
    public void anOwnerOutsideTheZoneDoesNotShutIt() {
        Box elsewhere = new Box(0, 0, 10, 10);
        assertTrue(press(500, 1850, elsewhere).opensPopup());
    }

    @Test
    public void anyMovementPastTheSlopIsNoHold() {
        assertTrue(SurfaceLongPressRoute.stillHolding(0, 0, 16));
        assertTrue(SurfaceLongPressRoute.stillHolding(9, 12, 16));
        assertFalse("a swipe or a scrub", SurfaceLongPressRoute.stillHolding(10, 14, 16));
        assertFalse(SurfaceLongPressRoute.stillHolding(1, 0, -1));
    }

    @Test
    public void editOpensEachTargetsCardThroughItsDoor() {
        assertEquals(EditorDoor.POPUP_DOCK, Target.DOCK.door);
        assertEquals(EditorDoor.POPUP_STATUS, Target.STATUS.door);
        assertEquals(EditorDoor.POPUP_KEYBOARD, Target.KEYBOARD.door);
        for (Target target : Target.values())
            assertEquals(target.card, target.door.cardFor(null, null));
        assertEquals("popup-dock -> DOCK", EditorDoor.POPUP_DOCK.line(EditorCard.DOCK));
        assertEquals("popup-status -> STATUS", EditorDoor.POPUP_STATUS.line(EditorCard.STATUS));
        assertEquals("popup-keyboard -> KEYBOARD",
            EditorDoor.POPUP_KEYBOARD.line(EditorCard.KEYBOARD));
        assertEquals(EditorCard.DOCK, EditorDoor.APP_MENU_EDIT_DOCK.cardFor(null, null));
        assertEquals("app-menu-edit-dock -> DOCK",
            EditorDoor.APP_MENU_EDIT_DOCK.line(EditorCard.DOCK));
    }

    @Test
    public void onlyTheKeyboardOffersDockedAndFloating() {
        assertTrue(Target.KEYBOARD.offersForm());
        assertFalse(Target.DOCK.offersForm());
        assertFalse(Target.STATUS.offersForm());
    }

    @Test
    public void hideTakesWhatTheCardsEyeTakes() {
        assertEquals(Element.STATUS, SurfaceLongPressRoute.hides(Target.STATUS, null));
        assertEquals(Element.APPS, SurfaceLongPressRoute.hides(Target.DOCK, null));
        assertEquals(Element.AZ, SurfaceLongPressRoute.hides(Target.DOCK, Element.AZ));
        assertEquals(Element.EXTRA_KEYS,
            SurfaceLongPressRoute.hides(Target.DOCK, Element.EXTRA_KEYS));
        assertNull("the keyboard is switched off, not put away",
            SurfaceLongPressRoute.hides(Target.KEYBOARD, null));
    }

    @Test
    public void thePopupPointsAtThePressAcrossALyingBarAndAlongAStandingOne() {
        Box across = SurfaceLongPressRoute.anchor(APPS, 300, 1850);
        assertEquals(300f, across.left, 0f);
        assertEquals(APPS.top, across.top, 0f);
        assertEquals(APPS.bottom, across.bottom, 0f);

        Box along = SurfaceLongPressRoute.anchor(AZ_HOST, 950, 700);
        assertEquals(AZ_HOST.left, along.left, 0f);
        assertEquals(AZ_HOST.right, along.right, 0f);
        assertEquals(700f, along.top, 0f);

        Box clamped = SurfaceLongPressRoute.anchor(APPS, -50, 1850);
        assertEquals(APPS.left, clamped.left, 0f);
    }

    // Two keys side by side at density 1 (dp = px): drawn 0..40 and 42.4..82.4 across, 10..50
    // down, a 2.4 dp gap between them; the keyboard view spans 0..100 by 0..60.
    private static final float INSET = SurfaceLongPressRoute.POPUP_KEY_CORE_INSET_DP;
    private static final Box LEFT_KEY = new Box(0, 10, 40, 50);
    private static final Box RIGHT_KEY = new Box(42.4f, 10, 82.4f, 50);

    @Test
    public void aHoldInTheGapBetweenKeysOpensThePopup() {
        // The gap's centre: whichever key's rectangle the cell gives, the point is outside it.
        assertFalse(SurfaceLongPressRoute.onKeyCore(41.2f, 30, LEFT_KEY, INSET));
        assertFalse(SurfaceLongPressRoute.onKeyCore(41.2f, 30, RIGHT_KEY, INSET));
    }

    @Test
    public void aHoldOnAKeysOuterBandOpensThePopup() {
        assertFalse("1.5 dp into a key from the gap",
            SurfaceLongPressRoute.onKeyCore(43.9f, 30, RIGHT_KEY, INSET));
        assertFalse("1.5 dp into a key from its bottom",
            SurfaceLongPressRoute.onKeyCore(20, 48.5f, LEFT_KEY, INSET));
    }

    @Test
    public void aHoldOnAKeysCoreKeepsTheKeysLongPress() {
        assertTrue("the key's centre",
            SurfaceLongPressRoute.onKeyCore(62.4f, 30, RIGHT_KEY, INSET));
        assertFalse("3 dp inside the key's edge is still its band",
            SurfaceLongPressRoute.onKeyCore(45.4f, 30, RIGHT_KEY, INSET));
        assertTrue("just past the inset",
            SurfaceLongPressRoute.onKeyCore(46.5f, 30, RIGHT_KEY, INSET));
    }

    @Test
    public void theBottomStripAndSideMarginOpenThePopup() {
        assertFalse("bottom strip", SurfaceLongPressRoute.onKeyCore(20, 55, LEFT_KEY, INSET));
        assertFalse("side margin", SurfaceLongPressRoute.onKeyCore(90, 30, RIGHT_KEY, INSET));
        assertFalse("an empty rectangle is no key",
            SurfaceLongPressRoute.onKeyCore(5, 5, new Box(0, 0, 0, 0), INSET));
        assertFalse("a key too small for its inset has no core",
            SurfaceLongPressRoute.onKeyCore(3, 3, new Box(0, 0, 6, 6), INSET));
    }

    @Test
    public void aCoreHoldOwnsTheKeyboardsPressAndABandHoldDoesNot() {
        // As SurfaceLongPress feeds the route: the keyboard's box is an owner only on a core.
        float y = 1230;
        Box key = new Box(100, 1210, 140, 1250);
        assertTrue(SurfaceLongPressRoute.onKeyCore(120, y, key, INSET));
        assertFalse(press(120, y, KEYBOARD).opensPopup());

        assertFalse(SurfaceLongPressRoute.onKeyCore(101.5f, y, key, INSET));
        Answer band = press(101.5f, y);
        assertTrue(band.opensPopup());
        assertEquals(Target.KEYBOARD, band.target());
    }
}
