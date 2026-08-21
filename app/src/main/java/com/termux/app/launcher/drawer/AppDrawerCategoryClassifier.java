package com.termux.app.launcher.drawer;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.drawer.AppDrawerCategoryAssignment.Source;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic classification pipeline plus the two local synthetic overlays. Per entry the
 * precedence is: user override, curated force, platform category, curated fill, default system
 * role, scored offline heuristics, then OTHER. Everything is offline and package-level.
 */
public final class AppDrawerCategoryClassifier {
    public static final long RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;
    public static final long FUTURE_SKEW_MS = 24L * 60L * 60L * 1000L;
    public static final int SUGGESTIONS_CAP = 8;
    public static final int RECENTLY_ADDED_CAP = 12;
    public static final float HEURISTIC_THRESHOLD = 1.0f;
    public static final float HEURISTIC_MARGIN = 0.5f;
    private static final float WEIGHT_STRONG = 1.0f;
    private static final float WEIGHT_WEAK = 0.5f;
    /** Heuristic confidence normalizer: a score of two strong keywords reads as certainty. */
    private static final float HEURISTIC_CONFIDENCE_CEILING = 2.0f;

    /** User-chosen category lookup; null means no override source (tests, headless builds). */
    public interface OverrideLookup {
        @Nullable AppDrawerCategory categoryForPackage(@NonNull String packageName);
    }

    private static final Map<String, Keyword> KEYWORDS = buildKeywords();

    @NonNull private final AppDrawerCuratedCategoryMap curated;
    @Nullable private final OverrideLookup userOverrides;
    @NonNull private String lastReport = "";

    public AppDrawerCategoryClassifier(@NonNull AppDrawerCuratedCategoryMap curated) {
        this(curated, null);
    }

    public AppDrawerCategoryClassifier(@NonNull AppDrawerCuratedCategoryMap curated,
                                       @Nullable OverrideLookup userOverrides) {
        this.curated = curated;
        this.userOverrides = userOverrides;
    }

    @NonNull
    public List<AppDrawerCategoryBucket> classify(@NonNull List<LauncherAppEntry> catalogue,
                                                   @NonNull LauncherUsageStatsStore usage,
                                                   @NonNull Map<String, AppDrawerCategory> roleMap,
                                                   long nowEpochMs) {
        return classify(catalogue, usage.rankForSuggestions(catalogue, nowEpochMs),
            usage.decayedScores(catalogue, nowEpochMs), roleMap, nowEpochMs,
            Build.VERSION.SDK_INT);
    }

    @NonNull
    public List<AppDrawerCategoryBucket> classify(@NonNull List<LauncherAppEntry> catalogue,
                                                   @NonNull List<LauncherAppEntry> suggestions,
                                                   long nowEpochMs, int sdkInt) {
        return classify(catalogue, suggestions, Collections.emptyMap(), Collections.emptyMap(),
            nowEpochMs, sdkInt);
    }

    @NonNull
    public List<AppDrawerCategoryBucket> classify(@NonNull List<LauncherAppEntry> catalogue,
                                                   @NonNull List<LauncherAppEntry> suggestions,
                                                   @NonNull Map<String, Double> usageScores,
                                                   @NonNull Map<String, AppDrawerCategory> roleMap,
                                                   long nowEpochMs, int sdkInt) {
        EnumMap<AppDrawerCategory, List<LauncherAppEntry>> buckets =
            new EnumMap<>(AppDrawerCategory.class);
        Set<String> catalogueIds = new HashSet<>();
        for (LauncherAppEntry entry : catalogue) catalogueIds.add(entry.appRef.stableId());
        List<LauncherAppEntry> ranked = new ArrayList<>();
        Set<String> suggestionIds = new HashSet<>();
        for (LauncherAppEntry entry : suggestions) {
            if (ranked.size() >= SUGGESTIONS_CAP) break;
            if (catalogueIds.contains(entry.appRef.stableId())
                && suggestionIds.add(entry.appRef.stableId())) ranked.add(entry);
        }
        buckets.put(AppDrawerCategory.SUGGESTIONS, ranked);

        StringBuilder report = new StringBuilder();
        Map<String, AppDrawerCategoryAssignment> assignments = new HashMap<>();
        List<LauncherAppEntry> recent = new ArrayList<>();
        for (LauncherAppEntry entry : catalogue) {
            long installed = entry.firstInstallTimeEpochMs;
            long age = nowEpochMs - installed;
            if (installed > 0L && age <= RECENT_WINDOW_MS && age >= -FUTURE_SKEW_MS)
                recent.add(entry);
            AppDrawerCategoryAssignment assignment = assign(entry, sdkInt, roleMap, report);
            assignments.put(entry.appRef.stableId(), assignment);
            buckets.computeIfAbsent(assignment.category, ignored -> new ArrayList<>()).add(entry);
        }
        // A tolerated future timestamp is age zero, not "newer than now". Clamp it for ordering
        // as well as eligibility so clock skew cannot jump an entry ahead of apps installed now.
        recent.sort(Comparator.comparingLong(
            (LauncherAppEntry e) -> Math.min(e.firstInstallTimeEpochMs, nowEpochMs))
            .reversed().thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(e -> e.appRef.stableId()));
        if (recent.size() > RECENTLY_ADDED_CAP)
            recent = new ArrayList<>(recent.subList(0, RECENTLY_ADDED_CAP));
        buckets.put(AppDrawerCategory.RECENTLY_ADDED, recent);

        foldHeuristicSingletons(buckets, assignments, report);

        List<AppDrawerCategoryBucket> result = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            List<LauncherAppEntry> entries = buckets.get(category);
            if (entries == null || entries.isEmpty()) continue;
            AppDrawerCategoryBucket bucket = new AppDrawerCategoryBucket(category, entries,
                category.synthetic ? null : usageScores);
            if (!bucket.isEmpty()) result.add(bucket);
        }
        lastReport = report.toString();
        return Collections.unmodifiableList(result);
    }

    /**
     * A semantic bucket holding exactly one heuristically guessed app reads as noise, not as a
     * category. Deliberate placements (user, curated, platform, role) keep their singletons.
     */
    private static void foldHeuristicSingletons(
        @NonNull EnumMap<AppDrawerCategory, List<LauncherAppEntry>> buckets,
        @NonNull Map<String, AppDrawerCategoryAssignment> assignments,
        @NonNull StringBuilder report) {
        List<LauncherAppEntry> folded = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            if (category.synthetic || category == AppDrawerCategory.OTHER) continue;
            List<LauncherAppEntry> entries = buckets.get(category);
            if (entries == null || entries.size() != 1) continue;
            AppDrawerCategoryAssignment assignment =
                assignments.get(entries.get(0).appRef.stableId());
            if (assignment == null || assignment.source != Source.HEURISTIC) continue;
            folded.add(entries.get(0));
            report.append(entries.get(0).appRef.packageName)
                .append(" folded to other: lone heuristic ").append(category.slug).append('\n');
            buckets.remove(category);
        }
        if (folded.isEmpty()) return;
        List<LauncherAppEntry> other = buckets.computeIfAbsent(AppDrawerCategory.OTHER,
            ignored -> new ArrayList<>());
        other.addAll(folded);
        other.sort(Comparator.comparing((LauncherAppEntry e) -> e.label,
            String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.appRef.stableId()));
    }

    @NonNull
    public AppDrawerCategoryAssignment assign(@NonNull LauncherAppEntry entry, int sdkInt,
                                              @NonNull Map<String, AppDrawerCategory> roleMap) {
        return assign(entry, sdkInt, roleMap, new StringBuilder());
    }

    @NonNull
    private AppDrawerCategoryAssignment assign(@NonNull LauncherAppEntry entry, int sdkInt,
                                               @NonNull Map<String, AppDrawerCategory> roleMap,
                                               @NonNull StringBuilder report) {
        AppDrawerCategoryAssignment assignment = assignUnreported(entry, sdkInt, roleMap, report);
        report.append(entry.appRef.packageName).append(" -> ").append(assignment.category.slug)
            .append(" [").append(assignment.source).append(' ')
            .append(String.format(Locale.US, "%.2f", assignment.confidence)).append("]\n");
        return assignment;
    }

    @NonNull
    private AppDrawerCategoryAssignment assignUnreported(
        @NonNull LauncherAppEntry entry, int sdkInt,
        @NonNull Map<String, AppDrawerCategory> roleMap, @NonNull StringBuilder report) {
        String packageName = entry.packageLower;
        if (userOverrides != null) {
            AppDrawerCategory user = userOverrides.categoryForPackage(packageName);
            if (user != null && !user.synthetic)
                return new AppDrawerCategoryAssignment(user, Source.USER, 1f);
        }
        AppDrawerCategory forced = curated.forcedCategoryForPackage(packageName);
        if (forced != null)
            return new AppDrawerCategoryAssignment(forced, Source.CURATED_FORCE, 1f);
        AppDrawerCategory platform = platformCategory(entry, sdkInt);
        if (platform != null)
            return new AppDrawerCategoryAssignment(platform, Source.PLATFORM, 0.9f);
        AppDrawerCategory fill = curated.fillCategoryForPackage(packageName);
        if (fill != null)
            return new AppDrawerCategoryAssignment(fill, Source.CURATED_FILL, 0.8f);
        AppDrawerCategory role = roleMap.get(packageName);
        if (role != null && !role.synthetic)
            return new AppDrawerCategoryAssignment(role, Source.ROLE, 0.7f);
        AppDrawerCategoryAssignment heuristic = heuristicAssignment(entry, report);
        if (heuristic != null) return heuristic;
        return new AppDrawerCategoryAssignment(AppDrawerCategory.OTHER, Source.DEFAULT, 0f);
    }

    /** Kept for direct taxonomy queries; the roleless, override-aware single-entry answer. */
    @NonNull
    public AppDrawerCategory taxonomy(@NonNull LauncherAppEntry entry, int sdkInt) {
        return assign(entry, sdkInt, Collections.emptyMap()).category;
    }

    /** The declared platform category when the SDK exposes one and the app defined it. */
    @Nullable
    private static AppDrawerCategory platformCategory(@NonNull LauncherAppEntry entry, int sdkInt) {
        if (sdkInt < Build.VERSION_CODES.O) return null;
        switch (entry.applicationCategory) {
            case ApplicationInfo.CATEGORY_SOCIAL: return AppDrawerCategory.SOCIAL;
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return AppDrawerCategory.PRODUCTIVITY;
            case ApplicationInfo.CATEGORY_ACCESSIBILITY: return AppDrawerCategory.UTILITIES;
            case ApplicationInfo.CATEGORY_GAME: return AppDrawerCategory.GAMES;
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO: return AppDrawerCategory.ENTERTAINMENT;
            case ApplicationInfo.CATEGORY_IMAGE: return AppDrawerCategory.PHOTO_VIDEO;
            case ApplicationInfo.CATEGORY_MAPS: return AppDrawerCategory.TRAVEL;
            case ApplicationInfo.CATEGORY_NEWS: return AppDrawerCategory.INFORMATION_READING;
            default: return null;
        }
    }

    // ------------------------------------------------------------- heuristics

    /**
     * Whole-word keyword scoring over package segments and label words. Deterministic, offline
     * and never substring-based; a guess needs both an absolute score and a margin over the
     * runner-up before it beats OTHER.
     */
    @Nullable
    private static AppDrawerCategoryAssignment heuristicAssignment(
        @NonNull LauncherAppEntry entry, @NonNull StringBuilder report) {
        EnumMap<AppDrawerCategory, Float> scores = new EnumMap<>(AppDrawerCategory.class);
        for (String token : tokens(entry)) {
            Keyword keyword = KEYWORDS.get(token);
            if (keyword == null) continue;
            Float current = scores.get(keyword.category);
            scores.put(keyword.category, (current == null ? 0f : current) + keyword.weight);
        }
        if (scores.isEmpty()) return null;
        AppDrawerCategory best = null;
        float bestScore = 0f;
        float runnerUpScore = 0f;
        for (Map.Entry<AppDrawerCategory, Float> score : scores.entrySet()) {
            if (best == null || score.getValue() > bestScore) {
                runnerUpScore = best == null ? 0f : bestScore;
                best = score.getKey();
                bestScore = score.getValue();
            } else if (score.getValue() > runnerUpScore) {
                runnerUpScore = score.getValue();
            }
        }
        if (bestScore >= HEURISTIC_THRESHOLD && bestScore - runnerUpScore >= HEURISTIC_MARGIN) {
            if (scores.size() > 1) {
                report.append(entry.appRef.packageName).append(" heuristic runners-up:");
                for (Map.Entry<AppDrawerCategory, Float> score : scores.entrySet()) {
                    if (score.getKey() == best) continue;
                    report.append(' ').append(score.getKey().slug).append('=')
                        .append(String.format(Locale.US, "%.1f", score.getValue()));
                }
                report.append('\n');
            }
            return new AppDrawerCategoryAssignment(best, Source.HEURISTIC,
                Math.min(1f, bestScore / HEURISTIC_CONFIDENCE_CEILING));
        }
        report.append(entry.appRef.packageName).append(" heuristic rejected:");
        for (Map.Entry<AppDrawerCategory, Float> score : scores.entrySet())
            report.append(' ').append(score.getKey().slug).append('=')
                .append(String.format(Locale.US, "%.1f", score.getValue()));
        report.append('\n');
        return null;
    }

    /** Whole words from package segments and the label; camelCase splits, nothing partial. */
    @NonNull
    static Set<String> tokens(@NonNull LauncherAppEntry entry) {
        Set<String> tokens = new LinkedHashSet<>();
        addTokens(tokens, entry.appRef.packageName);
        addTokens(tokens, entry.label);
        return tokens;
    }

    private static void addTokens(@NonNull Set<String> tokens, @NonNull String value) {
        for (String chunk : value.split("[^A-Za-z0-9]+")) {
            if (chunk.isEmpty()) continue;
            for (String word : chunk.split("(?<=[a-z0-9])(?=[A-Z])")) {
                if (!word.isEmpty()) tokens.add(word.toLowerCase(Locale.US));
            }
        }
    }

    @NonNull
    private static Map<String, Keyword> buildKeywords() {
        Map<String, Keyword> keywords = new HashMap<>();
        strong(keywords, AppDrawerCategory.FINANCE, "bank", "banking", "wallet");
        weak(keywords, AppDrawerCategory.FINANCE, "pay", "money", "invest", "finance");
        strong(keywords, AppDrawerCategory.HEALTH, "fitness", "workout");
        weak(keywords, AppDrawerCategory.HEALTH, "health", "gym", "run");
        strong(keywords, AppDrawerCategory.SHOPPING_FOOD, "recipe", "recipes");
        weak(keywords, AppDrawerCategory.SHOPPING_FOOD, "food", "shop", "store");
        strong(keywords, AppDrawerCategory.TRAVEL, "navigation");
        weak(keywords, AppDrawerCategory.TRAVEL, "travel", "hotel", "flight");
        weak(keywords, AppDrawerCategory.INFORMATION_READING, "news", "reader");
        weak(keywords, AppDrawerCategory.GAMES, "game", "games");
        weak(keywords, AppDrawerCategory.PHOTO_VIDEO, "photo", "camera");
        weak(keywords, AppDrawerCategory.ENTERTAINMENT, "video", "music");
        weak(keywords, AppDrawerCategory.SOCIAL, "chat", "messenger");
        weak(keywords, AppDrawerCategory.PRODUCTIVITY, "mail");
        weak(keywords, AppDrawerCategory.UTILITIES, "browser");
        return Collections.unmodifiableMap(keywords);
    }

    private static void strong(@NonNull Map<String, Keyword> keywords,
                               @NonNull AppDrawerCategory category, @NonNull String... words) {
        for (String word : words) keywords.put(word, new Keyword(category, WEIGHT_STRONG));
    }

    private static void weak(@NonNull Map<String, Keyword> keywords,
                             @NonNull AppDrawerCategory category, @NonNull String... words) {
        for (String word : words) keywords.put(word, new Keyword(category, WEIGHT_WEAK));
    }

    /** Multi-line per-package trace of the last {@code classify} pass, for debug logging only. */
    @NonNull public String debugReport() { return lastReport; }

    private static final class Keyword {
        final AppDrawerCategory category;
        final float weight;

        Keyword(@NonNull AppDrawerCategory category, float weight) {
            this.category = category;
            this.weight = weight;
        }
    }
}
