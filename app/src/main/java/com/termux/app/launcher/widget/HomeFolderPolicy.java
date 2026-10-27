package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.widget.LauncherWidgetRecord.FolderApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules of folders on Home pages, pure so each is testable without a view or a store.
 *
 * <ul>
 *   <li>An app icon dropped on another app icon makes a folder of the two: the dropped app first,
 *   named after it ({@link #merge}).</li>
 *   <li>An app icon dropped on a folder joins it at the end ({@link #add}).</li>
 *   <li>A folder holds at most {@link #MAX_APPS} apps, never the same app twice.</li>
 *   <li>Taking an app out leaves a folder while two or more remain, the last app's own icon when
 *   one does, and nothing when none does ({@link #remove}).</li>
 *   <li>A name is trimmed and capped at the dock's {@link FolderRenameModel#MAX_CODE_POINTS}; a
 *   blank one falls back ({@link #name}).</li>
 *   <li>In edit mode the drop counts as a merge only after the dragged icon has rested over the
 *   same icon or folder for {@link #MERGE_HOVER_MS} and is released there; released anywhere
 *   else it moves as before, and nothing merges into a widget, a built-in or a folder dragged
 *   onto another item ({@link #dropAction}).</li>
 * </ul>
 */
public final class HomeFolderPolicy {
    /** The most apps one folder on Home holds: a 4×4 popup, the dock's popup grid at its default. */
    public static final int MAX_APPS = 16;
    /**
     * Settings › Home › Folders › Max folder size: the caps offered. {@link #MAX_APPS} is the
     * default; a folder kept from a larger cap keeps its apps, it only takes no more.
     */
    public static final int[] CAPACITIES = {9, 12, 16, 20};
    /** The largest cap offered: what a stored folder is read back up to. */
    public static final int HARD_MAX_APPS = 20;
    /** How long a dragged icon rests over an icon or a folder before releasing it merges. */
    public static final long MERGE_HOVER_MS = 300L;
    /** The name of a folder whose first app has no label, before the caller's own translation. */
    public static final String DEFAULT_NAME = "Folder";

    /** What a drop in edit mode does. */
    public enum Drop {
        /** The ordinary move: the free cell, pushing neighbours aside as before. */
        MOVE,
        /** The dragged icon and the icon under it become one folder in the target's cell. */
        CREATE_FOLDER,
        /** The dragged icon joins the folder under it. */
        ADD_TO_FOLDER
    }

    /** A folder's contents as a rule leaves them. */
    public static final class Contents {
        @NonNull public final String name;
        @NonNull public final List<FolderApp> apps;

        Contents(@NonNull String name, @NonNull List<FolderApp> apps) {
            this.name = name;
            this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        }
    }

    /** What taking one app out of a folder leaves behind. */
    public static final class Removal {
        public enum Outcome {
            /** Two or more apps remain: still a folder, {@link #remaining} holds them. */
            FOLDER,
            /** One app remains: the folder becomes {@link #lastApp}'s own icon in its cell. */
            ICON,
            /** None remains: the folder goes. */
            GONE,
            /** The app was not in the folder; nothing changes. */
            MISSING
        }

        @NonNull public final Outcome outcome;
        /** The apps left in order: two or more for FOLDER, empty otherwise. */
        @NonNull public final List<FolderApp> remaining;
        /** The one app left for ICON; null otherwise. */
        @Nullable public final FolderApp lastApp;
        /** The app taken out; null for MISSING. */
        @Nullable public final FolderApp taken;

        Removal(@NonNull Outcome outcome, @NonNull List<FolderApp> remaining,
                @Nullable FolderApp lastApp, @Nullable FolderApp taken) {
            this.outcome = outcome;
            this.remaining = Collections.unmodifiableList(new ArrayList<>(remaining));
            this.lastApp = lastApp;
            this.taken = taken;
        }
    }

    private HomeFolderPolicy() {}

    /**
     * The folder made by dropping {@code dropped} on {@code target}: the dropped app first, then
     * the target, named after the dropped app's label ({@code fallback} when it has none). Null
     * when both are the same app, which makes no folder.
     */
    @Nullable
    public static Contents merge(@NonNull FolderApp dropped, @NonNull FolderApp target,
                                 @NonNull String fallback) {
        if (dropped.stableId().equals(target.stableId())) return null;
        List<FolderApp> apps = new ArrayList<>(2);
        apps.add(dropped);
        apps.add(target);
        return new Contents(name(dropped.label, fallback), apps);
    }

    /**
     * {@code apps} with {@code app} at the end, or null when it is already there or the folder
     * already holds {@link #MAX_APPS}.
     */
    @Nullable
    public static List<FolderApp> add(@NonNull List<FolderApp> apps, @NonNull FolderApp app) {
        return add(apps, app, MAX_APPS);
    }

    /** {@link #add} under a cap of {@code capacity} apps. */
    @Nullable
    public static List<FolderApp> add(@NonNull List<FolderApp> apps, @NonNull FolderApp app,
                                      int capacity) {
        if (!canAdd(apps, app, capacity)) return null;
        List<FolderApp> next = new ArrayList<>(apps);
        next.add(app);
        return Collections.unmodifiableList(next);
    }

    /** Whether {@code app} may join {@code apps}: not there yet, and room left under the cap. */
    public static boolean canAdd(@NonNull List<FolderApp> apps, @NonNull FolderApp app) {
        return canAdd(apps, app, MAX_APPS);
    }

    /** {@link #canAdd} under a cap of {@code capacity} apps. */
    public static boolean canAdd(@NonNull List<FolderApp> apps, @NonNull FolderApp app, int capacity) {
        if (apps.size() >= clampCapacity(capacity)) return false;
        return indexOf(apps, app.stableId()) < 0;
    }

    /** The offered cap nearest {@code capacity}. */
    public static int clampCapacity(int capacity) {
        int best = MAX_APPS;
        int distance = Integer.MAX_VALUE;
        for (int offered : CAPACITIES) {
            int d = Math.abs(offered - capacity);
            if (d < distance) {
                distance = d;
                best = offered;
            }
        }
        return best;
    }

    /** A stored cap, read leniently: unset or not a number is {@link #MAX_APPS}. */
    public static int parseCapacity(@Nullable String stored) {
        if (stored == null) return MAX_APPS;
        try {
            return clampCapacity(Integer.parseInt(stored.trim()));
        } catch (NumberFormatException e) {
            return MAX_APPS;
        }
    }

    /** What taking the app {@code stableId} out of {@code apps} leaves. */
    @NonNull
    public static Removal remove(@NonNull List<FolderApp> apps, @NonNull String stableId) {
        int index = indexOf(apps, stableId);
        if (index < 0) {
            return new Removal(Removal.Outcome.MISSING, Collections.<FolderApp>emptyList(), null,
                null);
        }
        List<FolderApp> next = new ArrayList<>(apps);
        FolderApp taken = next.remove(index);
        if (next.isEmpty()) {
            return new Removal(Removal.Outcome.GONE, Collections.<FolderApp>emptyList(), null,
                taken);
        }
        if (next.size() == 1) {
            return new Removal(Removal.Outcome.ICON, Collections.<FolderApp>emptyList(),
                next.get(0), taken);
        }
        return new Removal(Removal.Outcome.FOLDER, next, null, taken);
    }

    /**
     * A folder name as stored: {@code requested} trimmed and capped at the dock's limit, or
     * {@code fallback} when nothing is left of it.
     */
    @NonNull
    public static String name(@Nullable String requested, @NonNull String fallback) {
        String trimmed = requested == null ? "" : requested.trim();
        if (trimmed.isEmpty()) return fallback;
        int max = FolderRenameModel.MAX_CODE_POINTS;
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, max)).trim();
        }
        return trimmed;
    }

    /**
     * Whether {@code target} can take {@code dragged} by a drop: an app icon onto another app's
     * icon, or onto a folder that has room for it and does not hold it yet. Nothing else merges:
     * not a folder dragged onto anything, and nothing onto a widget or a built-in.
     */
    public static boolean canTarget(@NonNull LauncherWidgetRecord dragged,
                                    @Nullable LauncherWidgetRecord target) {
        return canTarget(dragged, target, MAX_APPS);
    }

    /** {@link #canTarget} with folders holding up to {@code capacity} apps. */
    public static boolean canTarget(@NonNull LauncherWidgetRecord dragged,
                                    @Nullable LauncherWidgetRecord target, int capacity) {
        if (target == null || target.appWidgetId == dragged.appWidgetId) return false;
        if (!dragged.isApp() || dragged.appRef() == null) return false;
        String draggedId = dragged.appRef().stableId();
        if (target.isApp()) {
            return target.appRef() != null && !target.appRef().stableId().equals(draggedId);
        }
        if (target.isFolder()) {
            return target.folderApps().size() < clampCapacity(capacity)
                && indexOf(target.folderApps(), draggedId) < 0;
        }
        return false;
    }

    /**
     * What releasing {@code dragged} does when it has rested {@code hoveredMs} over
     * {@code target} (null: over no item, or over the free part of the page).
     */
    @NonNull
    public static Drop dropAction(@NonNull LauncherWidgetRecord dragged,
                                  @Nullable LauncherWidgetRecord target, long hoveredMs) {
        return dropAction(dragged, target, hoveredMs, MAX_APPS);
    }

    /** {@link #dropAction} with folders holding up to {@code capacity} apps. */
    @NonNull
    public static Drop dropAction(@NonNull LauncherWidgetRecord dragged,
                                  @Nullable LauncherWidgetRecord target, long hoveredMs,
                                  int capacity) {
        if (!canTarget(dragged, target, capacity) || hoveredMs < MERGE_HOVER_MS) return Drop.MOVE;
        return target.isFolder() ? Drop.ADD_TO_FOLDER : Drop.CREATE_FOLDER;
    }

    /** The folder entry for an app icon: its reference and the label it was added under. */
    @Nullable
    public static FolderApp entryOf(@NonNull LauncherWidgetRecord icon) {
        if (!icon.isApp() || icon.appRef() == null) return null;
        return new FolderApp(icon.appRef(), icon.appLabel());
    }

    private static int indexOf(@NonNull List<FolderApp> apps, @NonNull String stableId) {
        for (int i = 0; i < apps.size(); i++) {
            if (stableId.equals(apps.get(i).stableId())) return i;
        }
        return -1;
    }
}
