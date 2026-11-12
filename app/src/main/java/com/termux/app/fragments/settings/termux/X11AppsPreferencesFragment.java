package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.LinuxAppIcons;
import com.termux.app.x11.X11AppSettingsStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every installed Linux app, one row each, opening that app's own page
 * ({@link X11AppPreferencesFragment}). An app whose settings differ from the display's says so.
 *
 * <p>Built at runtime because the rows are the install's, like the page it replaces. The
 * catalogue and the icons are read off the main thread; coming back to the page only re-reads
 * the store, never rescans.
 */
@Keep
public final class X11AppsPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_PREFIX = "x11_app:";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-apps-load");
        thread.setDaemon(true);
        return thread;
    });
    @Nullable private X11AppSettingsStore store;
    private boolean bound;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        store = new X11AppSettingsStore(context);

        Preference loading = new Preference(context);
        loading.setKey("x11_apps_loading");
        loading.setTitle(R.string.settings_x11_apps_loading);
        loading.setSelectable(false);
        screen.addPreference(loading);
        SettingsLayoutUtils.applyScreenLayout(this);
        loadAsync(context.getApplicationContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_x11_apps_title);
        if (bound) refreshTags();
    }

    @Override
    public void onDestroy() {
        loadExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadAsync(@NonNull Context appContext) {
        loadExecutor.execute(() -> {
            List<LinuxAppCatalog.LinuxApp> apps;
            try {
                apps = new ArrayList<>(LinuxAppCatalog.scan(LinuxAppCatalog.roots()));
            } catch (RuntimeException e) {
                apps = new ArrayList<>();
            }
            apps.sort(Comparator.comparing((LinuxAppCatalog.LinuxApp app) -> app.name,
                String.CASE_INSENSITIVE_ORDER));
            List<Drawable> icons = new ArrayList<>(apps.size());
            for (LinuxAppCatalog.LinuxApp app : apps) icons.add(iconOf(appContext, app));
            List<LinuxAppCatalog.LinuxApp> sorted = apps;
            handler.post(() -> bind(sorted, icons));
        });
    }

    @Nullable
    private static Drawable iconOf(@NonNull Context context, @NonNull LinuxAppCatalog.LinuxApp app) {
        try {
            File file = LinuxAppIcons.find(app);
            return file == null ? null : LinuxAppIcons.load(context.getResources(), file);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private void bind(@NonNull List<LinuxAppCatalog.LinuxApp> apps, @NonNull List<Drawable> icons) {
        if (!isAdded()) return;
        PreferenceScreen screen = getPreferenceScreen();
        X11AppSettingsStore kept = store;
        if (screen == null || kept == null) return;
        screen.removeAll();
        bound = true;
        Context context = screen.getContext();
        if (apps.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setKey("x11_apps_empty");
            empty.setTitle(R.string.settings_x11_apps_empty);
            empty.setSelectable(false);
            screen.addPreference(empty);
        }
        for (int i = 0; i < apps.size(); i++) {
            LinuxAppCatalog.LinuxApp app = apps.get(i);
            Preference row = new Preference(context);
            row.setKey(KEY_PREFIX + app.id);
            row.setTitle(app.name);
            if (icons.get(i) != null) row.setIcon(icons.get(i));
            row.setFragment(X11AppPreferencesFragment.class.getName());
            row.getExtras().putString(X11AppPreferencesFragment.ARG_ID, app.id);
            row.getExtras().putString(X11AppPreferencesFragment.ARG_NAME, app.name);
            row.setSummary(tag(context, kept.differsFromDisplay(app.id)));
            screen.addPreference(row);
        }
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    /** Re-reads the store for every row; the catalogue stays as it was. */
    private void refreshTags() {
        PreferenceScreen screen = getPreferenceScreen();
        X11AppSettingsStore kept = store;
        if (screen == null || kept == null) return;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference row = screen.getPreference(i);
            String key = row.getKey();
            if (key == null || !key.startsWith(KEY_PREFIX)) continue;
            row.setSummary(tag(row.getContext(), kept.differsFromDisplay(key.substring(KEY_PREFIX.length()))));
        }
    }

    /** "Custom settings" for an app that differs from the display, nothing otherwise. */
    @VisibleForTesting
    @Nullable
    static CharSequence tag(@NonNull Context context, boolean differs) {
        return differs ? context.getString(R.string.settings_x11_apps_custom) : null;
    }
}
