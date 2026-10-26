package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * F4: on a screen whose smallest width is 600 dp the root list stands beside the open section; a
 * list row switches the section, deeper pages stack in the detail, Back from a section's page
 * leaves Settings. A phone stays one pane even in landscape. Crossing 600 dp (a fold, a window
 * resize) rebuilds the stack for the new shape without losing the reader's place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "sw700dp-w900dp-h700dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsTwoPaneTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    private static ActivityController<SettingsActivity> open() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)).create().start().resume().visible();
        idle(controller.get());
        return controller;
    }

    private static void idle(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Fragment detail(SettingsActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    private static Fragment list(SettingsActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings_list);
    }

    private static void tap(SettingsActivity activity, PreferenceFragmentCompat page, String key) {
        activity.onPreferenceStartFragment(page, page.findPreference(key));
        idle(activity);
    }

    @Test
    public void theListStandsBesideHome() {
        SettingsActivity activity = open().get();
        assertTrue(activity.isTwoPane());
        assertTrue(list(activity) instanceof SettingsActivity.RootPreferencesFragment);
        assertEquals(PAGES + "HomePreferencesFragment", detail(activity).getClass().getName());
        assertFalse("the list's own box is in view", activity.showsSearchAction());
    }

    @Test
    public void aListRowSwitchesTheSectionAndBackFromItLeaves() {
        SettingsActivity activity = open().get();
        PreferenceFragmentCompat root = (PreferenceFragmentCompat) list(activity);
        tap(activity, root, "terminal");
        assertEquals(PAGES + "TerminalPreferencesFragment", detail(activity).getClass().getName());
        assertEquals(1, activity.getSupportFragmentManager().getBackStackEntryCount());

        tap(activity, (PreferenceFragmentCompat) detail(activity), "terminal_sub_text");
        assertEquals(PAGES + "TerminalTextPreferencesFragment", detail(activity).getClass().getName());
        assertEquals("deeper pages stack in the detail", 2,
            activity.getSupportFragmentManager().getBackStackEntryCount());

        activity.onBackPressed();
        idle(activity);
        assertEquals(PAGES + "TerminalPreferencesFragment", detail(activity).getClass().getName());
        assertFalse(activity.isFinishing());
        activity.onBackPressed();
        assertTrue("Back from a section's page leaves Settings", activity.isFinishing());
    }

    @Test
    public void crossingSixHundredDpKeepsThePlace() {
        ActivityController<SettingsActivity> controller = open();
        SettingsActivity activity = controller.get();
        tap(activity, (PreferenceFragmentCompat) list(activity), "terminal");
        tap(activity, (PreferenceFragmentCompat) detail(activity), "terminal_sub_text");

        RuntimeEnvironment.setQualifiers("sw400dp-w400dp-h800dp");
        controller.recreate();
        activity = controller.get();
        idle(activity);
        assertFalse(activity.isTwoPane());
        assertNull(list(activity));
        assertEquals(PAGES + "TerminalTextPreferencesFragment", detail(activity).getClass().getName());
        FragmentManager fm = activity.getSupportFragmentManager();
        assertEquals(2, fm.getBackStackEntryCount());
        fm.popBackStackImmediate();
        fm.popBackStackImmediate();
        assertTrue("the phone shape has the root under the stack",
            detail(activity) instanceof SettingsActivity.RootPreferencesFragment);

        RuntimeEnvironment.setQualifiers("sw700dp-w900dp-h700dp");
        controller.recreate();
        activity = controller.get();
        idle(activity);
        assertTrue(activity.isTwoPane());
        assertTrue(list(activity) instanceof SettingsActivity.RootPreferencesFragment);
        assertEquals("back at the root on the phone: Home beside the list",
            PAGES + "HomePreferencesFragment", detail(activity).getClass().getName());
    }

    @Test
    @Config(qualifiers = "sw400dp-w860dp-h400dp-land")
    public void aPhoneInLandscapeStaysOnePane() {
        SettingsActivity activity = open().get();
        assertFalse(activity.isTwoPane());
        assertNull(list(activity));
        assertTrue(detail(activity) instanceof SettingsActivity.RootPreferencesFragment);
    }

    @Test
    @Config(qualifiers = "sw400dp-w400dp-h800dp")
    public void aPhoneHasOnePane() {
        SettingsActivity activity = open().get();
        assertFalse(activity.isTwoPane());
        assertNull(list(activity));
        assertTrue(detail(activity) instanceof SettingsActivity.RootPreferencesFragment);
    }
}
