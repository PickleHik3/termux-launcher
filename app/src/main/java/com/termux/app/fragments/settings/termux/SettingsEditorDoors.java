package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.termux.app.TermuxActivity;
import com.termux.app.wall.PaneWallPage;

/**
 * The Settings rows that open an editor over the live launcher rather than a page of rows: the
 * Layout rows at the top of Home and of Terminal. The launcher comes forward on the named place
 * with the editor open; closing the editor comes back to Settings, as Look's Appearance row does.
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

    public static void openLayoutEditor(@NonNull Fragment fragment, @NonNull PaneWallPage place) {
        Context context = fragment.getContext();
        if (context == null) return;
        fragment.startActivity(layoutEditorIntent(context, place));
    }
}
