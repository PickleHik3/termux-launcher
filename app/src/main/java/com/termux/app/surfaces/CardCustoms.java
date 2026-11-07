package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Custom look of each card whose Look chips write grid cells (Home, Workspace and the element
 * cards): the Frost stop, Opacity and Tint the user left there, by card, in one JSON object under
 * one preference key. Written when the chips leave a Custom for a Look; read when Custom is
 * tapped again. The Customization card's own Custom is the saved preset
 * ({@link SurfacePresets#custom}).
 */
final class CardCustoms {

    static final String PREFS_KEY = "card_custom_looks_v1";

    private CardCustoms() {}

    /** The saved {frost, opacity, tint} of {@code card}, or null when it has none. */
    @Nullable
    static int[] get(@Nullable SharedPreferences store, @NonNull String card) {
        String raw = raw(store);
        if (raw == null)
            return null;
        try {
            JSONArray values = new JSONObject(raw).optJSONArray(card);
            if (values == null || values.length() != 3)
                return null;
            return new int[] {values.getInt(0), values.getInt(1), values.getInt(2)};
        } catch (JSONException e) {
            return null;
        }
    }

    static void put(@Nullable SharedPreferences store, @NonNull String card, @NonNull int[] look) {
        if (store == null)
            return;
        try {
            String raw = raw(store);
            JSONObject all = raw == null ? new JSONObject() : new JSONObject(raw);
            all.put(card, new JSONArray().put(look[0]).put(look[1]).put(look[2]));
            store.edit().putString(PREFS_KEY, all.toString()).apply();
        } catch (JSONException ignored) {
            // A damaged blob is replaced by the next save.
        }
    }

    /** The whole store as saved, for an editor session's entry state; null for none. */
    @Nullable
    static String raw(@Nullable SharedPreferences store) {
        return store == null ? null : store.getString(PREFS_KEY, null);
    }

    /** Puts the store back as {@link #raw} had it. */
    static void restore(@Nullable SharedPreferences store, @Nullable String raw) {
        if (store == null)
            return;
        if (raw == null)
            store.edit().remove(PREFS_KEY).apply();
        else
            store.edit().putString(PREFS_KEY, raw).apply();
    }
}
