package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * What a transcript goes through before it is typed into a terminal session (no key-value
 * interceptor). The shell-vocabulary prompt already yields lowercase, unpunctuated text; this is
 * the safety net for a recognizer that still says {@code "LS."}:
 * <ul>
 *   <li>trailing {@code . ? !} are stripped;</li>
 *   <li>a single-word segment is lowercased, multi-word prose is left as spoken;</li>
 *   <li>consecutive text segments are joined with one space ({@link #join}).</li>
 * </ul>
 * Other targets — the palette, a drawer search, the display — get the transcript as spoken.
 */
public final class VoiceTerminalCleanup {

    private VoiceTerminalCleanup() {
    }

    @NonNull
    public static String clean(@NonNull String transcript) {
        String text = transcript.trim();
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
