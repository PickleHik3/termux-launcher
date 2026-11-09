package com.termux.ai;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Input shapes rewritten inside the app's own Silero VAD graph (a real flatbuffer) and re-read. */
public class TfliteShapePatcherTest {
    private static File silero() {
        File file = new File("src/main/assets/vad/silero_vad_v5.tflite");
        if (!file.isFile()) file = new File("app/src/main/assets/vad/silero_vad_v5.tflite");
        if (!file.isFile()) throw new AssertionError("silero graph not found from " + new File(".").getAbsolutePath());
        return file;
    }

    private static byte[] bytes() throws IOException {
        return Files.readAllBytes(silero().toPath());
    }

    @Test
    public void rewrittenShapesReadBack() throws Exception {
        byte[] original = bytes();
        Map<String, int[]> declared = TfliteShapePatcher.inputShapes(ByteBuffer.wrap(original));
        Map<String, int[]> wanted = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : declared.entrySet()) {
            int[] shape = new int[e.getValue().length];
            Arrays.fill(shape, 7);
            wanted.put(e.getKey(), shape);
        }
        byte[] copy = original.clone();
        TfliteShapePatcher.patch(ByteBuffer.wrap(copy), wanted);
        Map<String, int[]> read = TfliteShapePatcher.inputShapes(ByteBuffer.wrap(copy));
        assertEquals(declared.keySet(), read.keySet());
        for (String name : declared.keySet()) assertArrayEquals(name, wanted.get(name), read.get(name));
        assertEquals(original.length, copy.length);
        // The source bytes are unchanged.
        Map<String, int[]> again = TfliteShapePatcher.inputShapes(ByteBuffer.wrap(original));
        for (String name : declared.keySet()) assertArrayEquals(name, declared.get(name), again.get(name));
    }

    @Test
    public void readCopyIsPatchedAndTheFileIsNot() throws Exception {
        File file = silero();
        Map<String, int[]> declared = TfliteShapePatcher.inputShapes(ByteBuffer.wrap(bytes()));
        String name = declared.keySet().iterator().next();
        int[] shape = new int[declared.get(name).length];
        Arrays.fill(shape, 3);
        Map<String, int[]> wanted = new LinkedHashMap<>();
        wanted.put(name, shape);
        ByteBuffer mapped = TfliteShapePatcher.readPatched(file, wanted);
        assertArrayEquals(shape, TfliteShapePatcher.inputShapes(mapped).get(name));
        assertArrayEquals(declared.get(name), TfliteShapePatcher.inputShapes(ByteBuffer.wrap(bytes())).get(name));
    }

    @Test
    public void wrongRankAndUnknownInputsAreRejected() throws Exception {
        ByteBuffer model = ByteBuffer.wrap(bytes());
        Map<String, int[]> declared = TfliteShapePatcher.inputShapes(model);
        String name = declared.keySet().iterator().next();
        Map<String, int[]> wrongRank = new LinkedHashMap<>();
        wrongRank.put(name, new int[declared.get(name).length + 1]);
        expectIo(model, wrongRank);
        Map<String, int[]> unknown = new LinkedHashMap<>();
        unknown.put("no_such_input", new int[] {1});
        expectIo(model, unknown);
    }

    @Test
    public void malformedBytesAreAnIoException() {
        expectIo(ByteBuffer.wrap(new byte[] {1, 2, 3}), new LinkedHashMap<>());
        byte[] junk = new byte[64];
        junk[0] = 100;
        expectIo(ByteBuffer.wrap(junk), new LinkedHashMap<>());
    }

    @Test
    public void canonicalNamesDropTheServingPrefixAndTensorIndex() {
        assertEquals("asr", TfliteShapePatcher.canonical("serving_default_asr:0"));
        assertEquals("style", TfliteShapePatcher.canonical("style"));
    }

    @Test
    public void vocoderOpenShapesFollowTheFramePipeline() {
        Map<String, int[]> shapes = KittenTtsRuntime.vocoderOpenShapes();
        int t = KittenTtsRuntime.VOCODER_OPEN_FRAMES;
        assertArrayEquals(new int[] {1, t, 128}, shapes.get("asr"));
        assertArrayEquals(new int[] {1, 2 * t}, shapes.get("f0"));
        assertArrayEquals(new int[] {1, 2 * t}, shapes.get("n"));
        assertArrayEquals(new int[] {1, 120 * t + 1, 22}, shapes.get("har"));
    }

    private static void expectIo(ByteBuffer model, Map<String, int[]> shapes) {
        try {
            TfliteShapePatcher.patch(model, shapes);
            fail("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }
}
