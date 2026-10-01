package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.AppearanceLooks.Target;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.List;

/**
 * The Appearance editor's rules (appearance-layout-editor SPEC §3.3–3.4), held as arithmetic:
 * which Look each slider stop is, and what Darkness, Key corners, Soft wallpaper, Dim and Layout's
 * Corners and Margin write.
 */
public class AppearanceLooksTest {

    // ------------------------------------------------------------------------------ the slider

    @Test
    public void theStopsAreClearMistTintSolidThenCustom() {
        assertEquals(5, AppearanceLooks.STOP_COUNT);
        assertEquals("minimal", AppearanceLooks.presetIdForStop(0));
        assertEquals("frost", AppearanceLooks.presetIdForStop(1));
        assertEquals("stock", AppearanceLooks.presetIdForStop(2));
        assertEquals("solid", AppearanceLooks.presetIdForStop(3));
        assertNull("the last stop is Custom", AppearanceLooks.presetIdForStop(4));
        assertEquals(4, AppearanceLooks.CUSTOM_STOP);
    }

    /** The slider's stops are the shipped Looks in the shipped order, one for one. */
    @Test
    public void everyLookStopNamesAShippedPresetInOrder() {
        List<SurfacePresets.Preset> presets = SurfacePresets.presets();
        assertEquals(AppearanceLooks.STOP_COUNT - 1, presets.size());
        for (int stop = 0; stop < presets.size(); stop++) {
            assertSame(presets.get(stop), AppearanceLooks.presetForStop(presets, stop));
            assertEquals(stop, AppearanceLooks.stopForPresetId(presets.get(stop).id));
        }
        assertNull(AppearanceLooks.presetForStop(presets, AppearanceLooks.CUSTOM_STOP));
    }

    @Test
    public void presetIdsAndStopsRoundTrip() {
        for (int stop = 0; stop < AppearanceLooks.CUSTOM_STOP; stop++)
            assertEquals(stop,
                AppearanceLooks.stopForPresetId(AppearanceLooks.presetIdForStop(stop)));
        assertEquals(AppearanceLooks.CUSTOM_STOP,
            AppearanceLooks.stopForPresetId(SurfacePresets.CUSTOM_ID));
        assertEquals(AppearanceLooks.CUSTOM_STOP, AppearanceLooks.stopForPresetId(null));
        assertEquals(AppearanceLooks.CUSTOM_STOP, AppearanceLooks.stopForPresetId("classic"));
    }

    @Test
    public void outOfRangeStopsAndSliderValuesClamp() {
        assertEquals("minimal", AppearanceLooks.presetIdForStop(-3));
        assertNull(AppearanceLooks.presetIdForStop(99));
        assertTrue(AppearanceLooks.isCustomStop(99));
        assertFalse(AppearanceLooks.isCustomStop(0));
        assertEquals(0, AppearanceLooks.stopForSliderValue(-1f));
        assertEquals(2, AppearanceLooks.stopForSliderValue(2.4f));
        assertEquals(3, AppearanceLooks.stopForSliderValue(2.6f));
        assertEquals(4, AppearanceLooks.stopForSliderValue(7f));
        for (int stop = 0; stop < AppearanceLooks.STOP_COUNT; stop++)
            assertEquals(stop, AppearanceLooks.stopForSliderValue(
                AppearanceLooks.sliderValueForStop(stop)));
    }

    // ------------------------------------------------------------------------------ the targets

    /**
     * Terminal: Darkness, Legibility, Blur. Status bar and dock: Blur alone. Keyboard: Key
     * corners and Blur. Wallpaper: Soft and Dim (2026-10-01).
     */
    @Test
    public void eachTargetHasItsControls() {
        assertFalse(Target.STATUS.hasFirstControl());
        assertFalse(Target.DOCK.hasFirstControl());
        assertTrue(Target.TERMINAL.hasFirstControl());
        assertTrue(Target.KEYBOARD.hasFirstControl());
        assertTrue(Target.WALLPAPER.hasFirstControl());
        for (Target target : Target.values()) {
            assertEquals(target == Target.TERMINAL, target.hasLegibility());
            assertEquals(target != Target.WALLPAPER, target.secondControlIsBlur());
        }
    }

    @Test
    public void targetsMapToTheirSurfacesAndTheWallpaperToNone() {
        assertEquals(Target.TERMINAL, Target.forSlot(SurfaceSlot.CANVAS));
        assertEquals(Target.STATUS, Target.forSlot(SurfaceSlot.STATUS));
        assertEquals(Target.DOCK, Target.forSlot(SurfaceSlot.DOCK));
        assertEquals(Target.KEYBOARD, Target.forSlot(SurfaceSlot.KEYBOARD));
        assertNull(Target.forSlot(null));
        assertNull(Target.WALLPAPER.slot);
    }

    // ---------------------------------------------------------------------------- Darkness

    @Test
    public void darknessIsTheTerminalsOwnOpacity() {
        assertEquals(0, AppearanceLooks.darknessOpacity(0));
        assertEquals(46, AppearanceLooks.darknessOpacity(46));
        assertEquals(100, AppearanceLooks.darknessOpacity(100));
        assertEquals(0, AppearanceLooks.darknessOpacity(-5));
        assertEquals(100, AppearanceLooks.darknessOpacity(140));
    }

    @Test
    public void darknessTurnsTheTintObsidianFromHalfway() {
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, AppearanceLooks.darknessTint(0));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, AppearanceLooks.darknessTint(49));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, AppearanceLooks.darknessTint(50));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, AppearanceLooks.darknessTint(100));
    }

    // ------------------------------------------------------------------------- Key corners

    @Test
    public void keyCornersIsTheKeyRadiusAloneUpToItsCeiling() {
        assertEquals(24, AppearanceLooks.KEY_CORNERS_MAX_DP);
        assertEquals(0, AppearanceLooks.keyCornersDp(0));
        assertEquals(9, AppearanceLooks.keyCornersDp(9));
        assertEquals(24, AppearanceLooks.keyCornersDp(24));
        assertEquals(24, AppearanceLooks.keyCornersDp(60));
        assertEquals(0, AppearanceLooks.keyCornersDp(-3));
        for (int dp = 0; dp <= AppearanceLooks.KEY_CORNERS_MAX_DP; dp++)
            assertEquals(dp, AppearanceLooks.keyCornersValueFor(dp));
        assertEquals(10, AppearanceLooks.keyCornersValueFor(9.6f));
    }

    // --------------------------------------------------------------- Layout's Corners, Margin

    @Test
    public void cornersRunToFortyAndMarginToFortyEight() {
        assertEquals(40, AppearanceLooks.CORNERS_MAX_DP);
        assertEquals(0, AppearanceLooks.cornersDp(-1));
        assertEquals(40, AppearanceLooks.cornersDp(55));
        assertEquals(48, AppearanceLooks.MARGIN_MAX_DP);
        assertEquals(0, AppearanceLooks.marginDp(-1));
        assertEquals(48, AppearanceLooks.marginDp(90));
    }

    /** The terminal's own margin never goes past 24, however much air Margin asks for. */
    @Test
    public void marginCapsTheTerminalsShareAtTwentyFour() {
        assertEquals(0, AppearanceLooks.terminalMarginDp(0));
        assertEquals(12, AppearanceLooks.terminalMarginDp(12));
        assertEquals(24, AppearanceLooks.terminalMarginDp(24));
        assertEquals(24, AppearanceLooks.terminalMarginDp(40));
        assertEquals(24, AppearanceLooks.terminalMarginDp(200));
    }

    /** Floating reads the side gap it spends Margin on; Docked the terminal's own margin. */
    @Test
    public void marginReadsTheSideGapFloatingAndThePaneGapDocked() {
        assertEquals(30, AppearanceLooks.marginValueFor(true, 30, 6));
        assertEquals(6, AppearanceLooks.marginValueFor(false, 30, 6));
        assertEquals(48, AppearanceLooks.marginValueFor(true, 99, 6));
    }

    // --------------------------------------------------------------------------- Soft and Dim

    @Test
    public void softAddsItsDimUnderWhateverDimAdds() {
        assertEquals(0, AppearanceLooks.storedDim(false, 0));
        assertEquals(40, AppearanceLooks.storedDim(false, 40));
        assertEquals(AppearanceLooks.SOFT_DIM, AppearanceLooks.storedDim(true, 0));
        assertEquals(AppearanceLooks.SOFT_DIM + 30, AppearanceLooks.storedDim(true, 30));
        assertEquals(100, AppearanceLooks.storedDim(true, 90));
        assertEquals(100, AppearanceLooks.storedDim(false, 130));
    }

    @Test
    public void withSoftOnDimShowsOnlyTheDimAboveSoftsOwn() {
        assertEquals(0, AppearanceLooks.dimSliderValue(true, AppearanceLooks.SOFT_DIM));
        assertEquals(30, AppearanceLooks.dimSliderValue(true, AppearanceLooks.SOFT_DIM + 30));
        assertEquals(0, AppearanceLooks.dimSliderValue(true, 10));
        assertEquals(10, AppearanceLooks.dimSliderValue(false, 10));
        assertEquals(100 - AppearanceLooks.SOFT_DIM, AppearanceLooks.dimSliderMax(true));
        assertEquals(100, AppearanceLooks.dimSliderMax(false));
    }

    /** Switching Soft keeps the extra dim the user asked for, and the slider reads it back. */
    @Test
    public void softAndDimComposeAndRoundTrip() {
        for (int extra = 0; extra <= AppearanceLooks.dimSliderMax(true); extra += 5) {
            int stored = AppearanceLooks.storedDim(true, extra);
            assertEquals(extra, AppearanceLooks.dimSliderValue(true, stored));
            int off = AppearanceLooks.storedDim(false,
                AppearanceLooks.dimSliderValue(true, stored));
            assertEquals(extra, off);
        }
        for (int dim = 0; dim <= 100; dim += 5)
            assertEquals(dim, AppearanceLooks.dimSliderValue(false,
                AppearanceLooks.storedDim(false, dim)));
    }

    // --------------------------------------------------------------------- Blur and Legibility

    @Test
    public void blurIsClampedToTheSlidersRange() {
        assertEquals(0, AppearanceLooks.blurDp(-4));
        assertEquals(12, AppearanceLooks.blurDp(12));
        assertEquals(AppearanceLooks.BLUR_MAX_DP, AppearanceLooks.blurDp(80));
    }

    @Test
    public void legibilitySegmentsAreSofterDefaultHarder() {
        assertEquals(TerminalContrastLevel.SOFTER, AppearanceLooks.legibilityAt(0));
        assertEquals(TerminalContrastLevel.DEFAULT, AppearanceLooks.legibilityAt(1));
        assertEquals(TerminalContrastLevel.HARDER, AppearanceLooks.legibilityAt(2));
        assertEquals(TerminalContrastLevel.HARDER, AppearanceLooks.legibilityAt(9));
        for (TerminalContrastLevel level : TerminalContrastLevel.values())
            assertEquals(level,
                AppearanceLooks.legibilityAt(AppearanceLooks.legibilityIndex(level)));
        assertEquals(1, AppearanceLooks.legibilityIndex(null));
    }
}
