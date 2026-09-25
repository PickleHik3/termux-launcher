package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Speech-to-text settings: the model in use, the preferred window for downloads, idle-unload
 *  minutes, and the download that becomes the model in use once it succeeds. */
@RunWith(RobolectricTestRunner.class)
public class TaiSttSettingsTest {
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
    public void sttModelId_isEmptyByDefaultAndRoundTrips() {
        assertEquals("", settings.getSttModelId());
        settings.setSttModelId("whisper-acft-base-en");
        assertEquals("whisper-acft-base-en", settings.getSttModelId());
        settings.setSttModelId(null);
        assertEquals("", settings.getSttModelId());
    }

    @Test
    public void sttWindowSeconds_defaultsTo10AndOnlyAccepts5Or10() {
        assertEquals(TaiSettings.DEFAULT_STT_WINDOW_SECONDS, settings.getSttWindowSeconds());
        assertEquals(10, settings.getSttWindowSeconds());

        settings.setSttWindowSeconds(5);
        assertEquals(5, settings.getSttWindowSeconds());

        // The plan offers only 5s/10s (no 30s window — it hallucinates on short speech); any other
        // stored value must fall back to the 10s default rather than silently propagate.
        preferences.edit().putInt(TaiSettings.KEY_STT_WINDOW_SECONDS, 30).commit();
        assertEquals(10, settings.getSttWindowSeconds());
    }

    @Test
    public void sttPendingDownload_isEmptyByDefaultAndClearsOnEmpty() {
        assertEquals("", settings.getSttPendingDownloadJson());
        settings.setSttPendingDownloadJson("{\"modelId\":\"whisper-acft-base-en\",\"windowSeconds\":5}");
        assertEquals("{\"modelId\":\"whisper-acft-base-en\",\"windowSeconds\":5}", settings.getSttPendingDownloadJson());
        settings.setSttPendingDownloadJson("");
        assertEquals("", settings.getSttPendingDownloadJson());
        assertFalse(preferences.contains(TaiSettings.KEY_STT_PENDING_DOWNLOAD));
        settings.setSttPendingDownloadJson(null);
        assertEquals("", settings.getSttPendingDownloadJson());
    }

    @Test
    public void sttIdleUnloadMinutes_defaultsTo2AndNeverGoesNegative() {
        assertEquals(TaiSettings.DEFAULT_STT_IDLE_UNLOAD_MINUTES, settings.getSttIdleUnloadMinutes());
        assertEquals(2, settings.getSttIdleUnloadMinutes());

        settings.setSttIdleUnloadMinutes(5);
        assertEquals(5, settings.getSttIdleUnloadMinutes());

        settings.setSttIdleUnloadMinutes(-3);
        assertEquals(0, settings.getSttIdleUnloadMinutes());
    }
}
