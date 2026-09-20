package com.termux.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds plain-text URLs on the screen and says exactly which cells each one occupies.
 *
 * <p>The view underlines those cells and a tap on one of them opens the address, so both read the
 * same answer: what is underlined is what opens. Detection works on the screen rather than on a
 * text dump so that an address can be mapped back to cells, and so that the row geometry — wrap
 * flags, the right edge, a multiplexer's pane border — can decide where an address continues.
 *
 * <p>Three kinds of row break are followed:
 * <ul>
 * <li>A row the emulator wrapped itself carries a wrap flag, and the next row is simply more of the
 * same line. Every terminal does this much.</li>
 * <li>A row a program wrapped for itself — a multiplexer pane, a TUI drawing into a box — carries no
 * flag. When such a row's address runs right up to the row's edge (the last cell, one cell of
 * padding short of it, or a pane border), and the row below opens with text after any border and
 * indentation, the address is tried with that text appended and kept if the match grows. Only an
 * address that reaches the edge is extended, so ordinary prose is never glued together, and only
 * the characters the URL grammar accepts are absorbed.</li>
 * <li>Trailing punctuation that closed a sentence rather than the address — {@code .,;:!?}, quotes,
 * and a closing bracket without its opener inside the address — is dropped, as kitty does.</li>
 * </ul>
 */
public final class UrlDetector {

    /**
     * The URL grammar. The first group is the scheme with its {@code ://}; the whole match is the
     * address. Shared with the transcript-text search in termux-shared so the two never disagree.
     */
    public static final Pattern URL_PATTERN = compileUrlPattern();

    /** Rows read around the requested range, so an address that starts or ends just outside it is whole. */
    private static final int CONTEXT_ROWS = 3;

    /** How far a chain of wrap flags is followed beyond the context, so a long paragraph stays one line. */
    private static final int MAX_WRAP_CHAIN = 64;

    private UrlDetector() {
    }

    /** One detected address and the cells it occupies. */
    public static final class UrlSpan {

        public final String url;

        /** Triples of {@code row, firstColumn, endColumnExclusive}, one per row the address touches, top to bottom. */
        private final int[] mSegments;

        UrlSpan(String url, int[] segments) {
            this.url = url;
            mSegments = segments;
        }

        public int segmentCount() {
            return mSegments.length / 3;
        }

        public int segmentRow(int segment) {
            return mSegments[segment * 3];
        }

        public int segmentStartColumn(int segment) {
            return mSegments[segment * 3 + 1];
        }

        /** Exclusive. */
        public int segmentEndColumn(int segment) {
            return mSegments[segment * 3 + 2];
        }

        public int firstRow() {
            return mSegments[0];
        }

        public int lastRow() {
            return mSegments[mSegments.length - 3];
        }

        public boolean covers(int row, int column) {
            for (int i = 0; i < mSegments.length; i += 3) {
                if (mSegments[i] == row && column >= mSegments[i + 1] && column < mSegments[i + 2])
                    return true;
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(url);
            for (int i = 0; i < mSegments.length; i += 3)
                sb.append(" [").append(mSegments[i]).append(':').append(mSegments[i + 1]).append('-').append(mSegments[i + 2]).append(']');
            return sb.toString();
        }
    }

    /** The address whose cells include {@code (column, row)}, or null. */
    public static UrlSpan at(TerminalBuffer screen, int column, int row) {
        if (column < 0 || column >= screen.mColumns) return null;
        for (UrlSpan span : find(screen, row, row)) {
            if (span.covers(row, column)) return span;
        }
        return null;
    }

    /**
     * Every address that touches a row in {@code [firstRow, lastRow]}, top to bottom. Rows are
     * external: negative for the transcript, {@code 0..mScreenRows-1} for the screen.
     */
    public static List<UrlSpan> find(TerminalBuffer screen, int firstRow, int lastRow) {
        List<UrlSpan> found = new ArrayList<>();
        final int minRow = -screen.getActiveTranscriptRows();
        final int maxRow = screen.mScreenRows - 1;
        if (firstRow > lastRow || lastRow < minRow || firstRow > maxRow) return found;
        firstRow = Math.max(minRow, firstRow);
        lastRow = Math.min(maxRow, lastRow);

        int scanFirst = Math.max(minRow, firstRow - CONTEXT_ROWS);
        for (int i = 0; i < MAX_WRAP_CHAIN && scanFirst > minRow && lineWraps(screen, scanFirst - 1); i++) scanFirst--;
        int scanLast = Math.min(maxRow, lastRow + CONTEXT_ROWS);
        for (int i = 0; i < MAX_WRAP_CHAIN && scanLast < maxRow && lineWraps(screen, scanLast); i++) scanLast++;

        List<Line> lines = new ArrayList<>();
        for (int row = scanFirst; row <= scanLast; ) {
            Line line = new Line();
            line.appendRow(screen, row);
            while (lineWraps(screen, row) && row < scanLast) {
                row++;
                line.appendRow(screen, row);
            }
            line.trimTrailingSpaces();
            lines.add(line);
            row++;
        }

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            Matcher matcher = URL_PATTERN.matcher(line.text);
            int from = line.consumed;
            while (from <= line.text.length() && matcher.find(from)) {
                int start = matcher.start(1);
                int end = matcher.end();
                from = end;
                Match match = new Match();
                match.append(line, start, end);
                // Follow the address into the rows below while it runs to a row's edge.
                Line current = line;
                int nextIndex = index + 1;
                while (end == current.text.length() && nextIndex < lines.size()
                    && current.reachesEdge(screen, current.text.length())) {
                    Line next = lines.get(nextIndex);
                    int contStart = next.continuationStart();
                    if (contStart < 0) break;
                    String continuation = next.text.substring(contStart);
                    if (URL_PATTERN.matcher(continuation).lookingAt()) break;  // A new address, not more of this one.
                    Matcher grown = URL_PATTERN.matcher(match.text() + continuation);
                    if (!grown.lookingAt() || grown.end() <= match.length()) break;
                    int consumed = grown.end() - match.length();
                    match.append(next, contStart, contStart + consumed);
                    next.consumed = contStart + consumed;
                    if (contStart + consumed < next.text.length()) break;  // Ended mid-row.
                    current = next;
                    end = next.text.length();
                    nextIndex++;
                }
                match.trimTrailingPunctuation();
                if (match.length() == 0) continue;
                UrlSpan span = match.toSpan();
                if (span.lastRow() >= firstRow && span.firstRow() <= lastRow) found.add(span);
            }
        }
        return found;
    }

    private static boolean lineWraps(TerminalBuffer screen, int row) {
        TerminalRow line = screen.mLines[screen.externalToInternalRow(row)];
        return line != null && line.mLineWrap;
    }

    /** Box-drawing and block glyphs: a multiplexer's pane edge or scrollbar, never part of an address. */
    static boolean isBorderGlyph(char c) {
        return c == '|' || (c >= '\u2500' && c <= '\u259F');
    }

    /** The cell-by-cell text of one logical line: a row and the rows its wrap flags pull in. */
    private static final class Line {
        final StringBuilder text = new StringBuilder();
        int[] row = new int[64];
        int[] columnStart = new int[64];
        int[] columnEnd = new int[64];
        /** Rows this line covers, in order. */
        final List<Integer> rows = new ArrayList<>(1);
        /** Characters an address from the line above already absorbed; matching starts after them. */
        int consumed;

        void appendRow(TerminalBuffer screen, int externalRow) {
            rows.add(externalRow);
            TerminalRow line = screen.mLines[screen.externalToInternalRow(externalRow)];
            if (line == null) return;
            final char[] chars = line.mText;
            final int used = line.getSpaceUsed();
            int column = 0;
            for (int i = 0; i < used; ) {
                char c = chars[i];
                boolean high = Character.isHighSurrogate(c) && i + 1 < used;
                int width = line.getDisplayWidthAt(i);
                int charCount = high ? 2 : 1;
                if (width > 0) {
                    // Non-BMP glyphs and box drawing never belong to an address: a placeholder or a
                    // space keeps the cell mapping one char per cell and stops the grammar there.
                    char out = high ? '\uFFFD' : isBorderGlyph(c) ? ' ' : c;
                    add(out, externalRow, column, column + width);
                    column += width;
                }
                i += charCount;
            }
        }

        private void add(char c, int r, int start, int end) {
            int n = text.length();
            if (n == row.length) {
                row = java.util.Arrays.copyOf(row, n * 2);
                columnStart = java.util.Arrays.copyOf(columnStart, n * 2);
                columnEnd = java.util.Arrays.copyOf(columnEnd, n * 2);
            }
            text.append(c);
            row[n] = r;
            columnStart[n] = start;
            columnEnd[n] = end;
        }

        void trimTrailingSpaces() {
            int n = text.length();
            while (n > 0 && text.charAt(n - 1) == ' ') n--;
            text.setLength(n);
        }

        /**
         * Whether text ending at {@code index} runs to its row's edge: the last cell, one cell of
         * padding short of it, or a pane border after at most one cell of padding.
         */
        boolean reachesEdge(TerminalBuffer screen, int index) {
            if (index == 0) return false;
            int r = row[index - 1];
            int column = columnEnd[index - 1];
            TerminalRow line = screen.mLines[screen.externalToInternalRow(r)];
            if (line == null) return false;
            int columns = screen.mColumns;
            if (column >= columns - 1) return true;
            // Read the raw cells after the address: [ ]? border.
            int probe = column;
            if (cellChar(line, probe) == ' ') probe++;
            return probe < columns && isBorderGlyph(cellChar(line, probe));
        }

        /**
         * Where text continuing an address from the row above would start: past leading spaces —
         * which is where a pane border, blanked in {@link #appendRow}, its padding and any
         * indentation all went. -1 when the row opens with nothing.
         */
        int continuationStart() {
            int i = 0;
            int n = text.length();
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n || i < consumed) return -1;
            // Only the first row of this line can continue the row above; a wrapped tail cannot.
            return row[i] == rows.get(0) ? i : -1;
        }

        private static char cellChar(TerminalRow line, int column) {
            int index = line.findStartOfColumn(column);
            if (index >= line.getSpaceUsed()) return ' ';
            return line.mText[index];
        }
    }

    /** An address under construction, with the cell behind every character. */
    private static final class Match {
        private final StringBuilder mText = new StringBuilder();
        private int[] mCells = new int[3 * 64];
        private int mLength;

        void append(Line line, int from, int to) {
            for (int i = from; i < to; i++) {
                if (mLength * 3 == mCells.length) mCells = java.util.Arrays.copyOf(mCells, mCells.length * 2);
                mText.append(line.text.charAt(i));
                mCells[mLength * 3] = line.row[i];
                mCells[mLength * 3 + 1] = line.columnStart[i];
                mCells[mLength * 3 + 2] = line.columnEnd[i];
                mLength++;
            }
        }

        String text() {
            return mText.toString();
        }

        int length() {
            return mLength;
        }

        void trimTrailingPunctuation() {
            while (mLength > 0) {
                char last = mText.charAt(mLength - 1);
                boolean drop;
                switch (last) {
                    case '.': case ',': case ';': case ':': case '!': case '?': case '\'': case '"':
                        drop = true;
                        break;
                    case ')':
                        drop = count('(') < count(')');
                        break;
                    case ']':
                        drop = count('[') < count(']');
                        break;
                    case '}':
                        drop = count('{') < count('}');
                        break;
                    default:
                        drop = false;
                }
                if (!drop) break;
                mLength--;
                mText.setLength(mLength);
            }
        }

        private int count(char c) {
            int n = 0;
            for (int i = 0; i < mLength; i++) if (mText.charAt(i) == c) n++;
            return n;
        }

        UrlSpan toSpan() {
            List<Integer> segments = new ArrayList<>();
            int i = 0;
            while (i < mLength) {
                int r = mCells[i * 3];
                int start = mCells[i * 3 + 1];
                int end = mCells[i * 3 + 2];
                int j = i + 1;
                while (j < mLength && mCells[j * 3] == r && mCells[j * 3 + 1] == end) {
                    end = mCells[j * 3 + 2];
                    j++;
                }
                segments.add(r);
                segments.add(start);
                segments.add(end);
                i = j;
            }
            int[] packed = new int[segments.size()];
            for (int k = 0; k < packed.length; k++) packed[k] = segments.get(k);
            return new UrlSpan(mText.toString(), packed);
        }
    }

    private static Pattern compileUrlPattern() {
        StringBuilder regex_sb = new StringBuilder();
        // Begin first matching group.
        regex_sb.append("(");
        // Begin scheme group.
        regex_sb.append("(?:");
        regex_sb.append("dav|");
        regex_sb.append("dict|");
        regex_sb.append("dns|");
        regex_sb.append("file|");
        regex_sb.append("finger|");
        regex_sb.append("ftp(?:s?)|");
        regex_sb.append("git|");
        regex_sb.append("gemini|");
        regex_sb.append("gopher|");
        regex_sb.append("http(?:s?)|");
        regex_sb.append("imap(?:s?)|");
        regex_sb.append("irc(?:[6s]?)|");
        regex_sb.append("ip[fn]s|");
        regex_sb.append("ldap(?:s?)|");
        regex_sb.append("pop3(?:s?)|");
        regex_sb.append("redis(?:s?)|");
        regex_sb.append("rsync|");
        regex_sb.append("rtsp(?:[su]?)|");
        regex_sb.append("sftp|");
        regex_sb.append("smb(?:s?)|");
        regex_sb.append("smtp(?:s?)|");
        regex_sb.append("svn(?:(?:\\+ssh)?)|");
        regex_sb.append("tcp|");
        regex_sb.append("telnet|");
        regex_sb.append("tftp|");
        regex_sb.append("udp|");
        regex_sb.append("vnc|");
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
        regex_sb.append("(?:/[a-zA-Z0-9:@%\\-._~!$&'()*+,;=?/\\[\\]]*)?");
        // Fragment.
        regex_sb.append("(?:#[a-zA-Z0-9:@%\\-._~!$&'()*+,;=?/\\[\\]]*)?");
        // End second matching group.
        regex_sb.append(")");
        return Pattern.compile(regex_sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    }
}
