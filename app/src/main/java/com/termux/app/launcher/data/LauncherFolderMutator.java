package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure folder membership and collapse rules shared by migration, dock and drawer mutations. */
public final class LauncherFolderMutator {
    private LauncherFolderMutator() {}

    public static boolean append(@NonNull PinnedFolderItem folder,
                                 @NonNull PinnedAppItem app) {
        if (!folder.canAdd(app.appRef)) return false;
        folder.apps.add(app);
        return true;
    }

    @NonNull
    public static PinnedFolderItem create(@NonNull String id,
                                          @NonNull PinnedAppItem target,
                                          @NonNull PinnedAppItem source) {
        PinnedFolderItem folder = new PinnedFolderItem(id, "Folder");
        append(folder, target);
        append(folder, source);
        return folder;
    }

    /** De-duplicates/caps members, then applies the zero/one-member invariant to every reference. */
    public static void normalize(@NonNull List<PinnedItem> dockItems,
                                 @NonNull LinkedHashMap<String, PinnedFolderItem> folders) {
        for (PinnedFolderItem folder : folders.values()) {
            LinkedHashMap<String, PinnedAppItem> unique = new LinkedHashMap<>();
            for (PinnedAppItem app : folder.apps) {
                if (app == null || app.appRef == null || app.appRef.packageName.isEmpty()) continue;
                if (unique.size() >= PinnedFolderItem.MAX_APPS) break;
                unique.putIfAbsent(app.appRef.stableId(), app);
            }
            folder.apps.clear();
            folder.apps.addAll(unique.values());
        }

        Iterator<Map.Entry<String, PinnedFolderItem>> iterator = folders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PinnedFolderItem> entry = iterator.next();
            PinnedFolderItem folder = entry.getValue();
            if (folder.apps.size() >= 2) continue;
            for (int i = dockItems.size() - 1; i >= 0; i--) {
                PinnedItem item = dockItems.get(i);
                if (!(item instanceof PinnedFolderItem)
                    || !entry.getKey().equals(((PinnedFolderItem) item).id)) continue;
                if (folder.apps.isEmpty()) dockItems.remove(i);
                else dockItems.set(i, folder.apps.get(0));
            }
            iterator.remove();
        }
    }
}
