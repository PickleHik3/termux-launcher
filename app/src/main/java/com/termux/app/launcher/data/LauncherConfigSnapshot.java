package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedIconOverride;
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
    /**
     * {@link #appIconOverridesJson} parsed, by {@code AppRef.stableId()}: the first entry for an
     * app wins, and maps to null when that entry's override is unusable. Built once per distinct
     * JSON; a catalogue build looks every app up here instead of re-parsing the array per app.
     */
    @NonNull final Map<String, PinnedIconOverride> appIconOverrides;

    LauncherConfigSnapshot(long revision, @NonNull List<PinnedItem> dockItems,
                           @NonNull Map<String, PinnedFolderItem> folders,
                           @NonNull String appIconOverridesJson) {
        this(revision, dockItems, folders, appIconOverridesJson,
            LauncherConfigRepository.parseAppIconOverrides(appIconOverridesJson));
    }

    /** With the overrides already parsed from {@code appIconOverridesJson}, to reuse them. */
    LauncherConfigSnapshot(long revision, @NonNull List<PinnedItem> dockItems,
                           @NonNull Map<String, PinnedFolderItem> folders,
                           @NonNull String appIconOverridesJson,
                           @NonNull Map<String, PinnedIconOverride> appIconOverrides) {
        this.revision = revision;
        this.dockItems = Collections.unmodifiableList(new ArrayList<>(dockItems));
        this.folders = Collections.unmodifiableMap(new LinkedHashMap<>(folders));
        this.appIconOverridesJson = appIconOverridesJson;
        this.appIconOverrides = appIconOverrides;
    }

    @Nullable
    public PinnedFolderItem folder(@NonNull String id) {
        return folders.get(id);
    }
}
