package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.wayland.LinuxDisplayBackend;
import com.termux.app.x11.DesktopCatalogue;
import com.termux.app.x11.DesktopInstallRun;
import com.termux.app.x11.DesktopsPage;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.ProotDistro;
import com.termux.app.x11.X11AppSettings;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.app.x11.X11Apps;
import com.termux.app.x11.X11GpuProbe;
import com.termux.app.x11.X11LinuxAppRunner;
import com.termux.app.x11.X11GpuProfileStore;
import com.termux.x11.LoriePreferences;
import com.termux.x11.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One Linux app's page under Apps: graphics, touch mode, scale, paste shortcut and whether the drawer lists it.
 * Each row is Follow display setting or an override kept in {@link X11AppSettingsStore}. The app
 * comes in the arguments ({@link #ARG_ID}, {@link #ARG_NAME}); a page opened without an id has
 * nothing to show and goes back. A whole desktop ({@link #ARG_DESKTOP}) has no scale or paste
 * shortcut row, and a desktop in a container no Graphics row either; one in the prefix has the
 * Graphics row of any app.
 */
@Keep
public final class X11AppPreferencesFragment extends MaterialPreferenceFragment {

    public static final String ARG_ID = "x11_app_id";
    public static final String ARG_NAME = "x11_app_name";
    /** True when the app is a whole desktop session. */
    public static final String ARG_DESKTOP = "x11_app_desktop";

    /** True when the app is a whole desktop made for Wayland. */
    public static final String ARG_WAYLAND_DESKTOP = "x11_app_wayland_desktop";

    static final String KEY_DISPLAY = "x11_app_display";
    static final String KEY_GRAPHICS = "x11_app_graphics";
    static final String KEY_TOUCH = "x11_app_touch_mode";
    static final String KEY_SCALE = "x11_app_scale";
    static final String KEY_PASTE = "x11_app_paste_shortcut";
    static final String KEY_DRAWER = "x11_app_drawer";

    static final String FOLLOW = "follow";
    static final String SOFTWARE = "software";
    static final String HIDE = "hide";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Replaces the device probe in tests. */
    @Nullable X11GpuProbe.Result probeForTest;
    /** The profiles the Graphics row offers beyond software; every one until the probe answers. */
    @NonNull private List<X11GpuProbe.Profile> offered = allProfiles();
    @Nullable private String appId;
    @Nullable private X11AppSettingsStore store;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        Bundle args = getArguments();
        appId = args == null ? null : args.getString(ARG_ID);
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        setPreferenceScreen(screen);
        if (appId == null || appId.isEmpty()) {
            appId = null;
            // A reopen that lost its arguments: nothing to edit, so leave the way Back does.
            handler.post(() -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
            return;
        }
        store = new X11AppSettingsStore(context);

        boolean desktop = args != null && args.getBoolean(ARG_DESKTOP, false);
        boolean waylandDesktop = args != null && args.getBoolean(ARG_WAYLAND_DESKTOP, false);
        addDisplayRow(context, screen, desktop, waylandDesktop);
        boolean graphics = showsGraphicsRow(desktop, waylandDesktop, X11Apps.containerOf(appId));
        if (graphics) {
            screen.addPreference(row(context, KEY_GRAPHICS, R.string.settings_x11_app_graphics_title,
                R.array.settings_x11_app_graphics_entries, R.array.settings_x11_app_graphics_values));
        }
        screen.addPreference(row(context, KEY_TOUCH, R.string.settings_x11_app_touch_title,
            R.array.settings_x11_app_touch_entries, R.array.settings_x11_app_touch_values));
        if (!desktop) {
            screen.addPreference(row(context, KEY_SCALE, R.string.settings_x11_app_scale_title,
                R.array.settings_x11_app_scale_entries, R.array.settings_x11_app_scale_values));
        }
        if (!desktop) {
            screen.addPreference(row(context, KEY_PASTE, R.string.settings_x11_paste_shortcut_title,
                R.array.settings_x11_app_paste_entries, R.array.settings_x11_app_paste_values));
        }
        screen.addPreference(row(context, KEY_DRAWER, R.string.settings_x11_app_drawer_title,
            R.array.settings_x11_app_drawer_entries, R.array.settings_x11_app_drawer_values));
        addRemoveRow(context, screen, desktop);
        refreshRows();
        SettingsLayoutUtils.applyScreenLayout(this);
        if (graphics) loadProfiles(context);
    }

    @NonNull
    private static List<X11GpuProbe.Profile> allProfiles() {
        List<X11GpuProbe.Profile> all = new ArrayList<>();
        for (X11GpuProbe.Profile profile : X11GpuProbe.Profile.values()) {
            if (profile != X11GpuProbe.Profile.SOFTWARE) all.add(profile);
        }
        return all;
    }

    /** The profiles that fit this phone, as the display's own picker lists them; off the main thread. */
    private void loadProfiles(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            List<X11GpuProbe.Profile> fitting = new ArrayList<>();
            try {
                X11GpuProbe.Result override = probeForTest;
                X11GpuProbe.Result gpu = override != null ? override : X11GpuProbe.probe(app);
                for (X11GpuProbe.Recommendation r : gpu.ranked) {
                    if (r.profile != X11GpuProbe.Profile.SOFTWARE) fitting.add(r.profile);
                }
            } catch (RuntimeException e) {
                return;
            }
            handler.post(() -> {
                if (!isAdded()) return;
                offered = fitting;
                refreshRows();
            });
        });
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @NonNull
    private ListPreference row(@NonNull Context context, @NonNull String key, int title,
                               int entries, int values) {
        ListPreference row = new ListPreference(context);
        row.setKey(key);
        row.setPersistent(false);
        row.setTitle(title);
        row.setDialogTitle(title);
        row.setEntries(entries);
        row.setEntryValues(values);
        row.setOnPreferenceChangeListener((preference, newValue) -> {
            save(key, String.valueOf(newValue));
            refreshRows();
            return true;
        });
        return row;
    }

    @Override
    public void onResume() {
        super.onResume();
        Bundle args = getArguments();
        String name = args == null ? null : args.getString(ARG_NAME);
        if (getActivity() != null && name != null && !name.isEmpty()) getActivity().setTitle(name);
        refreshRows();
    }

    private void save(@NonNull String key, @NonNull String value) {
        X11AppSettingsStore kept = store;
        String id = appId;
        Context context = getContext();
        if (kept == null || id == null || context == null) return;
        boolean follow = FOLLOW.equals(value);
        switch (key) {
            case KEY_GRAPHICS:
                kept.setDrawsInSoftware(id, SOFTWARE.equals(value));
                kept.setGpuProfile(id, follow || SOFTWARE.equals(value) ? null : value);
                break;
            case KEY_TOUCH: kept.setTouchMode(id, follow ? null : value); break;
            case KEY_SCALE: kept.setScale(id, follow ? null : value); break;
            case KEY_PASTE: kept.setPasteShortcut(id, follow ? null : value); break;
            case KEY_DRAWER: kept.setHiddenInDrawer(id, HIDE.equals(value)); break;
            default: return;
        }
        // A running display re-reads its preferences the way the Input page's changes tell it to.
        Intent intent = new Intent(LoriePreferences.ACTION_PREFERENCES_CHANGED);
        intent.putExtra("key", key);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /** Reads the store again and puts each row's value and summary in step with it. */
    private void refreshRows() {
        X11AppSettingsStore kept = store;
        String id = appId;
        Context context = getContext();
        PreferenceScreen screen = getPreferenceScreen();
        if (kept == null || id == null || context == null || screen == null) return;

        String touch = kept.touchMode(id);
        String scale = kept.scale(id);
        boolean software = kept.drawsInSoftware(id);
        String profile = kept.gpuProfile(id);
        boolean hidden = kept.hiddenInDrawer(id);

        ListPreference graphics = screen.findPreference(KEY_GRAPHICS);
        if (graphics != null) {
            String current = software ? SOFTWARE : profile != null ? profile : FOLLOW;
            setGraphicsEntries(context, graphics, profile);
            graphics.setValue(current);
            graphics.setSummary(FOLLOW.equals(current) ? followWith(context, displayGraphics(context))
                : graphics.getEntry());
        }
        ListPreference touchRow = screen.findPreference(KEY_TOUCH);
        if (touchRow != null) {
            touchRow.setValue(touch == null ? FOLLOW : touch);
            touchRow.setSummary(touch == null ? followWith(context, displayTouch(context))
                : touchRow.getEntry());
        }
        ListPreference scaleRow = screen.findPreference(KEY_SCALE);
        if (scaleRow != null) {
            scaleRow.setValue(scale == null ? FOLLOW : scale);
            scaleRow.setSummary(context.getString(R.string.settings_x11_app_scale_summary,
                scaleRow.getEntry()));
        }
        ListPreference pasteRow = screen.findPreference(KEY_PASTE);
        if (pasteRow != null) {
            String paste = kept.pasteShortcut(id);
            pasteRow.setValue(paste == null ? FOLLOW : paste);
            pasteRow.setSummary(paste == null
                ? followWith(context, pasteName(context, kept.globalPasteShortcut()))
                : pasteRow.getEntry());
        }
        ListPreference drawer = screen.findPreference(KEY_DRAWER);
        if (drawer != null) {
            drawer.setValue(hidden ? HIDE : FOLLOW);
            drawer.setSummary(drawer.getEntry());
        }
        refreshDisplayRow(context, screen, kept, id);
    }

    /**
     * Display: Default, X11 Display or Wayland Display, where {@link LinuxDisplayBackend#choosable}
     * says the choice can change a launch. Kept apart from the other rows: it has its own store
     * call and listener.
     */
    /**
     * Whether the page has a Graphics row: an ordinary app and an X11 desktop of the prefix do; a
     * desktop in a container and a Wayland desktop (which draws without the graphics chip) do not.
     */
    static boolean showsGraphicsRow(boolean desktop, boolean waylandDesktop, @NonNull String container) {
        if (waylandDesktop) return false;
        return !desktop || container.isEmpty();
    }

    private void addDisplayRow(@NonNull Context context, @NonNull PreferenceScreen screen,
                               boolean desktop, boolean waylandDesktop) {
        String id = appId;
        X11AppSettingsStore kept = store;
        if (id == null || kept == null || !LinuxDisplayBackend.choosable(
                com.termux.BuildConfig.WAYLAND_DISPLAY, desktop, waylandDesktop,
                !X11Apps.containerOf(id).isEmpty())) {
            return;
        }
        ListPreference row = new ListPreference(context);
        row.setKey(KEY_DISPLAY);
        row.setPersistent(false);
        row.setTitle(R.string.settings_x11_app_display_title);
        row.setDialogTitle(R.string.settings_x11_app_display_title);
        row.setEntryValues(new CharSequence[] {VALUE_DEFAULT, LinuxDisplayBackend.VALUE_X11,
            LinuxDisplayBackend.VALUE_WAYLAND});
        row.setOnPreferenceChangeListener((preference, newValue) -> {
            kept.setDisplay(id, LinuxDisplayBackend.Choice.parse(String.valueOf(newValue)));
            handler.post(this::refreshRows);
            return true;
        });
        screen.addPreference(row);
    }

    private static final String VALUE_DEFAULT = "default";

    static final String KEY_REMOVE = "x11_desktop_remove";

    /**
     * Remove, last on the page of a desktop installed from Termux's packages (not one in a
     * container). Kept apart from the other rows; it has no stored value.
     */
    private void addRemoveRow(@NonNull Context context, @NonNull PreferenceScreen screen, boolean desktop) {
        String id = appId;
        if (!desktop || id == null || !X11Apps.containerOf(id).isEmpty()) return;
        DesktopCatalogue.Desktop installed = prefixDesktopOf(id);
        if (installed == null) return;
        Preference row = new Preference(context);
        row.setKey(KEY_REMOVE);
        row.setPersistent(false);
        row.setTitle(R.string.settings_x11_desktop_remove_title);
        row.setSummary(context.getString(R.string.settings_x11_desktop_remove_summary, installed.name));
        // One install or removal at a time: while one goes, this waits.
        row.setEnabled(!DesktopInstallRun.get(context).isActive());
        row.setOnPreferenceClickListener(p -> {
            confirmRemove(installed);
            return true;
        });
        screen.addPreference(row);
    }

    /** The catalogue desktop whose prefix session has this app id, or null. */
    @Nullable
    private static DesktopCatalogue.Desktop prefixDesktopOf(@NonNull String id) {
        try {
            for (LinuxAppCatalog.LinuxApp app
                    : LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(ProotDistro.Container.PREFIX))) {
                if (!app.session || !app.id.equals(id)) continue;
                for (DesktopCatalogue.Desktop desktop : DesktopCatalogue.all()) {
                    if (DesktopsPage.isSessionOf(desktop, app)) return desktop;
                }
            }
        } catch (RuntimeException e) {
            // An unreadable desktop file leaves the page without Remove, not without rows.
        }
        return null;
    }

    private void confirmRemove(@NonNull DesktopCatalogue.Desktop desktop) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        CheckBox alsoSettings = new CheckBox(activity);
        alsoSettings.setText(R.string.settings_x11_desktop_remove_settings);
        FrameLayout holder = new FrameLayout(activity);
        TypedValue padding = new TypedValue();
        int inset = activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding,
            padding, true) ? TypedValue.complexToDimensionPixelSize(padding.data,
                activity.getResources().getDisplayMetrics()) : 0;
        holder.setPadding(inset, 0, inset, 0);
        holder.addView(alsoSettings);
        boolean running = X11LinuxAppRunner.isDesktopRunning();
        new MaterialAlertDialogBuilder(activity)
            .setTitle(getString(R.string.settings_x11_desktop_remove_confirm_title, desktop.name))
            .setMessage(getString(running ? R.string.settings_x11_desktop_remove_running
                : R.string.settings_x11_desktop_remove_confirm_message, desktop.name))
            .setView(holder)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_x11_desktop_remove_action, (dialog, which) -> {
                if (!DesktopInstallRun.get(activity).startRemoval(desktop, alsoSettings.isChecked())) return;
                // The Desktops page shows the removal under the row.
                activity.getOnBackPressedDispatcher().onBackPressed();
            })
            .show();
    }

    private static void refreshDisplayRow(@NonNull Context context, @NonNull PreferenceScreen screen,
                                          @NonNull X11AppSettingsStore kept, @NonNull String id) {
        ListPreference row = screen.findPreference(KEY_DISPLAY);
        if (row == null) return;
        LinuxDisplayBackend.Choice choice = kept.display(id);
        String x11 = context.getString(R.string.settings_linux_display_x11);
        String wayland = context.getString(R.string.settings_linux_display_wayland);
        // Default names the display it means now.
        String meant = LinuxDisplayBackend.current(context) == LinuxDisplayBackend.WAYLAND
            ? wayland : x11;
        row.setEntries(new CharSequence[] {
            context.getString(R.string.settings_x11_app_follow_named,
                context.getString(R.string.settings_x11_app_display_default), meant),
            x11, wayland});
        row.setValue(choice.value == null ? VALUE_DEFAULT : choice.value);
        row.setSummary(row.getEntry());
    }

    /** Follow, software, then each offered profile, and the app's own pick even if the phone no longer fits it. */
    private void setGraphicsEntries(@NonNull Context context, @NonNull ListPreference row,
                                    @Nullable String kept) {
        List<X11GpuProbe.Profile> shown = new ArrayList<>(offered);
        X11GpuProbe.Profile own = X11GpuProbe.Profile.of(kept);
        if (own != null && !shown.contains(own)) shown.add(own);
        int size = 2 + shown.size();
        CharSequence[] entries = new CharSequence[size];
        CharSequence[] values = new CharSequence[size];
        entries[0] = context.getString(R.string.settings_x11_app_follow);
        values[0] = FOLLOW;
        entries[1] = context.getString(R.string.settings_x11_app_graphics_software);
        values[1] = SOFTWARE;
        for (int i = 0; i < shown.size(); i++) {
            entries[2 + i] = GraphicsAccelerationFragment.profileName(context, shown.get(i));
            values[2 + i] = shown.get(i).id;
        }
        if (java.util.Arrays.equals(values, row.getEntryValues())) return;
        row.setEntries(entries);
        row.setEntryValues(values);
    }

    @NonNull
    private static String followWith(@NonNull Context context, @Nullable String current) {
        String follow = context.getString(R.string.settings_x11_app_follow);
        return current == null ? follow
            : context.getString(R.string.settings_x11_app_follow_named, follow, current);
    }

    /** The display's own touch mode, the value the Input page's pill persists under "touchMode". */
    @Nullable
    private static String displayTouch(@NonNull Context context) {
        try {
            Prefs prefs = new Prefs(context);
            LoriePreferences.PrefsProto.Preference pref = prefs.keys.get("touchMode");
            String value = pref == null ? null : pref.asList().get();
            if (X11AppSettings.TOUCH_TRACKPAD.equals(value)) {
                return context.getString(R.string.settings_x11_app_touch_trackpad);
            }
            if (X11AppSettings.TOUCH_DIRECT.equals(value)) {
                return context.getString(R.string.settings_x11_app_touch_direct);
            }
            return context.getString(R.string.settings_x11_app_touch_touch);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static String pasteName(@NonNull Context context, @NonNull String shortcut) {
        return context.getString(X11AppSettings.PASTE_CTRL_SHIFT_V.equals(shortcut)
            ? R.string.settings_x11_paste_ctrl_shift_v : R.string.settings_x11_paste_ctrl_v);
    }

    @Nullable
    private static String displayGraphics(@NonNull Context context) {
        try {
            String pick = new X11GpuProfileStore(context).pick();
            X11GpuProbe.Profile profile = X11GpuProbe.Profile.of(pick);
            return profile == null ? context.getString(R.string.settings_x11_app_graphics_automatic)
                : GraphicsAccelerationFragment.profileName(context, profile);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
