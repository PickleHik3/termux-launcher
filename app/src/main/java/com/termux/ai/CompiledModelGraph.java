package com.termux.ai;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litert.Accelerator;
import com.google.ai.edge.litert.CompiledModel;
import com.google.ai.edge.litert.Environment;
import com.google.ai.edge.litert.LiteRtException;
import com.google.ai.edge.litert.TensorBuffer;
import com.google.ai.edge.litert.TensorBufferRequirements;
import com.google.ai.edge.litert.TensorBufferType;
import com.google.ai.edge.litert.TensorType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LiteRtGraph} on LiteRT 2.x's {@link CompiledModel}, on the CPU (XNNPACK, the thread count
 * set explicitly) or the GPU ({@code litert-gpu}'s OpenCL/OpenGL accelerator).
 *
 * <p>Three things 2.3.2's Kotlin API lacks, and what stands in for them:
 * <ul>
 *   <li>No list of signature keys or tensor names: {@link TfliteSignatures} reads them from the
 *       file's flatbuffer, so no second runtime is opened on a 290 MB graph to ask.</li>
 *   <li>{@code TensorBuffer.readFloat()} copies the whole tensor into a new array:
 *       {@link TaiTensorRead} copies a slice through the C API instead, and the whole-tensor read
 *       stays as the fallback ({@link #sliceReads()} says which one is in use).</li>
 *   <li>A buffer made for one signature's output may not be a valid input of another (a GPU
 *       may want a different buffer type there): {@link #bind} shares the tensor when the input's
 *       requirements list the output buffer's type and size, and otherwise gives that input a
 *       buffer of its own, copied from the shared tensor only after the tensor has changed
 *       ({@link #copiedBindings()} counts them). An output bound into a signature it was not
 *       made for (Parakeet's LSTM state, written by both decoders) is checked the same way: when
 *       it does not fit, that signature writes a buffer of its own, copied into the shared
 *       tensor after each run.</li>
 * </ul>
 */
final class CompiledModelGraph implements LiteRtGraph {
    @Nullable private Environment environment;
    @Nullable private CompiledModel model;
    @NonNull private final Map<String, TfliteSignatures.Signature> signatures;
    @NonNull private final Accelerator accelerator;
    private final List<Buffer> buffers = new ArrayList<>();
    private int copiedBindings;
    private boolean anyWholeReads;

    private CompiledModelGraph(@NonNull Environment environment, @NonNull CompiledModel model,
                               @NonNull Map<String, TfliteSignatures.Signature> signatures, @NonNull Accelerator accelerator) {
        this.environment = environment;
        this.model = model;
        this.signatures = signatures;
        this.accelerator = accelerator;
    }

    /**
     * A loader for {@code accelerator} (CPU or GPU). The Environment is made without a Context:
     * 2.3.2 uses one only to find NPU dispatch libraries, and the bench (no app Context) then runs
     * exactly what the runtime process runs.
     */
    @NonNull
    static Loader loader(@NonNull Accelerator accelerator) {
        return (file, threads) -> open(file, threads, accelerator, false);
    }

    /** As {@link #loader(Accelerator)}; {@code gpuFp32} asks the GPU for full float precision (ignored on the CPU). */
    @NonNull
    static Loader loader(@NonNull Accelerator accelerator, boolean gpuFp32) {
        return (file, threads) -> open(file, threads, accelerator, gpuFp32);
    }

    @NonNull
    static CompiledModelGraph open(@NonNull File file, int threads, @NonNull Accelerator accelerator) throws Exception {
        return open(file, threads, accelerator, false);
    }

    @NonNull
    static CompiledModelGraph open(@NonNull File file, int threads, @NonNull Accelerator accelerator,
                                   boolean gpuFp32) throws Exception {
        // Read first: a file that is not a TFLite flatbuffer fails here, before any native work.
        Map<String, TfliteSignatures.Signature> signatures = TfliteSignatures.read(file);
        Environment environment = Environment.create();
        try {
            CompiledModel.Options options = new CompiledModel.Options(accelerator);
            if (accelerator == Accelerator.GPU && gpuFp32) {
                // Full float on the GPU; every other GpuOptions field null = the runtime's default.
                options.setGpuOptions(new CompiledModel.GpuOptions(null, null, null,
                    CompiledModel.GpuOptions.Precision.FP32, null, null, null, null, null, null, null,
                    null, null, null, null));
            }
            if (accelerator == Accelerator.CPU) {
                options.setCpuOptions(new CompiledModel.CpuOptions(threads, null, null));
            }
            CompiledModel model = CompiledModel.create(file.getAbsolutePath(), options, environment);
            return new CompiledModelGraph(environment, model, signatures, accelerator);
        } catch (Throwable t) {
            environment.close();
            throw t;
        }
    }

    /** {@code "cpu"} or {@code "gpu"}. */
    @NonNull
    String acceleratorName() {
        return accelerator == Accelerator.GPU ? "gpu" : "cpu";
    }

    /** Whether every float read so far went through the native slice read. */
    boolean sliceReads() {
        return !anyWholeReads && TaiTensorRead.available();
    }

    /** Inputs bound to a buffer of their own because the shared tensor's buffer did not fit them. */
    int copiedBindings() {
        return copiedBindings;
    }

    @NonNull
    private CompiledModel model() {
        CompiledModel compiled = model;
        if (compiled == null) throw new IllegalStateException("STT runtime is not loaded.");
        return compiled;
    }

    @NonNull
    private TfliteSignatures.Signature signature(@NonNull String key) {
        TfliteSignatures.Signature signature = signatures.get(key);
        if (signature == null) throw new IllegalArgumentException("No signature '" + key + "'");
        return signature;
    }

    @NonNull
    @Override
    public String[] signatureKeys() {
        return signatures.keySet().toArray(new String[0]);
    }

    @NonNull
    @Override
    public String[] inputNames(@NonNull String signature) {
        return signature(signature).inputs.clone();
    }

    @NonNull
    @Override
    public String[] outputNames(@NonNull String signature) {
        return signature(signature).outputs.clone();
    }

    @NonNull
    @Override
    public TensorSpec input(@NonNull String signature, @NonNull String name) {
        return spec(model().getInputTensorType(name, signature));
    }

    @NonNull
    @Override
    public TensorSpec output(@NonNull String signature, @NonNull String name) {
        return spec(model().getOutputTensorType(name, signature));
    }

    @NonNull
    @Override
    public LiteRtGraph.Tensor newInput(@NonNull String signature, @NonNull String name) {
        CompiledModel compiled = model();
        try {
            TensorBufferRequirements requirements = compiled.getInputBufferRequirements(name, signature);
            Buffer buffer = track(new Buffer(compiled.createInputBuffer(name, signature), input(signature, name), requirements));
            buffer.zero();
            return buffer;
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    @NonNull
    @Override
    public LiteRtGraph.Tensor newOutput(@NonNull String signature, @NonNull String name) {
        CompiledModel compiled = model();
        try {
            TensorBufferRequirements requirements = compiled.getOutputBufferRequirements(name, signature);
            return track(new Buffer(compiled.createOutputBuffer(name, signature), output(signature, name), requirements));
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    @NonNull
    private Buffer track(@NonNull Buffer buffer) {
        buffers.add(buffer);
        return buffer;
    }

    @NonNull
    @Override
    public Signature bind(@NonNull String signature, @NonNull Map<String, LiteRtGraph.Tensor> inputs,
                          @NonNull Map<String, LiteRtGraph.Tensor> outputs) {
        CompiledModel compiled = model();
        HashMap<String, TensorBuffer> in = new HashMap<>();
        List<Buffer> sources = new ArrayList<>();
        List<Buffer> twins = new ArrayList<>();
        for (Map.Entry<String, LiteRtGraph.Tensor> entry : inputs.entrySet()) {
            Buffer buffer = (Buffer) entry.getValue();
            TensorBufferRequirements wanted;
            try {
                wanted = compiled.getInputBufferRequirements(entry.getKey(), signature);
            } catch (LiteRtException e) {
                throw unchecked(e);
            }
            if (fits(buffer.requirements, wanted)) {
                in.put(entry.getKey(), buffer.buffer);
            } else {
                // Its own buffer, kept in step with the shared tensor before each run.
                Buffer twin = (Buffer) newInput(signature, entry.getKey());
                in.put(entry.getKey(), twin.buffer);
                sources.add(buffer);
                twins.add(twin);
                copiedBindings++;
            }
        }
        HashMap<String, TensorBuffer> out = new HashMap<>();
        Buffer[] written = new Buffer[outputs.size()];
        List<Buffer> outTwins = new ArrayList<>();
        List<Buffer> outTargets = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, LiteRtGraph.Tensor> entry : outputs.entrySet()) {
            Buffer buffer = (Buffer) entry.getValue();
            TensorBufferRequirements wanted;
            try {
                wanted = compiled.getOutputBufferRequirements(entry.getKey(), signature);
            } catch (LiteRtException e) {
                throw unchecked(e);
            }
            if (fits(buffer.requirements, wanted)) {
                out.put(entry.getKey(), buffer.buffer);
                written[i++] = buffer;
            } else {
                // The shared tensor was made for another signature's output and does not fit this
                // one: this signature writes a buffer of its own, copied into the shared tensor after each run.
                Buffer twin = (Buffer) newOutput(signature, entry.getKey());
                out.put(entry.getKey(), twin.buffer);
                written[i++] = twin;
                outTwins.add(twin);
                outTargets.add(buffer);
                copiedBindings++;
            }
        }
        return new Bound(signature, in, out, sources.toArray(new Buffer[0]), twins.toArray(new Buffer[0]), written,
            outTwins.toArray(new Buffer[0]), outTargets.toArray(new Buffer[0]));
    }

    /**
     * Whether a buffer made to {@code made} can stand where {@code wanted} is asked for: the
     * buffer is of {@code made}'s first supported type (what {@code create*Buffer} allocates) and
     * that type is one {@code wanted} supports, at a size it can hold.
     */
    static boolean fits(@NonNull TensorBufferRequirements made, @NonNull TensorBufferRequirements wanted) {
        List<TensorBufferType> madeTypes = made.getSupportedTypes();
        if (madeTypes.isEmpty()) return false;
        return wanted.getSupportedTypes().contains(madeTypes.get(0)) && made.getBufferSize() >= wanted.getBufferSize();
    }

    @Override
    public void close() {
        for (Buffer buffer : buffers) buffer.buffer.close();
        buffers.clear();
        if (model != null) {
            model.close();
            model = null;
        }
        if (environment != null) {
            environment.close();
            environment = null;
        }
    }

    /** The seam does not throw checked exceptions; a LiteRT failure surfaces as this. */
    @NonNull
    private static IllegalStateException unchecked(@NonNull LiteRtException e) {
        return new IllegalStateException(e.getMessage() == null ? "LiteRT error" : e.getMessage(), e);
    }

    private static void writeFloat(@NonNull TensorBuffer buffer, @NonNull float[] values) {
        try {
            buffer.writeFloat(values);
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    private static void writeInt(@NonNull TensorBuffer buffer, @NonNull int[] values) {
        try {
            buffer.writeInt(values);
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    @NonNull
    private static float[] readFloat(@NonNull TensorBuffer buffer) {
        try {
            return buffer.readFloat();
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    @NonNull
    private static int[] readInt(@NonNull TensorBuffer buffer) {
        try {
            return buffer.readInt();
        } catch (LiteRtException e) {
            throw unchecked(e);
        }
    }

    @NonNull
    private static TensorSpec spec(@NonNull TensorType type) {
        List<Integer> dims = type.getLayout() == null ? null : type.getLayout().getDimensions();
        int[] shape = new int[dims == null ? 0 : dims.size()];
        for (int i = 0; i < shape.length; i++) shape[i] = dims.get(i);
        TensorType.ElementType element = type.getElementType();
        ElementType mapped = element == TensorType.ElementType.FLOAT ? ElementType.FLOAT32
            : element == TensorType.ElementType.INT ? ElementType.INT32 : ElementType.OTHER;
        return new TensorSpec(shape, mapped);
    }

    /** One {@link TensorBuffer}; {@link #version} moves whenever its contents may have changed. */
    private final class Buffer implements LiteRtGraph.Tensor {
        final TensorBuffer buffer;
        final TensorSpec spec;
        final TensorBufferRequirements requirements;
        final int elements;
        /** The C-API handle for slice reads, 0 when they are not trusted for this buffer. */
        final long sliceHandle;
        long version;
        @Nullable private float[] zeroFloats;
        @Nullable private int[] zeroInts;
        @Nullable private float[] floatScratch;
        @Nullable private int[] intScratch;

        Buffer(@NonNull TensorBuffer buffer, @NonNull TensorSpec spec, @NonNull TensorBufferRequirements requirements) {
            this.buffer = buffer;
            this.spec = spec;
            this.requirements = requirements;
            this.elements = spec.elements();
            long handle = 0L;
            if (spec.type == ElementType.FLOAT32 && TaiTensorRead.available()) {
                long raw = buffer.getHandle$third_party_odml_litert_litert_kotlin_litert_api_for_maven();
                // Trusted only when the C API agrees on the tensor's size.
                if (TaiTensorRead.nativePackedBytes(raw) == (long) elements * 4L) handle = raw;
            }
            this.sliceHandle = handle;
        }

        @Override
        public void writeFloats(@NonNull float[] values, int count) {
            if (count == elements && values.length == elements) {
                writeFloat(buffer, values);
            } else {
                float[] scratch = floatScratch;
                if (scratch == null) scratch = floatScratch = new float[elements];
                System.arraycopy(values, 0, scratch, 0, Math.min(count, elements));
                writeFloat(buffer, scratch);
            }
            version++;
        }

        @Override
        public void writeInts(@NonNull int[] values, int count) {
            if (count == elements && values.length == elements) {
                writeInt(buffer, values);
            } else {
                int[] scratch = intScratch;
                if (scratch == null) scratch = intScratch = new int[elements];
                System.arraycopy(values, 0, scratch, 0, Math.min(count, elements));
                writeInt(buffer, scratch);
            }
            version++;
        }

        @Override
        public void readFloats(int offset, @NonNull float[] into, int count) {
            if (sliceHandle != 0L && TaiTensorRead.nativeReadFloats(sliceHandle, offset, into, count)) return;
            anyWholeReads = true;
            System.arraycopy(readFloat(buffer), offset, into, 0, count);
        }

        @Override
        public void zero() {
            if (spec.type == ElementType.INT32) {
                int[] zeros = zeroInts;
                if (zeros == null) zeros = zeroInts = new int[elements];
                writeInt(buffer, zeros);
            } else {
                float[] zeros = zeroFloats;
                if (zeros == null) zeros = zeroFloats = new float[elements];
                writeFloat(buffer, zeros);
            }
            version++;
        }

        @Override
        public void copyFrom(@NonNull LiteRtGraph.Tensor from) {
            Buffer source = (Buffer) from;
            if (spec.type == ElementType.INT32) writeInt(buffer, readInt(source.buffer));
            else writeFloat(buffer, readFloat(source.buffer));
            version++;
        }
    }

    /** A signature with its buffer maps built once; twins are refreshed when their source moved. */
    private final class Bound implements Signature {
        final String key;
        final Map<String, TensorBuffer> inputs;
        final Map<String, TensorBuffer> outputs;
        final Buffer[] sources;
        final Buffer[] twins;
        final long[] copiedAt;
        final Buffer[] written;
        /** Output buffers of this signature's own, and the shared tensors they are copied into after a run. */
        final Buffer[] outTwins;
        final Buffer[] outTargets;

        Bound(@NonNull String key, @NonNull Map<String, TensorBuffer> inputs, @NonNull Map<String, TensorBuffer> outputs,
              @NonNull Buffer[] sources, @NonNull Buffer[] twins, @NonNull Buffer[] written,
              @NonNull Buffer[] outTwins, @NonNull Buffer[] outTargets) {
            this.outTwins = outTwins;
            this.outTargets = outTargets;
            this.key = key;
            this.inputs = inputs;
            this.outputs = outputs;
            this.sources = sources;
            this.twins = twins;
            this.copiedAt = new long[sources.length];
            for (int i = 0; i < copiedAt.length; i++) copiedAt[i] = -1L;
            this.written = written;
        }

        @Override
        public void run() {
            CompiledModel compiled = model();
            for (int i = 0; i < sources.length; i++) {
                if (copiedAt[i] == sources[i].version) continue;
                twins[i].copyFrom(sources[i]);
                copiedAt[i] = sources[i].version;
            }
            try {
                compiled.run(inputs, outputs, key);
            } catch (LiteRtException e) {
                throw unchecked(e);
            }
            for (Buffer buffer : written) buffer.version++;
            for (int i = 0; i < outTwins.length; i++) outTargets[i].copyFrom(outTwins[i]);
        }
    }
}
