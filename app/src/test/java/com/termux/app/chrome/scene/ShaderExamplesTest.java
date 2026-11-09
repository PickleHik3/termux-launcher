package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** The bundled examples: the index, and every shader through the same prelude the app builds. */
@RunWith(RobolectricTestRunner.class)
public class ShaderExamplesTest {

    private static final File DIR = new File("src/main/assets/" + ShaderExamples.DIR);

    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(new File(DIR, name).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void theIndexListsFiveExamplesAtHalfAndThirty() throws Exception {
        List<ShaderExamples.Example> all = ShaderExamples.parseIndex(read("index.json"));
        assertEquals(5, all.size());
        for (ShaderExamples.Example e : all) {
            assertEquals(ShaderSafety.Quality.HALF, e.quality);
            assertEquals(30, e.rateHz);
            assertFalse(e.name.isEmpty());
        }
    }

    @Test
    public void everyExampleBuildsAsAnEs3MainImageShaderWithAMain() throws Exception {
        for (ShaderExamples.Example e : ShaderExamples.parseIndex(read("index.json"))) {
            String src = read(e.slug + ".glsl");
            assertTrue(e.slug + " defines mainImage", src.contains("void mainImage("));
            assertFalse(e.slug + " has no #define", src.contains("#define"));
            assertTrue(e.slug + " is under 60 lines", src.split("\n", -1).length <= 61);
            ShaderPrelude.Built b = ShaderPrelude.build(src);
            assertEquals(e.slug, ShaderPrelude.Flavour.ES3_MAIN_IMAGE, b.flavour);
            assertTrue(e.slug + " has a main", b.source.contains("void main"));
        }
    }

    @Test
    public void parseIndexSkipsUnsafeEntriesAndBadJson() {
        assertEquals(0, ShaderExamples.parseIndex(null).size());
        assertEquals(0, ShaderExamples.parseIndex("not json").size());
        List<ShaderExamples.Example> got = ShaderExamples.parseIndex(
            "[{\"slug\":\"../x\",\"name\":\"Bad\"},{\"slug\":\"ok\",\"name\":\"Ok\",\"rate\":60,\"quality\":\"full\"},"
                + "{\"slug\":\"noname\"}]");
        assertEquals(1, got.size());
        assertEquals("Ok", got.get(0).name);
        assertEquals(60, got.get(0).rateHz);
        assertEquals(ShaderSafety.Quality.FULL, got.get(0).quality);
    }
}
