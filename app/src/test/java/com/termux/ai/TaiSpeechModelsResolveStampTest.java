package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * The cached voice-typing answer is reused only while nothing it was derived from has changed:
 * the AI preferences, the models directory and each installed model's file.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiSpeechModelsResolveStampTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void nothingChangedIsTheSameStamp() {
        assertEquals(TaiSpeechModels.ResolveStamp.of(context), TaiSpeechModels.ResolveStamp.of(context));
    }

    @Test
    public void aSettingsWriteChangesTheStampAtOnce() {
        TaiSpeechModels.ResolveStamp before = TaiSpeechModels.ResolveStamp.of(context);
        // apply(), not commit(): the in-memory value is what the stamp reads.
        new TaiSettings(context).setSttModelId("whisper-acft-base-en");
        assertNotEquals(before, TaiSpeechModels.ResolveStamp.of(context));
    }

    @Test
    public void aModelStoreWriteChangesTheStamp() {
        TaiSpeechModels.ResolveStamp before = TaiSpeechModels.ResolveStamp.of(context);
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("stamp_probe", "1").apply();
        assertNotEquals(before, TaiSpeechModels.ResolveStamp.of(context));
    }

    @Test
    public void aModelFolderAppearingChangesTheDirectoryStamps() throws IOException {
        File models = folder.newFolder("models");
        assertEquals(TaiSpeechModels.ResolveStamp.directoryStamps(models),
            TaiSpeechModels.ResolveStamp.directoryStamps(models));
        java.util.Map<String, Long> before = TaiSpeechModels.ResolveStamp.directoryStamps(models);
        File model = new File(models, "whisper");
        model.mkdirs();
        assertNotEquals(before, TaiSpeechModels.ResolveStamp.directoryStamps(models));
    }

    @Test
    public void aDeletedOrRewrittenModelFileChangesTheModelFiles() throws IOException {
        File file = folder.newFile("whisper.tflite");
        write(file, "graph");
        TaiModelSpec spec = speechModel(file);
        TaiSpeechModels.ModelFiles before = TaiSpeechModels.ModelFiles.of(Collections.singletonList(spec));
        assertEquals(before, TaiSpeechModels.ModelFiles.of(Collections.singletonList(spec)));

        write(file, "a longer graph");
        TaiSpeechModels.ModelFiles rewritten = TaiSpeechModels.ModelFiles.of(Collections.singletonList(spec));
        assertNotEquals(before, rewritten);

        file.delete();
        assertNotEquals(rewritten, TaiSpeechModels.ModelFiles.of(Collections.singletonList(spec)));
    }

    @Test
    public void withNothingInstalledTheAnswerIsNoneAndStaysNone() {
        assertNull(TaiSpeechModels.resolveActive(context));
        assertNull(TaiSpeechModels.resolveActive(context));
    }

    private static TaiModelSpec speechModel(File file) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        return new TaiModelSpec("whisper-acft-base-en", "Whisper", "speech", "imported",
            file.getAbsolutePath(), "apache-2.0", file.length(), caps);
    }

    private static void write(File file, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
