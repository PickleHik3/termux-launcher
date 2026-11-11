package com.termux.app.launcher.folder;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedFolderItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Which folders the drawer's "Add to folder" choice lists for one app, as a pure answer. */
public final class DrawerFolderChoices {
    private DrawerFolderChoices() {}

    /** The folders {@code app} can still join: not already a member, and with room left. */
    @NonNull
    public static List<PinnedFolderItem> offered(@NonNull Collection<PinnedFolderItem> folders,
                                                 @NonNull AppRef app) {
        List<PinnedFolderItem> offered = new ArrayList<>(folders.size());
        for (PinnedFolderItem folder : folders) {
            if (folder != null && folder.canAdd(app)) offered.add(folder);
        }
        return offered;
    }
}
