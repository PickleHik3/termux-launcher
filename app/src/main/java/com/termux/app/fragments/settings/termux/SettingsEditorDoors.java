package com.termux.app.fragments.settings.termux;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.SettingsMotion;
import com.termux.app.fragments.settings.SettingsTransitions;
import com.termux.app.surfaces.AppearanceEditorEntry;
import com.termux.app.surfaces.EditorCard;

/**
 * The Settings rows that open an editor over the live launcher rather than a page of rows: the
 * root's Appearance row, the Appearance rows at the top of Home, Terminal and Keyboard (each on its own object's card);
 * a search hit on a row the editor holds (SettingsTree's Appearance door) opens it too. The launcher
 * comes forward growing out of the row that opened it (the window's scale-up from the row's
 * bounds: the container transform's equivalent across two activities), or at once under reduced
 * motion. Every one of these marks its Intent {@code EXTRA_APPEARANCE_FROM_SETTINGS}, so closing
 * the editor comes back to Settings.
 */
public final class SettingsEditorDoors {

    private SettingsEditorDoors() {}

    /**
     * The Intent that opens the Appearance editor on {@code card} (null: at rest), marked as coming
     * from Settings. Every Settings door into the editor is built here.
     */
    @NonNull
    public static Intent editorIntent(@NonNull Context context, @Nullable EditorCard card) {
        return AppearanceEditorEntry.intent(context, card, true);
    }

    /**
     * The card that holds a searchable Settings row the editor owns (SettingsTree's Appearance
     * door), by the row's key. Widgets on glass is Home's; every other row the editor holds
     * (theme, wallpaper, colours, icons, full screen) is the Appearance card's, as is any key not
     * listed.
     */
    @NonNull
    public static EditorCard cardForSearchKey(@Nullable String key) {
        if ("widgets_on_glass".equals(key)) return EditorCard.HOME;
        return EditorCard.APPEARANCE;
    }

    /** Opens the editor on {@code card} (null: at rest) from {@code row} on {@code page}. */
    public static void openEditor(@NonNull PreferenceFragmentCompat page, @NonNull Preference row,
                                  @Nullable EditorCard card) {
        Context context = page.getContext();
        if (context == null) return;
        open(page, row, editorIntent(context, card));
    }

    /** The Intent that opens Edit pages over Home, marked as coming from Settings. */
    @NonNull
    public static Intent homePagesEditorIntent(@NonNull Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_HOME_PAGES_EDITOR, true);
        intent.putExtra(TermuxActivity.EXTRA_APPEARANCE_FROM_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    /** Starts {@code intent} from {@code row} on {@code page}, growing out of the row. */
    public static void open(@NonNull PreferenceFragmentCompat page, @NonNull Preference row,
                            @NonNull Intent intent) {
        Context context = page.getContext();
        if (context == null) return;
        page.startActivity(intent, growFrom(context, SettingsTransitions.rowView(page, row)));
    }

    /** The scale-up from {@code row}'s bounds, or null (the system's own motion) when it cannot or should not. */
    @Nullable
    static Bundle growFrom(@NonNull Context context, @Nullable View row) {
        if (row == null || row.getWidth() == 0 || SettingsMotion.isInstant(context)) return null;
        return ActivityOptions.makeScaleUpAnimation(row, 0, 0, row.getWidth(), row.getHeight()).toBundle();
    }
}
