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
import com.termux.ai.TaiBenchStats;
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
 * either the empty state or the tabs and one row per leaderboard entry, then Run a benchmark.
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

    private static JSONObject record(String modelId, String accelerator, double writingTps) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L).put("pssBytes", 900_000_000L))
            .put("reading", new JSONObject().put("med", 400.0).put("min", 390.0).put("max", 410.0).put("runs", 3))
            .put("firstWord", new JSONObject().put("med", 200.0).put("min", 190.0).put("max", 210.0).put("runs", 3))
            .put("writing", new JSONObject().put("med", writingTps).put("min", writingTps - 1).put("max", writingTps + 1).put("runs", 3).put("tokens", 128))
            .put("sustained", JSONObject.NULL);
        return new JSONObject()
            .put("id", modelId + "-" + accelerator)
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
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
            .put("status", "complete")
            .put("verdict", TaiBenchStats.verdict(writingTps, true));
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
        RecyclerView list = (RecyclerView) home.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Device card, the empty state, the action block.
        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void withTwoRecordsHomeShowsTheTabsAndARowPerEntry() throws Exception {
        seed(record("qwen3-vl-2b", "cpu", 21.0), record("gemma-4-e2b", "gpu", 12.0));
        TaiBenchHomeFragment home = launch();
        assertTrue(home.hasRowsForTest());
        RecyclerView list = (RecyclerView) home.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Device card, tabs, two rows, the action block.
        assertEquals(5, adapter.getItemCount());
    }
}
