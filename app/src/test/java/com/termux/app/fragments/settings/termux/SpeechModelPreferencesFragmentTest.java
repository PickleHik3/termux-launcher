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
import com.termux.ai.TaiModelCatalog;
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
 * The Speech model picker lists what {@link com.termux.ai.TaiSpeechModels} says is installed —
 * by capability, under plain names — as cards, marks the one in use even when the stored id is
 * stale, and leaves downloading to the Model centre.
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
        context.getSharedPreferences("termux_ai_model_store", Context.MODE_PRIVATE).edit().clear().commit();
        store = new TaiModelStore(context);
        store.deleteUserModel("whisper-acft-base-en");
        store.deleteUserModel("whisper-acft-small");
        store.deleteUserModel(TaiModelCatalog.PARAKEET_TDT_V3_ID);
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
    public void withNothingInstalledTheScreenSaysSoAndPointsToTheModelCentre() {
        SpeechModelPreferencesFragment fragment = launch();

        Preference empty = fragment.getPreferenceScreen().findPreference("speech_model_empty");
        assertNotNull(empty);
        assertTrue(empty.isVisible());
        assertTrue(empty.getSummary().toString().contains("Model centre"));
        // No download UI here any more (D5): the only way to get a model is the centre.
        assertNull(fragment.getPreferenceScreen().findPreference("speech_model_download"));
        assertNotNull(fragment.getPreferenceScreen().findPreference(SpeechModelPreferencesFragment.KEY_GET_MORE));
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
        assertEquals("Small · Many languages", small.getTitle().toString());
        // The summary names the engine, then size and window.
        assertTrue(base.getSummary().toString().startsWith("Whisper · "));
        assertTrue(base.getSummary().toString().contains("10-second window"));
        assertTrue(small.getSummary().toString().startsWith("Whisper · "));
        assertTrue(small.getSummary().toString().contains("5-second window"));
        // The stale id is not written over; the first installed model simply stands in.
        assertEquals("whisper-acft-small-en", new TaiSettings(context).getSttModelId());
        assertTrue(((SpeechModelCardPreference) base).isChosen());
        assertFalse(((SpeechModelCardPreference) small).isChosen());
        // Both Whisper graphs come in a 5 and a 10 second window, so each card offers the other.
        assertEquals("Change window", ((SpeechModelCardPreference) base).getWindowAction().toString());
    }

    @Test
    public void tappingACardMakesItTheOneInUse() throws Exception {
        install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        install("whisper-acft-small", "acft_whisper_small_5s_drq.tflite");
        new TaiSettings(context).setSttModelId("whisper-acft-base-en");

        SpeechModelPreferencesFragment fragment = launch();
        SpeechModelCardPreference small = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-small");
        assertNotNull(small);
        assertFalse(small.isChosen());

        small.getOnPreferenceClickListener().onPreferenceClick(small);

        assertEquals("whisper-acft-small", new TaiSettings(context).getSttModelId());
        assertTrue(small.isChosen());
        SpeechModelCardPreference base = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-base-en");
        assertNotNull(base);
        assertFalse(base.isChosen());
    }

    @Test
    public void aDownloadInProgressIsNotACardHereItBelongsToTheModelCentre() throws Exception {
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

        assertNull(fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-small"));
        Preference empty = fragment.getPreferenceScreen().findPreference("speech_model_empty");
        assertNotNull(empty);
        assertTrue(empty.isVisible());
    }

    @Test
    public void aWindowSwitchOnItsWaySaysSoOnTheInstalledCard() throws Exception {
        install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        store.upsertDownload(new JSONObject()
            .put("id", "download-whisper-acft-base-en")
            .put("modelId", "whisper-acft-base-en")
            .put("url", "https://example.invalid/acft_whisper_base.en_5s_drq.tflite")
            .put("path", new File(store.getModelsDirectory(), "whisper-acft-base-en/acft_whisper_base.en_5s_drq.tflite").getAbsolutePath())
            .put("status", TaiModelStore.STATE_DOWNLOADING)
            .put("bytesRead", 50L)
            .put("totalBytes", 100L)
            .put("error", "")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)));

        SpeechModelPreferencesFragment fragment = launch();

        SpeechModelCardPreference card = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + "whisper-acft-base-en");
        assertNotNull(card);
        // The old window stays in use while the new one arrives, and there is nothing to change meanwhile.
        assertTrue(card.getSummary().toString().contains("10-second window"));
        assertEquals("", card.getWindowAction().toString());
    }

    @Test
    public void aParakeetModelIsARowUnderTheEngineNameWithNoWindowToChange() throws Exception {
        install(TaiModelCatalog.PARAKEET_TDT_V3_ID, "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite", "parakeet-tdt");
        new TaiSettings(context).setSttModelId(TaiModelCatalog.PARAKEET_TDT_V3_ID);

        SpeechModelPreferencesFragment fragment = launch();

        Preference row = fragment.getPreferenceScreen().findPreference(
            SpeechModelPreferencesFragment.ROW_KEY_PREFIX + TaiModelCatalog.PARAKEET_TDT_V3_ID);
        assertNotNull(row);
        assertEquals("Parakeet · Many languages", row.getTitle().toString());
        assertTrue(row.getSummary().toString().startsWith("Parakeet · "));
        assertTrue(row.getSummary().toString().contains("5-second window"));
        assertEquals(TaiModelCatalog.PARAKEET_TDT_V3_ID, new TaiSettings(context).getSttModelId());
    }

    @Test
    public void theWhisperCatalogIdFollowsSizeAndLanguageAndParakeetHasOne() {
        assertEquals("whisper-acft-base-en", TaiSpeechActions.whisperCatalogId(false, true));
        assertEquals("whisper-acft-small", TaiSpeechActions.whisperCatalogId(true, false));
        assertEquals("parakeet-tdt-0.6b-v3", TaiModelCatalog.PARAKEET_TDT_V3_ID);
        assertEquals(5, TaiSpeechActions.PARAKEET_WINDOW_SECONDS);
    }

    @Test
    public void theParakeetRamWarningShowsOnlyBelowTheEntryTier() {
        long gib = 1024L * 1024 * 1024;
        assertNull(TaiSpeechActions.parakeetRamWarning(context, 12L * gib));
        assertNull(TaiSpeechActions.parakeetRamWarning(context, 8L * gib));
        assertNull("unknown memory: no warning", TaiSpeechActions.parakeetRamWarning(context, 0L));
        String warning = TaiSpeechActions.parakeetRamWarning(context, 6L * gib);
        assertNotNull(warning);
        assertTrue(warning, warning.contains("6.0 GB"));
    }

    private void install(String id, String fileName) throws Exception {
        install(id, fileName, "whisper-acft");
    }

    private void install(String id, String fileName, String architecture) throws Exception {
        File dir = new File(store.getModelsDirectory(), id);
        Files.createDirectories(dir.toPath());
        File file = new File(dir, fileName);
        Files.write(file.toPath(), "not-a-real-speech-graph".getBytes(StandardCharsets.UTF_8));
        store.upsertUserModel(new TaiModelSpec(
            id, id, "Speech-to-text test model", "test", file.getAbsolutePath(), "test", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            architecture, "int8_drq", 128, 0, null));
    }
}
