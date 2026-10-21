package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.paging.PageTickStrip;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The Glass tray's geometry against the plain dock's: the tray moves the page dashes out of the
 * icons' air to stand above the slab and leaves the air equal on both sides; the plain dock, which
 * inputs that name no surface get, is exactly what it was for every input. Every page's dock wears
 * its Dock design's spec (#93), so no design maps to the plain dock.
 */
public class DockSurfaceSpecLayoutTest {

    private static final float DENSITY = 2.75f;
    private static final int BASE_TOOLBAR_PX = 103;

    private static DockLayoutPolicy.DockInputs.Builder inputs(boolean capsule, boolean alone,
                                                             boolean nextToCanvas, float preset) {
        return DockLayoutPolicy.DockInputs.builder()
            .capsule(capsule)
            .appsRowAlone(alone)
            .appsRowNextToCanvas(nextToCanvas)
            .density(DENSITY)
            .barHeightScale(preset)
            .configuredCornerRadiusDp(-1)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(!alone)
            .baseToolbarHeightPx(BASE_TOOLBAR_PX);
    }

    /** Every public number of two layouts, field by field, the spec itself aside. */
    private static void assertSameGeometry(String what, DockLayout expected, DockLayout actual)
        throws IllegalAccessException {
        for (Field field : DockLayout.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("surface")) continue;
            assertEquals(what + " " + field.getName(), field.get(expected), field.get(actual));
        }
        assertEquals(what, expected.appsRowViewBandPx(), actual.appsRowViewBandPx());
        assertEquals(what, expected.appsRowPaddingBesideTicksPx(), actual.appsRowPaddingBesideTicksPx());
        assertEquals(what, expected.combinedHeight(BASE_TOOLBAR_PX, true),
            actual.combinedHeight(BASE_TOOLBAR_PX, true));
    }

    @Test
    public void thePlainDockIsTheDockAsItWasForEveryInput() throws IllegalAccessException {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (boolean next : new boolean[] {false, true}) {
                    for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                        float scale = DockLayoutPolicy.sizePreset(preset);
                        DockLayout before = DockLayoutPolicy.compute(
                            inputs(capsule, alone, next, scale).build());
                        DockLayout plain = DockLayoutPolicy.compute(
                            inputs(capsule, alone, next, scale).surface(DockSurfaceSpec.PLAIN).build());
                        assertSameGeometry("capsule=" + capsule + " alone=" + alone + " next=" + next
                            + " preset=" + preset, before, plain);
                        assertSame(DockSurfaceSpec.PLAIN, plain.surface);
                        assertEquals(0, plain.slabTopInsetPx);
                    }
                }
            }
        }
    }

    @Test
    public void noDesignIsThePlainDock() {
        for (DockStyle style : DockStyle.values())
            assertNotSame(style.name(), DockSurfaceSpec.PLAIN, DockSurfaceSpec.forStyle(style));
        assertSame(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.forStyle(DockStyle.GLASS_TRAY));
        assertFalse(DockSurfaceSpec.PLAIN.wearsGlassFeatures());
        assertFalse(DockSurfaceSpec.PLAIN.dashesAboveSlab);
        assertFalse(DockSurfaceSpec.PLAIN.contactShadow);
        assertTrue(DockSurfaceSpec.GLASS_TRAY.wearsGlassFeatures());
    }

    @Test
    public void eachDesignHasItsSpec() {
        assertSame(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.forStyle(DockStyle.GLASS_TRAY));
        assertSame(DockSurfaceSpec.LENS_BEADS, DockSurfaceSpec.forStyle(DockStyle.LENS_BEADS));
        assertSame(DockSurfaceSpec.TASKBAR, DockSurfaceSpec.forStyle(DockStyle.TASKBAR));
        assertSame(DockSurfaceSpec.WALL_STRIP, DockSurfaceSpec.forStyle(DockStyle.WALL_STRIP));
        assertSame(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.forStyle(null));
        assertTrue(DockSurfaceSpec.LENS_BEADS.beads);
        assertFalse(DockSurfaceSpec.GLASS_TRAY.beads);
        assertFalse(DockSurfaceSpec.PLAIN.beads);
        assertTrue(DockSurfaceSpec.LENS_BEADS.wearsGlassFeatures());
        assertTrue(DockSurfaceSpec.LENS_BEADS.iconReflections);
        assertTrue(DockSurfaceSpec.LENS_BEADS.contactShadow);
        assertTrue("a bead's band is tighter than the tray's",
            DockSurfaceSpec.LENS_BEADS.softRimDp < DockSurfaceSpec.GLASS_TRAY.softRimDp);
    }

    /** The Prompt line is the Search field now (#96): a stray stored one draws the tray. */
    @Test
    public void aStrayPromptLineDrawsTheTray() {
        assertSame(DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.forStyle(DockStyle.PROMPT_LINE));
    }

    @Test
    public void theTaskbarIsTheTraysGlassWithTheHomeTileAndALowReflection() {
        assertSame(DockSurfaceSpec.TASKBAR, DockSurfaceSpec.forStyle(DockStyle.TASKBAR));
        DockSurfaceSpec bar = DockSurfaceSpec.TASKBAR;
        DockSurfaceSpec tray = DockSurfaceSpec.GLASS_TRAY;
        assertTrue(bar.taskbar);
        for (DockSurfaceSpec other : new DockSurfaceSpec[] {DockSurfaceSpec.PLAIN, tray,
                DockSurfaceSpec.LENS_BEADS}) {
            assertFalse(other.name, other.taskbar);
            assertEquals(other.name, 1f, other.reflectionScale, 0f);
        }
        assertFalse("one slab, never beads", bar.beads);
        assertEquals(tray.dashesAboveSlab, bar.dashesAboveSlab);
        assertEquals(tray.softRimDp, bar.softRimDp, 0f);
        assertEquals(tray.shelf, bar.shelf, 0f);
        assertTrue(bar.iconReflections);
        assertTrue(bar.contactShadow);
        assertTrue("the reflection is held under the tray's",
            bar.reflectionScale > 0f && bar.reflectionScale < 1f);
    }

    @Test
    public void theWallStripIsTheTraysGlassWithTheRowAndALowerBevelReflection() {
        assertSame(DockSurfaceSpec.WALL_STRIP, DockSurfaceSpec.forStyle(DockStyle.WALL_STRIP));
        DockSurfaceSpec wall = DockSurfaceSpec.WALL_STRIP;
        DockSurfaceSpec tray = DockSurfaceSpec.GLASS_TRAY;
        assertTrue(wall.wallStrip);
        assertTrue("the tiles are never reflected: the lower bevel alone",
            wall.reflectsLowerBevelOnly());
        for (DockSurfaceSpec other : new DockSurfaceSpec[] {DockSurfaceSpec.PLAIN, tray,
                DockSurfaceSpec.LENS_BEADS, DockSurfaceSpec.TASKBAR}) {
            assertFalse(other.name, other.wallStrip);
            assertFalse(other.name, other.reflectsLowerBevelOnly());
        }
        assertFalse("one slab, never beads", wall.beads);
        assertFalse(wall.taskbar);
        assertEquals(tray.dashesAboveSlab, wall.dashesAboveSlab);
        assertEquals(tray.softRimDp, wall.softRimDp, 0f);
        assertEquals(tray.shelf, wall.shelf, 0f);
        assertTrue(wall.iconReflections);
        assertTrue(wall.contactShadow);
        assertTrue(wall.reflectionScale > 0f && wall.reflectionScale <= 1f);
    }

    @Test
    public void onlyLensBeadsInsetTheRowMore() {
        for (boolean capsule : new boolean[] {false, true}) {
            DockLayout beads = DockLayoutPolicy.compute(inputs(capsule, true, true, 2.18f)
                .surface(DockSurfaceSpec.LENS_BEADS).build());
            assertEquals(LensBeadGeometry.rowSideInsetPx(DENSITY), beads.appsRowExtraSideInsetPx());
            assertEquals(Math.round(9f * DENSITY), beads.appsRowExtraSideInsetPx());
            for (DockSurfaceSpec other : new DockSurfaceSpec[] {DockSurfaceSpec.PLAIN,
                    DockSurfaceSpec.GLASS_TRAY}) {
                DockLayout layout = DockLayoutPolicy.compute(inputs(capsule, true, true, 2.18f)
                    .surface(other).promptFieldReveal(1f).build());
                assertEquals(other.name, 0, layout.appsRowExtraSideInsetPx());
            }
        }
    }


    @Test
    public void theBeadsStandOnTheTraysRowGeometry() throws IllegalAccessException {
        // The beads change the glass, not the row: dashes above, equal air, the same icon.
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (boolean next : new boolean[] {false, true}) {
                    for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                        float scale = DockLayoutPolicy.sizePreset(preset);
                        DockLayout tray = DockLayoutPolicy.compute(inputs(capsule, alone, next, scale)
                            .surface(DockSurfaceSpec.GLASS_TRAY).build());
                        DockLayout beads = DockLayoutPolicy.compute(inputs(capsule, alone, next, scale)
                            .surface(DockSurfaceSpec.LENS_BEADS).build());
                        assertSameGeometry("capsule=" + capsule + " alone=" + alone + " next="
                            + next + " preset=" + preset, tray, beads);
                        assertSame(DockSurfaceSpec.LENS_BEADS, beads.surface);
                    }
                }
            }
        }
    }

    @Test
    public void theTrayKeepsEqualAirAboveAndBelowTheIcons() {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                    DockLayout tray = DockLayoutPolicy.compute(inputs(capsule, alone, true,
                        DockLayoutPolicy.sizePreset(preset)).surface(DockSurfaceSpec.GLASS_TRAY).build());
                    String what = "capsule=" + capsule + " alone=" + alone + " preset=" + preset;
                    // The row's own view keeps this above its icons (the ticks lead the row) ...
                    int above = tray.appsRowPaddingBesideTicksPx();
                    // ... and this below them.
                    int below = tray.appsBottomPaddingPx;
                    assertEquals(what, below, above);
                    assertTrue(what, above > 0);
                    // The row's view is the icon and that air, nothing else: the slab.
                    assertEquals(what, tray.appsRowIconPx + above + below, tray.appsBarHeightPx);
                    // The dashes' band stands above it, and the glass starts below the band.
                    assertEquals(what, tray.appsRowStripBandPx, tray.slabTopInsetPx);
                    assertEquals(what, tray.appsRowStripBandPx, tray.indicatorBandHeightPx);
                    assertEquals(what, tray.appsBarHeightPx + tray.slabTopInsetPx, tray.appsRowBandPx);
                    assertEquals(what, tray.appsRowIconPx, tray.appsRowBandHintPx);
                }
            }
        }
    }

    @Test
    public void theTrayDrawsTheSameIconAsThePlainDock() {
        for (boolean alone : new boolean[] {false, true}) {
            for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                float scale = DockLayoutPolicy.sizePreset(preset);
                DockLayout plain = DockLayoutPolicy.compute(inputs(true, alone, true, scale).build());
                DockLayout tray = DockLayoutPolicy.compute(inputs(true, alone, true, scale)
                    .surface(DockSurfaceSpec.GLASS_TRAY).build());
                assertEquals(plain.appsRowIconPx, tray.appsRowIconPx);
                assertEquals(plain.iconScale, tray.iconScale, 0f);
                // The stack grows by the air the dashes no longer stand in, and nothing else.
                assertEquals(plain.appsRowBandPx + (tray.appsBottomPaddingPx
                        + tray.appsRowStripBandPx - plain.appsRowTickSideAirPx),
                    tray.appsRowBandPx);
            }
        }
    }

    @Test
    public void theDashesStandASmallGapOverTheSlab() {
        for (boolean alone : new boolean[] {false, true}) {
            DockLayout tray = DockLayoutPolicy.compute(inputs(true, alone, true,
                DockLayoutPolicy.sizePreset(2)).surface(DockSurfaceSpec.GLASS_TRAY).build());
            float gap = PageTickStrip.edgeGapPx(tray.slabTopInsetPx, DENSITY);
            assertTrue("alone=" + alone + ": " + gap, gap >= 1.5f * DENSITY && gap <= 4f * DENSITY);
        }
    }

    @Test
    public void whereTheTicksStandUnderTheIconsTheTrayIsThePlainDock() throws IllegalAccessException {
        // Not the band next to the canvas: the ticks stand on the row's outer side, under the
        // icons, and there is no slab top to float them over.
        DockLayout plain = DockLayoutPolicy.compute(inputs(true, false, false, 2.18f).build());
        DockLayout tray = DockLayoutPolicy.compute(inputs(true, false, false, 2.18f)
            .surface(DockSurfaceSpec.GLASS_TRAY).build());
        assertSameGeometry("outer ticks", plain, tray);
        // A rail carries its ticks in a column of its own.
        DockLayout railPlain = DockLayoutPolicy.compute(inputs(true, true, true, 2.18f)
            .appsRowOnEdge(true).appsOnRail(true).appsRowPageStripShown(false).build());
        DockLayout railTray = DockLayoutPolicy.compute(inputs(true, true, true, 2.18f)
            .appsRowOnEdge(true).appsOnRail(true).appsRowPageStripShown(false)
            .surface(DockSurfaceSpec.GLASS_TRAY).build());
        assertSameGeometry("rail", railPlain, railTray);
        assertEquals(0, railTray.slabTopInsetPx);
    }
}
