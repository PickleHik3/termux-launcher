package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class TaiRuntimeOptionsMomentaryTest {
    private static TaiRuntimeOptions plain() {
        return new TaiRuntimeOptions(null, null, null, null, "auto", 2048, null, null, null);
    }

    @Test
    public void momentarySurvivesTheJsonRoundTrip() throws Exception {
        TaiRuntimeOptions options = plain().withMomentary(Boolean.TRUE);
        TaiRuntimeOptions back = TaiRuntimeOptions.fromJson(new JSONObject(options.toJson().toString()));
        assertEquals(Boolean.TRUE, back.momentary);
        assertEquals(Integer.valueOf(2048), back.contextWindow);
    }

    @Test
    public void unsetMomentaryStaysNull() throws Exception {
        TaiRuntimeOptions back = TaiRuntimeOptions.fromJson(new JSONObject(plain().toJson().toString()));
        assertNull(back.momentary);
    }

    @Test
    public void copiesKeepMomentary() {
        TaiRuntimeOptions options = plain().withMomentary(Boolean.TRUE);
        assertEquals(Boolean.TRUE, options.withContextWindow(4096).momentary);
        assertEquals(Boolean.TRUE, options.withAccelerator("gpu").momentary);
        assertEquals(Boolean.TRUE, options.withGenerationOverrides(1, null, null, null, null, null, null).momentary);
    }
}
