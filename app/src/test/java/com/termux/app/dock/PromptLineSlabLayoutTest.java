package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The Search field's taller slab (once the Prompt line design's, #96): the apps row untouched,
 * then the palette's field under it on the same glass, with any Dock design, as wide as the row's 14 dp inset leaves it, the well standing as
 * far from the slab's lower edge as the icons do from its upper one, and the stack taller by the
 * field's band and nothing else. The band eases with the reveal; at none it is the tray.
 */
public class PromptLineSlabLayoutTest {

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

    private static DockLayout line(boolean capsule, boolean alone, float preset, float reveal) {
        return DockLayoutPolicy.compute(inputs(capsule, alone, preset)
            .surface(DockSurfaceSpec.GLASS_TRAY).promptFieldReveal(reveal).build());
    }

    private static boolean isPromptField(Field field) {
        return field.getName().startsWith("promptField");
    }

    @Test
    public void theFieldIsTheViewItStandsIn() {
        assertEquals(PromptLineFieldView.HIT_HEIGHT_DP, DockLayoutPolicy.PROMPT_FIELD_HEIGHT_DP, 0f);
        assertEquals(48f, DockLayoutPolicy.PROMPT_FIELD_HEIGHT_DP, 0f);
        assertEquals(PromptLineFieldView.WELL_HEIGHT_DP, DockLayoutPolicy.PROMPT_FIELD_WELL_DP, 0f);
    }

    @Test
    public void theAppsRowIsTheTraysAndOnlyTheFieldIsAdded() throws IllegalAccessException {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                    float scale = DockLayoutPolicy.sizePreset(preset);
                    DockLayout tray = tray(capsule, alone, scale);
                    DockLayout line = line(capsule, alone, scale, 1f);
                    String what = "capsule=" + capsule + " alone=" + alone + " preset=" + preset;
                    for (Field field : DockLayout.class.getFields()) {
                        if (Modifier.isStatic(field.getModifiers())
                            || field.getName().equals("surface") || isPromptField(field)) continue;
                        assertEquals(what + " " + field.getName(), field.get(tray), field.get(line));
                    }
                    assertEquals(what, tray.combinedHeight(BASE_TOOLBAR_PX, false)
                        + line.promptFieldBandPx, line.combinedHeight(BASE_TOOLBAR_PX, false));
                    assertEquals(what, tray.combinedHeight(BASE_TOOLBAR_PX, true)
                        + line.promptFieldBandPx, line.combinedHeight(BASE_TOOLBAR_PX, true));
                }
            }
        }
    }

    @Test
    public void theFieldIsFortyEightDpWithEqualOuterPadding() {
        for (boolean capsule : new boolean[] {false, true}) {
            for (boolean alone : new boolean[] {false, true}) {
                for (int preset = 0; preset < DockLayoutPolicy.sizePresetCount(); preset++) {
                    DockLayout line = line(capsule, alone, DockLayoutPolicy.sizePreset(preset), 1f);
                    String what = "capsule=" + capsule + " alone=" + alone + " preset=" + preset;
                    assertEquals(what, Math.round(48f * DENSITY), line.promptFieldHeightPx);
                    assertEquals(what, line.promptFieldHeightPx + line.promptFieldBottomPadPx,
                        line.promptFieldBandPx);
                    assertEquals(what, line.promptFieldBandPx, line.promptFieldFullBandPx);
                    // The air over the icons, inside the slab (the dashes stand above it) ...
                    int top = line.appsRowPaddingBesideTicksPx();
                    // ... is the air under the well, to the slab's lower edge.
                    int wellInset = DockLayoutPolicy.promptFieldWellInsetPx(DENSITY);
                    int bottom = wellInset + line.promptFieldBottomPadPx;
                    assertEquals(what, top, bottom);
                    assertEquals(what, top, line.promptFieldWellGapPx);
                    assertTrue(what, top > 0);
                }
            }
        }
    }

    @Test
    public void theFieldKeepsTheRowsFourteenDpInsideTheSlab() {
        DockLayout floating = line(true, true, 2.18f, 1f);
        assertEquals(floating.capsuleContentInsetPx, floating.promptFieldSideInsetPx);
        assertEquals(floating.horizontalInsetPx + Math.round(14f * DENSITY),
            floating.promptFieldSideInsetPx);
        // Docked, the slab runs edge to edge and the field still keeps the 14 dp.
        DockLayout docked = line(false, true, 2.18f, 1f);
        assertEquals(0, docked.horizontalInsetPx);
        assertEquals(Math.round(14f * DENSITY), docked.promptFieldSideInsetPx);
    }

    @Test
    public void noRevealIsTheTray() throws IllegalAccessException {
        for (boolean capsule : new boolean[] {false, true}) {
            DockLayout tray = tray(capsule, true, 2.18f);
            DockLayout line = line(capsule, true, 2.18f, 0f);
            for (Field field : DockLayout.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("surface"))
                    continue;
                assertEquals(field.getName(), field.get(tray), field.get(line));
            }
            assertEquals(0, line.promptFieldBandPx);
            assertEquals(tray.combinedHeight(BASE_TOOLBAR_PX, false),
                line.combinedHeight(BASE_TOOLBAR_PX, false));
        }
        // The reveal is clamped: nothing below none, nothing past the whole field.
        assertEquals(0, line(true, true, 2.18f, -1f).promptFieldBandPx);
        assertEquals(0, line(true, true, 2.18f, Float.NaN).promptFieldBandPx);
        assertEquals(line(true, true, 2.18f, 1f).promptFieldBandPx,
            line(true, true, 2.18f, 3f).promptFieldBandPx);
    }

    @Test
    public void theBandEasesWithTheReveal() {
        int previous = -1;
        int full = line(true, true, 2.18f, 1f).promptFieldFullBandPx;
        for (int step = 0; step <= 20; step++) {
            float reveal = step / 20f;
            DockLayout line = line(true, true, 2.18f, reveal);
            assertTrue("reveal " + reveal, line.promptFieldBandPx >= previous);
            assertTrue(line.promptFieldBandPx <= full);
            assertTrue(line.promptFieldHeightPx <= line.promptFieldBandPx);
            assertEquals(line.promptFieldBandPx,
                line.promptFieldHeightPx + line.promptFieldBottomPadPx);
            if (step > 0) assertEquals(full, line.promptFieldFullBandPx);
            previous = line.promptFieldBandPx;
        }
        assertEquals(full, previous);
    }

    @Test
    public void onlyTheDocksOwnRowCarriesTheField() {
        // A row on another edge, a rail, or no row at all: no field, whatever the reveal.
        DockLayout top = DockLayoutPolicy.compute(inputs(true, true, 2.18f).appsRowOnEdge(true)
            .surface(DockSurfaceSpec.GLASS_TRAY).promptFieldReveal(1f).build());
        assertEquals(0, top.promptFieldBandPx);
        DockLayout rail = DockLayoutPolicy.compute(inputs(true, true, 2.18f).appsRowOnEdge(true)
            .appsOnRail(true).appsRowPageStripShown(false)
            .surface(DockSurfaceSpec.GLASS_TRAY).promptFieldReveal(1f).build());
        assertEquals(0, rail.promptFieldBandPx);
        DockLayout none = DockLayoutPolicy.compute(inputs(true, true, 2.18f).appsRowEnabledPref(false)
            .surface(DockSurfaceSpec.GLASS_TRAY).promptFieldReveal(1f).build());
        assertEquals(0, none.promptFieldBandPx);
        assertEquals(0, none.combinedHeight(BASE_TOOLBAR_PX, false));
        DockLayout noStore = DockLayoutPolicy.compute(inputs(true, true, 2.18f)
            .preferencesAvailable(false).surface(DockSurfaceSpec.GLASS_TRAY)
            .promptFieldReveal(1f).build());
        assertEquals(0, noStore.promptFieldBandPx);
    }

    @Test
    public void theWellNestsInTheSlab() {
        DockLayout line = line(true, true, 2.18f, 1f);
        float dockRadius = line.capsuleCornerRadiusPx(line.appsBarHeightPx + line.promptFieldBandPx);
        float well = WallStripRowPolicy.tileRadiusPx(dockRadius, line.promptFieldWellGapPx);
        assertEquals(dockRadius - line.promptFieldWellGapPx, well, 1e-3f);
        assertTrue(well > 0f);
    }

    private static DockLayout design(DockSurfaceSpec spec, float fieldReveal) {
        return DockLayoutPolicy.compute(inputs(true, true, 2.18f).surface(spec)
            .promptFieldReveal(fieldReveal).wallStripReveal(spec.wallStrip ? 1f : 0f).build());
    }

    /**
     * The Search field stands under the apps row with every design (#96): the same band whatever
     * the design (Lens beads add the capsule's air over it, #84), the stack taller by it and
     * nothing else, and the Strip with the field the tallest dock of all.
     */
    @Test
    public void theFieldStandsUnderTheAppsRowOfEveryDesign() {
        DockSurfaceSpec[] designs = {DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.LENS_BEADS,
            DockSurfaceSpec.WALL_STRIP};
        int band = design(DockSurfaceSpec.GLASS_TRAY, 1f).promptFieldBandPx;
        assertTrue(band > 0);
        int tallest = 0;
        for (DockSurfaceSpec spec : designs) {
            DockLayout without = design(spec, 0f);
            DockLayout with = design(spec, 1f);
            int specBand = band + (spec.beads ? LensBeadGeometry.capsuleTopPadPx(DENSITY) : 0);
            assertEquals(spec.name, specBand, with.promptFieldBandPx);
            assertEquals(spec.name, 0, without.promptFieldBandPx);
            assertEquals(spec.name, without.wallStripBandPx, with.wallStripBandPx);
            assertEquals(spec.name, without.appsBarHeightPx, with.appsBarHeightPx);
            assertEquals(spec.name, without.combinedHeight(BASE_TOOLBAR_PX, false) + specBand,
                with.combinedHeight(BASE_TOOLBAR_PX, false));
            tallest = Math.max(tallest, with.combinedHeight(BASE_TOOLBAR_PX, false));
        }
        assertEquals("the Strip with the field is the tallest",
            design(DockSurfaceSpec.WALL_STRIP, 1f).combinedHeight(BASE_TOOLBAR_PX, false), tallest);
    }
}
