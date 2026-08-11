package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic mixed-list composition. Search and categories deliberately bypass this helper. */
public final class AppDrawerItemComposer {
    private AppDrawerItemComposer() {}

    @NonNull
    public static List<AppDrawerItem> compose(@NonNull List<LauncherAppEntry> sortedApps,
                                              @NonNull LauncherConfigSnapshot snapshot,
                                              boolean emptyQuery,
                                              @NonNull AppDrawerViewType viewType) {
        if (!emptyQuery || viewType == AppDrawerViewType.CATEGORIES) return appsOnly(sortedApps);

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < sortedApps.size(); i++)
            positions.put(sortedApps.get(i).appRef.stableId(), i);
        Set<String> suppressed = new HashSet<>();
        List<FolderAt> insertions = new ArrayList<>();
        for (PinnedFolderItem folder : snapshot.folders.values()) {
            int earliest = Integer.MAX_VALUE;
            for (PinnedAppItem member : folder.apps) {
                Integer position = positions.get(member.appRef.stableId());
                if (position == null) continue;
                suppressed.add(member.appRef.stableId());
                earliest = Math.min(earliest, position);
            }
            if (earliest != Integer.MAX_VALUE) insertions.add(new FolderAt(folder, earliest));
        }
        Collections.sort(insertions, Comparator.comparingInt((FolderAt value) -> value.position)
            .thenComparing(value -> normalize(value.folder.title))
            .thenComparing(value -> value.folder.id));

        List<AppDrawerItem> result = new ArrayList<>();
        int folderIndex = 0;
        for (int i = 0; i < sortedApps.size(); i++) {
            while (folderIndex < insertions.size() && insertions.get(folderIndex).position == i)
                result.add(AppDrawerItem.folder(insertions.get(folderIndex++).folder));
            LauncherAppEntry app = sortedApps.get(i);
            if (!suppressed.contains(app.appRef.stableId())) result.add(AppDrawerItem.app(app));
        }
        while (folderIndex < insertions.size())
            result.add(AppDrawerItem.folder(insertions.get(folderIndex++).folder));
        return result;
    }

    @NonNull
    public static List<AppDrawerItem> appsOnly(@NonNull List<LauncherAppEntry> apps) {
        List<AppDrawerItem> result = new ArrayList<>(apps.size());
        for (LauncherAppEntry app : apps) result.add(AppDrawerItem.app(app));
        return result;
    }

    @NonNull private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class FolderAt {
        final PinnedFolderItem folder;
        final int position;
        FolderAt(PinnedFolderItem folder, int position) {
            this.folder = folder;
            this.position = position;
        }
    }
}
