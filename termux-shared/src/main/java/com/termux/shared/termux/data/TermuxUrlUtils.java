package com.termux.shared.termux.data;

import com.termux.terminal.UrlDetector;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxUrlUtils {

    /** The URL grammar, shared with {@link UrlDetector} so a tap and the transcript search agree. */
    public static Pattern getUrlMatchRegex() {
        return UrlDetector.URL_PATTERN;
    }

    /**
     * Every URL in {@code text}, in order of appearance.
     *
     * <p>Lines the terminal itself wrapped arrive already joined by the transcript. Lines a
     * multiplexer pane wrapped do not: a pane draws each row with the cursor, so the emulator sees
     * two rows with no wrap between them, and a URL that ran into the pane's border is cut in two.
     * Those are found a second time with the rows joined at the border, so the whole address is
     * offered alongside whatever each row held on its own.
     */
    public static LinkedHashSet<CharSequence> extractUrls(String text) {
        LinkedHashSet<CharSequence> urlSet = new LinkedHashSet<>();
        if (text == null) return urlSet;
        collectUrls(text, urlSet);
        String joined = joinLinesCutAtBorder(text);
        if (!joined.equals(text)) collectUrls(joined, urlSet);
        return urlSet;
    }

    private static void collectUrls(String text, LinkedHashSet<CharSequence> urlSet) {
        Matcher matcher = getUrlMatchRegex().matcher(text);
        while (matcher.find()) {
            int matchStart = matcher.start(1);
            int matchEnd = matcher.end();
            urlSet.add(text.substring(matchStart, matchEnd));
        }
    }

    /** Box-drawing and block glyphs a multiplexer draws as a pane's edge or scrollbar. */
    private static final String BORDER_GLYPHS = "|\u2502\u2503\u2551\u258c\u2590\u258f\u2595\u2591\u2592\u2593\u2588";

    private static boolean isBorderGlyph(char c) {
        return BORDER_GLYPHS.indexOf(c) >= 0;
    }

    /**
     * {@code text} with each line whose content runs up to a trailing pane border joined to the
     * content of the line after it. Only a line that fills its pane — content reaching the border,
     * with at most the pane's one cell of padding before it — is treated as cut; a line that stops
     * short ended on its own and is left alone. Package-private for tests.
     */
    static String joinLinesCutAtBorder(String text) {
        if (text.indexOf('\n') < 0) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            int cut = contentEndBeforeBorder(line);
            while (cut >= 0 && i + 1 < lines.length) {
                String next = lines[i + 1];
                int start = contentStartAfterBorder(next);
                if (start < 0) break;
                line = line.substring(0, cut) + next.substring(start);
                cut = contentEndBeforeBorder(line);
                i++;
            }
            out.append(line);
            i++;
            if (i < lines.length) out.append('\n');
        }
        return out.toString();
    }

    /**
     * Where the content of a cut line ends, or -1 when the line does not end in a border it runs up
     * to. Trailing whitespace, then one border glyph, then at most one space of pane padding, then a
     * non-space character: that is a row filled to its edge.
     */
    private static int contentEndBeforeBorder(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        if (end == 0 || !isBorderGlyph(line.charAt(end - 1))) return -1;
        end--;
        if (end > 0 && line.charAt(end - 1) == ' ') end--;
        if (end == 0 || Character.isWhitespace(line.charAt(end - 1))
            || isBorderGlyph(line.charAt(end - 1))) return -1;
        return end;
    }

    /**
     * Where the content of the row after a cut begins — past the pane's leading border and its one
     * cell of padding — or -1 when the row does not open with something to continue into.
     */
    private static int contentStartAfterBorder(String line) {
        int start = 0;
        while (start < line.length() && line.charAt(start) == ' ') start++;
        if (start < line.length() && isBorderGlyph(line.charAt(start))) {
            start++;
            if (start < line.length() && line.charAt(start) == ' ') start++;
        }
        if (start >= line.length()) return -1;
        char first = line.charAt(start);
        if (Character.isWhitespace(first) || isBorderGlyph(first)) return -1;
        return start;
    }
}
