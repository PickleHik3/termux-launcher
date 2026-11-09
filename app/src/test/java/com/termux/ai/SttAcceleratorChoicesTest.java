package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class SttAcceleratorChoicesTest {
    @Test
    public void gpuIsOfferedWhenThePhoneRunsItAndNothingCrashed() {
        assertEquals(Arrays.asList("auto", "cpu", "gpu"), SttAcceleratorChoices.options(true, null));
    }

    @Test
    public void gpuIsHiddenWhenThePhoneCannotRunIt() {
        assertEquals(Arrays.asList("auto", "cpu"), SttAcceleratorChoices.options(false, null));
    }

    @Test
    public void gpuIsHiddenAfterARecordedCrash() {
        assertEquals(Arrays.asList("auto", "cpu"),
            SttAcceleratorChoices.options(true, "crash: the process died during 2 GPU loads in a row"));
    }

    @Test
    public void aPlainGpuFailureKeepsGpuOffered() {
        assertTrue(SttAcceleratorChoices.gpuOffered(true, "IllegalStateException: output not sane"));
    }

    @Test
    public void forcedGpuIsUnavailableOnlyWhenGpuIsPicked() {
        assertTrue(SttAcceleratorChoices.gpuForcedButUnavailable("gpu", true, "failed"));
        assertTrue(SttAcceleratorChoices.gpuForcedButUnavailable("gpu", false, null));
        assertFalse(SttAcceleratorChoices.gpuForcedButUnavailable("gpu", true, null));
        assertFalse(SttAcceleratorChoices.gpuForcedButUnavailable("auto", true, "failed"));
        assertFalse(SttAcceleratorChoices.gpuForcedButUnavailable("cpu", false, "crash: x"));
    }
}
