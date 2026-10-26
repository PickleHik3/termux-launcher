package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;
import com.termux.app.surfaces.cards.BarOrder.Position;
import com.termux.app.surfaces.cards.BarOrder.Setting;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BarOrderTest {

    private static final Element APPS = Element.APPS;
    private static final Element AZ = Element.AZ;
    private static final Element KEYS = Element.EXTRA_KEYS;
    private static final Element STATUS = Element.STATUS;

    /** The shipped arrangement: status on top, the three bars over the keyboard along the bottom. */
    private static PlaceLayout shipped() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(STATUS, Slot.on(Edge.TOP, STATUS));
        slots.put(APPS, Slot.on(Edge.BOTTOM, APPS));
        slots.put(AZ, Slot.on(Edge.BOTTOM, AZ));
        slots.put(KEYS, Slot.on(Edge.BOTTOM, KEYS));
        return layout(slots);
    }

    private static PlaceLayout layout(Map<Element, Slot> slots) {
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.OVERLAY,
            PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    }

    // ------------------------------------------------------------------------------ the list

    @Test
    public void theListStoresAsStorageKeysAndReadsBack() {
        assertEquals("apps_row,az_bar,extra_keys", BarOrder.format(BarOrder.DEFAULT));
        List<Element> order = Arrays.asList(KEYS, APPS, AZ);
        assertEquals(order, BarOrder.parse(BarOrder.format(order)));
    }

    @Test
    public void aStoredListMissingOrNonsenseFallsBackWithEveryBarOnce() {
        assertEquals(BarOrder.DEFAULT, BarOrder.parse(null));
        assertEquals(BarOrder.DEFAULT, BarOrder.parse(""));
        assertEquals(Arrays.asList(KEYS, APPS, AZ), BarOrder.parse("extra_keys,status_bar,"
            + "extra_keys,nonsense"));
        assertEquals("the status bar is never a row", BarOrder.DEFAULT,
            BarOrder.normalized(Arrays.asList(STATUS, APPS)));
    }

    @Test
    public void aMoveTakesTheRowOutAndPutsItBack() {
        assertEquals(Arrays.asList(AZ, KEYS, APPS), BarOrder.moved(BarOrder.DEFAULT, 0, 2));
        assertEquals(Arrays.asList(KEYS, APPS, AZ), BarOrder.moved(BarOrder.DEFAULT, 2, 0));
        assertEquals(BarOrder.DEFAULT, BarOrder.moved(BarOrder.DEFAULT, 5, 0));
    }

    @Test
    public void aDragAmongTheShownRowsLeavesAnUnshownBarInPlace() {
        // Home wears no extra keys: their row is not shown, and a drag among the other two
        // leaves them where the list had them.
        List<Element> full = Arrays.asList(APPS, KEYS, AZ);
        assertEquals(Arrays.asList(AZ, KEYS, APPS),
            BarOrder.mergeVisible(full, Arrays.asList(AZ, APPS)));
    }

    @Test
    public void aDraggedRowLandsAfterEveryCentreItPassed() {
        float[] centres = {50f, 150f, 250f};
        assertEquals(0, BarOrder.dropIndex(centres, 0, 60f));
        assertEquals(1, BarOrder.dropIndex(centres, 0, 160f));
        assertEquals(2, BarOrder.dropIndex(centres, 0, 400f));
        assertEquals(0, BarOrder.dropIndex(centres, 2, 10f));
    }

    // -------------------------------------------------------------------------- the stacking

    @Test
    public void theShippedBottomStackReadsAsTheDefaultList() {
        PlaceLayout shipped = shipped();
        assertEquals(BarOrder.DEFAULT, BarOrder.reconcile(BarOrder.DEFAULT, shipped));
        assertSame("already in order: untouched", shipped,
            BarOrder.restack(shipped, BarOrder.DEFAULT));
    }

    @Test
    public void extraKeysDraggedUpStandInnermostAlongTheBottom() {
        PlaceLayout next = BarOrder.restack(shipped(), Arrays.asList(KEYS, APPS, AZ));
        // Outermost first: A–Z against the screen's edge, then the apps row, the keys inside.
        assertEquals(Arrays.asList(AZ, APPS, KEYS),
            EdgeStackPolicy.overKeyboard(next));
        assertEquals(Arrays.asList(KEYS, APPS, AZ), BarOrder.reconcile(
            Arrays.asList(KEYS, APPS, AZ), next));
    }

    @Test
    public void onTheTopEdgeTheFirstRowIsTheOutermostBarAndTheStatusBarKeepsItsBand() {
        PlaceLayout top = shipped();
        top = top.withSlot(APPS, Slot.on(Edge.TOP, APPS));
        top = top.withSlot(KEYS, Slot.on(Edge.TOP, KEYS));
        PlaceLayout next = BarOrder.restack(top, Arrays.asList(KEYS, AZ, APPS));
        assertEquals(Arrays.asList(STATUS, KEYS, APPS), EdgeStackPolicy.stack(next, Edge.TOP));
        PlaceLayout back = BarOrder.restack(next, Arrays.asList(APPS, AZ, KEYS));
        assertEquals(Arrays.asList(STATUS, APPS, KEYS), EdgeStackPolicy.stack(back, Edge.TOP));
    }

    @Test
    public void onTheRightEdgeTheFirstRowIsTheInnermost() {
        PlaceLayout right = shipped();
        right = right.withSlot(APPS, Slot.on(Edge.RIGHT, APPS));
        right = right.withSlot(AZ, Slot.on(Edge.RIGHT, AZ));
        PlaceLayout next = BarOrder.restack(right, Arrays.asList(AZ, APPS, KEYS));
        assertEquals("A–Z first reads left of the apps rail", Arrays.asList(APPS, AZ),
            EdgeStackPolicy.stack(next, Edge.RIGHT));
    }

    @Test
    public void barsOnEdgesOfTheirOwnKeepTheStoredOrder() {
        PlaceLayout apart = shipped().withSlot(AZ, Slot.on(Edge.RIGHT, AZ))
            .withSlot(KEYS, Slot.on(Edge.TOP, KEYS));
        List<Element> stored = Arrays.asList(KEYS, AZ, APPS);
        assertEquals(stored, BarOrder.reconcile(stored, apart));
        assertEquals(apart, BarOrder.restack(apart, stored));
    }

    @Test
    public void aListThatDisagreesWithASharedStackReadsAsTheScreen() {
        // A revert put the bars back; the stored list still says the keys come first.
        List<Element> stored = Arrays.asList(KEYS, APPS, AZ);
        assertEquals(BarOrder.DEFAULT, BarOrder.reconcile(stored, shipped()));
    }

    @Test
    public void theTwoSidesOfTheKeyboardAreStacksOfTheirOwn() {
        PlaceLayout split = shipped().withSlot(APPS, Slot.on(Edge.BOTTOM, APPS)
            .withUnderKeyboard(true));
        PlaceLayout next = BarOrder.restack(split, Arrays.asList(APPS, KEYS, AZ));
        assertEquals(Arrays.asList(APPS), EdgeStackPolicy.underKeyboard(next));
        assertEquals("keys first reads innermost over the keyboard", Arrays.asList(AZ, KEYS),
            EdgeStackPolicy.overKeyboard(next));
    }

    @Test
    public void aHiddenBarKeepsItsSlotThroughARestack() {
        Slot hidden = Slot.hiddenFrom(Edge.BOTTOM, 0);
        PlaceLayout layout = shipped().withSlot(KEYS, hidden);
        PlaceLayout next = BarOrder.restack(layout, Arrays.asList(AZ, APPS, KEYS));
        assertEquals(hidden, next.slot(KEYS));
        assertEquals(Arrays.asList(APPS, AZ), EdgeStackPolicy.overKeyboard(next));
    }

    // -------------------------------------------------------------------------- the positions

    @Test
    public void eachBarOffersItsOwnPositions() {
        assertEquals(Arrays.asList(Position.TOP, Position.BOTTOM, Position.LEFT, Position.RIGHT,
            Position.ABOVE_KEYBOARD), BarOrder.positionsFor(APPS));
        assertEquals(BarOrder.positionsFor(APPS), BarOrder.positionsFor(KEYS));
        assertEquals(Arrays.asList(Position.RIDES_APPS_ROW, Position.TOP, Position.LEFT,
            Position.RIGHT), BarOrder.positionsFor(AZ));
    }

    @Test
    public void bottomIsUnderTheKeyboardAndAboveTheKeyboardIsOverIt() {
        assertEquals(Position.ABOVE_KEYBOARD, BarOrder.positionOf(APPS,
            Slot.on(Edge.BOTTOM, APPS)));
        assertEquals(Position.BOTTOM, BarOrder.positionOf(APPS,
            Slot.on(Edge.BOTTOM, APPS).withUnderKeyboard(true)));
        assertEquals(Position.RIDES_APPS_ROW, BarOrder.positionOf(AZ, Slot.on(Edge.BOTTOM, AZ)));
        assertEquals(Position.RIGHT, BarOrder.positionOf(AZ, Slot.on(Edge.RIGHT, AZ)));

        PlaceLayout bottom = BarOrder.withPosition(shipped(), APPS, Position.BOTTOM);
        assertTrue(bottom.slot(APPS).underKeyboard);
        assertEquals(Position.BOTTOM, BarOrder.positionOf(APPS, bottom.slot(APPS)));
        PlaceLayout above = BarOrder.withPosition(bottom, APPS, Position.ABOVE_KEYBOARD);
        assertFalse(above.slot(APPS).underKeyboard);
        assertEquals(Edge.BOTTOM, above.slot(APPS).edge);
    }

    @Test
    public void aBarOnANewEdgeTakesItsUsualBandAndKeepsBeingHidden() {
        PlaceLayout moved = BarOrder.withPosition(shipped(), KEYS, Position.TOP);
        assertEquals(Slot.on(Edge.TOP, KEYS), moved.slot(KEYS));
        PlaceLayout hidden = shipped().withSlot(KEYS, Slot.hiddenFrom(Edge.BOTTOM, 0));
        Slot away = BarOrder.withPosition(hidden, KEYS, Position.LEFT).slot(KEYS);
        assertTrue("moving a hidden bar does not show it", away.hidden);
        assertEquals(Edge.LEFT, away.edge);
    }

    @Test
    public void theAzStripRidingTheAppsRowFollowsItAcrossTheKeyboard() {
        PlaceLayout under = BarOrder.withPosition(shipped(), APPS, Position.BOTTOM);
        assertTrue("A–Z rides along under the keyboard", under.slot(AZ).underKeyboard);
        PlaceLayout right = BarOrder.withPosition(under, AZ, Position.RIGHT);
        assertFalse(right.slot(AZ).underKeyboard);
        PlaceLayout back = BarOrder.withPosition(right, AZ, Position.RIDES_APPS_ROW);
        assertEquals(Edge.BOTTOM, back.slot(AZ).edge);
        assertTrue("it rides back in under the keyboard", back.slot(AZ).underKeyboard);
    }

    // ------------------------------------------------------------------------- the expansion

    @Test
    public void eachBarOpensItsOwnSettings() {
        assertEquals(Arrays.asList(Setting.POSITION, Setting.APP_ICONS, Setting.MOST_USED,
            Setting.ICON_PACK), BarOrder.settingsOf(APPS));
        assertEquals(Arrays.asList(Setting.POSITION, Setting.GLASS_BACKGROUND, Setting.SHOW),
            BarOrder.settingsOf(AZ));
        assertEquals(Arrays.asList(Setting.POSITION, Setting.EXTRA_KEYS_EDITOR),
            BarOrder.settingsOf(KEYS));
        assertTrue(BarOrder.settingsOf(STATUS).isEmpty());
    }

    @Test
    public void oneBarIsOpenAtATimeAndASecondTapClosesIt() {
        assertEquals(AZ, BarOrder.toggled(null, AZ));
        assertEquals(KEYS, BarOrder.toggled(AZ, KEYS));
        assertNull(BarOrder.toggled(KEYS, KEYS));
    }

    @Test
    public void aDoorNamingABarOpensTheCardOnIt() {
        assertEquals(AZ, BarOrder.expandedOnOpen(AZ));
        assertEquals(KEYS, BarOrder.expandedOnOpen(KEYS));
        assertNull(BarOrder.expandedOnOpen(STATUS));
        assertNull(BarOrder.expandedOnOpen(null));
    }
}
