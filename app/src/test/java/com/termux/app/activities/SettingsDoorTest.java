package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.termux.app.fragments.settings.BenignPreferencesFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Every door into Settings starts it in its own task and hands an open Settings the new Intent. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SettingsDoorTest {

    private static void assertDoorFlags(Intent intent) {
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @Test
    public void plainDoorTargetsSettingsWithTheTaskFlagsAndNoDeepLink() {
        Context context = RuntimeEnvironment.getApplication();
        Intent intent = SettingsDoor.plain(context);
        assertEquals(SettingsActivity.class.getName(), intent.getComponent().getClassName());
        assertDoorFlags(intent);
        assertNull(intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
    }

    @Test
    public void namedDoorCarriesTheFlagsAndTheNamedPage() {
        Context context = RuntimeEnvironment.getApplication();
        Intent intent = SettingsActivity.createFragmentIntent(context, BenignPreferencesFragment.class, 0);
        assertDoorFlags(intent);
        assertEquals(BenignPreferencesFragment.class.getName(),
            intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
        Intent withPlace = SettingsActivity.createFragmentIntent(context, BenignPreferencesFragment.class, 0, "p", "k");
        assertDoorFlags(withPlace);
    }

    @Test
    public void flagKeepsExistingFlagsAndReturnsTheSameIntent() {
        Intent intent = new Intent().addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        assertEquals(intent, SettingsDoor.flag(intent));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
        assertNotEquals(0, intent.getFlags() & SettingsDoor.FLAGS);
    }
}
