package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SentencePieceBpeTokenizer {
    private static final int TOKEN_UNK = 3;
    /**
     * Upper bound on the piece count a model file may claim, well above the real 262144: the map is
     * sized from that count, so a corrupt or hostile file would otherwise allocate before anything
     * about it has been validated.
     */
    private static final int MAX_PIECES = 1 << 21;
    private static final char SPACE_MARKER = '▁';

    /** Character classes that are not scripts: every digit stands alone, whitespace runs are resolved separately. */
    private static final Object CLASS_DIGIT = new Object();
    private static final Object CLASS_WHITESPACE = new Object();

    @NonNull private final Map<String, Integer> pieceToId;
    @NonNull private final float[] scores;

    SentencePieceBpeTokenizer(@NonNull List<String> pieces, @NonNull float[] scores) {
        if (pieces.size() != scores.length) {
            throw new IllegalArgumentException("pieces and scores must have the same length");
        }
        this.scores = Arrays.copyOf(scores, scores.length);
        this.pieceToId = new HashMap<>(Math.max(16, (int) (pieces.size() / 0.75f) + 1));
        for (int i = 0; i < pieces.size(); i++) {
            this.pieceToId.put(pieces.get(i), i);
        }
    }

    @NonNull
    static SentencePieceBpeTokenizer fromModelFile(@NonNull File file) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(readAll(file)).order(ByteOrder.LITTLE_ENDIAN);
        List<String> pieces = new ArrayList<>();
        float[] scores = new float[1024];
        while (buffer.hasRemaining()) {
            long key = readVarint(buffer);
            int field = (int) (key >>> 3);
            int wireType = (int) (key & 7);
            if (field == 1 && wireType == 2) {
                int length = (int) readVarint(buffer);
                int end = buffer.position() + length;
                String piece = "";
                float score = 0f;
                while (buffer.position() < end) {
                    long pieceKey = readVarint(buffer);
                    int pieceField = (int) (pieceKey >>> 3);
                    int pieceWire = (int) (pieceKey & 7);
                    if (pieceField == 1 && pieceWire == 2) {
                        int pieceLength = (int) readVarint(buffer);
                        byte[] raw = new byte[pieceLength];
                        buffer.get(raw);
                        piece = new String(raw, StandardCharsets.UTF_8);
                    } else if (pieceField == 2 && pieceWire == 5) {
                        score = buffer.getFloat();
                    } else {
                        skipField(buffer, pieceWire);
                    }
                }
                if (pieces.size() >= MAX_PIECES) {
                    throw new IOException("Sentencepiece model declares more than " + MAX_PIECES
                        + " pieces: " + file.getAbsolutePath());
                }
                if (pieces.size() == scores.length) {
                    scores = Arrays.copyOf(scores, scores.length * 2);
                }
                scores[pieces.size()] = score;
                pieces.add(piece);
            } else {
                skipField(buffer, wireType);
            }
        }
        return new SentencePieceBpeTokenizer(pieces, Arrays.copyOf(scores, pieces.size()));
    }

    /** Token ids without bos/eos; the caller frames the sequence. */
    @NonNull
    int[] encode(@NonNull String text) {
        String marked = text.replace(' ', SPACE_MARKER);
        int[] codePoints = toCodePoints(marked);
        if (codePoints.length == 0) return new int[0];
        Object[] classes = classify(codePoints);
        List<Integer> ids = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < codePoints.length; i++) {
            boolean boundary = i > 0
                && (classes[i] == CLASS_DIGIT || classes[i - 1] == CLASS_DIGIT || classes[i] != classes[i - 1]);
            if (boundary) {
                encodeSegment(segment.toString(), ids);
                segment.setLength(0);
            }
            segment.appendCodePoint(codePoints[i]);
        }
        encodeSegment(segment.toString(), ids);
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    int pieceToId(@NonNull String piece) {
        Integer id = pieceToId.get(piece);
        return id == null ? -1 : id;
    }

    int vocabularySize() {
        return scores.length;
    }

    @NonNull
    private Object[] classify(@NonNull int[] codePoints) {
        Object[] classes = new Object[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            if (Character.isDigit(codePoints[i])) {
                classes[i] = CLASS_DIGIT;
            } else if (codePoints[i] == SPACE_MARKER) {
                classes[i] = CLASS_WHITESPACE;
            } else {
                classes[i] = Character.UnicodeScript.of(codePoints[i]);
            }
        }
        // A whitespace run joins the segment of the character that follows it, so "a  b" is one segment.
        // It stays its own segment before a digit or at end of string, which is what makes "  1" emit the
        // single piece "▁▁" instead of two separate whitespace pieces.
        for (int end = codePoints.length - 1; end >= 0; end--) {
            if (classes[end] != CLASS_WHITESPACE) continue;
            int start = end;
            while (start > 0 && classes[start - 1] == CLASS_WHITESPACE) start--;
            int following = end + 1;
            if (following < classes.length && classes[following] != CLASS_DIGIT) {
                Object inherited = classes[following];
                for (int i = start; i <= end; i++) classes[i] = inherited;
            }
            end = start;
        }
        return classes;
    }

    private void encodeSegment(@NonNull String segment, @NonNull List<Integer> out) {
        if (segment.isEmpty()) return;
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < segment.length(); ) {
            int codePoint = segment.codePointAt(i);
            i += Character.charCount(codePoint);
            String symbol = new String(Character.toChars(codePoint));
            if (pieceToId.containsKey(symbol)) {
                symbols.add(symbol);
            } else {
                // Byte fallback pieces are spelled "<0xNN>" with uppercase hex, one per UTF-8 byte.
                for (byte b : symbol.getBytes(StandardCharsets.UTF_8)) {
                    symbols.add(String.format(Locale.US, "<0x%02X>", b & 0xFF));
                }
            }
        }
        while (symbols.size() > 1) {
            int bestIndex = -1;
            float bestScore = 0f;
            String bestMerged = null;
            for (int i = 0; i + 1 < symbols.size(); i++) {
                String merged = symbols.get(i) + symbols.get(i + 1);
                Integer id = pieceToId.get(merged);
                if (id == null) continue;
                // Scores are negative log probabilities: the highest score is the earliest merge rank.
                float score = scores[id];
                if (bestIndex < 0 || score > bestScore) {
                    bestIndex = i;
                    bestScore = score;
                    bestMerged = merged;
                }
            }
            if (bestIndex < 0) break;
            symbols.set(bestIndex, bestMerged);
            symbols.remove(bestIndex + 1);
        }
        for (String symbol : symbols) {
            Integer id = pieceToId.get(symbol);
            out.add(id == null ? TOKEN_UNK : id);
        }
    }

    @NonNull
    private static int[] toCodePoints(@NonNull String text) {
        int[] codePoints = new int[text.codePointCount(0, text.length())];
        int index = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            codePoints[index++] = codePoint;
            i += Character.charCount(codePoint);
        }
        return codePoints;
    }

    @NonNull
    private static byte[] readAll(@NonNull File file) throws IOException {
        long length = file.length();
        if (length <= 0 || length > Integer.MAX_VALUE) {
            throw new IOException("Unreadable sentencepiece model: " + file.getAbsolutePath());
        }
        byte[] raw = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < raw.length) {
                int read = in.read(raw, offset, raw.length - offset);
                if (read < 0) throw new IOException("Truncated sentencepiece model: " + file.getAbsolutePath());
                offset += read;
            }
        }
        return raw;
    }

    private static long readVarint(@NonNull ByteBuffer buffer) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (!buffer.hasRemaining()) throw new IOException("Truncated varint in sentencepiece model");
            int b = buffer.get() & 0xFF;
            value |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("Malformed varint in sentencepiece model");
    }

    private static void skipField(@NonNull ByteBuffer buffer, int wireType) throws IOException {
        switch (wireType) {
            case 0:
                readVarint(buffer);
                break;
            case 2:
                // Read the length first: position() would otherwise be evaluated before the
                // varint is consumed, skipping short by the length prefix's own byte count.
                int length = (int) readVarint(buffer);
                buffer.position(buffer.position() + length);
                break;
            case 5:
                buffer.position(buffer.position() + 4);
                break;
            default:
                throw new IOException("Unsupported protobuf wire type " + wireType);
        }
    }
}
