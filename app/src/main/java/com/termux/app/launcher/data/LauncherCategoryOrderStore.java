package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the user's order for the Categories tiles as a list of category slugs. Slugs stay
 * opaque here; {@code AppDrawerCategoryOrder} resolves them and drops any it does not know. An
 * empty list means the default order.
 */
public final class LauncherCategoryOrderStore {

    static final String PREFS_KEY_CATEGORY_ORDER_V1 = "app_launcher_category_order_v1";

    private final SharedPreferences sharedPreferences;

    public LauncherCategoryOrderStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    /** @return the stored slugs; empty when nothing is stored or the value is malformed. */
    @NonNull
    public synchronized List<String> read() {
        List<String> slugs = new ArrayList<>();
        String raw;
        try {
            raw = sharedPreferences.getString(PREFS_KEY_CATEGORY_ORDER_V1, "");
        } catch (ClassCastException e) {
            return slugs;
        }
        if (raw == null || raw.trim().isEmpty()) return slugs;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String && !((String) item).isEmpty()) slugs.add((String) item);
            }
        } catch (JSONException e) {
            slugs.clear();
        }
        return slugs;
    }

    public synchronized void write(@Nullable List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            clear();
            return;
        }
        JSONArray array = new JSONArray();
        for (String slug : slugs) if (slug != null && !slug.isEmpty()) array.put(slug);
        sharedPreferences.edit().putString(PREFS_KEY_CATEGORY_ORDER_V1, array.toString()).apply();
    }

    /** Back to the default order. */
    public synchronized void clear() {
        sharedPreferences.edit().remove(PREFS_KEY_CATEGORY_ORDER_V1).apply();
    }
}
