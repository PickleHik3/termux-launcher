package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Which model and which order the "Re-sort apps" chooser offers. */
public class LauncherCategorySortChoiceTest {
    private static final String E2B = "gemma-4-e2b-it-litert-lm";
    private static final String E4B = "gemma-4-e4b-it-litert-lm";

    private static TaiFunctionModels.ModelInfo chat(String id, long size) {
        return new TaiFunctionModels.ModelInfo(id, size,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), TaiModelSpec.BACKEND_LITERT_LM);
    }

    @Test
    public void tier1WithE2BInstalled_offersItAndSuggestsCopy() {
        List<TaiFunctionModels.ModelInfo> installed = Arrays.asList(chat(E4B, 4_000), chat(E2B, 2_000));
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(true, null, false, installed, id -> false);
        assertEquals(E2B, choice.onDeviceModel);
        assertTrue(choice.explicit);
        assertTrue(choice.advisesAgainstOnDevice);
    }

    @Test
    public void tier1WithNoModel_hasNoOnDeviceModel() {
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(
            true, null, false, Collections.emptyList(), id -> false);
        assertNull(choice.onDeviceModel);
        assertFalse(choice.explicit);
    }

    @Test
    public void roomyPhone_keepsTodaysOrderAndPlan() {
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(
            false, E4B, false, Arrays.asList(chat(E4B, 4_000)), id -> false);
        assertEquals(E4B, choice.onDeviceModel);
        assertFalse(choice.explicit);
        assertFalse(choice.advisesAgainstOnDevice);
    }

    @Test
    public void ramCheckAdvisesAgainst_swapsOnAnyTier() {
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(
            false, E4B, false, Collections.emptyList(), E4B::equals);
        assertTrue(choice.advisesAgainstOnDevice);
    }

    @Test
    public void notTier1WithoutPlanModel_doesNotPickOne() {
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(
            false, null, false, Arrays.asList(chat(E2B, 2_000)), id -> false);
        assertNull(choice.onDeviceModel);
        assertFalse(choice.advisesAgainstOnDevice);
    }

    @Test
    public void remotePlan_isUntouched() {
        LauncherCategorySortChoice choice = LauncherCategorySortChoice.decide(
            true, "remote/x", true, Collections.emptyList(), id -> true);
        assertEquals("remote/x", choice.onDeviceModel);
        assertFalse(choice.advisesAgainstOnDevice);
    }
}
