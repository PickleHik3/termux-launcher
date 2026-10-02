package com.termux.app.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import com.termux.app.fragments.settings.BenignPreferencesFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Back on Settings' first screen leaves Settings; Back over a pushed screen pops it instead. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SettingsBackFinishesTest {

    @Test
    public void backOnADeepLinkedFirstScreenFinishes() {
        Intent intent = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, BenignPreferencesFragment.class.getName());
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();

            activity.onBackPressed();

            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void upOnTheRootScreenFinishes() {
        Intent intent = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();

            activity.onSupportNavigateUp();

            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void backOverAPushedScreenPopsItAndStays() {
        Intent intent = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().beginTransaction()
                .replace(com.termux.R.id.settings, new BenignPreferencesFragment())
                .addToBackStack(null)
                .commit();
            activity.getSupportFragmentManager().executePendingTransactions();

            activity.onBackPressed();
            activity.getSupportFragmentManager().executePendingTransactions();

            assertFalse(activity.isFinishing());
        }
    }
}
