package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiModelSpec;
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
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The benchmark's Home opens in SettingsActivity and lays out its list: the device card, then
 * either the empty state or one row per leaderboard entry, then Run a benchmark.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TaiBenchHomeFragmentTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        com.termux.ai.TaiDownloadEngine.resetForTesting();
        com.termux.ai.TaiDownloadHub.resetForTesting();
        com.termux.ai.TaiModelCatalog.resetForTesting();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("termux_ai_model_store", Context.MODE_PRIVATE).edit().clear().commit();
        File store = new File(new File(context.getFilesDir(), "tai"), "benchmarks.json");
        if (store.isFile()) assertTrue(store.delete());
        // TaiManager is a singleton that keeps the first test's application, and so its files dir.
        java.lang.reflect.Field instance = com.termux.ai.TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private TaiBenchHomeFragment launch() throws Exception {
        Intent intent = SettingsActivity.createFragmentIntent(context, TaiBenchHomeFragment.class, R.string.tai_bench_title, null, null);
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof TaiBenchHomeFragment);
        TaiBenchHomeFragment home = (TaiBenchHomeFragment) fragment;
        // The store is read on the fragment's own thread and posted back to the main looper.
        for (int i = 0; i < 200 && !home.boardLoadedForTest(); i++) {
            Thread.sleep(25L);
            ShadowLooper.idleMainLooper();
        }
        assertTrue(home.boardLoadedForTest());
        return home;
    }

    private static JSONObject series(double median) throws Exception {
        return new JSONObject().put("med", median).put("min", median).put("max", median).put("runs", 2);
    }

    private static JSONObject record(String modelId, String accelerator, double decodeTps) throws Exception {
        return record(modelId, accelerator, decodeTps, TaiBenchSuite.BENCH_VERSION);
    }

    private static JSONObject record(String modelId, String accelerator, double decodeTps, String benchVersion) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L))
            .put("chat", new JSONObject().put("ttftMs", series(600.0)).put("decodeTps", series(decodeTps)).put("tokens", 231))
            .put("longInput", new JSONObject().put("readMs", series(4_000.0)).put("promptTokens", 2600).put("peakPssBytes", 900_000_000L));
        return new JSONObject()
            .put("id", modelId + "-" + accelerator)
            .put("benchVersion", benchVersion)
            .put("preset", "standard")
            .put("timestamp", 1_700_000_000_000L)
            .put("modelId", modelId)
            .put("displayName", modelId)
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", accelerator)
            .put("speculative", false)
            .put("runtimeVersion", "3.6.1")
            .put("appVersion", "0.2.40")
            .put("conditions", new JSONObject().put("batteryStart", 80).put("batteryEnd", 77).put("charging", false)
                .put("thermalStart", "none").put("thermalEnd", "light").put("warmStart", false))
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", 3).put("total", 3))
            .put("status", "complete");
    }

    private void seed(JSONObject... records) throws Exception {
        File dir = new File(context.getFilesDir(), "tai");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        JSONArray array = new JSONArray();
        for (JSONObject record : records) array.put(record);
        String payload = new JSONObject().put("version", 1).put("records", array).toString();
        Files.write(new File(dir, "benchmarks.json").toPath(), payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void withAnEmptyStoreHomeShowsTheDeviceCardTheEmptyStateAndTheRunButton() throws Exception {
        TaiBenchHomeFragment home = launch();
        assertFalse(home.hasRowsForTest());
        RecyclerView list = home.requireView().findViewById(R.id.tai_bench_list);
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Device card and the empty state; the action floats over the list.
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void withTwoRecordsHomeShowsOneRowPerEntry() throws Exception {
        seed(record("qwen3-vl-2b", "cpu", 21.0), record("gemma-4-e2b", "gpu", 12.0));
        TaiBenchHomeFragment home = launch();
        assertTrue(home.hasRowsForTest());
        RecyclerView list = home.requireView().findViewById(R.id.tai_bench_list);
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Device card and two rows: one list, no sort tabs; the action floats over the list.
        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void aBenchV1RecordLeavesTheModelUntestedSoHomeShowsTheEmptyState() throws Exception {
        seed(record("qwen3-vl-2b", "cpu", 21.0, "bench_v1"));
        TaiBenchHomeFragment home = launch();
        assertFalse(home.hasRowsForTest());
        RecyclerView list = home.requireView().findViewById(R.id.tai_bench_list);
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Device card and the empty state; the action floats over the list.
        assertEquals(2, adapter.getItemCount());
    }
}
