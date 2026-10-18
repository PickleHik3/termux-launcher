package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The dock prunes pins that no longer resolve (an uninstalled app). The Terminal entry is in
 * every catalogue, so it must resolve whatever state the catalogue is in, or the prune takes the
 * way back to the Workspace off the dock for good: its once-only pin never comes back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherAppDataProviderTerminalEntryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
    }

    @Test public void terminalResolvesBeforeAnyCatalogueIsLoaded() {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        assertFalse(provider.hasLoadedApps());

        LauncherAppEntry entry = provider.findByRef(LauncherEntries.terminal());

        assertNotNull(entry);
        assertEquals(LauncherEntries.terminal().stableId(), entry.appRef.stableId());
        assertEquals(context.getString(com.termux.R.string.launcher_entry_terminal), entry.label);
    }

    @Test public void terminalStillResolvesAfterTheCatalogueIsInvalidated() {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        assertTrue(contains(provider, LauncherEntries.terminal()));

        provider.invalidate();

        assertNotNull(provider.findByRef(LauncherEntries.terminal()));
    }

    @Test public void displayAndUnknownAppsDoNotResolveWithoutACatalogue() {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        assertNull(provider.findByRef(LauncherEntries.display()));
        assertNull(provider.findByRef(new AppRef("com.example.gone", "com.example.gone.Main")));
    }

    @Test public void onlyTerminalIsAlwaysListed() {
        assertTrue(LauncherEntries.isAlwaysListed(LauncherEntries.terminal()));
        assertFalse(LauncherEntries.isAlwaysListed(LauncherEntries.display()));
        assertFalse(LauncherEntries.isAlwaysListed(new AppRef("com.example", "terminal")));
        assertFalse(LauncherEntries.isAlwaysListed(null));
    }

    private static boolean contains(LauncherAppDataProvider provider, AppRef ref) {
        for (LauncherAppEntry entry : provider.getAllAppsBlocking()) {
            if (ref.stableId().equals(entry.appRef.stableId())) return true;
        }
        return false;
    }
}
