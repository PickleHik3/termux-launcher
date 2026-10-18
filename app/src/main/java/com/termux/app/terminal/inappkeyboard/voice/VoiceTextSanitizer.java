package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

/**
 * What every transcript goes through before it is typed, regardless of where it lands (terminal,
 * palette, drawer search, display). This is the safety net for a recognizer that emits control
 * characters or a non-speech caption, not a style pass:
 * <ul>
 *   <li>{@code \r}, {@code \n} and other C0 control characters are mapped to a space (runs
 *       collapsed) before anything else runs — an embedded newline would otherwise run a command
 *       the moment it lands in the shell, and only "enter key" should do that;</li>
 *   <li>a segment with no letter or digit left once {@code [...]}, {@code (...)} and
 *       {@code *...*} spans and punctuation are removed is dropped entirely — the shapes measured
 *       on far-field and short-clip audio: {@code [Music]}, {@code [BLANK_AUDIO]}, {@code (B)},
 *       {@code *}, {@code ¶¶}, {@code .};</li>
 *   <li>consecutive text segments are joined with one space ({@link #join}).</li>
 * </ul>
 *
 * <p>The one exception is a cleanup's answer ({@link #cleanLines}): a Polished pass writes
 * paragraphs and list items on lines of their own, and those breaks are the structure the user
 * asked for. They are kept, one per break, all the way to the card, the clipboard and a shell
 * that takes bracketed paste ({@code DictationMarks}); everywhere a line break could run
 * something, the text goes in on one line ({@link #oneLine}).
 */
public final class VoiceTextSanitizer {

    /** Every C0 control character, {@code \r} and {@code \n} included, plus DEL. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]+");
    /** Every C0 control character but {@code \n}, plus DEL. */
    private static final Pattern CONTROL_CHARS_BUT_NEWLINE = Pattern.compile("[\\x00-\\x09\\x0B-\\x1F\\x7F]+");
    /** A line break with the blanks and blank lines around it. */
    private static final Pattern LINE_BREAKS = Pattern.compile("[ ]*\\n[\\n ]*");
    /** {@code [...]}, {@code (...)} and {@code *...*} spans: bracketed asides and captions ASR emits for non-speech. */
    private static final Pattern BRACKETED_SPAN =
        Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private VoiceTextSanitizer() {
    }

    /** {@code transcript} cleaned, or {@code ""} when the segment should be dropped, not typed. */
    @NonNull
    public static String clean(@NonNull String transcript) {
        String text = CONTROL_CHARS.matcher(transcript).replaceAll(" ").trim().replaceAll("\\s+", " ");
        if (isNonSpeech(text)) return "";
        return text;
    }

    /**
     * A cleanup's answer, cleaned as {@link #clean} does but keeping its line breaks: a carriage
     * return is a line break, each run of breaks and blank lines becomes one {@code \n}, every
     * other control character a space, and each line is trimmed. {@code ""} when there is no
     * speech in it.
     */
    @NonNull
    public static String cleanLines(@NonNull String text) {
        String lines = text.replace("\r\n", "\n").replace('\r', '\n');
        lines = CONTROL_CHARS_BUT_NEWLINE.matcher(lines).replaceAll(" ");
        lines = lines.replaceAll("[ ]{2,}", " ");
        lines = LINE_BREAKS.matcher(lines).replaceAll("\n").trim();
        if (isNonSpeech(lines)) return "";
        return lines;
    }

    /** {@code text} on one line: each line break, with the blanks around it, becomes one space. */
    @NonNull
    public static String oneLine(@NonNull String text) {
        return text.replaceAll("\\s*\\r?\\n\\s*", " ").trim();
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
}
