package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The Wall strip's two-row slab: the tray's apps row untouched, the 84 dp first row over it on the
 * same glass, the slab's air over the row equal to the air under the icons (equal outer padding),
 * the tiles nesting by that air, and the stack taller by the row's band and nothing else. The band
 * eases with the reveal; at none it is the tray.
 */
public class WallStripSlabLayoutTest {

    private static final float DENSITY = 2.75f;
    private static final int BASE_TOOLBAR_PX = 103;

    private static DockLayoutPolicy.DockInputs.Builder inputs(boolean capsule, boolean alone,
                                                             float preset) {
        return DockLayoutPolicy.DockInputs.builder()
            .capsule(capsule)
            .appsRowAlone(alone)
            .appsRowNextToCanvas(true)
            .density(DENSITY)
            .barHeightScale(preset)
            .configuredCornerRadiusDp(-1)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(!alone)
            .baseToolbarHeightPx(BASE_TOOLBAR_PX);
    }

    private static DockLayout tray(boolean capsule, boolean alone, float preset) {
        return DockLayoutPolicy.compute(inputs(capsule, alone, preset)
            .surface(DockSurfaceSpec.GLASS_TRAY).build());
    }

    private static DockLayout wall(boolean capsule, boolean alone, float preset, float reveal) {
        return DockLayoutPolicy.compute(inputs(capsule, alone, preset)
            .surface(DockSurfaceSpec.WALL_STRIP).wallStripReveal(reveal).build());
    }

    private static boolean isWallStrip(Field field) {
        return field.getName().startsWith("wallStrip");
    }

    @Test
    public void theRowIsTheViewItStandsIn() {
        assertEquals(WallStripRowView.HEIGHT_DP, DockLayoutPolicy.WALL_STRIP_HEIGHT_DP, 0f);
        assertEquals(84f, DockLayoutPolicy.WALL_STRIP_HEIGHT_DP, 0f);
        assertEquals(14f, DockLayoutPolicy.WALL_STRIP_SIDE_INSET_DP, 0f);
    }

    @Test
    public void theAppsRowIsTheTraysAndOnlyTheRowIsAdded() throws IllegalAccessException {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                    float scale = DockLayoutPolicy.sizePreset(preset);
                    DockLayout tray = tray(capsule, alone, scale);
                    DockLayout wall = wall(capsule, alone, scale, 1f);
                    String what = "capsule=" + capsule + " alone=" + alone + " preset=" + preset;
                    for (Field field : DockLayout.class.getFields()) {
                        if (Modifier.isStatic(field.getModifiers())
                            || field.getName().equals("surface") || isWallStrip(field)) continue;
                        assertEquals(what + " " + field.getName(), field.get(tray), field.get(wall));
                    }
                    assertEquals(what, 0, wall.promptFieldBandPx);
                    assertEquals(what, tray.combinedHeight(BASE_TOOLBAR_PX, false)
                        + wall.wallStripBandPx, wall.combinedHeight(BASE_TOOLBAR_PX, false));
                    assertEquals(what, tray.combinedHeight(BASE_TOOLBAR_PX, true)
                        + wall.wallStripBandPx, wall.combinedHeight(BASE_TOOLBAR_PX, true));
                }
            }
        }
    }

    @Test
    public void theRowIsEightyFourDpWithEqualOuterPadding() {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                    DockLayout wall = wall(capsule, alone, DockLayoutPolicy.sizePreset(preset), 1f);
                    String what = "capsule=" + capsule + " alone=" + alone + " preset=" + preset;
                    assertEquals(what, Math.round(84f * DENSITY), wall.wallStripHeightPx);
                    assertEquals(what, wall.wallStripHeightPx + wall.wallStripTopPadPx,
                        wall.wallStripBandPx);
                    assertEquals(what, wall.wallStripBandPx, wall.wallStripFullBandPx);
                    // The slab's air over the row (the dashes stand above the slab) ...
                    int top = wall.wallStripTopPadPx;
                    // ... is the air under the icons, to the slab's lower edge.
                    assertEquals(what, wall.appsBottomPaddingPx, top);
                    // The gap between the tiles and the icons is the air over the icons.
                    assertEquals(what, wall.appsRowPaddingBesideTicksPx(), top);
                    assertEquals(what, top, wall.wallStripWellGapPx);
                    assertTrue(what, top > 0);
                }
            }
        }
    }

    @Test
    public void theRowKeepsTheAppsRowsFourteenDpInsideTheSlab() {
        DockLayout floating = wall(true, true, 2.18f, 1f);
        assertEquals(floating.capsuleContentInsetPx, floating.wallStripSideInsetPx);
        assertEquals(floating.horizontalInsetPx + Math.round(14f * DENSITY),
            floating.wallStripSideInsetPx);
        DockLayout docked = wall(false, true, 2.18f, 1f);
        assertEquals(0, docked.horizontalInsetPx);
        assertEquals(Math.round(14f * DENSITY), docked.wallStripSideInsetPx);
    }

    @Test
    public void noRevealIsTheTray() throws IllegalAccessException {
        for (boolean capsule : new boolean[] {false, true}) {
            DockLayout tray = tray(capsule, true, 2.18f);
            DockLayout wall = wall(capsule, true, 2.18f, 0f);
            for (Field field : DockLayout.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("surface"))
                    continue;
                assertEquals(field.getName(), field.get(tray), field.get(wall));
            }
            assertEquals(0, wall.wallStripBandPx);
        }
        assertEquals(0, wall(true, true, 2.18f, -1f).wallStripBandPx);
        assertEquals(0, wall(true, true, 2.18f, Float.NaN).wallStripBandPx);
        assertEquals(wall(true, true, 2.18f, 1f).wallStripBandPx,
            wall(true, true, 2.18f, 3f).wallStripBandPx);
    }

    @Test
    public void theBandEasesWithTheReveal() {
        int previous = -1;
        int full = wall(true, true, 2.18f, 1f).wallStripFullBandPx;
        for (int step = 0; step <= 20; step++) {
            float reveal = step / 20f;
            DockLayout wall = wall(true, true, 2.18f, reveal);
            assertTrue("reveal " + reveal, wall.wallStripBandPx >= previous);
            assertTrue(wall.wallStripBandPx <= full);
            assertTrue(wall.wallStripHeightPx <= wall.wallStripBandPx);
            assertEquals(wall.wallStripBandPx, wall.wallStripHeightPx + wall.wallStripTopPadPx);
            if (step > 0) {
                assertEquals(full, wall.wallStripFullBandPx);
                assertEquals(wall(true, true, 2.18f, 1f).wallStripWellGapPx,
                    wall.wallStripWellGapPx);
            }
            previous = wall.wallStripBandPx;
        }
        assertEquals(full, previous);
    }

    @Test
    public void aSwitchFromThePromptLineCarriesBothBandsAtOnce() {
        // Mid-switch both bands are laid out, each at its own share; the stack holds both.
        DockLayout mid = DockLayoutPolicy.compute(inputs(true, true, 2.18f)
            .surface(DockSurfaceSpec.WALL_STRIP).promptFieldReveal(0.5f).wallStripReveal(0.5f)
            .build());
        DockLayout tray = tray(true, true, 2.18f);
        assertTrue(mid.promptFieldBandPx > 0);
        assertTrue(mid.wallStripBandPx > 0);
        assertEquals(tray.combinedHeight(BASE_TOOLBAR_PX, false) + mid.promptFieldBandPx
            + mid.wallStripBandPx, mid.combinedHeight(BASE_TOOLBAR_PX, false));
    }

    @Test
    public void onlyTheDocksOwnRowCarriesTheStrip() {
        DockLayout top = DockLayoutPolicy.compute(inputs(true, true, 2.18f).appsRowOnEdge(true)
            .surface(DockSurfaceSpec.WALL_STRIP).wallStripReveal(1f).build());
        assertEquals(0, top.wallStripBandPx);
        DockLayout none = DockLayoutPolicy.compute(inputs(true, true, 2.18f)
            .appsRowEnabledPref(false).surface(DockSurfaceSpec.WALL_STRIP).wallStripReveal(1f)
            .build());
        assertEquals(0, none.wallStripBandPx);
        assertEquals(0, none.combinedHeight(BASE_TOOLBAR_PX, false));
        DockLayout noStore = DockLayoutPolicy.compute(inputs(true, true, 2.18f)
            .preferencesAvailable(false).surface(DockSurfaceSpec.WALL_STRIP)
            .wallStripReveal(1f).build());
        assertEquals(0, noStore.wallStripBandPx);
    }

    @Test
    public void theTilesNestInTheSlab() {
        DockLayout wall = wall(true, true, 2.18f, 1f);
        float dockRadius = wall.capsuleCornerRadiusPx(wall.appsBarHeightPx + wall.wallStripBandPx);
        float tile = WallStripRowPolicy.tileRadiusPx(dockRadius, wall.wallStripWellGapPx);
        assertEquals(dockRadius - wall.wallStripWellGapPx, tile, 1e-3f);
        assertTrue(tile > 0f);
    }
}
