package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A reader for the one kind of NumPy array the TTS runtime needs: a little-endian float32,
 * C-ordered {@code .npy}, read straight out of an {@code .npz} (a zip of {@code .npy} entries, which
 * is what {@code voices.npz} is). No NumPy on the phone and no general parser: anything else — a
 * different dtype, Fortran order, a pickled object array — is refused with the header in the
 * message, so a changed upstream file fails loudly at load instead of speaking noise.
 *
 * <p>The data is copied into the result through one 64 KB scratch buffer, so a 400 × 256 voice
 * table (410 KB) costs its own float array and nothing else that outlives the call.
 */
final class TaiNpy {
    private static final byte[] MAGIC = {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'};
    private static final Pattern DESCR = Pattern.compile("'descr'\\s*:\\s*'([^']*)'");
    private static final Pattern FORTRAN = Pattern.compile("'fortran_order'\\s*:\\s*(True|False)");
    private static final Pattern SHAPE = Pattern.compile("'shape'\\s*:\\s*\\(([^)]*)\\)");
    private static final int SCRATCH_BYTES = 64 * 1024;

    private TaiNpy() {}

    /** A float32 array and its shape; {@code data.length} is the product of {@code shape}. */
    static final class FloatArray {
        @NonNull final int[] shape;
        @NonNull final float[] data;

        FloatArray(@NonNull int[] shape, @NonNull float[] data) {
            this.shape = shape;
            this.data = data;
        }

        /** Row {@code row} of a 2-D array, copied into {@code out} (which must hold a whole row). */
        void row(int row, @NonNull float[] out) {
            if (shape.length != 2) throw new IllegalStateException("Not a 2-D array");
            int columns = shape[1];
            System.arraycopy(data, row * columns, out, 0, columns);
        }
    }

    /** Reads entry {@code name} (with or without {@code .npy}) from the {@code .npz} at {@code npz}. */
    @NonNull
    static FloatArray readFromNpz(@NonNull File npz, @NonNull String name) throws IOException {
        String entryName = name.endsWith(".npy") ? name : name + ".npy";
        try (ZipFile zip = new ZipFile(npz)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) throw new IOException(npz.getName() + " has no entry " + entryName);
            try (InputStream in = zip.getInputStream(entry)) {
                return read(in);
            }
        }
    }

    /** Reads one {@code .npy} from {@code in}, which is left positioned after the data. */
    @NonNull
    static FloatArray read(@NonNull InputStream in) throws IOException {
        byte[] prefix = new byte[8];
        readFully(in, prefix, 0, 8);
        for (int i = 0; i < MAGIC.length; i++) {
            if (prefix[i] != MAGIC[i]) throw new IOException("Not a .npy file");
        }
        int major = prefix[6] & 0xff;
        int headerLength;
        if (major == 1) {
            byte[] length = new byte[2];
            readFully(in, length, 0, 2);
            headerLength = (length[0] & 0xff) | (length[1] & 0xff) << 8;
        } else if (major == 2 || major == 3) {
            byte[] length = new byte[4];
            readFully(in, length, 0, 4);
            headerLength = (length[0] & 0xff) | (length[1] & 0xff) << 8 | (length[2] & 0xff) << 16 | (length[3] & 0xff) << 24;
        } else {
            throw new IOException("Unsupported .npy version " + major);
        }
        if (headerLength <= 0 || headerLength > 1 << 20) throw new IOException("Bad .npy header length " + headerLength);
        byte[] headerBytes = new byte[headerLength];
        readFully(in, headerBytes, 0, headerLength);
        String header = new String(headerBytes, major == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
        int[] shape = parseHeader(header);
        long count = 1;
        for (int dim : shape) count *= dim;
        if (count > Integer.MAX_VALUE / 4) throw new IOException("Array too large: " + header.trim());
        float[] data = new float[(int) count];
        byte[] scratch = new byte[(int) Math.min(SCRATCH_BYTES, Math.max(4, count * 4))];
        ByteBuffer view = ByteBuffer.wrap(scratch).order(ByteOrder.LITTLE_ENDIAN);
        int written = 0;
        while (written < data.length) {
            int floats = Math.min(scratch.length / 4, data.length - written);
            readFully(in, scratch, 0, floats * 4);
            view.clear();
            view.asFloatBuffer().get(data, written, floats);
            written += floats;
        }
        return new FloatArray(shape, data);
    }

    /**
     * The shape a header describes, after checking it is a little-endian float32, C-ordered
     * array. The header is the Python dict literal NumPy writes, e.g.
     * {@code {'descr': '<f4', 'fortran_order': False, 'shape': (400, 256), }}.
     */
    @NonNull
    static int[] parseHeader(@NonNull String header) throws IOException {
        Matcher descr = DESCR.matcher(header);
        if (!descr.find()) throw new IOException("No descr in .npy header: " + header.trim());
        String dtype = descr.group(1);
        if (!"<f4".equals(dtype)) throw new IOException("Only little-endian float32 is supported, got " + dtype);
        Matcher fortran = FORTRAN.matcher(header);
        if (fortran.find() && "True".equals(fortran.group(1))) throw new IOException("Fortran-ordered arrays are not supported");
        Matcher shapeMatch = SHAPE.matcher(header);
        if (!shapeMatch.find()) throw new IOException("No shape in .npy header: " + header.trim());
        List<Integer> dims = new ArrayList<>();
        for (String part : shapeMatch.group(1).split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                int dim = Integer.parseInt(trimmed);
                if (dim < 0) throw new IOException("Negative dimension in .npy shape");
                dims.add(dim);
            } catch (NumberFormatException e) {
                throw new IOException("Bad .npy shape: " + shapeMatch.group(1));
            }
        }
        int[] shape = new int[dims.size()];
        for (int i = 0; i < shape.length; i++) shape[i] = dims.get(i);
        return shape;
    }

    private static void readFully(@NonNull InputStream in, @NonNull byte[] buffer, int offset, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int read = in.read(buffer, offset + done, length - done);
            if (read < 0) throw new EOFException("Truncated .npy data");
            done += read;
        }
    }
}
