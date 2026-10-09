package com.termux.app.fragments.settings.termux;

import com.termux.R;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaiImportFactsTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    /** The English strings the facts use, so the tests read like the screen. */
    static final TaiImportFacts.Words WORDS = (id, args) -> String.format(Locale.US, english(id), args);

    private static String english(int id) {
        if (id == R.string.termux_ai_import_variant_gpu_build) return "GPU build";
        if (id == R.string.termux_ai_import_variant_web_build) return "web build";
        if (id == R.string.termux_ai_import_variant_chip_build) return "for %1$s";
        if (id == R.string.termux_ai_import_variant_context) return "%1$d-token context";
        if (id == R.string.termux_ai_import_variant_input) return "%1$d-token input";
        if (id == R.string.termux_ai_import_fit_file) return "%1$s file";
        if (id == R.string.termux_ai_import_fit_file_unknown) return "File size not listed";
        if (id == R.string.termux_ai_import_fit_phone_free) return "this phone has %1$d GB RAM, about %2$s free now";
        if (id == R.string.termux_ai_import_fit_phone) return "this phone has %1$d GB RAM";
        if (id == R.string.termux_ai_import_fit_phone_unknown) return "this phone's RAM could not be read";
        throw new AssertionError("unexpected string " + id);
    }

    private static final String GRANITE = TaiImportNames.displayName("https://huggingface.co/litert-community/granite-4.0-h-350m");
    private static final String QWEN = TaiImportNames.displayName("https://huggingface.co/litert-community/Qwen3.5-2B");

    @Test
    public void graniteBuildsAreNamedByTheirFileNameOnly() {
        assertEquals("Granite 4.0 H 350M", GRANITE);
        assertEquals("Granite 4.0 H 350M · int8",
            TaiImportFacts.variantTitle(WORDS, GRANITE, "granite-4.0-h-350m_int8.litertlm"));
        assertEquals("Granite 4.0 H 350M · int8 · GPU build",
            TaiImportFacts.variantTitle(WORDS, GRANITE, "granite-4.0-h-350m_int8_gpu.litertlm"));
        assertEquals("Granite 4.0 H 350M · fp16",
            TaiImportFacts.variantTitle(WORDS, GRANITE, "granite-4.0-h-350m_fp16.litertlm"));
    }

    @Test
    public void qwenVisionAndTextFilesKeepTheirOwnNames() {
        assertEquals("Qwen3.5 2B VL · int8", TaiImportFacts.variantTitle(WORDS, QWEN, "Qwen3.5-2B-VL_int8.litertlm"));
        assertEquals("Qwen3.5 2B · int8", TaiImportFacts.variantTitle(WORDS, QWEN, "Qwen3.5-2B_int8.litertlm"));
        assertEquals("Qwen3.5 2B VL", TaiImportFacts.modelName(QWEN, "Qwen3.5-2B-VL_int8.litertlm"));
        assertEquals("Qwen3.5 2B", TaiImportFacts.modelName(QWEN, "Qwen3.5-2B_int8.litertlm"));
    }

    @Test
    public void qwenImageInputIsSourcedPerFileNotFromRepositoryTags() throws Exception {
        // The repository is tagged vlm and image-text-to-text for both files; only the VL file sees images.
        JSONObject facts = new JSONObject().put("pipelineTag", "text-generation")
            .put("tags", new JSONArray().put("vlm").put("image-text-to-text").put("text-generation"))
            .put("license", "apache-2.0");
        String base = "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/0123/";
        LinkedHashMap<String, Integer> vl = TaiImportFacts.groundedCapabilities(
            base + "Qwen3.5-2B-VL_int8.litertlm Qwen3.5-2B-VL_int8.litertlm", "Qwen3.5-2B-VL_int8.litertlm", facts, false);
        LinkedHashMap<String, Integer> text = TaiImportFacts.groundedCapabilities(
            base + "Qwen3.5-2B_int8.litertlm Qwen3.5-2B_int8.litertlm", "Qwen3.5-2B_int8.litertlm", facts, false);
        assertEquals(Integer.valueOf(R.string.termux_ai_import_source_card), vl.get(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertEquals(Integer.valueOf(R.string.termux_ai_import_source_card), vl.get(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertFalse(text.containsKey(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(text.containsKey(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        // A repository's sole file may take the image claim from its tags.
        LinkedHashMap<String, Integer> sole = TaiImportFacts.groundedCapabilities(
            "https://huggingface.co/org/vlm model.litertlm", "model.litertlm", facts, true);
        assertEquals(Integer.valueOf(R.string.termux_ai_import_source_tags), sole.get(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
    }

    @Test
    public void gemma4NameGuessesAreNotSourced() {
        // Gemma 4's repository states no pipeline tag and no modality tags; its image and audio
        // input are the importer's name guesses, so only chat (from the bundle format) is sourced.
        JSONObject facts = new JSONObject();
        String file = "gemma-4-E2B-it.litertlm";
        LinkedHashMap<String, Integer> grounded = TaiImportFacts.groundedCapabilities(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/0123/" + file, file, facts, false);
        assertEquals(1, grounded.size());
        assertEquals(Integer.valueOf(R.string.termux_ai_import_source_format), grounded.get(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        java.util.Set<String>[] split = TaiImportFlow.splitCapabilities(
            TaiImportGuess.capabilities("gemma-4-e2b-it " + file), grounded.keySet(), new java.util.HashSet<>());
        assertTrue(split[0].contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(split[1].contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(split[1].contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        // Ticking one under Advanced makes it the user's statement, not a guess.
        java.util.Set<String> user = new java.util.HashSet<>();
        user.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        split = TaiImportFlow.splitCapabilities(TaiImportGuess.capabilities("gemma-4-e2b-it " + file), grounded.keySet(), user);
        assertTrue(split[0].contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
    }

    @Test
    public void chipBuildsNameTheirChipAsTheFileSpellsIt() {
        String embedding = TaiImportNames.displayName("https://huggingface.co/litert-community/embeddinggemma-300m");
        assertEquals("Embeddinggemma 300M · mixed-precision · for MediaTek MT6991 · 512-token input",
            TaiImportFacts.variantTitle(WORDS, embedding, "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite"));
        String gemma = TaiImportNames.displayName("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm");
        assertEquals("Gemma 4 E2B IT · for Google Tensor G5",
            TaiImportFacts.variantTitle(WORDS, gemma, "gemma-4-E2B-it_Google_Tensor_G5.litertlm"));
        assertEquals("Gemma 4 E2B IT · for Qualcomm SM8750",
            TaiImportFacts.variantTitle(WORDS, gemma, "gemma-4-E2B-it_qualcomm_sm8750.litertlm"));
        assertEquals("Gemma 4 E2B IT · GPU build", TaiImportFacts.variantTitle(WORDS, gemma, "gemma-4-E2B-it-gpu.litertlm"));
        assertEquals("Gemma 4 E2B IT", TaiImportFacts.variantTitle(WORDS, gemma, "gemma-4-E2B-it.litertlm"));
    }

    @Test
    public void theKvCacheInTheNameIsTheContextWindow() {
        String smol = TaiImportNames.displayName("https://huggingface.co/litert-community/SmolLM3-3B");
        String file = "SmolLM3-3B_q4_block32_ekv4096.litertlm";
        assertEquals("SmolLM3 3B · q4_block32 · 4096-token context", TaiImportFacts.variantTitle(WORDS, smol, file));
        assertArrayEquals(new int[]{4096, R.string.termux_ai_import_source_file_name},
            TaiImportFacts.contextWindow("https://huggingface.co/litert-community/SmolLM3-3B " + file, file));
        assertEquals("Medgemma 1.5 4B IT · q4_block32 · vision · 2048-token context",
            TaiImportFacts.variantTitle(WORDS, "", "medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm"));
        // Qwen3.5 bundles carry no window in their names; the card-sourced family table gives 4096.
        assertArrayEquals(new int[]{4096, R.string.termux_ai_import_source_card},
            TaiImportFacts.contextWindow("litert-community/Qwen3.5-2B Qwen3.5-2B_int8.litertlm", "Qwen3.5-2B_int8.litertlm"));
        // Nobody states Gemma 4's window, so none is shown.
        assertNull(TaiImportFacts.contextWindow("gemma-4-E2B-it.litertlm", "gemma-4-E2B-it.litertlm"));
    }

    @Test
    public void filesTheNameCannotTellApartGetTheirFormat() throws Exception {
        String gemma = TaiImportNames.displayName("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm");
        JSONArray candidates = new JSONArray()
            .put(new JSONObject().put("file", "gemma-4-E2B-it-web.litertlm"))
            .put(new JSONObject().put("file", "gemma-4-E2B-it-web.task"))
            .put(new JSONObject().put("file", "gemma-4-E2B-it.litertlm"));
        String[] titles = TaiImportFlow.variantTitles(WORDS, gemma, candidates);
        assertEquals("Gemma 4 E2B IT · web build · .litertlm", titles[0]);
        assertEquals("Gemma 4 E2B IT · web build · .task", titles[1]);
        assertEquals("Gemma 4 E2B IT", titles[2]);
    }

    @Test
    public void functionGemmaAndMiniCpmFilesTakeTheRepositoryName() {
        String fg = TaiImportNames.displayName("https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions");
        assertEquals(fg + " · q8 · 1024-token context",
            TaiImportFacts.variantTitle(WORDS, fg, "mobile_actions_q8_ekv1024.litertlm"));
        String cpm = TaiImportNames.displayName("https://huggingface.co/litert-community/MiniCPM5-2B");
        assertEquals("MiniCPM5 2B · wi4c_wi8_afp32", TaiImportFacts.variantTitle(WORDS, cpm, "minicpm_wi4c_wi8_afp32.litertlm"));
        assertEquals("MiniCPM5 1B · wi4b32_wi8_afp32 · GPU build", TaiImportFacts.variantTitle(WORDS,
            TaiImportNames.displayName("https://huggingface.co/litert-community/MiniCPM5-1B"),
            "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"));
    }

    @Test
    public void fitIsStatedAsMeasurements() {
        assertEquals("3.7 GB file · this phone has 12 GB RAM, about 6.3 GB free now",
            TaiImportFacts.fitLine(WORDS, (long) (3.7 * GIB), 12L * GIB, (long) (6.3 * GIB)));
        assertEquals("3.7 GB file · this phone has 12 GB RAM",
            TaiImportFacts.fitLine(WORDS, (long) (3.7 * GIB), 12L * GIB, 0L));
        assertEquals("File size not listed · this phone's RAM could not be read",
            TaiImportFacts.fitLine(WORDS, -1L, 0L, 0L));
        assertTrue(TaiImportFacts.largerThanRam(13L * GIB, 12L * GIB));
        assertFalse(TaiImportFacts.largerThanRam(3L * GIB, 12L * GIB));
        assertFalse(TaiImportFacts.largerThanRam(-1L, 12L * GIB));
    }
}
