package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * The Speech model screen lists what {@link com.termux.ai.TaiSpeechModels} says is installed —
 * by capability, under plain names — and marks the one in use even when the stored id is stale.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SpeechModelPreferencesFragmentTest {
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        store = new TaiModelStore(context);
        store.deleteUserModel("whisper-acft-base-en");
        store.deleteUserModel("whisper-acft-small");
    }

    private SpeechModelPreferencesFragment launch() {
        Intent intent = new Intent(context, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, SpeechModelPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof SpeechModelPreferencesFragment);
        return (SpeechModelPreferencesFragment) fragment;
    }

    @Test
    public void withNothingInstalledTheScreenSaysSoAndOffersADownload() {
        SpeechModelPreferencesFragment fragment = launch();

        Preference empty = fragment.getPreferenceScreen().findPreference("speech_model_empty");
        assertNotNull(empty);
        assertTrue(empty.isVisible());
        assertNotNull(fragment.getPreferenceScreen().findPreference("speech_model_download"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("speech_model_idle_unload"));
        assertNull(fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-base-en"));
    }

    @Test
    public void installedModelsAreRowsUnderPlainNamesAndTheStaleChoiceFallsBackToOne() throws Exception {
        install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        install("whisper-acft-small", "acft_whisper_small_5s_drq.tflite");
        // Points at a model that is not there any more.
        new TaiSettings(context).setSttModelId("whisper-acft-small-en");

        SpeechModelPreferencesFragment fragment = launch();

        Preference empty = fragment.getPreferenceScreen().findPreference("speech_model_empty");
        assertNotNull(empty);
        assertFalse(empty.isVisible());
        Preference base = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-base-en");
        Preference small = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-small");
        assertNotNull(base);
        assertNotNull(small);
        assertEquals("Base · English", base.getTitle().toString());
        assertEquals("Small · Multilingual", small.getTitle().toString());
        assertTrue(base.getSummary().toString().contains("10-second window"));
        assertTrue(small.getSummary().toString().contains("5-second window"));
        // The stale id is not written over; the first installed model simply stands in.
        assertEquals("whisper-acft-small-en", new TaiSettings(context).getSttModelId());
    }

    @Test
    public void aDownloadInProgressIsARowOfItsOwn() throws Exception {
        store.upsertDownload(new JSONObject()
            .put("id", "download-whisper-acft-small")
            .put("modelId", "whisper-acft-small")
            .put("url", "https://example.invalid/acft_whisper_small_10s_drq.tflite")
            .put("path", new File(store.getModelsDirectory(), "whisper-acft-small/acft_whisper_small_10s_drq.tflite").getAbsolutePath())
            .put("status", TaiModelStore.STATE_DOWNLOADING)
            .put("bytesRead", 50L)
            .put("totalBytes", 100L)
            .put("error", "")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)));

        SpeechModelPreferencesFragment fragment = launch();

        Preference row = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-small");
        assertNotNull(row);
        assertEquals("Small · Multilingual", row.getTitle().toString());
        assertTrue(row.getSummary().toString().startsWith("Downloading"));
        Preference empty = fragment.getPreferenceScreen().findPreference("speech_model_empty");
        assertNotNull(empty);
        assertFalse(empty.isVisible());
    }

    @Test
    public void theWhisperCatalogIdFollowsSizeAndLanguage() {
        assertEquals("whisper-acft-base-en", SpeechModelPreferencesFragment.whisperCatalogId(false, true));
        assertEquals("whisper-acft-small", SpeechModelPreferencesFragment.whisperCatalogId(true, false));
    }

    private void install(String id, String fileName) throws Exception {
        File dir = new File(store.getModelsDirectory(), id);
        Files.createDirectories(dir.toPath());
        File file = new File(dir, fileName);
        Files.write(file.toPath(), "not-a-real-whisper-graph".getBytes(StandardCharsets.UTF_8));
        store.upsertUserModel(new TaiModelSpec(
            id, id, "Speech-to-text test model", "test", file.getAbsolutePath(), "test", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            "whisper-acft", "int8_drq", 128, 0, null));
    }
}
