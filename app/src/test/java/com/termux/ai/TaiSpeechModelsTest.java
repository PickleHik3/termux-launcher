package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The speech model picker's state: plain names, which installed model is in use (a stale id
 * counts as unset), a download becoming the model in use only once it succeeds, and a window
 * switch that downloads first and deletes the old graph after — or keeps it on failure.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiSpeechModelsTest {
    private Context context;
    private TaiModelStore store;
    private TaiSettings settings;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        store = new TaiModelStore(context);
        settings = new TaiSettings(context);
        for (String id : Arrays.asList("whisper-acft-base", "whisper-acft-base-en", "whisper-acft-small-en", "parakeet-tdt")) {
            store.deleteUserModel(id);
        }
    }

    // ---- naming ----

    @Test
    public void plainName_isSizeAndLanguageForWhisperAndCatalogNameOtherwise() {
        assertEquals("Base · English", TaiSpeechModels.plainName("whisper-acft-base-en", "Whisper ACFT Base (English)",
            "/m/whisper-acft-base-en/acft_whisper_base.en_10s_drq.tflite"));
        assertEquals("Small · Multilingual", TaiSpeechModels.plainName("whisper-acft-small", "Whisper ACFT Small",
            "/m/whisper-acft-small/acft_whisper_small_5s_drq.tflite"));
        // Another engine's model is just another speech_to_text entry: its own name, language kind appended.
        assertEquals("Parakeet TDT · Multilingual", TaiSpeechModels.plainName("parakeet-tdt", "Parakeet TDT", null));
        assertEquals("parakeet-tdt · Multilingual", TaiSpeechModels.plainName("parakeet-tdt", "", null));
    }

    @Test
    public void windowSeconds_isReadOffTheFileNameAndZeroWhenItDoesNotSay() {
        assertEquals(10, TaiSpeechModels.windowSeconds("/m/whisper-acft-base-en/acft_whisper_base.en_10s_drq.tflite"));
        assertEquals(5, TaiSpeechModels.windowSeconds("/m/whisper-acft-small/acft_whisper_small_5s_drq.tflite"));
        assertEquals(0, TaiSpeechModels.windowSeconds("/m/parakeet-tdt/model.tflite"));
        assertEquals(0, TaiSpeechModels.windowSeconds(null));
    }

    @Test
    public void isEnglishOnly_followsTheIdSuffixOrTheFileName() {
        assertTrue(TaiSpeechModels.isEnglishOnly("whisper-acft-base-en", null));
        assertTrue(TaiSpeechModels.isEnglishOnly("custom-import", "/m/x/acft_whisper_base.en_10s_drq.tflite"));
        assertFalse(TaiSpeechModels.isEnglishOnly("whisper-acft-base", "/m/x/acft_whisper_base_10s_drq.tflite"));
    }

    // ---- which model is in use ----

    @Test
    public void chooseActive_takesTheStoredIdWhenInstalledElseTheFirstInstalledElseNothing() {
        TaiModelSpec base = speechSpec("whisper-acft-base", "/m/base/acft_whisper_base_10s_drq.tflite");
        TaiModelSpec small = speechSpec("whisper-acft-small-en", "/m/small/acft_whisper_small.en_10s_drq.tflite");
        List<TaiModelSpec> installed = Arrays.asList(base, small);

        assertEquals(small, TaiSpeechModels.chooseActive("whisper-acft-small-en", installed));
        // A stored id whose files are gone is unset: another installed model stands in.
        assertEquals(base, TaiSpeechModels.chooseActive("whisper-acft-base-en", installed));
        assertEquals(base, TaiSpeechModels.chooseActive("", installed));
        assertEquals(base, TaiSpeechModels.chooseActive(null, installed));
        assertNull(TaiSpeechModels.chooseActive("whisper-acft-base-en", Collections.<TaiModelSpec>emptyList()));
    }

    @Test
    public void resolveActive_listsByCapabilityAndIgnoresAStaleStoredId() throws Exception {
        install("whisper-acft-base", "acft_whisper_base_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-small-en");

        TaiModelSpec active = TaiSpeechModels.resolveActive(settings, store);

        assertNotNull(active);
        assertEquals("whisper-acft-base", active.id);
        assertEquals("whisper-acft-base", TaiSpeechModels.activeModelId(settings, store));
        // The choice itself is left alone — a model that is briefly unreadable keeps it.
        assertEquals("whisper-acft-small-en", settings.getSttModelId());
        assertEquals(1, TaiSpeechModels.installed(store).size());
    }

    // ---- a download becomes the model in use only when it succeeds ----

    @Test
    public void startDownload_recordsThePendingChoiceWithoutChangingTheModelInUse() throws Exception {
        install("whisper-acft-base", "acft_whisper_base_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-base");

        JSONObject result = TaiSpeechModels.startDownload(context, "whisper-acft-small-en", 5);

        assertTrue(result.optBoolean("ok", false));
        assertEquals("whisper-acft-base", settings.getSttModelId());
        TaiSpeechModels.PendingDownload pending = TaiSpeechModels.PendingDownload.fromJson(settings.getSttPendingDownloadJson());
        assertNotNull(pending);
        assertEquals("whisper-acft-small-en", pending.modelId);
        assertEquals(5, pending.windowSeconds);
        assertFalse(pending.isWindowSwitch());
        assertEquals(TaiSpeechModels.Outcome.IN_PROGRESS, TaiSpeechModels.settlePending(settings, store).outcome);
        assertEquals("whisper-acft-base", settings.getSttModelId());
    }

    @Test
    public void settlePending_activatesAFreshDownloadOnceInstalled() throws Exception {
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-base-en", 5, null).toJson().toString());
        recordDownload("whisper-acft-base-en", TaiModelStore.STATE_DOWNLOADING, "/nowhere/acft_whisper_base.en_5s_drq.tflite", "");
        assertEquals(TaiSpeechModels.Outcome.IN_PROGRESS, TaiSpeechModels.settlePending(settings, store).outcome);
        assertEquals("", settings.getSttModelId());

        String path = install("whisper-acft-base-en", "acft_whisper_base.en_5s_drq.tflite");
        recordDownload("whisper-acft-base-en", TaiModelStore.STATE_INSTALLED, path, "");

        assertEquals(TaiSpeechModels.Outcome.ACTIVATED, TaiSpeechModels.settlePending(settings, store).outcome);
        assertEquals("whisper-acft-base-en", settings.getSttModelId());
        assertEquals(5, settings.getSttWindowSeconds());
        assertEquals("", settings.getSttPendingDownloadJson());
        // Settling again changes nothing.
        assertEquals(TaiSpeechModels.Outcome.NONE, TaiSpeechModels.settlePending(settings, store).outcome);
    }

    @Test
    public void settlePending_doesNotOverrideAModelTheUserPickedMeanwhile() throws Exception {
        String basePath = install("whisper-acft-base", "acft_whisper_base_10s_drq.tflite");
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-small-en", 10, null).toJson().toString());
        recordDownload("whisper-acft-small-en", TaiModelStore.STATE_DOWNLOADING, "/nowhere/small.tflite", "");

        // The user taps Base while Small is still downloading.
        TaiSpeechModels.activate(settings, "whisper-acft-base");
        assertEquals("", settings.getSttPendingDownloadJson());

        String smallPath = install("whisper-acft-small-en", "acft_whisper_small.en_10s_drq.tflite");
        recordDownload("whisper-acft-small-en", TaiModelStore.STATE_INSTALLED, smallPath, "");

        assertEquals(TaiSpeechModels.Outcome.NONE, TaiSpeechModels.settlePending(settings, store).outcome);
        assertEquals("whisper-acft-base", settings.getSttModelId());
        assertTrue(new File(basePath).isFile());
        assertEquals(2, TaiSpeechModels.installed(store).size());
    }

    @Test
    public void settlePending_leavesTheChoiceAloneWhenAFreshDownloadFails() throws Exception {
        install("whisper-acft-base", "acft_whisper_base_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-base");
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-small-en", 10, null).toJson().toString());
        recordDownload("whisper-acft-small-en", TaiModelStore.STATE_FAILED, "/nowhere/small.tflite", "HTTP 503");

        TaiSpeechModels.Settlement settlement = TaiSpeechModels.settlePending(settings, store);

        assertEquals(TaiSpeechModels.Outcome.FAILED, settlement.outcome);
        assertEquals("HTTP 503", settlement.error);
        assertEquals("whisper-acft-base", settings.getSttModelId());
        assertEquals("", settings.getSttPendingDownloadJson());
        // The failed record stays so the screen can offer a retry.
        assertNotNull(TaiSpeechModels.findDownload(store, "whisper-acft-small-en"));
    }

    @Test
    public void settlePending_forgetsAPendingDownloadWhoseModelWasDeleted() throws Exception {
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-small-en", 10, null).toJson().toString());

        assertEquals(TaiSpeechModels.Outcome.NONE, TaiSpeechModels.settlePending(settings, store).outcome);
        assertEquals("", settings.getSttPendingDownloadJson());
    }

    // ---- window switch: download first, delete after ----

    @Test
    public void startWindowSwitch_keepsTheInstalledGraphUntilTheNewOneArrives() throws Exception {
        String oldPath = install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-base-en");
        TaiModelSpec installed = TaiSpeechModels.resolveActive(settings, store);
        assertNotNull(installed);

        JSONObject result = TaiSpeechModels.startWindowSwitch(context, installed, 5);

        assertTrue(result.optBoolean("ok", false));
        assertTrue("the old graph is still there", new File(oldPath).isFile());
        JSONObject download = TaiSpeechModels.findDownload(store, "whisper-acft-base-en");
        assertNotNull(download);
        assertEquals(TaiModelStore.STATE_QUEUED, download.optString("status"));
        assertTrue(download.optString("path").endsWith("acft_whisper_base.en_5s_drq.tflite"));
        // Still installed, still in use, still the 10s graph while the 5s one downloads.
        TaiModelSpec active = TaiSpeechModels.resolveActive(settings, store);
        assertNotNull(active);
        assertEquals(oldPath, active.localPath);
        assertEquals(10, TaiSpeechModels.windowSeconds(active));
        TaiSpeechModels.PendingDownload pending = TaiSpeechModels.PendingDownload.fromJson(settings.getSttPendingDownloadJson());
        assertNotNull(pending);
        assertTrue(pending.isWindowSwitch());
        assertEquals(oldPath, pending.previousPath);
        // Picking the same model again (or another) does not forget a window switch: its old file
        // still has to be cleaned up when the new one lands.
        TaiSpeechModels.activate(settings, "whisper-acft-base-en");
        assertNotNull(TaiSpeechModels.PendingDownload.fromJson(settings.getSttPendingDownloadJson()));
    }

    @Test
    public void settlePending_deletesTheOldGraphOnlyAfterTheNewOneIsInstalled() throws Exception {
        String oldPath = install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-base-en");
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-base-en", 5, oldPath).toJson().toString());
        recordDownload("whisper-acft-base-en", TaiModelStore.STATE_DOWNLOADING, oldPath.replace("_10s_", "_5s_"), "");

        assertEquals(TaiSpeechModels.Outcome.IN_PROGRESS, TaiSpeechModels.settlePending(settings, store).outcome);
        assertTrue(new File(oldPath).isFile());

        // The downloader registers the new file and marks the transfer installed.
        String newPath = install("whisper-acft-base-en", "acft_whisper_base.en_5s_drq.tflite");
        recordDownload("whisper-acft-base-en", TaiModelStore.STATE_INSTALLED, newPath, "");

        assertEquals(TaiSpeechModels.Outcome.WINDOW_CHANGED, TaiSpeechModels.settlePending(settings, store).outcome);
        assertFalse("the old graph is gone", new File(oldPath).exists());
        assertTrue(new File(newPath).isFile());
        assertTrue("the shared tokenizer sidecar is untouched", new File(new File(newPath).getParentFile(), "tokenizer.json").isFile());
        TaiModelSpec active = TaiSpeechModels.resolveActive(settings, store);
        assertNotNull(active);
        assertEquals(5, TaiSpeechModels.windowSeconds(active));
        assertEquals(5, settings.getSttWindowSeconds());
        assertEquals("", settings.getSttPendingDownloadJson());
    }

    @Test
    public void settlePending_keepsTheOldGraphWhenTheWindowDownloadFails() throws Exception {
        String oldPath = install("whisper-acft-base-en", "acft_whisper_base.en_10s_drq.tflite");
        settings.setSttModelId("whisper-acft-base-en");
        String newPath = oldPath.replace("_10s_", "_5s_");
        Files.write(new File(newPath + ".part").toPath(), "half".getBytes(StandardCharsets.UTF_8));
        settings.setSttPendingDownloadJson(new TaiSpeechModels.PendingDownload("whisper-acft-base-en", 5, oldPath).toJson().toString());
        recordDownload("whisper-acft-base-en", TaiModelStore.STATE_CANCELLED, newPath, "cancelled");

        TaiSpeechModels.Settlement settlement = TaiSpeechModels.settlePending(settings, store);

        assertEquals(TaiSpeechModels.Outcome.WINDOW_KEPT, settlement.outcome);
        assertTrue("the old graph stays", new File(oldPath).isFile());
        assertFalse("the partial of the new one is cleaned up", new File(newPath + ".part").exists());
        // The failed record is dropped, so the model reads as plainly installed again.
        assertNull(TaiSpeechModels.findDownload(store, "whisper-acft-base-en"));
        TaiModelSpec active = TaiSpeechModels.resolveActive(settings, store);
        assertNotNull(active);
        assertEquals(oldPath, active.localPath);
        assertEquals(10, TaiSpeechModels.windowSeconds(active));
        assertEquals("", settings.getSttPendingDownloadJson());
    }

    // ---- helpers ----

    /** Registers a readable speech model under {@code tai/models/<id>/<fileName>} with a tokenizer sidecar. */
    private String install(String id, String fileName) throws Exception {
        File dir = new File(store.getModelsDirectory(), id);
        Files.createDirectories(dir.toPath());
        File file = new File(dir, fileName);
        Files.write(file.toPath(), "not-a-real-whisper-graph".getBytes(StandardCharsets.UTF_8));
        File tokenizer = new File(dir, "tokenizer.json");
        if (!tokenizer.isFile()) Files.write(tokenizer.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        store.upsertUserModel(speechSpec(id, file.getAbsolutePath()));
        return file.getAbsolutePath();
    }

    private void recordDownload(String modelId, String status, String path, String error) throws Exception {
        JSONObject transfer = new JSONObject()
            .put("id", "download-" + modelId)
            .put("modelId", modelId)
            .put("url", "https://example.invalid/" + new File(path).getName())
            .put("path", path)
            .put("status", status)
            .put("bytesRead", 0L)
            .put("totalBytes", 0L)
            .put("error", error)
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        store.upsertDownload(transfer);
    }

    private static TaiModelSpec speechSpec(String id, String localPath) {
        return new TaiModelSpec(
            id, id, "Speech-to-text test model", "test", localPath, "test", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            "whisper-acft", "int8_drq", 128, 0, null);
    }
}
