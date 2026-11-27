package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeDoor;
import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Edit mode's rows in Settings search while the Terl desktop is on: edit mode itself, and each look
 * a card holds. A hit carries {@link EditModeDoor#screenOf} in place of a page's class, and opens
 * edit mode on that card ({@link SettingsSearchResults}).
 */
public final class EditModeSearch {

    private EditModeSearch() { }

    /** Edit mode's search hits for {@code query}, best first. */
    @NonNull
    public static List<SettingsSearchIndex.Entry> search(@NonNull Context context,
                                                         @NonNull String query, int limit) {
        return new SettingsSearchIndex(entries(context::getString)).search(query, null, limit);
    }

    /** The searchable looks: edit mode itself, and each row of a card. */
    @VisibleForTesting
    @NonNull
    static List<SettingsSearchIndex.Entry> entries(@NonNull IntFunction<String> strings) {
        String trail = strings.apply(R.string.edit_mode_title);
        String keyboard = EditModeDoor.screenOf(EditModeController.Card.KEYBOARD.name());
        String bar = EditModeDoor.screenOf(EditModeController.Card.BAR.name());
        String global = EditModeDoor.screenOf(EditModeController.Card.GLOBAL.name());
        String globalTrail = trail + SettingsSearchIndex.PATH_SEPARATOR + strings.apply(R.string.edit_mode_card_global);
        return Arrays.asList(
            new SettingsSearchIndex.Entry(trail, strings.apply(R.string.edit_mode_search_summary),
                strings.apply(R.string.title_activity_termux_settings),
                EditModeDoor.screenOf(EditModeDoor.AT_REST), null, SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_keyboard_opacity), null,
                trail, keyboard, TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.OPACITY),
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_keyboard_blur), null,
                trail, keyboard, TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.BLUR),
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_bar_opacity), null,
                trail, bar, TerlLookIds.of(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY),
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_bar_blur), null,
                trail, bar, TerlLookIds.of(TerlSurface.CONTROL_BAR, TerlProperty.BLUR),
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_look), null,
                globalTrail, global, TerlLookIds.LOOK, SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_corners), null,
                globalTrail, global, TerlLookIds.CORNERS, SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_tint), null,
                globalTrail, global, TerlLookIds.TINT, SettingsTree.Section.APPEARANCE));
    }
}
