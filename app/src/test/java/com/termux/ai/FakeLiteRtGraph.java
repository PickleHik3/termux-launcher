package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link LiteRtGraph} in plain Java arrays for the speech runtime tests: signatures declared
 * with their tensor names and shapes, each run handed to a {@link Script} that reads the bound
 * inputs and fills the bound outputs, and every load, bind, run, zero, copy and close counted.
 */
final class FakeLiteRtGraph implements LiteRtGraph {
    /** What one signature run computes. */
    interface Script {
        void run(@NonNull String signature, @NonNull Map<String, FakeTensor> inputs, @NonNull Map<String, FakeTensor> outputs);
    }

    private final LinkedHashMap<String, LinkedHashMap<String, TensorSpec>> inputs = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, TensorSpec>> outputs = new LinkedHashMap<>();
    final List<Bound> bound = new ArrayList<>();
    final List<FakeTensor> tensors = new ArrayList<>();
    final List<String> runs = new ArrayList<>();
    @NonNull private Script script = (signature, in, out) -> { };
    File loadedFrom;
    int loadedThreads;
    int loads;
    int closes;

    @NonNull
    FakeLiteRtGraph input(@NonNull String signature, @NonNull String name, @NonNull ElementType type, int... shape) {
        inputs.computeIfAbsent(signature, k -> new LinkedHashMap<>()).put(name, new TensorSpec(shape, type));
        outputs.computeIfAbsent(signature, k -> new LinkedHashMap<>());
        return this;
    }

    @NonNull
    FakeLiteRtGraph output(@NonNull String signature, @NonNull String name, @NonNull ElementType type, int... shape) {
        inputs.computeIfAbsent(signature, k -> new LinkedHashMap<>());
        outputs.computeIfAbsent(signature, k -> new LinkedHashMap<>()).put(name, new TensorSpec(shape, type));
        return this;
    }

    @NonNull
    FakeLiteRtGraph script(@NonNull Script script) {
        this.script = script;
        return this;
    }

    /** A loader that hands out this graph and records what it was asked for. */
    @NonNull
    Loader loader() {
        return (model, threads) -> {
            loads++;
            loadedFrom = model;
            loadedThreads = threads;
            return this;
        };
    }

    int runs(@NonNull String signature) {
        int count = 0;
        for (String run : runs) if (run.equals(signature)) count++;
        return count;
    }

    @NonNull
    Bound bindingOf(@NonNull String signature) {
        for (Bound b : bound) if (b.signature.equals(signature)) return b;
        throw new AssertionError("never bound: " + signature);
    }

    @NonNull
    @Override
    public String[] signatureKeys() {
        return inputs.keySet().toArray(new String[0]);
    }

    @NonNull
    @Override
    public String[] inputNames(@NonNull String signature) {
        return names(inputs, signature).keySet().toArray(new String[0]);
    }

    @NonNull
    @Override
    public String[] outputNames(@NonNull String signature) {
        return names(outputs, signature).keySet().toArray(new String[0]);
    }

    @NonNull
    @Override
    public TensorSpec input(@NonNull String signature, @NonNull String name) {
        return spec(inputs, signature, name);
    }

    @NonNull
    @Override
    public TensorSpec output(@NonNull String signature, @NonNull String name) {
        return spec(outputs, signature, name);
    }

    @NonNull
    @Override
    public Tensor newInput(@NonNull String signature, @NonNull String name) {
        return track(new FakeTensor(signature + ":" + name, input(signature, name)));
    }

    @NonNull
    @Override
    public Tensor newOutput(@NonNull String signature, @NonNull String name) {
        return track(new FakeTensor(signature + ":" + name, output(signature, name)));
    }

    @NonNull
    @Override
    public Signature bind(@NonNull String signature, @NonNull Map<String, Tensor> in, @NonNull Map<String, Tensor> out) {
        Bound b = new Bound(signature, cast(in), cast(out));
        bound.add(b);
        return b;
    }

    @Override
    public void close() {
        closes++;
    }

    @NonNull
    private FakeTensor track(@NonNull FakeTensor tensor) {
        tensors.add(tensor);
        return tensor;
    }

    @NonNull
    private static LinkedHashMap<String, TensorSpec> names(@NonNull Map<String, LinkedHashMap<String, TensorSpec>> side, @NonNull String signature) {
        LinkedHashMap<String, TensorSpec> names = side.get(signature);
        if (names == null) throw new IllegalArgumentException("no signature " + signature);
        return names;
    }

    @NonNull
    private static TensorSpec spec(@NonNull Map<String, LinkedHashMap<String, TensorSpec>> side, @NonNull String signature, @NonNull String name) {
        TensorSpec spec = names(side, signature).get(name);
        if (spec == null) throw new IllegalArgumentException("no tensor " + name + " in " + signature);
        return spec;
    }

    @NonNull
    private static LinkedHashMap<String, FakeTensor> cast(@NonNull Map<String, Tensor> tensors) {
        LinkedHashMap<String, FakeTensor> out = new LinkedHashMap<>();
        for (Map.Entry<String, Tensor> entry : tensors.entrySet()) out.put(entry.getKey(), (FakeTensor) entry.getValue());
        return out;
    }

    /** One bound signature; a run checks every declared name is bound, then runs the script. */
    final class Bound implements Signature {
        final String signature;
        final Map<String, FakeTensor> inputs;
        final Map<String, FakeTensor> outputs;

        Bound(@NonNull String signature, @NonNull Map<String, FakeTensor> inputs, @NonNull Map<String, FakeTensor> outputs) {
            this.signature = signature;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        public void run() {
            if (!inputs.keySet().equals(names(FakeLiteRtGraph.this.inputs, signature).keySet())
                || !outputs.keySet().equals(names(FakeLiteRtGraph.this.outputs, signature).keySet())) {
                throw new AssertionError(signature + " bound to " + inputs.keySet() + " -> " + outputs.keySet());
            }
            runs.add(signature);
            script.run(signature, inputs, outputs);
        }
    }

    /** A tensor as a float array (ints stored in a parallel int array). */
    static final class FakeTensor implements Tensor {
        final String name;
        final TensorSpec spec;
        final float[] floats;
        final int[] ints;
        int zeroes;
        int copies;
        int writes;

        FakeTensor(@NonNull String name, @NonNull TensorSpec spec) {
            this.name = name;
            this.spec = spec;
            floats = new float[spec.elements()];
            ints = new int[spec.elements()];
        }

        @Override
        public void writeFloats(@NonNull float[] values, int count) {
            if (count > floats.length) throw new IllegalArgumentException(name + ": " + count + " > " + floats.length);
            System.arraycopy(values, 0, floats, 0, count);
            writes++;
        }

        @Override
        public void writeInts(@NonNull int[] values, int count) {
            if (count > ints.length) throw new IllegalArgumentException(name + ": " + count + " > " + ints.length);
            System.arraycopy(values, 0, ints, 0, count);
            writes++;
        }

        @Override
        public void readFloats(int offset, @NonNull float[] into, int count) {
            System.arraycopy(floats, offset, into, 0, count);
        }

        @Override
        public void zero() {
            Arrays.fill(floats, 0f);
            Arrays.fill(ints, 0);
            zeroes++;
        }

        @Override
        public void copyFrom(@NonNull Tensor from) {
            FakeTensor source = (FakeTensor) from;
            if (source.floats.length != floats.length) throw new IllegalArgumentException(source.name + " -> " + name + ": sizes differ");
            System.arraycopy(source.floats, 0, floats, 0, floats.length);
            System.arraycopy(source.ints, 0, ints, 0, ints.length);
            copies++;
        }
    }
}
