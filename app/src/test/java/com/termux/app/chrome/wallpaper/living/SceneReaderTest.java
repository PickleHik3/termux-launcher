package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

/** The v2 director: real Gallery answers, validation, request body, model choice and the round trip. */
public class SceneReaderTest {

    private static String fixture(String name) throws IOException {
        try (InputStream in = SceneReaderTest.class.getResourceAsStream("/sceneplan/" + name)) {
            assertNotNull(name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String two(String extra) {
        return "{\"elements\":[{\"name\":\"a\",\"kind\":\"sky\",\"box\":[0,0,500,1000],\"depth\":\"far\",\"motion\":\"drift\"},"
            + "{\"name\":\"b\",\"kind\":\"grass\",\"box\":[500,0,1000,1000],\"depth\":\"near\",\"motion\":\"sway\"}" + extra
            + "],\"scene\":{\"time\":\"day\",\"weather\":\"clear\",\"light_direction\":\"\",\"particles\":\"none\","
            + "\"mood\":\"calm\",\"do_not_animate\":[\"A\"]}}";
    }

    @Test
    public void e4bRound1DropsParticlesElement() throws Exception {
        ScenePlan p = SceneReader.parse(fixture("e4b-round1.json"));
        assertNotNull(p);
        assertEquals(8, p.elements.size());
        assertFalse(p.has("particles"));
        assertTrue(p.has("figure"));
        assertEquals(3, p.stillNames().size());
        assertTrue(p.stillNames().contains("dark pine trees"));
        assertEquals("petals", p.scene.particles);
        assertEquals("dusk", p.scene.time);
        assertArrayEquals(new int[] {590, 460, 700, 530}, p.elementsOfKind("figure").get(0).box);
    }

    @Test
    public void e4bRound2SpeculativeParses() throws Exception {
        ScenePlan p = SceneReader.parse(fixture("e4b-round2-speculative.json"));
        assertNotNull(p);
        assertEquals(8, p.elements.size());
        assertEquals("cloudy", p.scene.weather);
        assertEquals(1, p.stillNames().size());
    }

    @Test
    public void unmatchedDoNotAnimateNamesAreIgnored() throws Exception {
        ScenePlan p = SceneReader.parse(fixture("e2b-round1.json"));
        assertNotNull(p);
        assertEquals(5, p.elements.size());
        assertTrue(p.stillNames().isEmpty());
        assertEquals(1, p.scene.doNotAnimate.size());
    }

    @Test
    public void e2bThinkingKeepsOnlyValidBoxes() throws Exception {
        ScenePlan p = SceneReader.parse(fixture("e2b-thinking-broken.txt"));
        assertNotNull(p);
        assertEquals(4, p.elements.size());
        assertFalse(p.has("figure"));
        assertTrue(p.has("sky"));
        assertEquals("none", p.scene.particles);
    }

    @Test
    public void e4bThinkingIsRejected() throws Exception {
        assertNull(SceneReader.parse(fixture("e4b-thinking-broken.txt")));
    }

    @Test
    public void stringNumbersCountAndBadBoxesDrop() {
        String extra = ",{\"name\":\"c\",\"kind\":\"water\",\"box\":[\"450\",\"10\",\"600.4\",\"900\"],\"depth\":\"x\",\"motion\":\"ripple\"}"
            + ",{\"name\":\"d\",\"kind\":\"water\",\"box\":[600,10,500,900],\"depth\":\"far\",\"motion\":\"ripple\"}"
            + ",{\"name\":\"e\",\"kind\":\"water\",\"box\":[0,10,500,1001],\"depth\":\"far\",\"motion\":\"ripple\"}"
            + ",{\"name\":\"f\",\"kind\":\"water\",\"box\":[0,10,500],\"depth\":\"far\",\"motion\":\"ripple\"}"
            + ",{\"name\":\"g\",\"kind\":\"lava\",\"box\":[0,10,500,900],\"depth\":\"far\",\"motion\":\"ripple\"}"
            + ",{\"name\":\"h\",\"kind\":\"water\",\"box\":[0,10,500,900],\"depth\":\"far\",\"motion\":\"explode\"}";
        ScenePlan p = SceneReader.parse(two(extra));
        assertNotNull(p);
        assertEquals(3, p.elements.size());
        ScenePlan.Element c = p.elements.get(2);
        assertArrayEquals(new int[] {450, 10, 600, 900}, c.box);
        assertEquals("middle", c.depth);
        assertTrue(p.elements.get(0).still);
        assertFalse(p.elements.get(1).still);
    }

    @Test
    public void rejectsFewElementsOrMissingScene() {
        assertNull(SceneReader.parse(null));
        assertNull(SceneReader.parse("nothing"));
        assertNull(SceneReader.parse("{\"elements\":[{\"name\":\"a\",\"kind\":\"sky\",\"box\":[0,0,5,5],\"motion\":\"none\"}],\"scene\":{}}"));
        assertNull(SceneReader.parse(two("").replace("\"scene\"", "\"other\"")));
    }

    @Test
    public void unknownTimeWeatherAndParticlesAreTolerated() {
        ScenePlan p = SceneReader.parse(two("").replace("\"day\"", "\"noon\"").replace("\"clear\"", "\"hail\"")
            .replace("\"none\",\"mood", "\"confetti\",\"mood"));
        assertNotNull(p);
        assertNull(p.scene.time);
        assertNull(p.scene.weather);
        assertEquals("none", p.scene.particles);
    }

    @Test
    public void roundTrip() throws Exception {
        ScenePlan p = SceneReader.parse(fixture("e4b-round1.json"));
        JSONObject json = p.toJson();
        assertFalse(json.toString().contains("\"note\""));
        ScenePlan q = ScenePlan.fromJson(json);
        assertNotNull(q);
        assertEquals(json.toString(), q.toJson().toString());
        assertEquals(p.elements.size(), q.elements.size());
        assertEquals(p.stillNames(), q.stillNames());
        assertEquals(p.elementsOfKind("trees").size(), q.elementsOfKind("trees").size());
    }

    @Test
    public void requestShape() throws Exception {
        JSONObject o = new JSONObject(SceneReader.buildRequest("data:image/jpeg;base64,AA"));
        assertEquals("gemma-4-e4b-it-litert-lm-vision", o.getString("model"));
        assertEquals(2, o.getJSONArray("messages").getJSONObject(0).getJSONArray("content").length());
        assertEquals(0, o.getInt("temperature"));
        assertEquals(700, o.getInt("max_tokens"));
        assertFalse(o.getBoolean("stream"));
        assertEquals(2048, o.getInt("context_window"));
        assertEquals("momentary", o.getString("load_class"));
        assertTrue(o.getBoolean("speculative_decoding"));
        assertFalse(o.getBoolean("thinking"));
        assertFalse(SceneReader.PROMPT.contains("note"));
    }

    @Test
    public void theRequestCarriesTheResolvedAcceleratorOnlyForALocalModel() throws Exception {
        JSONObject local = new JSONObject(SceneReader.buildRequest("gemma-4-e2b-it-litert-lm-vision", "cpu", "data:image/jpeg;base64,AA"));
        assertEquals("gemma-4-e2b-it-litert-lm-vision", local.getString("model"));
        assertEquals("cpu", local.getString("accelerator"));
        JSONObject remote = new JSONObject(SceneReader.buildRequest("remote/vision-1", "gpu", "data:image/jpeg;base64,AA"));
        assertEquals("remote/vision-1", remote.getString("model"));
        assertFalse(remote.has("accelerator"));
        assertFalse(new JSONObject(SceneReader.buildRequest("data:image/jpeg;base64,AA")).has("accelerator"));
    }

    @Test
    public void failuresGiveNull() throws Exception {
        SceneReader.Chat boom = (body, timeout) -> { throw new IOException("timeout"); };
        assertNull(SceneReader.read(boom, "x"));
        SceneReader.Chat junk = (body, timeout) -> "I cannot see";
        assertNull(SceneReader.read(junk, "x"));
        SceneReader.Chat crash = (body, timeout) -> { throw new IllegalStateException(); };
        assertNull(SceneReader.read(crash, "x"));
        String good = fixture("e4b-round2-speculative.json");
        SceneReader.Chat ok = (body, timeout) -> {
            assertEquals(SceneReader.TIMEOUT_MS, timeout);
            return good;
        };
        assertNotNull(SceneReader.read(ok, "x"));
    }
}
