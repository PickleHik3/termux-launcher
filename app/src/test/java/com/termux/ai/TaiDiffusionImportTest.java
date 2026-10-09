package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Layout detection and file selection for text-to-image packages, from the two published repositories. */
public class TaiDiffusionImportTest {
    private static final String SHA = "0123456789012345678901234567890123456789";

    /** taobao-mnn/stable-diffusion-v1-5-mnn-opencl as the Hugging Face API lists it. */
    private static final List<String> SD = Arrays.asList(".DS_Store", ".gitattributes", "README.md", "alphas.txt",
        "merges.txt", "text_encoder.mnn", "text_encoder.mnn.weight", "unet.mnn", "unet.mnn.weight",
        "vae_decoder.mnn", "vae_decoder.mnn.weight", "vocab.json");

    /** taobao-mnn/MNN-Sana-Edit-V2. */
    private static final List<String> SANA = Arrays.asList(".gitattributes", "README.md", "config.json",
        "connector.mnn", "connector.mnn.weight", "llm/config.json", "llm/export_args.json", "llm/llm.mnn",
        "llm/llm.mnn.json", "llm/llm.mnn.weight", "llm/llm_config.json", "llm/meta_queries.mnn",
        "llm/tokenizer.txt", "projector.mnn", "projector.mnn.weight", "transformer.mnn",
        "transformer.mnn.weight", "vae_decoder.mnn", "vae_decoder.mnn.weight", "vae_encoder.mnn",
        "vae_encoder.mnn.weight");

    @Test
    public void bothLayoutsAreRecognised() {
        assertEquals(TaiDiffusionPackage.TYPE_SD15, TaiDiffusionImport.detectLayout(SD));
        assertEquals(TaiDiffusionPackage.TYPE_SANA, TaiDiffusionImport.detectLayout(SANA));
    }

    @Test
    public void anLlmPackageIsNotADiffusionPackage() {
        assertEquals(TaiDiffusionPackage.TYPE_AUTO, TaiDiffusionImport.detectLayout(
            Arrays.asList("config.json", "llm.mnn", "llm.mnn.weight", "tokenizer.txt", "llm_config.json")));
        assertEquals(TaiDiffusionPackage.TYPE_AUTO, TaiDiffusionImport.detectLayout(Arrays.asList("config.json")));
    }

    @Test
    public void aPartialPackageStillHasItsLayoutSoTheMissingFilesCanBeNamed() {
        assertEquals(TaiDiffusionPackage.TYPE_SD15, TaiDiffusionImport.detectLayout(Arrays.asList("unet.mnn")));
        assertEquals(TaiDiffusionPackage.TYPE_SANA, TaiDiffusionImport.detectLayout(Arrays.asList("connector.mnn", "config.json")));
    }

    @Test
    public void taiyiIsToldApartFromTheNameOnly() {
        assertEquals(TaiDiffusionPackage.TYPE_TAIYI, TaiDiffusionImport.typeFor(TaiDiffusionPackage.TYPE_SD15, "taobao-mnn/Taiyi-SD-MNN"));
        assertEquals(TaiDiffusionPackage.TYPE_SD15, TaiDiffusionImport.typeFor(TaiDiffusionPackage.TYPE_SD15, "taobao-mnn/stable-diffusion-v1-5-mnn-opencl"));
        assertEquals(TaiDiffusionPackage.TYPE_SANA, TaiDiffusionImport.typeFor(TaiDiffusionPackage.TYPE_SANA, "taiyi"));
    }

    @Test
    public void junkIsSkippedAndTheLlmFolderIsKept() {
        List<String> sd = TaiDiffusionImport.packageFiles(SD, "");
        assertFalse(sd.contains(".DS_Store"));
        assertFalse(sd.contains(".gitattributes"));
        assertFalse(sd.contains("README.md"));
        assertTrue(sd.contains("vocab.json"));
        assertTrue(sd.contains("merges.txt"));
        assertTrue(sd.contains("unet.mnn.weight"));
        assertEquals(9, sd.size());

        List<String> sana = TaiDiffusionImport.packageFiles(SANA, "");
        assertTrue(sana.contains("llm/llm.mnn"));
        assertTrue(sana.contains("llm/llm.mnn.weight"));
        assertTrue(sana.contains("llm/tokenizer.txt"));
        assertTrue(sana.contains("llm/meta_queries.mnn"));
        assertTrue(sana.contains("config.json"));
        assertFalse(sana.contains("README.md"));
        assertFalse(sana.contains(".gitattributes"));
    }

    @Test
    public void smallFilesComeBeforeGraphsSoTheTokenizerIsCheckedFirst() {
        List<String> sd = TaiDiffusionImport.packageFiles(SD, "");
        int firstHeavy = -1;
        int lastLight = -1;
        for (int i = 0; i < sd.size(); i++) {
            if (TaiDiffusionImport.isHeavy(sd.get(i))) { if (firstHeavy < 0) firstHeavy = i; }
            else lastLight = i;
        }
        assertTrue(lastLight < firstHeavy);
        assertEquals("alphas.txt", TaiDiffusionImport.entryFile(SD, ""));
        assertEquals("config.json", TaiDiffusionImport.entryFile(SANA, ""));
    }

    @Test
    public void aSubdirectoryPackageListsOnlyItsOwnFiles() {
        List<String> files = Arrays.asList("top.json", "sd/unet.mnn", "sd/text_encoder.mnn", "sd/vocab.json", "sd/README.md");
        assertEquals(Arrays.asList("sd/"), TaiDiffusionImport.packageDirectories(files));
        assertEquals(Arrays.asList("vocab.json", "text_encoder.mnn", "unet.mnn"), TaiDiffusionImport.packageFiles(files, "sd/"));
    }

    @Test
    public void nestedFoldersBelongToThePackageAbove() {
        assertEquals(Arrays.asList(""), TaiDiffusionImport.packageDirectories(SANA));
        assertTrue(TaiDiffusionImport.insidePackage(TaiDiffusionImport.packageDirectories(SANA), "llm/"));
        assertFalse(TaiDiffusionImport.insidePackage(TaiDiffusionImport.packageDirectories(Arrays.asList("config.json", "llm.mnn")), ""));
    }

    @Test
    public void theSpecIsAnImageModelWithItsTypeRecorded() throws Exception {
        TaiModelSpec spec = TaiDiffusionImport.spec("sana", "Sana", "imported", "x", "/data/models/sana",
            TaiDiffusionPackage.TYPE_SANA, 123L);
        assertEquals(TaiModelSpec.BACKEND_MNN_DIFFUSION, spec.backend);
        assertEquals(TaiModelSpec.FORMAT_MNN, spec.format);
        assertEquals("sana", spec.architecture);
        assertEquals("/data/models/sana", spec.localPath);
        assertEquals(123L, spec.sizeBytes);
        assertTrue(spec.isImageGeneration());
        assertTrue(spec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION));
        assertEquals(TaiDiffusionPackage.TYPE_SANA, TaiDiffusionPackage.parseType(spec.architecture));
        TaiModelSpec back = TaiModelSpec.fromJson(spec.toJson());
        assertTrue(back.isImageGeneration());
        assertEquals("sana", back.architecture);
    }

    // ---- the Hugging Face listing ----

    private static JSONObject metadata(List<String> files, long size) throws Exception {
        JSONArray siblings = new JSONArray();
        for (String file : files) siblings.put(new JSONObject().put("rfilename", file).put("size", size));
        return new JSONObject().put("sha", SHA).put("siblings", siblings);
    }

    @Test
    public void stableDiffusionRepositoryIsOneImageCandidateWithThePackageSize() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/taobao-mnn/stable-diffusion-v1-5-mnn-opencl");
        JSONArray result = source.candidates(metadata(SD, 100L));
        assertEquals(1, result.length());
        JSONObject candidate = result.getJSONObject(0);
        assertEquals("sd15", candidate.getString("diffusion"));
        assertEquals("alphas.txt", candidate.getString("file"));
        assertEquals(900L, candidate.getLong("sizeBytes"));
        assertTrue(candidate.getString("url").contains("/resolve/" + SHA + "/"));
    }

    @Test
    public void sanaRepositoryIsOneImageCandidateAndNeitherConfigIsAChatModel() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/taobao-mnn/MNN-Sana-Edit-V2");
        JSONArray result = source.candidates(metadata(SANA, 10L));
        assertEquals(1, result.length());
        JSONObject candidate = result.getJSONObject(0);
        assertEquals("sana", candidate.getString("diffusion"));
        assertEquals("config.json", candidate.getString("file"));
        // Every runnable file counts, llm/ included; README and .gitattributes do not.
        assertEquals(10L * 19, candidate.getLong("sizeBytes"));
    }

    @Test
    public void taiyiRepositoryNameSelectsTheTaiyiType() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/taobao-mnn/Taiyi-Stable-Diffusion-MNN");
        assertEquals("taiyi", source.candidates(metadata(SD, 1L)).getJSONObject(0).getString("diffusion"));
    }

    @Test
    public void chatRepositoriesAreUnchanged() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/taobao-mnn/Qwen3-0.6B-MNN");
        JSONArray result = source.candidates(metadata(Arrays.asList("config.json", "llm.mnn", "llm.mnn.weight", "tokenizer.txt"), 5L));
        assertEquals(1, result.length());
        assertFalse(result.getJSONObject(0).has("diffusion"));
        assertEquals("config.json", result.getJSONObject(0).getString("file"));
    }
}
