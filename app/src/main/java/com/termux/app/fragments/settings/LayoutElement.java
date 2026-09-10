package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.fragments.settings.PlaceMiniatureView.Block;
import com.termux.app.wall.PaneWallPage;

/**
 * One element of a place's arrangement, as the Layout page lists it: a row with a swatch, a name
 * and its portrait and landscape values, and a chooser behind it. The order here is the order the
 * rows stand in, and the swatch is the colour the miniature draws that element in, so the picture
 * and the row read as one thing.
 */
public enum LayoutElement {

    STATUS_BAR("layout_row_status_bar", R.string.settings_layout_status_bar_title, Block.STATUS_BAR),
    PINNED_APPS("layout_row_pinned_apps", R.string.settings_show_pinned_apps_title, Block.APPS_ROW),
    AZ_INDEX("layout_row_az_index", R.string.settings_layout_miniature_alphabets,
        Block.ALPHABETS_ROW),
    EXTRA_KEYS("layout_row_extra_keys", R.string.settings_layout_miniature_keys, Block.EXTRA_KEYS),
    KEYBOARD("layout_row_keyboard", R.string.settings_layout_keyboard_title, null),
    /** Home only: the widget grid stands where the canvas is on every other place. */
    WIDGET_GRID("layout_row_widget_grid", R.string.settings_layout_widget_grid_title, Block.CANVAS);

    @NonNull private final String mKey;
    @StringRes private final int mTitleRes;
    @Nullable private final Block mBlock;

    LayoutElement(@NonNull String key, @StringRes int titleRes, @Nullable Block block) {
        mKey = key;
        mTitleRes = titleRes;
        mBlock = block;
    }

    @NonNull
    public String key() {
        return mKey;
    }

    @StringRes
    public int titleRes() {
        return mTitleRes;
    }

    /** Whether this element is offered on a place at all. */
    public boolean isOn(@NonNull PaneWallPage place) {
        return this != WIDGET_GRID || place == PaneWallPage.WIDGETS;
    }

    /** The element a tap on one of the miniature's bands opens the chooser for. */
    @NonNull
    public static LayoutElement forBlock(@NonNull Block block, @NonNull PaneWallPage place) {
        switch (block) {
            case STATUS_BAR: return STATUS_BAR;
            case APPS_ROW: return PINNED_APPS;
            case ALPHABETS_ROW: return AZ_INDEX;
            case EXTRA_KEYS: return EXTRA_KEYS;
            case CANVAS:
            default:
                return place == PaneWallPage.WIDGETS ? WIDGET_GRID : KEYBOARD;
        }
    }

    /** The swatch colour: the band's own colour, or the keyboard's, which has no band. */
    @ColorInt
    public int swatchColor(@NonNull Context context) {
        if (mBlock != null) return PlaceMiniatureView.blockColor(context, mBlock);
        return MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimaryContainer,
            ContextCompat.getColor(context, R.color.termux_primary_container));
    }
}
