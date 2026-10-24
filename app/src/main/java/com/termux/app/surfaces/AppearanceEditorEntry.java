package com.termux.app.surfaces;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.place.SeamDress;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

/**
 * Every door into the Appearance editor from outside the launcher's window (appearance-and-
 * settings-rework §4: "every door into the editor is named Appearance and opens on that page's
 * object"). A door names the object whose card should be up when the editor opens; null opens the
 * editor at rest.
 *
 * <pre>
 *   // Settings › Home › Appearance:
 *   SettingsEditorDoors.open(page, row, AppearanceEditorEntry.intent(context, EditorCard.HOME, true));
 *   // anywhere with a Context:
 *   AppearanceEditorEntry.open(context, EditorCard.DOCK);
 * </pre>
 *
 * <p>The intent is the launcher's own ({@link TermuxActivity#EXTRA_SURFACE_EDITOR} with the card's
 * key as {@link TermuxActivity#EXTRA_SURFACE_EDITOR_SECTION}), so the old Settings deep links that
 * name {@code dock}, {@code keyboard}, {@code status} or {@code canvas} open the same cards.
 * Doors inside the launcher's window open the editor directly on the page's own object.</p>
 */
public final class AppearanceEditorEntry {

    private AppearanceEditorEntry() {}

    /**
     * The intent that brings the launcher forward with the editor open on {@code card}.
     * {@code fromSettings} marks a Settings door: closing the editor goes back there.
     */
    @NonNull
    public static Intent intent(@NonNull Context context, @Nullable EditorCard card,
                                boolean fromSettings) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
        if (card != null)
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR_SECTION, card.key);
        if (fromSettings)
            intent.putExtra(TermuxActivity.EXTRA_APPEARANCE_FROM_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    /** Opens the editor on {@code card} from any context. */
    public static void open(@NonNull Context context, @Nullable EditorCard card) {
        Intent intent = intent(context, card, false);
        if (!(context instanceof android.app.Activity))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Where the terminal long-press menu's Appearance opens the editor: at rest, no card up, on
     * the Workspace half. The page on screen is kept when it stands on the Workspace (a terminal
     * or the display window), so the frame does not slide; anything else lands on the terminal.
     *
     * <pre>
     *   openAppearanceSurface(null, AppearanceEditorEntry.terminalMenuPlace(currentWallPlace()), null);
     * </pre>
     */
    @NonNull
    public static PaneWallPage terminalMenuPlace(@Nullable PaneWallPage onScreen) {
        return onScreen != null && SeamDress.halfOf(onScreen) == WallHalf.WORKSPACE
            ? onScreen : PaneWallPage.TERMINAL;
    }

    /** The card an intent's section names, or null for none. */
    @Nullable
    public static EditorCard cardOf(@Nullable String section) {
        return EditorCard.forKey(section);
    }
}
