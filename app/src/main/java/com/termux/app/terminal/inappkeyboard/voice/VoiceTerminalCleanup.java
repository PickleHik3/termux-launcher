package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a transcript goes through before it is typed into a terminal session (no key-value
 * interceptor). The shell-vocabulary prompt already yields lowercase, unpunctuated text; this is
 * the safety net for a recognizer that still says {@code "LS."} — or worse:
 * <ul>
 *   <li>{@code \r}, {@code \n} and other C0 control characters are mapped to a space (runs
 *       collapsed) before anything else runs — an embedded newline would otherwise run a command
 *       the moment it lands in the shell, and only "enter key" should do that;</li>
 *   <li>a segment with no letter or digit left once {@code [...]}, {@code (...)} and
 *       {@code *...*} spans and punctuation are removed is dropped entirely — the shapes measured
 *       on far-field and short-clip audio: {@code [Music]}, {@code [BLANK_AUDIO]}, {@code (B)},
 *       {@code *}, {@code ¶¶}, {@code .};</li>
 *   <li>trailing {@code . ? !} are stripped;</li>
 *   <li>a single-word segment is lowercased, multi-word prose is left as spoken;</li>
 *   <li>consecutive text segments are joined with one space ({@link #join}).</li>
 * </ul>
 * Other targets — the palette, a drawer search, the display — get the transcript as spoken.
 */
public final class VoiceTerminalCleanup {

    /** Every C0 control character, {@code \r} and {@code \n} included, plus DEL. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]+");
    /** {@code [...]}, {@code (...)} and {@code *...*} spans: bracketed asides and captions ASR emits for non-speech. */
    private static final Pattern BRACKETED_SPAN =
        Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private VoiceTerminalCleanup() {
    }

    /** {@code transcript} cleaned for a terminal, or {@code ""} when the segment should be dropped, not typed. */
    @NonNull
    public static String clean(@NonNull String transcript) {
        String text = CONTROL_CHARS.matcher(transcript).replaceAll(" ").trim().replaceAll("\\s+", " ");
        if (isNonSpeech(text)) return "";
        int end = text.length();
        while (end > 0 && isTrailingPunctuation(text.charAt(end - 1))) end--;
        text = text.substring(0, end).trim();
        if (!text.isEmpty() && !hasWhitespace(text)) text = text.toLowerCase(Locale.ROOT);
        return text;
    }

    /** {@code text} as it is typed after the previous segment: one space between two text segments. */
    @NonNull
    public static String join(boolean afterText, @NonNull String text) {
        return afterText && !text.isEmpty() ? " " + text : text;
    }

    /**
     * True once {@code [...]}, {@code (...)}, {@code *...*} spans and every non-letter,
     * non-digit character are stripped and nothing is left: a caption or a punctuation-only
     * hallucination, never real speech.
     */
    private static boolean isNonSpeech(@NonNull String text) {
        String stripped = BRACKETED_SPAN.matcher(text).replaceAll(" ");
        stripped = NOT_ALPHANUMERIC.matcher(stripped).replaceAll("");
        return stripped.isEmpty();
    }

    private static boolean isTrailingPunctuation(char c) {
        return c == '.' || c == '?' || c == '!' || Character.isWhitespace(c);
    }

    private static boolean hasWhitespace(@NonNull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return true;
        }
        return false;
    }
}
