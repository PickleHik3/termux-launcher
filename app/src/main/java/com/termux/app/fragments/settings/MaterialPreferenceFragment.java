package com.termux.app.fragments.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Base preference fragment that renders ListPreference / EditTextPreference dialogs as
 * rounded Material dialogs (via {@link SettingsMaterialDialogs}) instead of the platform
 * AppCompat alert dialog. Settings fragments extend this to pick up the redesigned look.
 */
@Keep
public abstract class MaterialPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (getContext() != null && SettingsMaterialDialogs.show(getContext(), preference)) {
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @NonNull
    @Override
    public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater,
                                             @NonNull ViewGroup parent, Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        // No change animations: the download catalogs update rows in place on every progress
        // tick, and the default cross-fade binds a second holder per change — visible as a
        // flicker, and on Nothing OS (Android 16) as a ghost insertion cursor over the last
        // glyph of every freshly bound button label.
        recyclerView.setItemAnimator(null);
        // The last row scrolls fully clear of the bottom edge, with room to spare, instead of
        // ending flush against the navigation bar.
        int bottom = Math.round(24f * recyclerView.getResources().getDisplayMetrics().density);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
            recyclerView.getPaddingRight(), recyclerView.getPaddingBottom() + bottom);
        return recyclerView;
    }
}
