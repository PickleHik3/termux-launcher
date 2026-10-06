package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiFunctionModels.Source;
import com.termux.ai.TaiTierPolicy.WithoutModel;

import java.util.Collections;

import org.json.JSONObject;
import org.junit.Test;

/** How a dictation session reads the TIDY_DICTATION resolution, and the request it sends. */
public class LocalTaiVoiceTextPolisherPlanTest {

    private static Resolution resolution(String model, String accel, WithoutModel without, String remote) {
        return new Resolution(model, accel, Source.AUTOMATIC, without, Collections.emptyList(), false, remote);
    }

    private static LocalTaiVoiceTextPolisher.Plan plan(Resolution resolution) {
        return LocalTaiVoiceTextPolisher.plan(resolution, "light", null);
    }

    @Test
    public void aLocalModelIsAskedOnItsAccelerator() {
        LocalTaiVoiceTextPolisher.Plan plan = plan(
            resolution("gemma-4-e2b-it-litert-lm", "gpu", WithoutModel.NONE, null));
        assertEquals("gemma-4-e2b-it-litert-lm", plan.model);
        assertEquals("gpu", plan.accelerator);
        assertFalse(plan.remote);
        assertNull(plan.skipReason);
    }

    @Test
    public void aRemotePickIsAskedRemotelyWithNoLoad() {
        LocalTaiVoiceTextPolisher.Plan plan = plan(
            resolution(null, null, WithoutModel.NONE, "remote/tidy-1"));
        assertEquals("remote/tidy-1", plan.model);
        assertTrue(plan.remote);
        assertNull(plan.accelerator);
        assertNull(plan.skipReason);
    }

    @Test
    public void polishedUsesTheRemoteProviderWheneverOneIsSetUp() {
        LocalTaiVoiceTextPolisher.Plan plan = LocalTaiVoiceTextPolisher.plan(
            resolution("gemma-4-e2b-it-litert-lm", "gpu", WithoutModel.NONE, null), "polished", "remote/tidy-1");
        assertEquals("remote/tidy-1", plan.model);
        assertTrue(plan.remote);
        assertNull(plan.accelerator);
        assertNull(plan.skipReason);
        // Even where the tier has no local model of its own.
        assertTrue(LocalTaiVoiceTextPolisher.plan(
            resolution(null, null, WithoutModel.RAW_TEXT, null), "polished", "remote/tidy-1").remote);
    }

    @Test
    public void polishedWithNoProviderRunsOnTheLocalPick() {
        LocalTaiVoiceTextPolisher.Plan plan = LocalTaiVoiceTextPolisher.plan(
            resolution("gemma-4-e2b-it-litert-lm", "gpu", WithoutModel.NONE, null), "polished", null);
        assertEquals("gemma-4-e2b-it-litert-lm", plan.model);
        assertFalse(plan.remote);
    }

    @Test
    public void lightStaysLocalUnlessTheResolutionItselfIsRemote() {
        assertFalse(LocalTaiVoiceTextPolisher.plan(
            resolution("gemma-4-e2b-it-litert-lm", "gpu", WithoutModel.NONE, null), "light", "remote/tidy-1").remote);
        LocalTaiVoiceTextPolisher.Plan plan = LocalTaiVoiceTextPolisher.plan(
            resolution(null, null, WithoutModel.NONE, "remote/pick-1"), "light", "remote/tidy-1");
        assertEquals("remote/pick-1", plan.model);
    }

    @Test
    public void anOffPickStaysRawTextEvenForPolishedWithAProvider() {
        Resolution off = new Resolution(null, null, Source.PICK, WithoutModel.OFF, Collections.emptyList(), false, null);
        LocalTaiVoiceTextPolisher.Plan plan = LocalTaiVoiceTextPolisher.plan(off, "polished", "remote/tidy-1");
        assertEquals("raw_text", plan.skipReason);
        assertFalse(plan.remote);
    }

    @Test
    public void rawTextSkipsPolishing() {
        LocalTaiVoiceTextPolisher.Plan plan = plan(
            resolution(null, null, WithoutModel.RAW_TEXT, null));
        assertEquals("raw_text", plan.skipReason);
        assertEquals("", plan.model);
    }

    @Test
    public void noModelAtAllIsReportedAsNoModel() {
        LocalTaiVoiceTextPolisher.Plan plan = plan(
            resolution(null, null, WithoutModel.NONE, null));
        assertEquals("no_model", plan.skipReason);
    }

    @Test
    public void theRequestAsksForSpeculativeDecodingAndLeavesTheWindowAutomatic() throws Exception {
        JSONObject body = LocalTaiVoiceTextPolisher.request("gemma-4-e2b-it-litert-lm", "gpu", "polished", "hello there");
        assertTrue(body.getBoolean("speculative_decoding"));
        assertFalse(body.getBoolean("thinking"));
        assertEquals("gpu", body.getString("accelerator"));
        assertFalse(body.has("context_window"));
        assertEquals("gemma-4-e2b-it-litert-lm", body.getString("model"));
    }

    @Test
    public void aRemoteRequestCarriesNoAccelerator() throws Exception {
        JSONObject body = LocalTaiVoiceTextPolisher.request("remote/tidy-1", "gpu", "polished", "hello there");
        assertFalse(body.has("accelerator"));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("speculative_decoding"));
        assertFalse(body.getBoolean("stream"));
        assertEquals("remote/tidy-1", body.getString("model"));
    }
}
