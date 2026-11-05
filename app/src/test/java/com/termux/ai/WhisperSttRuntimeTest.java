package com.termux.ai;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Whisper runtime driven through {@link LiteRtGraph} with {@link FakeLiteRtGraph}: what it
 * loads, binds, writes, runs, reads and closes. The fake's decoder predicts, at every slot, the
 * token after that slot's token, so reading the logits at the wrong slot shows in the transcript.
 */
public class WhisperSttRuntimeTest {
    private static final int FRAMES = 1000;
    private static final int SEQUENCE = 16;
    private static final int VOCAB = 51864;
    private static final int EOT = 50256;
    private static final int NO_TIMESTAMPS = 50362;
    private static final int GIT = 18300;
    private static final int STATUS = 3722;
    private static final int WRONG = 3855;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void loadRunAndCloseAllGoThroughTheSeam() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph graph = whisperGraph();
        TaiResidency residency = new TaiResidency();
        WhisperSttRuntime runtime = new WhisperSttRuntime(residency, null, graph.loader());

        JSONObject warm = runtime.warm(spec(model.getAbsolutePath()));
        assertTrue(warm.toString(), warm.getBoolean("ok"));
        assertFalse(warm.getBoolean("alreadyLoaded"));
        assertEquals(1, graph.loads);
        assertEquals(model.getAbsoluteFile(), graph.loadedFrom.getAbsoluteFile());
        assertEquals(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())), graph.loadedThreads);
        assertTrue(runtime.isLoaded("whisper-base-en"));

        File audio = tmp.newFile("clip.wav");
        Files.write(audio.toPath(), WhisperMelTest.readBytes("whisper/git_status_16k.wav"));
        JSONObject result = runtime.transcribe(spec(model.getAbsolutePath()), audio, null, null);
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status", result.getString("text"));
        assertEquals(1, graph.loads);
        int pieces = result.getJSONObject("timings").getInt("pieces");
        assertEquals(pieces, graph.runs(WhisperSttRuntime.SIGNATURE_ENCODE));
        assertEquals(3 * pieces, graph.runs(WhisperSttRuntime.SIGNATURE_DECODE));

        // The encoder's output is the decoder's audio input: one tensor, never copied.
        FakeLiteRtGraph.Bound encode = graph.bindingOf(WhisperSttRuntime.SIGNATURE_ENCODE);
        FakeLiteRtGraph.Bound decode = graph.bindingOf(WhisperSttRuntime.SIGNATURE_DECODE);
        assertSame(encode.outputs.get("features"), decode.inputs.get("audio"));
        assertEquals(0, encode.outputs.get("features").copies);
        // The causal mask is written once, at the load.
        FakeLiteRtGraph.FakeTensor mask = decode.inputs.get("mask");
        assertEquals(1, mask.writes);
        for (int i = 0; i < SEQUENCE; i++) {
            for (int j = 0; j < SEQUENCE; j++) {
                assertEquals(j > i ? WhisperSttRuntime.MASK_DENY : WhisperSttRuntime.MASK_ALLOW, mask.floats[i * SEQUENCE + j], 0f);
            }
        }
        // The last piece's log-mel rows, laid end to end.
        float[] samples = WhisperAudio.read(audio);
        List<WhisperSegmenter.Piece> cut = WhisperSegmenter.pieces(samples, FRAMES * WhisperMel.HOP);
        float[][] mel = WhisperMel.logMel(cut.get(cut.size() - 1).samples, FRAMES);
        float[] written = encode.inputs.get("mel").floats;
        for (int row = 0; row < WhisperMel.N_MELS; row++) {
            assertArrayEquals(mel[row], Arrays.copyOfRange(written, row * FRAMES, (row + 1) * FRAMES), 0f);
        }

        runtime.close();
        assertEquals(1, graph.closes);
        assertFalse(runtime.isLoaded("whisper-base-en"));
        assertTrue(residency.snapshot().isEmpty());
    }

    @Test
    public void aGraphWithoutTheDecodeSignatureFailsTheLoadAndIsClosed() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph graph = new FakeLiteRtGraph()
            .input(WhisperSttRuntime.SIGNATURE_ENCODE, "mel", LiteRtGraph.ElementType.FLOAT32, 1, WhisperMel.N_MELS, FRAMES)
            .output(WhisperSttRuntime.SIGNATURE_ENCODE, "features", LiteRtGraph.ElementType.FLOAT32, 1, FRAMES / 2, 512);
        WhisperSttRuntime runtime = new WhisperSttRuntime(new TaiResidency(), null, graph.loader());

        JSONObject warm = runtime.warm(spec(model.getAbsolutePath()));
        assertFalse(warm.getBoolean("ok"));
        assertEquals(500, warm.getInt("_statusCode"));
        assertEquals("stt_load_failed", warm.getJSONObject("error").getString("code"));
        assertTrue(warm.getJSONObject("error").getString("message").startsWith("Whisper model failed to load: Graph lacks the encode/decode signatures"));
        assertEquals(1, graph.closes);
        assertFalse(runtime.isLoaded("whisper-base-en"));
    }

    @Test
    public void aGpuAskKeepsASaneGpuAndSaysSo() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = whisperGraph();
        FakeLiteRtGraph gpu = whisperGraph(0.25f);
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        WhisperSttRuntime runtime = new WhisperSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), gpu.loader()), () -> SttAccelerator.GPU);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribeGitStatus(runtime, model);
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status", result.getString("text"));
        assertEquals("gpu", result.getString("accelerator"));
        assertFalse(result.has("acceleratorFallback"));
        assertEquals(0, cpu.loads);
        // The proof: one encode and one decode step on the fixed input, before any piece.
        int pieces = result.getJSONObject("timings").getInt("pieces");
        assertEquals(1 + pieces, gpu.runs(WhisperSttRuntime.SIGNATURE_ENCODE));
        assertEquals(1 + 3 * pieces, gpu.runs(WhisperSttRuntime.SIGNATURE_DECODE));
        assertEquals(Arrays.asList("gpu:true:"), memory.records);
    }

    @Test
    public void aGpuThatFailsToLoadNeverBreaksTranscription() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = whisperGraph();
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        LiteRtGraph.Loader brokenGpu = (file, threads) -> {
            throw new IllegalStateException("Failed to create the GPU accelerator");
        };
        WhisperSttRuntime runtime = new WhisperSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), brokenGpu), () -> SttAccelerator.GPU);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribeGitStatus(runtime, model);
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status", result.getString("text"));
        assertEquals("cpu", result.getString("accelerator"));
        assertTrue(result.getString("acceleratorFallback").contains("GPU accelerator"));
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0).startsWith("gpu:false:"));
        // A GPU pick tries again after an ordinary failure, and still falls back to the CPU.
        runtime.close();
        assertTrue(transcribeGitStatus(runtime, model).getBoolean("ok"));
        assertEquals(2, memory.records.size());
        assertEquals(2, memory.begun);
    }

    @Test
    public void aGpuWithNanOutputIsDroppedForTheCpu() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = whisperGraph();
        FakeLiteRtGraph gpu = whisperGraph(Float.NaN);
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        WhisperSttRuntime runtime = new WhisperSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), gpu.loader()), () -> SttAccelerator.GPU);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribeGitStatus(runtime, model);
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status", result.getString("text"));
        assertEquals("cpu", result.getString("accelerator"));
        assertTrue(result.getString("acceleratorFallback"), result.getString("acceleratorFallback").contains("encoder output"));
        assertEquals(1, gpu.closes);
        assertEquals(1, cpu.loads);
    }

    @Test
    public void aDifferentAskReloadsTheGraph() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph graph = whisperGraph(0.25f);
        SttAccelerator[] ask = {SttAccelerator.CPU};
        WhisperSttRuntime runtime = new WhisperSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(graph.loader(), graph.loader(), graph.loader()), () -> ask[0]);
        assertEquals("cpu", runtime.warm(spec(model.getAbsolutePath())).getString("accelerator"));
        assertEquals("cpu", runtime.warm(spec(model.getAbsolutePath())).getString("accelerator"));
        assertEquals(1, graph.loads);
        ask[0] = SttAccelerator.GPU;
        assertEquals("gpu", runtime.warm(spec(model.getAbsolutePath())).getString("accelerator"));
        assertEquals(2, graph.loads);
    }

    @Test
    public void sanityRejectsNonFiniteHugeAndFlatOutput() {
        WhisperSttRuntime.requireSane(new float[] {0f, 1f, -2f}, "ok");
        for (float[] bad : new float[][] {{0f, Float.NaN}, {0f, Float.POSITIVE_INFINITY}, {0f, 2e6f}, {3f, 3f, 3f}, {}}) {
            try {
                WhisperSttRuntime.requireSane(bad, "x");
                org.junit.Assert.fail(Arrays.toString(bad));
            } catch (IllegalStateException expected) {
                // As it should.
            }
        }
    }

    private JSONObject transcribeGitStatus(WhisperSttRuntime runtime, File model) throws Exception {
        File audio = new File(tmp.getRoot(), "clip.wav");
        if (!audio.isFile()) Files.write(audio.toPath(), WhisperMelTest.readBytes("whisper/git_status_16k.wav"));
        return runtime.transcribe(spec(model.getAbsolutePath()), audio, null, null);
    }

    private static FakeLiteRtGraph whisperGraph() {
        return whisperShapes().script(decodeOnly());
    }

    private static FakeLiteRtGraph.Script decodeOnly() {
        return (signature, in, out) -> {
            if (!signature.equals(WhisperSttRuntime.SIGNATURE_DECODE)) return;
            int[] tokens = in.get("tokens").ints;
            float[] logits = out.get("logits").floats;
            Arrays.fill(logits, 0f);
            for (int slot = 0; slot < SEQUENCE; slot++) logits[slot * VOCAB + next(tokens[slot])] = 10f;
        };
    }

    /**
     * A Whisper graph whose encoder writes {@code featureStep × index} into every feature (NaN:
     * every feature NaN); its decoder is {@link #decodeOnly()}.
     */
    private static FakeLiteRtGraph whisperGraph(float featureStep) {
        FakeLiteRtGraph.Script decode = decodeOnly();
        return whisperShapes().script((signature, in, out) -> {
            if (signature.equals(WhisperSttRuntime.SIGNATURE_ENCODE)) {
                float[] features = out.get("features").floats;
                for (int i = 0; i < features.length; i++) features[i] = Float.isNaN(featureStep) ? Float.NaN : featureStep * (i % 7);
                return;
            }
            decode.run(signature, in, out);
        });
    }

    private static FakeLiteRtGraph whisperShapes() {
        return new FakeLiteRtGraph()
            .input(WhisperSttRuntime.SIGNATURE_ENCODE, "mel", LiteRtGraph.ElementType.FLOAT32, 1, WhisperMel.N_MELS, FRAMES)
            .output(WhisperSttRuntime.SIGNATURE_ENCODE, "features", LiteRtGraph.ElementType.FLOAT32, 1, FRAMES / 2, 512)
            // Listed as the export lists them, mask first: the runtime picks them by shape.
            .input(WhisperSttRuntime.SIGNATURE_DECODE, "mask", LiteRtGraph.ElementType.FLOAT32, 1, 1, SEQUENCE, SEQUENCE)
            .input(WhisperSttRuntime.SIGNATURE_DECODE, "audio", LiteRtGraph.ElementType.FLOAT32, 1, FRAMES / 2, 512)
            .input(WhisperSttRuntime.SIGNATURE_DECODE, "tokens", LiteRtGraph.ElementType.INT32, 1, SEQUENCE)
            .output(WhisperSttRuntime.SIGNATURE_DECODE, "logits", LiteRtGraph.ElementType.FLOAT32, 1, SEQUENCE, VOCAB);
    }

    private static int next(int token) {
        if (token == NO_TIMESTAMPS) return GIT;
        if (token == GIT) return STATUS;
        if (token == STATUS) return EOT;
        return WRONG;
    }

    private File modelDir() throws Exception {
        File dir = tmp.newFolder("whisper-base-en");
        File model = new File(dir, "acft_whisper_base.en_10s_drq.tflite");
        Files.write(model.toPath(), "fake-graph".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, WhisperSttRuntime.TOKENIZER_FILE).toPath(), WhisperMelTest.readBytes("whisper/tokenizer_base_en_trimmed.json"));
        return model;
    }

    private static TaiModelSpec spec(String localPath) {
        return new TaiModelSpec(
            "whisper-base-en", "Whisper base.en", "Speech-to-text", "test", localPath, "MIT", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            "whisper-acft", "int8", 128, 0, null);
    }
}
