/*
 * Copyright 2026 The Google AI Edge Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Termux Launcher: Java adaptation of Google AI Edge LiteRT samples'
 * KittenG2P.kt; runtime and behaviour changes are described below.
 */
package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * English text to KittenTTS symbol ids without espeak-ng (which is GPL and never used here): the
 * Java port of Google's {@code KittenG2P.kt} and of {@code scripts/tts-eval/g2p.py}, which is the
 * same logic in Python and was listened to before this was written.
 *
 * <pre>
 *   text → {@link TaiTextNormalizer} (dates, years, times, money, spoken acronyms)
 *        → tokens: ALL-CAPS run | number | word | punctuation (anything else is dropped)
 *        → IPA per token: the dictionary first, the neural phonemizer for a word it lacks,
 *          letter names for a caps run, number words for digits
 *        → one IPA string, tokens joined by spaces, punctuation as its own token ("wˈɜːld ,")
 *        → symbol ids on the 178-symbol StyleTTS2 table (characters it lacks are dropped)
 * </pre>
 *
 * The punctuation style is Kitten's ({@code basic_english_tokenize} in the pip package): the
 * duration predictor was tuned on punctuation as a separate token, not attached to the word.
 *
 * <p>The dictionary is loaded on first use (and on {@link #preload}, which the runtime calls
 * while warming), so a G2P that is built but never asked costs nothing.
 */
final class TaiG2p {
    /** Out-of-dictionary words: lower-case word in, espeak-style IPA out (may be empty). */
    interface NeuralPhonemizer {
        @NonNull String phonemize(@NonNull String lowerWord);
    }

    /** Supplies the dictionary once, on first use. */
    interface DictionarySource {
        @NonNull TaiG2pDictionary load() throws java.io.IOException;
    }

    /** One chunk's G2P: the normalised text, its IPA, and the ids (no BOS/EOS; the synthesiser adds them). */
    static final class Result {
        @NonNull final String text;
        @NonNull final String ipa;
        @NonNull final int[] ids;
        final int dictionaryWords;
        final int neuralWords;

        Result(@NonNull String text, @NonNull String ipa, @NonNull int[] ids, int dictionaryWords, int neuralWords) {
            this.text = text;
            this.ipa = ipa;
            this.ids = ids;
            this.dictionaryWords = dictionaryWords;
            this.neuralWords = neuralWords;
        }
    }

    // StyleTTS2 / Kokoro symbol table, verbatim from the model repo's say.py: pad, punctuation,
    // ASCII letters, then IPA. The same table as Matcha's config.json apart from the pad glyph.
    private static final String PAD = "$";
    private static final String PUNCTUATION = ";:,.!?¡¿—…\"«»“” ";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String IPA =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ";
    static final String SYMBOLS = PAD + PUNCTUATION + LETTERS + IPA;
    private static final Map<Character, Integer> SYMBOL_IDS;

    /** espeak letter names, for spelling an ALL-CAPS run ("GPU" → dʒˈiːpˈiːjˈuː); from the Kotlin sample. */
    private static final String[] LETTER_IPA = {"ˈeɪ", "bˈiː", "sˈiː", "dˈiː", "ˈiː", "ˈɛf", "dʒˈiː", "ˈeɪtʃ", "ˈaɪ",
        "dʒˈeɪ", "kˈeɪ", "ˈɛl", "ˈɛm", "ˈɛn", "ˈoʊ", "pˈiː", "kjˈuː", "ˈɑːɹ", "ˈɛs", "tˈiː", "jˈuː", "vˈiː",
        "dˈʌbəljˌuː", "ˈɛks", "wˈaɪ", "zˈiː"};

    /** ACRONYM (2+ caps) | NUMBER | word | punctuation, as in the Kotlin sample. */
    private static final Pattern TOKEN = Pattern.compile("[A-Z]{2,}|\\d[\\d,]*(?:\\.\\d+)?|[A-Za-z']+|[.,!?;:—…\"]");

    private static final int NEURAL_CACHE_WORDS = 512;

    static {
        HashMap<Character, Integer> ids = new HashMap<>();
        // Later duplicates win ("'" appears twice), as in the models' own {s: i for i, s in ...}.
        for (int i = 0; i < SYMBOLS.length(); i++) ids.put(SYMBOLS.charAt(i), i);
        SYMBOL_IDS = ids;
    }

    @NonNull private final DictionarySource dictionarySource;
    @NonNull private final NeuralPhonemizer neural;
    @Nullable private volatile TaiG2pDictionary dictionary;
    private final Map<String, String> neuralCache = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > NEURAL_CACHE_WORDS;
        }
    };

    TaiG2p(@NonNull DictionarySource dictionarySource, @NonNull NeuralPhonemizer neural) {
        this.dictionarySource = dictionarySource;
        this.neural = neural;
    }

    /** Loads the dictionary now rather than on the first sentence. */
    void preload() throws java.io.IOException {
        dictionary();
    }

    @NonNull
    private TaiG2pDictionary dictionary() throws java.io.IOException {
        TaiG2pDictionary loaded = dictionary;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (dictionary == null) dictionary = dictionarySource.load();
            return dictionary;
        }
    }

    /** Normalises and phonemises one chunk. */
    @NonNull
    Result phonemize(@NonNull String chunk) throws java.io.IOException {
        TaiG2pDictionary dict = dictionary();
        String text = TaiTextNormalizer.normalize(chunk, dict::contains);
        StringBuilder ipa = new StringBuilder(text.length() * 2);
        int[] counts = new int[2];
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            char first = token.charAt(0);
            if (token.length() >= 2 && isAllCaps(token)) {
                StringBuilder spelled = new StringBuilder();
                for (int i = 0; i < token.length(); i++) spelled.append(LETTER_IPA[token.charAt(i) - 'A']);
                append(ipa, spelled);
            } else if (first >= '0' && first <= '9') {
                List<String> words = TaiTextNormalizer.numberToWords(token);
                for (String word : words) append(ipa, word(dict, word, counts));
            } else if (Character.isLetter(first) || first == '\'') {
                append(ipa, word(dict, token.toLowerCase(java.util.Locale.ROOT), counts));
            } else {
                append(ipa, token);
            }
        }
        return new Result(text, ipa.toString(), toIds(ipa), counts[0], counts[1]);
    }

    @NonNull
    private String word(@NonNull TaiG2pDictionary dict, @NonNull String lower, @NonNull int[] counts) {
        String known = dict.get(lower);
        if (known != null && !known.isEmpty()) {
            counts[0]++;
            return known;
        }
        counts[1]++;
        synchronized (neuralCache) {
            String cached = neuralCache.get(lower);
            if (cached != null) return cached;
        }
        String guessed = neural.phonemize(lower);
        synchronized (neuralCache) {
            neuralCache.put(lower, guessed);
        }
        return guessed;
    }

    private static void append(@NonNull StringBuilder ipa, @NonNull CharSequence phonemes) {
        if (phonemes.length() == 0) return;
        if (ipa.length() > 0) ipa.append(' ');
        ipa.append(phonemes);
    }

    private static boolean isAllCaps(@NonNull String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 'A' || c > 'Z') return false;
        }
        return true;
    }

    /** Maps IPA onto symbol ids; characters outside the table are dropped, as in every reference. */
    @NonNull
    static int[] toIds(@NonNull CharSequence ipa) {
        int[] ids = new int[ipa.length()];
        int count = 0;
        for (int i = 0; i < ipa.length(); i++) {
            Integer id = SYMBOL_IDS.get(ipa.charAt(i));
            if (id != null) ids[count++] = id;
        }
        return count == ids.length ? ids : java.util.Arrays.copyOf(ids, count);
    }
}
