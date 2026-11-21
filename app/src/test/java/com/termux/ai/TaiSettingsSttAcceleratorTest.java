package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import android.app.Application;
import org.robolectric.annotation.Config;

/**
 * Where speech-to-text runs is remembered per speech model: the model's own pick wins, a model
 * with none follows the single value older builds stored, and with neither it is auto.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TaiSettingsSttAcceleratorTest {
    private SharedPreferences preferences;
    private TaiSettings settings;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        settings = new TaiSettings(context);
    }

    @Test
    public void resolve_prefersTheModelThenTheGlobalThenAuto() {
        assertEquals("gpu", TaiSettings.resolveSttAccelerator("gpu", "cpu"));
        assertEquals("cpu", TaiSettings.resolveSttAccelerator(null, "cpu"));
        assertEquals("auto", TaiSettings.resolveSttAccelerator(null, null));
        assertEquals("auto", TaiSettings.resolveSttAccelerator("auto", "gpu"));
    }

    @Test
    public void resolve_readsTheLegacyInterpreterAndUnknownValuesAsAuto() {
        assertEquals("auto", TaiSettings.resolveSttAccelerator("interp", "gpu"));
        assertEquals("auto", TaiSettings.resolveSttAccelerator(null, "interp"));
        assertEquals("auto", TaiSettings.resolveSttAccelerator("interpreter", null));
        assertEquals("auto", TaiSettings.resolveSttAccelerator("turbo", "gpu"));
    }

    @Test
    public void aPickIsKeptPerModel() {
        settings.setSttAccelerator("whisper-a", "gpu");
        settings.setSttAccelerator("whisper-b", "cpu");

        assertEquals("gpu", settings.getSttAccelerator("whisper-a"));
        assertEquals("cpu", settings.getSttAccelerator("whisper-b"));
        assertEquals("auto", settings.getSttAccelerator("parakeet"));
    }

    @Test
    public void aModelWithNoPickFollowsTheLegacyGlobalWithoutWritingIt() {
        preferences.edit().putString(TaiSettings.KEY_STT_ACCELERATOR, "gpu").commit();

        assertEquals("gpu", settings.getSttAccelerator("whisper-a"));
        assertFalse(preferences.contains(TaiSettings.sttAcceleratorKey("whisper-a")));

        settings.setSttAccelerator("whisper-a", "cpu");
        assertEquals("cpu", settings.getSttAccelerator("whisper-a"));
        assertEquals("gpu", settings.getSttAccelerator("whisper-b"));
    }

    @Test
    public void aStoredInterpreterReadsAsAutoAndIsNeverWritten() {
        preferences.edit().putString(TaiSettings.KEY_STT_ACCELERATOR, "interp").commit();
        assertEquals("auto", settings.getSttAccelerator("whisper-a"));

        settings.setSttAccelerator("whisper-a", "interp");
        assertEquals("auto", preferences.getString(TaiSettings.sttAcceleratorKey("whisper-a"), null));
    }

    @Test
    public void noModelMeansTheGlobalOrAuto() {
        assertEquals("auto", settings.getSttAccelerator(null));
        assertEquals("auto", settings.getSttAccelerator(""));
        settings.setSttAccelerator("", "gpu");
        assertEquals("auto", settings.getSttAccelerator(""));
    }
}
