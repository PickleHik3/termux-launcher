package com.termux.app.launcher.data;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;

import java.util.List;

/**
 * Pins the Terminal entry to the dock once, the first time a build with the entry runs (ADR 0008):
 * the way back to the Workspace from Home. Once, and only once: a user who takes it off the dock
 * keeps it off, and one who already pinned it, or put it in a folder, is not given a second.
 */
public final class TerminalEntryPin {

    /** Set once the pin has been offered, whatever the dock held then. */
    static final String KEY_PINNED_ONCE = "app_launcher_terminal_entry_pinned_once";

    private TerminalEntryPin() {}

    /**
     * Puts the Terminal entry at the end of the dock if this has never run before.
     *
     * @param flags the store the once-flag lives in
     * @param dock  the dock's pins
     * @return whether the entry was pinned now
     */
    public static boolean pinOnce(@NonNull SharedPreferences flags,
                                  @NonNull LauncherConfigRepository dock) {
        if (flags.getBoolean(KEY_PINNED_ONCE, false)) return false;
        flags.edit().putBoolean(KEY_PINNED_ONCE, true).apply();
        List<PinnedItem> items = dock.loadPinnedItems();
        AppRef terminal = LauncherEntries.terminal();
        if (holds(items, terminal)) return false;
        items.add(new PinnedAppItem(terminal));
        dock.savePinnedItems(items);
        return true;
    }

    private static boolean holds(@NonNull List<PinnedItem> items, @NonNull AppRef ref) {
        String id = ref.stableId();
        for (PinnedItem item : items) {
            if (item instanceof PinnedAppItem
                && id.equals(((PinnedAppItem) item).appRef.stableId())) return true;
            if (item instanceof PinnedFolderItem) {
                for (PinnedAppItem app : ((PinnedFolderItem) item).apps) {
                    if (id.equals(app.appRef.stableId())) return true;
                }
            }
        }
        return false;
    }
}
