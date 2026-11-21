package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Map;

/**
 * The one seam the speech-to-text runtimes ({@link WhisperSttRuntime}, {@link ParakeetSttRuntime})
 * load, run and close a LiteRT graph through, so the runtime behind it can change without the
 * runtimes noticing. Today the only implementation is {@link InterpreterGraph}, the stock
 * {@code org.tensorflow.lite.Interpreter} with XNNPACK; a CompiledModel implementation is meant
 * to slot in beside it.
 *
 * <p>The shape follows what both runtimes can do: a graph is looked at signature by signature,
 * tensor names are bound by name, a {@link Tensor} is made once per graph input or output and
 * reused across runs, and a {@link Signature} is a signature bound once to its tensors, run as
 * often as needed. One tensor may be bound into several signatures (Whisper's encoder output is
 * the decoder's audio input; Parakeet's LSTM state is shared by {@code decode} and
 * {@code decode_1}). Tensors live until the graph is closed; nothing outlives {@link #close()}.
 *
 * <p>Not thread-safe: the runtimes call it under their own monitor.
 */
interface LiteRtGraph extends AutoCloseable {
    /** Opens the graph at {@code model} for CPU inference on {@code threads} threads. */
    interface Loader {
        @NonNull
        LiteRtGraph load(@NonNull File model, int threads) throws Exception;
    }

    /** The element types the speech graphs use; anything else is {@link #OTHER}. */
    enum ElementType { FLOAT32, INT32, OTHER }

    /** A tensor's static shape and element type, as the graph declares it. */
    final class TensorSpec {
        @NonNull final int[] shape;
        @NonNull final ElementType type;

        TensorSpec(@NonNull int[] shape, @NonNull ElementType type) {
            this.shape = shape;
            this.type = type;
        }

        int rank() {
            return shape.length;
        }

        /** The element count, a dynamic ({@code <= 0}) dimension counted as 1. */
        int elements() {
            int count = 1;
            for (int dim : shape) count *= Math.max(1, dim);
            return count;
        }
    }

    /** One tensor's storage, made by the graph for one input or output and reused across runs. */
    interface Tensor {
        /** Writes {@code count} floats from {@code values[0]} into the tensor's first elements. */
        void writeFloats(@NonNull float[] values, int count);

        /** Writes {@code count} ints from {@code values[0]} into the tensor's first elements. */
        void writeInts(@NonNull int[] values, int count);

        /** Reads {@code count} floats starting at element {@code offset} into {@code into[0]}. */
        void readFloats(int offset, @NonNull float[] into, int count);

        /** Sets every element to zero. */
        void zero();

        /** Copies every element of {@code from}, a tensor of the same size from this graph. */
        void copyFrom(@NonNull Tensor from);
    }

    /** A signature bound to its tensors; {@link #run()} reads the inputs and fills the outputs. */
    interface Signature {
        void run();
    }

    @NonNull
    String[] signatureKeys();

    @NonNull
    String[] inputNames(@NonNull String signature);

    @NonNull
    String[] outputNames(@NonNull String signature);

    @NonNull
    TensorSpec input(@NonNull String signature, @NonNull String name);

    @NonNull
    TensorSpec output(@NonNull String signature, @NonNull String name);

    /** New storage sized for the input {@code name} of {@code signature}, zero-filled. */
    @NonNull
    Tensor newInput(@NonNull String signature, @NonNull String name);

    /** New storage sized for the output {@code name} of {@code signature}. */
    @NonNull
    Tensor newOutput(@NonNull String signature, @NonNull String name);

    /** Binds {@code signature} by name to these tensors, every input and output of it named once. */
    @NonNull
    Signature bind(@NonNull String signature, @NonNull Map<String, Tensor> inputs, @NonNull Map<String, Tensor> outputs);

    /** Releases the graph and every tensor made by it. Safe to call twice. */
    @Override
    void close();
}
