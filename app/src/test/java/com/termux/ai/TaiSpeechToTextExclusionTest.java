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
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Voice input phase 1: a speech-to-text model is downloadable but must never behave like a chat
 * model — it's kept out of /v1/models chat listings and refused by the chat-load path (that's
 * WhisperSttRuntime's job, phase 2).
 */
@RunWith(RobolectricTestRunner.class)
public class TaiSpeechToTextExclusionTest {
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        store = new TaiModelStore(context);
    }

    @Test
    public void openAiModels_neverListsASpeechToTextModel() throws Exception {
        String id = "whisper-acft-base-en";
        store.upsertUserModel(speechModel(id, writeSpeechModelFile(id)));
        TaiManager manager = TaiManager.getInstance(context);

        JSONObject response = manager.openAiModels();

        JSONArray data = response.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            assertFalse("speech_to_text model must not be a chat listing", id.equals(data.getJSONObject(i).optString("id", "")));
        }
    }

    @Test
    public void loadModel_refusesASpeechToTextModel() throws Exception {
        String id = "whisper-acft-base-en";
        store.upsertUserModel(speechModel(id, writeSpeechModelFile(id)));
        TaiManager manager = TaiManager.getInstance(context);

        JSONObject result = manager.loadModel(new JSONObject().put("model", id).toString());

        assertFalse(result.getBoolean("ok"));
        assertEquals("speech_model_not_loadable", result.getString("error"));
    }

    @Test
    public void catalog_chatEntriesExcludeWhisperAndSpeechEntriesContainOnlyWhisper() {
        assertTrue(TaiModelCatalog.speechEntries().containsKey("whisper-acft-base-en"));
        assertFalse(TaiModelCatalog.chatEntries().containsKey("whisper-acft-base-en"));
    }

    /** Writes a minimal on-disk file so {@code isModelReadable}/{@code getInstalledUserModels}
     *  actually surface this model — a spec pointing at a nonexistent path would be filtered out
     *  before the exclusion logic under test ever runs. */
    private String writeSpeechModelFile(String id) throws Exception {
        File file = new File(new File(store.getModelsDirectory(), id), "model.tflite");
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), "not-a-real-whisper-graph".getBytes(StandardCharsets.UTF_8));
        return file.getAbsolutePath();
    }

    private static TaiModelSpec speechModel(String id, String localPath) {
        return new TaiModelSpec(
            id,
            id,
            "Speech-to-text test model",
            "test",
            localPath,
            "test",
            123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            "whisper-acft",
            "int8_drq",
            128,
            0,
            null
        );
    }
}
