package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherHiddenAppsStore;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every app the drawer can show, one row each, so a checked row is shown and an unchecked one is
 * left out — of the drawer grid, its search and A-Z, and the suggestions row. Flat and
 * alphabetical, Android and Linux apps together: D8 asked for a hide that works "for every
 * drawer app", not a per-container switch, and grouping by container would put the distinction
 * this feature deliberately removed right back on screen.
 *
 * <p>Built at runtime rather than from a preferences XML resource, because the row count is the
 * install's, not fixed at build time — same reasoning as {@link TaiParameterPreferencesFragment}.
 * An already-pinned or foldered app keeps working when hidden (see
 * {@link LauncherAppDataProvider#getAllApps()}); hiding only takes it out of the ways a user finds
 * an app they have not already placed somewhere.
 */
@Keep
public final class HiddenAppsPreferencesFragment extends MaterialPreferenceFragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hidden-apps-load");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        setPreferenceScreen(screen);

        Preference loading = new Preference(context);
        loading.setKey("hidden_apps_loading");
        loading.setTitle(R.string.settings_x11_hidden_apps_loading);
        loading.setSelectable(false);
        screen.addPreference(loading);

        SettingsLayoutUtils.applyScreenLayout(this);
        loadAsync(context.getApplicationContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_x11_hidden_apps_title);
        }
    }

    @Override
    public void onDestroy() {
        loadExecutor.shutdownNow();
        super.onDestroy();
    }

    /** The catalogue walk is blocking; keep it off the fragment-creation thread. */
    private void loadAsync(@NonNull Context appContext) {
        loadExecutor.execute(() -> {
            List<LauncherAppEntry> apps = new ArrayList<>(LauncherAppDataProvider.getInstance(appContext)
                .getAllAppsIncludingHiddenBlocking());
            apps.sort(Comparator.comparing((LauncherAppEntry entry) -> entry.label,
                String.CASE_INSENSITIVE_ORDER));
            handler.post(() -> bind(appContext, apps));
        });
    }

    private void bind(@NonNull Context appContext, @NonNull List<LauncherAppEntry> apps) {
        if (!isAdded()) return;
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        screen.removeAll();

        if (apps.isEmpty()) {
            Preference empty = new Preference(screen.getContext());
            empty.setKey("hidden_apps_empty");
            empty.setTitle(R.string.settings_x11_hidden_apps_empty);
            empty.setSelectable(false);
            screen.addPreference(empty);
            return;
        }

        LauncherHiddenAppsStore hiddenApps = LauncherAppDataProvider.getInstance(appContext).hiddenApps();
        for (LauncherAppEntry app : apps) {
            String stableId = app.appRef.stableId();
            SwitchPreferenceCompat row = new SwitchPreferenceCompat(screen.getContext());
            row.setKey("hidden_app:" + stableId);
            row.setPersistent(false);
            row.setTitle(app.label);
            if (app.icon != null) row.setIcon(app.icon);
            row.setChecked(!hiddenApps.isHidden(stableId));
            row.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean shown = Boolean.TRUE.equals(newValue);
                hiddenApps.setHidden(stableId, !shown);
                return true;
            });
            screen.addPreference(row);
        }
    }
}
