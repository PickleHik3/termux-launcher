package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Who brings the catalogue up to date after a package change.
 *
 * <p>The catalogue is one snapshot, read by the dock, the drawer, Home's icons and search. The
 * page in front having no apps row and no index says nothing about the others: an install heard
 * there must still reach the snapshot, or the new app stays out of the drawer until something
 * else rebuilds it.
 */
public final class CatalogueRefreshPlan {

    public enum Route {
        /** The dock's own refresh: it rebuilds the snapshot and renders now or on its way back. */
        DOCK,
        /** No dock is reading, but a snapshot is held for the other readers: refresh it directly. */
        SNAPSHOT,
        /** Nothing has listed apps yet; the first load scans the packages as they are. */
        NONE
    }

    private CatalogueRefreshPlan() {}

    /**
     * @param dockListsApps the page in front shows the apps row or the index
     * @param dockHoldsCatalogue the dock has loaded the catalogue, on whatever page
     * @param snapshotHeld the provider holds a loaded snapshot
     */
    @NonNull
    public static Route route(boolean dockListsApps, boolean dockHoldsCatalogue,
            boolean snapshotHeld) {
        if (dockListsApps || dockHoldsCatalogue) return Route.DOCK;
        return snapshotHeld ? Route.SNAPSHOT : Route.NONE;
    }

    /**
     * Whether the change touched an icon pack in use. Entries of untouched packages are otherwise
     * reused, and a reused entry would keep the pack's previous artwork.
     */
    public static boolean touchesIconPack(@Nullable Set<String> changedPackages,
            @Nullable String globalPack, @Nullable String pinnedPack) {
        if (changedPackages == null || changedPackages.isEmpty()) return false;
        return (globalPack != null && changedPackages.contains(globalPack))
            || (pinnedPack != null && changedPackages.contains(pinnedPack));
    }
}
