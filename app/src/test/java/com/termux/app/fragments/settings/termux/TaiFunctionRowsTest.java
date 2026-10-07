package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Model Centre's wording rules (tai-device-tiers spec §4.2): the device line, the text of a
 * function row for each kind of resolution, "Used by" and the delete fallback, the fit lines and the
 * Get models grouping. Pure: a fake store, a fake installed set and a fake Labels that prints the
 * message names, so the tests read the decision and not the English.
 */
public class TaiFunctionRowsTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    private static final long E2B_BYTES = 2_588_147_712L;
    private static final long E4B_BYTES = 3_659_530_240L;

    /** Prints "MSG[args]"; function names are the enum names, a model's name is its id without {@code -vision}. */
    static final TaiFunctionRows.Labels LABELS = new TaiFunctionRows.Labels() {
        @Override public String text(TaiFunctionRows.Msg msg, Object... args) {
            return args.length == 0 ? msg.name() : msg.name() + Arrays.toString(args);
        }

        @Override public String functionName(TaiFunction function) {
            return function == TaiFunction.WALLPAPER_READER || function == TaiFunction.WALLPAPER_DEPTH ? "WALLPAPER" : function.name();
        }

        @Override public String modelName(String modelId) {
            return TaiFunctionRows.stripVision(modelId);
        }
    };

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, ModelInfo> installed = new LinkedHashMap<>();
    private boolean remoteConfigured;
    private boolean remoteImages;

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
        TaiFunctionModels.Remote remote = new TaiFunctionModels.Remote() {
            @Override public boolean configured() { return remoteConfigured; }
            @Override public String modelId() { return remoteConfigured ? "gpt-x" : ""; }
            @Override public boolean understandsImages() { return remoteImages; }
            @Override public boolean prefersRemote() { return false; }
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

    private static TaiFunctionRows.CatalogItem item(String id, long bytes, String backend, String... capabilities) {
        return new TaiFunctionRows.CatalogItem(new ModelInfo(id, bytes, caps(capabilities), backend), true);
    }

    private static List<TaiFunctionRows.CatalogItem> catalogue() {
        String lite = TaiModelSpec.BACKEND_LITERT_LM;
        return Arrays.asList(
            item(E2B, E2B_BYTES, lite, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            item(E4B, E4B_BYTES, lite, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            item(TaiTierPolicy.WHISPER_SMALL, 300L << 20, lite, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT),
            item(TaiModelCatalog.KITTEN_TTS_NANO_ID, 90L << 20, lite, TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH),
            item(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 175L << 20, lite, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS),
            item(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, 55L << 20, lite, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION),
            item(TaiModelCatalog.U2NET_ID, 170L << 20, lite, TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION),
            // An image-generation entry: never shown anywhere in the Centre (spec §3.6).
            item("sd-turbo", 2L * GIB, TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.CAPABILITY_IMAGE_GENERATION));
    }

    // ----------------------------------------------------------------------------------- header

    @Test
    public void theDeviceLineNamesTierRamSocAndAndroid() {
        assertEquals("Tier 2 · 12 GB · Snapdragon 8+ Gen 1 · Android 16",
            TaiFunctionRows.deviceLine(env(12, 36, GpuPath.YES), "Snapdragon 8+ Gen 1", "16"));
        // A part the phone does not know is left out, not printed empty.
        assertEquals("Tier 1 · 6 GB · Android 14", TaiFunctionRows.deviceLine(env(6, 34, GpuPath.YES), " ", "14"));
        assertEquals("Tier 3 · 16 GB", TaiFunctionRows.deviceLine(env(16, 34, GpuPath.YES), null, null));
    }

    // ------------------------------------------------------------------------------ row text

    @Test
    public void anAutomaticModelNamesItselfAndWhereItRuns() {
        installGemma();
        assertEquals("AUTOMATIC[" + E2B + "] · GPU",
            TaiFunctionRows.summary(models(env(12, 34, GpuPath.YES)).resolve(TaiFunction.ASSISTANT), LABELS));
        // No GPU path at all: the CPU.
        assertEquals("AUTOMATIC[" + E2B + "] · CPU",
            TaiFunctionRows.summary(models(env(12, 34, GpuPath.NO)).resolve(TaiFunction.ASSISTANT), LABELS));
    }

    @Test
    public void aPickShowsTheModelAndWhereItRunsWithoutTheAutomaticWord() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.ASSISTANT, E4B);
        models.setAcceleratorPick(TaiFunction.ASSISTANT, "cpu");
        assertEquals(E4B + " · CPU", TaiFunctionRows.summary(models.resolve(TaiFunction.ASSISTANT), LABELS));
    }

    @Test
    public void aFunctionWithoutAModelSaysRulesOnlyRawTextOrOff() {
        TaiFunctionModels tier1 = models(env(6, 34, GpuPath.YES));
        assertEquals("RULES_ONLY", TaiFunctionRows.summary(tier1.resolve(TaiFunction.WALLPAPER_READER), LABELS));
        assertEquals("RAW_TEXT", TaiFunctionRows.summary(tier1.resolve(TaiFunction.TIDY_DICTATION), LABELS));
        assertEquals("OFF", TaiFunctionRows.summary(tier1.resolve(TaiFunction.APP_CATEGORIES), LABELS));
        // A pick of "off" reads the same way.
        TaiFunctionModels pong = models(env(12, 34, GpuPath.YES));
        installGemma();
        pong.set(TaiFunction.APP_CATEGORIES, TaiFunctionModels.VALUE_OFF);
        assertEquals("OFF", TaiFunctionRows.summary(pong.resolve(TaiFunction.APP_CATEGORIES), LABELS));
    }

    @Test
    public void aFunctionWithNothingUsableSaysNotSet() {
        // Tier 1 has no assistant by default, and nothing is installed.
        assertEquals("NOT_SET", TaiFunctionRows.summary(models(env(6, 34, GpuPath.YES)).resolve(TaiFunction.ASSISTANT), LABELS));
        // Tier 2 with no model installed: Automatic's model is missing, the chain too.
        assertEquals("NOT_SET", TaiFunctionRows.summary(models(env(12, 34, GpuPath.YES)).resolve(TaiFunction.EMBEDDINGS), LABELS));
    }

    @Test
    public void aRemotePickNamesTheRemoteModel() {
        installGemma();
        remoteConfigured = true;
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.ASSISTANT, "remote/gpt-x");
        assertEquals("REMOTE[gpt-x]", TaiFunctionRows.summary(models.resolve(TaiFunction.ASSISTANT), LABELS));
    }

    // ------------------------------------------------------------------------------ the rows

    @Test
    public void thereIsOneRowPerAllowedFunctionAndNoneForImageGenerationOrDepth() {
        installGemma();
        List<TaiFunctionRows.FunctionRow> rows = TaiFunctionRows.functionRows(models(env(12, 34, GpuPath.YES)), LABELS, "polished");
        List<TaiFunction> functions = new ArrayList<>();
        for (TaiFunctionRows.FunctionRow row : rows) functions.add(row.function);
        assertEquals(Arrays.asList(TaiFunction.ASSISTANT, TaiFunction.VOICE_TYPING, TaiFunction.TIDY_DICTATION,
            TaiFunction.READ_ALOUD, TaiFunction.APP_CATEGORIES, TaiFunction.WALLPAPER_READER, TaiFunction.EMBEDDINGS), functions);
    }

    @Test
    public void theWallpaperCreatorGoesBelowApi34() {
        installGemma();
        for (TaiFunctionRows.FunctionRow row : TaiFunctionRows.functionRows(models(env(12, 33, GpuPath.YES)), LABELS, "polished")) {
            assertFalse(row.function == TaiFunction.WALLPAPER_READER);
        }
    }

    @Test
    public void theWallpaperRowShowsReaderAndDepthTogether() {
        installGemma();
        install(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, 55L << 20, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        TaiFunctionRows.FunctionRow wallpaper = row(TaiFunctionRows.functionRows(models(env(12, 34, GpuPath.YES)), LABELS, "polished"),
            TaiFunction.WALLPAPER_READER);
        assertEquals("READER_DEPTH[AUTOMATIC[" + E4B + "] · GPU, AUTOMATIC["
            + TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID + "]]", wallpaper.summary);
    }

    @Test
    public void theBackgroundWarningFollowsTheResolvedModelsSize() {
        installGemma();
        // E4B (the reader on 12 GB) is 30 % of 12 GB: warns. E2B (assistant, categories) is 21 %: does not.
        List<TaiFunctionRows.FunctionRow> rows = TaiFunctionRows.functionRows(models(env(12, 34, GpuPath.YES)), LABELS, "polished");
        assertTrue(row(rows, TaiFunction.WALLPAPER_READER).warnBackground);
        assertFalse(row(rows, TaiFunction.APP_CATEGORIES).warnBackground);
        assertFalse(row(rows, TaiFunction.ASSISTANT).warnBackground);
    }

    @Test
    public void tidyDictationCarriesItsLevelAsABadgeAndNoOtherRowDoes() {
        installGemma();
        List<TaiFunctionRows.FunctionRow> rows = TaiFunctionRows.functionRows(models(env(12, 34, GpuPath.YES)), LABELS, "light");
        assertEquals("LEVEL_LIGHT", row(rows, TaiFunction.TIDY_DICTATION).badge);
        assertEquals("", row(rows, TaiFunction.ASSISTANT).badge);
        assertEquals("LEVEL_POLISHED", row(TaiFunctionRows.functionRows(models(env(12, 34, GpuPath.YES)), LABELS, "polished"),
            TaiFunction.TIDY_DICTATION).badge);
    }

    private static TaiFunctionRows.FunctionRow row(List<TaiFunctionRows.FunctionRow> rows, TaiFunction function) {
        for (TaiFunctionRows.FunctionRow row : rows) if (row.function == function) return row;
        throw new AssertionError("no row for " + function);
    }

    // ------------------------------------------------------------------------ used by, delete

    @Test
    public void usedByNamesEachFunctionOnceAndIsEmptyForAnUnusedModel() {
        assertEquals("USED_BY[ASSISTANT, TIDY_DICTATION]", TaiFunctionRows.usedByLine(
            Arrays.asList(TaiFunction.ASSISTANT, TaiFunction.TIDY_DICTATION), LABELS));
        // Reader and depth are one name, "Wallpaper creator".
        assertEquals("USED_BY[WALLPAPER]", TaiFunctionRows.usedByLine(
            Arrays.asList(TaiFunction.WALLPAPER_READER, TaiFunction.WALLPAPER_DEPTH), LABELS));
        assertEquals("", TaiFunctionRows.usedByLine(Collections.<TaiFunction>emptyList(), LABELS));
    }

    @Test
    public void aUsedModelsDeleteWarningNamesEachFunctionAndWhatItFallsBackTo() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        models.set(TaiFunction.ASSISTANT, E4B);
        String warning = TaiFunctionRows.deleteWarning(models, E4B, LABELS);
        // E4B is the pick for the assistant and Automatic for the reader (categories use E2B on Tier 2).
        assertTrue(warning, warning.startsWith("DELETE_IN_USE["));
        assertTrue(warning, warning.contains("DELETE_LINE[ASSISTANT, AUTOMATIC[" + E2B + "] · GPU]"));
        assertFalse(warning, warning.contains("DELETE_LINE[APP_CATEGORIES"));
        assertTrue(warning, warning.contains("DELETE_LINE[CHOOSER_READER, AUTOMATIC[" + E2B + "] · GPU]"));
    }

    @Test
    public void aFunctionWithNoFallbackSaysNotSetInTheDeleteWarning() {
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFunctionModels models = models(env(12, 34, GpuPath.YES));
        String warning = TaiFunctionRows.deleteWarning(models, E2B, LABELS);
        assertTrue(warning, warning.contains("DELETE_LINE[ASSISTANT, NOT_SET]"));
    }

    @Test
    public void anUnusedModelHasNoDeleteWarning() {
        install("imported", GIB, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        installGemma();
        // Imported chat models serve the chat functions only when picked; nothing is picked here.
        assertEquals("", TaiFunctionRows.deleteWarning(models(env(12, 34, GpuPath.YES)), "imported", LABELS));
    }

    // ------------------------------------------------------------------------------------- fit

    @Test
    public void fitLinesFollowTheTiersOffer() {
        // 12 GB: E2B is ticked and fits; E4B is only suggested and, at 30 % of the RAM, wants room.
        Env pong = env(12, 34, GpuPath.YES);
        assertEquals(TaiFunctionRows.Fit.FITS, TaiFunctionRows.fit(pong, E2B, E2B_BYTES));
        assertEquals(TaiFunctionRows.Fit.ROOM, TaiFunctionRows.fit(pong, E4B, E4B_BYTES));
        // Tier 1 lists both as made for bigger phones.
        Env small = env(6, 34, GpuPath.YES);
        assertEquals(TaiFunctionRows.Fit.BIGGER, TaiFunctionRows.fit(small, E2B, E2B_BYTES));
        // A model the policy does not name has no line, and the -vision file is the same model.
        assertEquals(TaiFunctionRows.Fit.NONE, TaiFunctionRows.fit(pong, "imported", GIB));
        assertEquals(TaiFunctionRows.Fit.FITS, TaiFunctionRows.fit(pong, E2B + "-vision", E2B_BYTES));
    }

    @Test
    public void tierOneSuggestsNoChatModel() {
        Env small = env(6, 34, GpuPath.YES);
        assertFalse(TaiFunctionRows.suggested(small, E2B, true));
        assertFalse(TaiFunctionRows.suggested(small, E4B, true));
        // A speech model is still suggested there.
        assertTrue(TaiFunctionRows.suggested(small, TaiTierPolicy.WHISPER_BASE, false));
        assertTrue(TaiFunctionRows.suggested(env(12, 34, GpuPath.YES), E2B, true));
    }

    // ------------------------------------------------------------------------------ get models

    @Test
    public void getModelsGroupsTheCatalogueInOrderAndNeverShowsImageGeneration() {
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups =
            TaiFunctionRows.getModels(env(12, 34, GpuPath.YES), catalogue(), Collections.<String>emptySet());
        assertEquals(Arrays.asList(TaiFunctionRows.Group.ASSISTANTS, TaiFunctionRows.Group.SPEECH,
            TaiFunctionRows.Group.VOICE_OUTPUT, TaiFunctionRows.Group.SEARCH, TaiFunctionRows.Group.WALLPAPER_VISION),
            new ArrayList<>(groups.keySet()));
        List<String> ids = new ArrayList<>();
        for (List<TaiFunctionRows.GetEntry> list : groups.values()) {
            for (TaiFunctionRows.GetEntry entry : list) ids.add(entry.item.info.id);
        }
        assertFalse(ids.contains("sd-turbo"));
        assertEquals(Arrays.asList(E2B, E4B), idsOf(groups.get(TaiFunctionRows.Group.ASSISTANTS)));
        assertEquals(Arrays.asList(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiModelCatalog.U2NET_ID),
            idsOf(groups.get(TaiFunctionRows.Group.WALLPAPER_VISION)));
    }

    @Test
    public void wallpaperVisionIsHiddenBelowApi34() {
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups =
            TaiFunctionRows.getModels(env(12, 33, GpuPath.YES), catalogue(), Collections.<String>emptySet());
        assertNull(groups.get(TaiFunctionRows.Group.WALLPAPER_VISION));
        assertTrue(groups.containsKey(TaiFunctionRows.Group.ASSISTANTS));
    }

    @Test
    public void installedAndDownloadingModelsAreLeftOutAndEmptyGroupsAreAbsent() {
        Set<String> skip = new HashSet<>(Arrays.asList(E2B, E4B));
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups =
            TaiFunctionRows.getModels(env(12, 34, GpuPath.YES), catalogue(), skip);
        assertFalse(groups.containsKey(TaiFunctionRows.Group.ASSISTANTS));
    }

    @Test
    public void tierOneListsLlmsWithoutAnyRecommendedMark() {
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups =
            TaiFunctionRows.getModels(env(6, 34, GpuPath.YES), catalogue(), Collections.<String>emptySet());
        for (TaiFunctionRows.GetEntry entry : groups.get(TaiFunctionRows.Group.ASSISTANTS)) {
            assertFalse(entry.suggested);
            assertEquals(TaiFunctionRows.Fit.BIGGER, entry.fit);
        }
    }

    @Test
    public void tierTwoMarksTheLlmItsTiersAutomaticAsSuggested() {
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups =
            TaiFunctionRows.getModels(env(12, 34, GpuPath.YES), catalogue(), Collections.<String>emptySet());
        List<TaiFunctionRows.GetEntry> assistants = groups.get(TaiFunctionRows.Group.ASSISTANTS);
        assertTrue(assistants.get(0).suggested);
        assertEquals(TaiFunctionRows.Fit.FITS, assistants.get(0).fit);
        assertEquals(TaiFunctionRows.Fit.ROOM, assistants.get(1).fit);
    }

    @Test
    public void noUsableBackendMeansNothingToGet() {
        Env none = new Env(TaiDeviceTier.TIER_2, 12 * GIB, 34, false, false, GpuPath.NO, false);
        assertTrue(TaiFunctionRows.getModels(none, catalogue(), Collections.<String>emptySet()).isEmpty());
    }

    @Test
    public void forLineListsWhatAModelCanServeAndTheCutOutIsWallpaperCreator() {
        Env pong = env(12, 34, GpuPath.YES);
        ModelInfo gemma = new ModelInfo(E2B, E2B_BYTES, caps(TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals("FOR[ASSISTANT, TIDY_DICTATION, APP_CATEGORIES, WALLPAPER]", TaiFunctionRows.forLine(pong, gemma, LABELS));
        ModelInfo cutout = new ModelInfo(TaiModelCatalog.U2NET_ID, 1L, caps(TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION),
            TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals("FOR[FOR_CUTOUT]", TaiFunctionRows.forLine(pong, cutout, LABELS));
        // The cut-out can be shown but never picked: it serves no function.
        assertTrue(TaiFunctionRows.servedBy(pong, cutout).isEmpty());
        // A text-only chat model serves no wallpaper reader.
        ModelInfo textOnly = new ModelInfo("t", 1L, caps(TaiModelSpec.CAPABILITY_TEXT_CHAT), TaiModelSpec.BACKEND_LITERT_LM);
        assertFalse(TaiFunctionRows.servedBy(pong, textOnly).contains(TaiFunction.WALLPAPER_READER));
    }

    @Test
    public void theChainReadsAsModelsThenAWithoutModelEnd() {
        TaiFunctionModels.Resolution resolution = models(env(12, 34, GpuPath.YES)).resolve(TaiFunction.WALLPAPER_READER);
        // The fake prints the message name; the real line lower-cases the without-model end ("rules only").
        assertEquals("CHAIN[" + E2B + " → rules_only]", TaiFunctionRows.chainLine(resolution.chain, LABELS));
        // Search falls back to the other EmbeddingGemma 2 file, then the v1 300M.
        assertEquals("CHAIN[" + TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID + " → " + TaiModelCatalog.EMBEDDING_GEMMA_300M_ID + "]",
            TaiFunctionRows.chainLine(models(env(12, 34, GpuPath.YES)).resolve(TaiFunction.EMBEDDINGS).chain, LABELS));
        // An empty chain has no line.
        assertEquals("", TaiFunctionRows.chainLine(Collections.<TaiTierPolicy.Choice>emptyList(), LABELS));
    }

    private static List<String> idsOf(List<TaiFunctionRows.GetEntry> entries) {
        List<String> ids = new ArrayList<>();
        for (TaiFunctionRows.GetEntry entry : entries) ids.add(entry.item.info.id);
        return ids;
    }
}
