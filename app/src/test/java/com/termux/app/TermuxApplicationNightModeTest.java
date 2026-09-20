package com.termux.app;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;

import com.termux.shared.theme.NightMode;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * A system day/night flip reaches the application even when {@code termux.properties} pins the app
 * to one mode — the activity pins its own night mode and is not recreated, so nothing else in the
 * app moves. The background palette export used to follow the system anyway, writing the opposite
 * palette into {@code ~/.termux/material-colors.*} and re-running every template in it, and the
 * activity's resume check then found an unchanged signature and left it there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxApplicationNightModeTest {

    @After
    public void restoreAppNightMode() {
        NightMode.setAppNightMode(NightMode.SYSTEM.getName());
    }

    @Test
    public void aPinnedNightModeOverridesTheSystemsFlip() {
        NightMode.setAppNightMode(NightMode.TRUE.getName());

        Configuration system = configuration(Configuration.UI_MODE_NIGHT_NO);
        Configuration effective = TermuxApplication.withPinnedNightMode(system);

        assertEquals(Configuration.UI_MODE_NIGHT_YES, nightMask(effective));
        // The configuration Android handed us is not ours to edit.
        assertEquals(Configuration.UI_MODE_NIGHT_NO, nightMask(system));
    }

    @Test
    public void aPinnedDayModeOverridesTheSystemsFlip() {
        NightMode.setAppNightMode(NightMode.FALSE.getName());

        Configuration effective =
            TermuxApplication.withPinnedNightMode(configuration(Configuration.UI_MODE_NIGHT_YES));

        assertEquals(Configuration.UI_MODE_NIGHT_NO, nightMask(effective));
    }

    /** The other bits of {@code uiMode} — desk, television, car — are not ours to touch. */
    @Test
    public void aPinnedModeKeepsTheRestOfTheUiMode() {
        NightMode.setAppNightMode(NightMode.TRUE.getName());

        Configuration system = configuration(Configuration.UI_MODE_NIGHT_NO);
        system.uiMode |= Configuration.UI_MODE_TYPE_DESK;

        Configuration effective = TermuxApplication.withPinnedNightMode(system);

        assertEquals(Configuration.UI_MODE_TYPE_DESK,
            effective.uiMode & Configuration.UI_MODE_TYPE_MASK);
        assertEquals(Configuration.UI_MODE_NIGHT_YES, nightMask(effective));
    }

    @Test
    public void followingTheSystemLeavesTheConfigurationAlone() {
        NightMode.setAppNightMode(NightMode.SYSTEM.getName());

        Configuration system = configuration(Configuration.UI_MODE_NIGHT_YES);

        assertSame(system, TermuxApplication.withPinnedNightMode(system));
    }

    private static Configuration configuration(int nightMask) {
        Configuration configuration = new Configuration();
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMask;
        return configuration;
    }

    private static int nightMask(Configuration configuration) {
        return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }
}
