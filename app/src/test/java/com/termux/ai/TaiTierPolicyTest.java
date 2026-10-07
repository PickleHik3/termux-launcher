package com.termux.ai;

import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiTierPolicy.Choice;
import com.termux.ai.TaiTierPolicy.Env;
import com.termux.ai.TaiTierPolicy.Offer;
import com.termux.ai.TaiTierPolicy.WelcomeRow;
import com.termux.ai.TaiTierPolicy.WithoutModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The policy table of tai-device-tiers §3, the platform intersection of §2, §4.4 and §5.3. */
public class TaiTierPolicyTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long E4B_BYTES = 3_659_530_240L;
    private static final long E2B_BYTES = 2_588_147_712L;

    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    private static final String E2B_V = E2B + "-vision";
    private static final String E4B_V = E4B + "-vision";

    private static Env env(int gb, GpuPath gpu) {
        return env(gb, 34, gpu, false);
    }

    private static Env env(int gb, int sdk, GpuPath gpu, boolean english) {
        return new Env(TaiDeviceTier.from(gb * GIB), gb * GIB, sdk, true, true, gpu, english);
    }

    private static void assertModel(String id, String accel, Choice choice) {
        assertEquals(id, choice.modelId);
        assertEquals(accel, choice.accelerator);
    }

    // ----------------------------------------------------------------------------------- tiers

    @Test
    public void tier1OffersNothingByDefault() {
        Env env = env(6, GpuPath.YES);
        assertNull(TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT).modelId);
        assertEquals(WithoutModel.NONE, TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT).without);
        assertEquals(WithoutModel.RAW_TEXT, TaiTierPolicy.automatic(env, TaiFunction.TIDY_DICTATION).without);
        assertEquals(WithoutModel.OFF, TaiTierPolicy.automatic(env, TaiFunction.APP_CATEGORIES).without);
        assertEquals(WithoutModel.RULES_ONLY, TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_READER).without);
        assertEquals("whisper-acft-base", TaiTierPolicy.automatic(env, TaiFunction.VOICE_TYPING).modelId);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID,
            TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_DEPTH).modelId);
        // Voice, read aloud and search are offered, never preselected; LLMs are only listed.
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, "whisper-acft-base"));
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, TaiModelCatalog.KITTEN_TTS_NANO_ID));
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID));
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID));
        assertEquals(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID, TaiTierPolicy.automatic(env, TaiFunction.EMBEDDINGS).modelId);
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, E2B));
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, E4B));
        for (WelcomeRow row : TaiTierPolicy.welcomeRows(env)) assertFalse(row.id, row.preselected);
    }

    @Test
    public void tier2At12GbIsE2bAssistantAndE4bReader() {
        Env env = env(12, GpuPath.YES);
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT));
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.TIDY_DICTATION));
        // Categories moved to E2B on Tier 2 (pong benchmark 2026-10-05): same accuracy, half the time.
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.APP_CATEGORIES));
        assertModel(E4B_V, "gpu", TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_READER));
        assertEquals("whisper-acft-small", TaiTierPolicy.automatic(env, TaiFunction.VOICE_TYPING).modelId);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID,
            TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_DEPTH).modelId);
        assertEquals(TaiModelCatalog.KITTEN_TTS_NANO_ID, TaiTierPolicy.automatic(env, TaiFunction.READ_ALOUD).modelId);
        assertEquals(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID, TaiTierPolicy.automatic(env, TaiFunction.EMBEDDINGS).modelId);
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(env, TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID));
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, TaiModelCatalog.EMBEDDING_GEMMA_300M_ID));

        List<Choice> readerChain = TaiTierPolicy.fallbackChain(env, TaiFunction.WALLPAPER_READER);
        assertEquals(2, readerChain.size());
        assertEquals(E2B_V, readerChain.get(0).modelId);
        assertEquals(WithoutModel.RULES_ONLY, readerChain.get(1).without);
        List<Choice> categoriesChain = TaiTierPolicy.fallbackChain(env, TaiFunction.APP_CATEGORIES);
        assertEquals(1, categoriesChain.size());
        assertEquals(WithoutModel.OFF, categoriesChain.get(0).without);
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(env, E2B));
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, E4B));
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(env, "whisper-acft-small"));
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, "whisper-acft-base"));
        assertEquals(Offer.SUGGESTED, TaiTierPolicy.offer(env, TaiModelCatalog.PARAKEET_TDT_V3_ID));
    }

    @Test
    public void categoriesKeepE4bOnTierThreeWithE2bAsItsFallback() {
        Env env = env(16, GpuPath.YES);
        assertModel(E4B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.APP_CATEGORIES));
        List<Choice> chain = TaiTierPolicy.fallbackChain(env, TaiFunction.APP_CATEGORIES);
        assertEquals(E2B, chain.get(0).modelId);
        assertEquals(WithoutModel.OFF, chain.get(1).without);
    }

    @Test
    public void tier2At8GbNeverGetsE4bByDefault() {
        Env env = env(8, GpuPath.YES);
        assertTrue(env.isEightGb());
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT));
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.APP_CATEGORIES));
        assertModel(E2B_V, "gpu", TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_READER));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID,
            TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_DEPTH).modelId);
        List<Choice> readerChain = TaiTierPolicy.fallbackChain(env, TaiFunction.WALLPAPER_READER);
        assertEquals(1, readerChain.size());
        assertEquals(WithoutModel.RULES_ONLY, readerChain.get(0).without);
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, E4B));
        // The 8 GB welcome card has no E4B row at all.
        for (WelcomeRow row : TaiTierPolicy.welcomeRows(env)) assertFalse(row.id.equals("smarter_reading"));
        assertFalse(env(10, GpuPath.YES).isEightGb());
    }

    @Test
    public void tier3IsE4bAssistantWithE2bFallback() {
        Env env = env(16, GpuPath.YES);
        assertModel(E4B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT));
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env, TaiFunction.TIDY_DICTATION));
        assertEquals(E2B, TaiTierPolicy.fallbackChain(env, TaiFunction.ASSISTANT).get(0).modelId);
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(env, E4B));
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(env, "whisper-acft-small"));
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(env, "whisper-acft-base"));
    }

    @Test
    public void englishPhonesGetTheEnFiles() {
        Env english = env(12, 34, GpuPath.YES, true);
        assertEquals("whisper-acft-small-en", TaiTierPolicy.automatic(english, TaiFunction.VOICE_TYPING).modelId);
        assertEquals(Offer.PRESELECTED, TaiTierPolicy.offer(english, "whisper-acft-small-en"));
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(english, "whisper-acft-small"));
        Env other = env(12, 34, GpuPath.YES, false);
        assertEquals("whisper-acft-small", TaiTierPolicy.automatic(other, TaiFunction.VOICE_TYPING).modelId);
        assertEquals(Offer.LISTED, TaiTierPolicy.offer(other, "whisper-acft-small-en"));
        Env tier1English = env(4, 34, GpuPath.YES, true);
        assertEquals("whisper-acft-base-en", TaiTierPolicy.automatic(tier1English, TaiFunction.VOICE_TYPING).modelId);
    }

    // -------------------------------------------------------------------------------- platform

    @Test
    public void belowApi34TheWallpaperFunctionsGo() {
        Env env = env(12, 33, GpuPath.YES, false);
        assertFalse(TaiTierPolicy.functionAvailable(env, TaiFunction.WALLPAPER_READER));
        assertFalse(TaiTierPolicy.functionAvailable(env, TaiFunction.WALLPAPER_DEPTH));
        assertTrue(TaiTierPolicy.functionAvailable(env, TaiFunction.ASSISTANT));
        assertNull(TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_READER).modelId);
        assertTrue(TaiTierPolicy.fallbackChain(env, TaiFunction.WALLPAPER_READER).isEmpty());
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID));
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, TaiModelCatalog.U2NET_ID));
        for (WelcomeRow row : TaiTierPolicy.welcomeRows(env)) assertFalse(row.id.equals("wallpaper_creator"));
        assertTrue(TaiTierPolicy.welcomeRows(env(12, 34, GpuPath.YES, false)).size()
            > TaiTierPolicy.welcomeRows(env).size());
    }

    @Test
    public void withNoUsableAbiThereAreNoLocalModels() {
        Env env = new Env(TaiDeviceTier.TIER_3, 16 * GIB, 34, false, false, GpuPath.YES, false);
        assertFalse(env.localModelsSupported());
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, E4B));
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, "whisper-acft-small"));
        assertTrue(TaiTierPolicy.welcomeRows(env).isEmpty());
        assertFalse(TaiTierPolicy.platformAllows(env, TaiFunction.ASSISTANT));
    }

    @Test
    public void withNoGpuPathTheAssistantIsE2bOnTheCpuAndTheReaderE4bOnTheCpu() {
        for (int gb : new int[] {12, 16}) {
            Env env = env(gb, GpuPath.NO);
            assertModel(E2B, "cpu", TaiTierPolicy.automatic(env, TaiFunction.ASSISTANT));
            assertModel(E2B, "cpu", TaiTierPolicy.automatic(env, TaiFunction.TIDY_DICTATION));
            // Tier 3 keeps E4B for categories (its resident assistant); Tier 2 uses E2B.
            assertModel(gb == 16 ? E4B : E2B, "cpu", TaiTierPolicy.automatic(env, TaiFunction.APP_CATEGORIES));
            assertModel(E4B_V, "cpu", TaiTierPolicy.automatic(env, TaiFunction.WALLPAPER_READER));
            assertFalse(TaiTierPolicy.gpuOffered(env));
        }
        assertTrue(TaiTierPolicy.fallbackChain(env(16, GpuPath.NO), TaiFunction.ASSISTANT).isEmpty());
        // The E4B row is unticked on a phone with no GPU path.
        for (WelcomeRow row : TaiTierPolicy.welcomeRows(env(16, GpuPath.NO))) {
            if (row.id.equals("smarter_reading")) assertFalse(row.preselected);
        }
    }

    @Test
    public void gpuPathChoosesTheAcceleratorAndTheNote() {
        assertEquals("gpu", TaiTierPolicy.defaultAccelerator(env(12, GpuPath.YES)));
        assertEquals("gpu", TaiTierPolicy.defaultAccelerator(env(12, GpuPath.UNKNOWN)));
        assertEquals("cpu", TaiTierPolicy.defaultAccelerator(env(12, GpuPath.CPU_FIRST)));
        assertEquals("cpu", TaiTierPolicy.defaultAccelerator(env(12, GpuPath.NO)));
        assertNull(TaiTierPolicy.gpuNote(env(12, GpuPath.YES)));
        assertEquals("Not confirmed on this GPU yet", TaiTierPolicy.gpuNote(env(12, GpuPath.UNKNOWN)));
        assertEquals("Often fails on this GPU", TaiTierPolicy.gpuNote(env(12, GpuPath.CPU_FIRST)));
        assertTrue(TaiTierPolicy.gpuOffered(env(12, GpuPath.CPU_FIRST)));
        assertModel(E2B, "cpu", TaiTierPolicy.automatic(env(12, GpuPath.CPU_FIRST), TaiFunction.ASSISTANT));
        assertModel(E2B, "gpu", TaiTierPolicy.automatic(env(12, GpuPath.UNKNOWN), TaiFunction.ASSISTANT));
    }

    // ------------------------------------------------------------------------- background rule

    @Test
    public void backgroundWarningIsAQuarterOfTheRamClass() {
        assertTrue(TaiTierPolicy.warnsBackground(env(12, GpuPath.YES), E4B_BYTES));
        assertFalse(TaiTierPolicy.warnsBackground(env(16, GpuPath.YES), E4B_BYTES));
        assertTrue(TaiTierPolicy.warnsBackground(env(8, GpuPath.YES), E2B_BYTES));
        assertFalse(TaiTierPolicy.warnsBackground(env(12, GpuPath.YES), E2B_BYTES));
        assertFalse(TaiTierPolicy.warnsBackground(env(12, GpuPath.YES), 0L));
        // Exactly a quarter warns.
        assertTrue(TaiTierPolicy.warnsBackground(env(8, GpuPath.YES), 2 * GIB));
    }

    // ---------------------------------------------------------------------------- welcome card

    private static List<String> rowIds(List<WelcomeRow> rows, boolean preselectedOnly) {
        List<String> ids = new ArrayList<>();
        for (WelcomeRow row : rows) if (!preselectedOnly || row.preselected) ids.add(row.id);
        return ids;
    }

    private static WelcomeRow row(List<WelcomeRow> rows, String id) {
        for (WelcomeRow row : rows) if (row.id.equals(id)) return row;
        return null;
    }

    @Test
    public void welcomeRowsFollowThePreselectionTable() {
        List<WelcomeRow> t1 = TaiTierPolicy.welcomeRows(env(4, GpuPath.YES));
        assertEquals(java.util.Arrays.asList("voice_typing", "read_aloud", "search", "wallpaper_creator"), rowIds(t1, false));
        assertTrue(rowIds(t1, true).isEmpty());
        assertEquals("whisper-acft-base", row(t1, "voice_typing").modelIds.get(0));

        List<WelcomeRow> t8 = TaiTierPolicy.welcomeRows(env(8, GpuPath.YES));
        assertEquals(java.util.Arrays.asList("voice_typing", "read_aloud", "assistant", "search", "wallpaper_creator"),
            rowIds(t8, true));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, row(t8, "wallpaper_creator").modelIds.get(0));
        assertEquals("whisper-acft-small", row(t8, "voice_typing").modelIds.get(0));

        List<WelcomeRow> t12 = TaiTierPolicy.welcomeRows(env(12, GpuPath.YES));
        WelcomeRow smarter = row(t12, "smarter_reading");
        assertEquals(E4B, smarter.modelIds.get(0));
        assertFalse(smarter.preselected);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, row(t12, "wallpaper_creator").modelIds.get(0));
        assertEquals(TaiModelCatalog.U2NET_ID, row(t12, "wallpaper_creator").modelIds.get(1));
        assertTrue(row(t12, "assistant").functions.contains(TaiFunction.ASSISTANT));
        assertTrue(row(t12, "assistant").functions.contains(TaiFunction.APP_CATEGORIES));
        assertFalse(smarter.functions.contains(TaiFunction.APP_CATEGORIES));

        List<WelcomeRow> t16 = TaiTierPolicy.welcomeRows(env(16, GpuPath.YES));
        WelcomeRow e4bRow = row(t16, "smarter_reading");
        assertTrue(e4bRow.preselected);
        assertTrue(e4bRow.functions.contains(TaiFunction.ASSISTANT));
        assertTrue(e4bRow.functions.contains(TaiFunction.APP_CATEGORIES));
        assertFalse(row(t16, "assistant").functions.contains(TaiFunction.ASSISTANT));
        assertTrue(row(t16, "assistant").functions.contains(TaiFunction.TIDY_DICTATION));
    }

    // -------------------------------------------------------------------- image generation

    @Test
    public void imageGenerationNeverAppears() {
        List<String> ids = new ArrayList<>();
        for (int gb : new int[] {4, 8, 12, 16}) {
            for (GpuPath gpu : GpuPath.values()) {
                for (int sdk : new int[] {33, 34}) {
                    Env env = env(gb, sdk, gpu, false);
                    for (TaiFunction function : TaiFunction.values()) {
                        ids.add(String.valueOf(TaiTierPolicy.automatic(env, function)));
                        for (Choice choice : TaiTierPolicy.fallbackChain(env, function)) ids.add(String.valueOf(choice));
                    }
                    for (WelcomeRow row : TaiTierPolicy.welcomeRows(env)) {
                        ids.add(row.id);
                        ids.addAll(row.modelIds);
                    }
                }
            }
        }
        for (String text : ids) {
            String lower = text.toLowerCase(Locale.ROOT);
            assertFalse(text, lower.contains("diffusion") || lower.contains("image") || lower.contains("sd15")
                || lower.contains("sana") || lower.contains("taiyi"));
        }
        for (TaiFunction function : TaiFunction.values()) {
            assertFalse(function.name(), function.name().contains("IMAGE"));
        }
        Env env = env(16, GpuPath.YES);
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, "stable-diffusion-1.5-mnn"));
        assertEquals(Offer.HIDDEN, TaiTierPolicy.offer(env, TaiModelCatalog.SEGFORMER_B0_ADE20K_ID));
    }
}
