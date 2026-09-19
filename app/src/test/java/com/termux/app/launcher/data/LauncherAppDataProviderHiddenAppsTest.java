package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * D8 (project-docs/distro-apps/SPEC.md): a hidden app leaves the drawer's own listings but stays
 * resolvable by exact reference, so a pin, a folder member or an "open <package>" command does
 * not silently break just because the app it names was hidden.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherAppDataProviderHiddenAppsTest {
    private static final String HIDDEN_APPS_KEY = "app_launcher_hidden_apps_v1";
    private Context context;
    private ShadowPackageManager packages;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        packages = shadowOf(context.getPackageManager());
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
        context.getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
                Context.MODE_PRIVATE)
            .edit().remove(HIDDEN_APPS_KEY).commit();
    }

    // -- LauncherAppDataProvider.filterHidden: pure, no package manager involved --

    @Test public void filterHiddenSkipsOnlyMatchingStableIdsAndKeepsOrder() {
        LauncherAppEntry a = entry("com.example.a", "Main", "A");
        LauncherAppEntry b = entry("com.example.b", "Main", "B");
        LauncherAppEntry c = entry("com.example.c", "Main", "C");
        Set<String> hidden = new LinkedHashSet<>(Collections.singletonList(b.appRef.stableId()));

        List<LauncherAppEntry> visible =
            LauncherAppDataProvider.filterHidden(Arrays.asList(a, b, c), hidden);

        assertEquals(Arrays.asList(a, c), visible);
    }

    @Test public void filterHiddenReturnsTheSameListWhenNothingIsHidden() {
        LauncherAppEntry a = entry("com.example.a", "Main", "A");
        List<LauncherAppEntry> apps = Collections.singletonList(a);
        assertSame(apps, LauncherAppDataProvider.filterHidden(apps, Collections.emptySet()));
    }

    @Test public void filterHiddenMatchesAContainerQualifiedLinuxAppIdExactly() {
        LauncherAppEntry debianFirefox = entry("x11:linux", "distro:debian:firefox", "Firefox");
        LauncherAppEntry archFirefox = entry("x11:linux", "distro:arch:firefox", "Firefox");
        Set<String> hidden = new LinkedHashSet<>(
            Collections.singletonList(debianFirefox.appRef.stableId()));

        List<LauncherAppEntry> visible = LauncherAppDataProvider.filterHidden(
            Arrays.asList(debianFirefox, archFirefox), hidden);

        assertEquals(Collections.singletonList(archFirefox), visible);
    }

    // -- Through the live provider: install a real launchable app and hide it --

    @Test public void hidingAnAppDropsItFromListingsButKeepsItResolvableByReference() {
        String pkg = "com.example.hideme";
        installLauncherApp(pkg, pkg + ".Main");
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);

        List<LauncherAppEntry> before = provider.getAllAppsBlocking();
        LauncherAppEntry target = onlyEntryFor(pkg, before);
        assertNotNull("the app must be visible before it is hidden", target);
        char letter = LauncherAppDataProvider.normalizeLetter(target.label);
        assertTrue("sanity: the letter bucket carries it before hiding",
            provider.getAppsForLetter(letter).contains(target));

        provider.hiddenApps().setHidden(target.appRef, true);

        assertNull("hidden from the plain listing",
            onlyEntryFor(pkg, provider.getAllAppsBlocking()));
        assertNull("hidden from getAllApps() too",
            onlyEntryFor(pkg, provider.getAllApps()));
        assertTrue("hidden from its A-Z letter bucket",
            !provider.getAppsForLetter(letter).contains(target));

        // Still resolvable: a pin, a folder member or a terminal "open <package>" keeps working.
        assertNotNull("findByRef must still resolve a hidden app",
            provider.findByRef(target.appRef));
        assertNotNull("findFirstByPackage must still resolve a hidden app",
            provider.findFirstByPackage(pkg));
        assertNotNull("findDefaultByPackage must still resolve a hidden app",
            provider.findDefaultByPackage(pkg));

        // The settings screen that edits the hidden set needs to find it again to restore it.
        assertNotNull("the full catalogue (for the settings list) still carries it",
            onlyEntryFor(pkg, provider.getAllAppsIncludingHiddenBlocking()));

        provider.hiddenApps().setHidden(target.appRef, false);
        assertNotNull("un-hiding restores it to the plain listing",
            onlyEntryFor(pkg, provider.getAllAppsBlocking()));
    }

    @Test public void hidingIsReadLiveEvenAfterTheCatalogueIsRescanned() {
        String pkg = "com.example.rescan";
        installLauncherApp(pkg, pkg + ".Main");
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        LauncherAppEntry target = onlyEntryFor(pkg, provider.getAllAppsBlocking());
        assertNotNull(target);

        provider.hiddenApps().setHidden(target.appRef, true);
        provider.invalidate();

        assertNull("a fresh scan must not resurrect a hidden app",
            onlyEntryFor(pkg, provider.getAllAppsBlocking()));
    }

    private void installLauncherApp(String pkg, String activity) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = pkg;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = pkg;
        packages.installPackage(packageInfo);

        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        ResolveInfo resolve = new ResolveInfo();
        resolve.nonLocalizedLabel = activity;
        resolve.activityInfo = new ActivityInfo();
        resolve.activityInfo.packageName = pkg;
        resolve.activityInfo.name = activity;
        resolve.activityInfo.nonLocalizedLabel = activity;
        resolve.activityInfo.applicationInfo = new ApplicationInfo();
        resolve.activityInfo.applicationInfo.packageName = pkg;
        packages.addResolveInfoForIntent(launcher, resolve);
    }

    private static LauncherAppEntry onlyEntryFor(String pkg, List<LauncherAppEntry> entries) {
        for (LauncherAppEntry entry : entries)
            if (pkg.equals(entry.appRef.packageName)) return entry;
        return null;
    }

    private static LauncherAppEntry entry(String pkg, String activity, String label) {
        return new LauncherAppEntry(new AppRef(pkg, activity), label, null);
    }
}
