package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.AppearanceLooks.Door;
import com.termux.app.surfaces.AppearanceLooks.Target;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Appearance editor's rules (appearance-layout-editor SPEC §3.3–3.4), held as arithmetic:
 * which Look each slider stop is, the Custom row's slider and button sets, and what Key corners,
 * Key spacing, Icon size, Soft wallpaper's arithmetic and Layout's Corners and Margin write.
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

    /** The Custom row's sets, legend order, per selection (appearance final pass, section 5). */
    @Test
    public void eachSelectionHasItsSliderSet() {
        assertEquals(Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.TINT,
            Control.MARGIN, Control.CORNER_RADIUS), AppearanceLooks.controls(null));
        assertEquals(Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.TINT,
            Control.KEY_RADIUS, Control.KEY_SPACING), AppearanceLooks.controls(Target.KEYBOARD));
        assertEquals(Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.TINT,
            Control.ICON_SIZE, Control.APP_ICONS), AppearanceLooks.controls(Target.DOCK));
        assertEquals(Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.TINT,
            Control.CONTRAST), AppearanceLooks.controls(Target.TERMINAL));
        assertEquals(Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.TINT),
            AppearanceLooks.controls(Target.STATUS));
        for (Target target : Target.values()) {
            assertTrue(target.name(), AppearanceLooks.controls(target).size()
                <= AppearanceLooks.MAX_CONTROLS);
            assertEquals("glass first, so the same columns stay under the thumb", Control.BLUR,
                AppearanceLooks.controls(target).get(0));
        }
        assertEquals(AppearanceLooks.MAX_CONTROLS, AppearanceLooks.controls(null).size());
    }

    /** The buttons above the sliders: Keyboard theme, Trail and Effect, Clock; none elsewhere. */
    @Test
    public void eachSelectionHasItsButtons() {
        assertTrue(AppearanceLooks.doors(null).isEmpty());
        assertTrue(AppearanceLooks.doors(Target.DOCK).isEmpty());
        assertEquals(Collections.singletonList(Door.KEYBOARD_THEME),
            AppearanceLooks.doors(Target.KEYBOARD));
        assertEquals(Arrays.asList(Door.TRAIL, Door.EFFECT),
            AppearanceLooks.doors(Target.TERMINAL));
        assertEquals(Collections.singletonList(Door.CLOCK), AppearanceLooks.doors(Target.STATUS));
    }

    /** Each control's range is the stored value's own, and its value round-trips. */
    @Test
    public void controlRangesAreTheStoredRanges() {
        assertEquals(AppearanceLooks.BLUR_MAX_DP, Control.BLUR.max);
        assertEquals(AppearanceLooks.CORNERS_MAX_DP, Control.CORNER_RADIUS.max);
        assertEquals(AppearanceLooks.MARGIN_MAX_DP, Control.MARGIN.max);
        assertEquals(AppearanceLooks.KEY_CORNERS_MAX_DP, Control.KEY_RADIUS.max);
        assertEquals(3, Control.APP_ICONS.min);
        assertEquals(10, Control.APP_ICONS.max);
        assertEquals("three stops", 2, Control.CONTRAST.max);
        for (Control control : Control.values()) {
            assertEquals(control.name(), control.min, control.clamp(control.min - 50));
            assertEquals(control.name(), control.max, control.clamp(control.max + 50));
            assertEquals("the range is whole steps: " + control, 0,
                (control.max - control.min) % control.step);
        }
    }

    /** Key spacing is the key margin scale in tenths, 0.0 to 8.0. */
    @Test
    public void keySpacingIsTheMarginScaleInTenths() {
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            AppearanceLooks.keySpacingScaleFor(0), 0f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            AppearanceLooks.keySpacingScaleFor(Control.KEY_SPACING.max), 0f);
        assertEquals(1.5f, AppearanceLooks.keySpacingScaleFor(15), 0f);
        assertEquals(15, AppearanceLooks.keySpacingValueFor(1.5f));
        assertEquals(Control.KEY_SPACING.max, AppearanceLooks.keySpacingValueFor(20f));
        for (int tenths = 0; tenths <= Control.KEY_SPACING.max; tenths++)
            assertEquals(tenths, AppearanceLooks.keySpacingValueFor(
                AppearanceLooks.keySpacingScaleFor(tenths)));
    }

    /**
     * Icon size is the pinned icon in dp, stored as the dock height scale that draws it. Its ends
     * are the smallest and largest icon the dock gives under each Style, so no stretch of the
     * slider is dead, and every value comes back as itself.
     */
    @Test
    public void iconSizeIsTheIconTheDockDrawsUnderEachStyle() {
        for (boolean floating : new boolean[] {false, true}) {
            for (float density : new float[] {2f, 2.625f, 2.75f, 3f, 3.5f}) {
                String at = (floating ? "Floating" : "Docked") + " at " + density;
                int[] range = AppearanceLooks.iconSizeRangeDp(floating, density);
                assertTrue(at, range[0] < range[1]);
                assertTrue(at, range[0] >= Control.ICON_SIZE.min
                    && range[1] <= Control.ICON_SIZE.max);
                // The ends are the stretch's ends, and anything stored past them reads as them.
                assertEquals(at, range[0], AppearanceLooks.iconSizeValueFor(floating,
                    DockLayoutPolicy.minUsefulScale(), density));
                assertEquals(at, range[1], AppearanceLooks.iconSizeValueFor(floating,
                    DockLayoutPolicy.maxUsefulScale(floating), density));
                assertEquals(at, range[0], AppearanceLooks.iconSizeValueFor(floating,
                    TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT, density));
                assertEquals(at, range[1], AppearanceLooks.iconSizeValueFor(floating,
                    TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT, density));
                for (int dp = range[0]; dp <= range[1]; dp++) {
                    float scale = AppearanceLooks.dockScaleForIconSize(floating, dp, density);
                    assertTrue(at + " " + dp + " dp writes inside the stretch",
                        scale >= DockLayoutPolicy.minUsefulScale()
                            && scale <= DockLayoutPolicy.maxUsefulScale(floating));
                    assertEquals(at + " " + dp + " dp", dp,
                        AppearanceLooks.iconSizeValueFor(floating, scale, density));
                }
                // Out of range values are held to the ends.
                assertEquals(at, range[0], AppearanceLooks.iconSizeValueFor(floating,
                    AppearanceLooks.dockScaleForIconSize(floating, 1, density), density));
            }
        }
    }

    /** The shipped default stays the shipped default: its icon writes 2.18 back, both Styles. */
    @Test
    public void theDefaultsIconWritesTheDefault() {
        float preset = TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT;
        for (boolean floating : new boolean[] {false, true}) {
            int dp = AppearanceLooks.iconSizeValueFor(floating, preset, 2.75f);
            assertEquals(preset, AppearanceLooks.dockScaleForIconSize(floating, dp, 2.75f), 0f);
        }
    }

    @Test
    public void appIconsIsACountHeldInItsRange() {
        assertEquals(7, AppearanceLooks.appIconsValueFor(7));
        assertEquals(3, AppearanceLooks.appIconsValueFor(1));
        assertEquals(10, AppearanceLooks.appIconsValueFor(20));
    }

    @Test
    public void aLegendNameIsTheTextBeforeTheSeparator() {
        assertEquals("Blur", AppearanceLooks.legendName("Blur · 12 dp"));
        assertEquals("Contrast", AppearanceLooks.legendName("Contrast"));
    }

    // ------------------------------------------------------------- layout editor v2: the global row

    /** Row 2 is the global row only at Custom with nothing tapped (DECISIONS item 13). */
    @Test
    public void theGlobalRowShowsOnlyAtCustomWithNothingTapped() {
        assertEquals(AppearanceLooks.Row.GLOBAL,
            AppearanceLooks.rowFor(AppearanceLooks.CUSTOM_STOP, null));
        for (Target target : Target.values()) {
            assertEquals(AppearanceLooks.Row.ELEMENT,
                AppearanceLooks.rowFor(AppearanceLooks.CUSTOM_STOP, target));
        }
        for (int stop = 0; stop < AppearanceLooks.CUSTOM_STOP; stop++) {
            assertEquals("no global row on a Look stop", AppearanceLooks.Row.NONE,
                AppearanceLooks.rowFor(stop, null));
            assertEquals(AppearanceLooks.Row.NONE, AppearanceLooks.rowFor(stop, Target.DOCK));
        }
    }

    /** A tap on bare wallpaper deselects at Custom and does nothing at a Look stop. */
    @Test
    public void aWallpaperTapGoesBackToTheGlobalSetOnlyAtCustom() {
        assertTrue(AppearanceLooks.wallpaperTapDeselects(AppearanceLooks.CUSTOM_STOP));
        for (int stop = 0; stop < AppearanceLooks.CUSTOM_STOP; stop++)
            assertFalse(AppearanceLooks.wallpaperTapDeselects(stop));
    }

    /** The global Opacity and Grain ranges cover every Look's value (DECISIONS item 13). */
    @Test
    public void theGlobalOpacityAndGrainRangesCoverEveryLook() {
        assertEquals(100, AppearanceLooks.OPACITY_MAX);
        assertEquals(100, AppearanceLooks.GRAIN_MAX);
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            Object opacity = preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY);
            Object grain = preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN);
            if (opacity instanceof Number) {
                int value = ((Number) opacity).intValue();
                assertEquals(preset.id, value, AppearanceLooks.opacityPercent(value));
            }
            if (grain instanceof Number) {
                int value = ((Number) grain).intValue();
                assertEquals(preset.id, value, AppearanceLooks.grainPercent(value));
            }
        }
        assertEquals(0, AppearanceLooks.opacityPercent(-5));
        assertEquals(100, AppearanceLooks.grainPercent(140));
    }

    @Test
    public void targetsMapToTheirSurfaces() {
        assertEquals(Target.TERMINAL, Target.forSlot(SurfaceSlot.CANVAS));
        assertEquals(Target.STATUS, Target.forSlot(SurfaceSlot.STATUS));
        assertEquals(Target.DOCK, Target.forSlot(SurfaceSlot.DOCK));
        assertEquals(Target.KEYBOARD, Target.forSlot(SurfaceSlot.KEYBOARD));
        assertNull(Target.forSlot(null));
        assertEquals("the wallpaper is no target", 4, Target.values().length);
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

    /** Margin reads the side gap under both Styles: air under Floating, the gutter under Docked. */
    @Test
    public void marginReadsTheSideGapUnderBothStyles() {
        assertEquals(30, AppearanceLooks.marginValueFor(true, 30, 6));
        assertEquals(30, AppearanceLooks.marginValueFor(false, 30, 6));
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
        assertEquals("the cap is 48 dp, which Clear's 44 sits under", 48, AppearanceLooks.BLUR_MAX_DP);
        assertEquals(44, AppearanceLooks.blurDp(44));
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
