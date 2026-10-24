package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The Peek glass card: the icons' centres on its top edge, the icons apart and on the card, the
 * card clear of the screen's sides by its inset, its lower corners off screen, and the
 * reflection's band on the card and below the icons.
 */
public class PeekGlassGeometryTest {

    private static final float DENSITY = 2.625f;
    private static final int SCREEN_W = 1080;
    /** The icons' top in the host, as a row with its air lays them out. */
    private static final float ICON_TOP = 22f;
    private static final int HOST_H = 180;
    /** The strip under the gesture pill continues the card this far below the host. */
    private static final int STRIP_H = 63;

    /** The row's icon boxes under Peek glass, laid out as the row lays them: even slots, centred. */
    private static float[] rowBoxes(int screenW, int slots, int wantedIconPx, float density) {
        int inset = PeekGlassGeometry.rowSideInsetPx(screenW, density);
        int usable = screenW - 2 * inset;
        int icon = DockLayoutPolicy.slotIconPx(wantedIconPx, usable, slots, false, density);
        float slot = usable / (float) slots;
        float[] boxes = new float[slots * 4];
        for (int i = 0; i < slots; i++) {
            float cx = inset + slot * (i + 0.5f);
            boxes[i * 4] = cx - icon / 2f;
            boxes[i * 4 + 1] = ICON_TOP;
            boxes[i * 4 + 2] = cx + icon / 2f;
            boxes[i * 4 + 3] = ICON_TOP + icon;
        }
        return boxes;
    }

    private static PeekGlassGeometry.Card card(float[] boxes, int count) {
        return PeekGlassGeometry.compute(boxes, count, 0f, SCREEN_W, HOST_H, SCREEN_W,
            HOST_H + STRIP_H, DENSITY);
    }

    @Test
    public void theIconsCentresStandOnTheCardsTopEdge() {
        float[] boxes = rowBoxes(SCREEN_W, 5, 150, DENSITY);
        PeekGlassGeometry.Card card = card(boxes, 5);
        assertNotNull(card);
        for (int i = 0; i < 5; i++) {
            float centre = (boxes[i * 4 + 1] + boxes[i * 4 + 3]) / 2f;
            assertEquals("icon " + i, centre, card.top, 0.01f);
        }
    }

    @Test
    public void noTwoIconsOverlapAndEveryIconStandsOnTheCard() {
        for (int slots = 3; slots <= 10; slots++) {
            for (int screenW : new int[] {720, 1080, 1440}) {
                float[] boxes = rowBoxes(screenW, slots, 160, DENSITY);
                PeekGlassGeometry.Card card = PeekGlassGeometry.compute(boxes, slots, 0f, screenW,
                    HOST_H, screenW, HOST_H + STRIP_H, DENSITY);
                assertNotNull(card);
                for (int i = 1; i < slots; i++) {
                    assertTrue(slots + " slots on " + screenW + ": icon " + i + " overlaps",
                        boxes[(i - 1) * 4 + 2] <= boxes[i * 4] + 0.01f);
                }
                float clearance = PeekGlassGeometry.ICON_CLEARANCE_DP * DENSITY;
                assertTrue("first icon on the card", boxes[0] >= card.left + clearance - 1f);
                assertTrue("last icon on the card",
                    boxes[(slots - 1) * 4 + 2] <= card.right - clearance + 1f);
            }
        }
    }

    @Test
    public void theCardIsClearOfTheScreensSidesByItsInset() {
        PeekGlassGeometry.Card card = card(rowBoxes(SCREEN_W, 7, 150, DENSITY), 7);
        assertNotNull(card);
        int inset = PeekGlassGeometry.sideInsetPx(SCREEN_W);
        assertEquals("about 3% of the width", Math.round(SCREEN_W * 0.03f), inset);
        assertEquals(inset, card.left, 0.01f);
        assertEquals(SCREEN_W - inset, card.right, 0.01f);
    }

    @Test
    public void aHostAlreadyInsetKeepsTheCardOnTheScreensInset() {
        // A host 10 px in from each side: the card still stands the inset from the screen's sides.
        float[] boxes = rowBoxes(SCREEN_W, 5, 150, DENSITY);
        for (int i = 0; i < 5; i++) {
            boxes[i * 4] -= 10f;
            boxes[i * 4 + 2] -= 10f;
        }
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(boxes, 5, 10f, SCREEN_W - 20,
            HOST_H, SCREEN_W, HOST_H + STRIP_H, DENSITY);
        assertNotNull(card);
        int inset = PeekGlassGeometry.sideInsetPx(SCREEN_W);
        assertEquals(inset, card.left + 10f, 0.01f);
        assertEquals(SCREEN_W - inset, card.right + 10f, 0.01f);
    }

    @Test
    public void theLowerCornersAreNeverOnScreen() {
        PeekGlassGeometry.Card card = card(rowBoxes(SCREEN_W, 7, 150, DENSITY), 7);
        assertNotNull(card);
        assertEquals(HOST_H + STRIP_H, card.screenBottom, 0.01f);
        assertTrue("generous corner", card.radiusPx >= 20f * DENSITY);
        assertTrue("the rounded lower corners start below the screen's edge",
            card.bottom - card.radiusPx >= card.screenBottom);
    }

    @Test
    public void theReflectionsBandStandsOnTheCardBelowTheIcons() {
        float[] boxes = rowBoxes(SCREEN_W, 7, 150, DENSITY);
        PeekGlassGeometry.Card card = card(boxes, 7);
        assertNotNull(card);
        float iconBottom = boxes[3];
        float iconHeight = boxes[3] - boxes[1];
        assertTrue("it has a band to fade over", card.reflectionDepth() > 0f);
        assertTrue("on the card", card.mirrorAxisY >= card.top);
        assertTrue("never past the screen's edge", card.reflectionBottom <= card.screenBottom);
        assertTrue("inside the card", card.reflectionBottom <= card.bottom);
        // Mirrored about the card's top edge (the icons' centres) the picture would land exactly
        // under the icons, so the band hangs from their lower edges instead.
        assertEquals(iconBottom, card.mirrorAxisY, 0.01f);
        assertTrue(card.reflectionDepth() <= iconHeight * PeekGlassGeometry.REFLECTION_DEPTH_SCALE
            + 0.01f);
        for (int i = 0; i < 7; i++) {
            assertTrue(boxes[i * 4] >= card.left && boxes[i * 4 + 2] <= card.right);
        }
    }

    @Test
    public void aShortScreenEdgeCutsTheBandRatherThanRunningPastIt() {
        float[] boxes = rowBoxes(SCREEN_W, 5, 150, DENSITY);
        float iconBottom = boxes[3];
        // Nothing under the host, and the host ends 10 px under the icons.
        int hostH = Math.round(iconBottom) + 10;
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(boxes, 5, 0f, SCREEN_W, hostH,
            SCREEN_W, hostH, DENSITY);
        assertNotNull(card);
        assertEquals(hostH, card.reflectionBottom, 0.5f);
    }

    @Test
    public void noIconsOrNoHostIsNoCard() {
        assertNull(PeekGlassGeometry.compute(new float[0], 0, 0f, SCREEN_W, HOST_H, SCREEN_W,
            HOST_H, DENSITY));
        assertNull(PeekGlassGeometry.compute(rowBoxes(SCREEN_W, 5, 150, DENSITY), 5, 0f, 0,
            HOST_H, SCREEN_W, HOST_H, DENSITY));
    }

    @Test
    public void theReflectionKnobRunsFromOffToStrongWithAVisibleDefault() {
        assertEquals(0f, PeekGlassGeometry.reflectionAlpha(0), 0f);
        assertEquals(PeekGlassGeometry.REFLECTION_MAX_ALPHA,
            PeekGlassGeometry.reflectionAlpha(PeekGlassGeometry.REFLECTION_MAX), 0.0001f);
        float middle = PeekGlassGeometry.reflectionAlpha(PeekGlassGeometry.REFLECTION_DEFAULT);
        assertTrue(middle > 0.2f && middle < PeekGlassGeometry.REFLECTION_MAX_ALPHA);
        assertEquals(100, PeekGlassGeometry.clampReflection(140));
        assertEquals(0, PeekGlassGeometry.clampReflection(-3));
        assertEquals(55, PeekGlassGeometry.clampReflection(53));
    }

    @Test
    public void theDocksRowTakesTheCardsInsetUnderPeekGlassDocked() {
        DockLayout docked = DockLayoutPolicy.compute(inputs(false, DockSurfaceSpec.PEEK_GLASS));
        assertEquals(PeekGlassGeometry.rowSideInsetPx(SCREEN_W, DENSITY), docked.appsRowSideInsetPx());
        assertEquals(docked.appsRowSideInsetPx(), docked.appsRowEdgeInsetPx);
        DockLayout tray = DockLayoutPolicy.compute(inputs(false, DockSurfaceSpec.GLASS_TRAY));
        assertEquals(0, tray.peekRowSideInsetPx);
        DockLayout floating = DockLayoutPolicy.compute(inputs(true, DockSurfaceSpec.PEEK_GLASS));
        assertEquals("Floating draws the tray's slab", 0, floating.peekRowSideInsetPx);
    }

    private static DockLayoutPolicy.DockInputs inputs(boolean capsule, DockSurfaceSpec surface) {
        return DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .appsRowAlone(true)
            .appsRowNextToCanvas(true)
            .surface(surface)
            .density(DENSITY)
            .barHeightScale(1.95f)
            .appsRowEnabledPref(true)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .rowWidthPx(SCREEN_W)
            .slotCount(7)
            .build();
    }
}
