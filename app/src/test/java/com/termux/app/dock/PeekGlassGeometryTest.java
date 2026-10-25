package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals("Floating keeps its own inset", 0, floating.peekRowSideInsetPx);
    }

    // ------------------------------------------------------------------ the Floating card
    //
    // Floating the dock's glass host is the Floating card (inset from the screen, its own rounded
    // corners); the Peek card is that card cut down to the icons' centres.

    /** A Floating host this wide and tall, its row's icons laid out as the row lays them. */
    private static final int FLOAT_HOST_W = 1000;
    private static final int FLOAT_HOST_H = 190;
    /** The Floating card's corners as the host is cut (Corners 26 dp). */
    private static final float FLOAT_RADIUS = 26f * DENSITY;

    private static float[] floatingBoxes(int slots, int icon, float iconTop, float sideInset) {
        float slot = (FLOAT_HOST_W - 2f * sideInset) / slots;
        float[] boxes = new float[slots * 4];
        for (int i = 0; i < slots; i++) {
            float cx = sideInset + slot * (i + 0.5f);
            boxes[i * 4] = cx - icon / 2f;
            boxes[i * 4 + 1] = iconTop;
            boxes[i * 4 + 2] = cx + icon / 2f;
            boxes[i * 4 + 3] = iconTop + icon;
        }
        return boxes;
    }

    private static PeekGlassGeometry.Card floatingCard(float[] boxes, int count) {
        return PeekGlassGeometry.computeFloating(boxes, count, FLOAT_HOST_W, FLOAT_HOST_H,
            FLOAT_RADIUS);
    }

    @Test
    public void floatingTheCardIsTheFloatingCardCutToTheIconsCentres() {
        float[] boxes = floatingBoxes(6, 129, 30f, 40f);
        PeekGlassGeometry.Card card = floatingCard(boxes, 6);
        assertNotNull(card);
        assertTrue(card.floating);
        assertEquals("its sides are the Floating card's", 0f, card.left, 0f);
        assertEquals(FLOAT_HOST_W, card.right, 0f);
        assertEquals("its lower edge is the Floating card's own", FLOAT_HOST_H, card.bottom, 0f);
        assertEquals("the icons' centres on its top edge", 30f + 129 / 2f, card.top, 0.01f);
        assertEquals("the reflection's floor is the card's own lower edge", card.bottom,
            card.screenBottom, 0f);
    }

    @Test
    public void floatingItsCornersAreTheFloatingCardsHeldToHalfTheCutCard() {
        // Tall enough: the Floating card's own radius.
        float[] tall = floatingBoxes(5, 60, 10f, 40f);
        PeekGlassGeometry.Card roomy = floatingCard(tall, 5);
        assertNotNull(roomy);
        assertTrue(roomy.bottom - roomy.top >= 2f * FLOAT_RADIUS);
        assertEquals(FLOAT_RADIUS, roomy.radiusPx, 0.001f);
        // Cut short: half of what is left, so the rounded rect is never past its own height.
        float[] low = floatingBoxes(5, 129, 70f, 40f);
        PeekGlassGeometry.Card shallow = floatingCard(low, 5);
        assertNotNull(shallow);
        float height = shallow.bottom - shallow.top;
        assertTrue(height < 2f * FLOAT_RADIUS);
        assertEquals(height / 2f, shallow.radiusPx, 0.001f);
        // A square Floating card stays square.
        PeekGlassGeometry.Card square = PeekGlassGeometry.computeFloating(low, 5, FLOAT_HOST_W,
            FLOAT_HOST_H, 0f);
        assertNotNull(square);
        assertEquals(0f, square.radiusPx, 0f);
    }

    @Test
    public void floatingTheReflectionHangsFromTheIconsAndStopsAtTheCardsLowerEdge() {
        // Room under the icons for the whole band.
        float[] boxes = floatingBoxes(6, 100, 10f, 40f);
        PeekGlassGeometry.Card card = floatingCard(boxes, 6);
        assertNotNull(card);
        assertEquals("from the icons' lower edges", 110f, card.mirrorAxisY, 0.01f);
        assertEquals(100f * PeekGlassGeometry.REFLECTION_DEPTH_SCALE, card.reflectionDepth(),
            0.01f);
        // Little room: the band ends at the card's lower edge, never past it.
        float[] low = floatingBoxes(6, 129, 40f, 40f);
        PeekGlassGeometry.Card tight = floatingCard(low, 6);
        assertNotNull(tight);
        assertEquals(FLOAT_HOST_H, tight.reflectionBottom, 0.01f);
        assertEquals(FLOAT_HOST_H - (40f + 129f), tight.reflectionDepth(), 0.01f);
        // Drawn on the host it is wholly the host's: no share left for any strip under it.
        float[] band = new float[8];
        assertTrue(PeekGlassGeometry.reflectionBandIn(tight, 0f, 0f, FLOAT_HOST_W, FLOAT_HOST_H,
            band));
        assertTrue(band[3] <= FLOAT_HOST_H + 0.5f);
        assertEquals(0f, PeekGlassGeometry.reflectionProgress(band, band[5]), 0f);
        assertEquals(1f, PeekGlassGeometry.reflectionProgress(band, band[3]), 0f);
    }

    @Test
    public void floatingTheEndIconsStandOnTheCardWithTheRowsOwnInset() {
        // The row's Floating inset, measured from the screen's edge, against a host inset by the
        // Floating margin: the outermost icons stand inside the card's sides.
        DockLayout floating = DockLayoutPolicy.compute(inputs(true, DockSurfaceSpec.PEEK_GLASS));
        int hostW = SCREEN_W - 2 * floating.capsuleHorizontalMarginPx;
        float rowInsetInHost = floating.appsRowSideInsetPx() - floating.capsuleHorizontalMarginPx;
        assertTrue("the row stands in from the card's sides", rowInsetInHost > 0f);
        int slots = 7;
        float slot = (hostW - 2f * rowInsetInHost) / slots;
        int icon = floating.slotIconPx;
        float[] boxes = new float[slots * 4];
        for (int i = 0; i < slots; i++) {
            float cx = rowInsetInHost + slot * (i + 0.5f);
            boxes[i * 4] = cx - icon / 2f;
            boxes[i * 4 + 1] = 20f;
            boxes[i * 4 + 2] = cx + icon / 2f;
            boxes[i * 4 + 3] = 20f + icon;
        }
        PeekGlassGeometry.Card card = PeekGlassGeometry.computeFloating(boxes, slots, hostW,
            Math.round(20f + icon + 30f), FLOAT_RADIUS);
        assertNotNull(card);
        assertTrue(boxes[0] >= card.left);
        assertTrue(boxes[(slots - 1) * 4 + 2] <= card.right);
    }

    @Test
    public void floatingNoIconsNoHostOrIconsBelowTheCardIsNoCard() {
        assertNull(PeekGlassGeometry.computeFloating(new float[0], 0, FLOAT_HOST_W, FLOAT_HOST_H,
            FLOAT_RADIUS));
        float[] boxes = floatingBoxes(5, 100, 10f, 40f);
        assertNull(PeekGlassGeometry.computeFloating(boxes, 5, 0, FLOAT_HOST_H, FLOAT_RADIUS));
        assertNull(PeekGlassGeometry.computeFloating(boxes, 5, FLOAT_HOST_W, 0, FLOAT_RADIUS));
        // The icons' centres at the host's lower edge: no card is left under them.
        float[] under = floatingBoxes(5, 100, FLOAT_HOST_H - 50f, 40f);
        assertNull(PeekGlassGeometry.computeFloating(under, 5, FLOAT_HOST_W, FLOAT_HOST_H,
            FLOAT_RADIUS));
    }

    @Test
    public void theDockedCardIsNotFloatingAndTheTwoNeverCompareEqual() {
        PeekGlassGeometry.Card docked = card(rowBoxes(SCREEN_W, 7, 150, DENSITY), 7);
        assertNotNull(docked);
        assertFalse(docked.floating);
        float[] boxes = floatingBoxes(5, 100, 10f, 40f);
        PeekGlassGeometry.Card floating = floatingCard(boxes, 5);
        assertNotNull(floating);
        assertTrue("an offset card is still the Floating one", floating.offset(3f, 4f).floating);
        assertEquals(floating, floating.offset(0f, 0f));
        assertFalse(floating.equals(docked));
    }

    @Test
    public void theSlabDrawsWithTheCardsSpec() {
        // No card: the tray's slab, shelf and shadow included.
        assertEquals(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.PEEK_GLASS.forCard(null));
        // Docked: exactly as before, no shelf and no shadow.
        PeekGlassGeometry.Card docked = card(rowBoxes(SCREEN_W, 7, 150, DENSITY), 7);
        assertEquals(DockSurfaceSpec.PEEK_GLASS, DockSurfaceSpec.PEEK_GLASS.forCard(docked));
        // Floating: the card floats as the tray does, so its lower edge wears the tray's shelf
        // and casts its shadow; the rest is Peek's.
        DockSurfaceSpec floating = DockSurfaceSpec.PEEK_GLASS.forCard(
            floatingCard(floatingBoxes(5, 100, 10f, 40f), 5));
        assertEquals(DockSurfaceSpec.PEEK_GLASS_FLOATING, floating);
        assertTrue(floating.peek);
        assertEquals(DockSurfaceSpec.GLASS_TRAY.shelf, floating.shelf, 0f);
        assertTrue(floating.contactShadow);
        assertEquals(DockSurfaceSpec.PEEK_GLASS.softRimDp, floating.softRimDp, 0f);
        assertEquals(DockSurfaceSpec.PEEK_GLASS.dashesAboveSlab, floating.dashesAboveSlab);
        // Other designs are themselves, card or none.
        assertEquals(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.GLASS_TRAY.forCard(docked));
        assertEquals(DockSurfaceSpec.LENS_BEADS, DockSurfaceSpec.LENS_BEADS.forCard(null));
    }

    // ------------------------------------------------------------------ the band on two surfaces
    //
    // Pong (1080 x 2400, density 2.625, gesture navigation) as its screenshot measures it: seven
    // 129 px icons whose lower edges stand 29 px above the host's bottom, a 63 px strip under the
    // pill. The band is 0.6 x 129 = 77 px deep, so it hangs 29 px on the host and 48 px on the
    // strip: the reflection's strongest rows are the host's, and the two must read as one band.

    private static final int PONG_ICON = 129;
    private static final int PONG_HOST_H = 180;
    private static final int PONG_STRIP_H = 63;

    private static float[] pongBoxes() {
        float top = PONG_HOST_H - 29 - PONG_ICON;
        int inset = PeekGlassGeometry.rowSideInsetPx(SCREEN_W, DENSITY);
        float slot = (SCREEN_W - 2f * inset) / 7f;
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = inset + slot * (i + 0.5f);
            boxes[i * 4] = cx - PONG_ICON / 2f;
            boxes[i * 4 + 1] = top;
            boxes[i * 4 + 2] = cx + PONG_ICON / 2f;
            boxes[i * 4 + 3] = top + PONG_ICON;
        }
        return boxes;
    }

    private static PeekGlassGeometry.Card pongCard() {
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(pongBoxes(), 7, 0f, SCREEN_W,
            PONG_HOST_H, SCREEN_W, PONG_HOST_H + PONG_STRIP_H, DENSITY);
        assertNotNull(card);
        return card;
    }

    /** The strip under the pill: inset as the card is, its origin at the host's bottom. */
    private static float[] stripBand(PeekGlassGeometry.Card card, boolean[] shows) {
        int inset = PeekGlassGeometry.sideInsetPx(SCREEN_W);
        float[] band = new float[8];
        shows[0] = PeekGlassGeometry.reflectionBandIn(card, inset, PONG_HOST_H,
            SCREEN_W - 2 * inset, PONG_STRIP_H, band);
        return band;
    }

    @Test
    public void onPongTheBandHangsAcrossTheSeamWithItsStrongestRowsOnTheHost() {
        PeekGlassGeometry.Card card = pongCard();
        assertEquals(PONG_HOST_H - 29, card.mirrorAxisY, 0.01f);
        assertEquals(77, card.bandHeightPx());
        float[] host = new float[8];
        assertTrue("the host shows the band's top",
            PeekGlassGeometry.reflectionBandIn(card, 0f, 0f, SCREEN_W, PONG_HOST_H, host));
        assertEquals(card.bandTopPx(), host[5], 0f);
        assertEquals("the host shows the band down to its own bottom", PONG_HOST_H, host[7], 0f);
        assertEquals(0f, PeekGlassGeometry.reflectionProgress(host, host[5]), 0f);
        boolean[] shows = new boolean[1];
        float[] strip = stripBand(card, shows);
        assertTrue("the strip shows the rest", shows[0]);
        assertEquals(0f, strip[5], 0f);
        assertEquals(card.bandTopPx() + card.bandHeightPx() - PONG_HOST_H, strip[7], 0f);
    }

    @Test
    public void theFadeRunsOnUnbrokenFromTheHostIntoTheStrip() {
        PeekGlassGeometry.Card card = pongCard();
        float[] host = new float[8];
        PeekGlassGeometry.reflectionBandIn(card, 0f, 0f, SCREEN_W, PONG_HOST_H, host);
        boolean[] shows = new boolean[1];
        float[] strip = stripBand(card, shows);
        // The host's last row and the strip's first are one row of the band apart, at any column.
        float atSeamOnHost = PeekGlassGeometry.reflectionProgress(host, PONG_HOST_H);
        float atSeamOnStrip = PeekGlassGeometry.reflectionProgress(strip, 0f);
        assertEquals(atSeamOnHost, atSeamOnStrip, 1e-5f);
        assertEquals(29f / 77f, atSeamOnStrip, 1e-4f);
        // The old program measured the fade from each drawing's own top: both read 0 there.
        assertTrue("the seam is part way down the band, not a fresh start",
            atSeamOnStrip > 0.3f);
        // Side by side the two surfaces put the band in the same place on screen.
        int inset = PeekGlassGeometry.sideInsetPx(SCREEN_W);
        assertEquals(host[0], strip[0] + inset, 0f);
        assertEquals(host[1], strip[1] + PONG_HOST_H, 0f);
    }

    @Test
    public void eachSurfaceHoldsItsBlurToItsOwnShareOfTheBand() {
        PeekGlassGeometry.Card card = pongCard();
        float[] host = new float[8];
        PeekGlassGeometry.reflectionBandIn(card, 0f, 0f, SCREEN_W, PONG_HOST_H, host);
        assertTrue(host[5] >= 0f && host[7] <= PONG_HOST_H);
        assertTrue(host[4] >= host[0] && host[6] <= host[2]);
        boolean[] shows = new boolean[1];
        float[] strip = stripBand(card, shows);
        assertTrue(strip[5] >= 0f && strip[7] <= PONG_STRIP_H);
        assertTrue(strip[4] >= 0f && strip[6] <= SCREEN_W - 2 * PeekGlassGeometry.sideInsetPx(SCREEN_W));
    }

    @Test
    public void aSurfaceWithNoSizeOrBelowTheBandShowsNone() {
        PeekGlassGeometry.Card card = pongCard();
        float[] band = new float[8];
        assertFalse("not laid out yet",
            PeekGlassGeometry.reflectionBandIn(card, 0f, 0f, 0, 0, band));
        assertFalse("wholly under the band's end",
            PeekGlassGeometry.reflectionBandIn(card, 0f, card.bandTopPx() + card.bandHeightPx(),
                SCREEN_W, 40, band));
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
