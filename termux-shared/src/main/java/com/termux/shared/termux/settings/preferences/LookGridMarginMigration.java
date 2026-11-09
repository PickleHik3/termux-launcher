package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.LookGrid.Key;
import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

/**
 * The one-way step that makes Margin one number for every page (developer, 2026-11-08), run once
 * per install behind {@link #KEY_MIGRATED}, after the other look-grid steps.
 *
 * <p>Margin was held per kind of page (Home, Workspace) and could be set apart on a page, so a
 * swipe between Home and the Workspace laid the chrome out at a second Margin mid-slide. Now only
 * Base holds it: the Margin Home pages wore becomes Base, and every kind and page Margin cell is
 * dropped. An install whose pages all wore Base's Margin sees nothing change.</p>
 */
public final class LookGridMarginMigration {

    private LookGridMarginMigration() {}

    public static final String KEY_MIGRATED = "look_grid_margin_global";

    /**
     * Writes Home's Margin into Base and drops every other Margin cell, once. Returns whether the
     * Margin any page wore changed (the Workspace's, a page's own, or Base's).
     */
    public static boolean run(@NonNull LookGrid.Store store) {
        if (store.getBoolean(KEY_MIGRATED, false)) return false;
        boolean changed = fold(new LookGrid(store));
        store.putBoolean(KEY_MIGRATED, true);
        return changed;
    }

    /** The fold itself, ungated: Home's Margin into Base, every kind and page cell dropped. */
    static boolean fold(@NonNull LookGrid grid) {
        int home = grid.value(Property.MARGIN, null, Kind.HOME);
        boolean changed = false;
        for (String stored : grid.gridKeys()) {
            Key cell = LookGrid.parse(stored);
            if (cell == null || cell.property != Property.MARGIN) continue;
            if (cell.kind == null && cell.page < 0) continue;
            grid.clear(cell);
            changed = true;
        }
        if (grid.value(Property.MARGIN, null, LookGrid.NO_PAGE) != home) {
            grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, home);
            changed = true;
        }
        return changed;
    }
}
