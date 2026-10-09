package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiEvidence;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiResidency;
import com.termux.ai.TaiTierPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/** How a category sort reads app sorting's feature load plan. */
public class LauncherCategorySortPlanTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = "gemma-4-e2b-it-litert-lm";
    private static final String E4B = "gemma-4-e4b-it-litert-lm";

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, TaiFunctionModels.ModelInfo> installed = new LinkedHashMap<>();
    private boolean remoteConfigured;

    private LauncherCategorySortPlan plan(int gb, TaiPlatformCaps.GpuPath gpu) {
        TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.from(gb * GIB), gb * GIB, 34, true, true, gpu, false);
        TaiFunctionModels.Store store = new TaiFunctionModels.Store() {
            @Override public String get(String key) {
                String value = prefs.get(key);
                return value == null ? "" : value;
            }

            @Override public void put(String key, String value) {
                prefs.put(key, value);
            }
        };
        TaiFunctionModels.Remote remote = new TaiFunctionModels.Remote() {
            @Override public boolean configured() { return remoteConfigured; }
            @Override public String modelId() { return remoteConfigured ? "big" : ""; }
            @Override public boolean understandsImages() { return false; }
            @Override public boolean prefersRemote() { return true; }
        };
        return LauncherCategorySortPlan.of(TaiFeaturePlan.of(TaiFunction.APP_CATEGORIES,
            new TaiFunctionModels(env, store, () -> installed, remote), TaiEvidence.NONE,
            Collections.<TaiResidency.Entry>emptyList(), 0L, false));
    }

    private void install(String id) {
        installed.put(id, new TaiFunctionModels.ModelInfo(id, 4L * GIB,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), TaiModelSpec.BACKEND_LITERT_LM));
    }

    @Test
    public void aLocalPlanBecomesAModelOnThePlansAccelerator() {
        install(E2B);
        LauncherCategorySortPlan plan = plan(12, TaiPlatformCaps.GpuPath.YES);
        assertEquals(E2B, plan.model);
        // The GPU verdict, not "the CPU unless a speed test says otherwise".
        assertEquals("gpu", plan.accelerator);
        assertFalse(plan.remote);
        assertTrue(plan.warnBackground);
        assertTrue(plan.hasModel());
        assertEquals("cpu", plan(12, TaiPlatformCaps.GpuPath.CPU_FIRST).accelerator);
    }

    @Test
    public void aRemotePlanBecomesTheRemoteModelWithNoAccelerator() {
        remoteConfigured = true;
        LauncherCategorySortPlan plan = plan(12, TaiPlatformCaps.GpuPath.YES);
        assertEquals("remote/big", plan.model);
        assertNull(plan.accelerator);
        assertTrue(plan.remote);
        assertEquals("big", plan.displayId());
    }

    @Test
    public void noModelMeansTheSortHasNothingToAsk() {
        assertFalse(plan(12, TaiPlatformCaps.GpuPath.YES).hasModel());
    }

    @Test
    public void theEstimateUsesTheModelsMeasuredSecondsPerApp() {
        assertEquals(2, new LauncherCategorySortPlan(E2B, "gpu", false, false).estimatedMinutes(100));
        assertEquals(5, new LauncherCategorySortPlan(E4B, "gpu", false, false).estimatedMinutes(100));
        assertEquals(1, new LauncherCategorySortPlan(E2B, "gpu", false, false).estimatedMinutes(1));
    }

    @Test
    public void aCpuSortIsEstimatedSlowerThanTheMeasuredGpuFigure() {
        LauncherCategorySortPlan plan = new LauncherCategorySortPlan(E2B, "gpu", false, false);
        assertEquals(4, plan.estimatedMinutes(100, "cpu"));
        assertEquals(2, plan.estimatedMinutes(100, "gpu"));
    }
}
