package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Silero VAD v5 on LiteRT: the {@link SileroVoiceDecider.ProbabilitySource} behind the voice
 * key's voiced decision. The model is bundled as {@link #ASSET_PATH} (about 1.2 MB, 16 kHz only,
 * converted and checked against the upstream ONNX by {@code scripts/voice-eval/convert_silero.py});
 * it takes a 576-sample window (64 samples of context and a 512-sample chunk) plus the LSTM state
 * {@code [2, 1, 128]}, and returns the chunk's speech probability and the next state. The state
 * lives in a buffer here, not inside the model, so this class carries it from chunk to chunk and a
 * new instance starts from zeros.
 *
 * <p>One interpreter thread with the stock XNNPACK path: a chunk is about 0.7 M multiply-adds, far
 * too little for a thread pool to pay for itself, so the {@code TaiXnnpackDelegate} the speech
 * models use for their worker threads buys nothing here. All buffers are allocated once; a chunk
 * run allocates nothing on the Java side.
 */
final class SileroVad implements SileroVoiceDecider.ProbabilitySource {

    static final String ASSET_PATH = "vad/silero_vad_v5.tflite";
    private static final String LOG_TAG = "SileroVad";
    private static final int STATE_FLOATS = 2 * 128;

    /**
     * Process-wide, so a missing or broken model is logged once rather than on every press of the
     * voice key; a failure mid-stream is reported through the same gate.
     */
    private static final SileroVoiceDecider.FailureReporter REPORTER = SileroVoiceDecider.once(
        (message, cause) -> Logger.logStackTraceWithMessage(LOG_TAG, message, cause));

    /** The flatbuffer; the interpreter reads its weights from here for its whole life. */
    private final ByteBuffer model;
    private final Interpreter interpreter;
    private final ByteBuffer windowBytes;
    private final FloatBuffer windowFloats;
    private final ByteBuffer stateIn;
    private final ByteBuffer stateOut;
    private final ByteBuffer probability;
    private final Object[] inputs = new Object[2];
    private final Map<Integer, Object> outputs = new HashMap<>(4);

    /**
     * The session's Silero decider, or null when the model cannot be opened (missing asset, no
     * LiteRT native library, a graph that does not have the expected tensors); the caller then
     * keeps the energy detector. Reads and builds the model on the calling thread — the capture
     * thread, never the main one.
     */
    static SileroVoiceDecider openDecider(@NonNull Context context) {
        return SileroVoiceDecider.open(() -> load(context), REPORTER);
    }

    @NonNull
    static SileroVad load(@NonNull Context context) throws IOException {
        ByteBuffer model = readAsset(context, ASSET_PATH);
        Interpreter interpreter = new Interpreter(model,
            new Interpreter.Options().setNumThreads(1).setUseXNNPACK(true));
        try {
            return new SileroVad(model, interpreter);
        } catch (RuntimeException e) {
            interpreter.close();
            throw e;
        }
    }

    private SileroVad(@NonNull ByteBuffer model, @NonNull Interpreter interpreter) {
        this.model = model;
        this.interpreter = interpreter;
        // The converter's tensor order is not part of the contract; the shapes are, so tensors
        // are told apart by size: the window is 576 floats, the state 256, the probability 1.
        int windowIndex = -1, stateInIndex = -1;
        for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
            int n = interpreter.getInputTensor(i).numElements();
            if (n == SileroVoiceDecider.WINDOW_SAMPLES) windowIndex = i;
            else if (n == STATE_FLOATS) stateInIndex = i;
        }
        int probabilityIndex = -1, stateOutIndex = -1;
        for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
            int n = interpreter.getOutputTensor(i).numElements();
            if (n == 1) probabilityIndex = i;
            else if (n == STATE_FLOATS) stateOutIndex = i;
        }
        if (windowIndex < 0 || stateInIndex < 0 || probabilityIndex < 0 || stateOutIndex < 0
            || interpreter.getInputTensorCount() != 2 || interpreter.getOutputTensorCount() != 2) {
            throw new IllegalStateException("unexpected Silero tensors: " + describe(interpreter));
        }
        windowBytes = direct(SileroVoiceDecider.WINDOW_SAMPLES);
        windowFloats = windowBytes.asFloatBuffer();
        stateIn = direct(STATE_FLOATS);
        stateOut = direct(STATE_FLOATS);
        probability = direct(1);
        inputs[windowIndex] = windowBytes;
        inputs[stateInIndex] = stateIn;
        outputs.put(probabilityIndex, probability);
        outputs.put(stateOutIndex, stateOut);
    }

    @Override
    public float probability(@NonNull float[] window) {
        windowFloats.clear();
        windowFloats.put(window, 0, SileroVoiceDecider.WINDOW_SAMPLES);
        windowBytes.rewind();
        stateIn.rewind();
        stateOut.rewind();
        probability.rewind();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        // The state the model just produced is what it reads with the next chunk.
        stateOut.rewind();
        stateIn.rewind();
        stateIn.put(stateOut);
        return probability.getFloat(0);
    }

    @Override
    public void close() {
        interpreter.close();
    }

    @NonNull
    private static ByteBuffer direct(int floats) {
        return ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder());
    }

    /**
     * The asset copied into a direct buffer, which works whether or not the build stored it
     * compressed (only an uncompressed asset could be memory-mapped, and nothing in the build
     * guarantees that). 1.2 MB, read once per session.
     */
    @NonNull
    private static ByteBuffer readAsset(@NonNull Context context, @NonNull String path) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 21);
        try (InputStream in = context.getAssets().open(path)) {
            byte[] block = new byte[64 * 1024];
            int read;
            while ((read = in.read(block)) > 0) bytes.write(block, 0, read);
        }
        byte[] data = bytes.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        buffer.put(data);
        buffer.rewind();
        return buffer;
    }

    @NonNull
    private static String describe(@NonNull Interpreter interpreter) {
        StringBuilder out = new StringBuilder("inputs");
        for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
            Tensor t = interpreter.getInputTensor(i);
            out.append(' ').append(t.name()).append('=').append(t.numElements());
        }
        out.append(", outputs");
        for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
            Tensor t = interpreter.getOutputTensor(i);
            out.append(' ').append(t.name()).append('=').append(t.numElements());
        }
        return out.toString();
    }
}
