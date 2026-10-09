package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The Icons mode applies a pack live under an open surface: the preference and the artwork
 * follow at once, and the activity's restyle is not broadcast (the surface owes it when it
 * closes). The other callers' {@link IconPackChoices#apply} still restyles.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class IconPackChoicesLiveApplyTest {
    private Context context;
    private TermuxAppSharedPreferences prefs;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
        prefs = TermuxAppSharedPreferences.build(context, false);
        assertNotNull(prefs);
        shadowOf((Application) context).clearBroadcastIntents();
    }

    private boolean restyleBroadcast() {
        for (Intent intent : shadowOf((Application) context).getBroadcastIntents()) {
            if (TERMUX_ACTIVITY.ACTION_RELOAD_STYLE.equals(intent.getAction())) return true;
        }
        return false;
    }

    @Test public void applyLiveWritesTheChoiceAndSendsNoRestyle() {
        IconPackChoices.applyLive(context, prefs, IconPackChoices.KEY_PINNED, "pack.beta");
        assertEquals("pack.beta", IconPackChoices.current(prefs, IconPackChoices.KEY_PINNED));
        assertFalse("the restyle waits for the surface to close", restyleBroadcast());
    }

    @Test public void applyLiveToTheGlobalKeyWritesThatKey() {
        IconPackChoices.applyLive(context, prefs, IconPackChoices.KEY_GLOBAL, "pack.alpha");
        assertEquals("pack.alpha", IconPackChoices.current(prefs, IconPackChoices.KEY_GLOBAL));
        assertFalse(restyleBroadcast());
    }

    @Test public void applyStillRestylesForTheOtherCallers() {
        IconPackChoices.apply(context, prefs, IconPackChoices.KEY_PINNED, "pack.beta");
        assertEquals("pack.beta", IconPackChoices.current(prefs, IconPackChoices.KEY_PINNED));
        assertTrue(restyleBroadcast());
    }
}
