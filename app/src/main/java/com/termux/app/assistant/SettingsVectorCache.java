package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The corpus vectors on disk, in app-private storage. A file is good for one embedding model and
 * one corpus hash; any other file is a miss and gets rewritten.
 */
final class SettingsVectorCache {
    private static final int MAGIC = 0x53564331; // "SVC1"

    private SettingsVectorCache() {
    }

    /** The vectors saved for exactly this model, corpus and count, or null. */
    @Nullable
    static float[][] read(@NonNull File file, @NonNull String modelId, @NonNull String corpusHash, int count) {
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC) return null;
            if (!modelId.equals(in.readUTF()) || !corpusHash.equals(in.readUTF())) return null;
            if (in.readInt() != count) return null;
            int dims = in.readInt();
            if (dims <= 0 || dims > 8192) return null;
            float[][] vectors = new float[count][dims];
            for (float[] vector : vectors) for (int i = 0; i < dims; i++) vector[i] = in.readFloat();
            return vectors;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static void write(@NonNull File file, @NonNull String modelId, @NonNull String corpusHash,
                      @NonNull float[][] vectors) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("no cache directory");
        File tmp = new File(file.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeUTF(modelId);
            out.writeUTF(corpusHash);
            out.writeInt(vectors.length);
            out.writeInt(vectors.length == 0 ? 0 : vectors[0].length);
            for (float[] vector : vectors) for (float v : vector) out.writeFloat(v);
        }
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("cache not replaced");
        }
    }
}
