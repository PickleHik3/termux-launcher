package com.termux.ai;

import com.termux.ai.TaiFeaturePlan.Reason;
import com.termux.ai.TaiFeaturePlan.Residency;
import com.termux.ai.TaiFeaturePlan.Where;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiTierPolicy.Env;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The feature load plan (tai-feature-load-plan spec, step 1) from tables: the user's pick, then what
 * this phone measured for the feature, then the tier and GPU verdict; the windows, residency, groups
 * and remote routing; and what a request that names the feature loads with.
 */
public class TaiFeaturePlanTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String LITERT = TaiModelSpec.BACKEND_LITERT_LM;
    private static final String GPU = TaiTierPolicy.ACCEL_GPU;
    private static final String CPU = TaiTierPolicy.ACCEL_CPU;
    private static final long NOW = 10_000_000L;

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, ModelInfo> installed = new LinkedHashMap<>();
    private final TaiEvidence.InMemory evidence = new TaiEvidence.InMemory();
    private boolean remoteConfigured;
    private boolean remotePrefers;

    private final TaiFunctionModels.Remote remote = new TaiFunctionModels.Remote() {
        @Override public boolean configured() { return remoteConfigured; }
        @Override public String modelId() { return remoteConfigured ? "gpt-x" : ""; }
        @Override public boolean understandsImages() { return false; }
        @Override public boolean prefersRemote() { return remotePrefers; }
    };

    private static Env env(int gb, GpuPath gpu) {
        return new Env(TaiDeviceTier.from(gb * GIB), gb * GIB, 34, true, true, gpu, false);
    }

    private TaiFunctionModels models(Env env) {
        TaiFunctionModels.Store store = new TaiFunctionModels.Store() {
            @Override public String get(String key) {
                String value = prefs.get(key);
                return value == null ? "" : value;
            }

            @Override public void put(String key, String value) {
                if (value.isEmpty()) prefs.remove(key);
                else prefs.put(key, value);
            }
        };
        return new TaiFunctionModels(env, store, () -> installed, remote);
    }

    private void install(String id, String... capabilities) {
        installed.put(id, new ModelInfo(id, GIB, new HashSet<>(Arrays.asList(capabilities)), LITERT));
    }

    /** E2B with a file that declares speculative decoding. */
    private void installE2b() {
        install(E2B, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING);
    }

    private TaiFeaturePlan plan(TaiFunction feature, Env env) {
        return TaiFeaturePlan.of(feature, models(env), evidence, Collections.<TaiResidency.Entry>emptyList(), NOW, false);
    }

    private TaiFeaturePlan pong(TaiFunction feature) {
        return plan(feature, env(12, GpuPath.YES));
    }

    private static TaiEvidence.ChatResult chat(String accelerator, boolean speculative, double tps) {
        return new TaiEvidence.ChatResult(E2B, LITERT, accelerator, speculative, tps, true);
    }

    private static TaiResidency.Entry resident(TaiResidency.Kind kind, TaiFunction feature, long lastUsedMs) {
        return new TaiResidency.Entry("m-" + kind, kind, LITERT, CPU, 0, GIB, null, lastUsedMs, false, feature);
    }

    // ------------------------------------------------------------------------------- defaults

    @Test
    public void withoutMeasurementsEveryChatFeatureFollowsTheGpuVerdict() {
        installE2b();
        for (TaiFunction feature : new TaiFunction[] {TaiFunction.ASSISTANT, TaiFunction.TIDY_DICTATION,
                TaiFunction.APP_CATEGORIES, TaiFunction.DAWN_CHAT}) {
            TaiFeaturePlan plan = pong(feature);
            assertEquals(feature.name(), Where.ON_DEVICE, plan.where);
            assertEquals(feature.name(), E2B, plan.modelId);
            assertEquals(feature.name(), GPU, plan.accelerator);
            assertEquals(feature.name(), Reason.DEFAULT, plan.acceleratorReason);
            assertEquals(feature.name(), Boolean.TRUE, plan.speculative);
            assertEquals(feature.name(), Reason.DEFAULT, plan.speculativeReason);
        }
        // A GPU the self-test failed, or one the phone does not have: the CPU.
        evidence.verdict(TaiGpuVerdict.State.FAILED);
        assertEquals(CPU, pong(TaiFunction.APP_CATEGORIES).accelerator);
        assertEquals(Reason.DEFAULT, pong(TaiFunction.APP_CATEGORIES).acceleratorReason);
        assertEquals(CPU, plan(TaiFunction.TIDY_DICTATION, env(12, GpuPath.NO)).accelerator);
    }

    @Test
    public void eachFeatureHasItsWindowAndResidency() {
        installE2b();
        assertEquals(2048, pong(TaiFunction.TIDY_DICTATION).contextWindow);
        assertEquals(1024, pong(TaiFunction.APP_CATEGORIES).contextWindow);
        assertEquals(0, pong(TaiFunction.ASSISTANT).contextWindow);
        assertEquals(0, pong(TaiFunction.DAWN_CHAT).contextWindow);
        assertEquals(Residency.UNTIL_RUN_ENDS, pong(TaiFunction.APP_CATEGORIES).residency);
        assertEquals(Residency.UNTIL_IDLE, pong(TaiFunction.TIDY_DICTATION).residency);
        assertEquals(Residency.UNTIL_IDLE, pong(TaiFunction.ASSISTANT).residency);
    }

    @Test
    public void speculativeDecodingNeedsAFileThatDeclaresIt() {
        install(E2B, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFeaturePlan plan = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Boolean.FALSE, plan.speculative);
        assertEquals(Reason.DEFAULT, plan.speculativeReason);
    }

    @Test
    public void theSpeechVoiceAndSearchModelsRunOnTheCpu() {
        install(TaiTierPolicy.whisper("small", env(12, GpuPath.YES)), TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        TaiFeaturePlan plan = pong(TaiFunction.VOICE_TYPING);
        assertEquals(Where.ON_DEVICE, plan.where);
        assertEquals(CPU, plan.accelerator);
        assertNull(plan.speculative);
        assertEquals(0, plan.contextWindow);
    }

    // ---------------------------------------------------------------------------------- picks

    @Test
    public void aPickedAcceleratorWinsAndOnlyAPickGoesExplicit() {
        installE2b();
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, E2B);
        prefs.put(TaiFunction.TIDY_DICTATION.accelKey, CPU);
        TaiFeaturePlan plan = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(CPU, plan.accelerator);
        assertEquals(Reason.PICK, plan.acceleratorReason);
        assertEquals(Reason.PICK, plan.modelReason);
        // A GPU pick on a phone with no GPU at all runs on the CPU, and says why.
        prefs.put(TaiFunction.TIDY_DICTATION.accelKey, GPU);
        TaiFeaturePlan noGpu = plan(TaiFunction.TIDY_DICTATION, env(12, GpuPath.NO));
        assertEquals(CPU, noGpu.accelerator);
        assertEquals(Reason.DEFAULT, noGpu.acceleratorReason);
    }

    @Test
    public void anAcceleratorPickWithoutAModelPickIsNotAPick() {
        installE2b();
        prefs.put(TaiFunction.TIDY_DICTATION.accelKey, CPU);
        assertEquals(GPU, pong(TaiFunction.TIDY_DICTATION).accelerator);
    }

    @Test
    public void aPickMeasuredSlowerStaysAndTheFasterSetupIsOffered() {
        installE2b();
        prefs.put(TaiFunction.ASSISTANT.modelKey, E2B);
        prefs.put(TaiFunction.ASSISTANT.accelKey, CPU);
        evidence.chat(chat(GPU, false, 20.0)).chat(chat(CPU, false, 8.0));
        TaiFeaturePlan plan = pong(TaiFunction.ASSISTANT);
        assertEquals(CPU, plan.accelerator);
        assertEquals(Reason.PICK, plan.acceleratorReason);
        assertNotNull(plan.faster);
        assertEquals(GPU, plan.faster.accelerator);
        assertEquals(2.5, plan.faster.ratio, 1e-9);
        assertEquals(0.0, plan.speedup, 1e-9);
    }

    // ------------------------------------------------------------------- the Parameters screen

    /** What the user stored in Parameters, as {@code TaiSettings.storedParameter} answers it. */
    private static TaiFeaturePlan.Parameters stored(String accelerator, Boolean speculative) {
        return new TaiFeaturePlan.Parameters() {
            @Override public String accelerator(String modelId, String backend) { return accelerator; }
            @Override public Boolean speculative(String modelId, String backend) { return speculative; }
        };
    }

    private TaiFeaturePlan planWith(TaiFunction feature, TaiFeaturePlan.Parameters parameters) {
        return TaiFeaturePlan.of(feature, models(env(12, GpuPath.YES)), parameters, evidence,
            Collections.<TaiResidency.Entry>emptyList(), NOW, false);
    }

    @Test
    public void aStoredParameterIsAPickAndBeatsWhatWasMeasured() {
        installE2b();
        // Measured: the GPU is twice as fast, and speculative decoding faster still.
        evidence.chat(chat(GPU, false, 20.0)).chat(chat(CPU, false, 10.0)).chat(chat(GPU, true, 30.0));
        Object[][] table = {
            // stored accelerator, stored speculative, plan accelerator, its reason, plan speculative, its reason
            {null, null, GPU, Reason.MEASURED, Boolean.TRUE, Reason.MEASURED},
            {CPU, null, CPU, Reason.PICK, Boolean.TRUE, Reason.DEFAULT},
            {null, Boolean.FALSE, GPU, Reason.MEASURED, Boolean.FALSE, Reason.PICK},
            {CPU, Boolean.TRUE, CPU, Reason.PICK, Boolean.TRUE, Reason.PICK},
        };
        for (Object[] row : table) {
            TaiFeaturePlan plan = planWith(TaiFunction.ASSISTANT, stored((String) row[0], (Boolean) row[1]));
            String name = java.util.Arrays.toString(row);
            assertEquals(name, row[2], plan.accelerator);
            assertEquals(name, row[3], plan.acceleratorReason);
            assertEquals(name, row[4], plan.speculative);
            assertEquals(name, row[5], plan.speculativeReason);
        }
        // The stored accelerator is a pick, so it goes explicit, and the faster setup is offered.
        TaiFeaturePlan cpu = planWith(TaiFunction.ASSISTANT, stored(CPU, null));
        assertEquals(CPU, cpu.applyTo(settings(), E2B).accelerator);
        assertNotNull(cpu.faster);
    }

    @Test
    public void theFunctionsOwnPickBeatsTheParametersValueAndNothingStoredIsNoPick() {
        installE2b();
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, E2B);
        prefs.put(TaiFunction.TIDY_DICTATION.accelKey, GPU);
        assertEquals(GPU, planWith(TaiFunction.TIDY_DICTATION, stored(CPU, null)).accelerator);
        // Nothing stored (a schema default the user never set): the plan's own default.
        TaiFeaturePlan none = planWith(TaiFunction.APP_CATEGORIES, TaiFeaturePlan.Parameters.NONE);
        assertEquals(Reason.DEFAULT, none.acceleratorReason);
        assertEquals(Reason.DEFAULT, none.speculativeReason);
        // A stored GPU on a phone with no GPU at all still runs on the CPU.
        TaiFeaturePlan noGpu = TaiFeaturePlan.of(TaiFunction.APP_CATEGORIES, models(env(12, GpuPath.NO)),
            stored(GPU, null), evidence, Collections.<TaiResidency.Entry>emptyList(), NOW, false);
        assertEquals(CPU, noGpu.accelerator);
    }

    // ------------------------------------------------------------------------------- measured

    @Test
    public void aFailureRecordMovesTheDefaultToTheOtherAccelerator() {
        installE2b();
        evidence.failure(E2B, LITERT, GPU);
        TaiFeaturePlan plan = pong(TaiFunction.APP_CATEGORIES);
        assertEquals(CPU, plan.accelerator);
        assertEquals(Reason.MEASURED, plan.acceleratorReason);
        // Matched on the backend: an MNN file's failure says nothing of the LiteRT-LM one.
        TaiEvidence.InMemory other = new TaiEvidence.InMemory().failure(E2B, TaiModelSpec.BACKEND_MNN_LLM, GPU);
        assertEquals(GPU, TaiFeaturePlan.of(TaiFunction.APP_CATEGORIES, models(env(12, GpuPath.YES)), other,
            Collections.<TaiResidency.Entry>emptyList(), NOW, false).accelerator);
        // Both failed: nothing to prefer, so the default stands.
        evidence.failure(E2B, LITERT, CPU);
        assertEquals(Reason.DEFAULT, pong(TaiFunction.APP_CATEGORIES).acceleratorReason);
    }

    @Test
    public void theChatBenchSteersOnlyTheAssistantAndDawnChat() {
        installE2b();
        evidence.chat(chat(GPU, false, 6.0)).chat(chat(CPU, false, 12.0));
        TaiFeaturePlan assistant = pong(TaiFunction.ASSISTANT);
        assertEquals(CPU, assistant.accelerator);
        assertEquals(Reason.MEASURED, assistant.acceleratorReason);
        assertEquals(2.0, assistant.speedup, 1e-9);
        assertEquals(CPU, pong(TaiFunction.DAWN_CHAT).accelerator);
        // Cleanup and app sorting wait for a check of their own workload (decision 11).
        for (TaiFunction feature : new TaiFunction[] {TaiFunction.TIDY_DICTATION, TaiFunction.APP_CATEGORIES}) {
            assertEquals(feature.name(), GPU, pong(feature).accelerator);
            assertEquals(feature.name(), Reason.DEFAULT, pong(feature).acceleratorReason);
        }
    }

    @Test
    public void resultsWithinTheMarginDecideNothing() {
        installE2b();
        evidence.chat(chat(GPU, false, 10.0)).chat(chat(CPU, false, 10.5));
        assertEquals(GPU, pong(TaiFunction.ASSISTANT).accelerator);
        assertEquals(Reason.DEFAULT, pong(TaiFunction.ASSISTANT).acceleratorReason);
    }

    @Test
    public void aFastGpuResultNeverPromotesAGpuThatAnswersWrongly() {
        installE2b();
        evidence.verdict(TaiGpuVerdict.State.FAILED).chat(chat(GPU, false, 30.0)).chat(chat(CPU, false, 10.0));
        assertEquals(CPU, pong(TaiFunction.ASSISTANT).accelerator);
    }

    @Test
    public void speculativeDecodingIsMeasuredOnDecodeSpeed() {
        installE2b();
        evidence.chat(chat(GPU, false, 20.0)).chat(chat(GPU, true, 15.0));
        TaiFeaturePlan slower = pong(TaiFunction.ASSISTANT);
        assertEquals(Boolean.FALSE, slower.speculative);
        assertEquals(Reason.MEASURED, slower.speculativeReason);

        TaiEvidence.InMemory faster = new TaiEvidence.InMemory().chat(chat(GPU, false, 20.0)).chat(chat(GPU, true, 30.0));
        TaiFeaturePlan plan = TaiFeaturePlan.of(TaiFunction.ASSISTANT, models(env(12, GpuPath.YES)), faster,
            Collections.<TaiResidency.Entry>emptyList(), NOW, false);
        assertEquals(Boolean.TRUE, plan.speculative);
        assertEquals(Reason.MEASURED, plan.speculativeReason);
    }

    @Test
    public void aFeatureCheckDecidesItsOwnFeatureOnly() {
        installE2b();
        evidence.check(new TaiEvidence.FeatureResult(TaiFunction.TIDY_DICTATION, E2B, LITERT, GPU, false, 5.0, true, null))
            .check(new TaiEvidence.FeatureResult(TaiFunction.TIDY_DICTATION, E2B, LITERT, CPU, false, 9.0, true, null))
            .check(new TaiEvidence.FeatureResult(TaiFunction.TIDY_DICTATION, E2B, LITERT, CPU, true, 12.0, true, Boolean.FALSE));
        TaiFeaturePlan cleanup = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(CPU, cleanup.accelerator);
        assertEquals(Reason.MEASURED, cleanup.acceleratorReason);
        // Asked for and never ran: off, however fast the run that did not use it was.
        assertEquals(Boolean.FALSE, cleanup.speculative);
        assertEquals(Reason.MEASURED, cleanup.speculativeReason);
        assertTrue(cleanup.isMeasured());
        assertEquals(GPU, pong(TaiFunction.APP_CATEGORIES).accelerator);
    }

    // --------------------------------------------------------------------------------- remote

    @Test
    public void theRemoteRoutingDecidesOnDeviceOrRemote() {
        installE2b();
        remoteConfigured = true;
        remotePrefers = true;
        TaiFeaturePlan preferred = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Where.REMOTE, preferred.where);
        assertEquals("remote/gpt-x", preferred.requestModel());
        assertEquals(Reason.REMOTE, preferred.whereReason);
        assertNull(preferred.accelerator);
        // The local-only features never go remote.
        install(TaiTierPolicy.whisper("small", env(12, GpuPath.YES)), TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        assertEquals(Where.ON_DEVICE, pong(TaiFunction.VOICE_TYPING).where);

        // "Only when no local model fits": local, with the provider as the fallback...
        remotePrefers = false;
        TaiFeaturePlan local = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Where.ON_DEVICE, local.where);
        assertEquals("remote/gpt-x", local.remoteFallback);
        // ...and remote where no local model serves.
        installed.clear();
        TaiFeaturePlan none = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Where.REMOTE, none.where);
        assertEquals(Reason.REMOTE, none.whereReason);
    }

    @Test
    public void aRemotePickIsAPickAndOffStaysOff() {
        installE2b();
        remoteConfigured = true;
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, "remote/gpt-x");
        TaiFeaturePlan picked = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Where.REMOTE, picked.where);
        assertEquals(Reason.PICK, picked.whereReason);

        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, TaiFunctionModels.VALUE_OFF);
        TaiFeaturePlan off = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(Where.NONE, off.where);
        assertEquals(TaiTierPolicy.WithoutModel.RAW_TEXT, off.without);
        assertNull(off.remoteFallback);
    }

    // ------------------------------------------------------------------------- applying a plan

    private static TaiRuntimeOptions settings() {
        return new TaiRuntimeOptions(null, null, null, null, null, 4096, null, null, null, null, null, null);
    }

    @Test
    public void aDefaultAcceleratorGoesAsTheAutomaticLoadsPreference() {
        installE2b();
        TaiRuntimeOptions options = pong(TaiFunction.TIDY_DICTATION).applyTo(settings(), E2B);
        assertEquals("auto", options.accelerator);
        assertEquals(GPU, options.preferredAccelerator);
        assertEquals(Integer.valueOf(2048), options.contextWindow);
        assertEquals(Boolean.TRUE, options.speculativeDecodingEnabled);
        assertEquals("tidy_dictation", options.feature);
        // An explicit accelerator from the caller replaces the preference; "auto" keeps it.
        assertNull(options.withGenerationOverrides(null, null, null, null, CPU, null, null).preferredAccelerator);
        assertEquals(GPU, options.withGenerationOverrides(null, null, null, null, "auto", null, null).preferredAccelerator);
    }

    @Test
    public void aPickedAcceleratorGoesExplicit() {
        installE2b();
        prefs.put(TaiFunction.APP_CATEGORIES.modelKey, E2B);
        prefs.put(TaiFunction.APP_CATEGORIES.accelKey, CPU);
        TaiRuntimeOptions options = pong(TaiFunction.APP_CATEGORIES).applyTo(settings(), E2B);
        assertEquals(CPU, options.accelerator);
        assertNull(options.preferredAccelerator);
        assertEquals(Integer.valueOf(1024), options.contextWindow);
    }

    @Test
    public void anotherModelKeepsItsOwnSettingsButTakesTheWindow() {
        installE2b();
        TaiRuntimeOptions options = pong(TaiFunction.TIDY_DICTATION).applyTo(settings(), "some-other-model");
        assertNull(options.accelerator);
        assertNull(options.preferredAccelerator);
        assertNull(options.speculativeDecodingEnabled);
        assertEquals(Integer.valueOf(2048), options.contextWindow);
        // The automatic window leaves the settings' own.
        assertEquals(Integer.valueOf(4096), pong(TaiFunction.ASSISTANT).applyTo(settings(), E2B).contextWindow);
    }

    @Test
    public void thePlanCrossesToTheRuntimeProcessIntact() throws Exception {
        installE2b();
        evidence.chat(chat(GPU, false, 6.0)).chat(chat(CPU, false, 12.0));
        TaiFeaturePlan plan = pong(TaiFunction.ASSISTANT);
        TaiFeaturePlan back = TaiFeaturePlan.fromJson(new JSONObject(plan.toJson().toString()));
        assertNotNull(back);
        assertSame(TaiFunction.ASSISTANT, back.feature);
        assertEquals(plan.modelId, back.modelId);
        assertEquals(plan.accelerator, back.accelerator);
        assertEquals(Reason.MEASURED, back.acceleratorReason);
        assertEquals(plan.speculative, back.speculative);
        assertEquals(plan.contextWindow, back.contextWindow);
        assertNull(TaiFeaturePlan.fromJson(new JSONObject().put("feature", "nonsense")));
    }

    // ---------------------------------------------------------------------------------- groups

    @Test
    public void aRecentGroupMemberStaysForItsGroup() {
        TaiResidency.Entry dictation = resident(TaiResidency.Kind.STT, null, NOW - 60_000L);
        assertTrue(TaiFeaturePlan.keptForItsGroup(dictation, TaiFunction.APP_CATEGORIES, NOW));
        // The memory watch is no feature: it keeps a recent member too.
        assertTrue(TaiFeaturePlan.keptForItsGroup(dictation, null, NOW));
        // Past two minutes, or the very feature asking for room: no hold.
        assertFalse(TaiFeaturePlan.keptForItsGroup(resident(TaiResidency.Kind.STT, null, NOW - 180_000L), null, NOW));
        assertFalse(TaiFeaturePlan.keptForItsGroup(dictation, TaiFunction.VOICE_TYPING, NOW));
        // The assistant and app sorting belong to no group; an unknown chat user neither.
        assertFalse(TaiFeaturePlan.keptForItsGroup(resident(TaiResidency.Kind.CHAT, TaiFunction.ASSISTANT, NOW), null, NOW));
        assertFalse(TaiFeaturePlan.keptForItsGroup(resident(TaiResidency.Kind.CHAT, null, NOW), null, NOW));
        assertTrue(TaiFeaturePlan.keptForItsGroup(resident(TaiResidency.Kind.CHAT, TaiFunction.TIDY_DICTATION, NOW), null, NOW));
    }

    @Test
    public void readAloudLoadsPerReplyWhenItsGroupDoesNotFit() {
        List<TaiResidency.Entry> dawn = Collections.singletonList(resident(TaiResidency.Kind.EMBEDDING, null, NOW - 30_000L));
        assertEquals(Residency.PER_REPLY, TaiFeaturePlan.readAloudResidency(dawn, NOW, true));
        assertEquals(Residency.UNTIL_IDLE, TaiFeaturePlan.readAloudResidency(dawn, NOW, false));
        List<TaiResidency.Entry> assistant = Collections.singletonList(
            resident(TaiResidency.Kind.CHAT, TaiFunction.ASSISTANT, NOW));
        assertEquals(Residency.UNTIL_IDLE, TaiFeaturePlan.readAloudResidency(assistant, NOW, true));
        assertEquals(3, TaiFeaturePlan.groupsOf(TaiFunction.VOICE_TYPING).size() + TaiFeaturePlan.groupsOf(TaiFunction.TIDY_DICTATION).size());
        assertTrue(TaiFeaturePlan.groupsOf(TaiFunction.APP_CATEGORIES).isEmpty());
    }

    @Test
    public void requestsNameTheFeatureByIdOrGlossaryWord() {
        assertSame(TaiFunction.TIDY_DICTATION, TaiFunction.fromId("tidy_dictation"));
        assertSame(TaiFunction.TIDY_DICTATION, TaiFunction.fromId("cleanup"));
        assertSame(TaiFunction.APP_CATEGORIES, TaiFunction.fromId("app-sorting"));
        assertSame(TaiFunction.EMBEDDINGS, TaiFunction.fromId("dawn_search"));
        assertSame(TaiFunction.DAWN_CHAT, TaiFunction.fromId("Dawn_Chat"));
        assertNull(TaiFunction.fromId("nonsense"));
        assertNull(TaiFunction.fromId(null));
        // Dawn chat runs on the assistant's pick.
        assertEquals(TaiFunction.ASSISTANT.modelKey, TaiFunction.DAWN_CHAT.modelKey);
        assertEquals(TaiFunction.ASSISTANT.accelKey, TaiFunction.DAWN_CHAT.accelKey);
    }

    // ---------------------------------------------------------------- the feature check (step 2)

    /** The file and runtime on the phone now; a check measured under another key is stale. */
    private static final String NOW_KEY = "size:1:mtime:2|litert-lm 0.18.0";

    /**
     * A cleanup check of E2B, stored as {@link TaiFeatureCheck#record} writes it and read back as the evidence
     * files read it, against the phone's key now ({@code currentKey}).
     */
    private static TaiEvidence.FeatureResult checked(String accelerator, boolean speculative, double speed, double decodeTps,
                                                     Boolean ran, String currentKey) throws Exception {
        TaiFeatureCheck.Measurement m = TaiFeatureCheckTest.measurement(accelerator, speculative, speed);
        m.decodeTps = decodeTps;
        m.speculativeRan = speculative ? ran : null;
        m.staleKey = NOW_KEY;
        return TaiFeatureCheck.resultOf(TaiFeatureCheck.record(m), currentKey);
    }

    private TaiFeaturePlan cleanupOn(TaiEvidence measured) {
        return TaiFeaturePlan.of(TaiFunction.TIDY_DICTATION, models(env(12, GpuPath.YES)), measured,
            Collections.<TaiResidency.Entry>emptyList(), NOW, false);
    }

    @Test
    public void aCheckedFasterAcceleratorIsMeasured() throws Exception {
        installE2b();
        // The check runs both accelerators at the plan's own setting: speculative decoding on.
        evidence.check(checked(GPU, true, 6.0, 30.0, Boolean.TRUE, NOW_KEY))
            .check(checked(CPU, true, 12.0, 25.0, Boolean.TRUE, NOW_KEY));
        TaiFeaturePlan cleanup = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(CPU, cleanup.accelerator);
        assertEquals(Reason.MEASURED, cleanup.acceleratorReason);
        assertEquals(2.0, cleanup.speedup, 1e-9);
        // Only cleanup was checked: app sorting stays on its default.
        assertEquals(GPU, pong(TaiFunction.APP_CATEGORIES).accelerator);
        assertEquals(Reason.DEFAULT, pong(TaiFunction.APP_CATEGORIES).acceleratorReason);
    }

    @Test
    public void speculativeDecodingIsJudgedOnTheChecksDecodeSpeedNotItsFigure() throws Exception {
        installE2b();
        // With it on the whole cleanup was quicker (a shorter wait for the first word), but it decoded slower: off.
        TaiFeaturePlan slower = cleanupOn(new TaiEvidence.InMemory()
            .check(checked(GPU, true, 12.0, 18.0, Boolean.TRUE, NOW_KEY))
            .check(checked(GPU, false, 10.0, 24.0, null, NOW_KEY)));
        assertEquals(Boolean.FALSE, slower.speculative);
        assertEquals(Reason.MEASURED, slower.speculativeReason);

        TaiFeaturePlan faster = cleanupOn(new TaiEvidence.InMemory()
            .check(checked(GPU, true, 9.0, 40.0, Boolean.TRUE, NOW_KEY))
            .check(checked(GPU, false, 10.0, 24.0, null, NOW_KEY)));
        assertEquals(Boolean.TRUE, faster.speculative);
        assertEquals(Reason.MEASURED, faster.speculativeReason);
    }

    @Test
    public void speculativeDecodingThatNeverRanIsNoLongerAskedFor() throws Exception {
        installE2b();
        TaiFeaturePlan cleanup = cleanupOn(new TaiEvidence.InMemory()
            .check(checked(GPU, true, 12.0, 40.0, Boolean.FALSE, NOW_KEY))
            .check(checked(GPU, false, 10.0, 24.0, null, NOW_KEY)));
        assertEquals(Boolean.FALSE, cleanup.speculative);
        assertEquals(Reason.MEASURED, cleanup.speculativeReason);
    }

    @Test
    public void aStaleCheckIsIgnored() throws Exception {
        installE2b();
        // Measured on another file or runtime than the phone has now: the plan is its default again.
        String newer = "size:1:mtime:2|litert-lm 0.19.0";
        TaiFeaturePlan cleanup = cleanupOn(new TaiEvidence.InMemory()
            .check(checked(GPU, true, 6.0, 30.0, Boolean.FALSE, newer))
            .check(checked(CPU, true, 12.0, 25.0, Boolean.TRUE, newer)));
        assertEquals(GPU, cleanup.accelerator);
        assertEquals(Reason.DEFAULT, cleanup.acceleratorReason);
        assertEquals(Boolean.TRUE, cleanup.speculative);
        assertEquals(Reason.DEFAULT, cleanup.speculativeReason);
        assertFalse(cleanup.isMeasured());
    }

    @Test
    public void aPickTheCheckMeasuredSlowerStaysWithTheFasterSetupOffered() throws Exception {
        installE2b();
        prefs.put(TaiFunction.TIDY_DICTATION.modelKey, E2B);
        prefs.put(TaiFunction.TIDY_DICTATION.accelKey, CPU);
        evidence.check(checked(GPU, true, 15.0, 30.0, Boolean.TRUE, NOW_KEY))
            .check(checked(CPU, true, 6.0, 20.0, Boolean.TRUE, NOW_KEY));
        TaiFeaturePlan cleanup = pong(TaiFunction.TIDY_DICTATION);
        assertEquals(CPU, cleanup.accelerator);
        assertEquals(Reason.PICK, cleanup.acceleratorReason);
        assertNotNull(cleanup.faster);
        assertEquals(GPU, cleanup.faster.accelerator);
        assertEquals(2.5, cleanup.faster.ratio, 1e-9);
    }

    @Test
    public void aRunWithWrongAnswersDecidesNothing() throws Exception {
        installE2b();
        TaiFeatureCheck.Measurement wrong = TaiFeatureCheckTest.measurement(CPU, true, 50.0);
        wrong.passed = false;
        TaiFeaturePlan cleanup = cleanupOn(new TaiEvidence.InMemory()
            .check(TaiFeatureCheck.resultOf(TaiFeatureCheck.record(wrong), NOW_KEY))
            .check(checked(GPU, true, 6.0, 30.0, Boolean.TRUE, NOW_KEY)));
        assertEquals(GPU, cleanup.accelerator);
        assertEquals(Reason.DEFAULT, cleanup.acceleratorReason);
    }
}
