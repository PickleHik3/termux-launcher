package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.List;

/**
 * The Appearance editor's arithmetic (appearance-layout-editor SPEC §3.3–3.4), as pure functions.
 *
 * <p>The Look slider has five stops: the four Looks in {@link SurfacePresets#presets()} order —
 * Clear, Mist, Tint, Solid — and Custom last. Custom's row 2 folds each tapped element to a few
 * controls, and each control writes one or two stored keys along a fixed rule: Darkness moves the
 * terminal's own opacity and the tint together, Key corners is the key caps' radius alone, and
 * Soft wallpaper is a fixed dim plus a fixed blur that Dim then adds to.</p>
 *
 * <p>Layout mode's Corners and Margin (SPEC §3.5) are here too: the global shape, which no Look
 * sets (2026-10-01).</p>
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

    /** The elements a tap in the frame can select. Wallpaper is any bare area. */
    public enum Target {
        STATUS(SurfaceSlot.STATUS),
        TERMINAL(SurfaceSlot.CANVAS),
        DOCK(SurfaceSlot.DOCK),
        KEYBOARD(SurfaceSlot.KEYBOARD),
        WALLPAPER(null);

        /** The surface this target is, or null for the wallpaper, which is none. */
        @Nullable public final SurfaceSlot slot;

        Target(@Nullable SurfaceSlot slot) {
            this.slot = slot;
        }

        /**
         * Whether row 2 has a first control: Darkness, Key corners or Soft wallpaper. The status
         * bar and the dock have Blur alone.
         */
        public boolean hasFirstControl() {
            return this != STATUS && this != DOCK;
        }

        /**
         * Whether row 2 carries Legibility: the terminal only, since what it changes is the
         * terminal palette's contrast (2026-10-01).
         */
        public boolean hasLegibility() {
            return this == TERMINAL;
        }

        /** Whether the second control is the one shared Blur (every target but the wallpaper). */
        public boolean secondControlIsBlur() {
            return this != WALLPAPER;
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

    // ------------------------------------------------------------------------------ the controls

    /** Blur is one value for every surface, in dp. */
    public static final int BLUR_MAX_DP = 30;

    public static int blurDp(int value) {
        return clamp(value, 0, BLUR_MAX_DP);
    }

    /** At or above this Darkness the tint is Obsidian; below it the scheme's own. */
    public static final int DARKNESS_OBSIDIAN_FROM = 50;

    /** The terminal's own opacity a Darkness value writes. */
    public static int darknessOpacity(int darkness) {
        return clamp(darkness, 0, 100);
    }

    /** The glass tint a Darkness value writes. */
    @NonNull
    public static String darknessTint(int darkness) {
        return clamp(darkness, 0, 100) >= DARKNESS_OBSIDIAN_FROM
            ? TERMUX_APP.GLASS_TINT_OBSIDIAN : TERMUX_APP.GLASS_TINT_SCHEME;
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
