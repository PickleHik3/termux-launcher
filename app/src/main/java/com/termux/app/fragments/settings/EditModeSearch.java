package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeControls;
import com.termux.app.editmode.EditModeDoor;
import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;

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

    /** The searchable looks: edit mode itself, and each control the cards hold. */
    @VisibleForTesting
    @NonNull
    static List<SettingsSearchIndex.Entry> entries(@NonNull IntFunction<String> strings) {
        String trail = strings.apply(R.string.edit_mode_title);
        List<SettingsSearchIndex.Entry> entries = new ArrayList<>();
        entries.add(new SettingsSearchIndex.Entry(trail, strings.apply(R.string.edit_mode_search_summary),
            strings.apply(R.string.title_activity_termux_settings),
            EditModeDoor.screenOf(EditModeDoor.AT_REST), null, SettingsTree.Section.APPEARANCE));
        // Each control and the card it opens come from the look ids (EditModeControls), not a table here.
        for (EditModeControls.Control control : EditModeControls.all()) {
            entries.add(new SettingsSearchIndex.Entry(title(control, strings), null,
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
            default: return R.string.edit_mode_card_global;
        }
    }

    private static String title(@NonNull EditModeControls.Control control,
                                @NonNull IntFunction<String> strings) {
        if (control.surface == null) {
            switch (control.id) {
                case TerlLookIds.LOOK: return strings.apply(R.string.edit_mode_look);
                case TerlLookIds.CORNERS: return strings.apply(R.string.edit_mode_corners);
                default: return strings.apply(R.string.edit_mode_tint);
            }
        }
        boolean opacity = control.property == TerlProperty.OPACITY;
        switch (control.surface) {
            case KEYBOARD:
                return strings.apply(opacity ? R.string.edit_mode_keyboard_opacity : R.string.edit_mode_keyboard_blur);
            case CONTROL_BAR:
                return strings.apply(opacity ? R.string.edit_mode_bar_opacity : R.string.edit_mode_bar_blur);
            default:
                return strings.apply(opacity ? R.string.edit_mode_windows_opacity : R.string.edit_mode_windows_blur);
        }
    }
}
