package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * One app's page: each row writes the per-app store and Follow display setting clears it; opened
 * without an app it shows nothing and leaves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11AppPreferencesFragmentTest {
    private static final String ID = "firefox";

    private Context context;
    private X11AppSettingsStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
        store = new X11AppSettingsStore(context);
    }

    private X11AppPreferencesFragment launch(Bundle args) {
        Application app = RuntimeEnvironment.getApplication();
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class,
            SettingsActivity.createFragmentIntent(app, X11AppsPreferencesFragment.class,
                R.string.settings_x11_apps_title)).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        activity.openScreen(X11AppPreferencesFragment.class, R.string.settings_x11_apps_title, args);
        activity.getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertNotNull(fragment);
        return fragment instanceof X11AppPreferencesFragment ? (X11AppPreferencesFragment) fragment : null;
    }

    private static Bundle forApp() {
        Bundle args = new Bundle();
        args.putString(X11AppPreferencesFragment.ARG_ID, ID);
        args.putString(X11AppPreferencesFragment.ARG_NAME, "Firefox");
        return args;
    }

    private static void pick(X11AppPreferencesFragment page, String key, String value) {
        ListPreference row = page.findPreference(key);
        assertNotNull(key, row);
        assertTrue(row.getOnPreferenceChangeListener().onPreferenceChange(row, value));
    }

    @Test
    public void everyRowStartsOnFollow() {
        X11AppPreferencesFragment page = launch(forApp());
        assertNotNull(page);
        for (String key : new String[] {X11AppPreferencesFragment.KEY_GRAPHICS,
            X11AppPreferencesFragment.KEY_TOUCH, X11AppPreferencesFragment.KEY_SCALE,
            X11AppPreferencesFragment.KEY_DRAWER}) {
            ListPreference row = page.findPreference(key);
            assertEquals(key, X11AppPreferencesFragment.FOLLOW, row.getValue());
            assertFalse(row.isPersistent());
        }
    }

    @Test
    public void graphicsWritesTheSoftwareSwitch() {
        X11AppPreferencesFragment page = launch(forApp());
        pick(page, X11AppPreferencesFragment.KEY_GRAPHICS, X11AppPreferencesFragment.SOFTWARE);
        assertTrue(store.drawsInSoftware(ID));
        pick(page, X11AppPreferencesFragment.KEY_GRAPHICS, X11AppPreferencesFragment.FOLLOW);
        assertFalse(store.drawsInSoftware(ID));
    }

    @Test
    public void touchModeWritesAndFollowClears() {
        X11AppPreferencesFragment page = launch(forApp());
        pick(page, X11AppPreferencesFragment.KEY_TOUCH, "3");
        assertEquals("3", store.touchMode(ID));
        pick(page, X11AppPreferencesFragment.KEY_TOUCH, X11AppPreferencesFragment.FOLLOW);
        assertNull(store.touchMode(ID));
    }

    @Test
    public void scaleWritesAndFollowClears() {
        X11AppPreferencesFragment page = launch(forApp());
        pick(page, X11AppPreferencesFragment.KEY_SCALE, "1.5");
        assertEquals("1.5", store.scale(ID));
        ListPreference row = page.findPreference(X11AppPreferencesFragment.KEY_SCALE);
        assertEquals("1.5", row.getValue());
        assertTrue(row.getSummary().toString().contains("Takes effect the next time the app opens."));
        pick(page, X11AppPreferencesFragment.KEY_SCALE, X11AppPreferencesFragment.FOLLOW);
        assertNull(store.scale(ID));
    }

    @Test
    public void drawerHidesAndFollowShows() {
        X11AppPreferencesFragment page = launch(forApp());
        pick(page, X11AppPreferencesFragment.KEY_DRAWER, X11AppPreferencesFragment.HIDE);
        assertTrue(store.hiddenInDrawer(ID));
        pick(page, X11AppPreferencesFragment.KEY_DRAWER, X11AppPreferencesFragment.FOLLOW);
        assertFalse(store.hiddenInDrawer(ID));
    }

    @Test
    public void withoutAnIdThePageShowsNothing() {
        X11AppPreferencesFragment page = launch(null);
        // The page leaves on its own; whichever fragment is on show, nothing was built or written.
        if (page != null) assertEquals(0, page.getPreferenceScreen().getPreferenceCount());
        assertTrue(store.appsWithOverrides().isEmpty());
    }

    @Test
    public void customSettingsTagOnlyWhenTheAppDiffers() {
        assertNull(X11AppsPreferencesFragment.tag(context, false));
        assertEquals(context.getString(R.string.settings_x11_apps_custom),
            X11AppsPreferencesFragment.tag(context, true).toString());
        assertFalse(store.differsFromDisplay(ID));
        store.setScale(ID, "2");
        assertTrue(store.differsFromDisplay(ID));
    }
}
