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

    @Test public void emptyRepoAndConfigWithoutGraphAreNotInstallable() throws Exception {
        TaiHuggingFace source = TaiHuggingFace.parse("https://huggingface.co/org/model");
        assertEquals(0, source.candidates(metadata(".gitattributes", "config.json")).length());
        assertEquals(1, source.candidates(metadata("quant/config.json", "quant/llm.mnn")).length());
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
