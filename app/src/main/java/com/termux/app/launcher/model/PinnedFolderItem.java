package com.termux.app.launcher.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PinnedFolderItem implements PinnedItem {
    public static final int MAX_GRID = 6;
    /** A 6x6 folder is the largest persisted and rendered folder. */
    public static final int MAX_APPS = MAX_GRID * MAX_GRID;
    public static final int DEFAULT_ROWS = 3;
    public static final int DEFAULT_COLS = 3;

    /** {@link #drawerAnchor} value that parks a folder after every app in the drawer. */
    public static final String DRAWER_ANCHOR_END = "";

    public final String id;
    public String title;
    public int rows;
    public int cols;
    public boolean tintOverrideEnabled;
    public int tintColor;
    /**
     * Free drawer position: the stable id of the app this folder sits in front of,
     * {@link #DRAWER_ANCHOR_END} for the end of the list, or null to keep the automatic placement
     * (next to the first member alphabetically). An anchor rather than an index, because apps come
     * and go and an index would silently drift with every install.
     */
    @Nullable public String drawerAnchor;
    public final List<PinnedAppItem> apps;

    public PinnedFolderItem(@NonNull String id, @NonNull String title) {
        this.id = id;
        this.title = title;
        this.rows = DEFAULT_ROWS;
        this.cols = DEFAULT_COLS;
        this.tintOverrideEnabled = false;
        this.tintColor = 0xFF202020;
        this.apps = new ArrayList<>();
    }

    public boolean containsApp(@NonNull AppRef appRef) {
        String stableId = appRef.stableId();
        for (PinnedAppItem app : apps) {
            if (app != null && stableId.equals(app.appRef.stableId())) return true;
        }
        return false;
    }

    public boolean canAdd(@NonNull AppRef appRef) {
        return apps.size() < MAX_APPS && !containsApp(appRef);
    }

    @Override
    public int getType() {
        return TYPE_FOLDER;
    }
}
