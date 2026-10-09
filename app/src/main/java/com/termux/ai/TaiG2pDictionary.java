package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * The pronunciation dictionary ({@code g2p_dict.txt.gz}: 275k lines of {@code word<TAB>ipa}, 7.5 MB
 * uncompressed, OpenPhonemizer, Clear BSD) held the compact way: the decompressed bytes in one
 * array and the start of every line in an {@code int[]}, searched by binary search over the raw
 * UTF-8. A {@code HashMap<String, String>} of the same data would cost around 50 MB of heap in the
 * runtime process; this costs the file itself plus 1.1 MB of offsets, and only the IPA of a word
 * actually looked up is ever turned into a String.
 *
 * <p>The shipped file is sorted by byte order with lower-case ASCII words, which the loader
 * checks; an unsorted file (a test fixture, a future upstream change) is sorted once at load, and
 * when a word appears twice the later line wins, as it did in the Python and Kotlin maps.
 */
final class TaiG2pDictionary {
    private final byte[] data;
    private final int[] starts;
    private final int size;

    private TaiG2pDictionary(@NonNull byte[] data, @NonNull int[] starts, int size) {
        this.data = data;
        this.starts = starts;
        this.size = size;
    }

    /**
     * Loads a gzip file. The gzip trailer records the uncompressed length, so the byte array is
     * allocated once at its final size instead of grown and copied.
     */
    @NonNull
    static TaiG2pDictionary loadGzip(@NonNull File gz) throws IOException {
        int expected = gzipUncompressedSize(gz);
        try (InputStream in = new GZIPInputStream(new FileInputStream(gz), 64 * 1024)) {
            return read(in, expected);
        }
    }

    /** Builds a dictionary from an already decompressed {@code word<TAB>ipa} stream. */
    @NonNull
    static TaiG2pDictionary read(@NonNull InputStream in, int sizeHint) throws IOException {
        byte[] buffer = new byte[Math.max(4096, sizeHint > 0 ? sizeHint : 1 << 20)];
        int length = 0;
        while (true) {
            if (length == buffer.length) {
                // Full at exactly the hinted size is the normal end; a byte past it means the hint
                // was short (no trailer, or a test stream), so grow and go on.
                int probe = in.read();
                if (probe < 0) break;
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
                buffer[length++] = (byte) probe;
                continue;
            }
            int read = in.read(buffer, length, buffer.length - length);
            if (read < 0) break;
            length += read;
        }
        if (length != buffer.length) buffer = Arrays.copyOf(buffer, length);
        return fromBytes(buffer);
    }

    /** Indexes {@code data} in place; it must not be changed afterwards. */
    @NonNull
    static TaiG2pDictionary fromBytes(@NonNull byte[] data) {
        int lines = 0;
        for (byte b : data) if (b == '\n') lines++;
        int[] starts = new int[lines + 1];
        int count = 0;
        int lineStart = 0;
        boolean sorted = true;
        for (int i = 0; i <= data.length; i++) {
            if (i < data.length && data[i] != '\n') continue;
            if (tabOf(data, lineStart, i) > lineStart) {
                if (count > 0 && compareWords(data, starts[count - 1], lineStart) > 0) sorted = false;
                starts[count++] = lineStart;
            }
            lineStart = i + 1;
        }
        if (!sorted) count = sortAndDedupe(data, starts, count);
        else count = dedupeSorted(data, starts, count);
        return new TaiG2pDictionary(data, starts, count);
    }

    int size() {
        return size;
    }

    /** The IPA for {@code word} (lower case, as the dictionary stores it), or {@code null}. */
    @Nullable
    String get(@NonNull String word) {
        if (word.isEmpty() || size == 0) return null;
        byte[] key = word.getBytes(StandardCharsets.UTF_8);
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = compareKey(data, starts[mid], key);
            if (cmp < 0) low = mid + 1;
            else if (cmp > 0) high = mid - 1;
            else return ipaAt(starts[mid]);
        }
        return null;
    }

    boolean contains(@NonNull String word) {
        return get(word) != null;
    }

    @NonNull
    private String ipaAt(int lineStart) {
        int end = lineEnd(data, lineStart);
        int tab = tabOf(data, lineStart, end);
        int ipaEnd = end;
        if (ipaEnd > tab + 1 && data[ipaEnd - 1] == '\r') ipaEnd--;
        return new String(data, tab + 1, Math.max(0, ipaEnd - tab - 1), StandardCharsets.UTF_8);
    }

    /** Compares the word of the line at {@code start} with {@code key}, byte by byte, unsigned. */
    private static int compareKey(@NonNull byte[] data, int start, @NonNull byte[] key) {
        int i = 0;
        while (true) {
            int a = start + i < data.length ? data[start + i] & 0xff : '\n';
            boolean wordEnded = a == '\t' || a == '\n';
            boolean keyEnded = i >= key.length;
            if (wordEnded || keyEnded) {
                if (wordEnded && keyEnded) return 0;
                return wordEnded ? -1 : 1;
            }
            int b = key[i] & 0xff;
            if (a != b) return a < b ? -1 : 1;
            i++;
        }
    }

    /** Compares the words of two lines of the same buffer. */
    private static int compareWords(@NonNull byte[] data, int left, int right) {
        int i = 0;
        while (true) {
            int a = left + i < data.length ? data[left + i] & 0xff : '\n';
            int b = right + i < data.length ? data[right + i] & 0xff : '\n';
            boolean leftEnded = a == '\t' || a == '\n';
            boolean rightEnded = b == '\t' || b == '\n';
            if (leftEnded || rightEnded) {
                if (leftEnded && rightEnded) return 0;
                return leftEnded ? -1 : 1;
            }
            if (a != b) return a < b ? -1 : 1;
            i++;
        }
    }

    /** Sorts the line index by word, stable so a later duplicate stays after an earlier one, then dedupes. */
    private static int sortAndDedupe(@NonNull byte[] data, @NonNull int[] starts, int count) {
        Integer[] boxed = new Integer[count];
        for (int i = 0; i < count; i++) boxed[i] = starts[i];
        Arrays.sort(boxed, (a, b) -> compareWords(data, a, b));
        for (int i = 0; i < count; i++) starts[i] = boxed[i];
        return dedupeSorted(data, starts, count);
    }

    /** Keeps the last line of every run of equal words: the later line wins, as a map insert would. */
    private static int dedupeSorted(@NonNull byte[] data, @NonNull int[] starts, int count) {
        int out = 0;
        for (int i = 0; i < count; i++) {
            if (i + 1 < count && compareWords(data, starts[i], starts[i + 1]) == 0) continue;
            starts[out++] = starts[i];
        }
        return out;
    }

    private static int lineEnd(@NonNull byte[] data, int start) {
        int i = start;
        while (i < data.length && data[i] != '\n') i++;
        return i;
    }

    /** The tab in {@code [start, end)}, or {@code start - 1} when the line has none. */
    private static int tabOf(@NonNull byte[] data, int start, int end) {
        for (int i = start; i < end; i++) if (data[i] == '\t') return i;
        return start - 1;
    }

    /** ISIZE from the gzip trailer: the uncompressed length modulo 2^32; 0 when it cannot be read. */
    private static int gzipUncompressedSize(@NonNull File gz) {
        try (RandomAccessFile file = new RandomAccessFile(gz, "r")) {
            if (file.length() < 18) return 0;
            file.seek(file.length() - 4);
            int b0 = file.read(), b1 = file.read(), b2 = file.read(), b3 = file.read();
            long size = (b0 & 0xffL) | (b1 & 0xffL) << 8 | (b2 & 0xffL) << 16 | (b3 & 0xffL) << 24;
            return size > 0 && size < 256L * 1024 * 1024 ? (int) size : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}
