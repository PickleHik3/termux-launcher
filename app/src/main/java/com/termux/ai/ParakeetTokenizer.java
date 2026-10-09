package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Decode-only view of {@code nvidia/parakeet-tdt-0.6b-v3}'s {@code tokenizer.json}: id → SentencePiece
 * piece from {@code model.vocab}, the markup among the {@code added_tokens} ({@code <unk>},
 * {@code <|nospeech|>}, the task/format markers) dropped while its plain pieces (digits) are kept, the TDT blank never in the vocabulary, {@code ▁} read as a space
 * and the result trimmed. The vocabulary has 8192 pieces; the graph's blank is id 8192, past it.
 */
final class ParakeetTokenizer {
    static final char PIECE_SPACE = '▁';

    private final String[] pieces;
    private final Set<Integer> skipped;

    private ParakeetTokenizer(@NonNull JSONObject json) throws JSONException {
        JSONObject vocab = json.getJSONObject("model").getJSONObject("vocab");
        int maxId = -1;
        for (Iterator<String> keys = vocab.keys(); keys.hasNext(); ) {
            maxId = Math.max(maxId, vocab.getInt(keys.next()));
        }
        pieces = new String[maxId + 1];
        for (Iterator<String> keys = vocab.keys(); keys.hasNext(); ) {
            String piece = keys.next();
            pieces[vocab.getInt(piece)] = piece;
        }
        skipped = new HashSet<>();
        JSONArray added = json.optJSONArray("added_tokens");
        if (added != null) {
            for (int i = 0; i < added.length(); i++) {
                JSONObject token = added.getJSONObject(i);
                // Only markup is dropped (<unk>, <pad>, <|nospeech|>, <|en|>, …). The list also holds
                // ordinary pieces, the ten digits among them, and its "special" flag marks just three
                // tokens, so skipping every added token deleted every spoken number ("42 seconds"
                // came out as "seconds", "27th" as "th").
                if (isMarkup(token.optString("content", ""))) skipped.add(token.getInt("id"));
            }
        }
    }

    @NonNull
    static ParakeetTokenizer fromFile(@NonNull File tokenizerJson) throws IOException, JSONException {
        return parse(new String(Files.readAllBytes(tokenizerJson.toPath()), StandardCharsets.UTF_8));
    }

    @NonNull
    static ParakeetTokenizer parse(@NonNull String tokenizerJson) throws JSONException {
        return new ParakeetTokenizer(new JSONObject(tokenizerJson));
    }

    /** A control token such as {@code <unk>} or {@code <|nospeech|>}, as opposed to a text piece. */
    static boolean isMarkup(@NonNull String content) {
        return content.length() > 2 && content.startsWith("<") && content.endsWith(">");
    }

    /** How many pieces the vocabulary has (ids {@code 0 … size − 1}). */
    int size() {
        return pieces.length;
    }

    /** The text of {@code ids}: added tokens, the blank and unknown ids contribute nothing. */
    @NonNull
    String decode(@NonNull int[] ids) {
        StringBuilder text = new StringBuilder();
        for (int id : ids) {
            if (id == ParakeetTdtDecoder.BLANK || id < 0 || id >= pieces.length || skipped.contains(id)) continue;
            String piece = pieces[id];
            if (piece != null) text.append(piece);
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PIECE_SPACE) text.setCharAt(i, ' ');
        }
        return text.toString().trim();
    }
}
