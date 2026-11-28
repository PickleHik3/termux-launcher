package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one list of the controls edit mode holds, and the card each lives on. Settings search lists
 * them ({@code EditModeSearch}), a hit opens edit mode on the card, and Copy as config on a hit
 * uses {@link Control#id}. The card builders in {@link EditModeController} place the same
 * settings; {@code EditModeControlsTest} reads their source and fails when a builder places a
 * slider this list does not put on that card.
 */
public final class EditModeControls {

    private EditModeControls() { }

    /** One control: the setting a card row changes, its card, its title and its registry id. */
    public static final class Control {
        @NonNull public final EditSession.Setting setting;
        @NonNull public final EditModeController.Card card;
        /** The settings registry id (what Copy as config and terl.conf name); null when it has none. */
        @Nullable public final String id;
        /** The title a search hit shows. */
        public final int titleRes;
        /** A second card that places the same row (Text size: Global and the Windows card's Text tab). */
        @Nullable public EditModeController.Card alsoOn;

        Control(@NonNull EditSession.Setting setting, @NonNull EditModeController.Card card,
                @Nullable String id, int titleRes) {
            this.setting = setting;
            this.card = card;
            this.id = id;
            this.titleRes = titleRes;
        }
    }

    private static final List<Control> ALL = build();

    @NonNull
    private static List<Control> build() {
        List<Control> list = new ArrayList<>();
        EditModeController.Card kb = EditModeController.Card.KEYBOARD;
        EditModeController.Card global = EditModeController.Card.GLOBAL;
        EditModeController.Card wall = EditModeController.Card.WALLPAPER;
        EditModeController.Card home = EditModeController.Card.HOME;
        EditModeController.Card bar = EditModeController.Card.BAR;
        EditModeController.Card win = EditModeController.Card.WINDOWS;
        // Own Opacity and Blur of the three surfaces that may hold them.
        own(list, TerlSurface.KEYBOARD, kb, R.string.edit_mode_keyboard_opacity, R.string.edit_mode_keyboard_blur);
        // The keyboard's sizes and its extra-keys row. Height has no config line: it is the layout's,
        // one per orientation.
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.KEYBOARD_HEIGHT), kb,
            null, R.string.edit_mode_keyboard_height));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.KEY_SPACING), kb,
            com.termux.app.settings.KeySpacingAdapter.ID, R.string.edit_mode_key_spacing));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.EXTRA_KEYS), kb,
            "terl.extra_keys", R.string.edit_mode_extra_keys));
        own(list, TerlSurface.CONTROL_BAR, bar, R.string.edit_mode_bar_opacity, R.string.edit_mode_bar_blur);
        own(list, TerlSurface.WINDOWS, win, R.string.edit_mode_windows_opacity, R.string.edit_mode_windows_blur);
        // Global.
        list.add(new Control(EditSession.Setting.LOOK, global, TerlLookIds.LOOK, R.string.edit_mode_look));
        list.add(new Control(EditSession.Setting.custom(TerlProperty.OPACITY), global,
            TerlLookIds.ofCustom(TerlProperty.OPACITY), R.string.edit_mode_custom_opacity));
        list.add(new Control(EditSession.Setting.custom(TerlProperty.BLUR), global,
            TerlLookIds.ofCustom(TerlProperty.BLUR), R.string.edit_mode_custom_blur));
        list.add(new Control(EditSession.Setting.CORNERS, global, TerlLookIds.CORNERS, R.string.edit_mode_corners));
        list.add(new Control(EditSession.Setting.TINT, global, TerlLookIds.TINT, R.string.edit_mode_tint));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.DEPTH), global,
            "look.depth", R.string.edit_mode_depth));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.SHINE), global,
            "look.shine", R.string.edit_mode_shine));
        list.add(new Control(EditSession.Setting.ICON_WEIGHT, global, TerlLookIds.ICON_WEIGHT,
            R.string.edit_mode_icon_weight));
        list.add(new Control(EditSession.Setting.ICON_PACK, global, null, R.string.edit_mode_icon_pack));
        Control textSize = new Control(EditSession.Setting.TEXT_SIZE, global, "text.size", R.string.edit_mode_text_size);
        textSize.alsoOn = win;
        list.add(textSize);
        // Wallpaper & colours.
        list.add(new Control(EditSession.Setting.DIM, wall, null, R.string.edit_mode_dim));
        list.add(new Control(EditSession.Setting.DRIFT, wall, null, R.string.edit_mode_drift));
        list.add(new Control(EditSession.Setting.THEME, wall, null, R.string.edit_mode_theme));
        list.add(new Control(EditSession.Setting.COLOURS, wall, null, R.string.edit_mode_colours_from_wallpaper));
        // Home.
        list.add(new Control(EditSession.Setting.COLUMNS, home, null, R.string.edit_mode_columns));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.LABELS_SHOWN), home,
            null, R.string.edit_mode_labels));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.ICON_SIZE), home,
            null, R.string.edit_mode_icon_size));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.MONOCHROME), home,
            null, R.string.edit_mode_monochrome));
        list.add(new Control(EditSession.Setting.PAGE_DOTS, home, "home.page_dots", R.string.edit_mode_page_dots));
        list.add(new Control(EditSession.Setting.AZ_EDGE, home, "home.az_edge", R.string.edit_mode_az));
        // Control bar.
        list.add(new Control(EditSession.Setting.BAR_MARGIN, bar, "bar.margin", R.string.edit_mode_margin));
        list.add(new Control(EditSession.Setting.BAR_READING, bar, "bar.reading", R.string.edit_mode_reading));
        list.add(new Control(EditSession.Setting.BAR_RAIL, bar, "bar.rail_side", R.string.edit_mode_rail_side));
        // Windows: the Window tab, then the Text tab.
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.GAP), win,
            "windows.gap", R.string.edit_mode_gap));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.RIM), win,
            "windows.rim", R.string.edit_mode_rim));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.RIM_COLOUR), win,
            "windows.rim_colour", R.string.edit_mode_rim_colour));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.UNFOCUSED_DIM), win,
            "windows.unfocused_dim", R.string.edit_mode_unfocused_dim));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.FONT), win, null,
            R.string.edit_mode_font));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.CONTRAST), win,
            "terminal.contrast", R.string.edit_mode_contrast));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.CURSOR_TRAIL), win, null,
            R.string.edit_mode_cursor_trail));
        // The row shows dp; its config line is the registry's, in px (as terl.conf has always had it).
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.FONT_SIZE), win,
            com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter.FONT_SIZE,
            R.string.edit_mode_font_size));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.SCHEME), win,
            com.termux.app.settings.TerminalColoursAdapter.ID, R.string.edit_mode_colour_scheme));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.CURSOR_STYLE), win,
            "terminal.cursor_style", R.string.edit_mode_cursor));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.CURSOR_BLINK), win,
            "terminal.cursor_blink", R.string.edit_mode_cursor_blink));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.EFFECT), win,
            "terminal.effect", R.string.edit_mode_effect));
        // The Text tab's Effects, folded away until asked for.
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.SCREEN_EVENTS), win,
            "terminal.screen_events", R.string.edit_mode_screen_events));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.COMPOSITE), win,
            "terminal.effect_composite", R.string.edit_mode_composite));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.IDLE_ANIMATIONS), win,
            "motion.idle_animations", R.string.edit_mode_idle_animations));
        list.add(new Control(EditSession.Setting.keyedChoice(EditSession.Keyed.IDLE_TEAR_RATE), win,
            "motion.idle_tear_rate", R.string.edit_mode_glitch_rate));
        list.add(new Control(EditSession.Setting.keyedNumber(EditSession.Keyed.ADAPTIVE_LEGIBILITY), win,
            "terminal.adaptive_legibility", R.string.edit_mode_adaptive_legibility));
        return Collections.unmodifiableList(list);
    }

    private static void own(@NonNull List<Control> list, @NonNull TerlSurface surface,
                            @NonNull EditModeController.Card card, int opacityTitle, int blurTitle) {
        list.add(new Control(EditSession.Setting.own(surface, TerlProperty.OPACITY), card,
            TerlLookIds.of(surface, TerlProperty.OPACITY), opacityTitle));
        list.add(new Control(EditSession.Setting.own(surface, TerlProperty.BLUR), card,
            TerlLookIds.of(surface, TerlProperty.BLUR), blurTitle));
    }

    /** Every control edit mode holds. */
    @NonNull
    public static List<Control> all() {
        return ALL;
    }

    /** The control for {@code setting}; null for a setting no row changes. */
    @Nullable
    public static Control of(@NonNull EditSession.Setting setting) {
        for (Control control : ALL) if (control.setting.equals(setting)) return control;
        return null;
    }

    /**
     * The registry id of the control a card row titled {@code titleRes} changes (a row's label: Tint,
     * Rim colour, Contrast…); null for a title no control has, or one with no id.
     */
    @Nullable
    public static String idOfTitle(int titleRes) {
        for (Control control : ALL) if (control.titleRes == titleRes) return control.id;
        return null;
    }

    /** The card holding the control whose registry id is {@code id}; null for any other id. */
    @Nullable
    public static EditModeController.Card cardOf(@NonNull String id) {
        for (Control control : ALL) if (id.equals(control.id)) return control.card;
        return null;
    }
}
