package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The signature keys and their input and output tensor names, read straight from a {@code .tflite}
 * file's flatbuffer ({@code Model.signature_defs}). LiteRT 2.3.2's {@code CompiledModel} has no
 * call that lists them, and opening a second runtime on the file just to ask would hold a second
 * copy of a 290 MB graph; this reads a few kilobytes of the file's header through a read-only
 * mapping instead, so only the pages it touches are ever read.
 *
 * <p>The layout is the TFLite schema's ({@code tensorflow/compiler/mlir/lite/schema/schema.fbs}):
 * {@code Model} field 7 is {@code signature_defs:[SignatureDef]}; a {@code SignatureDef} is
 * {@code inputs:[TensorMap]} (0), {@code outputs:[TensorMap]} (1), {@code signature_key:string}
 * (2); a {@code TensorMap} is {@code name:string} (0). Names come in file order. Every offset is
 * bounds-checked; a malformed file is an {@link IOException}, never a wild read.
 */
final class TfliteSignatures {
    private static final int MODEL_SIGNATURE_DEFS = 7;
    private static final int SIGNATURE_INPUTS = 0;
    private static final int SIGNATURE_OUTPUTS = 1;
    private static final int SIGNATURE_KEY = 2;
    private static final int TENSOR_MAP_NAME = 0;

    /** One signature's tensor names, in the order the file lists them. */
    static final class Signature {
        @NonNull final String[] inputs;
        @NonNull final String[] outputs;

        Signature(@NonNull String[] inputs, @NonNull String[] outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }
    }

    private TfliteSignatures() {
    }

    /** Every signature of the model at {@code file}, keyed by signature key, in file order. */
    @NonNull
    static Map<String, Signature> read(@NonNull File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel channel = raf.getChannel()) {
            long size = channel.size();
            // The flatbuffer root and its tables sit at the front; a >2 GB model keeps its weights
            // past the flatbuffer (offset buffers), so the first 2 GB always hold what is read here.
            long mapped = Math.min(size, Integer.MAX_VALUE);
            ByteBuffer bytes = channel.map(FileChannel.MapMode.READ_ONLY, 0, mapped);
            return read(bytes);
        }
    }

    /** As {@link #read(File)}, over the model's bytes. */
    @NonNull
    static Map<String, Signature> read(@NonNull ByteBuffer source) throws IOException {
        ByteBuffer bytes = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int base = bytes.position();
        Reader reader = new Reader(bytes, base);
        int model = reader.indirect(base);
        LinkedHashMap<String, Signature> out = new LinkedHashMap<>();
        int defs = reader.field(model, MODEL_SIGNATURE_DEFS);
        if (defs < 0) return out;
        int defsVector = reader.indirect(defs);
        int count = reader.vectorLength(defsVector);
        for (int i = 0; i < count; i++) {
            int def = reader.indirect(defsVector + 4 + 4 * i);
            int keyField = reader.field(def, SIGNATURE_KEY);
            String key = keyField < 0 ? "" : reader.string(reader.indirect(keyField));
            out.put(key, new Signature(names(reader, def, SIGNATURE_INPUTS), names(reader, def, SIGNATURE_OUTPUTS)));
        }
        return out;
    }

    @NonNull
    private static String[] names(@NonNull Reader reader, int def, int field) throws IOException {
        int at = reader.field(def, field);
        if (at < 0) return new String[0];
        int vector = reader.indirect(at);
        int count = reader.vectorLength(vector);
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            int map = reader.indirect(vector + 4 + 4 * i);
            int name = reader.field(map, TENSOR_MAP_NAME);
            names[i] = name < 0 ? "" : reader.string(reader.indirect(name));
        }
        return names;
    }

    /** Bounds-checked flatbuffer reads; every position is absolute in {@code bytes}. */
    static final class Reader {
        final ByteBuffer bytes;
        final int base;
        final int limit;

        Reader(@NonNull ByteBuffer bytes, int base) {
            this.bytes = bytes;
            this.base = base;
            this.limit = bytes.limit();
        }

        private int checked(int at, int width) throws IOException {
            if (at < base || at > limit - width) throw new IOException("Malformed .tflite: offset " + at + " out of range");
            return at;
        }

        int u32(int at) throws IOException {
            int value = bytes.getInt(checked(at, 4));
            if (value < 0) throw new IOException("Malformed .tflite: offset too large at " + at);
            return value;
        }

        /** The position a {@code uoffset_t} at {@code at} points to. */
        int indirect(int at) throws IOException {
            int target = at + u32(at);
            return checked(target, 4);
        }

        /** The position of field {@code index} of the table at {@code table}, or {@code -1} when absent. */
        int field(int table, int index) throws IOException {
            int vtable = table - bytes.getInt(checked(table, 4));
            int vtableSize = bytes.getShort(checked(vtable, 2)) & 0xFFFF;
            int slot = 4 + 2 * index;
            if (slot + 2 > vtableSize) return -1;
            int offset = bytes.getShort(checked(vtable + slot, 2)) & 0xFFFF;
            return offset == 0 ? -1 : checked(table + offset, 4);
        }

        int vectorLength(int vector) throws IOException {
            int length = u32(vector);
            checked(vector + 4, 0);
            if ((long) length * 4L > limit - vector - 4) throw new IOException("Malformed .tflite: vector too long at " + vector);
            return length;
        }

        @NonNull
        String string(int at) throws IOException {
            int length = u32(at);
            if ((long) length > limit - at - 4) throw new IOException("Malformed .tflite: string too long at " + at);
            byte[] raw = new byte[length];
            for (int i = 0; i < length; i++) raw[i] = bytes.get(at + 4 + i);
            return new String(raw, StandardCharsets.UTF_8);
        }
    }
}
