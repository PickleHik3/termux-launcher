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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Parakeet runtime driven through {@link LiteRtGraph} with {@link FakeLiteRtGraph}: three
 * signatures loaded, run and closed through the seam, and the decoder's LSTM state zeroed per
 * window and carried from one call to the next only through the seam's tensors.
 *
 * <p>The fake's stateless {@code decode} emits ids 2..5 at slots 0..3 (filling the token slots,
 * which hands over to {@code decode_1}) and returns state 7; {@code decode_1} emits id 9 when it
 * is handed state 7 and returns state 9, and ends the window when handed state 9. A state that
 * did not arrive through the seam emits id 6 instead.
 */
public class ParakeetSttRuntimeGraphTest {
    private static final int SLOTS = 4;
    private static final int FRAMES = 4;
    private static final int VOCAB = ParakeetTdtDecoder.BLANK + 1 + ParakeetTdtDecoder.NUM_DURATIONS;
    private static final int[] STATE = {2, 1, 8};
    private static final String TOKENIZER = "{\"model\":{\"type\":\"BPE\",\"vocab\":{"
        + "\"<unk>\":0,\"<pad>\":1,\"▁git\":2,\"▁status\":3,\"▁he\":4,\"llo\":5,\"▁\":6,\".\":7,\"É\":8,\"▁café\":9"
        + "}},\"added_tokens\":[{\"id\":0,\"content\":\"<unk>\",\"special\":true},{\"id\":1,\"content\":\"<pad>\",\"special\":false}]}";

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void threeSignaturesRunThroughTheSeamAndTheStateIsCarriedByIt() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph graph = parakeetGraph();
        TaiResidency residency = new TaiResidency();
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(residency, null, graph.loader());
        File audio = tmp.newFile("clip.wav");
        Files.write(audio.toPath(), WhisperMelTest.readBytes("whisper/git_status_16k.wav"));

        JSONObject result = runtime.transcribe(spec(model.getAbsolutePath()), audio, null, null);

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status hello café", result.getString("text"));
        assertEquals(1, graph.loads);
        assertEquals(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())), graph.loadedThreads);
        assertEquals(1, result.getJSONObject("timings").getInt("pieces"));
        assertEquals(1, graph.runs(ParakeetSttRuntime.SIGNATURE_ENCODE));
        assertEquals(SLOTS, graph.runs(ParakeetSttRuntime.SIGNATURE_DECODE));
        assertEquals(2, graph.runs(ParakeetSttRuntime.SIGNATURE_DECODE_STATEFUL));

        FakeLiteRtGraph.Bound encode = graph.bindingOf(ParakeetSttRuntime.SIGNATURE_ENCODE);
        FakeLiteRtGraph.Bound decode = graph.bindingOf(ParakeetSttRuntime.SIGNATURE_DECODE);
        FakeLiteRtGraph.Bound stateful = graph.bindingOf(ParakeetSttRuntime.SIGNATURE_DECODE_STATEFUL);
        // One encoder output feeds both decoders; both decoders share one state.
        assertSame(encode.outputs.get("enc"), decode.inputs.get("args_0"));
        assertSame(encode.outputs.get("enc"), stateful.inputs.get("args_0"));
        for (String in : new String[] {"args_2", "args_3"}) assertSame(decode.inputs.get(in), stateful.inputs.get(in));
        for (String out : new String[] {"output_1", "output_2"}) assertSame(decode.outputs.get(out), stateful.outputs.get(out));
        // Zeroed once for the window, adopted twice (at the hand-over and after id 9): h and c each.
        FakeLiteRtGraph.FakeTensor h = decode.inputs.get("args_2");
        FakeLiteRtGraph.FakeTensor c = decode.inputs.get("args_3");
        assertEquals(1, h.zeroes);
        assertEquals(1, c.zeroes);
        assertEquals(2, h.copies);
        assertEquals(2, c.copies);
        assertEquals(9f, h.floats[0], 0f);

        runtime.close();
        assertEquals(1, graph.closes);
        assertFalse(runtime.isLoaded("parakeet-tdt-0.6b-v3"));
        assertTrue(residency.snapshot().isEmpty());
    }

    @Test
    public void aDecoderWithTheWrongLogitsWidthFailsTheLoadAndIsClosed() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph graph = new FakeLiteRtGraph();
        declare(graph, VOCAB - 1);
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null, graph.loader());

        JSONObject warm = runtime.warm(spec(model.getAbsolutePath()));

        assertFalse(warm.getBoolean("ok"));
        assertEquals(500, warm.getInt("_statusCode"));
        assertEquals("stt_load_failed", warm.getJSONObject("error").getString("code"));
        assertEquals("Parakeet model failed to load: Unexpected logits width " + (VOCAB - 1), warm.getJSONObject("error").getString("message"));
        assertEquals(1, graph.closes);
        assertFalse(runtime.isLoaded("parakeet-tdt-0.6b-v3"));
    }

    @Test
    public void autoKeepsASaneGpuProvesItAndCarriesTheStateThroughIt() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = parakeetGraph();
        FakeLiteRtGraph gpu = parakeetGraph(0.25f);
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), gpu.loader()), () -> SttAccelerator.AUTO);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribe(runtime, model);

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status hello café", result.getString("text"));
        assertEquals("gpu", result.getString("accelerator"));
        assertFalse(result.has("acceleratorFallback"));
        assertEquals(0, cpu.loads);
        assertEquals(Arrays.asList("gpu:true:"), memory.records);
        // The proof: one encode and one stateless decode step on the fixed input, before the piece.
        assertEquals(2, gpu.runs(ParakeetSttRuntime.SIGNATURE_ENCODE));
        assertEquals(1 + SLOTS, gpu.runs(ParakeetSttRuntime.SIGNATURE_DECODE));
        assertEquals(2, gpu.runs(ParakeetSttRuntime.SIGNATURE_DECODE_STATEFUL));
        // The state: zeroed in the proof and once for the window, adopted at every step after.
        FakeLiteRtGraph.Bound decode = gpu.bindingOf(ParakeetSttRuntime.SIGNATURE_DECODE);
        FakeLiteRtGraph.FakeTensor h = decode.inputs.get("args_2");
        FakeLiteRtGraph.FakeTensor c = decode.inputs.get("args_3");
        assertEquals(2, h.zeroes);
        assertEquals(2, c.zeroes);
        assertEquals(2, h.copies);
        assertEquals(2, c.copies);
        assertEquals(9f, h.floats[0], 0f);
    }

    @Test
    public void aGpuThatFailsToLoadNeverBreaksParakeet() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = parakeetGraph();
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        LiteRtGraph.Loader brokenGpu = (file, threads) -> {
            throw new IllegalStateException("Failed to create the GPU accelerator");
        };
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), brokenGpu), () -> SttAccelerator.AUTO);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribe(runtime, model);

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status hello café", result.getString("text"));
        assertEquals("cpu", result.getString("accelerator"));
        assertTrue(result.getString("acceleratorFallback").contains("GPU accelerator"));
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0).startsWith("gpu:false:"));
    }

    @Test
    public void aGpuWhoseEncoderOutputIsFlatIsDroppedForTheCpuAndRecorded() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = parakeetGraph();
        FakeLiteRtGraph gpu = parakeetGraph();   // its encoder output stays all zero: flat
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), gpu.loader()), () -> SttAccelerator.AUTO);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribe(runtime, model);

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("git status hello café", result.getString("text"));
        assertEquals("cpu", result.getString("accelerator"));
        assertTrue(result.getString("acceleratorFallback"), result.getString("acceleratorFallback").contains("flat"));
        assertEquals(1, gpu.closes);
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0).startsWith("gpu:false:"));
    }

    @Test
    public void autoWithAFailureOnRecordGoesStraightToTheCpu() throws Exception {
        File model = modelDir();
        FakeLiteRtGraph cpu = parakeetGraph();
        FakeLiteRtGraph gpu = parakeetGraph(0.25f);
        SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();
        memory.failure = "earlier failure";
        ParakeetSttRuntime runtime = new ParakeetSttRuntime(new TaiResidency(), null,
            new SttGraphChooser(cpu.loader(), cpu.loader(), gpu.loader()), () -> SttAccelerator.AUTO);
        runtime.setMemory(spec -> memory);

        JSONObject result = transcribe(runtime, model);

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals("cpu", result.getString("accelerator"));
        assertEquals(0, gpu.loads);
        // No proof run on the CPU path: the window's zero is the only one.
        assertEquals(1, cpu.runs(ParakeetSttRuntime.SIGNATURE_ENCODE));
        assertEquals(1, cpu.bindingOf(ParakeetSttRuntime.SIGNATURE_DECODE).inputs.get("args_2").zeroes);
    }

    private JSONObject transcribe(ParakeetSttRuntime runtime, File model) throws Exception {
        File audio = tmp.newFile();
        Files.write(audio.toPath(), WhisperMelTest.readBytes("whisper/git_status_16k.wav"));
        return runtime.transcribe(spec(model.getAbsolutePath()), audio, null, null);
    }

    private static FakeLiteRtGraph parakeetGraph() {
        return parakeetGraph(0f);
    }

    /** With {@code step != 0} the encoder fills its output with a ramp, as a GPU that works would. */
    private static FakeLiteRtGraph parakeetGraph(float step) {
        FakeLiteRtGraph graph = new FakeLiteRtGraph();
        declare(graph, VOCAB);
        return graph.script((signature, in, out) -> {
            if (signature.equals(ParakeetSttRuntime.SIGNATURE_ENCODE)) {
                float[] enc = out.get("enc").floats;
                for (int i = 0; i < enc.length; i++) enc[i] = i * step;
                return;
            }
            run(signature, in, out);
        });
    }

    private static void declare(FakeLiteRtGraph graph, int vocab) {
        LiteRtGraph.ElementType f32 = LiteRtGraph.ElementType.FLOAT32;
        graph.input(ParakeetSttRuntime.SIGNATURE_ENCODE, "mel", f32, 1, ParakeetFeatures.N_MELS, ParakeetFeatures.FRAMES)
            .output(ParakeetSttRuntime.SIGNATURE_ENCODE, "enc", f32, 1, 1024, FRAMES);
        String[] keys = {ParakeetSttRuntime.SIGNATURE_DECODE, ParakeetSttRuntime.SIGNATURE_DECODE_STATEFUL};
        int[] slots = {SLOTS, 1};
        for (int k = 0; k < keys.length; k++) {
            graph.input(keys[k], "args_0", f32, 1, 1024, FRAMES)
                .input(keys[k], "args_1", LiteRtGraph.ElementType.INT32, 1, slots[k])
                .input(keys[k], "args_2", f32, STATE)
                .input(keys[k], "args_3", f32, STATE)
                .output(keys[k], "output_0", f32, 1, FRAMES, slots[k], vocab)
                .output(keys[k], "output_1", f32, STATE)
                .output(keys[k], "output_2", f32, STATE);
        }
    }

    private static void run(String signature, Map<String, FakeLiteRtGraph.FakeTensor> in, Map<String, FakeLiteRtGraph.FakeTensor> out) {
        if (signature.equals(ParakeetSttRuntime.SIGNATURE_ENCODE)) return;
        float[] logits = out.get("output_0").floats;
        Arrays.fill(logits, 0f);
        float state = in.get("args_2").floats[0];
        if (signature.equals(ParakeetSttRuntime.SIGNATURE_DECODE)) {
            for (int t = 0; t < FRAMES; t++) {
                for (int slot = 0; slot < SLOTS; slot++) {
                    // A stateless call only ever sees the window's zero state.
                    emit(logits, (t * SLOTS + slot) * VOCAB, state == 0f ? 2 + slot : 6, 0);
                }
            }
            Arrays.fill(out.get("output_1").floats, 7f);
            Arrays.fill(out.get("output_2").floats, 8f);
            return;
        }
        for (int t = 0; t < FRAMES; t++) {
            if (state == 7f) emit(logits, t * VOCAB, 9, 0);
            else if (state == 9f) emit(logits, t * VOCAB, ParakeetTdtDecoder.BLANK, ParakeetTdtDecoder.NUM_DURATIONS - 1);
            else emit(logits, t * VOCAB, 6, ParakeetTdtDecoder.NUM_DURATIONS - 1);
        }
        Arrays.fill(out.get("output_1").floats, 9f);
        Arrays.fill(out.get("output_2").floats, 9f);
    }

    private static void emit(float[] logits, int at, int token, int duration) {
        logits[at + token] = 10f;
        logits[at + ParakeetTdtDecoder.BLANK + 1 + duration] = 10f;
    }

    private File modelDir() throws Exception {
        File dir = tmp.newFolder("parakeet-tdt-0.6b-v3");
        File model = new File(dir, "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite");
        Files.write(model.toPath(), "fake-graph".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, ParakeetSttRuntime.TOKENIZER_FILE).toPath(), TOKENIZER.getBytes(StandardCharsets.UTF_8));
        return model;
    }

    private static TaiModelSpec spec(String localPath) {
        return new TaiModelSpec(
            "parakeet-tdt-0.6b-v3", "Parakeet TDT 0.6B v3", "Speech-to-text", "test", localPath, "CC-BY-4.0", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            ParakeetSttRuntime.ARCHITECTURE, "int8", 128, 0, null);
    }
}
