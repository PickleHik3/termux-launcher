package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeControls;
import com.termux.app.editmode.EditModeDoor;

import java.util.ArrayList;
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

    /** Every edit mode hit there is, for a search that ranks them itself (Terl's). */
    @NonNull
    public static List<SettingsSearchIndex.Entry> all(@NonNull Context context) {
        return entries(context::getString);
    }

    /** The words people say for edit mode besides its title (spec "Edit mode and Settings"). */
    public static final String DOOR_WORDS = "appearance edit look";

    /** The searchable looks: edit mode itself, and each control the cards hold. */
    @VisibleForTesting
    @NonNull
    static List<SettingsSearchIndex.Entry> entries(@NonNull IntFunction<String> strings) {
        String trail = strings.apply(R.string.edit_mode_title);
        List<SettingsSearchIndex.Entry> entries = new ArrayList<>();
        entries.add(new SettingsSearchIndex.Entry(trail,
            strings.apply(R.string.edit_mode_search_summary) + " " + DOOR_WORDS,
            strings.apply(R.string.title_activity_termux_settings),
            EditModeDoor.screenOf(EditModeDoor.AT_REST), null, SettingsTree.Section.APPEARANCE));
        // Each control and the card it opens come from the one list (EditModeControls).
        for (EditModeControls.Control control : EditModeControls.all()) {
            entries.add(new SettingsSearchIndex.Entry(strings.apply(control.titleRes), null,
                trail + SettingsSearchIndex.PATH_SEPARATOR + strings.apply(cardTitle(control.card)),
                EditModeDoor.screenOf(control.card.name()), control.id, SettingsTree.Section.APPEARANCE));
        }
        return entries;
    }

    private static int cardTitle(@NonNull EditModeController.Card card) {
        switch (card) {
            case KEYBOARD: return R.string.edit_mode_card_keyboard;
            case BAR: return R.string.edit_mode_card_control_bar;
            case WINDOWS: return R.string.edit_mode_card_windows;
            case WALLPAPER: return R.string.edit_mode_card_wallpaper;
            case HOME: return R.string.edit_mode_card_home;
            default: return R.string.edit_mode_card_global;
        }
    }
}
