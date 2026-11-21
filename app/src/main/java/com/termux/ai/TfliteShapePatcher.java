package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites the declared shapes of a {@code .tflite} graph's inputs inside its flatbuffer, in
 * place and at the same rank, so no offset moves. LiteRT 2.3.2's {@code Interpreter} allocates
 * every tensor at the model's declared shapes inside its constructor and has no way to resize
 * first; a graph whose declared input shapes carry dynamic dimensions ({@code -1}) can fail there
 * (XNNPACK cannot prepare them). Opening a copy whose inputs already carry a real, valid shape
 * gets past that first allocation, after which the usual {@code resizeInput} +
 * {@code allocateTensors} per run works.
 *
 * <p>Layout (TFLite {@code schema.fbs}): {@code Model.subgraphs} is field 2; a {@code SubGraph}
 * has {@code tensors} (0) and {@code inputs} (1); a {@code Tensor} has {@code shape} (0) and
 * {@code name} (3). Reads go through {@link TfliteSignatures.Reader}, so every offset is
 * bounds-checked and a malformed file is an {@link IOException}.
 */
final class TfliteShapePatcher {
    private static final int MODEL_SUBGRAPHS = 2;
    private static final int SUBGRAPH_TENSORS = 0;
    private static final int SUBGRAPH_INPUTS = 1;
    private static final int TENSOR_SHAPE = 0;
    private static final int TENSOR_NAME = 3;

    private TfliteShapePatcher() {
    }

    /**
     * The bytes of {@code file} in a direct buffer, with the inputs in {@code shapes} (canonical
     * names) rewritten. Nothing is written to disk. (A copy-on-write mapping would avoid the read
     * but needs the file opened for writing.) The returned buffer must stay referenced for as long
     * as an interpreter built on it lives.
     */
    @NonNull
    static ByteBuffer readPatched(@NonNull File file, @NonNull Map<String, int[]> shapes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel channel = raf.getChannel()) {
            long size = channel.size();
            if (size <= 0 || size > Integer.MAX_VALUE) throw new IOException(file.getName() + ": unexpected size " + size);
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) size).order(ByteOrder.nativeOrder());
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException(file.getName() + ": ended early");
            }
            buffer.flip();
            patch(buffer, shapes);
            return buffer;
        }
    }

    /** Rewrites, in {@code model} (modified in place), the declared shape of each input named in {@code shapes}. */
    static void patch(@NonNull ByteBuffer model, @NonNull Map<String, int[]> shapes) throws IOException {
        ByteBuffer bytes = model.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        Located located = locate(bytes, bytes.position());
        for (Map.Entry<String, int[]> entry : shapes.entrySet()) {
            int[] wanted = entry.getValue();
            int tensor = located.input(entry.getKey());
            int vector = located.shapeVectors[tensor];
            int rank = vector < 0 ? 0 : bytes.getInt(vector);
            if (vector < 0 || rank != wanted.length) {
                throw new IOException(entry.getKey() + ": declared rank " + rank + ", wanted " + wanted.length);
            }
            for (int other = 0; other < located.shapeVectors.length; other++) {
                if (other != tensor && located.shapeVectors[other] == vector) {
                    throw new IOException(entry.getKey() + ": shape vector shared with tensor " + located.names[other]);
                }
            }
            for (int k = 0; k < rank; k++) bytes.putInt(vector + 4 + 4 * k, wanted[k]);
        }
    }

    /** The declared shapes of subgraph 0's inputs, keyed by canonical name, in input order. */
    @NonNull
    static Map<String, int[]> inputShapes(@NonNull ByteBuffer model) throws IOException {
        ByteBuffer bytes = model.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        Located located = locate(bytes, bytes.position());
        Map<String, int[]> out = new LinkedHashMap<>();
        for (int index : located.inputs) {
            int vector = located.shapeVectors[index];
            int[] shape = new int[vector < 0 ? 0 : bytes.getInt(vector)];
            for (int k = 0; k < shape.length; k++) shape[k] = bytes.getInt(vector + 4 + 4 * k);
            out.put(canonical(located.names[index]), shape);
        }
        return out;
    }

    /** {@code serving_default_x:0} and {@code x} are both {@code x}. */
    @NonNull
    static String canonical(@NonNull String name) {
        String out = name.startsWith("serving_default_") ? name.substring("serving_default_".length()) : name;
        int colon = out.indexOf(':');
        return colon >= 0 ? out.substring(0, colon) : out;
    }

    private static final class Located {
        final String[] names;
        final int[] shapeVectors;
        final int[] inputs;

        Located(String[] names, int[] shapeVectors, int[] inputs) {
            this.names = names;
            this.shapeVectors = shapeVectors;
            this.inputs = inputs;
        }

        int input(String canonical) throws IOException {
            for (int index : inputs) if (canonical(names[index]).equals(canonical)) return index;
            throw new IOException("No subgraph input " + canonical);
        }
    }

    private static Located locate(ByteBuffer bytes, int base) throws IOException {
        TfliteSignatures.Reader reader = new TfliteSignatures.Reader(bytes, base);
        int model = reader.indirect(base);
        int subgraphsField = reader.field(model, MODEL_SUBGRAPHS);
        if (subgraphsField < 0) throw new IOException("Malformed .tflite: no subgraphs");
        int subgraphs = reader.indirect(subgraphsField);
        if (reader.vectorLength(subgraphs) < 1) throw new IOException("Malformed .tflite: no subgraphs");
        int subgraph = reader.indirect(subgraphs + 4);
        int tensorsField = reader.field(subgraph, SUBGRAPH_TENSORS);
        int tensors = tensorsField < 0 ? -1 : reader.indirect(tensorsField);
        int count = tensors < 0 ? 0 : reader.vectorLength(tensors);
        String[] names = new String[count];
        int[] shapeVectors = new int[count];
        for (int i = 0; i < count; i++) {
            int tensor = reader.indirect(tensors + 4 + 4 * i);
            int name = reader.field(tensor, TENSOR_NAME);
            names[i] = name < 0 ? "" : reader.string(reader.indirect(name));
            int shape = reader.field(tensor, TENSOR_SHAPE);
            shapeVectors[i] = shape < 0 ? -1 : reader.indirect(shape);
            if (shapeVectors[i] >= 0) reader.vectorLength(shapeVectors[i]);
        }
        int inputsField = reader.field(subgraph, SUBGRAPH_INPUTS);
        int[] inputs = new int[0];
        if (inputsField >= 0) {
            int vector = reader.indirect(inputsField);
            inputs = new int[reader.vectorLength(vector)];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = bytes.getInt(vector + 4 + 4 * i);
                if (inputs[i] < 0 || inputs[i] >= count) throw new IOException("Malformed .tflite: input tensor " + inputs[i]);
            }
        }
        return new Located(names, shapeVectors, inputs);
    }
}
