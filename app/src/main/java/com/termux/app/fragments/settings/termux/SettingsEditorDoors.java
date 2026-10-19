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
import com.termux.app.wall.PaneWallPage;

/**
 * The Settings rows that open an editor over the live launcher rather than a page of rows: the
 * Layout rows at the top of Home and of Terminal, Look's Appearance row and Keyboard's. The launcher
 * comes forward growing out of the row that opened it (the window's scale-up from the row's
 * bounds: the container transform's equivalent across two activities), or at once under reduced
 * motion. Closing the Layout or Appearance editor comes back to Settings.
 */
public final class SettingsEditorDoors {

    private SettingsEditorDoors() {}

    /** The Intent that opens the Layout editor on {@code place}, marked as coming from Settings. */
    @NonNull
    public static Intent layoutEditorIntent(@NonNull Context context, @NonNull PaneWallPage place) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR, true);
        intent.putExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR_PLACE, place.toolName());
        intent.putExtra(TermuxActivity.EXTRA_APPEARANCE_FROM_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static void openLayoutEditor(@NonNull PreferenceFragmentCompat page, @NonNull Preference row,
                                        @NonNull PaneWallPage place) {
        Context context = page.getContext();
        if (context == null) return;
        open(page, row, layoutEditorIntent(context, place));
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
