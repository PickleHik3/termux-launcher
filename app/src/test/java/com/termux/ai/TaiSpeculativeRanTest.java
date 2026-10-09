package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Whether speculative decoding ran, as each runtime reports it on its load result and state. */
public class TaiSpeculativeRanTest {

    @Test
    public void liteRtRanItOnlyWhenAskedAndTheEngineFlagWasSet() {
        // Asked, and the file declares support: the engine was created with the flag on.
        assertEquals(Boolean.TRUE, LiteRtTaiRuntime.speculativeRan(Boolean.TRUE, Boolean.TRUE));
        // Asked, and the file has no drafter: the flag stayed unset, so it did not run.
        assertEquals(Boolean.FALSE, LiteRtTaiRuntime.speculativeRan(Boolean.TRUE, null));
        // Not asked: the engine's own default is not known.
        assertNull(LiteRtTaiRuntime.speculativeRan(null, null));
        assertNull(LiteRtTaiRuntime.speculativeRan(Boolean.FALSE, null));
    }

    @Test
    public void mnnRanItWhenTheSpeculativeTypeSurvivedIntoTheConfig() throws Exception {
        JSONObject eagle = new JSONObject().put("speculative_type", "eagle");
        JSONObject off = new JSONObject().put("speculative_type", "");
        JSONObject silent = new JSONObject().put("backend_type", "opencl");
        // MNN's own dump is read first.
        assertEquals(Boolean.TRUE, MnnTaiRuntime.speculativeRan(eagle, off));
        assertEquals(Boolean.FALSE, MnnTaiRuntime.speculativeRan(off, eagle));
        // A dump without the key falls back to what the runtime handed MNN.
        assertEquals(Boolean.TRUE, MnnTaiRuntime.speculativeRan(silent, eagle));
        assertEquals(Boolean.FALSE, MnnTaiRuntime.speculativeRan(null, silent));
        assertNull(MnnTaiRuntime.speculativeRan(null, null));
    }
}
