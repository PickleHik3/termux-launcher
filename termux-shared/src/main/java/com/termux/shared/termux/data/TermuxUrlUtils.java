package com.termux.shared.termux.data;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxUrlUtils {

    public static Pattern URL_MATCH_REGEX;

    public static Pattern getUrlMatchRegex() {
        if (URL_MATCH_REGEX != null)
            return URL_MATCH_REGEX;
        StringBuilder regex_sb = new StringBuilder();
        // Begin first matching group.
        regex_sb.append("(");
        // Begin scheme group.
        regex_sb.append("(?:");
        // The DAV proto.
        regex_sb.append("dav|");
        // The DICT proto.
        regex_sb.append("dict|");
        // The DNS proto.
        regex_sb.append("dns|");
        // File path.
        regex_sb.append("file|");
        // The Finger proto.
        regex_sb.append("finger|");
        // The FTP proto.
        regex_sb.append("ftp(?:s?)|");
        // The Git proto.
        regex_sb.append("git|");
        // The Gemini proto.
        regex_sb.append("gemini|");
        // The Gopher proto.
        regex_sb.append("gopher|");
        // The HTTP proto.
        regex_sb.append("http(?:s?)|");
        // The IMAP proto.
        regex_sb.append("imap(?:s?)|");
        // The IRC proto.
        regex_sb.append("irc(?:[6s]?)|");
        // The IPFS proto.
        regex_sb.append("ip[fn]s|");
        // The LDAP proto.
        regex_sb.append("ldap(?:s?)|");
        // The POP3 proto.
        regex_sb.append("pop3(?:s?)|");
        // The Redis proto.
        regex_sb.append("redis(?:s?)|");
        // The Rsync proto.
        regex_sb.append("rsync|");
        // The RTSP proto.
        regex_sb.append("rtsp(?:[su]?)|");
        // The SFTP proto.
        regex_sb.append("sftp|");
        // The SAMBA proto.
        regex_sb.append("smb(?:s?)|");
        // The SMTP proto.
        regex_sb.append("smtp(?:s?)|");
        // The Subversion proto.
        regex_sb.append("svn(?:(?:\\+ssh)?)|");
        // The TCP proto.
        regex_sb.append("tcp|");
        // The Telnet proto.
        regex_sb.append("telnet|");
        // The TFTP proto.
        regex_sb.append("tftp|");
        // The UDP proto.
        regex_sb.append("udp|");
        // The VNC proto.
        regex_sb.append("vnc|");
        // The Websocket proto.
        regex_sb.append("ws(?:s?)");
        // End scheme group.
        regex_sb.append(")://");
        // End first matching group.
        regex_sb.append(")");
        // Begin second matching group.
        regex_sb.append("(");
        // User name and/or password in format 'user:pass@'.
        regex_sb.append("(?:\\S+(?::\\S*)?@)?");
        // Begin host group.
        regex_sb.append("(?:");
        // IP address (from http://www.regular-expressions.info/examples.html).
        regex_sb.append("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|");
        // Host name or domain.
        regex_sb.append("(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*){1,}[a-z\\u00a1-\\uffff0-9]{1,}))?|");
        // Just path. Used in case of 'file://' scheme.
        regex_sb.append("/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)");
        // End host group.
        regex_sb.append(")");
        // Port number.
        regex_sb.append("(?::\\d{1,5})?");
        // Resource path with optional query string.
        regex_sb.append("(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");
        // Fragment.
        regex_sb.append("(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");
        // End second matching group.
        regex_sb.append(")");
        URL_MATCH_REGEX = Pattern.compile(regex_sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        return URL_MATCH_REGEX;
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
