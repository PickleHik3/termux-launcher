package com.termux.ai;

import com.termux.ai.TaiGpuVerdict.State;
import com.termux.ai.TaiPlatformCaps.GpuPath;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The GPU canary's matcher, the verdict keyed by driver and app version, and what it does to the GPU path. */
public class TaiGpuVerdictTest {
    private static final String KEY = TaiGpuVerdict.key("Adreno (TM) 830", "OpenCL 3.0 Compiler E031", "1.0.0");

    private static class FakeStore implements TaiGpuVerdict.Store {
        String value;
        @Override public String read() { return value; }
        @Override public void write(String v) { value = v; }
    }

    @Test
    public void theMatcherAcceptsAReplyContainingTheExpectedText() {
        assertTrue(TaiGpuVerdict.canaryPasses("OK 42"));
        assertTrue(TaiGpuVerdict.canaryPasses("  ok 42.\n"));
        assertTrue(TaiGpuVerdict.canaryPasses("Sure: OK 42"));
    }

    @Test
    public void theMatcherRejectsGarbageAndEmptyReplies() {
        assertFalse(TaiGpuVerdict.canaryPasses(null));
        assertFalse(TaiGpuVerdict.canaryPasses(""));
        assertFalse(TaiGpuVerdict.canaryPasses("OK 24"));
        assertFalse(TaiGpuVerdict.canaryPasses("\u0000\u0000 ### ###"));
        assertFalse(TaiGpuVerdict.canaryPasses("OK"));
    }

    @Test
    public void theCanaryPromptIsTheFixedOneAtSixteenTokens() {
        assertEquals("Reply with exactly: OK 42", TaiGpuVerdict.CANARY_PROMPT);
        assertEquals(16, TaiGpuVerdict.CANARY_MAX_TOKENS);
    }

    @Test
    public void aStoredVerdictReadsBackForTheSameDriverAndBuild() {
        FakeStore store = new FakeStore();
        assertEquals(State.UNKNOWN, TaiGpuVerdict.current(store, KEY));
        store.write(TaiGpuVerdict.encode(State.VERIFIED, KEY));
        assertEquals(State.VERIFIED, TaiGpuVerdict.current(store, KEY));
        store.write(TaiGpuVerdict.encode(State.FAILED, KEY));
        assertEquals(State.FAILED, TaiGpuVerdict.current(store, KEY));
    }

    @Test
    public void aDriverOrAppUpdateRunsTheCanaryAgain() {
        FakeStore store = new FakeStore();
        store.write(TaiGpuVerdict.encode(State.FAILED, KEY));
        assertEquals(State.UNKNOWN, TaiGpuVerdict.current(store, TaiGpuVerdict.key("Adreno (TM) 830", "OpenCL 3.0 Compiler E032", "1.0.0")));
        assertEquals(State.UNKNOWN, TaiGpuVerdict.current(store, TaiGpuVerdict.key("Adreno (TM) 830", "OpenCL 3.0 Compiler E031", "1.0.1")));
    }

    @Test
    public void aCanaryThatNoLiveProcessOwnsReadsAsACrashAndFailed() {
        String running = TaiGpuVerdict.encode(State.CANARY_RUNNING, KEY);
        assertEquals(State.FAILED, TaiGpuVerdict.decode(running, KEY, false));
        assertEquals(State.UNKNOWN, TaiGpuVerdict.decode(running, KEY, true));
    }

    @Test
    public void garbageInTheStoreIsUnknown() {
        assertEquals(State.UNKNOWN, TaiGpuVerdict.decode("nonsense", KEY, false));
        assertEquals(State.UNKNOWN, TaiGpuVerdict.decode("BOGUS\t" + KEY, KEY, false));
        assertEquals(State.UNKNOWN, TaiGpuVerdict.decode(null, KEY, false));
    }

    @Test
    public void theVerdictMapsToTheGpuPathAndNoStaysNo() {
        assertEquals(GpuPath.YES, TaiGpuVerdict.apply(GpuPath.UNKNOWN, State.VERIFIED));
        assertEquals(GpuPath.YES, TaiGpuVerdict.apply(GpuPath.CPU_FIRST, State.VERIFIED));
        assertEquals(GpuPath.CPU_FIRST, TaiGpuVerdict.apply(GpuPath.UNKNOWN, State.FAILED));
        assertEquals(GpuPath.CPU_FIRST, TaiGpuVerdict.apply(GpuPath.YES, State.FAILED));
        assertEquals(GpuPath.UNKNOWN, TaiGpuVerdict.apply(GpuPath.UNKNOWN, State.UNKNOWN));
        assertEquals(GpuPath.NO, TaiGpuVerdict.apply(GpuPath.NO, State.VERIFIED));
        assertEquals(GpuPath.NO, TaiGpuVerdict.apply(GpuPath.NO, State.FAILED));
    }

    @Test
    public void theDefaultAcceleratorFollowsTheVerdict() {
        long gib = 1024L * 1024L * 1024L;
        for (State state : new State[] {State.VERIFIED, State.FAILED, State.UNKNOWN}) {
            GpuPath path = TaiGpuVerdict.apply(GpuPath.UNKNOWN, state);
            TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.TIER_2, 12 * gib, 34, true, true, path, false);
            assertEquals(state == State.FAILED ? "cpu" : "gpu", TaiTierPolicy.defaultAccelerator(env));
        }
    }

    @Test
    public void theCanaryRunsOnlyOnTheFirstUnconfirmedGpuLoadOfAGemma4File() {
        String e2b = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
        assertTrue(TaiGpuVerdict.shouldRunCanary(GpuPath.UNKNOWN, e2b, "gpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.UNKNOWN, e2b, "gpu", true));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.UNKNOWN, e2b, "cpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.YES, e2b, "gpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.CPU_FIRST, e2b, "gpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.NO, e2b, "gpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.UNKNOWN, "qwen3-0.6b", "gpu", false));
        assertFalse(TaiGpuVerdict.shouldRunCanary(GpuPath.UNKNOWN, null, "gpu", false));
    }

    @Test
    public void theKeyCarriesTheDriverAndTheBuild() {
        assertEquals("gpu|drv|1.2", TaiGpuVerdict.key(" gpu ", "drv", "1.2"));
        assertEquals("||", TaiGpuVerdict.key(null, null, null));
    }
}
