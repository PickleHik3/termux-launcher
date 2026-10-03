package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

/** Strict validation of Gemma's answer (good, bad, partial), the request body, and the fallback on failure. */
public class GemmaSceneReaderTest {

    private static final String GOOD = "{\"style\":\"illustration\",\"regions\":{\"water\":[3,4],\"falling_water\":[],"
        + "\"sky\":[1],\"foliage\":[5],\"lights\":[],\"subject\":[2]},\"water_style\":\"lake\",\"sky_motion\":\"clouds\","
        + "\"light_style\":\"none\",\"trail_angle_deg\":null,\"particles\":\"glints\",\"intensity\":1.2}";

    @Test
    public void goodAnswerParses() {
        GemmaSceneReader.Plan p = GemmaSceneReader.parse(GOOD, 10);
        assertNotNull(p);
        assertEquals("illustration", p.style);
        assertArrayEquals(new int[] {3, 4}, p.regions.get("water"));
        assertNull(p.regions.get("falling_water"));
        assertEquals("lake", p.waterStyle);
        assertEquals("glints", p.particles);
        assertNull(p.trailAngleDeg);
        assertEquals(1.2f, p.intensity, 1e-6f);
    }

    @Test
    public void fencedAnswerWithProseParses() {
        assertNotNull(GemmaSceneReader.parse("Here you go:\n```json\n" + GOOD + "\n```", 10));
    }

    @Test
    public void partialAnswerKeepsWhatItHas() {
        GemmaSceneReader.Plan p = GemmaSceneReader.parse(
            "{\"style\":\"photo\",\"regions\":{\"sky\":[2]},\"water_style\":\"pool\"}", 10);
        assertNotNull(p);
        assertEquals("pool", p.waterStyle);
        assertNull(p.skyMotion);
        assertNull(p.particles);
        assertNull(p.intensity);
        assertArrayEquals(new int[] {2}, p.regions.get("sky"));
    }

    @Test
    public void badAnswersAreRejected() {
        assertNull(GemmaSceneReader.parse(null, 10));
        assertNull(GemmaSceneReader.parse("no json here", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"painting\",\"regions\":{}}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\"}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\",\"regions\":{\"sky\":[\"a\"]}}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\",\"regions\":{\"sky\":1}}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\",\"regions\":{},\"water_style\":\"ocean\"}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\",\"regions\":{},\"intensity\":3}", 10));
        assertNull(GemmaSceneReader.parse("{\"style\":\"photo\",\"regions\":{},\"trail_angle_deg\":\"left\"}", 10));
    }

    @Test
    public void marksPastTheRegionsAreDropped() {
        GemmaSceneReader.Plan p = GemmaSceneReader.parse(
            "{\"style\":\"photo\",\"regions\":{\"sky\":[0,4,11],\"water\":[12]}}", 10);
        assertNotNull(p);
        assertArrayEquals(new int[] {4}, p.regions.get("sky"));
        assertNull(p.regions.get("water"));
    }

    @Test
    public void duplicateMarksCollapse() {
        GemmaSceneReader.Plan p = GemmaSceneReader.parse(
            "{\"style\":\"photo\",\"regions\":{\"water\":[2,2,3]}}", 10);
        assertNotNull(p);
        assertArrayEquals(new int[] {2, 3}, p.regions.get("water"));
    }

    @Test
    public void requestCarriesVisionModelAndBothImages() throws Exception {
        String body = GemmaSceneReader.buildRequest("hi", "data:image/jpeg;base64,AA", "data:image/jpeg;base64,BB");
        JSONObject o = new JSONObject(body);
        assertEquals("gemma-4-e4b-it-litert-lm-vision", o.getString("model"));
        assertEquals(3, o.getJSONArray("messages").getJSONObject(0).getJSONArray("content").length());
    }

    @Test
    public void promptNamesEveryMarkAndTheSchema() {
        int w = 20, h = 10;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = i % w < 10 ? 0xFF2040C0 : 0xFFE0D040;
        ColourClusters.Result c = ColourClusters.compute(px, w, h, 2, 1L);
        String prompt = GemmaSceneReader.buildPrompt(c);
        assertTrue(prompt.contains("1: #"));
        assertTrue(prompt.contains("2: #"));
        assertTrue(prompt.contains("\"falling_water\""));
    }

    @Test
    public void failuresFallBackToNull() {
        ColourClusters.Result c = ColourClusters.compute(new int[] {0xFF000000, 0xFFFFFFFF}, 2, 1, 2, 1L);
        GemmaSceneReader.Chat boom = (body, timeout) -> { throw new IOException("timeout"); };
        assertNull(GemmaSceneReader.read(boom, "a", "b", c));
        GemmaSceneReader.Chat junk = (body, timeout) -> "I cannot see";
        assertNull(GemmaSceneReader.read(junk, "a", "b", c));
        GemmaSceneReader.Chat ok = (body, timeout) -> {
            assertEquals(GemmaSceneReader.TIMEOUT_MS, timeout);
            return GOOD.replace("[5]", "[2]");
        };
        assertNotNull(GemmaSceneReader.read(ok, "a", "b", c));
    }
}
