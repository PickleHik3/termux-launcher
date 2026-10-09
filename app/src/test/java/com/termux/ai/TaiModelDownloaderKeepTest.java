package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A download of a file that is already complete on disk keeps it instead of fetching it again.
 * Kept apart from {@link TaiModelDownloaderStateTest}, which the build excludes (it needs
 * com.sun.net.httpserver), so these network-free cases actually run.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiModelDownloaderKeepTest {
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        store = new TaiModelStore(context);
    }

    @After
    public void tearDown() {
        store.deleteUserModel("resume-skip-test");
    }

    @Test
    public void isFileAlreadyComplete_matchesLengthAndKnownSize() throws Exception {
        File file = new File(context.getCacheDir(), "size-check-test.bin");
        java.nio.file.Files.write(file.toPath(), new byte[128]);
        try {
            assertTrue(TaiModelDownloader.isFileAlreadyComplete(file, 128L));
            assertFalse(TaiModelDownloader.isFileAlreadyComplete(file, 127L));
            // An unknown expected size (0 or negative) never counts as complete: the caller keeps
            // today's behaviour of fetching when the source hasn't said how big the file should be.
            assertFalse(TaiModelDownloader.isFileAlreadyComplete(file, 0L));
            assertFalse(TaiModelDownloader.isFileAlreadyComplete(file, -1L));
            assertFalse(TaiModelDownloader.isFileAlreadyComplete(new File(context.getCacheDir(), "missing.bin"), 128L));
        } finally {
            file.delete();
        }
    }

    @Test
    public void runDownload_keepsAnAlreadyCompleteFileWithoutTouchingTheNetwork() throws Exception {
        // No server is started for this URL: if the download did anything but skip the fetch, the
        // connection would refuse instantly and the transfer would end up FAILED, not INSTALLED.
        byte[] model = modelBytes('k');
        File output = output("resume-skip-test", "model.litertlm");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        java.nio.file.Files.write(output.toPath(), model);

        List<String> states = new ArrayList<>();
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-resume-skip-test", "resume-skip-test",
            "https://127.0.0.1:1/model.litertlm", output, "resume-skip-test", "license",
            capabilities(), TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "", "",
            4096, 0, "", model.length, null, null, Collections.emptyList(),
            new TaiModelDownloader.Control(), transfer -> states.add(transfer.optString("status")));

        assertFalse(states.contains(TaiModelStore.STATE_DOWNLOADING));
        assertTrue(states.contains(TaiModelStore.STATE_VERIFYING));
        assertEquals(TaiModelStore.STATE_INSTALLED, states.get(states.size() - 1));
        assertArrayEquals(model, java.nio.file.Files.readAllBytes(output.toPath()));
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
    }

    private File output(String modelId, String name) {
        return new File(new File(store.getModelsDirectory(), modelId), name);
    }

    private static LinkedHashSet<String> capabilities() {
        return new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private static byte[] modelBytes(char fill) {
        byte[] bytes = new byte[1024 * 1024 + 16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) fill;
        bytes[0] = 'T';
        bytes[1] = 'A';
        bytes[2] = 'I';
        return bytes;
    }
}
