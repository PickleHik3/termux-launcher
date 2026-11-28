package com.termux.app.editmode;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The doors into edit mode from outside the launcher's screen: Settings search
 * ({@code EditModeSearch}), whose hit on a look opens edit mode on that look's card (spec "Edit
 * mode and Settings"), through the Intent {@code SettingsEditorDoors.editModeIntent} builds.
 * Offered only while the Terl desktop is switched on.
 */
public final class EditModeDoor {

    private EditModeDoor() { }

    /** The Intent extra naming the card to open: a {@link EditModeController.Card} or {@link #AT_REST}. */
    public static final String EXTRA_CARD = "com.termux.app.extra.EDIT_MODE_CARD";
    /** Opens edit mode with no card raised. */
    public static final String AT_REST = "rest";
    /**
     * The Windows card's door (#451): the window menu's Edit look. It names
     * {@link EditModeController.Card#WINDOWS}, so edit mode opens on that card.
     */
    public static final String WINDOWS = "WINDOWS";
    /** Search hits that open edit mode carry this in place of a Settings page's class. */
    static final String SCREEN_PREFIX = "editmode:";

    /** Whether edit mode's doors are offered: the Terl desktop is switched on. */
    public static boolean offered(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences != null && preferences.isTerlDesktopEnabled();
    }

    /** The page a search hit carries to open edit mode on {@code extra}'s card (or at rest). */
    @NonNull
    public static String screenOf(@NonNull String extra) {
        return SCREEN_PREFIX + extra;
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
