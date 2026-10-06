package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.data.CategorySortLoadPolicy.Decision;
import com.termux.app.launcher.data.CategorySortLoadPolicy.Entry;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CategorySortLoadPolicyTest {
    private static final String M = "gemma-4-e2b-it-litert-lm";

    private static Entry e(String accel, boolean spec, double ttft, long load, boolean passed) {
        return new Entry(M, accel, spec, ttft, load, passed);
    }

    private static Decision decide(String picked, boolean gpuOffered, boolean gpuTrusted, Entry... entries) {
        return CategorySortLoadPolicy.decide(M, "gpu", picked, gpuOffered, gpuTrusted, Arrays.asList(entries), 1000);
    }

    @Test
    public void noResultsMeansCpuSpeculativeOffSmallWindow() {
        Decision d = CategorySortLoadPolicy.decide(M, "gpu", null, true, true, Collections.emptyList(), 1000);
        assertEquals("cpu", d.accelerator);
        assertFalse(d.speculative);
        assertEquals(1024, d.contextWindow);
        assertFalse(d.benchmarked);
    }

    @Test
    public void aFasterPassingGpuResultWins() {
        Decision d = decide(null, true, true, e("cpu", false, 900, 4000, true), e("gpu", false, 400, 6000, true));
        assertEquals("gpu", d.accelerator);
        assertTrue(d.benchmarked);
        assertFalse(d.speculative);
    }

    @Test
    public void aFailedGpuResultIsIgnored() {
        Decision d = decide(null, true, true, e("cpu", false, 900, 4000, true), e("gpu", false, 100, 1000, false));
        assertEquals("cpu", d.accelerator);
        assertTrue(d.benchmarked);
    }

    @Test
    public void onlyFailedResultsLeaveTheSafeDefault() {
        Decision d = decide(null, true, true, e("gpu", false, 100, 1000, false));
        assertEquals("cpu", d.accelerator);
        assertFalse(d.benchmarked);
    }

    @Test
    public void loadTimeBreaksATie() {
        Decision d = decide(null, true, true, e("cpu", false, 500, 3000, true), e("gpu", false, 500, 6000, true));
        assertEquals("cpu", d.accelerator);
    }

    @Test
    public void speculativeIsOnlyOnWhenItBeatsThePlainResultOnTheSameAccelerator() {
        assertTrue(decide(null, true, true, e("gpu", false, 500, 5000, true), e("gpu", true, 400, 7000, true)).speculative);
        assertFalse(decide(null, true, true, e("gpu", false, 500, 5000, true), e("gpu", true, 500, 7000, true)).speculative);
        assertFalse(decide(null, true, true, e("gpu", false, 500, 5000, true), e("gpu", true, 700, 7000, true)).speculative);
        // a faster speculative CPU entry does not turn it on for the GPU
        assertFalse(decide(null, true, true, e("gpu", false, 500, 5000, true), e("cpu", true, 100, 7000, true)).speculative);
        // a speculative result with nothing to compare against stays off
        assertFalse(decide(null, true, true, e("gpu", true, 100, 7000, true)).speculative);
    }

    @Test
    public void theUsersAcceleratorPickWinsButTheResultsStillSetSpeculative() {
        Decision d = decide("cpu", true, true, e("gpu", false, 100, 1000, true),
            e("cpu", false, 900, 4000, true), e("cpu", true, 600, 5000, true));
        assertEquals("cpu", d.accelerator);
        assertTrue(d.speculative);
        assertTrue(d.benchmarked);
        assertEquals(1024, d.contextWindow);
    }

    @Test
    public void aPickWithoutResultsForItIsNotBenchmarked() {
        Decision d = decide("gpu", true, false, e("cpu", false, 900, 4000, true));
        assertEquals("gpu", d.accelerator);
        assertFalse(d.benchmarked);
        assertFalse(d.speculative);
    }

    @Test
    public void aDeviceWithoutAGpuAlwaysRunsOnTheCpu() {
        assertEquals("cpu", decide(null, false, false, e("gpu", false, 100, 1000, true), e("cpu", false, 900, 4000, true)).accelerator);
        assertEquals("cpu", decide("gpu", false, false).accelerator);
    }

    @Test
    public void aGpuWhoseCanaryFailedIsNotChosenFromResultsAlone() {
        assertEquals("cpu", decide(null, true, false, e("gpu", false, 100, 1000, true), e("cpu", false, 900, 4000, true)).accelerator);
    }

    @Test
    public void otherModelsResultsAreIgnored() {
        Entry other = new Entry("other", "gpu", false, 100, 1000, true);
        Decision d = CategorySortLoadPolicy.decide(M, "gpu", null, true, true, Collections.singletonList(other), 1000);
        assertEquals("cpu", d.accelerator);
        assertFalse(d.benchmarked);
    }

    @Test
    public void theWindowIsTheSmallestTheRuntimeAcceptsUnlessThePromptNeedsMore() {
        assertEquals(1024, CategorySortLoadPolicy.contextWindowFor(0));
        assertEquals(1024, CategorySortLoadPolicy.contextWindowFor(1200));
        assertEquals(1280, CategorySortLoadPolicy.contextWindowFor(3000));
        assertEquals(4096, CategorySortLoadPolicy.contextWindowFor(100000));
        // the real prompt for an ordinary app fits the minimum
        assertTrue(LauncherCategorySortPrompt.singleAppPrompt("Some App", "com.example.some").length() < 1500);
    }

    @Test
    public void entriesAreReadFromTheLeaderboardRows() throws Exception {
        JSONObject ok = new JSONObject().put("modelId", M).put("accelerator", "GPU").put("speculative", false)
            .put("ttftMs", 420.0).put("loadMs", 5000).put("checkPassed", true).put("verdict", "usable");
        JSONObject broken = new JSONObject().put("modelId", M).put("accelerator", "cpu").put("checkPassed", true)
            .put("verdict", "crashed").put("ttftMs", JSONObject.NULL);
        JSONObject board = new JSONObject().put("leaderboard", new JSONObject().put("ranked",
            new org.json.JSONArray().put(ok).put(broken)));
        List<Entry> entries = CategorySortLoadPolicy.entriesFrom(board);
        assertEquals(2, entries.size());
        assertEquals("gpu", entries.get(0).accelerator);
        assertTrue(entries.get(0).passed);
        assertFalse(entries.get(1).passed);
        assertTrue(Double.isNaN(entries.get(1).ttftMs));
        assertTrue(CategorySortLoadPolicy.entriesFrom(null).isEmpty());
        assertEquals(new ArrayList<Entry>().size(), CategorySortLoadPolicy.entriesFrom(new JSONObject()).size());
    }
}
