package com.termux.app.assistant;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Assistant's small markdown reader (sheet 01.6): paragraphs, headings, bulleted and numbered
 * lists, fenced code blocks, and inline bold, italic, code and links. Tables and images are plain
 * text. It reads text that is still arriving: an unclosed fence is a code block holding what has
 * come so far, and an unclosed marker stays as the characters typed, so a half-written answer never
 * throws and never flickers into the wrong shape.
 */
public final class AssistantMarkdown {

    public enum Kind { PARAGRAPH, HEADING, LIST_ITEM, CODE }

    /** One block of an answer. {@code text} is the raw inline text, or the code for CODE. */
    public static final class Block {
        @NonNull public final Kind kind;
        @NonNull public final String text;
        /** The fence's language for CODE, or empty. */
        @NonNull public final String lang;
        /** The nesting of a list item; 0 is the top. */
        public final int level;
        /** The bullet or number of a list item. */
        @NonNull public final String marker;

        Block(@NonNull Kind kind, @NonNull String text, @NonNull String lang, int level, @NonNull String marker) {
            this.kind = kind;
            this.text = text;
            this.lang = lang;
            this.level = level;
            this.marker = marker;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Block)) return false;
            Block b = (Block) o;
            return kind == b.kind && text.equals(b.text) && lang.equals(b.lang) && level == b.level
                && marker.equals(b.marker);
        }

        @Override
        public int hashCode() {
            return text.hashCode() * 31 + kind.hashCode();
        }

        @Override
        public String toString() {
            return kind + (level > 0 ? "@" + level : "") + "[" + marker + text + "]";
        }
    }

    /** What the inline spans are drawn with. */
    public static final class Style {
        public final int codeBackground;
        public final int codeInk;
        public final int link;
        @Nullable public final Typeface mono;

        public Style(int codeBackground, int codeInk, int link, @Nullable Typeface mono) {
            this.codeBackground = codeBackground;
            this.codeInk = codeInk;
            this.link = link;
            this.mono = mono;
        }
    }

    private AssistantMarkdown() {
    }

    // ------------------------------------------------------------------------------- blocks

    @NonNull
    public static List<Block> parse(@Nullable String source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null || source.isEmpty()) return blocks;
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();
            if (isFence(line)) {
                flush(blocks, paragraph);
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                boolean first = true;
                while (i < lines.length && !isFence(lines[i])) {
                    if (!first) code.append('\n');
                    code.append(lines[i]);
                    first = false;
                    i++;
                }
                // i is on the closing fence, or past the end for a fence still open.
                i++;
                blocks.add(new Block(Kind.CODE, code.toString(), lang, 0, ""));
                continue;
            }
            if (trimmed.isEmpty()) {
                flush(blocks, paragraph);
                i++;
                continue;
            }
            int hashes = 0;
            while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') hashes++;
            if (hashes >= 1 && hashes <= 6 && trimmed.length() > hashes && trimmed.charAt(hashes) == ' ') {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.HEADING, trimmed.substring(hashes + 1).trim(), "", 0, ""));
                i++;
                continue;
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            String rest = line.substring(indent);
            if (rest.length() >= 2 && (rest.charAt(0) == '-' || rest.charAt(0) == '*' || rest.charAt(0) == '+')
                && rest.charAt(1) == ' ') {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.LIST_ITEM, rest.substring(2).trim(), "", Math.min(3, indent / 2), "•"));
                i++;
                continue;
            }
            int digits = 0;
            while (digits < rest.length() && Character.isDigit(rest.charAt(digits))) digits++;
            if (digits > 0 && digits <= 3 && rest.length() > digits + 1
                && (rest.charAt(digits) == '.' || rest.charAt(digits) == ')') && rest.charAt(digits + 1) == ' ') {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.LIST_ITEM, rest.substring(digits + 2).trim(), "", Math.min(3, indent / 2),
                    rest.substring(0, digits) + "."));
                i++;
                continue;
            }
            if (paragraph.length() > 0) paragraph.append('\n');
            paragraph.append(trimmed.startsWith("> ") ? trimmed.substring(2) : trimmed);
            i++;
        }
        flush(blocks, paragraph);
        return blocks;
    }

    private static boolean isFence(@NonNull String line) {
        String t = line.trim();
        return t.startsWith("```") && line.length() - t.length() <= 3;
    }

    private static void flush(@NonNull List<Block> blocks, @NonNull StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        blocks.add(new Block(Kind.PARAGRAPH, paragraph.toString(), "", 0, ""));
        paragraph.setLength(0);
    }

    // ------------------------------------------------------------------------------- inline

    /** The raw inline text as styled text. Unclosed markers are left as typed. */
    @NonNull
    public static Spannable inline(@NonNull String raw, @NonNull Style style) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        render(out, raw, 0, raw.length(), style);
        return out;
    }

    private static void render(SpannableStringBuilder out, String s, int from, int to, Style style) {
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (c == '`') {
                int close = s.indexOf('`', i + 1);
                if (close > i + 1 && close < to) {
                    int start = out.length();
                    out.append(s, i + 1, close);
                    int end = out.length();
                    if (style.mono != null) out.setSpan(new TypefaceSpan(style.mono), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    else out.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(0.875f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(style.codeBackground), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(style.codeInk), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            } else if ((c == '*' || c == '_') && i + 1 < to && s.charAt(i + 1) == c) {
                int close = s.indexOf(String.valueOf(c) + c, i + 2);
                if (close > i + 2 && close < to && !Character.isWhitespace(s.charAt(i + 2))) {
                    int start = out.length();
                    render(out, s, i + 2, close, style);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            } else if ((c == '*' || c == '_') && i + 1 < to && !Character.isWhitespace(s.charAt(i + 1))
                && s.charAt(i + 1) != c && !(c == '_' && i > from && Character.isLetterOrDigit(s.charAt(i - 1)))) {
                int close = closingSingle(s, c, i + 1, to);
                if (close > i + 1) {
                    int start = out.length();
                    render(out, s, i + 1, close, style);
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            } else if (c == '[') {
                int mid = s.indexOf("](", i + 1);
                int end = mid < 0 ? -1 : s.indexOf(')', mid + 2);
                if (mid > i + 1 && end > mid + 2 && end < to) {
                    String url = s.substring(mid + 2, end).trim();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        int start = out.length();
                        render(out, s, i + 1, mid, style);
                        out.setSpan(new URLSpan(url), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        out.setSpan(new ForegroundColorSpan(style.link), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        out.setSpan(new UnderlineSpan(), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
    }

    /** The index of the {@code marker} that closes an emphasis opened before {@code from}, or -1. */
    private static int closingSingle(String s, char marker, int from, int to) {
        for (int j = from; j < to; j++) {
            char c = s.charAt(j);
            if (c == '`') {
                int close = s.indexOf('`', j + 1);
                if (close > j && close < to) j = close;
                continue;
            }
            if (c != marker) continue;
            if (j + 1 < to && s.charAt(j + 1) == marker) {
                j++;
                continue;
            }
            if (Character.isWhitespace(s.charAt(j - 1))) continue;
            if (marker == '_' && j + 1 < to && Character.isLetterOrDigit(s.charAt(j + 1))) continue;
            return j;
        }
        return -1;
    }

    // ------------------------------------------------------------------------------- plain

    /** The text without its markdown marks, for a chat list's preview. One line. */
    @NonNull
    public static String plain(@Nullable String source) {
        StringBuilder out = new StringBuilder();
        for (Block block : parse(source)) {
            String text = block.text.replace('\n', ' ').trim();
            if (block.kind == Kind.CODE) text = text.isEmpty() ? "" : text;
            else {
                text = text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                    .replace("**", "").replace("__", "").replace("`", "")
                    .replaceAll("(^|\\s)[*_]([^*_\\s][^*_]*)[*_](?=\\s|$|[.,;:!?])", "$1$2");
            }
            if (text.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(text);
        }
        return out.toString();
    }
}
