package com.termux.app.editmode;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchIndex;
import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The doors into edit mode from outside the launcher's screen: Settings search, whose hit on a look
 * opens edit mode on that look's card (spec "Edit mode and Settings"), through the Intent
 * {@code SettingsEditorDoors.editModeIntent} builds.
 * Offered only while the Terl desktop is switched on.
 */
public final class EditModeDoor {

    private EditModeDoor() { }

    /** The Intent extra naming the card to open: a {@link EditModeController.Card} or {@link #AT_REST}. */
    public static final String EXTRA_CARD = "com.termux.app.extra.EDIT_MODE_CARD";
    /** Opens edit mode with no card raised. */
    public static final String AT_REST = "rest";
    /** Search hits that open edit mode carry this in place of a Settings page's class. */
    static final String SCREEN_PREFIX = "editmode:";

    /** Whether edit mode's doors are offered: the Terl desktop is switched on. */
    public static boolean offered(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences != null && preferences.isTerlDesktopEnabled();
    }

    /** Edit mode's search hits for {@code query}, best first. */
    @NonNull
    public static List<SettingsSearchIndex.Entry> search(@NonNull Context context,
                                                         @NonNull String query, int limit) {
        return new SettingsSearchIndex(entries(context::getString)).search(query, null, limit);
    }

    /** The searchable looks: edit mode itself, and each row of a card. */
    @NonNull
    static List<SettingsSearchIndex.Entry> entries(@NonNull IntFunction<String> strings) {
        String trail = strings.apply(R.string.edit_mode_title);
        String keyboard = SCREEN_PREFIX + EditModeController.Card.KEYBOARD.name();
        return Arrays.asList(
            new SettingsSearchIndex.Entry(trail, strings.apply(R.string.edit_mode_search_summary),
                strings.apply(R.string.title_activity_termux_settings), SCREEN_PREFIX + AT_REST, null,
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_keyboard_opacity), null,
                trail, keyboard, TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.OPACITY),
                SettingsTree.Section.APPEARANCE),
            new SettingsSearchIndex.Entry(strings.apply(R.string.edit_mode_keyboard_blur), null,
                trail, keyboard, TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.BLUR),
                SettingsTree.Section.APPEARANCE));
    }

    /** Whether a search hit's page is edit mode's. */
    public static boolean isDoor(@NonNull String screenClass) {
        return screenClass.startsWith(SCREEN_PREFIX);
    }

    /** The {@link #EXTRA_CARD} a search hit on {@code screenClass} hands the launcher. */
    @NonNull
    public static String extraOf(@NonNull String screenClass) {
        return screenClass.substring(SCREEN_PREFIX.length());
    }

    /**
     * What an Intent's {@link #EXTRA_CARD} names: a card, or null for {@link #AT_REST} and for a
     * name this build does not know (edit mode then opens at rest).
     */
    @Nullable
    public static EditModeController.Card cardOf(@NonNull String extra) {
        for (EditModeController.Card card : EditModeController.Card.values()) {
            if (card.name().equals(extra)) return card;
        }
        return null;
    }
}
