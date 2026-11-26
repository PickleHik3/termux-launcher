package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.x11.DisplayStartWith;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.x11.LoriePreferences;
import com.termux.x11.Prefs;

/**
 * The Linux display's page: the switch that turns it on, how touch is read, how big the X screen
 * is, whether the clipboard is shared, and how the launcher starts a display when asked to.
 *
 * <p>The display rows write into the store the display server and {@code termux-x11-preference}
 * read — one store, so the three never disagree — and a running display picks a change up at
 * once. The starting rows are the launcher's own.
 *
 * <p>Graphics moved to the Setup GUI Apps screen (user, 2026-09-22), where it ends in a
 * command on the clipboard like the rest of that screen; the window manager row went with it,
 * leaving the one line at the foot of this page that names what arranges the windows.
 */
public class X11DisplayPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_KEYBOARD_FOLLOWS_TEXT = "x11_keyboard_follows_text";
    private static final String KEY_RESOLUTION_MODE = "displayResolutionMode";
    private static final String KEY_SCALE = "displayScale";
    private static final String KEY_RESOLUTION_EXACT = "displayResolutionExact";
    private static final String KEY_RESOLUTION_CUSTOM = "displayResolutionCustom";

    /**
     * The overview is this class, Terminal › Linux apps (the old Display section, with its Linux
     * apps rows on top); each focused subpage names its own XML and title.
     */
    @androidx.annotation.XmlRes
    protected int preferencesXml() {
        return R.xml.x11_display_preferences;
    }

    @androidx.annotation.StringRes
    protected int titleRes() {
        return R.string.settings_terminal_sub_linux_apps_title;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Child screens (GUI apps) retitle the activity; take it back when they pop.
        if (getActivity() != null) getActivity().setTitle(titleRes());
        // The guide's row says what the last run set up.
        Preference setup = findPreference("x11_gui_apps_setup");
        Context context = getContext();
        if (setup != null && context != null) {
            setup.setSummary(com.termux.app.x11.GuiAppsSetupGuide.rowSummary(context,
                com.termux.app.x11.GuiAppsSetupGuide.preferences(context),
                com.termux.app.AppEdition.of(context.getPackageName())));
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new X11DisplayPreferencesDataStore(context));
        setPreferencesFromResource(preferencesXml(), rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference scaleRow = findPreference(KEY_SCALE);
        if (scaleRow instanceof com.termux.app.fragments.settings.SliderPreference)
            ((com.termux.app.fragments.settings.SliderPreference) scaleRow).setValueSuffix("%");
        Preference dpiRow = findPreference("x11_display_dpi");
        if (dpiRow instanceof com.termux.app.fragments.settings.SliderPreference)
            ((com.termux.app.fragments.settings.SliderPreference) dpiRow).setValueSuffix(" dpi");
        ListPreference mode = findPreference(KEY_RESOLUTION_MODE);
        if (mode != null) {
            applyResolutionRows(mode.getValue());
            mode.setOnPreferenceChangeListener((preference, value) -> {
                applyResolutionRows(String.valueOf(value));
                return true;
            });
        }
        setUpStartWith();
        setUpDisplayBackend();
    }

    private static final String KEY_DISPLAY_BACKEND =
        TermuxPreferenceConstants.TERMUX_APP.KEY_LINUX_DISPLAY_BACKEND;

    /**
     * Display: X11 Display or Wayland Display, offered only in a build with the Wayland host.
     * Going back to X11 Display stops the Wayland host at once, and its apps with it.
     */
    private void setUpDisplayBackend() {
        Preference backend = findPreference(KEY_DISPLAY_BACKEND);
        Preference hint = findPreference(KEY_DISPLAY_BACKEND + "_hint");
        if (backend == null) return;
        if (!com.termux.BuildConfig.WAYLAND_DISPLAY) {
            backend.setVisible(false);
            if (hint != null) hint.setVisible(false);
            return;
        }
        backend.setOnPreferenceChangeListener((preference, value) -> {
            Context context = getContext();
            if (context == null) return true;
            boolean wayland = com.termux.app.wayland.LinuxDisplayBackend.resolve(
                String.valueOf(value), true) == com.termux.app.wayland.LinuxDisplayBackend.WAYLAND;
            com.termux.app.wayland.WaylandAppRunner runner =
                com.termux.app.wayland.WaylandAppRunner.shared(context);
            // The choice stops nothing: windows stay open and the socket stays owned.
            runner.setWaylandChosen(wayland);
            return true;
        });
    }

    private static final String KEY_START_WITH =
        TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH;
    private static final String KEY_START_WITH_COMMAND =
        TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH_COMMAND;

    /**
     * Start with (#166): Nothing and A command… at once, then one desktop row per installed
     * distro that has XFCE, found off the main thread. A stored desktop stays listed while the
     * search runs, so the row never holds a choice it does not show.
     */
    private void setUpStartWith() {
        ListPreference startWith = findPreference(KEY_START_WITH);
        if (startWith == null) return;
        String stored = startWith.getValue();
        java.util.List<String> distros = new java.util.ArrayList<>();
        DisplayStartWith.Choice choice = DisplayStartWith.parse(stored, "");
        if (choice.kind == DisplayStartWith.Kind.DESKTOP) distros.add(choice.distro);
        applyStartWithEntries(startWith, distros);
        startWith.setSummaryProvider(preference -> {
            ListPreference list = (ListPreference) preference;
            String value = list.getValue();
            if (value == null || value.isEmpty() || list.getEntry() == null) {
                return list.getContext().getString(R.string.settings_x11_start_with_summary);
            }
            return list.getEntry();
        });
        applyStartWithCommandRow(stored);
        startWith.setOnPreferenceChangeListener((preference, value) -> {
            String picked = String.valueOf(value);
            applyStartWithCommandRow(picked);
            Preference command = findPreference(KEY_START_WITH_COMMAND);
            if (DisplayStartWith.VALUE_COMMAND.equals(picked) && command != null
                && getListView() != null) {
                // "A command…" asks for the command straight away, once the list dialog is gone.
                getListView().post(() -> {
                    if (isAdded()) onDisplayPreferenceDialog(command);
                });
            }
            return true;
        });
        new Thread(() -> {
            java.util.List<String> found =
                DisplayStartWith.distrosWithDesktop(com.termux.app.x11.ProotDistro.installed());
            android.app.Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                ListPreference row = findPreference(KEY_START_WITH);
                if (row == null) return;
                java.util.List<String> merged = new java.util.ArrayList<>(found);
                DisplayStartWith.Choice now = DisplayStartWith.parse(row.getValue(), "");
                if (now.kind == DisplayStartWith.Kind.DESKTOP && !merged.contains(now.distro)) {
                    merged.add(now.distro);
                }
                applyStartWithEntries(row, merged);
            });
        }, "x11-start-with-distros").start();
    }

    /** Nothing, a desktop for each of {@code distros}, then A command…. */
    private void applyStartWithEntries(@NonNull ListPreference row,
                                       @NonNull java.util.List<String> distros) {
        Context context = row.getContext();
        java.util.List<CharSequence> entries = new java.util.ArrayList<>();
        java.util.List<CharSequence> values = new java.util.ArrayList<>();
        entries.add(context.getString(R.string.settings_x11_start_with_nothing));
        values.add(DisplayStartWith.VALUE_NOTHING);
        for (String distro : distros) {
            entries.add(context.getString(R.string.settings_x11_start_with_desktop,
                DisplayStartWith.distroLabel(distro)));
            values.add(DisplayStartWith.desktopValue(distro));
        }
        entries.add(context.getString(R.string.settings_x11_start_with_command));
        values.add(DisplayStartWith.VALUE_COMMAND);
        CharSequence[] newEntries = entries.toArray(new CharSequence[0]);
        if (java.util.Arrays.equals(newEntries, row.getEntries())) return;
        row.setEntries(newEntries);
        row.setEntryValues(values.toArray(new CharSequence[0]));
        // The summary reads the entry, which may only now exist for a stored desktop.
        Preference.SummaryProvider<ListPreference> summary = row.getSummaryProvider();
        if (summary != null) row.setSummaryProvider(summary);
    }

    /** The command row shows only while the choice is a command. */
    private void applyStartWithCommandRow(@Nullable String choice) {
        Preference command = findPreference(KEY_START_WITH_COMMAND);
        boolean visible = DisplayStartWith.VALUE_COMMAND.equals(choice);
        if (command != null && command.isVisible() != visible) command.setVisible(visible);
    }

    /** Only the size row that the chosen resolution mode reads is shown. */
    private void applyResolutionRows(@Nullable String mode) {
        Preference scale = findPreference(KEY_SCALE);
        Preference exact = findPreference(KEY_RESOLUTION_EXACT);
        Preference custom = findPreference(KEY_RESOLUTION_CUSTOM);
        if (scale != null) scale.setVisible("scaled".equals(mode));
        if (exact != null) exact.setVisible("exact".equals(mode));
        if (custom != null) custom.setVisible("custom".equals(mode));
    }

    /**
     * Routes each key to the store that owns it: the display's own keys to the server's store,
     * everything else to the launcher's preferences.
     */
    static final class X11DisplayPreferencesDataStore extends PreferenceDataStore {

        @NonNull private final Context context;
        @NonNull private final TermuxAppSharedPreferences launcher;
        @Nullable private final Prefs display;

        X11DisplayPreferencesDataStore(@NonNull Context context) {
            this.context = context.getApplicationContext();
            this.launcher = TermuxAppSharedPreferences.build(this.context);
            Prefs prefs;
            try {
                prefs = new Prefs(this.context);
            } catch (RuntimeException e) {
                prefs = null;
            }
            this.display = prefs;
        }

        private boolean isDisplayKey(@Nullable String key) {
            return display != null && key != null && display.keys.containsKey(key);
        }

        /** A running display re-reads its preferences the way it does for termux-x11-preference. */
        private void notifyDisplay(@NonNull String key) {
            Intent intent = new Intent(LoriePreferences.ACTION_PREFERENCES_CHANGED);
            intent.putExtra("key", key);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        }

        @Override
        public void putString(String key, @Nullable String value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                if (KEY_RESOLUTION_CUSTOM.equals(key) && !isResolution(value)) return;
                LoriePreferences.PrefsProto.Preference pref = display.keys.get(key);
                if (pref instanceof LoriePreferences.PrefsProto.ListPreference) {
                    pref.asList().put(value == null ? "" : value);
                } else {
                    pref.asString().put(value == null ? "" : value);
                }
                notifyDisplay(key);
                return;
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_COMMAND.equals(key)) {
                launcher.setX11DisplayCommand(value == null ? "" : value);
            } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_WINDOW_MANAGER.equals(key)) {
                launcher.setX11WindowManager(value);
            } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH.equals(key)) {
                launcher.setX11StartWith(value);
            } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH_COMMAND.equals(key)) {
                launcher.setX11StartWithCommand(value);
            } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_LINUX_DISPLAY_BACKEND.equals(key)) {
                launcher.setLinuxDisplayBackend(value);
            }
        }

        @Override
        public String getString(String key, @Nullable String defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) {
                LoriePreferences.PrefsProto.Preference pref = display.keys.get(key);
                return pref instanceof LoriePreferences.PrefsProto.ListPreference
                    ? pref.asList().get() : pref.asString().get();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_COMMAND.equals(key)) {
                return launcher.getX11DisplayCommand();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_WINDOW_MANAGER.equals(key)) {
                return launcher.getX11WindowManager();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH.equals(key)) {
                return launcher.getX11StartWith();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_START_WITH_COMMAND.equals(key)) {
                return launcher.getX11StartWithCommand();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_LINUX_DISPLAY_BACKEND.equals(key)) {
                return launcher.getLinuxDisplayBackend();
            }
            return defValue;
        }

        @Override
        public void putInt(String key, int value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                display.keys.get(key).asInt().put(value);
                notifyDisplay(key);
                return;
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_DPI.equals(key)) {
                launcher.setX11DisplayDpi(value);
            }
        }

        @Override
        public int getInt(String key, int defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) return display.keys.get(key).asInt().get();
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_DPI.equals(key)) {
                return launcher.getX11DisplayDpi();
            }
            return defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                display.keys.get(key).asBoolean().put(value);
                notifyDisplay(key);
                return;
            }
            switch (key) {
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART:
                    launcher.setX11DisplayAutostartEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_SET_DISPLAY_ENV:
                    launcher.setX11SetDisplayEnvEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_LEGACY_DRAWING:
                    launcher.setX11LegacyDrawingEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_FORCE_BGRA:
                    launcher.setX11ForceBgraEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_ANDROID_KEYBOARD:
                    launcher.setX11AndroidKeyboardEnabled(value);
                    // Same reason as the row below: the Display place reads it while it is up.
                    notifyDisplay(key);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_KEYBOARD_FOLLOWS_TEXT:
                    launcher.setX11KeyboardFollowsTextEnabled(value);
                    // The launcher's own key, but the display's page reads it, so it goes out on
                    // the same broadcast the display keys use: a Display place already on screen
                    // picks the change up without having to be left and come back to.
                    notifyDisplay(key);
                    break;
                default:
                    break;
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) return display.keys.get(key).asBoolean().get();
            switch (key) {
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART:
                    return launcher.isX11DisplayAutostartEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_SET_DISPLAY_ENV:
                    return launcher.isX11SetDisplayEnvEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_LEGACY_DRAWING:
                    return launcher.isX11LegacyDrawingEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_FORCE_BGRA:
                    return launcher.isX11ForceBgraEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_ANDROID_KEYBOARD:
                    return launcher.isX11AndroidKeyboardEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_KEYBOARD_FOLLOWS_TEXT:
                    return launcher.isX11KeyboardFollowsTextEnabled();
                default:
                    return defValue;
            }
        }

        /** {@code WIDTHxHEIGHT}, both positive — what the server accepts. */
        static boolean isResolution(@Nullable String value) {
            if (value == null) return false;
            String[] parts = value.trim().split("x");
            if (parts.length != 2) return false;
            try {
                return Integer.parseInt(parts[0]) > 0 && Integer.parseInt(parts[1]) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
