package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.termux.app.launcher.drawer.AppDrawerCategoryAssignment.Source;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppDrawerCategoryClassifierTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long NOW = 2_000_000_000_000L;
    private static final Map<String, AppDrawerCategory> NO_ROLES = Collections.emptyMap();

    @Test public void precedenceUserOverrideBeatsCuratedForceBeatsPlatform() throws Exception {
        Map<String, AppDrawerCategory> overrides = new HashMap<>();
        overrides.put("com.example.bank", AppDrawerCategory.HEALTH);
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.parse(new StringReader(
                "# schema=2\ncom.example.bank,finance,force\n")), overrides::get);
        LauncherAppEntry entry = app("com.example.bank", "Bank",
            ApplicationInfo.CATEGORY_SOCIAL, 0);
        AppDrawerCategoryAssignment user = classifier.assign(entry, 28, NO_ROLES);
        assertEquals(AppDrawerCategory.HEALTH, user.category);
        assertEquals(Source.USER, user.source);

        overrides.clear();
        AppDrawerCategoryAssignment forced = classifier.assign(entry, 28, NO_ROLES);
        assertEquals(AppDrawerCategory.FINANCE, forced.category);
        assertEquals(Source.CURATED_FORCE, forced.source);
    }

    @Test public void platformBeatsFillWhileFillBeatsRoleAndHeuristic() throws Exception {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.parse(new StringReader(
                "# schema=2\ncom.example.bank,shopping_food,fill\n")));
        LauncherAppEntry declared = app("com.example.bank", "Bank",
            ApplicationInfo.CATEGORY_SOCIAL, 0);
        AppDrawerCategoryAssignment platform = classifier.assign(declared, 28, NO_ROLES);
        assertEquals(AppDrawerCategory.SOCIAL, platform.category);
        assertEquals(Source.PLATFORM, platform.source);

        LauncherAppEntry undeclared = app("com.example.bank", "Bank",
            ApplicationInfo.CATEGORY_UNDEFINED, 0);
        Map<String, AppDrawerCategory> roles =
            Collections.singletonMap("com.example.bank", AppDrawerCategory.TRAVEL);
        AppDrawerCategoryAssignment fill = classifier.assign(undeclared, 28, roles);
        assertEquals(AppDrawerCategory.SHOPPING_FOOD, fill.category);
        assertEquals(Source.CURATED_FILL, fill.source);
        // Below API 26 the declared category is unreadable, so fill wins there too.
        assertEquals(Source.CURATED_FILL, classifier.assign(declared, 25, NO_ROLES).source);
    }

    @Test public void roleBeatsHeuristicAndDefaultIsOther() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        LauncherAppEntry entry = app("com.example.bank", "Bank",
            ApplicationInfo.CATEGORY_UNDEFINED, 0);
        Map<String, AppDrawerCategory> roles =
            Collections.singletonMap("com.example.bank", AppDrawerCategory.UTILITIES);
        AppDrawerCategoryAssignment role = classifier.assign(entry, 28, roles);
        assertEquals(AppDrawerCategory.UTILITIES, role.category);
        assertEquals(Source.ROLE, role.source);

        AppDrawerCategoryAssignment heuristic = classifier.assign(entry, 28, NO_ROLES);
        assertEquals(AppDrawerCategory.FINANCE, heuristic.category);
        assertEquals(Source.HEURISTIC, heuristic.source);

        AppDrawerCategoryAssignment other = classifier.assign(
            app("com.example.plain", "Plain", ApplicationInfo.CATEGORY_UNDEFINED, 0), 28,
            NO_ROLES);
        assertEquals(AppDrawerCategory.OTHER, other.category);
        assertEquals(Source.DEFAULT, other.source);
        assertEquals(0f, other.confidence, 0f);
    }

    @Test public void everyPlatformConstantMapsExactlyIncludingGames() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        assertCategory(classifier, ApplicationInfo.CATEGORY_SOCIAL, AppDrawerCategory.SOCIAL);
        assertCategory(classifier, ApplicationInfo.CATEGORY_PRODUCTIVITY,
            AppDrawerCategory.PRODUCTIVITY);
        assertCategory(classifier, ApplicationInfo.CATEGORY_ACCESSIBILITY,
            AppDrawerCategory.UTILITIES);
        assertCategory(classifier, ApplicationInfo.CATEGORY_GAME, AppDrawerCategory.GAMES);
        assertCategory(classifier, ApplicationInfo.CATEGORY_AUDIO,
            AppDrawerCategory.ENTERTAINMENT);
        assertCategory(classifier, ApplicationInfo.CATEGORY_VIDEO,
            AppDrawerCategory.ENTERTAINMENT);
        assertCategory(classifier, ApplicationInfo.CATEGORY_IMAGE,
            AppDrawerCategory.PHOTO_VIDEO);
        assertCategory(classifier, ApplicationInfo.CATEGORY_MAPS, AppDrawerCategory.TRAVEL);
        assertCategory(classifier, ApplicationInfo.CATEGORY_NEWS,
            AppDrawerCategory.INFORMATION_READING);
        assertCategory(classifier, ApplicationInfo.CATEGORY_UNDEFINED, AppDrawerCategory.OTHER);
        assertCategory(classifier, Integer.MAX_VALUE, AppDrawerCategory.OTHER);
        assertEquals(AppDrawerCategory.OTHER, classifier.taxonomy(
            app("com.example.old", "Old", ApplicationInfo.CATEGORY_SOCIAL, 0), 25));
    }

    @Test public void heuristicsNeedThresholdAndMarginAndNeverMatchSubstrings() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        // Two weak words in one category reach the threshold; camelCase splits count as words.
        AppDrawerCategoryAssignment weakPair = classifier.assign(
            app("com.example.app", "MoneyPay", ApplicationInfo.CATEGORY_UNDEFINED, 0), 28,
            NO_ROLES);
        assertEquals(AppDrawerCategory.FINANCE, weakPair.category);
        assertEquals(Source.HEURISTIC, weakPair.source);
        // One weak word stays below the threshold.
        assertEquals(Source.DEFAULT, classifier.assign(
            app("com.example.app", "Pay", ApplicationInfo.CATEGORY_UNDEFINED, 0), 28,
            NO_ROLES).source);
        // Equal strong scores in two categories fail the margin.
        assertEquals(Source.DEFAULT, classifier.assign(
            app("com.example.app", "Bank Fitness", ApplicationInfo.CATEGORY_UNDEFINED, 0), 28,
            NO_ROLES).source);
        // Whole words only: "bankruptcy" is not "bank".
        assertEquals(Source.DEFAULT, classifier.assign(
            app("com.example.bankruptcy", "Bankruptcy", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            28, NO_ROLES).source);
    }

    @Test public void loneHeuristicSingletonFoldsToOtherButDeliberateSingletonsStay()
        throws Exception {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.parse(new StringReader(
                "# schema=2\ncom.example.curated,health,fill\n")));
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.alpha", "Alpha", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            app("com.example.bank", "Zeta Bank", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            app("com.example.curated", "Curated", ApplicationInfo.CATEGORY_UNDEFINED, 0));
        List<AppDrawerCategoryBucket> buckets = classifier.classify(catalogue,
            Collections.emptyList(), NOW, 28);
        assertEquals(Arrays.asList(AppDrawerCategory.HEALTH, AppDrawerCategory.OTHER),
            categories(buckets));
        // The folded entry lands in OTHER in label order, not appended.
        assertEquals(Arrays.asList("Alpha", "Zeta Bank"),
            labels(bucket(buckets, AppDrawerCategory.OTHER).entries()));
        assertEquals(1, bucket(buckets, AppDrawerCategory.HEALTH).size());
        // Two heuristic entries in one category are a real bucket, not noise.
        List<AppDrawerCategoryBucket> pair = classifier.classify(Arrays.asList(
            app("com.example.bank", "Bank One", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            app("com.example.wallet", "Wallet Two", ApplicationInfo.CATEGORY_UNDEFINED, 0)),
            Collections.emptyList(), NOW, 28);
        assertEquals(Collections.singletonList(AppDrawerCategory.FINANCE), categories(pair));
    }

    @Test public void suggestionsCapAtEightAndRecentlyAddedCapAtTwelveNewest() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        List<LauncherAppEntry> catalogue = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            catalogue.add(app("com.example.n" + (char) ('a' + i), "App " + (char) ('a' + i),
                ApplicationInfo.CATEGORY_UNDEFINED, NOW - i * DAY));
        }
        List<AppDrawerCategoryBucket> buckets = classifier.classify(catalogue,
            new ArrayList<>(catalogue), NOW, 28);
        assertEquals(AppDrawerCategoryClassifier.SUGGESTIONS_CAP,
            bucket(buckets, AppDrawerCategory.SUGGESTIONS).size());
        List<LauncherAppEntry> recent =
            bucket(buckets, AppDrawerCategory.RECENTLY_ADDED).entries();
        assertEquals(AppDrawerCategoryClassifier.RECENTLY_ADDED_CAP, recent.size());
        assertEquals("App a", recent.get(0).label);
        assertEquals("App l", recent.get(recent.size() - 1).label);
    }

    @Test public void previewsFloatUsedEntriesWhileTheEntryListStaysAlphabetical() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        List<LauncherAppEntry> catalogue = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            catalogue.add(app("com.example.n" + (char) ('a' + i), "App " + (char) ('a' + i),
                ApplicationInfo.CATEGORY_UNDEFINED, 0));
        }
        Map<String, Double> scores = new HashMap<>();
        scores.put(catalogue.get(8).appRef.stableId(), 5.0);
        scores.put(catalogue.get(4).appRef.stableId(), 2.0);
        List<AppDrawerCategoryBucket> buckets = classifier.classify(catalogue,
            Collections.emptyList(), scores, NO_ROLES, NOW, 28);
        AppDrawerCategoryBucket other = bucket(buckets, AppDrawerCategory.OTHER);
        assertEquals(Arrays.asList("App a", "App b", "App c", "App d", "App e", "App f",
            "App g", "App h", "App i"), labels(other.entries()));
        assertEquals(Arrays.asList("App i", "App e", "App a", "App b", "App c", "App d",
            "App f"), labels(other.previews()));
    }

    @Test public void taxonomyIsExclusiveWhileSyntheticOverlapIsDeduplicatedAndOrdered() {
        LauncherAppEntry social = app("com.example.social", "Social",
            ApplicationInfo.CATEGORY_SOCIAL, NOW);
        LauncherAppEntry utility = app("com.example.utility", "Utility",
            ApplicationInfo.CATEGORY_ACCESSIBILITY, NOW - DAY);
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        List<AppDrawerCategoryBucket> buckets = classifier.classify(
            Arrays.asList(social, utility), Arrays.asList(utility, social, social), NOW, 28);

        assertEquals(Arrays.asList(AppDrawerCategory.SUGGESTIONS,
            AppDrawerCategory.RECENTLY_ADDED, AppDrawerCategory.SOCIAL,
            AppDrawerCategory.UTILITIES), categories(buckets));
        assertEquals(2, bucket(buckets, AppDrawerCategory.SUGGESTIONS).size());
        Set<String> taxonomyIds = new HashSet<>();
        for (AppDrawerCategoryBucket value : buckets) {
            Set<String> within = new HashSet<>();
            for (LauncherAppEntry entry : value.entries()) {
                assertTrue(within.add(entry.appRef.stableId()));
                if (!value.category.synthetic) assertTrue(taxonomyIds.add(entry.appRef.stableId()));
            }
        }
        assertEquals(2, taxonomyIds.size());
    }

    @Test public void emptyBucketsAreOmittedAndZeroCatalogueProducesEmptyModel() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        assertTrue(classifier.classify(Collections.emptyList(), Collections.emptyList(), NOW, 28)
            .isEmpty());
        List<AppDrawerCategoryBucket> one = classifier.classify(Collections.singletonList(
            app("com.example.other", "Other", ApplicationInfo.CATEGORY_UNDEFINED, 0)),
            Collections.emptyList(), NOW, 28);
        assertEquals(Collections.singletonList(AppDrawerCategory.OTHER), categories(one));
    }

    @Test public void recentWindowAndFutureSkewUseFirstInstallTimeOnly() {
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.boundary", "Boundary", 0, NOW - 30L * DAY),
            app("com.example.outside", "Outside", 0, NOW - 30L * DAY - 1L),
            app("com.example.zero", "Zero", 0, 0L),
            app("com.example.future", "Zulu", 0, NOW + DAY),
            app("com.example.now", "Alpha", 0, NOW),
            app("com.example.toofar", "Too far", 0, NOW + DAY + 1L));
        List<AppDrawerCategoryBucket> buckets = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty()).classify(catalogue,
            Collections.emptyList(), NOW, Build.VERSION_CODES.P);
        List<LauncherAppEntry> recent = bucket(buckets, AppDrawerCategory.RECENTLY_ADDED).entries();
        assertEquals(Arrays.asList("Alpha", "Zulu", "Boundary"), labels(recent));
        assertFalse(labels(recent).contains("Outside"));
        assertFalse(labels(recent).contains("Zero"));
        assertFalse(labels(recent).contains("Too far"));
    }

    @Test public void debugReportTracesTheLastClassifyPass() {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.empty());
        classifier.classify(Collections.singletonList(
            app("com.example.bank", "Bank", ApplicationInfo.CATEGORY_UNDEFINED, 0)),
            Collections.emptyList(), NOW, 28);
        String report = classifier.debugReport();
        assertTrue(report.contains("com.example.bank"));
        assertTrue(report.contains("HEURISTIC"));
        assertTrue(report.contains("folded to other"));
    }

    private static void assertCategory(AppDrawerCategoryClassifier classifier, int platform,
                                       AppDrawerCategory expected) {
        assertEquals(expected, classifier.taxonomy(
            app("com.example.p" + platform, "App", platform, 0), 28));
    }

    private static LauncherAppEntry app(String pkg, String label, int category, long installed) {
        return new LauncherAppEntry(new AppRef(pkg, "Main"), label, null, false,
            category, installed);
    }

    private static List<AppDrawerCategory> categories(List<AppDrawerCategoryBucket> buckets) {
        List<AppDrawerCategory> result = new ArrayList<>();
        for (AppDrawerCategoryBucket value : buckets) result.add(value.category);
        return result;
    }

    private static AppDrawerCategoryBucket bucket(List<AppDrawerCategoryBucket> buckets,
                                                   AppDrawerCategory category) {
        for (AppDrawerCategoryBucket value : buckets) if (value.category == category) return value;
        throw new AssertionError("missing " + category);
    }

    private static List<String> labels(List<LauncherAppEntry> entries) {
        List<String> result = new ArrayList<>();
        for (LauncherAppEntry entry : entries) result.add(entry.label);
        return result;
    }
}
