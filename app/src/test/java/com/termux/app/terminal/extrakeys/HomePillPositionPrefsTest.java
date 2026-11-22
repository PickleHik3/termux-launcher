package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HomePillPositionPrefsTest {

    private TermuxAppSharedPreferences prefs() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences raw = app.getSharedPreferences("home-pill-position-test", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        return new TermuxAppSharedPreferences(app, raw, null);
    }

    @Test
    public void aPillThatWasNeverMovedHasNoPosition() {
        assertNull(prefs().getHomePillPosition());
    }

    @Test
    public void thePositionRoundTrips() {
        TermuxAppSharedPreferences prefs = prefs();
        prefs.setHomePillPosition(0.8f, 0.4f);
        assertArrayEquals(new float[]{0.8f, 0.4f}, prefs.getHomePillPosition(), 0.0001f);
    }

    @Test
    public void aPositionOutsideTheWindowIsKeptInRange() {
        TermuxAppSharedPreferences prefs = prefs();
        prefs.setHomePillPosition(-3f, 7f);
        assertArrayEquals(new float[]{0f, 1f}, prefs.getHomePillPosition(), 0.0001f);
    }
}
