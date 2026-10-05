package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiTierPolicy;
import com.termux.ai.TaiTierPolicy.Env;
import com.termux.app.fragments.settings.termux.TaiFunctionPickerModel.Section;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sections of the function picker sheet (tai-device-tiers spec §4.3) per function and tier:
 * Automatic named, the models on the phone with fit lines, Remote only when set up and able, the
 * without-a-model choice, the downloads, and the GPU choice. Pure, like the model it tests.
 */
public class TaiFunctionPickerModelTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    private static final long E2B_BYTES = 2_588_147_712L;
    private static final long E4B_BYTES = 3_659_530_240L;
    private static final TaiFunctionRows.Labels LABELS = TaiFunctionRowsTest.LABELS;

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, ModelInfo> installed = new LinkedHashMap<>();
    private boolean remoteConfigured;
    private boolean remoteImages;
    private boolean remotePrefers;

    private final TaiFunctionModels.Remote remote = new TaiFunctionModels.Remote() {
        @Override public boolean configured() { return remoteConfigured; }
        @Override public String modelId() { return remoteConfigured ? "gpt-x" : ""; }
        @Override public boolean understandsImages() { return remoteImages; }
        @Override public boolean prefersRemote() { return remotePrefers; }
    };

    private static Env env(int gb, int sdk, GpuPath gpu) {
        return new Env(TaiDeviceTier.from(gb * GIB), gb * GIB, sdk, true, true, gpu, false);
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

    private static Set<String> caps(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private void install(String id, long bytes, String... capabilities) {
        installed.put(id, new ModelInfo(id, bytes, caps(capabilities), TaiModelSpec.BACKEND_LITERT_LM));
    }

    private void installGemma() {
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        install(E4B, E4B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
    }

    private static TaiFunctionRows.CatalogItem item(String id, long bytes, String... capabilities) {
        return new TaiFunctionRows.CatalogItem(new ModelInfo(id, bytes, caps(capabilities), TaiModelSpec.BACKEND_LITERT_LM), true);
    }

    private static List<TaiFunctionRows.CatalogItem> catalogue() {
        return Arrays.asList(
            item(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            item(E4B, E4B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            item(TaiTierPolicy.WHISPER_SMALL, 300L << 20, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT),
            item(TaiModelCatalog.KITTEN_TTS_NANO_ID, 90L << 20, TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH),
            item(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 175L << 20, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS),
            item(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, 55L << 20, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION),
            item(TaiModelCatalog.U2NET_ID, 170L << 20, TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION));
    }

    private TaiFunctionPickerModel.Model build(TaiFunction function, Env env) {
        return TaiFunctionPickerModel.build(function, models(env), remote, catalogue(), Collections.<String>emptySet(), LABELS);
    }

    private static List<String> values(List<TaiFunctionPickerModel.Entry> entries) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (TaiFunctionPickerModel.Entry entry : entries) out.add(entry.value);
        return out;
    }

    // ----------------------------------------------------------------------------- automatic

    @Test
    public void automaticNamesTheTiersChoiceAndIsSelectedOnAFreshInstall() {
        installGemma();
        TaiFunctionPickerModel.Model model = build(TaiFunction.ASSISTANT, env(12, 34, GpuPath.YES));
        TaiFunctionPickerModel.Entry automatic = model.entries(Section.AUTOMATIC).get(0);
        assertEquals("AUTOMATIC[" + E2B + "]", automatic.title);
        assertEquals("", automatic.value);
        assertTrue(automatic.selected);
        // Tier 3 names E4B.
        assertEquals("AUTOMATIC[" + E4B + "]", build(TaiFunction.ASSISTANT, env(16, 34, GpuPath.YES))
            .entries(Section.AUTOMATIC).get(0).title);
    }

    @Test
    public void tierOneHasNoAutomaticAssistantToName() {
        TaiFunctionPickerModel.Model model = build(TaiFunction.ASSISTANT, env(6, 34, GpuPath.YES));
        assertEquals("AUTOMATIC_BARE", model.entries(Section.AUTOMATIC).get(0).title);
        // Without a model, tidy dictation's Automatic is raw text, and says so.
        assertEquals("AUTOMATIC[RAW_TEXT]", build(TaiFunction.TIDY_DICTATION, env(6, 34, GpuPath.YES))
            .entries(Section.AUTOMATIC).get(0).title);
    }

    @Test
    public void automaticStepsAsideForAPickAndForAPickOfOff() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.APP_CATEGORIES, E4B);
        TaiFunctionPickerModel.Model picked = TaiFunctionPickerModel.build(TaiFunction.APP_CATEGORIES, models, remote,
            catalogue(), Collections.<String>emptySet(), LABELS);
        assertFalse(picked.entries(Section.AUTOMATIC).get(0).selected);
        assertTrue(picked.entries(Section.ON_PHONE).get(1).selected);
        assertFalse(picked.entries(Section.ON_PHONE).get(0).selected);

        models.set(TaiFunction.APP_CATEGORIES, TaiFunctionModels.VALUE_OFF);
        TaiFunctionPickerModel.Model off = TaiFunctionPickerModel.build(TaiFunction.APP_CATEGORIES, models, remote,
            catalogue(), Collections.<String>emptySet(), LABELS);
        assertTrue(off.entries(Section.WITHOUT).get(0).selected);
        assertFalse(off.entries(Section.AUTOMATIC).get(0).selected);
    }

    // ----------------------------------------------------------------------------- on this phone

    @Test
    public void installedModelsShowTheirFitLineAndTheBackgroundWarning() {
        installGemma();
        List<TaiFunctionPickerModel.Entry> onPhone = build(TaiFunction.ASSISTANT, env(12, 34, GpuPath.YES)).entries(Section.ON_PHONE);
        assertEquals(Arrays.asList(E2B, E4B), values(onPhone));
        assertEquals("FIT_FITS", onPhone.get(0).detail);
        assertFalse(onPhone.get(0).warnBackground);
        assertEquals("FIT_ROOM", onPhone.get(1).detail);
        assertTrue(onPhone.get(1).warnBackground);
    }

    @Test
    public void theReaderListsOnlyModelsThatSeeImagesUnderTheirBaseIds() {
        installGemma();
        install("text-only", GIB, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        List<TaiFunctionPickerModel.Entry> onPhone = build(TaiFunction.WALLPAPER_READER, env(12, 34, GpuPath.YES)).entries(Section.ON_PHONE);
        // The stored pick is the file, not its -vision variant.
        assertEquals(Arrays.asList(E2B, E4B), values(onPhone));
    }

    @Test
    public void searchOffersEmbeddersAndVoiceTypingSpeechModels() {
        install(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 175L << 20, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        install(TaiTierPolicy.WHISPER_SMALL, 300L << 20, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        installGemma();
        assertEquals(Collections.singletonList(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID),
            values(build(TaiFunction.EMBEDDINGS, env(12, 34, GpuPath.YES)).entries(Section.ON_PHONE)));
        assertEquals(Collections.singletonList(TaiTierPolicy.WHISPER_SMALL),
            values(build(TaiFunction.VOICE_TYPING, env(12, 34, GpuPath.YES)).entries(Section.ON_PHONE)));
    }

    // ----------------------------------------------------------------------------- GPU choice

    @Test
    public void theGpuChoiceShowsForALocalPickPreselectedByThePolicy() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.UNKNOWN));
        models.set(TaiFunction.ASSISTANT, E4B);
        TaiFunctionPickerModel.Accelerator accelerator = TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models, remote,
            catalogue(), Collections.<String>emptySet(), LABELS).accelerator;
        assertNotNull(accelerator);
        assertTrue(accelerator.gpuOffered);
        assertTrue(accelerator.gpuSelected);
        assertEquals(TaiTierPolicy.NOTE_GPU_UNCONFIRMED, accelerator.note);
    }

    @Test
    public void aCpuFirstGpuPreselectsTheCpuWithTheOftenFailsNote() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.CPU_FIRST));
        models.set(TaiFunction.ASSISTANT, E4B);
        TaiFunctionPickerModel.Accelerator accelerator = TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models, remote,
            catalogue(), Collections.<String>emptySet(), LABELS).accelerator;
        assertNotNull(accelerator);
        assertFalse(accelerator.gpuSelected);
        assertEquals(TaiTierPolicy.NOTE_GPU_OFTEN_FAILS, accelerator.note);
    }

    @Test
    public void theGpuIsHiddenWhenThePhoneHasNoGpuPath() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.NO));
        models.set(TaiFunction.ASSISTANT, E2B);
        TaiFunctionPickerModel.Accelerator accelerator = TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models, remote,
            catalogue(), Collections.<String>emptySet(), LABELS).accelerator;
        assertNotNull(accelerator);
        assertFalse(accelerator.gpuOffered);
        assertFalse(accelerator.gpuSelected);
        assertNull(accelerator.note);
    }

    @Test
    public void thereIsNoAcceleratorOnAutomaticOrForNonChatFunctions() {
        installGemma();
        install(TaiTierPolicy.WHISPER_SMALL, 300L << 20, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        assertNull(build(TaiFunction.ASSISTANT, env(12, 34, GpuPath.YES)).accelerator);
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.VOICE_TYPING, TaiTierPolicy.WHISPER_SMALL);
        assertNull(TaiFunctionPickerModel.build(TaiFunction.VOICE_TYPING, models, remote, catalogue(),
            Collections.<String>emptySet(), LABELS).accelerator);
    }

    // ----------------------------------------------------------------------------- remote

    @Test
    public void remoteIsAJustASetupLinkWhileNotConfigured() {
        List<TaiFunctionPickerModel.Entry> remoteEntries = build(TaiFunction.ASSISTANT, env(12, 34, GpuPath.YES)).entries(Section.REMOTE);
        assertEquals(1, remoteEntries.size());
        assertTrue(remoteEntries.get(0).setupLink);
        assertEquals("REMOTE_SETUP", remoteEntries.get(0).title);
    }

    @Test
    public void remoteIsOneEntryOnceConfiguredAndWritesRemoteSlashId() {
        remoteConfigured = true;
        List<TaiFunctionPickerModel.Entry> remoteEntries = build(TaiFunction.APP_CATEGORIES, env(12, 34, GpuPath.YES)).entries(Section.REMOTE);
        assertEquals(1, remoteEntries.size());
        assertFalse(remoteEntries.get(0).setupLink);
        assertEquals("remote/gpt-x", remoteEntries.get(0).value);
        assertEquals("REMOTE[gpt-x]", remoteEntries.get(0).title);
    }

    @Test
    public void remoteNeverShowsForVoiceTypingReadAloudOrSearch() {
        for (boolean configured : new boolean[] {false, true}) {
            remoteConfigured = configured;
            remoteImages = true;
            assertTrue(build(TaiFunction.VOICE_TYPING, env(12, 34, GpuPath.YES)).entries(Section.REMOTE).isEmpty());
            assertTrue(build(TaiFunction.READ_ALOUD, env(12, 34, GpuPath.YES)).entries(Section.REMOTE).isEmpty());
            assertTrue(build(TaiFunction.EMBEDDINGS, env(12, 34, GpuPath.YES)).entries(Section.REMOTE).isEmpty());
        }
    }

    @Test
    public void theReadersRemoteEntryNeedsAModelThatUnderstandsImages() {
        remoteConfigured = true;
        remoteImages = false;
        assertTrue(build(TaiFunction.WALLPAPER_READER, env(12, 34, GpuPath.YES)).entries(Section.REMOTE).isEmpty());
        remoteImages = true;
        assertEquals(1, build(TaiFunction.WALLPAPER_READER, env(12, 34, GpuPath.YES)).entries(Section.REMOTE).size());
    }

    @Test
    public void aRemotePickMarksTheRemoteEntryAndPreferRemoteRenamesAutomatic() {
        installGemma();
        remoteConfigured = true;
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.ASSISTANT, "remote/gpt-x");
        TaiFunctionPickerModel.Model picked = TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models, remote, catalogue(),
            Collections.<String>emptySet(), LABELS);
        assertTrue(picked.entries(Section.REMOTE).get(0).selected);
        assertFalse(picked.entries(Section.AUTOMATIC).get(0).selected);
        assertNull(picked.accelerator);

        models.set(TaiFunction.ASSISTANT, "");
        remotePrefers = true;
        assertEquals("AUTOMATIC[REMOTE[gpt-x]]", TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models, remote, catalogue(),
            Collections.<String>emptySet(), LABELS).entries(Section.AUTOMATIC).get(0).title);
    }

    // ----------------------------------------------------------------------------- without a model

    @Test
    public void withoutAModelIsRulesOnlyRawTextOrOffWhereTheFunctionHasOne() {
        Env pong = env(12, 34, GpuPath.YES);
        assertEquals("RULES_ONLY", build(TaiFunction.WALLPAPER_READER, pong).entries(Section.WITHOUT).get(0).title);
        assertEquals("RAW_TEXT", build(TaiFunction.TIDY_DICTATION, pong).entries(Section.WITHOUT).get(0).title);
        assertEquals("OFF", build(TaiFunction.APP_CATEGORIES, pong).entries(Section.WITHOUT).get(0).title);
        assertEquals(TaiFunctionModels.VALUE_OFF, build(TaiFunction.APP_CATEGORIES, pong).entries(Section.WITHOUT).get(0).value);
        // Others cannot do without.
        assertTrue(build(TaiFunction.ASSISTANT, pong).entries(Section.WITHOUT).isEmpty());
        assertTrue(build(TaiFunction.VOICE_TYPING, pong).entries(Section.WITHOUT).isEmpty());
        assertTrue(build(TaiFunction.EMBEDDINGS, pong).entries(Section.WITHOUT).isEmpty());
    }

    // ----------------------------------------------------------------------------- get a model

    @Test
    public void getAModelListsCompatibleCatalogueEntriesNotInstalled() {
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        List<TaiFunctionPickerModel.Entry> get = build(TaiFunction.ASSISTANT, env(12, 34, GpuPath.YES)).entries(Section.GET);
        // E2B is here already; speech, voice and search entries are not assistants.
        assertEquals(Collections.singletonList(E4B), values(get));
        assertEquals(E4B_BYTES, get.get(0).sizeBytes);
        assertEquals("FIT_ROOM", get.get(0).detail);
        assertTrue(get.get(0).warnBackground);
        assertEquals(Collections.singletonList(TaiTierPolicy.WHISPER_SMALL),
            values(build(TaiFunction.VOICE_TYPING, env(12, 34, GpuPath.YES)).entries(Section.GET)));
        assertEquals(Collections.singletonList(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID),
            values(build(TaiFunction.WALLPAPER_DEPTH, env(12, 34, GpuPath.YES)).entries(Section.GET)));
    }

    @Test
    public void aBusyDownloadIsLeftOutOfGetAModel() {
        List<TaiFunctionPickerModel.Entry> get = TaiFunctionPickerModel.build(TaiFunction.ASSISTANT, models(env(12, 34, GpuPath.YES)),
            remote, catalogue(), Collections.singleton(E4B), LABELS).entries(Section.GET);
        assertEquals(Collections.singletonList(E2B), values(get));
    }

    @Test
    public void tierOneRecommendsNoLlmAndTheReaderHasNoGetEntriesBelowApi34() {
        for (TaiFunctionPickerModel.Entry entry : build(TaiFunction.ASSISTANT, env(6, 34, GpuPath.YES)).entries(Section.GET)) {
            assertFalse(entry.suggested);
            assertEquals("FIT_BIGGER", entry.detail);
        }
        assertTrue(build(TaiFunction.WALLPAPER_READER, env(12, 33, GpuPath.YES)).entries(Section.GET).isEmpty());
    }

    @Test
    public void theCutOutIsNeverPickable() {
        List<TaiFunctionPickerModel.Entry> get = build(TaiFunction.WALLPAPER_DEPTH, env(12, 34, GpuPath.YES)).entries(Section.GET);
        assertFalse(values(get).contains(TaiModelCatalog.U2NET_ID));
    }

    // ----------------------------------------------------------------------------- the chain

    @Test
    public void theFallbackChainLineIsReadOnlyTextBelowTheList() {
        installGemma();
        assertEquals("CHAIN[" + E2B + " → rules_only]", build(TaiFunction.WALLPAPER_READER, env(12, 34, GpuPath.YES)).chainLine);
        assertEquals("", build(TaiFunction.EMBEDDINGS, env(12, 34, GpuPath.YES)).chainLine);
    }
}
