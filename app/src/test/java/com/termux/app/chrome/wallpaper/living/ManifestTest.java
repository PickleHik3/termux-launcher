package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Recipe JSON round trip, version check, manifest folder contract, photo hash, analysis parsers. */
public class ManifestTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static LivingRecipe sample() {
        LivingRecipe r = new LivingRecipe();
        r.drift = 0.8f;
        r.swaySpeed = 1f;
        r.swayAmp = 0.005f;
        r.waterMode = LivingRecipe.WATER_LAKE;
        r.waterParams = RecipeRules.waterParams(LivingRecipe.WATER_LAKE);
        r.skyFlow = 0.035f;
        r.skyStars = true;
        r.pour = 1f;
        r.glowMode = LivingRecipe.GLOW_TRAILS;
        r.glowGain = 1.2f;
        r.glowTrailAngleDeg = 135f;
        r.mistColour = new float[] {0.25f, 0.5f, 0.75f};
        r.mistAmount = 0.4f;
        r.particles = LivingRecipe.PARTICLES_FIREFLIES;
        r.intensity = 1.25f;
        r.gemma = true;
        r.gemmaModel = "gemma-4-e4b-it-litert-lm-vision";
        r.models.put("depth", "depth-anything-3-small");
        r.timingsMs.put("total", 1234L);
        return r;
    }

    @Test
    public void backendFieldsRoundTripWhenPresentAndAreOptional() throws Exception {
        LivingRecipe a = sample();
        a.gemmaAccelerator = "gpu";
        a.gemmaFallbackReason = "history_failure: Model load cancelled.";
        LivingRecipe b = LivingRecipe.fromJson(a.toJson().toString());
        assertEquals("gpu", b.gemmaAccelerator);
        assertEquals("history_failure: Model load cancelled.", b.gemmaFallbackReason);

        LivingRecipe plain = LivingRecipe.fromJson(sample().toJson().toString());
        assertNull(plain.gemmaAccelerator);
        assertNull(plain.gemmaFallbackReason);
        assertTrue(!sample().toJson().has("gemmaAccelerator"));
    }

    @Test
    public void recipeRoundTrips() throws Exception {
        LivingRecipe a = sample();
        LivingRecipe b = LivingRecipe.fromJson(a.toJson().toString());
        assertEquals(a.toJson().toString(), b.toJson().toString());
        assertEquals(LivingRecipe.WATER_LAKE, b.waterMode);
        assertEquals(4.5e-3f, b.waterParams.get("ampX"), 1e-7f);
        assertArrayEquals(a.mistColour, b.mistColour, 1e-6f);
        assertTrue(b.gemma);
        assertEquals(1234L, (long) b.timingsMs.get("total"));
    }

    @Test
    public void unknownVersionIsRefused() throws Exception {
        JSONObject o = sample().toJson();
        o.put("version", 99);
        try {
            LivingRecipe.fromJson(o.toString());
            fail();
        } catch (JSONException expected) {
            // refused
        }
    }

    private File fakeFolder(boolean withRecipe) throws Exception {
        File dir = tmp.newFolder("abc" + (withRecipe ? "1" : "0"));
        for (String n : new String[] {Manifest.IMAGE, Manifest.DEPTH, Manifest.MASK_A, Manifest.MASK_B, Manifest.MASK_C}) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, n))) {
                out.write(1);
            }
        }
        if (withRecipe) Manifest.writeRecipe(dir, sample());
        return dir;
    }

    @Test
    public void manifestLoadsWhenComplete() throws Exception {
        File dir = fakeFolder(true);
        Manifest m = Manifest.load(dir);
        assertNotNull(m);
        assertEquals(dir.getName(), m.hash());
        assertEquals("living:" + dir.getName(), m.wallpaperId());
        assertEquals(new File(dir, "maskB.png"), m.maskB());
        assertEquals(LivingRecipe.PARTICLES_FIREFLIES, m.recipe().particles);
    }

    @Test
    public void manifestWithoutRecipeOrAFileIsNull() throws Exception {
        assertNull(Manifest.load(fakeFolder(false)));
        File dir = fakeFolder(true);
        assertTrue(new File(dir, Manifest.MASK_C).delete());
        assertNull(Manifest.load(dir));
    }

    @Test
    public void hashIsFirstSixteenHexOfSha256() throws IOException {
        File f = tmp.newFile("p.bin");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("ba7816bf8f01cfea", LivingStills.hash16(f));
    }

    @Test
    public void groupNamesComeFromSceneJson() {
        List<String> a = AnalysisMaps.parseGroupNames(
            "{\"groups\":[\"water\",\"falling_water\",\"sky\",\"foliage\",\"lights\",\"signs\",\"subject_like\"]}");
        assertEquals(7, a.size());
        assertEquals("foliage", a.get(3));
        assertEquals(Arrays.asList("a", "b"), AnalysisMaps.parseGroupNames("[\"a\",\"b\"]"));
        assertEquals(6, AnalysisMaps.parseGroupNames(
            "{\"scene0\":[\"x\",\"y\",\"z\"],\"scene1\":[\"p\",\"q\",\"r\"]}").size());
        assertTrue(AnalysisMaps.parseGroupNames("not json").isEmpty());
    }

    @Test
    public void groupNamesFollowTheAnalysisJobLayout() {
        // What WallpaperVisionRuntime writes: classes is a count, groups maps names to class ids,
        // and images names each file's channels, with null for an unused channel.
        String json = "{\"width\":128,\"height\":128,\"classes\":150,"
            + "\"images\":[{\"file\":\"scene0.png\",\"channels\":[\"water\",\"falling_water\",\"sky\"]},"
            + "{\"file\":\"scene1.png\",\"channels\":[\"foliage\",\"lights\",\"signs\"]},"
            + "{\"file\":\"scene2.png\",\"channels\":[\"subject_like\",null,null]}],"
            + "\"groups\":{\"water\":[21,26],\"sky\":[2]}}";
        List<String> names = AnalysisMaps.parseGroupNames(json);
        assertEquals(Arrays.asList("water", "falling_water", "sky", "foliage", "lights", "signs",
            "subject_like", "", ""), names);
    }

    @Test
    public void modelIdsComeFromAnalysisJson() {
        assertEquals("u2net", AnalysisMaps.parseModels("{\"models\":{\"subject\":\"u2net\"}}").get("subject"));
        assertTrue(AnalysisMaps.parseModels("{}").isEmpty());
    }
}
