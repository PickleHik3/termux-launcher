package com.termux.app.launcher.drawer;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedFolderItem;

public interface AppDrawerPickupDelegate {
    boolean claimContext(@NonNull View source, @NonNull LauncherAppEntry entry);
    boolean startPickup(@NonNull View source, @NonNull LauncherAppEntry entry);

    /**
     * Long-press pickup of a drawer folder, which carries the folder to a free position instead of
     * merging anything. Folders have no app entry, so they get their own entry point.
     */
    boolean startFolderPickup(@NonNull View source, @NonNull PinnedFolderItem folder);
}
