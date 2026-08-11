package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedFolderItem;

/** Immutable tagged row item shared by the vertical and horizontal all-apps presentations. */
public final class AppDrawerItem {
    public enum Kind { APP, FOLDER }

    @NonNull public final Kind kind;
    @NonNull public final String stableId;
    @Nullable public final LauncherAppEntry app;
    @Nullable public final PinnedFolderItem folder;

    private AppDrawerItem(@NonNull Kind kind, @NonNull String stableId,
                          @Nullable LauncherAppEntry app, @Nullable PinnedFolderItem folder) {
        this.kind = kind;
        this.stableId = stableId;
        this.app = app;
        this.folder = folder;
    }

    @NonNull public static AppDrawerItem app(@NonNull LauncherAppEntry app) {
        return new AppDrawerItem(Kind.APP, app.appRef.stableId(), app, null);
    }

    @NonNull public static AppDrawerItem folder(@NonNull PinnedFolderItem folder) {
        return new AppDrawerItem(Kind.FOLDER, folder.id, null, folder);
    }
}
