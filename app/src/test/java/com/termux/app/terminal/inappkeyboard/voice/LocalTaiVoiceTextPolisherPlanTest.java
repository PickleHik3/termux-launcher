package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiEvidence;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiResidency;
import com.termux.ai.TaiTierPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

/** How a dictation session reads cleanup's feature load plan, and the request it sends. */
public class LocalTaiVoiceTextPolisherPlanTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, TaiFunctionModels.ModelInfo> installed = new LinkedHashMap<>();
    private boolean remoteConfigured;
    private boolean remotePrefers;

    private LocalTaiVoiceTextPolisher.Plan plan(int gb) {
        TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.from(gb * GIB), gb * GIB, 34, true, true,
            TaiPlatformCaps.GpuPath.YES, false);
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
            @Override public String modelId() { return remoteConfigured ? "tidy-1" : ""; }
            @Override public boolean understandsImages() { return false; }
            @Override public boolean prefersRemote() { return remotePrefers; }
        };
        TaiFeaturePlan feature = TaiFeaturePlan.of(TaiFunction.TIDY_DICTATION,
            new TaiFunctionModels(env, store, () -> installed, remote), TaiEvidence.NONE,
            Collections.<TaiResidency.Entry>emptyList(), 0L, false);
        return LocalTaiVoiceTextPolisher.plan(feature);
    }

    private void installE2b() {
        installed.put(E2B, new TaiFunctionModels.ModelInfo(E2B, GIB,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), TaiModelSpec.BACKEND_LITERT_LM));
    }

    @Test
    public void aLocalModelIsAskedWithNoRemoteWhenNoneIsSetUp() {
        installE2b();
        LocalTaiVoiceTextPolisher.Plan plan = plan(12);
        assertEquals(E2B, plan.model);
        assertFalse(plan.remote);
        assertNull(plan.skipReason);
        assertNull(plan.remoteFallback);
    }

    @Test
    public void aRemotePickIsAskedRemotelyWithNoLoad() {
        installE2b();
        remoteConfigured = true;
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, "remote/tidy-1");
        LocalTaiVoiceTextPolisher.Plan plan = plan(12);
        assertEquals("remote/tidy-1", plan.model);
        assertTrue(plan.remote);
        assertNull(plan.skipReason);
    }

    @Test
    public void theRoutingDecidesRemoteNotTheCleanupLevel() {
        installE2b();
        remoteConfigured = true;
        // "Prefer remote": Automatic is the provider's model.
        remotePrefers = true;
        assertTrue(plan(12).remote);
        // "Only when no local model fits": local, with the provider to fall back on.
        remotePrefers = false;
        LocalTaiVoiceTextPolisher.Plan local = plan(12);
        assertFalse(local.remote);
        assertEquals("remote/tidy-1", local.remoteFallback);
        // Tier 1 has no local cleanup model, so the provider takes it.
        installed.clear();
        LocalTaiVoiceTextPolisher.Plan tier1 = plan(6);
        assertTrue(tier1.remote);
        assertEquals("remote/tidy-1", tier1.model);
    }

    @Test
    public void anOffPickStaysRawTextWhateverTheProvider() {
        installE2b();
        remoteConfigured = true;
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, TaiFunctionModels.VALUE_OFF);
        LocalTaiVoiceTextPolisher.Plan plan = plan(12);
        assertEquals("raw_text", plan.skipReason);
        assertFalse(plan.remote);
    }

    @Test
    public void rawTextSkipsPolishing() {
        LocalTaiVoiceTextPolisher.Plan plan = plan(6);
        assertEquals("raw_text", plan.skipReason);
        assertEquals("", plan.model);
    }

    @Test
    public void theRequestNamesTheFeatureAndLeavesTheLoadToThePlan() throws Exception {
        JSONObject body = LocalTaiVoiceTextPolisher.request(E2B, "polished", "hello there");
        assertEquals("tidy_dictation", body.getString("function"));
        assertFalse(body.getBoolean("thinking"));
        assertFalse(body.has("speculative_decoding"));
        assertFalse(body.has("accelerator"));
        assertFalse(body.has("context_window"));
        assertEquals(E2B, body.getString("model"));
    }

    @Test
    public void aRemoteRequestCarriesNoneOfTaisOwnFields() throws Exception {
        JSONObject body = LocalTaiVoiceTextPolisher.request("remote/tidy-1", "polished", "hello there");
        assertFalse(body.has("function"));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("speculative_decoding"));
        assertFalse(body.getBoolean("stream"));
        assertEquals("remote/tidy-1", body.getString("model"));
    }
}
