package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppDrawerCategoryClassifierTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long NOW = 2_000_000_000_000L;

    @Test public void curatedWinsAndEveryPlatformConstantMapsExactly() throws Exception {
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(
            AppDrawerCuratedCategoryMap.parse(new StringReader(
                "# schema=1\ncom.example.bank,finance\n")));
        assertEquals(AppDrawerCategory.FINANCE, classifier.taxonomy(
            app("com.example.bank", "Bank", ApplicationInfo.CATEGORY_PRODUCTIVITY, 0), 28));
        assertCategory(classifier, ApplicationInfo.CATEGORY_SOCIAL, AppDrawerCategory.SOCIAL);
        assertCategory(classifier, ApplicationInfo.CATEGORY_PRODUCTIVITY,
            AppDrawerCategory.PRODUCTIVITY);
        assertCategory(classifier, ApplicationInfo.CATEGORY_ACCESSIBILITY,
            AppDrawerCategory.UTILITIES);
        assertCategory(classifier, ApplicationInfo.CATEGORY_GAME,
            AppDrawerCategory.ENTERTAINMENT);
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

    @Test public void taxonomyIsExclusiveWhileSyntheticOverlapIsDeduplicatedAndOrdered()
        throws Exception {
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
