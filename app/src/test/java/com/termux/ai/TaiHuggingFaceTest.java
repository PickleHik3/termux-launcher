package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaiHuggingFaceTest {
    private static final String SHA = "0123456789012345678901234567890123456789";
    private JSONObject metadata(String... files) throws Exception {
        JSONArray siblings = new JSONArray();
        for (String file : files) siblings.put(new JSONObject().put("rfilename", file).put("size", 20));
        return new JSONObject().put("sha", SHA).put("siblings", siblings);
    }

    @Test public void preservesRevisionAndNestedBlobInsteadOfGuessingAnotherFile() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/org/model/blob/release%2Fv1/sub/vision.litertlm?download=true");
        assertEquals("release/v1", source.revision);
        assertTrue(source.metadataUrl().contains("release%2Fv1"));
        JSONArray result = source.candidates(metadata("text.litertlm", "sub/vision.litertlm"));
        assertEquals(1, result.length());
        assertEquals("https://huggingface.co/org/model/resolve/" + SHA + "/sub/vision.litertlm", result.getJSONObject(0).getString("url"));
    }

    @Test public void offersEveryArtifactWithSizes() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/org/model");
        JSONArray result = source.candidates(metadata("text.litertlm", "vision.litertlm", "README.md"));
        assertEquals(2, result.length());
        assertEquals(20, result.getJSONObject(0).getLong("sizeBytes"));
    }

    @Test public void modelFactsCopyOnlyWhatTheRepositoryDeclares() throws Exception {
        // Shaped like litert-community/gemma-4-E2B-it-litert-lm: no pipeline tag, base_model a list.
        JSONObject metadata = metadata("gemma-4-E2B-it.litertlm", "README.md")
            .put("tags", new JSONArray().put("litert-lm").put("license:apache-2.0"))
            .put("cardData", new JSONObject().put("license", "apache-2.0")
                .put("base_model", new JSONArray().put("google/gemma-4-E2B-it")));
        JSONObject facts = TaiHuggingFace.modelFacts(metadata);
        assertFalse(facts.has("pipelineTag"));
        assertEquals("apache-2.0", facts.getString("license"));
        assertEquals("google/gemma-4-E2B-it", facts.getString("baseModel"));
        assertEquals(2, facts.getJSONArray("tags").length());
        assertTrue(TaiHuggingFace.hasReadme(metadata));
        assertFalse(TaiHuggingFace.hasReadme(metadata("model.litertlm")));

        // Shaped like litert-community/granite-4.0-h-350m: the pipeline tag is declared.
        JSONObject granite = metadata("granite-4.0-h-350m_int8.litertlm").put("pipeline_tag", "text-generation");
        assertEquals("text-generation", TaiHuggingFace.modelFacts(granite).getString("pipelineTag"));

        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/litert-community/granite-4.0-h-350m");
        assertEquals("https://huggingface.co/litert-community/granite-4.0-h-350m/resolve/" + SHA + "/README.md",
            source.readmeUrl(SHA));
    }

    @Test public void emptyRepoAndConfigWithoutGraphAreNotInstallable() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/org/model");
        assertEquals(0, source.candidates(metadata(".gitattributes", "config.json")).length());
        assertEquals(1, source.candidates(metadata("quant/config.json", "quant/llm.mnn")).length());
    }

    @Test public void marksAnEagleMnnPackageAndSumsItsFilesInsteadOfConfigJsonAlone() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-Eagle3-MNN");
        JSONArray result = source.candidates(metadata("config.json", "llm.mnn", "llm.mnn.weight",
            "eagle.mnn", "eagle.mnn.weight", "eagle_fc.mnn", "eagle_fc.mnn.weight", "eagle_d2t.mnn", "tokenizer.txt"));
        assertEquals(1, result.length());
        JSONObject candidate = result.getJSONObject(0);
        assertEquals("eagle", candidate.getString("speculative"));
        // Nine sibling files at 20 bytes each ("size" in metadata()), not config.json's own 20.
        assertEquals(180, candidate.getLong("sizeBytes"));
    }

    @Test public void plainMnnPackageIsNotMarkedSpeculative() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/org/model");
        JSONArray result = source.candidates(metadata("config.json", "llm.mnn", "llm.mnn.weight"));
        assertFalse(result.getJSONObject(0).has("speculative"));
    }

    @Test public void rejectsTraversalAndWrongHosts() {
        assertNull(TaiHuggingFace.parse("https://huggingface.co.evil/org/model"));
        assertNull(TaiHuggingFace.parse("https://huggingface.co/org/model/resolve/main/%2E%2E/config.json"));
        assertNull(TaiHuggingFace.parse("https://user@huggingface.co/org/model"));
    }

    @Test public void runtimeVersionsCompareNumerically() {
        assertFalse(TaiArtifactCompatibility.versionAtLeast("0.14.0", "0.15.0"));
        assertTrue(TaiArtifactCompatibility.versionAtLeast("0.15.1", "0.15"));
        assertTrue(TaiArtifactCompatibility.versionAtLeast("0.100.0", "0.15.0"));
        assertFalse(TaiArtifactCompatibility.versionAtLeast("unknown", "0.15.0"));
    }

    @Test public void resumeMustStartAtRequestedOffset() {
        assertTrue(TaiModelDownloader.validContentRange("bytes 512-1023/1024", 512));
        assertFalse(TaiModelDownloader.validContentRange("bytes 0-1023/1024", 512));
        assertFalse(TaiModelDownloader.validContentRange(null, 512));
    }
}
