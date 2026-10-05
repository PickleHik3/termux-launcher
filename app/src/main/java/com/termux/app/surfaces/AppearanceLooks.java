package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Appearance editor's arithmetic (appearance-layout-editor SPEC §3.3–3.4), as pure functions.
 *
 * <p>The Look slider has five stops: the four Looks in {@link SurfacePresets#presets()} order —
 * Clear, Mist, Tint, Solid — and Custom last. At Custom the sheet shows a row of vertical sliders
 * whose set follows the selection ({@link #controls}): the global set with nothing tapped, or the
 * tapped element's own, with the buttons above them ({@link #doors}). Blur, Grain and Opacity are
 * the shared glass on the global set and the element's own values on an element.</p>
 *
 * <p>Layout mode's Corners and Margin (SPEC §3.5) are here too: the global shape, which no Look
 * sets (2026-10-01); the Custom row's Margin and Corner radius write the same keys.</p>
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

    /** The slider's float value for a stop (the Slider works in floats, one step per stop). */
    public static float sliderValueForStop(int stop) {
        return clamp(stop, 0, CUSTOM_STOP);
    }

    /** The stop a slider value lands on. */
    public static int stopForSliderValue(float value) {
        return clamp(Math.round(value), 0, CUSTOM_STOP);
    }

    // ------------------------------------------------------------------------ what can be tapped

    /** The elements a tap in the frame can select. The bare wallpaper selects none. */
    public enum Target {
        STATUS(SurfaceSlot.STATUS),
        TERMINAL(SurfaceSlot.CANVAS),
        DOCK(SurfaceSlot.DOCK),
        KEYBOARD(SurfaceSlot.KEYBOARD);

        /** The surface this target is. */
        @NonNull public final SurfaceSlot slot;

        Target(@NonNull SurfaceSlot slot) {
            this.slot = slot;
        }

        @Nullable
        public static Target forSlot(@Nullable SurfaceSlot slot) {
            if (slot == null)
                return null;
            for (Target target : values()) {
                if (target.slot == slot)
                    return target;
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ the control sets

    /**
     * A vertical slider of the Custom row, with the integer range its stored value maps onto.
     * Dock size is the dock's height scale in percent of its unscaled height; Key spacing is the
     * key margin scale in tenths; the others are their own units (dp, percent, a count, a stop).
     */
    public enum Control {
        BLUR(0, BLUR_MAX_DP, 1),
        GRAIN(0, GRAIN_MAX, 1),
        OPACITY(0, OPACITY_MAX, 1),
        MARGIN(0, MARGIN_MAX_DP, 1),
        CORNER_RADIUS(0, CORNERS_MAX_DP, 1),
        KEY_RADIUS(0, KEY_CORNERS_MAX_DP, 1),
        KEY_SPACING(0, KEY_SPACING_MAX_TENTHS, 1),
        DOCK_SIZE(DOCK_SIZE_MIN_PERCENT, DOCK_SIZE_MAX_PERCENT, DOCK_SIZE_STEP_PERCENT),
        APP_ICONS(APP_ICONS_MIN, APP_ICONS_MAX, 1),
        CONTRAST(0, 2, 1);

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

    /**
     * A button above the sliders: a door to another page, a menu, or the clock's popup. Keyboard
     * theme and Clock are tonal buttons; Trail and Effect are the outlined menu buttons.
     */
    public enum Door {
        KEYBOARD_THEME,
        CLOCK,
        TRAIL,
        EFFECT
    }

    /** Key spacing is the key margin scale, 0.0 to 8.0, in tenths. */
    public static final int KEY_SPACING_MAX_TENTHS = 80;

    /** Dock size is the dock height scale (0.4 to 3.0) in percent, five at a time. */
    public static final int DOCK_SIZE_MIN_PERCENT = 40;
    public static final int DOCK_SIZE_MAX_PERCENT = 300;
    public static final int DOCK_SIZE_STEP_PERCENT = 5;

    /** The dock's visible app buttons. */
    public static final int APP_ICONS_MIN = 3;
    public static final int APP_ICONS_MAX = 10;

    /**
     * The sliders of the Custom row for a selection, in legend order (left to right): the global
     * set for nothing tapped (null), or the element's own. Blur, Grain and Opacity come first for
     * everything, so the same three columns stay under the user's thumb as the selection changes.
     */
    @NonNull
    public static List<Control> controls(@Nullable Target target) {
        if (target == null)
            return Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY, Control.MARGIN,
                Control.CORNER_RADIUS);
        switch (target) {
            case KEYBOARD:
                return Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY,
                    Control.KEY_RADIUS, Control.KEY_SPACING);
            case DOCK:
                return Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY,
                    Control.DOCK_SIZE, Control.APP_ICONS);
            case TERMINAL:
                return Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY,
                    Control.CONTRAST);
            case STATUS:
            default:
                return Arrays.asList(Control.BLUR, Control.GRAIN, Control.OPACITY);
        }
    }

    /** The buttons above the sliders for a selection, in order; none for the global set. */
    @NonNull
    public static List<Door> doors(@Nullable Target target) {
        if (target == null)
            return Collections.emptyList();
        switch (target) {
            case KEYBOARD:
                return Collections.singletonList(Door.KEYBOARD_THEME);
            case TERMINAL:
                return Arrays.asList(Door.TRAIL, Door.EFFECT);
            case STATUS:
                return Collections.singletonList(Door.CLOCK);
            case DOCK:
            default:
                return Collections.emptyList();
        }
    }

    /** The most sliders any selection has: the columns the sheet is built for. */
    public static final int MAX_CONTROLS = 5;

    /** The name in a legend such as "Blur · 12 dp": the part before the separator. */
    @NonNull
    public static String legendName(@NonNull CharSequence legend) {
        String text = legend.toString();
        int cut = text.indexOf(" \u00b7 ");
        return cut > 0 ? text.substring(0, cut) : text;
    }

    /** The dock height scale a Dock size value (percent) writes. */
    public static float dockScaleFor(int percent) {
        return Control.DOCK_SIZE.clamp(percent) / 100f;
    }

    /** Where the Dock size slider stands for a stored dock height scale. */
    public static int dockSizeValueFor(float scale) {
        return Control.DOCK_SIZE.clamp(Math.round(scale * 100f / DOCK_SIZE_STEP_PERCENT)
            * DOCK_SIZE_STEP_PERCENT);
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

    /** What row 2 holds (DECISIONS item 13). */
    public enum Row {
        /** Down: a Look stop. */
        NONE,
        /** The global Blur, Opacity and Grain: the Custom stop with nothing tapped. */
        GLOBAL,
        /** The tapped element's own controls. */
        ELEMENT
    }

    /**
     * Row 2 for a stop and a selection: nothing at a Look stop, the tapped element's controls at
     * Custom with one tapped, and the global row at Custom with nothing tapped.
     */
    @NonNull
    public static Row rowFor(int stop, @Nullable Target target) {
        if (!isCustomStop(stop))
            return Row.NONE;
        return target == null ? Row.GLOBAL : Row.ELEMENT;
    }

    /**
     * Whether a tap on the bare wallpaper deselects and brings the global set back: at the Custom
     * stop it does; at a Look stop there is nothing selected and nothing to open, so it does
     * nothing. The wallpaper has no controls of its own any more (Soften and Dim left the editor).
     */
    public static boolean wallpaperTapDeselects(int stop) {
        return isCustomStop(stop);
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

    /** Blur is one value for every surface, in dp. */
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

    /** The terminal's own margin ceiling, which the shared Margin never writes past. */
    public static final int TERMINAL_MARGIN_MAX_DP = 24;

    public static int cornersDp(int value) {
        return clamp(value, 0, CORNERS_MAX_DP);
    }

    public static int marginDp(int value) {
        return clamp(value, 0, MARGIN_MAX_DP);
    }

    /** The terminal's margin (its pane gap) a Margin value writes. */
    public static int terminalMarginDp(int margin) {
        return Math.min(TERMINAL_MARGIN_MAX_DP, marginDp(margin));
    }

    /**
     * Where Margin stands: the side gap under both Styles, the air round Floating cards and the
     * gutter round the Docked insert. The Style and the pane gap no longer change the read.
     */
    public static int marginValueFor(boolean floating, int sideGap, int paneGap) {
        return marginDp(sideGap);
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
