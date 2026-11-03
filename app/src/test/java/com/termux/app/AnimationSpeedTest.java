package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.SettingsMotion;
import com.termux.app.fragments.settings.termux.GeneralPreferencesFragment;
import com.termux.app.terminal.Motion;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** F3: General › Animation speed, the one accessor every launcher motion reads. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AnimationSpeedTest {

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @After
    public void backToNormal() {
        AnimationSpeed.set(context(), AnimationSpeed.NORMAL);
    }

    @Test
    public void eachChoiceIsOneMultiplier() {
        assertEquals(1.5f, AnimationSpeed.multiplierFor("slower"), 0f);
        assertEquals(1f, AnimationSpeed.multiplierFor("normal"), 0f);
        assertEquals(0.67f, AnimationSpeed.multiplierFor("faster"), 0f);
        assertEquals(0f, AnimationSpeed.multiplierFor("off"), 0f);
        assertEquals("anything else is Normal", 1f, AnimationSpeed.multiplierFor("warp"), 0f);
        assertEquals(1f, AnimationSpeed.multiplierFor(null), 0f);
    }

    @Test
    public void durationsFollowTheChoiceAndOffIsInstant() {
        assertEquals(AnimationSpeed.NORMAL, AnimationSpeed.get(context()));
        assertEquals(320L, Motion.floatDepthMs(context()));
        assertEquals(300L, SettingsMotion.scaled(context(), 300L));
        assertFalse(SettingsMotion.isInstant(context()));

        AnimationSpeed.set(context(), AnimationSpeed.SLOWER);
        assertEquals(480L, Motion.floatDepthMs(context()));
        assertEquals(450L, SettingsMotion.scaled(context(), 300L));

        AnimationSpeed.set(context(), AnimationSpeed.FASTER);
        assertEquals(214L, Motion.floatDepthMs(context()));
        assertEquals(0.67f, AnimationSpeed.springTime(context()), 1e-6f);

        AnimationSpeed.set(context(), AnimationSpeed.OFF);
        assertEquals(0L, Motion.floatDepthMs(context()));
        assertTrue("settings cut instead of moving", SettingsMotion.isInstant(context()));
        assertTrue("a spring is very quick, never divided by zero", AnimationSpeed.springTime(context()) > 0f);
    }

    @Test
    public void theGeneralRowReadsAndWritesTheSameValue() {
        Intent intent = SettingsActivity.createFragmentIntent(context(), GeneralPreferencesFragment.class,
            R.string.settings_section_general);
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment shown = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        ListPreference row = ((GeneralPreferencesFragment) shown).findPreference(AnimationSpeed.KEY);
        assertEquals("the Motion group comes first", "general_motion", row.getParent().getKey());
        assertEquals(AnimationSpeed.NORMAL, row.getValue());
        row.setValue(AnimationSpeed.FASTER);
        assertEquals(AnimationSpeed.FASTER, AnimationSpeed.get(context()));
        assertEquals("Faster", String.valueOf(row.getSummary()));
    }
}
