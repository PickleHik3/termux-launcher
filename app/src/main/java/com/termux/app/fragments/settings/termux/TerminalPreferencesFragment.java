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
 * Terminal: the section for the Terminal half. The overview is this class itself, an Appearance
 * row and one row per part (windows and panes, status bar, text and display, clipboard); each
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
        androidx.preference.Preference events = findPreference("terminal_screen_events");
        if (events != null) events.setVisible(com.termux.app.terminal.PaneRetroEffect.available());
        androidx.preference.Preference composite = findPreference("terminal_retro_composite");
        if (composite != null) composite.setVisible(com.termux.app.terminal.PaneRetroEffect.available());
        androidx.preference.Preference engine = findPreference("terminal_engine");
        if (engine != null) engine.setVisible(com.termux.terminal.TerminalEngine.isAvailable());
        androidx.preference.Preference clipboardReset = findPreference("terminal_clipboard_read_reset");
        if (clipboardReset != null) {
            clipboardReset.setVisible(com.termux.terminal.TerminalEngine.isAvailable());
            clipboardReset.setOnPreferenceClickListener(row -> {
                com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences =
                    com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(context, false);
                if (preferences != null) preferences.clearClipboardReadAlways();
                android.widget.Toast.makeText(context, R.string.msg_clipboard_permissions_reset,
                    android.widget.Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        androidx.preference.Preference layout = findPreference("terminal_layout_editor");
        if (layout != null) layout.setOnPreferenceClickListener(row -> {
            SettingsEditorDoors.openEditor(this, row,
                com.termux.app.surfaces.EditorDoor.SETTINGS_TERMINAL);
            return true;
        });
        androidx.preference.Preference notifications = findPreference("terminal_notifications");
        if (notifications != null) notifications.setOnPreferenceClickListener(row -> {
            openNotificationSettings(context);
            return true;
        });
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    /** The app's own notification settings, where each kind has its channel. */
    private static void openNotificationSettings(Context context) {
        android.content.Intent intent = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            ? new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName())
            : new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.getPackageName()));
        try {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException ignored) {
            // No settings screen to open on this device.
        }
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
                || "terminal_screen_events".equals(key) || "terminal_retro_composite".equals(key))
                view.putBoolean(key, value);
            else if ("show_in_recents_when_not_default".equals(key)) style.putBoolean(key, value);
            else io.putBoolean(key, value);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if ("split_pane_controls".equals(key)) return !io.getBoolean("compatibility_mode", !fallback);
            if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)
                || "terminal_screen_events".equals(key) || "terminal_retro_composite".equals(key))
                return view.getBoolean(key, fallback);
            if ("show_in_recents_when_not_default".equals(key)) return style.getBoolean(key, fallback);
            return io.getBoolean(key, fallback);
        }

        private static boolean isViewStringKey(String key) {
            return "terminal_cursor_trail_style".equals(key) || "terminal_retro_effect".equals(key)
                || "terminal_engine".equals(key);
        }

        @Override public void putString(String key, @Nullable String value) {
            if (isViewStringKey(key)) view.putString(key, value); else io.putString(key, value);
        }
        @Override public String getString(String key, @Nullable String fallback) {
            return isViewStringKey(key) ? view.getString(key, fallback) : io.getString(key, fallback);
        }
    }
}
