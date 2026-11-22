package com.termux.app.launcher.folder;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
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

    /**
     * The apps a new folder with {@code app} can take as its second: the drawer's top-level apps,
     * so not {@code app} itself and not any app already in a folder.
     */
    @NonNull
    public static List<LauncherAppEntry> secondApps(@NonNull Collection<LauncherAppEntry> all,
                                                    @NonNull Collection<PinnedFolderItem> folders,
                                                    @NonNull AppRef app) {
        List<LauncherAppEntry> offered = new ArrayList<>(all.size());
        for (LauncherAppEntry entry : all) {
            if (entry == null || entry.appRef.stableId().equals(app.stableId())) continue;
            boolean inFolder = false;
            for (PinnedFolderItem folder : folders) {
                if (folder != null && folder.containsApp(entry.appRef)) {
                    inFolder = true;
                    break;
                }
            }
            if (!inFolder) offered.add(entry);
        }
        return offered;
    }

    /** Whether Add to folder shows: a folder can take {@code app}, or another free app can start a new folder with it. */
    public static boolean showsAddToFolder(@NonNull Collection<LauncherAppEntry> all,
                                           @NonNull Collection<PinnedFolderItem> folders,
                                           @NonNull AppRef app) {
        return !offered(folders, app).isEmpty() || !secondApps(all, folders, app).isEmpty();
    }
}
