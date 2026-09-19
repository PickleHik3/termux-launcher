package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherHiddenAppsStoreTest {
    private static final String KEY = "app_launcher_hidden_apps_v1";
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
                Context.MODE_PRIVATE)
            .edit().remove(KEY).commit();
    }

    @Test public void hidingAnAppRemovesItAndShowingRestoresIt() {
        LauncherHiddenAppsStore store = new LauncherHiddenAppsStore(context);
        AppRef ref = new AppRef("com.example.app", "com.example.app.MainActivity");
        assertFalse(store.isHidden(ref));

        store.setHidden(ref, true);
        assertTrue(store.isHidden(ref));
        assertTrue(store.hiddenStableIds().contains(ref.stableId()));

        store.setHidden(ref, false);
        assertFalse(store.isHidden(ref));
        assertTrue(store.hiddenStableIds().isEmpty());
    }

    @Test public void hiddenSetSurvivesAReload() {
        AppRef ref = new AppRef("com.example.reload", "Main");
        new LauncherHiddenAppsStore(context).setHidden(ref, true);

        // A fresh instance re-reads the same SharedPreferences file rather than sharing any
        // in-memory state with the one above — this is what a relaunched process does.
        LauncherHiddenAppsStore reloaded = new LauncherHiddenAppsStore(context);
        assertTrue(reloaded.isHidden(ref));
        assertEquals(1, reloaded.hiddenStableIds().size());
    }

    @Test public void containerQualifiedIdHidesOnlyThatContainerAppNotTheWholePackage() {
        // Every Linux app shares the reserved package "x11:linux"; a container app's id also
        // carries its container ("distro:<container>:<desktop file>"), which is what must be
        // keyed on instead of the package alone (unlike LauncherCategoryOverrideStore).
        AppRef debianFirefox = new AppRef("x11:linux", "distro:debian:firefox");
        AppRef archFirefox = new AppRef("x11:linux", "distro:arch:firefox");
        AppRef prefixFirefox = new AppRef("x11:linux", "firefox");
        LauncherHiddenAppsStore store = new LauncherHiddenAppsStore(context);

        store.setHidden(debianFirefox, true);

        assertTrue(store.isHidden(debianFirefox));
        assertFalse("a sibling container's copy of the same app must stay visible",
            store.isHidden(archFirefox));
        assertFalse("the prefix's copy of the same app must stay visible",
            store.isHidden(prefixFirefox));

        // Restored correctly: unhiding it clears exactly that id and nothing else.
        store.setHidden(debianFirefox, false);
        assertFalse(store.isHidden(debianFirefox));
        assertTrue(store.isEmpty());
    }

    @Test public void malformedStoredJsonIsTreatedAsNothingHidden() {
        SharedPreferences prefs = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        prefs.edit().putString(KEY, "{not json").commit();

        LauncherHiddenAppsStore store = new LauncherHiddenAppsStore(context);
        assertTrue(store.isEmpty());
        Set<String> hidden = store.hiddenStableIds();
        assertTrue(hidden.isEmpty());
    }
}
