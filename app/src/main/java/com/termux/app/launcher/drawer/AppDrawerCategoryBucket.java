package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, ordered and stable-id-deduplicated category contents. */
public final class AppDrawerCategoryBucket {
    public static final int PREVIEW_COUNT = 7;

    @NonNull public final AppDrawerCategory category;
    @NonNull private final List<LauncherAppEntry> entries;
    @NonNull private final List<LauncherAppEntry> previews;

    public AppDrawerCategoryBucket(@NonNull AppDrawerCategory category,
                                   @NonNull List<LauncherAppEntry> source) {
        this.category = category;
        Set<String> seen = new LinkedHashSet<>();
        List<LauncherAppEntry> copy = new ArrayList<>();
        for (LauncherAppEntry entry : source) {
            if (entry != null && seen.add(entry.appRef.stableId())) copy.add(entry);
        }
        entries = Collections.unmodifiableList(copy);
        previews = Collections.unmodifiableList(entries.subList(0,
            Math.min(PREVIEW_COUNT, entries.size())));
    }

    @NonNull public List<LauncherAppEntry> entries() { return entries; }
    @NonNull public List<LauncherAppEntry> previews() { return previews; }
    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }
}
