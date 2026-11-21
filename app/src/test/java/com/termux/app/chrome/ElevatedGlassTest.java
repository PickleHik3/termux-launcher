package com.termux.app.chrome;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A surface raised over other glass wears the sheets' glass made denser, never less of anything. */
public class ElevatedGlassTest {

    private static LookGrid global(int frostStop, int opacity, int grain) {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(LookGrid.Property.FROST, null, frostStop);
        grid.write(LookGrid.Property.OPACITY, null, opacity);
        grid.write(LookGrid.Property.GRAIN, null, grain);
        grid.write(LookGrid.Property.TINT, null, 15);
        return grid;
    }

    @Test
    public void neverLessBlurGrainOrBodyThanTheSheet() {
        for (int stop = 0; stop < FrostStops.COUNT; stop++) {
            for (int opacity = 0; opacity <= 100; opacity += 5) {
                for (int grain = 0; grain <= 100; grain += 10) {
                    ElementGlass sheet = SheetGlass.of(global(stop, opacity, grain));
                    ElementGlass raised = ElevatedGlass.of(sheet);
                    String at = "stop=" + stop + " o=" + opacity + " g=" + grain;
                    assertTrue(at, raised.blurDp >= sheet.blurDp);
                    assertTrue(at, raised.opacityPercent >= sheet.opacityPercent);
                    assertTrue(at, raised.grainPercent >= sheet.grainPercent);
                    // Tint and corners are the one look's, untouched.
                    assertEquals(at, sheet.tintPercent, raised.tintPercent);
                    assertEquals(at, sheet.cornersDp, raised.cornersDp);
                }
            }
        }
    }

    @Test
    public void eachNumberGrowsMonotonically() {
        for (int v = 1; v <= 100; v++) {
            assertTrue(ElevatedGlass.opacityPercent(v) >= ElevatedGlass.opacityPercent(v - 1));
            assertTrue(ElevatedGlass.grainPercent(v) >= ElevatedGlass.grainPercent(v - 1));
            if (v <= ElementGlass.MAX_BLUR_DP) {
                assertTrue(ElevatedGlass.blurDp(v) >= ElevatedGlass.blurDp(v - 1));
            }
        }
    }

    @Test
    public void theDefaultLookRaised() {
        // Fresh install: blur 8, opacity 34, grain 18; the sheet floor lifts opacity to 60.
        assertEquals(16, ElevatedGlass.blurDp(8));
        assertEquals(65, ElevatedGlass.opacityPercent(60));
        assertEquals(29, ElevatedGlass.grainPercent(18));
    }

    /** The glass cues carry the elevation: over the sheet floor the body gains only a step. */
    @Test
    public void theBodyGainsOnlyAStep_soWhatIsBehindShowsThroughTheBlur() {
        for (int o = SheetGlass.OPACITY_FLOOR_PERCENT; o <= 100; o++) {
            assertTrue("o=" + o, ElevatedGlass.opacityPercent(o) - o <= 5);
        }
        assertTrue(ElevatedGlass.opacityPercent(SheetGlass.OPACITY_FLOOR_PERCENT) < 70);
    }

    @Test
    public void opacityZeroStillReadsAsAPanel_throughTheSheetFloor() {
        ElementGlass raised = ElevatedGlass.of(SheetGlass.of(global(FrostStops.MIST, 0, 18)));
        assertEquals(ElevatedGlass.opacityPercent(SheetGlass.OPACITY_FLOOR_PERCENT),
            raised.opacityPercent);
        assertTrue(raised.opacityPercent > SheetGlass.OPACITY_FLOOR_PERCENT);
    }

    @Test
    public void neverFullyOpaqueUnlessTheLookAlreadyIs() {
        for (int o = 0; o <= ElevatedGlass.OPACITY_CEILING_PERCENT; o++) {
            assertTrue(ElevatedGlass.opacityPercent(o) <= ElevatedGlass.OPACITY_CEILING_PERCENT);
        }
        assertEquals(95, ElevatedGlass.opacityPercent(95));
        assertEquals(100, ElevatedGlass.opacityPercent(100));
    }

    @Test
    public void blurOffStaysOff_grainAndBodyCarryIt() {
        ElementGlass raised = ElevatedGlass.of(SheetGlass.of(global(FrostStops.SOLID, 30, 18)));
        assertEquals(0, raised.blurDp);
        assertTrue(raised.grainPercent > 18);
        assertTrue(raised.opacityPercent > SheetGlass.OPACITY_FLOOR_PERCENT);
    }

    @Test
    public void blurAddsAtLeastAStepAndStopsAtTheChromeCeiling() {
        assertEquals(1 + ElevatedGlass.BLUR_STEP_DP, ElevatedGlass.blurDp(1));
        assertEquals(30, ElevatedGlass.blurDp(20));
        assertEquals(ElementGlass.MAX_BLUR_DP, ElevatedGlass.blurDp(40));
        assertEquals(ElementGlass.MAX_BLUR_DP, ElevatedGlass.blurDp(ElementGlass.MAX_BLUR_DP));
    }

    @Test
    public void grainAddsAtLeastAStepAndStopsAtItsCeiling_neverCuttingAHigherLook() {
        assertEquals(ElevatedGlass.GRAIN_STEP_PERCENT, ElevatedGlass.grainPercent(0));
        assertEquals(ElevatedGlass.GRAIN_CEILING_PERCENT, ElevatedGlass.grainPercent(40));
        assertEquals(70, ElevatedGlass.grainPercent(70));
    }

    @Test
    public void theRimIsAQuarterStrongerAndClamped() {
        assertEquals(Math.round(0x1A * ElevatedGlass.RIM_GAIN), ElevatedGlass.rimAlpha(0x1A));
        assertEquals(255, ElevatedGlass.rimAlpha(250));
        assertEquals(0, ElevatedGlass.rimAlpha(0));
    }

    @Test
    public void theLookSpecCarriesTheRaisedNumbers() {
        ElementGlass raised = ElevatedGlass.of(global(FrostStops.MIST, 34, 18));
        LookGlassSpec spec = ElevatedGlass.lookSpec(true, raised);
        assertEquals(raised.blurDp, spec.blurDp);
        assertEquals(raised.opacityPercent, spec.opacityPercent);
        assertEquals(raised.grainPercent, spec.grainPercent);
        assertEquals(raised.tintPercent, spec.tintPercent);
        assertTrue(spec.slabShown());
    }
}
