package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which language a dictation is in, from its text alone, for the cleanup's prompt line ("The
 * transcript is in Spanish. Write in Spanish.") and its filler strip ({@link VoiceFillers}). Pure
 * and cheap: the script the letters are written in, then, for Latin, Cyrillic and Devanagari, which
 * language's commonest words it uses. Unclear or mixed text has no language, and the cleanup then
 * only strips fillers ({@link VoiceGatedCleanup}): a model told nothing translated non-English
 * dictation into English in the 2026-10-13 eval, and a wrong language line would do the same.
 *
 * <p>The speech model's own language, when it reports one, comes first
 * ({@link VoiceSessionCleanup#setLanguageHint}). The eval's {@code cleanup_eval.py} carries a copy of
 * this class; {@code VoiceCleanupEvalParityTest} holds the two together.
 */
public final class VoiceTextLanguage {

    /** The share of the letters one script must have, or the text is mixed. */
    static final double SCRIPT_SHARE = 0.8;
    /** The fewest hits (common words, or words with a letter only that language writes) for a language. */
    static final int MIN_HITS = 2;
    /** How many times the runner-up's hits the winner needs, or the text is unclear. */
    static final int MARGIN = 2;

    /*
     * Frequent words of each language, each list kept clear of the commonest words of the others where
     * it can be ("to", "i" and "my" are English, not Polish; "is" and "we" English, not Dutch).
     */
    static final String STOPWORDS_EN =
        "the and of to is are was were that this it i you we they he she with for have has had not but what "
            + "can my your just be been would should could there about from if or do don does did at by all "
            + "ll ve when how who which them then than some out up get got go going know think need make now after "
            + "before because very only here where why still back let us our its";
    static final String STOPWORDS_ES =
        "el la los las de del que y en un una es por para con no se lo al mi su pero como más muy está "
            + "estoy tengo hay este esta eso porque también puedo ya cuando qué sí yo voy va fue tiene poco ahora "
            + "aquí todavía nosotros dónde entonces";
    static final String STOPWORDS_DE =
        "der die das und ist nicht ich du sie wir ihr es ein eine einen zu mit auf für den dem des von auch "
            + "aber wie noch nur schon heute etwas dass sich bin habe hat kann mal jetzt wenn oder sehr gegen halb";
    static final String STOPWORDS_FR =
        "le les des du et est je tu il elle nous vous ils pas ne sur pour dans avec ce cette mais trop très "
            + "au aux ça oui qui que une un la de en se sont suis ai d l j qu c n";
    static final String STOPWORDS_IT =
        "il lo la gli i di che e è non un una per con del della sono ho ha ma come anche molto questo "
            + "questa perché se mi ti ci si al alla nel nella più già";
    static final String STOPWORDS_PT =
        "o os as uma de do da dos das em no na que e é não para com por mas como mais muito eu você ele "
            + "ela isso este esta está estou tenho também se ao à vou vai foi tem ter pouco agora aqui ainda já só "
            + "nós meu minha onde então";
    static final String STOPWORDS_NL =
        "de het een en van dat die niet op te met voor zijn ik je maar ook als er aan nog wel naar om dan "
            + "kan hebben heb moet wat bij uit nu zo deze dit";
    static final String STOPWORDS_PL =
        "w z na nie się że jest do co jak ale tak ja ty ona wy oni ten ta po od za czy już tylko mnie mi "
            + "jestem być bardzo dla przez tu tam też jeszcze";
    static final String STOPWORDS_RU =
        "и в не на я что с он как это по но к у из за так же от все мы вы они был есть только ещё уже "
            + "если или бы мне меня его её очень когда сейчас надо нужно";
    static final String STOPWORDS_UK =
        "і в у на не що це як та з до за від він вона ми ви вони але або так мені мене його її дуже коли "
            + "зараз треба потрібно є був тільки ще вже якщо";
    static final String STOPWORDS_HI = "है हैं के की का में और से को नहीं यह मैं कि पर भी था थे हम आप तो ही";
    /** Persian and Urdu letters: Arabic script, but not Arabic, which the prompt would then name. */
    static final String NOT_ARABIC = "پچژگکیےٹڈڑں";

    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}");
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, Set<String>> STOPWORDS = new HashMap<>();
    /** Letters only one of a script's languages writes: a word that has one counts as a hit. */
    private static final Map<String, String> MARKER_LETTERS = new HashMap<>();
    /** The languages told apart within each script, in the order a tie goes to. */
    private static final Map<Script, List<String>> SCRIPT_LANGUAGES = new LinkedHashMap<>();

    static {
        String[][] names = {{"en", "English"}, {"es", "Spanish"}, {"de", "German"}, {"fr", "French"},
            {"it", "Italian"}, {"pt", "Portuguese"}, {"nl", "Dutch"}, {"pl", "Polish"}, {"ru", "Russian"},
            {"uk", "Ukrainian"}, {"ar", "Arabic"}, {"hi", "Hindi"}, {"zh", "Chinese"}, {"ja", "Japanese"},
            {"ko", "Korean"}};
        for (String[] name : names) NAMES.put(name[0], name[1]);
        stopwords("en", STOPWORDS_EN);
        stopwords("es", STOPWORDS_ES);
        stopwords("de", STOPWORDS_DE);
        stopwords("fr", STOPWORDS_FR);
        stopwords("it", STOPWORDS_IT);
        stopwords("pt", STOPWORDS_PT);
        stopwords("nl", STOPWORDS_NL);
        stopwords("pl", STOPWORDS_PL);
        stopwords("ru", STOPWORDS_RU);
        stopwords("uk", STOPWORDS_UK);
        stopwords("hi", STOPWORDS_HI);
        MARKER_LETTERS.put("es", "ñ¿¡");
        MARKER_LETTERS.put("de", "ßäöü");
        MARKER_LETTERS.put("pt", "ãõ");
        MARKER_LETTERS.put("pl", "łąęśźżćń");
        MARKER_LETTERS.put("ru", "ыэъё");
        MARKER_LETTERS.put("uk", "іїєґ");
        SCRIPT_LANGUAGES.put(Script.LATIN, Arrays.asList("en", "es", "de", "fr", "it", "pt", "nl", "pl"));
        SCRIPT_LANGUAGES.put(Script.CYRILLIC, Arrays.asList("ru", "uk"));
        SCRIPT_LANGUAGES.put(Script.DEVANAGARI, Collections.singletonList("hi"));
    }

    private enum Script { LATIN, CYRILLIC, ARABIC, DEVANAGARI, HANGUL, KANA, HAN, OTHER }

    private VoiceTextLanguage() {
    }

    private static void stopwords(@NonNull String language, @NonNull String words) {
        STOPWORDS.put(language, new HashSet<>(Arrays.asList(words.split(" "))));
    }

    /**
     * {@code code} as a bare ISO 639 code ("en-US", "EN_us" → "en"), or {@code null} for none or
     * anything that is not one.
     */
    @Nullable
    public static String normalize(@Nullable String code) {
        if (code == null || code.isEmpty()) return null;
        String bare = code.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return CODE.matcher(bare).matches() ? bare : null;
    }

    /**
     * The English name of {@code code} for the prompt ("es" → "Spanish"), or {@code null} when it has
     * none: the languages this class tells apart by name, any other the platform knows by its own.
     */
    @Nullable
    public static String displayName(@Nullable String code) {
        String bare = normalize(code);
        if (bare == null) return null;
        String name = NAMES.get(bare);
        if (name != null) return name;
        String platform = Locale.forLanguageTag(bare).getDisplayLanguage(Locale.ENGLISH);
        return platform.isEmpty() || platform.equalsIgnoreCase(bare) ? null : platform;
    }

    /** The ISO 639-1 code of the language {@code text} is in, or {@code null} when that is unclear or mixed. */
    @Nullable
    public static String guess(@NonNull String text) {
        int[] counts = new int[Script.values().length];
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            counts[scriptOf(cp).ordinal()]++;
            total++;
        }
        if (total == 0) return null;
        int hangul = counts[Script.HANGUL.ordinal()];
        int kana = counts[Script.KANA.ordinal()];
        int cjk = hangul + kana + counts[Script.HAN.ordinal()];
        // The script most letters are in; on a tie the first in this order.
        Script[] order = {Script.LATIN, Script.CYRILLIC, Script.ARABIC, Script.DEVANAGARI, Script.HAN};
        Script script = Script.LATIN;
        int most = -1;
        for (Script candidate : order) {
            int n = candidate == Script.HAN ? cjk : counts[candidate.ordinal()];
            if (n > most) {
                most = n;
                script = candidate;
            }
        }
        if (most < SCRIPT_SHARE * total) return null;
        switch (script) {
            case HAN:
                if (hangul > kana) return "ko";
                return kana > 0 ? "ja" : "zh";
            case ARABIC:
                for (int i = 0; i < NOT_ARABIC.length(); i++) {
                    if (text.indexOf(NOT_ARABIC.charAt(i)) >= 0) return null;
                }
                return "ar";
            default:
                return byWords(text, SCRIPT_LANGUAGES.get(script));
        }
    }

    /** The language of {@code candidates} whose words {@code text} uses clearly most, or {@code null}. */
    @Nullable
    private static String byWords(@NonNull String text, @NonNull List<String> candidates) {
        int[] scores = new int[candidates.size()];
        for (String word : letterWords(text)) {
            for (int i = 0; i < candidates.size(); i++) {
                String language = candidates.get(i);
                if (STOPWORDS.get(language).contains(word)) scores[i]++;
                String markers = MARKER_LETTERS.get(language);
                if (markers != null && hasAny(word, markers)) scores[i]++;
            }
        }
        int best = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[best]) best = i;
        }
        int second = 0;
        for (int i = 0; i < scores.length; i++) {
            if (i != best) second = Math.max(second, scores[i]);
        }
        return scores[best] >= MIN_HITS && scores[best] >= MARGIN * second ? candidates.get(best) : null;
    }

    private static boolean hasAny(@NonNull String word, @NonNull String letters) {
        for (int i = 0; i < letters.length(); i++) {
            if (word.indexOf(letters.charAt(i)) >= 0) return true;
        }
        return false;
    }

    /** Lower-case runs of letters and marks; Devanagari's vowel signs are marks. */
    @NonNull
    static List<String> letterWords(@NonNull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp) || isMark(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) {
                words.add(word.toString());
                word.setLength(0);
            }
        }
        if (word.length() > 0) words.add(word.toString());
        return words;
    }

    private static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
            || type == Character.ENCLOSING_MARK;
    }

    @NonNull
    private static Script scriptOf(int cp) {
        if (cp < 0x250 || (cp >= 0x1E00 && cp <= 0x1EFF)) return Script.LATIN;
        if (cp >= 0x400 && cp <= 0x4FF) return Script.CYRILLIC;
        if ((cp >= 0x600 && cp <= 0x6FF) || (cp >= 0x750 && cp <= 0x77F) || (cp >= 0x8A0 && cp <= 0x8FF)
            || (cp >= 0xFB50 && cp <= 0xFDFF) || (cp >= 0xFE70 && cp <= 0xFEFF)) return Script.ARABIC;
        if (cp >= 0x900 && cp <= 0x97F) return Script.DEVANAGARI;
        if ((cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0x1100 && cp <= 0x11FF) || (cp >= 0x3130 && cp <= 0x318F)) {
            return Script.HANGUL;
        }
        if ((cp >= 0x3040 && cp <= 0x30FF) || (cp >= 0x31F0 && cp <= 0x31FF)) return Script.KANA;
        if ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF)) return Script.HAN;
        return Script.OTHER;
    }
}
