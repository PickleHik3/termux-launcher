package com.termux.app.place;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.place.layout.SharedChromeFold;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * The layouts part of the appearance reset on upgrade ({@code AppearanceReset}): every kind's
 * layout and dress, the shared sizes (dock height among them) and the dock's design go back to
 * what a new install folds to. The widget grid is kept, so the widgets placed on it stay where
 * they are.
 */
public final class LayoutReset {

    private LayoutReset() {}

    /**
     * Brings the layouts up to date first (the migration steps and the fold the launcher runs on
     * its way up, so an install from before the {@code layouts} file has one), then returns the
     * layouts the reset writes: a new install's, with this install's widget grid.
     */
    @NonNull
    public static Layouts prepare(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Context context) {
        PlaceLayoutStore store = new PlaceLayoutStore(prefs);
        LayoutStore documents = store.layouts();
        Layouts current = (documents != null ? documents : LayoutStore.shared(context)).current();
        String wall = context.getSharedPreferences(com.termux.app.wall.WallPagesStore.PREFS,
            Context.MODE_PRIVATE).getString(com.termux.app.wall.WallPagesStore.KEY_STATE, null);
        return newInstall(current, wall,
            context.getResources().getConfiguration().smallestScreenWidthDp);
    }

    /** Writes {@code layouts} whole, as the fold does. */
    public static void write(@NonNull Context context, @NonNull Layouts layouts) {
        LayoutStore.shared(context).replaceAll(layouts);
    }

    /**
     * Whether {@code key} is a flat copy of a layout field: a place's layout or dress as the
     * launcher preferences held it before the {@code layouts} file, kept there for a downgrade.
     * Positions (the voice pill, a floating keyboard) and the launcher's own keys are not.
     */
    public static boolean isFlatLayoutKey(@NonNull String key) {
        return LayoutDocumentPrefs.route(key) != null;
    }

    /**
     * What a new install's fold writes over {@code wall}: the flat keys its migration leaves (the
     * Home layout switch off and the new-install dock design on every layout, {@code
     * seedNewInstallDockDesign}), folded, then the shared chrome folded; with {@code current}'s
     * widget grid.
     */
    @VisibleForTesting
    @NonNull
    static Layouts newInstall(@NonNull Layouts current, @Nullable String wall, int widthDp) {
        Map<String, Object> flat = new HashMap<>();
        flat.put(PlaceLayoutStore.homeLayoutKey(), false);
        for (LayoutVariant variant : LayoutVariant.values())
            flat.put(PlaceLayoutStore.layoutWideKey(variant, LayoutsJson.DOCK_DESIGN),
                DockStyle.NEW_INSTALL_DEFAULT.storageValue());
        Layouts fresh = SharedChromeFold.fold(new LayoutsMigration(flat, wall, widthDp).layouts());
        KindLayout home = fresh.kind(LayoutKind.HOME);
        KindLayout kept = current.kind(LayoutKind.HOME);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String field : LayoutsJson.GRID_FIELDS)
                home = home.with(orientation, field, kept.orientation(orientation).get(field));
        }
        return fresh.withKind(LayoutKind.HOME, home);
    }
}
