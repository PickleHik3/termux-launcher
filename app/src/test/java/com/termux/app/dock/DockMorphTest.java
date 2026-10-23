package com.termux.app.dock;

import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Side;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The dock's dress across a crossing (ADR 0012, decision 3), read as the numbers the user sees:
 * the slot's icon, the row's width, the bands and their fades. Built from Dock geometry's own rests
 * for halves that dress the dock differently, across the shipped densities and both Styles.
 */
public class DockMorphTest {

    private static final float[] DENSITIES = {1f, 2.625f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};
    private static final int WIDTH_DP = 411;
    /** A slow drag's step. */
    private static final float STEP = 0.02f;

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Home layout: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    /** Home on Lens beads with a Search field, the Workspace on the Tray: the dresses differ. */
    private static Snapshot device(float density, boolean floating) {
        Half home = Half.builder(HOME).floating(floating).design(DockStyle.LENS_BEADS)
            .searchField(true).build();
        Half workspace = Half.builder(WINDOW).floating(floating).design(DockStyle.GLASS_TRAY)
            .build();
        return Snapshot.builder(home, workspace)
            .density(density)
            .widthPx(Math.round(WIDTH_DP * density))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .pinnedSlotCount(7)
            .build();
    }

    private static DockLayout rest(Snapshot snapshot, Side side) {
        return DockGeometry.compute(snapshot.restOf(side));
    }

    private static String at(float density, boolean floating) {
        return "density " + density + (floating ? " floating" : " docked");
    }

    /** Every int the morph answers, in one order, for comparing frames. */
    private static int[] numbers(DockMorph m) {
        return new int[] {m.slotIconPx(), m.appsRowIconPx(), m.appsRowUsableWidthPx(),
            m.appsRowEdgeInsetPx(), m.appsBarHeightPx(), m.azRowHeightPx(),
            m.indicatorBandHeightPx(), m.promptFieldBandPx(), m.wallStripBandPx(), m.rowsPx()};
    }

    private static int[] numbers(DockLayout l) {
        return new int[] {l.slotIconPx, l.appsRowIconPx, l.appsRowUsableWidthPx,
            l.appsRowEdgeInsetPx, l.appsBarHeightPx, l.azRowHeightPx, l.indicatorBandHeightPx,
            l.promptFieldBandPx, l.wallStripBandPx, DockMorph.rowsPx(l)};
    }

    @Test
    public void theHalvesUnderTestDressTheDockDifferently() {
        for (float density : DENSITIES)
            for (boolean floating : STYLES) {
                Snapshot s = device(density, floating);
                DockLayout home = rest(s, Side.HOME);
                DockLayout workspace = rest(s, Side.WORKSPACE);
                String at = at(density, floating);
                assertTrue(at + ": Home carries the Search field", home.promptFieldBandPx > 0);
                assertEquals(at + ": the Workspace has none", 0, workspace.promptFieldBandPx);
                assertNotEquals(at + ": the rows differ", DockMorph.rowsPx(home),
                    DockMorph.rowsPx(workspace));
            }
    }

    @Test
    public void atZeroAndOneTheMorphIsEachRest() {
        for (float density : DENSITIES)
            for (boolean floating : STYLES)
                for (boolean fromHome : new boolean[] {true, false}) {
                    Snapshot s = device(density, floating);
                    DockLayout leaving = rest(s, fromHome ? Side.HOME : Side.WORKSPACE);
                    DockLayout arriving = rest(s, fromHome ? Side.WORKSPACE : Side.HOME);
                    DockMorph morph = new DockMorph();
                    morph.between(leaving, fromHome, arriving, !fromHome);
                    String at = at(density, floating) + (fromHome ? " from Home" : " to Home");
                    assertArrayEquals(at + " at 0", numbers(leaving), numbers(morph));
                    assertEquals(at + ": the leaving tile, on Home only", fromHome ? 1f : 0f,
                        morph.drawerTileAlpha(), 0f);
                    morph.at(1f);
                    assertArrayEquals(at + " at 1", numbers(arriving), numbers(morph));
                    assertEquals(at + ": the arriving tile, on Home only", fromHome ? 0f : 1f,
                        morph.drawerTileAlpha(), 0f);
                    // Every band's presence is the arriving rest's, and was the leaving one's.
                    org.junit.Assert.assertArrayEquals(at + " presence at 1", presence(arriving),
                        presence(morph), 0f);
                    morph.at(0f);
                    org.junit.Assert.assertArrayEquals(at + " presence at 0", presence(leaving),
                        presence(morph), 0f);
                }
    }

    /** Whether each band shows, as 1 or 0, in the order {@link #presence(DockMorph)} reads. */
    private static float[] presence(DockLayout l) {
        boolean apps = l.appsBarHeightPx > 0;
        return new float[] {apps ? 1f : 0f, l.azRowHeightPx > 0 ? 1f : 0f,
            apps && l.indicatorBandHeightPx > 0 ? 1f : 0f, l.promptFieldBandPx > 0 ? 1f : 0f,
            l.wallStripBandPx > 0 ? 1f : 0f, l.railActive ? 1f : 0f};
    }

    private static float[] presence(DockMorph m) {
        return new float[] {m.appsAlpha(), m.azAlpha(), m.indicatorAlpha(), m.searchFieldAlpha(),
            m.wallStripAlpha(), m.railAlpha()};
    }

    @Test
    public void theRowsHeightIsAlwaysTheSumOfItsBands() {
        for (float density : DENSITIES)
            for (boolean floating : STYLES) {
                Snapshot s = device(density, floating);
                DockMorph morph = new DockMorph();
                morph.between(rest(s, Side.HOME), true, rest(s, Side.WORKSPACE), false);
                for (float t = 0f; t <= 1f + 1e-6f; t += STEP) {
                    morph.at(t);
                    int bands = morph.appsBarHeightPx() + morph.promptFieldBandPx()
                        + morph.wallStripBandPx() + morph.indicatorBandHeightPx()
                        + morph.azRowHeightPx();
                    assertEquals(at(density, floating) + " at " + t, bands, morph.rowsPx());
                }
            }
    }

    @Test
    public void againstARailOnlyTheBandsMorphAndTheRowDrawsAsLaidOut() {
        float density = 2.625f;
        Half home = Half.builder(HOME).design(DockStyle.GLASS_TRAY).build();
        PlaceLayout railWindow = WINDOW.withSlot(com.termux.app.place.Element.APPS,
            com.termux.app.place.Slot.on(Edge.LEFT, com.termux.app.place.Element.APPS));
        Half workspace = Half.builder(railWindow).design(DockStyle.GLASS_TRAY).build();
        Snapshot s = Snapshot.builder(home, workspace)
            .density(density)
            .widthPx(Math.round(WIDTH_DP * density))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .pinnedSlotCount(7)
            .build();
        DockLayout row = rest(s, Side.HOME);
        DockLayout rail = rest(s, Side.WORKSPACE);
        assertFalse(row.railActive);
        assertTrue(rail.railActive);
        DockMorph morph = new DockMorph();
        morph.between(row, false, rail, false);
        for (float t = 0f; t <= 1f + 1e-6f; t += STEP) {
            morph.at(t);
            assertFalse(morph.rowMorphs());
            assertEquals("the row's icon stays its own at " + t, row.slotIconPx,
                morph.slotIconPx());
            assertEquals(row.appsRowUsableWidthPx, morph.appsRowUsableWidthPx());
            assertEquals(row.appsRowEdgeInsetPx, morph.appsRowEdgeInsetPx());
            assertEquals("the rail fades in", t, morph.railAlpha(), 1e-6f);
        }
        assertEquals("the bands still land on the rail's rest", DockMorph.rowsPx(rail),
            morph.rowsPx());
    }

    @Test
    public void aSlotKeepsItsShareOfTheRowAndItsIconScalesToTheFrame() {
        // A row laid out 40 px in and 800 px wide, drawn 60 px in and 600 px wide.
        assertEquals(20f, DockMorph.slotCentreX(0f, 40, 800, 60, 600), 1e-4f);
        assertEquals(20f + 300f, DockMorph.slotCentreX(400f, 40, 800, 60, 600), 1e-4f);
        assertEquals(20f + 600f, DockMorph.slotCentreX(800f, 40, 800, 60, 600), 1e-4f);
        // The same rest draws every slot where it is laid out.
        assertEquals(123f, DockMorph.slotCentreX(123f, 40, 800, 40, 800), 0f);
        assertEquals(123f, DockMorph.slotCentreX(123f, 40, 0, 60, 600), 0f);
        assertEquals(118f / 143f, DockMorph.iconScale(118, 143), 1e-6f);
        assertEquals(1f, DockMorph.iconScale(143, 143), 0f);
        assertEquals(1f, DockMorph.iconScale(0, 143), 0f);
    }

    @Test
    public void everyMeasurementIsMonotonicInTheOffset() {
        for (float density : DENSITIES)
            for (boolean floating : STYLES) {
                Snapshot s = device(density, floating);
                DockLayout leaving = rest(s, Side.WORKSPACE);
                DockLayout arriving = rest(s, Side.HOME);
                DockMorph morph = new DockMorph();
                morph.between(leaving, false, arriving, true);
                int[] from = numbers(leaving);
                int[] to = numbers(arriving);
                int[] last = numbers(morph);
                float lastField = morph.searchFieldAlpha();
                for (float t = STEP; t <= 1f + 1e-6f; t += STEP) {
                    morph.at(t);
                    int[] now = numbers(morph);
                    for (int i = 0; i < now.length; i++) {
                        int direction = Integer.signum(to[i] - from[i]);
                        int moved = Integer.signum(now[i] - last[i]);
                        assertTrue(at(density, floating) + " measurement " + i + " at " + t,
                            moved == 0 || moved == direction);
                    }
                    assertTrue("the Search field fades in", morph.searchFieldAlpha() >= lastField);
                    last = now;
                    lastField = morph.searchFieldAlpha();
                }
            }
    }

    @Test
    public void aReversalRunsTheSameNumbersBack() {
        Snapshot s = device(2.625f, true);
        DockMorph morph = new DockMorph();
        morph.between(rest(s, Side.HOME), true, rest(s, Side.WORKSPACE), false);
        int frames = Math.round(1f / STEP);
        int[][] out = new int[frames + 1][];
        for (int i = 0; i <= frames; i++) {
            morph.at(i * STEP);
            out[i] = numbers(morph);
        }
        for (int i = frames; i >= 0; i--) {
            morph.at(i * STEP);
            assertArrayEquals("frame " + i + " on the way back", out[i], numbers(morph));
        }
    }

    @Test
    public void aBandOnOneSideOnlyGrowsFromNothingAndFades() {
        Snapshot s = device(3f, false);
        DockLayout workspace = rest(s, Side.WORKSPACE);
        DockLayout home = rest(s, Side.HOME);
        DockMorph morph = new DockMorph();
        morph.between(workspace, false, home, false);
        assertEquals(0, morph.promptFieldBandPx());
        assertEquals(0f, morph.searchFieldAlpha(), 0f);
        morph.at(0.5f);
        assertTrue(morph.promptFieldBandPx() > 0);
        assertTrue(morph.promptFieldBandPx() < home.promptFieldBandPx);
        assertEquals(0.5f, morph.searchFieldAlpha(), 1e-6f);
        // A band both sides carry, and the Drawer tile neither does, never fade.
        assertEquals(1f, morph.appsAlpha(), 0f);
        assertEquals(0f, morph.drawerTileAlpha(), 0f);
        assertEquals(0f, morph.wallStripAlpha(), 0f);
    }

    @Test
    public void clearedItHoldsNothing() {
        Snapshot s = device(2.625f, false);
        DockMorph morph = new DockMorph();
        morph.between(rest(s, Side.HOME), true, rest(s, Side.WORKSPACE), false);
        morph.at(0.4f);
        morph.clear();
        assertFalse(morph.active());
        assertEquals(0, morph.slotIconPx());
        assertEquals(0f, morph.fraction(), 0f);
        // A frame with no rests changes nothing.
        morph.at(0.7f);
        assertEquals(0, morph.rowsPx());
    }

    private static void assertArrayEquals(String message, int[] expected, int[] actual) {
        org.junit.Assert.assertArrayEquals(message, expected, actual);
    }
}
