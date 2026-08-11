package com.termux.app.launcher.drawer;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic curated/platform classifier plus the two local synthetic overlays. */
public final class AppDrawerCategoryClassifier {
    public static final long RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;
    public static final long FUTURE_SKEW_MS = 24L * 60L * 60L * 1000L;

    @NonNull private final AppDrawerCuratedCategoryMap curated;

    public AppDrawerCategoryClassifier(@NonNull AppDrawerCuratedCategoryMap curated) {
        this.curated = curated;
    }

    @NonNull
    public List<AppDrawerCategoryBucket> classify(@NonNull List<LauncherAppEntry> catalogue,
                                                   @NonNull LauncherUsageStatsStore usage,
                                                   long nowEpochMs) {
        return classify(catalogue, usage.rankForSuggestions(catalogue), nowEpochMs,
            Build.VERSION.SDK_INT);
    }

    @NonNull
    public List<AppDrawerCategoryBucket> classify(@NonNull List<LauncherAppEntry> catalogue,
                                                   @NonNull List<LauncherAppEntry> suggestions,
                                                   long nowEpochMs, int sdkInt) {
        EnumMap<AppDrawerCategory, List<LauncherAppEntry>> buckets =
            new EnumMap<>(AppDrawerCategory.class);
        Set<String> catalogueIds = new HashSet<>();
        for (LauncherAppEntry entry : catalogue) catalogueIds.add(entry.appRef.stableId());
        List<LauncherAppEntry> ranked = new ArrayList<>();
        Set<String> suggestionIds = new HashSet<>();
        for (LauncherAppEntry entry : suggestions) {
            if (catalogueIds.contains(entry.appRef.stableId())
                && suggestionIds.add(entry.appRef.stableId())) ranked.add(entry);
        }
        buckets.put(AppDrawerCategory.SUGGESTIONS, ranked);

        List<LauncherAppEntry> recent = new ArrayList<>();
        for (LauncherAppEntry entry : catalogue) {
            long installed = entry.firstInstallTimeEpochMs;
            long age = nowEpochMs - installed;
            if (installed > 0L && age <= RECENT_WINDOW_MS && age >= -FUTURE_SKEW_MS)
                recent.add(entry);
            AppDrawerCategory category = taxonomy(entry, sdkInt);
            buckets.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
        }
        // A tolerated future timestamp is age zero, not "newer than now". Clamp it for ordering
        // as well as eligibility so clock skew cannot jump an entry ahead of apps installed now.
        recent.sort(Comparator.comparingLong(
            (LauncherAppEntry e) -> Math.min(e.firstInstallTimeEpochMs, nowEpochMs))
            .reversed().thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(e -> e.appRef.stableId()));
        buckets.put(AppDrawerCategory.RECENTLY_ADDED, recent);

        List<AppDrawerCategoryBucket> result = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            List<LauncherAppEntry> entries = buckets.get(category);
            if (entries == null || entries.isEmpty()) continue;
            AppDrawerCategoryBucket bucket = new AppDrawerCategoryBucket(category, entries);
            if (!bucket.isEmpty()) result.add(bucket);
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public AppDrawerCategory taxonomy(@NonNull LauncherAppEntry entry, int sdkInt) {
        AppDrawerCategory override = curated.categoryForPackage(entry.appRef.packageName);
        if (override != null) return override;
        if (sdkInt < Build.VERSION_CODES.O) return AppDrawerCategory.OTHER;
        switch (entry.applicationCategory) {
            case ApplicationInfo.CATEGORY_SOCIAL: return AppDrawerCategory.SOCIAL;
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return AppDrawerCategory.PRODUCTIVITY;
            case ApplicationInfo.CATEGORY_ACCESSIBILITY: return AppDrawerCategory.UTILITIES;
            case ApplicationInfo.CATEGORY_GAME:
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO: return AppDrawerCategory.ENTERTAINMENT;
            case ApplicationInfo.CATEGORY_IMAGE: return AppDrawerCategory.PHOTO_VIDEO;
            case ApplicationInfo.CATEGORY_MAPS: return AppDrawerCategory.TRAVEL;
            case ApplicationInfo.CATEGORY_NEWS: return AppDrawerCategory.INFORMATION_READING;
            default: return AppDrawerCategory.OTHER;
        }
    }
}
