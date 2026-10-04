package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Optional services read as neutral when off; required access keeps its warning. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatusActionPreferenceTest {

    private static StatusActionPreference make() {
        return new StatusActionPreference(RuntimeEnvironment.getApplication(), null);
    }

    @Test
    public void requiredAccessKeepsItsTone() {
        StatusActionPreference p = make();
        p.setState("Needed", "Grant", StatusActionPreference.Tone.ERROR);
        assertFalse(p.isOptional());
        assertEquals(StatusActionPreference.Tone.ERROR, p.effectiveTone());
    }

    @Test
    public void optionalServiceOffIsNeutral() {
        StatusActionPreference p = make();
        p.setState("Not enabled", "Enable", StatusActionPreference.Tone.ERROR);
        p.setOptional(true);
        assertTrue(p.isOptional());
        assertEquals(StatusActionPreference.Tone.NEUTRAL, p.effectiveTone());
        p.setState("On", "Manage", StatusActionPreference.Tone.POSITIVE);
        assertEquals(StatusActionPreference.Tone.POSITIVE, p.effectiveTone());
    }
}
