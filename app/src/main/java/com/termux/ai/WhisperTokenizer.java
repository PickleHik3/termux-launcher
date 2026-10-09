package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whisper's GPT-2-style byte-level BPE tokenizer, read from the {@code tokenizer.json} sidecar of
 * the matching {@code openai/whisper-*} repo: {@code model.vocab} and {@code model.merges} for the
 * text tokens, {@code added_tokens} for the specials ({@code <|endoftext|>}, {@code <|startoftranscript|>},
 * the {@code <|xx|>} language tokens, task tokens and timestamps).
 *
 * <p>Decoding maps token strings back through GPT-2's byte-to-unicode table to UTF-8. Encoding is
 * needed for one thing only, the terminal vocabulary prompt behind {@code <|startofprev|>}: the
 * multilingual vocab splits most shell words into several tokens ({@code pkg} is {@code Ġp kg}),
 * so a proper merge-rank BPE is run over GPT-2's pre-tokenizer regex, with Whisper's convention
 * of a leading space before the prompt text.
 */
final class WhisperTokenizer {
    static final String EOT = "<|endoftext|>";
    static final String SOT = "<|startoftranscript|>";
    static final String TRANSCRIBE = "<|transcribe|>";
    static final String TRANSLATE = "<|translate|>";
    static final String START_OF_LM = "<|startoflm|>";
    static final String START_OF_PREV = "<|startofprev|>";
    static final String NO_CAPTIONS = "<|nocaptions|>";
    static final String NO_SPEECH = "<|nospeech|>";
    static final String NO_TIMESTAMPS = "<|notimestamps|>";

    /** GPT-2's pre-tokenizer: contractions, words with their leading space, numbers, punctuation runs, whitespace. */
    private static final Pattern PRE_TOKENIZER = Pattern.compile(
        "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    private final String[] idToToken;
    private final Map<String, Integer> tokenToId;
    private final Map<String, Integer> mergeRank;
    private final Map<String, Integer> special;
    private final boolean[] isSpecial;
    private final char[] byteToUnicode = new char[256];
    private final int[] unicodeToByte = new int[512];

    private WhisperTokenizer(@NonNull JSONObject json) throws JSONException {
        JSONObject model = json.getJSONObject("model");
        JSONObject vocab = model.getJSONObject("vocab");
        JSONArray added = json.optJSONArray("added_tokens");
        JSONArray merges = model.getJSONArray("merges");
        int maxId = -1;
        tokenToId = new HashMap<>(vocab.length() * 2);
        for (java.util.Iterator<String> keys = vocab.keys(); keys.hasNext(); ) {
            String token = keys.next();
            int id = vocab.getInt(token);
            tokenToId.put(token, id);
            if (id > maxId) maxId = id;
        }
        special = new HashMap<>();
        if (added != null) {
            for (int i = 0; i < added.length(); i++) {
                JSONObject entry = added.getJSONObject(i);
                int id = entry.getInt("id");
                special.put(entry.getString("content"), id);
                if (id > maxId) maxId = id;
            }
        }
        idToToken = new String[maxId + 1];
        isSpecial = new boolean[maxId + 1];
        for (Map.Entry<String, Integer> entry : tokenToId.entrySet()) idToToken[entry.getValue()] = entry.getKey();
        for (Map.Entry<String, Integer> entry : special.entrySet()) {
            idToToken[entry.getValue()] = entry.getKey();
            isSpecial[entry.getValue()] = true;
        }
        mergeRank = new HashMap<>(merges.length() * 2);
        for (int i = 0; i < merges.length(); i++) {
            Object merge = merges.get(i);
            // Older tokenizer.json files store "a b"; newer ones store ["a", "b"].
            String key = merge instanceof JSONArray
                ? ((JSONArray) merge).getString(0) + " " + ((JSONArray) merge).getString(1)
                : (String) merge;
            if (!mergeRank.containsKey(key)) mergeRank.put(key, i);
        }
        buildByteTables();
    }

    @NonNull
    static WhisperTokenizer fromFile(@NonNull File tokenizerJson) throws IOException, JSONException {
        return parse(new String(Files.readAllBytes(tokenizerJson.toPath()), StandardCharsets.UTF_8));
    }

    @NonNull
    static WhisperTokenizer parse(@NonNull String tokenizerJson) throws JSONException {
        return new WhisperTokenizer(new JSONObject(tokenizerJson));
    }

    /**
     * GPT-2's reversible byte-to-unicode map: printable Latin-1 bytes stand for themselves and the
     * remaining 68 bytes are moved up to U+0100 onwards, so every byte has a visible character.
     */
    private void buildByteTables() {
        java.util.Arrays.fill(unicodeToByte, -1);
        int next = 256;
        for (int b = 0; b < 256; b++) {
            boolean printable = (b >= '!' && b <= '~') || (b >= 0xA1 && b <= 0xAC) || (b >= 0xAE && b <= 0xFF);
            char c = printable ? (char) b : (char) (next++);
            byteToUnicode[b] = c;
            unicodeToByte[c] = b;
        }
    }

    /** The id of a special token such as {@code <|notimestamps|>}, or {@code -1} when this vocabulary lacks it. */
    int specialId(@NonNull String content) {
        Integer id = special.get(content);
        return id == null ? -1 : id;
    }

    /** {@code <|en|>} for {@code "en"}: the language token, or {@code -1} for a code Whisper does not know. */
    int languageToken(@Nullable String code) {
        if (code == null) return -1;
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        // Language codes are two or three letters; this keeps "<|transcribe|>" and friends from matching.
        if (!normalized.matches("[a-z]{2,3}")) return -1;
        return specialId("<|" + normalized + "|>");
    }

    /** Whether this vocabulary carries language tokens at all (every openai/whisper-* tokenizer.json does, the .en ones included). */
    boolean hasLanguageTokens() {
        return specialId("<|en|>") >= 0;
    }

    boolean isSpecial(int id) {
        return id >= 0 && id < isSpecial.length && isSpecial[id];
    }

    int vocabularySize() {
        return idToToken.length;
    }

    /** The text of {@code ids} with every special token dropped; malformed UTF-8 decodes to U+FFFD. */
    @NonNull
    String decode(@NonNull int[] ids, int from, int to) {
        StringBuilder text = new StringBuilder();
        for (int i = from; i < to; i++) {
            int id = ids[i];
            if (id < 0 || id >= idToToken.length || isSpecial[id] || idToToken[id] == null) continue;
            text.append(idToToken[id]);
        }
        byte[] bytes = new byte[text.length()];
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int b = c < unicodeToByte.length ? unicodeToByte[c] : -1;
            if (b >= 0) bytes[n++] = (byte) b;
        }
        return new String(bytes, 0, n, StandardCharsets.UTF_8);
    }

    @NonNull
    String decode(@NonNull int[] ids) {
        return decode(ids, 0, ids.length);
    }

    /** The BPE ids of {@code text}, no specials added; an empty array for empty text. */
    @NonNull
    int[] encode(@NonNull String text) {
        ArrayList<Integer> ids = new ArrayList<>();
        Matcher matcher = PRE_TOKENIZER.matcher(text);
        while (matcher.find()) {
            byte[] bytes = matcher.group().getBytes(StandardCharsets.UTF_8);
            StringBuilder word = new StringBuilder(bytes.length);
            for (byte b : bytes) word.append(byteToUnicode[b & 0xFF]);
            for (String piece : bpe(word.toString())) {
                Integer id = tokenToId.get(piece);
                if (id != null) {
                    ids.add(id);
                    continue;
                }
                // Not reachable with a complete vocab (every byte character is a token); fall back per character.
                for (int i = 0; i < piece.length(); i++) {
                    Integer single = tokenToId.get(String.valueOf(piece.charAt(i)));
                    if (single != null) ids.add(single);
                }
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    /** Merges the characters of one pre-token by rank, lowest first, until no listed pair is left. */
    @NonNull
    private List<String> bpe(@NonNull String word) {
        ArrayList<String> parts = new ArrayList<>(word.length());
        for (int i = 0; i < word.length(); i++) parts.add(String.valueOf(word.charAt(i)));
        while (parts.size() > 1) {
            int bestRank = Integer.MAX_VALUE;
            String bestFirst = null, bestSecond = null;
            for (int i = 0; i + 1 < parts.size(); i++) {
                Integer rank = mergeRank.get(parts.get(i) + " " + parts.get(i + 1));
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestFirst = parts.get(i);
                    bestSecond = parts.get(i + 1);
                }
            }
            if (bestFirst == null) break;
            ArrayList<String> merged = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                if (i + 1 < parts.size() && parts.get(i).equals(bestFirst) && parts.get(i + 1).equals(bestSecond)) {
                    merged.add(bestFirst + bestSecond);
                    i++;
                } else {
                    merged.add(parts.get(i));
                }
            }
            parts = merged;
        }
        return parts;
    }
}
