package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.layouteditor.LayoutEditorPlan.TrayItem;

/**
 * The things the editor's stack of cards can hold when the edited layout puts them away: the
 * Layout editor's tray ({@link TrayItem}) plus the dock's Search field and Drawer tile. The order
 * here is the order the stack lists them in.
 */
enum HiddenItem {
    STATUS_BAR(TrayItem.STATUS_BAR, R.string.settings_layout_miniature_status, EditorCard.STATUS),
    PINNED_APPS(TrayItem.PINNED_APPS, R.string.settings_layout_miniature_apps, EditorCard.DOCK),
    AZ_INDEX(TrayItem.AZ_INDEX, R.string.settings_layout_miniature_alphabets, EditorCard.DOCK),
    EXTRA_KEYS(TrayItem.EXTRA_KEYS, R.string.settings_layout_miniature_keys, EditorCard.KEYBOARD),
    KEYBOARD(TrayItem.KEYBOARD, R.string.appearance_card_keyboard, EditorCard.KEYBOARD),
    SEARCH_FIELD(null, R.string.appearance_row_search_field, EditorCard.DOCK),
    DRAWER_TILE(null, R.string.layout_editor_drawer_tile, EditorCard.DOCK);

    /** The tray chip this item is, or null for the dock's Search field and Drawer tile. */
    @Nullable final TrayItem tray;
    @StringRes final int labelRes;
    /** The card that opens for the item once it is back. */
    @NonNull final EditorCard card;

    HiddenItem(@Nullable TrayItem tray, @StringRes int labelRes, @NonNull EditorCard card) {
        this.tray = tray;
        this.labelRes = labelRes;
        this.card = card;
    }

    @Nullable
    static HiddenItem of(@NonNull TrayItem tray) {
        for (HiddenItem item : values()) {
            if (item.tray == tray)
                return item;
        }
        return null;
    }
}
