package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.TestLauncherActivityInfo;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherAppDataProviderCategoryMetadataTest {
    private Context context;
    private ShadowPackageManager packages;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        packages = shadowOf(context.getPackageManager());
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
    }

    @Test public void primaryCarriesDeclaredCategoryAndSharedPackageFirstInstallTime() {
        String pkg = "com.example.metadata";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = pkg;
        packageInfo.firstInstallTime = 123456L;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = pkg;
        packages.installPackage(packageInfo);
        addLauncher(pkg, pkg + ".One", ApplicationInfo.CATEGORY_PRODUCTIVITY);
        addLauncher(pkg, pkg + ".Two", ApplicationInfo.CATEGORY_PRODUCTIVITY);

        List<LauncherAppEntry> found = entriesFor(pkg,
            LauncherAppDataProvider.getInstance(context).getAllAppsBlocking());
        assertEquals(2, found.size());
        for (LauncherAppEntry entry : found) {
            assertEquals(ApplicationInfo.CATEGORY_PRODUCTIVITY, entry.applicationCategory);
            assertEquals(123456L, entry.firstInstallTimeEpochMs);
        }
    }

    @Test public void installLookupCacheCallsItsSourceOncePerPackageIncludingFailure() {
        LauncherAppDataProvider.FirstInstallTimeCache cache =
            new LauncherAppDataProvider.FirstInstallTimeCache();
        int[] calls = {0};
        assertEquals(7L, cache.valueFor("com.example.same", pkg -> { calls[0]++; return 7L; }));
        assertEquals(7L, cache.valueFor("com.example.same", pkg -> { calls[0]++; return 9L; }));
        assertEquals(1, calls[0]);
        assertEquals(0L, cache.valueFor("com.example.fail", pkg -> {
            calls[0]++; throw new SecurityException("denied");
        }));
        assertEquals(0L, cache.valueFor("com.example.fail", pkg -> { calls[0]++; return 3L; }));
        assertEquals(2, calls[0]);
    }

    @Test public void profileMetadataComesFromLauncherActivityAndFailureIsPerEntry() {
        ApplicationInfo info = new ApplicationInfo();
        info.category = ApplicationInfo.CATEGORY_SOCIAL;
        LauncherAppDataProvider.EntryMetadata good = LauncherAppDataProvider.readProfileMetadata(
            new TestLauncherActivityInfo(info, 99L, false, false), 28);
        assertEquals(ApplicationInfo.CATEGORY_SOCIAL, good.applicationCategory);
        assertEquals(99L, good.firstInstallTimeEpochMs);

        LauncherAppDataProvider.EntryMetadata failed = LauncherAppDataProvider.readProfileMetadata(
            new TestLauncherActivityInfo(info, 99L, true, true), 28);
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, failed.applicationCategory);
        assertEquals(0L, failed.firstInstallTimeEpochMs);
        // A neighboring entry remains intact; failure state is not shared.
        LauncherAppDataProvider.EntryMetadata neighbor = LauncherAppDataProvider.readProfileMetadata(
            new TestLauncherActivityInfo(info, 101L, false, false), 28);
        assertEquals(101L, neighbor.firstInstallTimeEpochMs);
    }

    @Test
    public void api25IgnoresDeclaredCategoryButStillCarriesInstallTime() {
        ApplicationInfo info = new ApplicationInfo();
        info.category = ApplicationInfo.CATEGORY_SOCIAL;
        LauncherAppDataProvider.EntryMetadata metadata = LauncherAppDataProvider.readProfileMetadata(
            new TestLauncherActivityInfo(info, 55L, true, false), 25);
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, metadata.applicationCategory);
        assertEquals(55L, metadata.firstInstallTimeEpochMs);
    }

    @Test public void oldConstructorsRemainUndefinedAndZero() {
        LauncherAppEntry entry = new LauncherAppEntry(
            new AppRef("com.example.old", "Main"), "Old", null);
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, entry.applicationCategory);
        assertEquals(0L, entry.firstInstallTimeEpochMs);
    }

    private void addLauncher(String pkg, String activity, int category) {
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
        resolve.activityInfo.applicationInfo.category = category;
        packages.addResolveInfoForIntent(launcher, resolve);
    }

    private static List<LauncherAppEntry> entriesFor(String pkg, List<LauncherAppEntry> entries) {
        List<LauncherAppEntry> result = new ArrayList<>();
        for (LauncherAppEntry entry : entries)
            if (pkg.equals(entry.appRef.packageName)) result.add(entry);
        return result;
    }
}
