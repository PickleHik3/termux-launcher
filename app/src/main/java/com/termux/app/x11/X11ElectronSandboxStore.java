package com.termux.app.x11;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * Which container apps (D7) need {@code --no-sandbox} to open at all. Everything built on
 * Electron refuses to start under proot without it, whatever user it runs as, and there is no way
 * to tell that from a {@code .desktop} file — so it is learned the first time an app dies at once
 * without the flag, and remembered by {@link LinuxAppCatalog.LinuxApp#id} so the next tap goes
 * straight to it instead of stuttering through the failed attempt again.
 *
 * <p>Modelled on {@code LauncherCategoryOverrideStore}: the same small JSON blob in the launcher's
 * own preferences file, an in-memory cache loaded once and written back on every change. A set of
 * ids is all this needs — unlike a category override there is no value to remember per id, only
 * whether one is in it.
 */
public final class X11ElectronSandboxStore {

    private static final String PREFS_KEY_NO_SANDBOX_APPS_V1 = "x11_no_sandbox_apps_v1";

    private final SharedPreferences sharedPreferences;
    private final Set<String> ids = new HashSet<>();
    private boolean loaded;

    public X11ElectronSandboxStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    /** Whether {@code appId} is remembered as needing the flag from the very first try. */
    public synchronized boolean needsNoSandbox(@NonNull String appId) {
        ensureLoaded();
        return ids.contains(appId);
    }

    /** The flag opened the app; keep skipping the plain attempt for it from now on. */
    public synchronized void remember(@NonNull String appId) {
        ensureLoaded();
        if (ids.add(appId)) persist();
    }

    /**
     * The flag stopped helping — or never did for this id — so forget it. The next tap gives the
     * plain command a fresh chance instead of retrying a combination that just failed too; nothing
     * is lost by forgetting, since remembering it bought nothing either.
     */
    public synchronized void forget(@NonNull String appId) {
        ensureLoaded();
        if (ids.remove(appId)) persist();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        ids.clear();
        String raw = sharedPreferences.getString(PREFS_KEY_NO_SANDBOX_APPS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty()) ids.add(id);
            }
        } catch (JSONException ignored) {
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (String id : ids) array.put(id);
        sharedPreferences.edit().putString(PREFS_KEY_NO_SANDBOX_APPS_V1, array.toString()).apply();
    }
}
