package com.termux.ai;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the Parakeet runtime does before any graph is touched: how audio is cut into 5 s pieces,
 * the file checks a request fails on, and how the router tells a Parakeet model from a Whisper one.
 */
public class ParakeetSttRuntimeTest {
    private static final int SR = ParakeetFeatures.SAMPLE_RATE;
    private static final int FRAME = WhisperSegmenter.FRAME_SAMPLES;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void longAudioIsCutAtTheQuietestFrameInTheLastSecondBeforeEachWindow() {
        float[] audio = new float[12 * SR];
        for (int i = 0; i < audio.length; i++) audio[i] = (float) (0.1 * Math.sin(2.0 * Math.PI * 440.0 * i / SR));
        // A silent frame 0.4 s before the first 5 s boundary, and one 0.7 s before the second.
        int firstDip = ParakeetFeatures.WINDOW_SAMPLES - (int) (0.4 * SR);
        int secondDip = firstDip + ParakeetFeatures.WINDOW_SAMPLES - (int) (0.7 * SR);
        for (int i = firstDip; i < firstDip + FRAME; i++) audio[i] = 0f;
        for (int i = secondDip; i < secondDip + FRAME; i++) audio[i] = 0f;

        List<int[]> pieces = ParakeetSttRuntime.pieces(audio);

        assertEquals(3, pieces.size());
        assertArrayEquals(new int[] {0, firstDip}, pieces.get(0));
        assertArrayEquals(new int[] {firstDip, secondDip}, pieces.get(1));
        assertArrayEquals(new int[] {secondDip, audio.length}, pieces.get(2));
        for (int[] piece : pieces) assertTrue(piece[1] - piece[0] <= ParakeetFeatures.WINDOW_SAMPLES);
    }

    @Test
    public void audioWithinTheWindowIsOnePieceAndNothingIsDroppedOrPadded() {
        float[] silent = new float[3 * SR];
        List<int[]> pieces = ParakeetSttRuntime.pieces(silent);
        assertEquals(1, pieces.size());
        assertArrayEquals(new int[] {0, silent.length}, pieces.get(0));
        assertArrayEquals(new int[] {0, ParakeetFeatures.WINDOW_SAMPLES}, ParakeetSttRuntime.pieces(new float[ParakeetFeatures.WINDOW_SAMPLES]).get(0));
    }

    @Test
    public void aMissingGraphOrTokenizerIsRefusedBeforeAnythingLoads() throws Exception {
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null);
        File dir = tmp.newFolder("parakeet-tdt-0.6b-v3");
        File graph = new File(dir, "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite");

        JSONObject noPath = runtime.warm(spec(null));
        assertFalse(noPath.getBoolean("ok"));
        assertEquals(404, noPath.getInt("_statusCode"));
        assertEquals("model_file_missing", noPath.getJSONObject("error").getString("code"));

        JSONObject noFile = runtime.warm(spec(graph.getAbsolutePath()));
        assertEquals(404, noFile.getInt("_statusCode"));
        assertEquals("model_file_not_readable", noFile.getJSONObject("error").getString("code"));

        Files.write(graph.toPath(), "not-a-real-graph".getBytes(StandardCharsets.UTF_8));
        JSONObject noTokenizer = runtime.transcribe(spec(graph.getAbsolutePath()), graph, null, null);
        assertFalse(noTokenizer.getBoolean("ok"));
        assertEquals(409, noTokenizer.getInt("_statusCode"));
        assertEquals("stt_tokenizer_missing", noTokenizer.getJSONObject("error").getString("code"));
        assertFalse(runtime.isLoaded("parakeet-tdt-0.6b-v3"));
        runtime.close();
    }

    @Test
    public void theRouterKnowsAParakeetModelByArchitectureOrFileName() {
        assertTrue(MultiBackendTaiRuntime.isParakeetModel(spec("/m/x/model.tflite", ParakeetSttRuntime.ARCHITECTURE)));
        assertTrue(MultiBackendTaiRuntime.isParakeetModel(spec("/m/x/parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite", null)));
        assertFalse(MultiBackendTaiRuntime.isParakeetModel(spec("/m/x/acft_whisper_base.en_10s_drq.tflite", "whisper-acft")));
        assertFalse(MultiBackendTaiRuntime.isParakeetModel(spec(null, null)));
    }

    private static TaiModelSpec spec(String localPath) {
        return spec(localPath, ParakeetSttRuntime.ARCHITECTURE);
    }

    private static TaiModelSpec spec(String localPath, String architecture) {
        return new TaiModelSpec(
            "parakeet-tdt-0.6b-v3", "Parakeet TDT 0.6B v3", "Speech-to-text", "test", localPath, "CC-BY-4.0", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            architecture, "int8", 128, 0, null);
    }
}
