package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockLayoutPolicy;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.List;

/**
 * The Appearance editor's arithmetic (appearance-layout-editor SPEC §3.3–3.4), as pure functions.
 *
 * <p>The Look slider has five stops: the four Looks in {@link SurfacePresets#presets()} order —
 * Clear, Mist, Tint, Solid — and Custom last; a surface's own value leaves a half at Custom by
 * itself. The ranges and conversions of the cards' controls ({@link Control}) are here too, and
 * the shape no Look sets: Corners and Margin.</p>
 *
 * <p>No views and no {@code Context}, so the rules are held by JVM tests.</p>
 */
public final class AppearanceLooks {

    private AppearanceLooks() {}

    // ------------------------------------------------------------------------------ the slider

    /** The Look ids, in slider order. Held against {@link SurfacePresets#presets()} by a test. */
    static final String[] LOOK_IDS = {"minimal", "frost", "stock", "solid"};

    /** Four Looks and Custom. */
    public static final int STOP_COUNT = LOOK_IDS.length + 1;

    /** Custom is the last stop. */
    public static final int CUSTOM_STOP = STOP_COUNT - 1;

    /** The stop a preset id sits on; Custom for {@link SurfacePresets#CUSTOM_ID}, null or unknown. */
    public static int stopForPresetId(@Nullable String presetId) {
        if (presetId == null)
            return CUSTOM_STOP;
        for (int i = 0; i < LOOK_IDS.length; i++) {
            if (LOOK_IDS[i].equals(presetId))
                return i;
        }
        return CUSTOM_STOP;
    }

    /** The preset id a stop applies, or null at Custom. Out-of-range stops clamp. */
    @Nullable
    public static String presetIdForStop(int stop) {
        int clamped = clamp(stop, 0, CUSTOM_STOP);
        return clamped == CUSTOM_STOP ? null : LOOK_IDS[clamped];
    }

    public static boolean isCustomStop(int stop) {
        return clamp(stop, 0, CUSTOM_STOP) == CUSTOM_STOP;
    }

    /** The shipped preset a stop names, or null at Custom or when the build lacks that id. */
    @Nullable
    public static SurfacePresets.Preset presetForStop(@NonNull List<SurfacePresets.Preset> presets,
                                                     int stop) {
        String id = presetIdForStop(stop);
        if (id == null)
            return null;
        for (SurfacePresets.Preset preset : presets) {
            if (preset.id.equals(id))
                return preset;
        }
        return null;
    }

    // ------------------------------------------------------------------ the controls

    /**
     * A slider of the editor's cards, with the integer range its stored value maps onto.
     * Icon size is the pinned icon in dp, and its slider runs over the range the dock gives under
     * the Style ({@link #iconSizeRangeDp}); the bounds here are only the ones no dock passes, the
     * icon's floor and the deepest row. Key spacing is the key margin scale in tenths; Key opacity
     * the caps' opacity in percent (stored -1 leaves it to the theme); the others are their own units (dp, percent, a count, a stop). Glass is 0 to 100 over blur and grain
     * ({@link GlassControls}); Vibrancy the frame's saturation in percent, 70 to 150. Edge and
     * Depth are the glass edge's: Edge the stored dispersion itself, 0 (no fringe) to 100 (a bold
     * coloured edge), Glint at 40; Depth 0 to 100 over the bend and the edge width
     * ({@link GlassEdgeControls}). Text size is the shared size of the type the launcher draws, in
     * percent of each surface's own, 80 to 150 in steps of 5. Split gap is the terminal's own: the
     * gutter between split panes, 0 to 24 dp.
     */
    public enum Control {
        GLASS(GlassControls.GLASS_MIN, GlassControls.GLASS_MAX, 1),
        OPACITY(0, OPACITY_MAX, 1),
        TINT(0, TINT_MAX, 1),
        EDGE(TERMUX_APP.MIN_FANCIER_GLASS_DISPERSION, TERMUX_APP.MAX_FANCIER_GLASS_DISPERSION, 1),
        VIBRANCY(TERMUX_APP.MIN_SURFACE_GLASS_VIBRANCY, TERMUX_APP.MAX_SURFACE_GLASS_VIBRANCY, 1),
        KEY_OPACITY(TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_OPACITY,
            TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_OPACITY, 1),
        KEY_RADIUS(0, KEY_CORNERS_MAX_DP, 1),
        KEY_SPACING(0, KEY_SPACING_MAX_TENTHS, 1),
        ICON_SIZE(Math.round(DockLayoutPolicy.MIN_DOCK_ICON_DP),
            DockLayoutPolicy.maxRowBandPx(1f), 1),
        APP_ICONS(APP_ICONS_MIN, APP_ICONS_MAX, 1),
        CONTRAST(0, 2, 1),
        DEPTH(GlassEdgeControls.MIN, GlassEdgeControls.MAX, 1),
        TEXT_SIZE(TERMUX_APP.MIN_TEXT_SIZE_PERCENT, TERMUX_APP.MAX_TEXT_SIZE_PERCENT, 5),
        SPLIT_GAP(0, TERMUX_APP.MAX_TERMINAL_PANE_GAP, 1);

        public final int min;
        public final int max;
        public final int step;

        Control(int min, int max, int step) {
            this.min = min;
            this.max = max;
            this.step = step;
        }

        /** The value held inside the control's range. */
        public int clamp(int value) {
            return AppearanceLooks.clamp(value, min, max);
        }
    }

    /** Key spacing is the key margin scale, 0.0 to 8.0, in tenths. */
    public static final int KEY_SPACING_MAX_TENTHS = 80;

    /** The dock's visible app buttons. */
    public static final int APP_ICONS_MIN = 3;
    public static final int APP_ICONS_MAX = 10;

    /**
     * The Icon size slider's ends, {@code {min, max}} in dp: the smallest and largest pinned icon
     * the dock draws under the Style on a screen of this density, for a row that stands
     * {@code alone} in its container or shares it. The stored dock height scale
     * moves the icon only between {@link DockLayoutPolicy#minUsefulScale} and
     * {@link DockLayoutPolicy#maxUsefulScale}, so a slider over these ends moves it everywhere.
     */
    @NonNull
    public static int[] iconSizeRangeDp(boolean floating, boolean alone, float density) {
        float d = safeDensity(density);
        return new int[] {
            iconDp(DockLayoutPolicy.iconPxForScale(floating, alone,
                DockLayoutPolicy.minUsefulScale(), d), d),
            iconDp(DockLayoutPolicy.iconPxForScale(floating, alone,
                DockLayoutPolicy.maxUsefulScale(floating), d), d)};
    }

    /** Where the Icon size slider stands for a stored dock height scale: the icon it draws. */
    public static int iconSizeValueFor(boolean floating, boolean alone, float scale,
                                       float density) {
        float d = safeDensity(density);
        int[] range = iconSizeRangeDp(floating, alone, d);
        return clamp(iconDp(DockLayoutPolicy.iconPxForScale(floating, alone,
            DockLayoutPolicy.clampToUsefulScale(floating, scale), d), d), range[0], range[1]);
    }

    /**
     * The dock height scale an Icon size value (dp) writes: the one that draws that icon. A
     * shipped preset that reads as this value is preferred, so the slider brought back to the
     * default's reading stores the default rather than a scale a pixel away from it.
     */
    public static float dockScaleForIconSize(boolean floating, boolean alone, int iconDp,
                                             float density) {
        float d = safeDensity(density);
        int[] range = iconSizeRangeDp(floating, alone, d);
        int dp = clamp(iconDp, range[0], range[1]);
        for (int i = 0; i < DockLayoutPolicy.sizePresetCount(); i++) {
            float preset = DockLayoutPolicy.sizePreset(i);
            if (preset >= DockLayoutPolicy.minUsefulScale()
                && preset <= DockLayoutPolicy.maxUsefulScale(floating)
                && iconSizeValueFor(floating, alone, preset, d) == dp)
                return preset;
        }
        return DockLayoutPolicy.scaleForIconPx(floating, alone, Math.round(dp * d), d);
    }

    private static int iconDp(int px, float density) {
        return Math.round(px / density);
    }

    private static float safeDensity(float density) {
        return density > 0f ? density : 1f;
    }

    /** The key margin scale a Key spacing value (tenths) writes. */
    public static float keySpacingScaleFor(int tenths) {
        return Control.KEY_SPACING.clamp(tenths) / 10f;
    }

    /** Where the Key spacing slider stands for a stored key margin scale. */
    public static int keySpacingValueFor(float scale) {
        return Control.KEY_SPACING.clamp(Math.round(scale * 10f));
    }

    /** Where the App icons slider stands for a stored button count. */
    public static int appIconsValueFor(int count) {
        return Control.APP_ICONS.clamp(count);
    }

    // ------------------------------------------------------------------------------ the controls

    /**
     * The global Opacity and Grain, in percent (DECISIONS item 13). Their ranges are the ones the
     * stored values already have (the material curves' clamp and the keyboard grain's ceiling),
     * and cover every Look: Clear 10 / 4, Mist 60 / 8, Tint 46 / 14, Solid 92 / 0.
     */
    public static final int OPACITY_MAX = 100;
    public static final int GRAIN_MAX = 100;

    public static int opacityPercent(int value) {
        return clamp(value, 0, OPACITY_MAX);
    }

    public static int grainPercent(int value) {
        return clamp(value, 0, GRAIN_MAX);
    }

    /**
     * Tint is how much accent colour a surface mixes in, in percent: 0 the Look's own colour, 35
     * the Material blend the Tint Look wears, 100 the accent's container.
     */
    public static final int TINT_MAX = 100;

    public static int tintPercent(int value) {
        return clamp(value, 0, TINT_MAX);
    }

    /** Blur is one value for every surface, in dp; Glass writes it ({@link GlassControls}). */
    public static final int BLUR_MAX_DP = 48;

    public static int blurDp(int value) {
        return clamp(value, 0, BLUR_MAX_DP);
    }

    /**
     * Key corners runs to the key cap radius's own ceiling. It writes the radius alone: the old
     * Keys slider also moved key opacity along the same curve, and read as a radius control with
     * the wrong name (2026-10-01). Key opacity is the Look's.
     */
    public static final int KEY_CORNERS_MAX_DP =
        Math.round(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);

    /** The key cap radius a Key corners value writes, in dp. */
    public static int keyCornersDp(int value) {
        return clamp(value, 0, KEY_CORNERS_MAX_DP);
    }

    /** Where the Key corners slider stands for a stored radius. */
    public static int keyCornersValueFor(float radiusDp) {
        return keyCornersDp(Math.round(radiusDp));
    }

    /** What Soft wallpaper adds: this much dim, and this much blur on the wallpaper itself. */
    public static final int SOFT_DIM = 25;
    public static final int SOFT_BLUR_DP = 12;

    /** The stored {@code wallpaper_backdrop_dim} for Soft and the Dim slider's value. */
    public static int storedDim(boolean soft, int dimSlider) {
        return soft ? clamp(SOFT_DIM + Math.max(0, dimSlider), 0, 100) : clamp(dimSlider, 0, 100);
    }

    /** What the Dim slider shows for a stored dim: with Soft on, only the dim above Soft's own. */
    public static int dimSliderValue(boolean soft, int storedDim) {
        return soft ? clamp(storedDim - SOFT_DIM, 0, dimSliderMax(true))
            : clamp(storedDim, 0, 100);
    }

    /** How far the Dim slider goes: what is left of 100 once Soft has taken its share. */
    public static int dimSliderMax(boolean soft) {
        return soft ? 100 - SOFT_DIM : 100;
    }

    // ------------------------------------------------------------------- Layout's global shape

    /** Corners: one radius for every surface and the terminal, as "All surfaces" had it. */
    public static final int CORNERS_MAX_DP = 40;

    /** Margin: all the air on screen. */
    public static final int MARGIN_MAX_DP = 48;

    public static int cornersDp(int value) {
        return clamp(value, 0, CORNERS_MAX_DP);
    }

    public static int marginDp(int value) {
        return clamp(value, 0, MARGIN_MAX_DP);
    }

    /** Legibility's three segments, in order. */
    private static final TerminalContrastLevel[] LEGIBILITY = {
        TerminalContrastLevel.SOFTER, TerminalContrastLevel.DEFAULT, TerminalContrastLevel.HARDER};

    public static int legibilityIndex(@Nullable TerminalContrastLevel level) {
        for (int i = 0; i < LEGIBILITY.length; i++) {
            if (LEGIBILITY[i] == level)
                return i;
        }
        return 1;
    }

    @NonNull
    public static TerminalContrastLevel legibilityAt(int index) {
        return LEGIBILITY[clamp(index, 0, LEGIBILITY.length - 1)];
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
