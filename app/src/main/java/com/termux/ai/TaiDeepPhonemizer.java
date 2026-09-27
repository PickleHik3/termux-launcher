package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The neural fallback for words the dictionary lacks: DeepPhonemizer's forward transformer
 * ({@code dp_g2p_matcha_fp16.tflite}, MIT, OpenPhonemizer's espeak-IPA checkpoint) with the
 * vocabulary from {@code g2p_meta.json}. The graph has one fixed shape — {@code [1, 96]} character
 * ids in, {@code [1, 96, 64]} phoneme logits out — so it is allocated once and its buffers reused
 * for every word.
 *
 * <pre>
 *   word → &lt;en_us&gt; + every known character id repeated char_repeats times + &lt;end&gt;, zero-padded to 96
 *        → logits → argmax per position → collapse repeats → drop special tokens and blanks → IPA
 * </pre>
 *
 * Characters without an id (an apostrophe, a digit) are skipped, as in the Kotlin sample. Runs on
 * the CPU with two threads: a word is a few milliseconds, and the synthesiser's graphs want the
 * cores.
 */
final class TaiDeepPhonemizer implements TaiG2p.NeuralPhonemizer, AutoCloseable {
    private final Interpreter interpreter;
    private final Map<Character, Integer> charToIndex = new HashMap<>();
    private final Map<Integer, String> indexToPhoneme = new HashMap<>();
    private final Set<String> special = new HashSet<>();
    private final int charRepeats;
    private final int startId;
    private final int endId;
    private final int maxTokens;
    private final int numPhonemes;
    private final ByteBuffer input;
    private final ByteBuffer output;
    private final float[] logits;

    TaiDeepPhonemizer(@NonNull File model, @NonNull File metaJson) throws IOException {
        JSONObject meta;
        try {
            meta = new JSONObject(readUtf8(metaJson));
            JSONObject char2idx = meta.getJSONObject("char2idx");
            for (Iterator<String> keys = char2idx.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (key.length() == 1) charToIndex.put(key.charAt(0), char2idx.getInt(key));
            }
            JSONObject idx2ph = meta.getJSONObject("idx2ph");
            for (Iterator<String> keys = idx2ph.keys(); keys.hasNext(); ) {
                String key = keys.next();
                indexToPhoneme.put(Integer.parseInt(key), idx2ph.getString(key));
            }
            JSONArray specials = meta.getJSONArray("special");
            for (int i = 0; i < specials.length(); i++) special.add(specials.getString(i));
            charRepeats = meta.getInt("char_repeats");
            startId = meta.getInt("start");
            endId = meta.getInt("end");
            maxTokens = meta.getInt("MAXT");
            numPhonemes = meta.getInt("n_phonemes");
        } catch (JSONException | NumberFormatException e) {
            throw new IOException("g2p_meta.json is not the expected vocabulary: " + e.getMessage(), e);
        }
        interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(2).setUseXNNPACK(true));
        int[] inShape = interpreter.getInputTensor(0).shape();
        int[] outShape = interpreter.getOutputTensor(0).shape();
        if (inShape.length != 2 || inShape[1] != maxTokens || outShape.length != 3
                || outShape[1] != maxTokens || outShape[2] != numPhonemes) {
            interpreter.close();
            throw new IOException("Unexpected phonemizer shapes " + java.util.Arrays.toString(inShape)
                + " -> " + java.util.Arrays.toString(outShape));
        }
        input = ByteBuffer.allocateDirect(maxTokens * 4).order(ByteOrder.nativeOrder());
        output = ByteBuffer.allocateDirect(maxTokens * numPhonemes * 4).order(ByteOrder.nativeOrder());
        logits = new float[maxTokens * numPhonemes];
    }

    @NonNull
    @Override
    public synchronized String phonemize(@NonNull String lowerWord) {
        int[] ids = new int[maxTokens];
        int length = 0;
        ids[length++] = startId;
        for (int i = 0; i < lowerWord.length() && length < maxTokens; i++) {
            Integer id = charToIndex.get(lowerWord.charAt(i));
            if (id == null) continue;
            for (int r = 0; r < charRepeats && length < maxTokens; r++) ids[length++] = id;
        }
        if (length < maxTokens) ids[length++] = endId;
        input.rewind();
        FloatBuffer floats = input.asFloatBuffer();
        for (int i = 0; i < maxTokens; i++) floats.put(i < length ? ids[i] : 0f);
        input.rewind();
        output.rewind();
        interpreter.run(input, output);
        output.rewind();
        output.asFloatBuffer().get(logits);
        return decode(logits, length, numPhonemes, indexToPhoneme, special);
    }

    /** Greedy CTC-style decoding of the first {@code length} positions. */
    @NonNull
    static String decode(@NonNull float[] logits, int length, int numPhonemes,
                         @NonNull Map<Integer, String> indexToPhoneme, @NonNull Set<String> special) {
        StringBuilder result = new StringBuilder();
        int previous = -1;
        for (int t = 0; t < length; t++) {
            int best = 0;
            float bestScore = logits[t * numPhonemes];
            for (int k = 1; k < numPhonemes; k++) {
                float score = logits[t * numPhonemes + k];
                if (score > bestScore) {
                    bestScore = score;
                    best = k;
                }
            }
            if (best == previous) continue;
            previous = best;
            String phoneme = indexToPhoneme.get(best);
            if (phoneme == null || best == 0 || special.contains(phoneme)) continue;
            for (int i = 0; i < phoneme.length(); i++) {
                char c = phoneme.charAt(i);
                if (c != '-') result.append(c);
            }
        }
        return result.toString();
    }

    @Override
    public synchronized void close() {
        interpreter.close();
    }

    @NonNull
    private static String readUtf8(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.max(0, Math.min(file.length(), 1 << 20))];
            int done = 0;
            while (done < buffer.length) {
                int read = in.read(buffer, done, buffer.length - done);
                if (read < 0) break;
                done += read;
            }
            return new String(buffer, 0, done, StandardCharsets.UTF_8);
        }
    }
}
