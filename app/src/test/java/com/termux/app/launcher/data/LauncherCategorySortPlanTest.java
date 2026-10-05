package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiFunctionModels.Source;
import com.termux.ai.TaiTierPolicy;

import java.util.Collections;

import org.junit.Test;

/** How a category sort reads the APP_CATEGORIES resolution. */
public class LauncherCategorySortPlanTest {
    private static final String E2B = "gemma-4-e2b-it-litert-lm";
    private static final String E4B = "gemma-4-e4b-it-litert-lm";

    private static Resolution local(String id, String accel, boolean warn) {
        return new Resolution(id, accel, Source.AUTOMATIC, TaiTierPolicy.WithoutModel.NONE, Collections.emptyList(), warn, null);
    }

    @Test
    public void aLocalResolutionBecomesAModelOnAnAccelerator() {
        LauncherCategorySortPlan plan = LauncherCategorySortPlan.of(local(E2B, "gpu", true));
        assertEquals(E2B, plan.model);
        assertEquals("gpu", plan.accelerator);
        assertFalse(plan.remote);
        assertTrue(plan.warnBackground);
        assertTrue(plan.hasModel());
    }

    @Test
    public void aRemoteResolutionBecomesTheRemoteModelWithNoAccelerator() {
        Resolution remote = new Resolution(null, null, Source.PICK, TaiTierPolicy.WithoutModel.NONE,
            Collections.emptyList(), false, "remote/big");
        LauncherCategorySortPlan plan = LauncherCategorySortPlan.of(remote);
        assertEquals("remote/big", plan.model);
        assertNull(plan.accelerator);
        assertTrue(plan.remote);
        assertEquals("big", plan.displayId());
    }

    @Test
    public void noModelMeansTheSortHasNothingToAsk() {
        Resolution none = new Resolution(null, null, Source.AUTOMATIC, TaiTierPolicy.WithoutModel.OFF,
            Collections.emptyList(), false, null);
        assertFalse(LauncherCategorySortPlan.of(none).hasModel());
    }

    @Test
    public void theEstimateUsesTheModelsMeasuredSecondsPerApp() {
        assertEquals(2, LauncherCategorySortPlan.of(local(E2B, "gpu", false)).estimatedMinutes(100));
        assertEquals(5, LauncherCategorySortPlan.of(local(E4B, "gpu", false)).estimatedMinutes(100));
        assertEquals(1, LauncherCategorySortPlan.of(local(E2B, "gpu", false)).estimatedMinutes(1));
    }
}
