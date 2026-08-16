package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One normalized, revisioned launcher-config read. Folder references share the table instances. */
public final class LauncherConfigSnapshot {
    public final long revision;
    @NonNull public final List<PinnedItem> dockItems;
    @NonNull public final Map<String, PinnedFolderItem> folders;
    @NonNull public final String appIconOverridesJson;

    LauncherConfigSnapshot(long revision, @NonNull List<PinnedItem> dockItems,
                           @NonNull Map<String, PinnedFolderItem> folders,
                           @NonNull String appIconOverridesJson) {
        this.revision = revision;
        this.dockItems = Collections.unmodifiableList(new ArrayList<>(dockItems));
        this.folders = Collections.unmodifiableMap(new LinkedHashMap<>(folders));
        this.appIconOverridesJson = appIconOverridesJson;
    }

    @Nullable
    public PinnedFolderItem folder(@NonNull String id) {
        return folders.get(id);
    }
}
