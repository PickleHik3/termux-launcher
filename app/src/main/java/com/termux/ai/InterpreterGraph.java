package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link LiteRtGraph} on the stock LiteRT {@link Interpreter}: XNNPACK on, {@code threads}
 * interpreter threads, every signature run through {@link Interpreter#runSignature} with its
 * inputs and outputs bound by name. Each {@link LiteRtGraph.Tensor} is a direct native-order
 * {@link ByteBuffer} sized exactly to the tensor's static shape (the Interpreter compares a
 * buffer's capacity to the tensor's bytes), rewound before every run.
 */
final class InterpreterGraph implements LiteRtGraph {
    /** The loader the speech runtimes use outside tests. */
    static final Loader LOADER = InterpreterGraph::open;

    @Nullable private Interpreter interpreter;

    private InterpreterGraph(@NonNull Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @NonNull
    static InterpreterGraph open(@NonNull File model, int threads) {
        Interpreter.Options options = new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true);
        return new InterpreterGraph(new Interpreter(model, options));
    }

    @NonNull
    private Interpreter interpreter() {
        Interpreter runner = interpreter;
        if (runner == null) throw new IllegalStateException("STT runtime is not loaded.");
        return runner;
    }

    @NonNull
    @Override
    public String[] signatureKeys() {
        return interpreter().getSignatureKeys();
    }

    @NonNull
    @Override
    public String[] inputNames(@NonNull String signature) {
        return interpreter().getSignatureInputs(signature);
    }

    @NonNull
    @Override
    public String[] outputNames(@NonNull String signature) {
        return interpreter().getSignatureOutputs(signature);
    }

    @NonNull
    @Override
    public TensorSpec input(@NonNull String signature, @NonNull String name) {
        return spec(interpreter().getInputTensorFromSignature(name, signature));
    }

    @NonNull
    @Override
    public TensorSpec output(@NonNull String signature, @NonNull String name) {
        return spec(interpreter().getOutputTensorFromSignature(name, signature));
    }

    @NonNull
    @Override
    public LiteRtGraph.Tensor newInput(@NonNull String signature, @NonNull String name) {
        return buffer(interpreter().getInputTensorFromSignature(name, signature));
    }

    @NonNull
    @Override
    public LiteRtGraph.Tensor newOutput(@NonNull String signature, @NonNull String name) {
        return buffer(interpreter().getOutputTensorFromSignature(name, signature));
    }

    @NonNull
    @Override
    public Signature bind(@NonNull String signature, @NonNull Map<String, LiteRtGraph.Tensor> inputs,
                          @NonNull Map<String, LiteRtGraph.Tensor> outputs) {
        return new Bound(signature, inputs, outputs);
    }

    @Override
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    @NonNull
    private static TensorSpec spec(@NonNull org.tensorflow.lite.Tensor tensor) {
        DataType type = tensor.dataType();
        ElementType element = type == DataType.FLOAT32 ? ElementType.FLOAT32
            : type == DataType.INT32 ? ElementType.INT32 : ElementType.OTHER;
        return new TensorSpec(tensor.shape(), element);
    }

    @NonNull
    private static Buffer buffer(@NonNull org.tensorflow.lite.Tensor tensor) {
        // Sizes from the static shape, known before the signature's tensors are allocated.
        TensorSpec spec = spec(tensor);
        int bytesPerElement = spec.type == ElementType.OTHER ? tensor.dataType().byteSize() : 4;
        return new Buffer(ByteBuffer.allocateDirect(spec.elements() * bytesPerElement).order(ByteOrder.nativeOrder()));
    }

    /** A direct buffer, left rewound after every call. */
    private static final class Buffer implements LiteRtGraph.Tensor {
        final ByteBuffer bytes;

        Buffer(@NonNull ByteBuffer bytes) {
            this.bytes = bytes;
        }

        @Override
        public void writeFloats(@NonNull float[] values, int count) {
            bytes.rewind();
            bytes.asFloatBuffer().put(values, 0, count);
            bytes.rewind();
        }

        @Override
        public void writeInts(@NonNull int[] values, int count) {
            bytes.rewind();
            bytes.asIntBuffer().put(values, 0, count);
            bytes.rewind();
        }

        @Override
        public void readFloats(int offset, @NonNull float[] into, int count) {
            bytes.position(offset * 4);
            bytes.asFloatBuffer().get(into, 0, count);
            bytes.rewind();
        }

        @Override
        public void zero() {
            bytes.rewind();
            while (bytes.hasRemaining()) bytes.put((byte) 0);
            bytes.rewind();
        }

        @Override
        public void copyFrom(@NonNull LiteRtGraph.Tensor from) {
            ByteBuffer source = ((Buffer) from).bytes;
            source.rewind();
            bytes.rewind();
            bytes.put(source);
            source.rewind();
            bytes.rewind();
        }
    }

    /** A signature with its name maps built once; every bound buffer is rewound before a run. */
    private final class Bound implements Signature {
        final String key;
        final HashMap<String, Object> inputs = new HashMap<>();
        final HashMap<String, Object> outputs = new HashMap<>();
        final ByteBuffer[] buffers;

        Bound(@NonNull String key, @NonNull Map<String, LiteRtGraph.Tensor> inputs, @NonNull Map<String, LiteRtGraph.Tensor> outputs) {
            this.key = key;
            buffers = new ByteBuffer[inputs.size() + outputs.size()];
            int i = 0;
            for (Map.Entry<String, LiteRtGraph.Tensor> entry : inputs.entrySet()) {
                ByteBuffer bytes = ((Buffer) entry.getValue()).bytes;
                this.inputs.put(entry.getKey(), bytes);
                buffers[i++] = bytes;
            }
            for (Map.Entry<String, LiteRtGraph.Tensor> entry : outputs.entrySet()) {
                ByteBuffer bytes = ((Buffer) entry.getValue()).bytes;
                this.outputs.put(entry.getKey(), bytes);
                buffers[i++] = bytes;
            }
        }

        @Override
        public void run() {
            Interpreter runner = interpreter();
            for (ByteBuffer bytes : buffers) bytes.rewind();
            runner.runSignature(inputs, outputs, key);
        }
    }
}
