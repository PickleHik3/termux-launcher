package com.termux.app.settings;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.SurfacePresets;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The "Low-memory preset" switch of Settings → App behavior: the preferences that keep a weak
 * phone smooth, written through the same setters the Appearance editor and the other Settings
 * pages use. Nothing here changes a default; turning it off puts back what it replaced.
 */
public final class LowMemoryPreset {

    private static final String SOLID_LOOK_ID = "solid";
    private static final String EFFECT_NONE = "none";
    private static final int TERMINAL_OPACITY_PERCENT = 100;

    private LowMemoryPreset() {}

    /**
     * Applies the preset and remembers what it replaced: every stored key it changed, with the
     * value it held (or that it held none), so {@link #turnOff} can put the look back. Already on
     * keeps the first snapshot, which is the user's own look.
     */
    public static void turnOn(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null || prefs.isLowMemoryPresetEnabled()) {
            apply(prefs);
            prefs.setLowMemoryPresetEnabled(true);
            return;
        }
        Map<String, ?> before = new HashMap<>(store.getAll());
        apply(prefs);
        Map<String, ?> after = store.getAll();
        JSONObject snapshot = new JSONObject();
        try {
            for (Map.Entry<String, ?> entry : after.entrySet()) {
                String key = entry.getKey();
                if (isOwnKey(key) || Objects.equals(before.get(key), entry.getValue())) continue;
                snapshot.put(key, encode(before.get(key)));
            }
        } catch (JSONException e) {
            snapshot = new JSONObject();
        }
        prefs.setLowMemoryPresetSnapshot(snapshot.toString());
        prefs.setLowMemoryPresetEnabled(true);
    }

    /**
     * Puts back what {@link #turnOn} replaced, whatever was changed while the preset was on, and
     * forgets it. With no snapshot only the switch goes off.
     */
    public static void turnOff(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        String stored = prefs.getLowMemoryPresetSnapshot();
        if (store != null && !stored.isEmpty()) {
            try {
                JSONObject snapshot = new JSONObject(stored);
                SharedPreferences.Editor editor = store.edit();
                for (Iterator<String> keys = snapshot.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    if (!isOwnKey(key)) restore(editor, key, snapshot.getJSONArray(key));
                }
                editor.apply();
            } catch (JSONException ignored) {
                // An unreadable snapshot is treated as none.
            }
        }
        prefs.setLowMemoryPresetSnapshot(null);
        prefs.setLowMemoryPresetEnabled(false);
    }

    private static boolean isOwnKey(@NonNull String key) {
        return TERMUX_APP.KEY_LOW_MEMORY_PRESET.equals(key)
            || TERMUX_APP.KEY_LOW_MEMORY_PRESET_SNAPSHOT.equals(key);
    }

    /** A stored value as [type, value]; type "x" is a key that held nothing. */
    @NonNull
    private static JSONArray encode(@Nullable Object value) throws JSONException {
        JSONArray out = new JSONArray();
        if (value == null) return out.put("x");
        if (value instanceof Boolean) return out.put("b").put(value);
        if (value instanceof Integer) return out.put("i").put(value);
        if (value instanceof Long) return out.put("l").put(value);
        if (value instanceof Float) return out.put("f").put(((Float) value).doubleValue());
        if (value instanceof Set) return out.put("s").put(new JSONArray((Set<?>) value));
        return out.put("t").put(value);
    }

    private static void restore(@NonNull SharedPreferences.Editor editor, @NonNull String key,
                                @NonNull JSONArray encoded) throws JSONException {
        switch (encoded.getString(0)) {
            case "b": editor.putBoolean(key, encoded.getBoolean(1)); break;
            case "i": editor.putInt(key, encoded.getInt(1)); break;
            case "l": editor.putLong(key, encoded.getLong(1)); break;
            case "f": editor.putFloat(key, (float) encoded.getDouble(1)); break;
            case "t": editor.putString(key, encoded.getString(1)); break;
            case "s":
                JSONArray items = encoded.getJSONArray(1);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < items.length(); i++) set.add(items.getString(i));
                editor.putStringSet(key, set);
                break;
            default: editor.remove(key); break;
        }
    }

    private static void apply(@NonNull TermuxAppSharedPreferences prefs) {
        for (SurfacePresets.Preset look : SurfacePresets.presets()) {
            if (SOLID_LOOK_ID.equals(look.id)) SurfacePresets.apply(prefs, look);
        }
        // What the editor's Terminal Opacity slider writes: the terminal's own cell, and the copy
        // wallpaper mode restores from. The other surfaces keep Solid's opacity.
        prefs.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, TERMINAL_OPACITY_PERCENT);
        prefs.setTerminalBackgroundOpacity(TERMINAL_OPACITY_PERCENT);
        prefs.setTerminalCursorTrailEnabled(false);
        // Solid's defaults already set the classic glass motion.
        prefs.setTerminalRetroEffect(EFFECT_NONE);
        prefs.setWallpaperParallaxEnabled(false);
        prefs.setLazyModeEnabled(true);
    }
}
