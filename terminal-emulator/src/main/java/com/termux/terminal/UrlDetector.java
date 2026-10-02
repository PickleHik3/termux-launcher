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
 * flag. Such rows are joined by pane window, the way iTerm2 restricts a scan to the column span
 * between two dividers: a column holding a vertical line glyph on a run of consecutive rows is a
 * divider, and the cells between two dividers (or a divider and the screen edge) are one window.
 * Text is matched per window, so a sidebar or a neighbouring pane is never part of an address.
 * When an address runs to its window's last cell (or one short of it at the screen's right edge),
 * and the row below, in the same window, opens with text after any indentation, the address is
 * tried with that text appended and kept if the match grows. Without dividers the window is the
 * whole row, which is kitty's rule: join when the address fills the last column.</li>
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

    /** Consecutive rows a vertical line glyph must fill in one column for it to be a pane divider. */
    private static final int DIVIDER_RUN = 8;

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

    /**
     * The address whose cells include {@code (column, row)}, or null. The search covers the whole
     * screen, not a window around the row: an address a program wrapped by hand carries no wrap
     * flag, so a window would hand back a truncated address where the renderer underlines a whole
     * one, and a tap would open an address the user never saw.
     */
    public static UrlSpan at(TerminalBuffer screen, int column, int row) {
        if (column < 0 || column >= screen.mColumns) return null;
        int first = Math.min(row, 0);
        int last = Math.max(row, screen.mScreenRows - 1);
        for (UrlSpan span : find(screen, first, last)) {
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

        // Dividers are found once over every row the walks below may touch. A run is measured on
        // the rows read, so a divider cut by the edge of that span can only be shorter than it is.
        final int dividerFirst = Math.max(minRow, firstRow - CONTEXT_ROWS - 2 * MAX_WRAP_CHAIN);
        final int dividerLast = Math.min(maxRow, lastRow + CONTEXT_ROWS + MAX_WRAP_CHAIN);
        final Dividers dividers = new Dividers(screen, dividerFirst, dividerLast, Math.min(DIVIDER_RUN, screen.mScreenRows));

        int scanFirst = Math.max(minRow, firstRow - CONTEXT_ROWS);
        for (int i = 0; i < MAX_WRAP_CHAIN && scanFirst > dividerFirst && lineWraps(screen, scanFirst - 1); i++) scanFirst--;
        // A program that wraps by hand sets no wrap flag, so the chain above has to be walked by
        // sight: while the row above still runs to a window's edge it may carry the start of an
        // address that reaches into the range, and without it the rows in view match nothing at all.
        for (int i = 0; i < MAX_WRAP_CHAIN && scanFirst > dividerFirst && rowReachesEdge(screen, dividers, scanFirst - 1); i++) scanFirst--;
        int scanLast = Math.min(maxRow, lastRow + CONTEXT_ROWS);
        for (int i = 0; i < MAX_WRAP_CHAIN && scanLast < dividerLast && lineWraps(screen, scanLast); i++) scanLast++;

        final int columns = screen.mColumns;
        List<Line> lines = new ArrayList<>();
        for (int row = scanFirst; row <= scanLast; ) {
            if (lineWraps(screen, row) && row < scanLast) {
                // Rows the emulator wrapped are full-width by definition: one line, no windows.
                Line line = new Line(0, columns);
                line.appendRow(screen, row);
                while (lineWraps(screen, row) && row < scanLast) {
                    row++;
                    line.appendRow(screen, row);
                }
                line.trimTrailingSpaces();
                if (line.text.length() > 0) lines.add(line);
            } else {
                int[] windows = dividers.windows(row);
                for (int w = 0; w < windows.length; w += 2) {
                    Line line = new Line(windows[w], windows[w + 1]);
                    line.appendRow(screen, row);
                    line.trimTrailingSpaces();
                    if (line.text.length() > 0) lines.add(line);
                }
            }
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
                // Follow the address into the window's next row while it runs to the window's edge.
                Line current = line;
                int searchFrom = index + 1;
                while (end == current.text.length() && current.endsAtWindowEdge(columns)) {
                    int nextIndex = lineBelow(lines, searchFrom, current.lastRow() + 1, current.left, current.right);
                    if (nextIndex < 0) break;
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
                    searchFrom = nextIndex + 1;
                }
                match.trimTrailingPunctuation();
                if (match.length() == 0) continue;
                UrlSpan span = match.toSpan();
                if (span.lastRow() >= firstRow && span.firstRow() <= lastRow) found.add(span);
            }
        }
        return found;
    }

    /** The index of the line that starts on {@code row} inside exactly the window {@code [left, right)}, or -1. */
    private static int lineBelow(List<Line> lines, int from, int row, int left, int right) {
        for (int i = from; i < lines.size(); i++) {
            Line line = lines.get(i);
            int first = line.rows.get(0);
            if (first > row) return -1;
            if (first == row && line.left == left && line.right == right) return i;
        }
        return -1;
    }

    private static boolean lineWraps(TerminalBuffer screen, int row) {
        TerminalRow line = screen.mLines[screen.externalToInternalRow(row)];
        return line != null && line.mLineWrap;
    }

    /**
     * Whether some pane window of a row ends in text: a non-blank, non-border cell at the window's
     * last column, or one short of it at the screen's right edge. Such a row may carry the start of
     * an address that continues below.
     */
    private static boolean rowReachesEdge(TerminalBuffer screen, Dividers dividers, int externalRow) {
        char[] cells = readCells(screen, externalRow);
        int[] windows = dividers.windows(externalRow);
        for (int w = 0; w < windows.length; w += 2) {
            int left = windows[w];
            int right = windows[w + 1];
            if (isText(cells[right - 1])) return true;
            if (right == cells.length && right - 2 >= left && isText(cells[right - 2])) return true;
        }
        return false;
    }

    private static boolean isText(char c) {
        return c != ' ' && !isBorderGlyph(c);
    }

    /** One char per cell of a row: blank where empty, the glyph in every cell a wide glyph covers. */
    private static char[] readCells(TerminalBuffer screen, int externalRow) {
        char[] cells = new char[screen.mColumns];
        java.util.Arrays.fill(cells, ' ');
        TerminalRow line = screen.mLines[screen.externalToInternalRow(externalRow)];
        if (line == null) return cells;
        final char[] chars = line.mText;
        final int used = line.getSpaceUsed();
        int column = 0;
        for (int i = 0; i < used; ) {
            char c = chars[i];
            boolean high = Character.isHighSurrogate(c) && i + 1 < used;
            int width = line.getDisplayWidthAt(i);
            if (width > 0) {
                for (int k = 0; k < width && column + k < cells.length; k++) cells[column + k] = high ? '\uFFFD' : c;
                column += width;
            }
            i += high ? 2 : 1;
        }
        return cells;
    }

    /** Box-drawing and block glyphs: a multiplexer's pane edge or scrollbar, never part of an address. */
    static boolean isBorderGlyph(char c) {
        return c == '|' || (c >= '\u2500' && c <= '\u259F');
    }

    /**
     * Glyphs that draw a vertical line through their cell: the candidates for a pane divider.
     * Horizontal-only glyphs, corners and the down or up tees are not.
     */
    static boolean isDividerGlyph(char c) {
        if (c == '|') return true;
        return c == '\u2502' || c == '\u2503' || c == '\u2506' || c == '\u2507' || c == '\u250A' || c == '\u250B'
            || (c >= '\u251C' && c <= '\u252B')   // Left and right tees and their weights.
            || (c >= '\u253C' && c <= '\u254B')   // Crosses and their weights.
            || c == '\u254E' || c == '\u254F'     // Dashed verticals.
            || c == '\u2551'                       // Double vertical.
            || (c >= '\u255E' && c <= '\u2563')   // Double-line left and right tees.
            || (c >= '\u256A' && c <= '\u256C');  // Double-line crosses.
    }

    /**
     * The vertical divider columns of a span of rows. A column is a divider on a row when divider
     * glyphs fill it on a run of at least {@code minRun} consecutive rows that includes that row; a
     * stray bar in prose, or the pane edge on a status row that lacks it, is not one. The runs are
     * found in one top-to-bottom pass per column, so the cost is rows times columns.
     */
    private static final class Dividers {
        private final int mFirst;
        private final int mColumns;
        /** Per row from {@code mFirst}: the divider columns, or null for a row without any candidate. */
        private final boolean[][] mCells;

        Dividers(TerminalBuffer screen, int first, int last, int minRun) {
            mFirst = first;
            mColumns = screen.mColumns;
            int rows = Math.max(0, last - first + 1);
            mCells = new boolean[rows][];
            boolean any = false;
            for (int r = 0; r < rows; r++) {
                TerminalRow line = screen.mLines[screen.externalToInternalRow(first + r)];
                if (line == null) continue;
                final char[] chars = line.mText;
                final int used = line.getSpaceUsed();
                int column = 0;
                for (int i = 0; i < used; ) {
                    char c = chars[i];
                    boolean high = Character.isHighSurrogate(c) && i + 1 < used;
                    int width = line.getDisplayWidthAt(i);
                    if (width > 0) {
                        if (!high && column < mColumns && isDividerGlyph(c)) {
                            if (mCells[r] == null) mCells[r] = new boolean[mColumns];
                            mCells[r][column] = true;
                            any = true;
                        }
                        column += width;
                    }
                    i += high ? 2 : 1;
                }
            }
            if (!any) return;
            for (int c = 0; c < mColumns; c++) {
                int r = 0;
                while (r < rows) {
                    if (!has(r, c)) {
                        r++;
                        continue;
                    }
                    int runStart = r;
                    while (r < rows && has(r, c)) r++;
                    if (r - runStart < minRun) {
                        for (int k = runStart; k < r; k++) mCells[k][c] = false;
                    }
                }
            }
        }

        private boolean has(int r, int c) {
            return mCells[r] != null && mCells[r][c];
        }

        /** The pane windows of a row as {@code left, rightExclusive} pairs, left to right; dividers belong to none. */
        int[] windows(int externalRow) {
            int r = externalRow - mFirst;
            boolean[] d = r >= 0 && r < mCells.length ? mCells[r] : null;
            if (d == null) return new int[] {0, mColumns};
            int count = 0;
            for (boolean b : d) if (b) count++;
            int[] out = new int[2 * (count + 1)];
            int n = 0;
            int left = 0;
            for (int c = 0; c <= mColumns; c++) {
                if (c == mColumns || d[c]) {
                    if (c > left) {
                        out[n++] = left;
                        out[n++] = c;
                    }
                    left = c + 1;
                }
            }
            return java.util.Arrays.copyOf(out, n);
        }
    }

    /** The cell-by-cell text of one logical line: a pane window of a row, or the rows its wrap flags pull in. */
    private static final class Line {
        final StringBuilder text = new StringBuilder();
        int[] row = new int[64];
        int[] columnStart = new int[64];
        int[] columnEnd = new int[64];
        /** Rows this line covers, in order. */
        final List<Integer> rows = new ArrayList<>(1);
        /** Characters an address from the line above already absorbed; matching starts after them. */
        int consumed;
        /** The pane window {@code [left, right)} this line was read from; the full row for wrapped lines. */
        final int left;
        final int right;

        Line(int left, int right) {
            this.left = left;
            this.right = right;
        }

        int lastRow() {
            return rows.get(rows.size() - 1);
        }

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
                    // Cells outside this line's pane window belong to another pane or a divider.
                    if (column >= left && column + width <= right) {
                        char out = high ? '\uFFFD' : isBorderGlyph(c) ? ' ' : c;
                        add(out, externalRow, column, column + width);
                    }
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
         * Whether the line's text ends where an address would be wrapped: in the window's last cell,
         * or one short of it when the window ends at the screen's right edge.
         */
        boolean endsAtWindowEdge(int columns) {
            int n = text.length();
            if (n == 0) return false;
            int end = columnEnd[n - 1];
            return end == right || (right == columns && end == right - 1);
        }

        /**
         * Where text continuing an address from the row above would start: the first non-blank cell,
         * which is the window's left edge or follows indentation only. -1 when the row offers nothing.
         */
        int continuationStart() {
            int i = 0;
            int n = text.length();
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n || i < consumed) return -1;
            // Only the first row of this line can continue the row above; a wrapped tail cannot.
            return row[i] == rows.get(0) ? i : -1;
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
        // Host name or domain. Unicode letters, digits and marks for international names, but not
        // symbols: fish's ⏎ after output without a newline, or a pane border, is not part of a host.
        final String h = "[a-z0-9\\p{L}\\p{N}\\p{M}]";
        regex_sb.append("(?:(?:" + h + "-*)*" + h + "+)(?:(?:\\.(?:" + h + "-*)*" + h + "+)*(?:\\.(?:" + h + "-*){1,}" + h + "{1,}))?|");
        // Just path. Used in case of 'file://' scheme.
        regex_sb.append("/(?:(?:" + h + "-*)*" + h + "+)");
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
