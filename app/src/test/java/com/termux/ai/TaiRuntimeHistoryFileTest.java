package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The history lives in its own locked file, so a stale copy in one process cannot resurrect cleared entries. */
@RunWith(RobolectricTestRunner.class)
public class TaiRuntimeHistoryFileTest {

    private Context context;
    private TaiDeviceCapabilities device;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        File dir = new File(context.getFilesDir(), "tai");
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        device = TaiDeviceCapabilities.createForTest("Pixel 9", "Google", "tensor", 34,
            Arrays.asList("arm64-v8a"), 8L * 1024L * 1024L * 1024L, "totalMem", false);
    }

    private static TaiModelSpec model(String id) {
        return new TaiModelSpec(id, id, "test", "test", "/none", "test", 1L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)), false, null,
            TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, "qwen2.5", "int4", 4096, 0, null);
    }

    private File historyFile() {
        return new File(new File(context.getFilesDir(), "tai"), "runtime-history.json");
    }

    private int entriesOnDisk() throws Exception {
        return new JSONObject(new String(Files.readAllBytes(historyFile().toPath()), StandardCharsets.UTF_8)).length();
    }

    @Test
    public void recordClearRecordLeavesOnlyTheNewEntry() throws Exception {
        TaiRuntimeHistory.recordFailure(context, model("a"), device, "mnn_llm", "gpu", "boom");
        TaiRuntimeHistory.recordSuccess(context, model("b"), device, "mnn_llm", "cpu");
        assertEquals(2, TaiRuntimeHistory.clear(context));
        TaiRuntimeHistory.recordSuccess(context, model("c"), device, "mnn_llm", "cpu");
        assertEquals(1, entriesOnDisk());
        assertEquals(1, TaiRuntimeHistory.summary(context).getInt("count"));
    }

    @Test
    public void aStaleReadCannotResurrectClearedEntries() throws Exception {
        TaiRuntimeHistory.recordFailure(context, model("a"), device, "mnn_llm", "gpu", "boom");
        JSONObject stale = TaiRuntimeHistory.summary(context).getJSONObject("entries");
        assertEquals(1, stale.length());
        TaiRuntimeHistory.clear(context);
        TaiRuntimeHistory.recordSuccess(context, model("b"), device, "mnn_llm", "cpu");
        assertEquals(1, entriesOnDisk());
        assertNull(TaiRuntimeHistory.failedEntry(context, model("a"), device, "gpu"));
    }

    @Test
    public void concurrentRecordsFromTwoThreadsAllLand() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[2];
        for (int t = 0; t < threads.length; t++) {
            final int id = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 25; i++) {
                        TaiRuntimeHistory.recordSuccess(context, model("m" + id + "-" + i), device, "mnn_llm", "cpu");
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            threads[t].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertNull(failure.get());
        assertEquals(50, entriesOnDisk());
    }

    @Test
    public void legacyPreferencesValueIsMigratedIntoTheFile() throws Exception {
        String legacy = new JSONObject().put("k", new JSONObject().put("modelId", "m").put("updatedAtMs", 1L)).toString();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("tai_runtime_history_json", legacy).commit();
        assertFalse(historyFile().exists());
        assertEquals(1, TaiRuntimeHistory.summary(context).getInt("count"));
        assertTrue(historyFile().isFile());
        assertEquals(1, entriesOnDisk());
        assertFalse(context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .contains("tai_runtime_history_json"));
    }

    @Test
    public void anUnparseableFileIsSetAsideAndReadAsEmpty() throws Exception {
        File file = historyFile();
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, TaiRuntimeHistory.summary(context).getInt("count"));
        assertTrue(new File(file.getParentFile(), "runtime-history.json.bad").isFile());
    }
}
