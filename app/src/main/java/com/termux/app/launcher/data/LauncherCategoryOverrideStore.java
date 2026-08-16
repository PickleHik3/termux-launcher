package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Persists the user's explicit drawer-category choices as package → category slug. Slugs are kept
 * opaque here so this store stays independent of the drawer taxonomy; readers resolve them and
 * simply ignore any slug the current taxonomy no longer knows.
 */
public final class LauncherCategoryOverrideStore {

    private static final String PREFS_KEY_CATEGORY_OVERRIDES_V1 =
        "app_launcher_category_overrides_v1";

    private final SharedPreferences sharedPreferences;
    private final Map<String, String> slugByPackage = new HashMap<>();
    private boolean loaded;

    public LauncherCategoryOverrideStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    /** @return the stored slug for the package, or null when the user has not chosen one. */
    @Nullable
    public synchronized String get(@Nullable String packageName) {
        if (packageName == null) return null;
        ensureLoaded();
        return slugByPackage.get(packageName.toLowerCase(Locale.US));
    }

    public synchronized void set(@NonNull String packageName, @NonNull String slug) {
        ensureLoaded();
        slugByPackage.put(packageName.toLowerCase(Locale.US), slug);
        persist();
    }

    public synchronized void clear(@NonNull String packageName) {
        ensureLoaded();
        if (slugByPackage.remove(packageName.toLowerCase(Locale.US)) != null) persist();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        slugByPackage.clear();
        String raw = sharedPreferences.getString(PREFS_KEY_CATEGORY_OVERRIDES_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keyIterator = root.keys();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                String slug = root.optString(key, "");
                if (!slug.isEmpty()) slugByPackage.put(key, slug);
            }
        } catch (JSONException ignored) {
        }
    }

    private void persist() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, String> entry : slugByPackage.entrySet())
                root.put(entry.getKey(), entry.getValue());
        } catch (JSONException ignored) {
        }
        sharedPreferences.edit()
            .putString(PREFS_KEY_CATEGORY_OVERRIDES_V1, root.toString()).apply();
    }
}
