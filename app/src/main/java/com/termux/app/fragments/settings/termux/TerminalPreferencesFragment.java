package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

/**
 * The terminal's own page: panes and their tmux-style controls, lazy mode, full screen, system
 * keyboard compatibility, and whether the launcher stays reachable from Recents.
 *
 * <p>Splits the terminal half out of the old combined Terminal &amp; status page; the clock and
 * status-widget half is now {@link StatusBarPreferencesFragment}.
 */
@Keep
public class TerminalPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new TerminalPreferencesDataStore(context));
        setPreferencesFromResource(R.xml.terminal_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_terminal);
        }
    }

    private static final class TerminalPreferencesDataStore extends PreferenceDataStore {
        private final TerminalIOPreferencesDataStore io;
        private final TerminalViewPreferencesDataStore view;
        private final TermuxStylePreferencesDataStore style;

        TerminalPreferencesDataStore(Context context) {
            io = TerminalIOPreferencesDataStore.getInstance(context);
            view = TerminalViewPreferencesDataStore.getInstance(context);
            style = TermuxStylePreferencesDataStore.getInstance(context);
        }

        @Override public void putBoolean(String key, boolean value) {
            if ("split_pane_controls".equals(key)) io.putBoolean("compatibility_mode", !value);
            else if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) view.putBoolean(key, value);
            else if ("show_in_recents_when_not_default".equals(key)) style.putBoolean(key, value);
            else io.putBoolean(key, value);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if ("split_pane_controls".equals(key)) return !io.getBoolean("compatibility_mode", !fallback);
            if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) return view.getBoolean(key, fallback);
            if ("show_in_recents_when_not_default".equals(key)) return style.getBoolean(key, fallback);
            return io.getBoolean(key, fallback);
        }

        @Override public void putString(String key, @Nullable String value) { io.putString(key, value); }
        @Override public String getString(String key, @Nullable String fallback) { return io.getString(key, fallback); }
    }
}
