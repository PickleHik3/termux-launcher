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
 * Terminal: the section for the Terminal half. The overview is this class itself, a Layout row and
 * one row per part (windows and panes, status bar, text and display, clipboard, Linux apps); each
 * focused subpage is a thin subclass naming its own XML and title, sharing this store, so every
 * row writes the key it always did whichever page holds it. Every lookup here is null-tolerant.
 */
@Keep
public class TerminalPreferencesFragment extends MaterialPreferenceFragment {

    @androidx.annotation.XmlRes
    protected int preferencesXml() {
        return R.xml.terminal_preferences;
    }

    @androidx.annotation.StringRes
    protected int titleRes() {
        return R.string.settings_destination_terminal;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new TerminalPreferencesDataStore(context));
        setPreferencesFromResource(preferencesXml(), rootKey);
        androidx.preference.Preference retro = findPreference("terminal_retro_effect");
        if (retro != null) retro.setVisible(com.termux.app.terminal.PaneRetroEffect.available());
        // A build made without the X server has no display to set up.
        androidx.preference.Preference display = findPreference("display");
        if (display != null && !com.termux.BuildConfig.X11_SERVER) display.setVisible(false);
        androidx.preference.Preference layout = findPreference("terminal_layout_editor");
        if (layout != null) layout.setOnPreferenceClickListener(row -> {
            SettingsEditorDoors.openLayoutEditor(this, com.termux.app.wall.PaneWallPage.TERMINAL);
            return true;
        });
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(titleRes());
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
            else if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)
                || "terminal_padding_fill".equals(key)) view.putBoolean(key, value);
            else if ("show_in_recents_when_not_default".equals(key)) style.putBoolean(key, value);
            else io.putBoolean(key, value);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if ("split_pane_controls".equals(key)) return !io.getBoolean("compatibility_mode", !fallback);
            if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)
                || "terminal_padding_fill".equals(key)) return view.getBoolean(key, fallback);
            if ("show_in_recents_when_not_default".equals(key)) return style.getBoolean(key, fallback);
            return io.getBoolean(key, fallback);
        }

        private static boolean isViewStringKey(String key) {
            return "terminal_cursor_trail_style".equals(key) || "terminal_retro_effect".equals(key);
        }

        @Override public void putString(String key, @Nullable String value) {
            if (isViewStringKey(key)) view.putString(key, value); else io.putString(key, value);
        }
        @Override public String getString(String key, @Nullable String fallback) {
            return isViewStringKey(key) ? view.getString(key, fallback) : io.getString(key, fallback);
        }
    }
}
