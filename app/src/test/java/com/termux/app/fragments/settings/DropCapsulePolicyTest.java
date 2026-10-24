package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * The lifted element's drop targets as arithmetic (SPEC §3.6a items 5–7): a slim marker per
 * slot, no two meeting, none past the phone, the rows starting after a side strip wherever one
 * shows, the tray left to the rail; and the hovered slot's capsule the held element's own size
 * there, a row's thickness on a row and a column's on a side.
 */
public class DropCapsulePolicyTest {

    private static final float[] FRAME = {0f, 0f, 400f, 800f};
    private static final float T = 4f;
    private static final float INSET = 2f;
    private static final float E = 0.001f;

    private static MiniatureDragPolicy.Slot slot(Edge edge, int index, float line, float left,
                                                 float top, float right, float bottom) {
        return new MiniatureDragPolicy.Slot(edge, index, line, left, top, right, bottom);
    }

    private static MiniatureDragPolicy.Slot tray() {
        return new MiniatureDragPolicy.Slot(null, -1, 0f, -90f, 500f, -10f, 800f);
    }

    /**
     * The slots a canvas offers a bar that may stand anywhere: two gaps at the top (one on the
     * phone's edge), two at the bottom, one each side, the side strips reaching into the rows'
     * corners, and the tray.
     */
    private static List<MiniatureDragPolicy.Slot> everyEdge() {
        return Arrays.asList(
            slot(Edge.TOP, 0, 0f, 40f, 0f, 360f, 20f),
            slot(Edge.TOP, 1, 40f, 40f, 20f, 360f, 90f),
            slot(Edge.BOTTOM, 0, 800f, 40f, 760f, 360f, 800f),
            slot(Edge.BOTTOM, 1, 720f, 40f, 690f, 360f, 760f),
            slot(Edge.LEFT, 0, 30f, 0f, 60f, 70f, 740f),
            slot(Edge.RIGHT, 0, 370f, 330f, 60f, 400f, 740f),
            tray());
    }

    private static void assertClean(List<float[]> markers, float[] frame) {
        for (int i = 0; i < markers.size(); i++) {
            float[] a = markers.get(i);
            if (a == null) continue;
            assertTrue("marker " + i + " has room", a[2] > a[0] && a[3] > a[1]);
            assertTrue("marker " + i + " inside the frame",
                a[0] >= frame[0] + INSET - E && a[1] >= frame[1] + INSET - E
                    && a[2] <= frame[2] - INSET + E && a[3] <= frame[3] - INSET + E);
            for (int j = i + 1; j < markers.size(); j++) {
                float[] b = markers.get(j);
                if (b == null) continue;
                assertFalse("markers " + i + " and " + j + " meet",
                    DropCapsulePolicy.overlaps(a, b));
            }
        }
    }

    private static float thickness(float[] marker, Edge edge) {
        return edge.isOnSide() ? marker[2] - marker[0] : marker[3] - marker[1];
    }

    @Test
    public void everySlotHasASlimMarkerAndNoTwoMeet() {
        List<MiniatureDragPolicy.Slot> slots = everyEdge();
        List<float[]> markers = DropCapsulePolicy.markers(slots, FRAME, T, INSET);
        assertEquals(slots.size(), markers.size());
        for (int i = 0; i < slots.size() - 1; i++) {
            float[] marker = markers.get(i);
            assertNotNull("slot " + i + " is marked", marker);
            assertEquals("slot " + i + "'s marker is slim", T,
                thickness(marker, slots.get(i).edge), E);
        }
        assertNull("the tray is the rail's to draw", markers.get(slots.size() - 1));
        assertClean(markers, FRAME);
    }

    @Test
    public void aMarkerStandsOnItsGapLineOrJustInsideTheFrame() {
        List<float[]> markers = DropCapsulePolicy.markers(everyEdge(), FRAME, T, INSET);
        // Between two bands: centred on the line.
        assertEquals(40f - T / 2f, markers.get(1)[1], E);
        assertEquals(40f + T / 2f, markers.get(1)[3], E);
        // On the phone's own edge: moved in whole, not cut.
        assertEquals(INSET, markers.get(0)[1], E);
        assertEquals(800f - INSET, markers.get(2)[3], E);
    }

    @Test
    public void rowMarkersStartAfterTheSideStripsWhereTheyShow() {
        List<float[]> markers = DropCapsulePolicy.markers(everyEdge(), FRAME, T, INSET);
        for (int i = 0; i < 4; i++) {
            float[] row = markers.get(i);
            assertTrue("row " + i + " starts after the left strip", row[0] >= 70f + INSET - E);
            assertTrue("row " + i + " ends before the right strip", row[2] <= 330f - INSET + E);
        }
        // The strips run their slot's length.
        assertEquals(60f + INSET, markers.get(4)[1], E);
        assertEquals(740f - INSET, markers.get(4)[3], E);
    }

    @Test
    public void withNoSideStripTheRowsRunTheirSlotsLength() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 10f, 40f, 0f, 360f, 30f),
            slot(Edge.BOTTOM, 0, 780f, 40f, 740f, 360f, 800f));
        List<float[]> markers = DropCapsulePolicy.markers(slots, FRAME, T, INSET);
        assertEquals(40f + INSET, markers.get(0)[0], E);
        assertEquals(360f - INSET, markers.get(0)[2], E);
        assertClean(markers, FRAME);
    }

    @Test
    public void gapsOnOneLineAsAThinBandLeavesThemStillNeverMeet() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 20f, 40f, 10f, 360f, 20f),
            slot(Edge.TOP, 1, 20f, 40f, 20f, 360f, 40f));
        List<float[]> markers = DropCapsulePolicy.markers(slots, FRAME, T, INSET);
        assertNotNull(markers.get(0));
        assertNotNull(markers.get(1));
        assertClean(markers, FRAME);
    }

    @Test
    public void manyGapsOnEveryEdgeStillNeverMeet() {
        List<MiniatureDragPolicy.Slot> slots = new ArrayList<>();
        // Gaps whose regions overlap their neighbours, as thin bands leave them.
        for (int i = 0; i < 5; i++) {
            slots.add(slot(Edge.TOP, i, i * 18f + 1f, 30f, i * 18f, 370f, i * 18f + 30f));
            slots.add(slot(Edge.BOTTOM, i, 799f - i * 18f, 30f, 800f - i * 18f - 30f, 370f,
                800f - i * 18f));
            slots.add(slot(Edge.LEFT, i, i * 14f + 2f, i * 14f, 80f, i * 14f + 25f, 720f));
            slots.add(slot(Edge.RIGHT, i, 398f - i * 14f, 400f - i * 14f - 25f, 80f,
                400f - i * 14f, 720f));
        }
        assertClean(DropCapsulePolicy.markers(slots, FRAME, T, INSET), FRAME);
    }

    // ---------------------------------------------------------------- the hovered capsule

    private static final ChromeShapeModel.Thickness THICKNESS =
        ChromeShapeModel.Thickness.of(40f, 50f, 60f, 70f, 30f, 24f, 50f, 45f, 200f);

    /** Status on top, apps at the bottom, the A–Z index hidden; no extra keys, no keyboard. */
    private static PlaceLayout azHidden() {
        EnumMap<Element, com.termux.app.place.Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, com.termux.app.place.Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, com.termux.app.place.Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, com.termux.app.place.Slot.hiddenFrom(Edge.TOP, 1));
        slots.put(Element.EXTRA_KEYS, com.termux.app.place.Slot.hiddenFrom(Edge.BOTTOM, 0));
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, false, 4, 6);
    }

    private static ChromeShapeModel.Input input(LayoutStyle style) {
        return ChromeShapeModel.input(azHidden(), style, 400f, 800f, THICKNESS)
            .corners(12f).margin(8f).screenRadius(28f).paneGapCap(24f).build();
    }

    @Test
    public void theHoveredCapsuleIsTheHeldElementsOwnSizeAtThatSlot() {
        float[] frame = {10f, 20f, 410f, 820f};
        for (LayoutStyle style : LayoutStyle.values()) {
            Piece row = ChromeShapeModel.shapeIfDropped(input(style), Element.AZ, Edge.TOP, 1,
                false);
            float[] capsule = DropCapsulePolicy.hoveredCapsule(row.box, 10f, 20f, frame);
            assertNotNull(capsule);
            assertEquals(row.box.left + 10f, capsule[0], E);
            assertEquals(row.box.top + 20f, capsule[1], E);
            assertEquals(row.box.width(), capsule[2] - capsule[0], E);
            assertEquals(style + ": a row's thickness on a row", 30f, capsule[3] - capsule[1], E);

            Piece column = ChromeShapeModel.shapeIfDropped(input(style), Element.AZ, Edge.LEFT,
                0, false);
            float[] side = DropCapsulePolicy.hoveredCapsule(column.box, 10f, 20f, frame);
            assertNotNull(side);
            assertEquals(style + ": a column's thickness on a side", 24f, side[2] - side[0], E);
            assertEquals(column.box.height(), side[3] - side[1], E);
        }
    }

    @Test
    public void aCapsuleIsKeptInsideTheFrame() {
        com.termux.app.place.ChromeShape.Box past =
            new com.termux.app.place.ChromeShape.Box(-10f, -5f, 120f, 30f);
        float[] capsule = DropCapsulePolicy.hoveredCapsule(past, 0f, 0f, FRAME);
        assertNotNull(capsule);
        assertEquals(0f, capsule[0], E);
        assertEquals(0f, capsule[1], E);
    }
}
