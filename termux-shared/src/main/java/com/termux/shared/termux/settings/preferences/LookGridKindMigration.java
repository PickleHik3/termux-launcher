package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid.Key;
import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The one-way step that gives the Home kind back its own cells (ADR 0018 §3), run once per
 * install behind {@link #KEY_MIGRATED}, right after {@link LookGridMigration}.
 *
 * <p>The 2026-10-30 fold built element cells everywhere from keys that once held Home's values
 * only, so the keyboard Home wore went everywhere. The saved custom Look
 * ({@code surface_custom_preset}) still names Home's values under each key's {@code _home} twin
 * ({@code look.opacity.keyboard_home}). An element cell everywhere whose value equals its twin
 * moves to {@code @kind:home} and leaves everywhere free, so the other kinds wear their own shipped
 * look again. A cell with no twin, or a value the user changed since, stays where it is; page
 * cells and all-glass cells are never moved. No custom Look, nothing to move.</p>
 */
public final class LookGridKindMigration {

    private LookGridKindMigration() {}

    public static final String KEY_MIGRATED = "look_grid_kind_migrated";

    /** The preset format's suffix for Home's twin of a key. */
    static final String HOME_TWIN_SUFFIX = "_home";

    /**
     * Moves the everywhere element cells that equal their Home twin into the Home kind, once. A
     * fresh install is left untouched, as {@link LookGridMigration#run} leaves it, so it still
     * reads as fresh. Returns how many cells moved.
     */
    public static int run(@NonNull LookGrid.Store store) {
        return run(store, LookGridMigration.isFresh(store));
    }

    /** As above, with freshness decided by the caller before other migrations wrote their keys. */
    public static int run(@NonNull LookGrid.Store store, boolean fresh) {
        if (store.getBoolean(KEY_MIGRATED, false) || fresh) return 0;
        int moved = rehome(store, homeTwins(store.getString(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, null)));
        store.putBoolean(KEY_MIGRATED, true);
        return moved;
    }

    private static int rehome(@NonNull LookGrid.Store store, @NonNull Map<String, Integer> twins) {
        if (twins.isEmpty()) return 0;
        int moved = 0;
        for (String stored : new LookGrid(store, null, null).gridKeys()) {
            Key cell = LookGrid.parse(stored);
            if (cell == null || cell.element == null || cell.page >= 0 || cell.kind != null) continue;
            Integer twin = twins.get(stored);
            if (twin == null || store.getInt(stored, Integer.MIN_VALUE) != twin) continue;
            String home = LookGrid.key(cell.property, cell.element, Kind.HOME);
            if (store.contains(home)) continue;
            store.putInt(home, twin);
            store.remove(stored);
            moved++;
        }
        return moved;
    }

    /** The saved Look's Home twins by the key they twin: {@code look.opacity.keyboard} → 0. */
    @NonNull
    static Map<String, Integer> homeTwins(@Nullable String preset) {
        Map<String, Integer> twins = new HashMap<>();
        if (preset == null || preset.isEmpty()) return twins;
        try {
            JSONObject json = new JSONObject(preset);
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!key.startsWith(LookGrid.PREFIX) || !key.endsWith(HOME_TWIN_SUFFIX)) continue;
                Object value = json.get(key);
                if (value instanceof Number)
                    twins.put(key.substring(0, key.length() - HOME_TWIN_SUFFIX.length()),
                        ((Number) value).intValue());
            }
        } catch (JSONException e) {
            twins.clear();
        }
        return twins;
    }
}
