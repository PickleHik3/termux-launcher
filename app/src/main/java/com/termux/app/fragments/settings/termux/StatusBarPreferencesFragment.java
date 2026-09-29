package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

/**
 * The status bar's own page: the 12-hour choice and the CPU/memory/weather cards. Media and pinned
 * notifications, and the rules that decide which ones stay pinned, live on the Notifications page;
 * the last row here links to it.
 *
 * <p>Splits the status half out of the old combined Terminal &amp; status page; the terminal half
 * is now {@link TerminalPreferencesFragment}. The clock's face and position, and the status bar's
 * surface, are set in the Appearance editor, so this page repeats none of them.
 */
@Keep
public final class StatusBarPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TerminalIOPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.status_bar_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_cpu"));
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_ram"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_status_bar);
        }
    }
}
