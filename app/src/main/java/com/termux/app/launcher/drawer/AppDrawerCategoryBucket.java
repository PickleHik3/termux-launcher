package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, ordered and stable-id-deduplicated category contents. */
public final class AppDrawerCategoryBucket {
    public static final int PREVIEW_COUNT = 7;

    @NonNull public final AppDrawerCategory category;
    @NonNull private final List<LauncherAppEntry> entries;
    @NonNull private final List<LauncherAppEntry> previews;

    public AppDrawerCategoryBucket(@NonNull AppDrawerCategory category,
                                   @NonNull List<LauncherAppEntry> source) {
        this(category, source, null);
    }

    /**
     * @param previewUsageScores per-stable-id decayed usage; when present the previews float the
     *     most-used entries first (stable sort, so unused entries keep the source order) while the
     *     full entry list keeps the source order untouched
     */
    public AppDrawerCategoryBucket(@NonNull AppDrawerCategory category,
                                   @NonNull List<LauncherAppEntry> source,
                                   @Nullable Map<String, Double> previewUsageScores) {
        this.category = category;
        Set<String> seen = new LinkedHashSet<>();
        List<LauncherAppEntry> copy = new ArrayList<>();
        for (LauncherAppEntry entry : source) {
            if (entry != null && seen.add(entry.appRef.stableId())) copy.add(entry);
        }
        entries = Collections.unmodifiableList(copy);
        List<LauncherAppEntry> previewOrder = entries;
        if (previewUsageScores != null && !previewUsageScores.isEmpty() && entries.size() > 1) {
            List<LauncherAppEntry> reordered = new ArrayList<>(entries);
            reordered.sort((a, b) -> Double.compare(
                scoreOf(previewUsageScores, b), scoreOf(previewUsageScores, a)));
            previewOrder = reordered;
        }
        previews = Collections.unmodifiableList(new ArrayList<>(previewOrder.subList(0,
            Math.min(PREVIEW_COUNT, previewOrder.size()))));
    }

    private static double scoreOf(@NonNull Map<String, Double> scores,
                                  @NonNull LauncherAppEntry entry) {
        Double score = scores.get(entry.appRef.stableId());
        return score == null ? 0.0 : score;
    }

    @NonNull public List<LauncherAppEntry> entries() { return entries; }
    @NonNull public List<LauncherAppEntry> previews() { return previews; }
    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }
}
