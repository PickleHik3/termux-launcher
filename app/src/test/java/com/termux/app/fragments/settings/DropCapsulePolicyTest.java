package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The lifted element's drop capsules as arithmetic: one per slot, no two meeting, the rows
 * starting after a side strip wherever one shows, none past the phone, the tray left to the rail.
 */
public class DropCapsulePolicyTest {

    private static final float[] FRAME = {0f, 0f, 400f, 800f};
    private static final float INSET = 2f;

    private static MiniatureDragPolicy.Slot slot(Edge edge, int index, float left, float top,
                                                 float right, float bottom) {
        return new MiniatureDragPolicy.Slot(edge, index, 0f, left, top, right, bottom);
    }

    private static MiniatureDragPolicy.Slot tray() {
        return new MiniatureDragPolicy.Slot(null, -1, 0f, -90f, 500f, -10f, 800f);
    }

    /**
     * The slots a canvas offers a bar that may stand anywhere: two gaps at the top, two at the
     * bottom, one each side, the side strips reaching into the rows' corners, and the tray.
     */
    private static List<MiniatureDragPolicy.Slot> everyEdge() {
        return Arrays.asList(
            slot(Edge.TOP, 0, 40f, 0f, 360f, 20f),
            slot(Edge.TOP, 1, 40f, 20f, 360f, 90f),
            slot(Edge.BOTTOM, 0, 40f, 760f, 360f, 800f),
            slot(Edge.BOTTOM, 1, 40f, 690f, 360f, 760f),
            slot(Edge.LEFT, 0, 0f, 60f, 70f, 740f),
            slot(Edge.RIGHT, 0, 330f, 60f, 400f, 740f),
            tray());
    }

    private static void assertClean(List<float[]> capsules) {
        for (int i = 0; i < capsules.size(); i++) {
            float[] a = capsules.get(i);
            if (a == null) continue;
            assertTrue("capsule " + i + " has room", a[2] > a[0] && a[3] > a[1]);
            assertTrue("capsule " + i + " inside the frame",
                a[0] >= FRAME[0] + INSET - 0.001f && a[1] >= FRAME[1] + INSET - 0.001f
                    && a[2] <= FRAME[2] - INSET + 0.001f && a[3] <= FRAME[3] - INSET + 0.001f);
            for (int j = i + 1; j < capsules.size(); j++) {
                float[] b = capsules.get(j);
                if (b == null) continue;
                assertFalse("capsules " + i + " and " + j + " meet",
                    DropCapsulePolicy.overlaps(a, b));
                // Not even touching: one inset each side keeps them apart.
                float gapX = Math.max(b[0] - a[2], a[0] - b[2]);
                float gapY = Math.max(b[1] - a[3], a[1] - b[3]);
                assertTrue("capsules " + i + " and " + j + " stand apart",
                    Math.max(gapX, gapY) >= 2 * INSET - 0.001f);
            }
        }
    }

    @Test
    public void everySlotHasItsOwnCapsuleAndNoTwoMeet() {
        List<MiniatureDragPolicy.Slot> slots = everyEdge();
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        assertEquals(slots.size(), capsules.size());
        for (int i = 0; i < slots.size() - 1; i++)
            assertNotNull("slot " + i + " is outlined", capsules.get(i));
        assertNull("the tray is the rail's to draw", capsules.get(slots.size() - 1));
        assertClean(capsules);
    }

    @Test
    public void rowsStartAfterTheSideStripsWhereTheyShow() {
        List<MiniatureDragPolicy.Slot> slots = everyEdge();
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        for (int i = 0; i < 4; i++) {
            float[] row = capsules.get(i);
            assertEquals("row " + i + " starts after the left strip", 70f + INSET, row[0], 0.001f);
            assertEquals("row " + i + " ends before the right strip", 330f - INSET, row[2],
                0.001f);
        }
        // The strips themselves keep their whole height.
        assertEquals(60f + INSET, capsules.get(4)[1], 0.001f);
        assertEquals(740f - INSET, capsules.get(4)[3], 0.001f);
    }

    @Test
    public void withNoSideStripTheRowsKeepTheirLength() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 40f, 0f, 360f, 30f),
            slot(Edge.BOTTOM, 0, 40f, 740f, 360f, 800f));
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        assertEquals(40f + INSET, capsules.get(0)[0], 0.001f);
        assertEquals(360f - INSET, capsules.get(0)[2], 0.001f);
        assertClean(capsules);
    }

    @Test
    public void aStripOnOneSideShortensTheRowsOnThatSideAlone() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 40f, 0f, 360f, 30f),
            slot(Edge.RIGHT, 0, 330f, 60f, 400f, 740f));
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        assertEquals(40f + INSET, capsules.get(0)[0], 0.001f);
        assertEquals(330f - INSET, capsules.get(0)[2], 0.001f);
        assertClean(capsules);
    }

    @Test
    public void overlappingGapsOnOneEdgeAreCutApartHalfway() {
        // Two thin bands leave two gaps whose regions overlap by 4px.
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 40f, 0f, 360f, 24f),
            slot(Edge.TOP, 1, 40f, 20f, 360f, 60f));
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        assertEquals(22f - INSET, capsules.get(0)[3], 0.001f);
        assertEquals(22f + INSET, capsules.get(1)[1], 0.001f);
        assertClean(capsules);
    }

    @Test
    public void theTopAndTheBottomMeetingOnAShortPhoneAreCutApart() {
        float[] shortFrame = {0f, 0f, 800f, 300f};
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 0f, 0f, 800f, 180f),
            slot(Edge.BOTTOM, 0, 0f, 140f, 800f, 300f));
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, shortFrame, INSET);
        assertEquals(160f - INSET, capsules.get(0)[3], 0.001f);
        assertEquals(160f + INSET, capsules.get(1)[1], 0.001f);
        assertFalse(DropCapsulePolicy.overlaps(capsules.get(0), capsules.get(1)));
    }

    @Test
    public void aSlotReachingPastThePhoneIsKeptInsideIt() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.LEFT, 0, -20f, -10f, 60f, 820f));
        float[] capsule = DropCapsulePolicy.capsules(slots, FRAME, INSET).get(0);
        assertEquals(INSET, capsule[0], 0.001f);
        assertEquals(INSET, capsule[1], 0.001f);
        assertEquals(800f - INSET, capsule[3], 0.001f);
    }

    @Test
    public void aSlotWithNoRoomLeftHasNoCapsule() {
        List<MiniatureDragPolicy.Slot> slots = Arrays.asList(
            slot(Edge.TOP, 0, 40f, 0f, 360f, 3f));
        assertNull(DropCapsulePolicy.capsules(slots, FRAME, INSET).get(0));
    }

    @Test
    public void manyGapsOnEveryEdgeStillNeverMeet() {
        List<MiniatureDragPolicy.Slot> slots = new ArrayList<>();
        // Gaps whose regions overlap their neighbours, as thin bands leave them.
        for (int i = 0; i < 5; i++) {
            slots.add(slot(Edge.TOP, i, 30f, i * 18f, 370f, i * 18f + 30f));
            slots.add(slot(Edge.BOTTOM, i, 30f, 800f - i * 18f - 30f, 370f, 800f - i * 18f));
            slots.add(slot(Edge.LEFT, i, i * 14f, 80f, i * 14f + 25f, 720f));
            slots.add(slot(Edge.RIGHT, i, 400f - i * 14f - 25f, 80f, 400f - i * 14f, 720f));
        }
        List<float[]> capsules = DropCapsulePolicy.capsules(slots, FRAME, INSET);
        assertClean(capsules);
    }
}
