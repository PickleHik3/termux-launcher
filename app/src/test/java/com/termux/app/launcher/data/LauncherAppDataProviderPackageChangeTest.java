package com.termux.app.launcher.data;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Collections;
import java.util.List;

/**
 * An app installed after the catalogue loaded reaches the snapshot through the refresh the
 * launcher runs when no dock is reading it: the drawer, Home's icons and search list from the
 * same snapshot, and an install heard on a page without the apps row used to leave them on the
 * old list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherAppDataProviderPackageChangeTest {
    private Context context;
    private ShadowPackageManager packages;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        packages = shadowOf(context.getPackageManager());
        LauncherAppDataProvider.resetForTest();
    }

    @Test public void anAppInstalledAfterTheLoadIsListedOnceThePackageChangeIsRefreshed() {
        String held = "com.example.held";
        String sideloaded = "com.example.sideloaded";
        installLauncherApp(held, held + ".Main");
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        LauncherAppEntry heldEntry = onlyEntryFor(held, provider.getAllAppsBlocking());
        assertNotNull(heldEntry);

        installLauncherApp(sideloaded, sideloaded + ".Main");
        assertNull("sanity: the held snapshot predates the install",
            onlyEntryFor(sideloaded, provider.getAllApps()));

        provider.refreshAfterPackageChange(Collections.singleton(sideloaded));
        // One worker, FIFO: this returns after the refresh above has landed.
        provider.loadBlockingForTest();

        List<LauncherAppEntry> apps = provider.getAllApps();
        assertNotNull("the new app is listed without a restart or a rebuild from elsewhere",
            onlyEntryFor(sideloaded, apps));
        assertSame("an untouched package keeps its entry",
            heldEntry, onlyEntryFor(held, apps));
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
}
