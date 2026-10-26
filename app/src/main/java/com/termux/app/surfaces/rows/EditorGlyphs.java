package com.termux.app.surfaces.rows;

import androidx.annotation.DrawableRes;

import com.termux.R;

/**
 * The editor's own glyphs (editor-redesign §2), by what they mean: the card waves name these, so
 * a redrawn glyph drops in as a file with no code change. The rest of the cards use Material
 * outline icons.
 */
public final class EditorGlyphs {

    private EditorGlyphs() {}

    // Position rows: a phone outline with the bar on the edge it goes to.
    @DrawableRes public static final int POS_TOP = R.drawable.ic_ed_pos_top;
    @DrawableRes public static final int POS_BOTTOM = R.drawable.ic_ed_pos_bottom;
    @DrawableRes public static final int POS_LEFT = R.drawable.ic_ed_pos_left;
    @DrawableRes public static final int POS_RIGHT = R.drawable.ic_ed_pos_right;
    @DrawableRes public static final int POS_ABOVE_KEYBOARD = R.drawable.ic_ed_pos_above_keyboard;
    @DrawableRes public static final int POS_RIDES_APPS_ROW = R.drawable.ic_ed_pos_rides_apps_row;

    // Keyboard type.
    @DrawableRes public static final int KB_DOCKED = R.drawable.ic_ed_kb_docked;
    @DrawableRes public static final int KB_FLOATING = R.drawable.ic_ed_kb_floating;
    @DrawableRes public static final int KB_SPLIT = R.drawable.ic_ed_kb_split;

    // Clock position.
    @DrawableRes public static final int CLOCK_ALIGN_LEFT = R.drawable.ic_ed_clock_align_left;
    @DrawableRes public static final int CLOCK_ALIGN_CENTER = R.drawable.ic_ed_clock_align_center;
    @DrawableRes public static final int CLOCK_ALIGN_RIGHT = R.drawable.ic_ed_clock_align_right;

    // Folder density.
    @DrawableRes public static final int FOLDER_COMPACT = R.drawable.ic_ed_folder_compact;
    @DrawableRes public static final int FOLDER_DEFAULT = R.drawable.ic_ed_folder_default;
    @DrawableRes public static final int FOLDER_ROOMY = R.drawable.ic_ed_folder_roomy;

    // Home label size and icon size.
    @DrawableRes public static final int LABEL_SMALL = R.drawable.ic_ed_label_small;
    @DrawableRes public static final int LABEL_MEDIUM = R.drawable.ic_ed_label_medium;
    @DrawableRes public static final int LABEL_LARGE = R.drawable.ic_ed_label_large;
    @DrawableRes public static final int ICON_SMALL = R.drawable.ic_ed_icon_small;
    @DrawableRes public static final int ICON_DEFAULT = R.drawable.ic_ed_icon_default;
    @DrawableRes public static final int ICON_LARGE = R.drawable.ic_ed_icon_large;

    // Keyboard strips.
    @DrawableRes public static final int KB_BACKGROUND_OPACITY =
        R.drawable.ic_ed_kb_background_opacity;
    @DrawableRes public static final int KB_KEY_OPACITY = R.drawable.ic_ed_kb_key_opacity;
    @DrawableRes public static final int BOTTOM_PADDING = R.drawable.ic_ed_bottom_padding;

    // The Dock's Bars list: the extra-keys editor's handle, and the sliders glyph.
    @DrawableRes public static final int BARS_REORDER = R.drawable.ic_drag_indicator_24;
    @DrawableRes public static final int BAR_SETTINGS = R.drawable.ic_tune_sliders_24;

    // A-Z, glass, wallpaper and the CRT colour.
    @DrawableRes public static final int GLASS_OFF = R.drawable.ic_ed_glass_off;
    @DrawableRes public static final int AZ_SHOWN = R.drawable.ic_ed_az_shown;
    @DrawableRes public static final int AZ_FAINT = R.drawable.ic_ed_az_faint;
    @DrawableRes public static final int AZ_PULL_TAB = R.drawable.ic_ed_az_pull_tab;
    @DrawableRes public static final int WALLPAPER_COLOURS = R.drawable.ic_ed_wallpaper_colours;
    @DrawableRes public static final int MATERIAL_CRT = R.drawable.ic_ed_material_crt;

    /** Every glyph above, for a test that each one inflates. */
    @DrawableRes
    public static int[] all() {
        return new int[] {POS_TOP, POS_BOTTOM, POS_LEFT, POS_RIGHT, POS_ABOVE_KEYBOARD,
            POS_RIDES_APPS_ROW, KB_DOCKED, KB_FLOATING, KB_SPLIT, CLOCK_ALIGN_LEFT,
            CLOCK_ALIGN_CENTER, CLOCK_ALIGN_RIGHT, FOLDER_COMPACT, FOLDER_DEFAULT, FOLDER_ROOMY,
            LABEL_SMALL, LABEL_MEDIUM, LABEL_LARGE, ICON_SMALL, ICON_DEFAULT, ICON_LARGE,
            KB_BACKGROUND_OPACITY, KB_KEY_OPACITY, BOTTOM_PADDING, BARS_REORDER, BAR_SETTINGS,
            GLASS_OFF, AZ_SHOWN, AZ_FAINT, AZ_PULL_TAB, WALLPAPER_COLOURS, MATERIAL_CRT};
    }
}
