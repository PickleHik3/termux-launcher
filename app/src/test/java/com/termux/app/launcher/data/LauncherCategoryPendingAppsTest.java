package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.launcher.drawer.AppDrawerCategoryAssignment;
import com.termux.app.launcher.drawer.AppDrawerCategoryAssignment.Source;
import com.termux.app.launcher.drawer.AppDrawerCategoryClassifier;
import com.termux.app.launcher.drawer.AppDrawerCuratedCategoryMap;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

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

/** The one "needs sorting" rule, against the drawer's real classifier. */
public class LauncherCategoryPendingAppsTest {

    private static final int SDK = 28;

    @Test
    public void onlyGuessesAndTheFallToOtherNeedTheModel() {
        for (Source source : Source.values()) {
            boolean confident = source != Source.HEURISTIC && source != Source.DEFAULT;
            assertEquals(source.name(), confident, LauncherCategoryPendingApps.placedConfidently(
                new AppDrawerCategoryAssignment(AppDrawerCategory.SOCIAL, source, 1f)));
        }
        // A sure stage that still lands on Other is no answer at all.
        assertFalse(LauncherCategoryPendingApps.placedConfidently(
            new AppDrawerCategoryAssignment(AppDrawerCategory.OTHER, Source.CURATED_FILL, 1f)));
    }

    @Test
    public void theBuiltInClassifiersSurePlacementsSkipTheModel() throws Exception {
        Map<String, AppDrawerCategory> roles = new HashMap<>();
        roles.put("com.example.dialer", AppDrawerCategory.SOCIAL);
        Placement placement = new Placement(curated("com.example.curated,finance,fill\n"), roles);
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.curated", "Curated", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.example.declared", "Declared", ApplicationInfo.CATEGORY_GAME),
            app("com.example.dialer", "Dialer", ApplicationInfo.CATEGORY_UNDEFINED),
            // "bank" scores FINANCE in the keyword heuristics: a guess, so still the model's.
            app("com.example.bank", "Bank", ApplicationInfo.CATEGORY_UNDEFINED),
            // Nothing at all: the fall to Other.
            app("com.example.mystery", "Mystery", ApplicationInfo.CATEGORY_UNDEFINED));

        assertEquals(Arrays.asList("com.example.bank", "com.example.mystery"),
            packages(LauncherCategoryPendingApps.pending(catalogue, placement)));
    }

    @Test
    public void aDragOrAFileLineWhateverItSaysIsSorted() throws Exception {
        Placement placement = new Placement(AppDrawerCuratedCategoryMap.empty(), Collections.emptyMap());
        placement.user.add("com.example.dragged");
        placement.user.add("com.example.unknownsection");
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.dragged", "Dragged", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.example.unknownsection", "Filed", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.example.new", "New", ApplicationInfo.CATEGORY_UNDEFINED));

        assertEquals(Collections.singletonList("com.example.new"),
            packages(LauncherCategoryPendingApps.pending(catalogue, placement)));
    }

    @Test
    public void aWorkProfileTwinIsTheSameAppAndLinuxAppsNeverCount() {
        Placement placement = new Placement(AppDrawerCuratedCategoryMap.empty(), Collections.emptyMap());
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.new.one", "One", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.new.one", "One", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.new.two", "Two", ApplicationInfo.CATEGORY_UNDEFINED),
            new LauncherAppEntry(X11Apps.ref("gimp.desktop"), "GIMP", null, false,
                ApplicationInfo.CATEGORY_UNDEFINED, 0));

        assertEquals(Arrays.asList("com.new.one", "com.new.two"),
            packages(LauncherCategoryPendingApps.pending(catalogue, placement)));
    }

    @Test
    public void appsTheModelAnsweredOtherForAreNotWaiting() {
        Placement placement = new Placement(AppDrawerCuratedCategoryMap.empty(), Collections.emptyMap());
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.new.one", "One", ApplicationInfo.CATEGORY_UNDEFINED),
            app("Com.New.Two", "Two", ApplicationInfo.CATEGORY_UNDEFINED),
            app("com.new.three", "Three", ApplicationInfo.CATEGORY_UNDEFINED));
        Set<String> answeredOther = new HashSet<>(Collections.singletonList("com.new.two"));

        assertEquals(3, LauncherCategoryPendingApps.pending(catalogue, placement).size());
        assertEquals(2, LauncherCategoryPendingApps.waiting(catalogue, placement, answeredOther));
    }

    @Test
    public void anEmptyCatalogueIsNothingPending() {
        Placement placement = new Placement(AppDrawerCuratedCategoryMap.empty(), Collections.emptyMap());
        assertTrue(LauncherCategoryPendingApps.pending(
            Collections.<LauncherAppEntry>emptyList(), placement).isEmpty());
    }

    /** The real classifier, the user stage replaced by a set of placed packages. */
    private static final class Placement implements LauncherCategoryPendingApps.Placement {
        final Set<String> user = new HashSet<>();
        final AppDrawerCategoryClassifier classifier;
        final Map<String, AppDrawerCategory> roles;

        Placement(AppDrawerCuratedCategoryMap curated, Map<String, AppDrawerCategory> roles) {
            this.classifier = new AppDrawerCategoryClassifier(curated);
            this.roles = roles;
        }

        @Override public boolean placedByUser(String packageLower) {
            return user.contains(packageLower);
        }

        @Override public AppDrawerCategoryAssignment builtIn(LauncherAppEntry entry) {
            return classifier.assign(entry, SDK, roles);
        }
    }

    private static AppDrawerCuratedCategoryMap curated(String rows) throws Exception {
        return AppDrawerCuratedCategoryMap.parse(new StringReader(AppDrawerCuratedCategoryMap.SCHEMA_LINE + "\n" + rows));
    }

    private static LauncherAppEntry app(String packageName, String label, int category) {
        return new LauncherAppEntry(new AppRef(packageName, "Main"), label, null, false, category, 0);
    }

    private static List<String> packages(List<LauncherAppEntry> entries) {
        List<String> packages = new ArrayList<>();
        for (LauncherAppEntry entry : entries) packages.add(entry.appRef.packageName);
        return packages;
    }
}
