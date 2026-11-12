package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.x11.X11AppSettings;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.app.x11.X11GpuProbe;
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
 * nothing to show and goes back. A whole desktop ({@link #ARG_DESKTOP}) follows the display's
 * graphics, scale and paste shortcut, so its page has none of those rows.
 */
@Keep
public final class X11AppPreferencesFragment extends MaterialPreferenceFragment {

    public static final String ARG_ID = "x11_app_id";
    public static final String ARG_NAME = "x11_app_name";
    /** True when the app is a whole desktop session. */
    public static final String ARG_DESKTOP = "x11_app_desktop";

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
        if (!desktop) {
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
        refreshRows();
        SettingsLayoutUtils.applyScreenLayout(this);
        if (!desktop) loadProfiles(context);
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
