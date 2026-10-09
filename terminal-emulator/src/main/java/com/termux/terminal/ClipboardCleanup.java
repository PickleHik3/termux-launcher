package com.termux.terminal;

/**
 * The "Clipboard Cleanup" setting: strips the ragged whitespace a terminal's own copy and paste
 * add that a real document never would, so a copied command runs on paste instead of leaving a
 * blank line, and a pasted one runs immediately instead of waiting on a trailing newline.
 *
 * <p>{@link #forCopy(String)} is Ghostty's {@code clipboard-trim-trailing-spaces}: every line
 * loses its trailing spaces and tabs, and blank lines at the very end of the text are dropped.
 * {@link #forPaste(String)} is Windows Terminal's {@code TrimPaste}, ported byte for byte: a
 * single-line paste loses its trailing whitespace and newline, a multi-line one is left alone.
 * Both are pure text transforms with no Android dependency, so they are also reachable from a
 * plain JUnit test.</p>
 */
public final class ClipboardCleanup {

    private ClipboardCleanup() {
    }

    /**
     * Strips trailing spaces and tabs from every line, then drops any blank lines left at the end
     * of the text. Leading whitespace and blank lines in the middle of the text are kept.
     */
    public static String forCopy(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = stripTrailingSpacesAndTabs(lines[i]);
        }
        int lastNonBlank = lines.length - 1;
        while (lastNonBlank >= 0 && lines[lastNonBlank].isEmpty()) lastNonBlank--;
        if (lastNonBlank < 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i <= lastNonBlank; i++) {
            if (i > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString();
    }

    private static String stripTrailingSpacesAndTabs(String line) {
        int end = line.length();
        while (end > 0) {
            char c = line.charAt(end - 1);
            if (c != ' ' && c != '\t') break;
            end--;
        }
        return line.substring(0, end);
    }

    /**
     * Windows Terminal's {@code TrimPaste}: a one-line paste loses its trailing whitespace and
     * newline (so a copied command runs immediately instead of queuing an empty line behind it);
     * a multi-line paste is returned unchanged, since a newline in the middle is meant to run.
     */
    public static String forPaste(String text) {
        if (text == null || text.isEmpty()) return text;
        int lastNonSpace = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            if (!isPasteWhitespace(text.charAt(i))) {
                lastNonSpace = i;
                break;
            }
        }
        if (lastNonSpace < 0) return "";
        int firstNewline = Integer.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\u000B' || c == '\f' || c == '\r') {
                firstNewline = i;
                break;
            }
        }
        if (firstNewline < lastNonSpace) return text;
        return text.substring(0, lastNonSpace + 1);
    }

    /** "\t\n\v\f\r " - the set {@code TrimPaste} treats as trailing whitespace. */
    private static boolean isPasteWhitespace(char c) {
        return c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r' || c == ' ';
    }
}
