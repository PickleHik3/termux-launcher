package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.EnumMap;

/**
 * How the live frame's pictures move over a target (SPEC §3.6a item 1), on the shape model's
 * 400 × 800 Docked frame: a 40-high status bar, a 30-high A–Z row, a 60-high apps row, so every
 * move can be added up by hand. The held element never moves with the others.
 */
public class LiveDragPolicyTest {

    private static final float E = 0.01f;
    private static final ChromeShapeModel.Thickness THICKNESS =
        ChromeShapeModel.Thickness.of(40f, 50f, 60f, 70f, 30f, 24f, 50f, 45f, 200f);

    /** Status and A–Z on top (A–Z under it), apps at the bottom, no extra keys, no keyboard. */
    private static PlaceLayout layout() {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.TOP, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.hiddenFrom(Edge.BOTTOM, 0));
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, false, 4, 6);
    }

    private static ChromeShapeModel.Input input() {
        return ChromeShapeModel.input(layout(), LayoutStyle.DOCKED, 400f, 800f, THICKNESS)
            .corners(12f).screenRadius(28f).paneGapCap(24f).build();
    }

    @Test
    public void theHeldElementLeavesTheOthersToMoveAndHasNoMoveOfItsOwn() {
        LiveDragPolicy.Reflow reflow = LiveDragPolicy.reflow(input(), layout(), Element.STATUS,
            Edge.BOTTOM, 0, false);
        assertFalse(reflow.elements.containsKey(Element.STATUS));
        // The status bar gone from the top: the A–Z row under it moves up by its height.
        float[] az = reflow.elements.get(Element.AZ);
        assertNotNull(az);
        assertEquals(0f, az[0], E);
        assertEquals(-40f, az[1], E);
        // Dropped outside the apps row at the bottom: the row moves up by the status bar's height.
        float[] apps = reflow.elements.get(Element.APPS);
        assertNotNull(apps);
        assertEquals(-40f, apps[1], E);
    }

    @Test
    public void aDropInsideAStackPushesOnlyWhatStandsFurtherIn() {
        // The apps row dropped on top, between the status bar and the A–Z row.
        LiveDragPolicy.Reflow reflow = LiveDragPolicy.reflow(input(), layout(), Element.APPS,
            Edge.TOP, 1, false);
        assertEquals(0f, reflow.elements.get(Element.STATUS)[1], E);
        assertEquals(60f, reflow.elements.get(Element.AZ)[1], E);
    }

    @Test
    public void putAwayTheOthersCloseTheGap() {
        LiveDragPolicy.Reflow reflow = LiveDragPolicy.reflow(input(), layout(), Element.STATUS,
            null, -1, false);
        assertEquals(-40f, reflow.elements.get(Element.AZ)[1], E);
        assertEquals(0f, reflow.elements.get(Element.APPS)[1], E);
    }

    @Test
    public void overItsOwnPlaceNothingMoves() {
        // The A–Z row dropped back where it is: second on top.
        LiveDragPolicy.Reflow reflow = LiveDragPolicy.reflow(input(), layout(), Element.AZ,
            Edge.TOP, 1, false);
        for (float[] move : reflow.elements.values()) {
            assertEquals(0f, move[0], E);
            assertEquals(0f, move[1], E);
        }
        assertTrue(reflow.elements.containsKey(Element.STATUS));
    }
}
