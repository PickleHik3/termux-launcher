package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persists the drawer apps the user has hidden, keyed on {@link AppRef#stableId()} rather than on
 * a package name: a container app's id ("distro:debian:firefox") shares its package
 * ("x11:linux") with every other app from every other container, so the package alone cannot
 * tell them apart (unlike {@link LauncherCategoryOverrideStore}, which is fine keying on package
 * because a category applies to the whole package). Pins and folders already key the same way —
 * see {@link LauncherConfigRepository} — so this store follows that finer-grained precedent
 * instead.
 *
 * <p>One instance lives on {@link LauncherAppDataProvider} ({@link LauncherAppDataProvider#hiddenApps()}),
 * which both filters the drawer catalogue through it and hands the same instance to the settings
 * screen that edits it. A second, independently-constructed instance would keep its own
 * in-memory copy of the hidden set and could go stale against the provider's — which is a
 * singleton that otherwise never re-reads this preference on its own — so callers should not
 * construct this class themselves outside a test.
 */
public final class LauncherHiddenAppsStore {

    private static final String PREFS_KEY_HIDDEN_APPS_V1 = "app_launcher_hidden_apps_v1";

    private final SharedPreferences sharedPreferences;
    private final Set<String> hiddenStableIds = new LinkedHashSet<>();
    private boolean loaded;

    public LauncherHiddenAppsStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    public synchronized boolean isHidden(@NonNull String stableId) {
        ensureLoaded();
        return hiddenStableIds.contains(stableId);
    }

    public boolean isHidden(@NonNull AppRef ref) {
        return isHidden(ref.stableId());
    }

    /** @return a snapshot of every hidden app's id. Mutating it does not write anything back. */
    @NonNull
    public synchronized Set<String> hiddenStableIds() {
        ensureLoaded();
        return new LinkedHashSet<>(hiddenStableIds);
    }

    public synchronized boolean isEmpty() {
        ensureLoaded();
        return hiddenStableIds.isEmpty();
    }

    /** Hides or restores one app. A no-op call (already in the wanted state) does not persist. */
    public synchronized void setHidden(@NonNull String stableId, boolean hidden) {
        ensureLoaded();
        boolean changed = hidden ? hiddenStableIds.add(stableId) : hiddenStableIds.remove(stableId);
        if (changed) persist();
    }

    public void setHidden(@NonNull AppRef ref, boolean hidden) {
        setHidden(ref.stableId(), hidden);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        hiddenStableIds.clear();
        String raw = sharedPreferences.getString(PREFS_KEY_HIDDEN_APPS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty()) hiddenStableIds.add(id);
            }
        } catch (JSONException ignored) {
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (String id : hiddenStableIds) array.put(id);
        sharedPreferences.edit().putString(PREFS_KEY_HIDDEN_APPS_V1, array.toString()).apply();
    }
}
