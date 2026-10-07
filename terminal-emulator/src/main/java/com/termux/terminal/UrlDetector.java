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
 * <p>Four kinds of row break are followed:
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
 * <li>A program that wraps inside a padded box stops its rows short of the window's edge, so the
 * rule above never sees them. Such a row is still followed when the address is the last text on
 * it (only blanks follow, up to the window's edge), the row below in the same window starts its
 * text at the same column as this row's text (one program wrapping one paragraph), and that text
 * is a single run with nothing after it, as the tail of a hand-wrapped address is. As a guard, the
 * address must cover at least {@value #MIN_HAND_WRAPPED_CELLS} cells of its row: a short address
 * ending a line is far more often complete than wrapped. A word after the tail, such as the rest
 * of the next log line or a prompt with a command typed, keeps the rows apart.</li>
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

    /** The fewest cells an address must cover on a row that stops short of its window's edge to continue below. */
    static final int MIN_HAND_WRAPPED_CELLS = 24;

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
        return find(screen, firstRow, lastRow, new Cache(false));
    }

    /**
     * {@link #find(TerminalBuffer, int, int)}, answered from {@code cache} wherever the text it
     * would read is unchanged since the cache's last call. The answer is the same either way.
     */
    public static List<UrlSpan> find(TerminalBuffer screen, int firstRow, int lastRow, Cache cache) {
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
        // sight: while the row above still runs to a window's edge, or wraps by hand into the row
        // below it, it may carry the start of an address that reaches into the range, and without
        // it the rows in view match nothing at all.
        for (int i = 0; i < MAX_WRAP_CHAIN && scanFirst > dividerFirst && rowReachesEdge(screen, dividers, scanFirst - 1); i++) scanFirst--;
        int scanLast = Math.min(maxRow, lastRow + CONTEXT_ROWS);
        for (int i = 0; i < MAX_WRAP_CHAIN && scanLast < dividerLast && lineWraps(screen, scanLast); i++) scanLast++;

        final int columns = screen.mColumns;
        final List<Line> lines = cache.mLines;
        lines.clear();
        cache.mLinesInUse = 0;
        for (int row = scanFirst; row <= scanLast; ) {
            if (lineWraps(screen, row) && row < scanLast) {
                // Rows the emulator wrapped are full-width by definition: one line, no windows.
                Line line = cache.obtainLine(0, columns);
                line.appendRow(screen, row);
                while (lineWraps(screen, row) && row < scanLast) {
                    row++;
                    line.appendRow(screen, row);
                }
                line.trimTrailingSpaces();
                if (line.text.length() > 0) lines.add(line); else cache.mLinesInUse--;
            } else {
                int[] windows = dividers.windows(row);
                for (int w = 0; w < windows.length; w += 2) {
                    Line line = cache.obtainLine(windows[w], windows[w + 1]);
                    line.appendRow(screen, row);
                    line.trimTrailingSpaces();
                    if (line.text.length() > 0) lines.add(line); else cache.mLinesInUse--;
                }
            }
            row++;
        }

        // An address only ever continues from a line into the one recorded in mBelow for it, and
        // the scan below follows no other pair, so lines joined that way form a group whose answer
        // depends on nothing outside it. Both rules decide from the two lines' own text and cells,
        // which the group's signature holds. A group whose text and cells match one from the
        // cache's last call keeps that call's answer, moved to where the group now stands; only
        // the others are matched against the grammar.
        final int count = lines.size();
        final Cache.Frame frame = cache.beginFrame(count);
        for (int index = 0; index < count; index++) {
            frame.mBelow[index] = -1;
            Line line = lines.get(index);
            boolean atEdge = line.endsAtWindowEdge(columns);
            if (!atEdge && !line.mayWrapByHand()) continue;
            int below = lineBelow(lines, index + 1, line.lastRow() + 1, line.left, line.right);
            if (below < 0 || (!atEdge && !lines.get(below).continuesByHand(line))) continue;
            frame.mBelow[index] = below;
            frame.join(index, below);
        }
        frame.collectGroups();
        for (int g = 0; g < frame.mGroupCount; g++) cache.lookUp(frame, g, lines, columns);

        for (int index = 0; index < count; index++) {
            if (frame.mEntry[frame.mRoot[index]] != null) continue;
            cache.mScannedLines++;
            Line line = lines.get(index);
            Matcher matcher = URL_PATTERN.matcher(line.text);
            int from = line.consumed;
            while (from <= line.text.length() && matcher.find(from)) {
                int start = matcher.start(1);
                int end = matcher.end();
                from = end;
                Match match = new Match();
                match.append(line, start, end);
                // Follow the address into the window's next row while it is the last text on its
                // row and the grouping above joined that row to the next.
                Line current = line;
                int currentIndex = index;
                int rowStart = start;
                while (end == current.text.length()) {
                    int nextIndex = frame.mBelow[currentIndex];
                    if (nextIndex < 0) break;
                    boolean byHand = !current.endsAtWindowEdge(columns);
                    if (byHand && current.columnEnd[end - 1] - current.columnStart[rowStart] < MIN_HAND_WRAPPED_CELLS) break;
                    Line next = lines.get(nextIndex);
                    int contStart = next.continuationStart();
                    if (contStart < 0) break;
                    String continuation = next.text.substring(contStart);
                    if (URL_PATTERN.matcher(continuation).lookingAt()) break;  // A new address, not more of this one.
                    Matcher grown = URL_PATTERN.matcher(match.text() + continuation);
                    if (!grown.lookingAt() || grown.end() <= match.length()) break;
                    int consumed = grown.end() - match.length();
                    // A hand-wrapped tail is all of its row; a word after it means the rows are unrelated.
                    if (byHand && contStart + consumed < next.text.length()) break;
                    match.append(next, contStart, contStart + consumed);
                    next.consumed = contStart + consumed;
                    if (contStart + consumed < next.text.length()) break;  // Ended mid-row.
                    current = next;
                    currentIndex = nextIndex;
                    rowStart = contStart;
                    end = next.text.length();
                }
                match.trimTrailingPunctuation();
                if (match.length() == 0) continue;
                frame.addScanned(index, match.toSpan());
            }
        }
        cache.endFrame(frame);

        // Out in line order, as one pass over every line would have found them.
        for (int index = 0; index < count; index++) {
            Cache.Entry entry = frame.mEntry[frame.mRoot[index]];
            int base = frame.mBase[frame.mRoot[index]];
            int ordinal = frame.mOrdinal[index];
            for (int k = 0; k < entry.mOrdinals.length; k++) {
                if (entry.mOrdinals[k] != ordinal) continue;
                UrlSpan span = entry.spanAt(k, base);
                if (span.lastRow() >= firstRow && span.firstRow() <= lastRow) found.add(span);
            }
        }
        return found;
    }

    /**
     * What {@link #find(TerminalBuffer, int, int, Cache)} found last time, group by group, and
     * the line objects it reads the screen into. One per caller that asks every frame; not
     * thread-safe.
     */
    public static final class Cache {

        /** One group's answer, kept with the exact cells it was computed from. */
        static final class Entry {
            final long mHash;
            final int[] mSignature;
            /** Per address: the ordinal, within the group, of the line it was found on. */
            final int[] mOrdinals;
            final String[] mUrls;
            /** Per address: its segments with rows relative to the group's first row. */
            final int[][] mRelativeSegments;
            /** The spans as last handed out, and the group's first row they were handed out at. */
            final UrlSpan[] mSpans;
            int mSpansBase;

            Entry(long hash, int[] signature, int[] ordinals, UrlSpan[] spans, int base) {
                mHash = hash;
                mSignature = signature;
                mOrdinals = ordinals;
                mSpans = spans;
                mSpansBase = base;
                mUrls = new String[spans.length];
                mRelativeSegments = new int[spans.length][];
                for (int k = 0; k < spans.length; k++) {
                    mUrls[k] = spans[k].url;
                    int[] segments = spans[k].mSegments.clone();
                    for (int i = 0; i < segments.length; i += 3) segments[i] -= base;
                    mRelativeSegments[k] = segments;
                }
            }

            UrlSpan spanAt(int k, int base) {
                if (base != mSpansBase) {
                    for (int i = 0; i < mSpans.length; i++) {
                        int[] segments = mRelativeSegments[i].clone();
                        for (int j = 0; j < segments.length; j += 3) segments[j] += base;
                        mSpans[i] = new UrlSpan(mUrls[i], segments);
                    }
                    mSpansBase = base;
                }
                return mSpans[k];
            }
        }

        /** The grouping of one call's lines; reused from call to call. */
        static final class Frame {
            int[] mRoot = new int[0];
            int[] mOrdinal = new int[0];
            int[] mNextMember = new int[0];
            int[] mLastMember = new int[0];
            /** Per line: the line an address on it may continue into, or -1. The scan follows nothing else. */
            int[] mBelow = new int[0];
            /** Indexed by root line: the group's entry, its first row and, while scanning, what it found. */
            Entry[] mEntry = new Entry[0];
            int[] mBase = new int[0];
            long[] mHash = new long[0];
            int[] mSignatureStart = new int[0];
            int[] mSignatureEnd = new int[0];
            final List<List<UrlSpan>> mScannedSpans = new ArrayList<>();
            final List<List<Integer>> mScannedOrdinals = new ArrayList<>();
            /** Roots in line order. */
            int[] mGroups = new int[0];
            int mGroupCount;
            int mCount;

            void reset(int count) {
                mCount = count;
                if (mRoot.length < count) {
                    int size = Math.max(count, mRoot.length * 2);
                    mRoot = new int[size];
                    mOrdinal = new int[size];
                    mNextMember = new int[size];
                    mLastMember = new int[size];
                    mBelow = new int[size];
                    mEntry = new Entry[size];
                    mBase = new int[size];
                    mHash = new long[size];
                    mSignatureStart = new int[size];
                    mSignatureEnd = new int[size];
                    mGroups = new int[size];
                }
                for (int i = 0; i < count; i++) {
                    mRoot[i] = i;
                    mEntry[i] = null;
                }
                mGroupCount = 0;
            }

            private int root(int i) {
                while (mRoot[i] != i) {
                    mRoot[i] = mRoot[mRoot[i]];
                    i = mRoot[i];
                }
                return i;
            }

            /** The smaller index stays the root, so a group's root is its first line. */
            void join(int a, int b) {
                int ra = root(a), rb = root(b);
                if (ra == rb) return;
                if (ra < rb) mRoot[rb] = ra; else mRoot[ra] = rb;
            }

            void collectGroups() {
                for (int i = 0; i < mCount; i++) {
                    int r = root(i);
                    mRoot[i] = r;
                    mNextMember[i] = -1;
                    if (r == i) {
                        mGroups[mGroupCount++] = i;
                        mOrdinal[i] = 0;
                    } else {
                        mNextMember[mLastMember[r]] = i;
                        mOrdinal[i] = mOrdinal[mLastMember[r]] + 1;
                    }
                    mLastMember[r] = i;
                }
            }

            void addScanned(int index, UrlSpan span) {
                int root = mRoot[index];
                int slot = -1;
                for (int g = 0; g < mGroupCount; g++) if (mGroups[g] == root) slot = g;
                while (mScannedSpans.size() <= slot) {
                    mScannedSpans.add(new ArrayList<>());
                    mScannedOrdinals.add(new ArrayList<>());
                }
                mScannedSpans.get(slot).add(span);
                mScannedOrdinals.get(slot).add(mOrdinal[index]);
            }
        }

        final List<Line> mLines = new ArrayList<>();
        private final List<Line> mLinePool = new ArrayList<>();
        int mLinesInUse;

        /** False for the throwaway cache of a one-off call, which has nothing to remember for. */
        private final boolean mRemember;

        private final Frame mFrame = new Frame();
        private List<Entry> mEntries = new ArrayList<>();
        private List<Entry> mNextEntries = new ArrayList<>();
        private int[] mSignatures = new int[256];
        private int mSignaturesUsed;

        /** Lines matched against the grammar in the last call, for tests. */
        int mScannedLines;
        /** Groups that missed the cache in the last call, for tests. */
        int mScannedGroups;

        public Cache() {
            this(true);
        }

        private Cache(boolean remember) {
            mRemember = remember;
        }

        Line obtainLine(int left, int right) {
            Line line;
            if (mLinesInUse < mLinePool.size()) {
                line = mLinePool.get(mLinesInUse);
                line.reset(left, right);
            } else {
                line = new Line(left, right);
                mLinePool.add(line);
            }
            mLinesInUse++;
            return line;
        }

        Frame beginFrame(int count) {
            mScannedLines = 0;
            mScannedGroups = 0;
            mSignaturesUsed = 0;
            mNextEntries.clear();
            mFrame.reset(count);
            for (List<UrlSpan> spans : mFrame.mScannedSpans) spans.clear();
            for (List<Integer> ordinals : mFrame.mScannedOrdinals) ordinals.clear();
            return mFrame;
        }

        /**
         * Write the group's signature — every cell its answer is computed from, rows taken from
         * its first row — and take the last call's entry for it if one matches exactly.
         */
        void lookUp(Frame frame, int g, List<Line> lines, int columns) {
            final int root = frame.mGroups[g];
            final int base = lines.get(root).rows[0];
            frame.mBase[root] = base;
            if (!mRemember) {
                mScannedGroups++;
                return;
            }
            final int start = mSignaturesUsed;
            put(columns);
            for (int i = root; i >= 0; i = frame.mNextMember[i]) {
                Line line = lines.get(i);
                put(line.rowCount);
                for (int r = 0; r < line.rowCount; r++) put(line.rows[r] - base);
                put(line.left);
                put(line.right);
                final int length = line.text.length();
                put(length);
                final int lineFirstRow = line.rows[0];
                for (int k = 0; k < length; k++) {
                    put((line.text.charAt(k) << 16) | ((line.row[k] - lineFirstRow) & 0xFFFF));
                    put((line.columnStart[k] << 16) | ((line.columnEnd[k] - line.columnStart[k]) & 0xFFFF));
                }
            }
            final int end = mSignaturesUsed;
            long hash = 1125899906842597L;
            for (int k = start; k < end; k++) hash = hash * 31 + mSignatures[k];
            frame.mHash[root] = hash;
            frame.mSignatureStart[root] = start;
            frame.mSignatureEnd[root] = end;
            for (Entry entry : mEntries) {
                if (entry.mHash == hash && sameSignature(entry.mSignature, start, end)) {
                    frame.mEntry[root] = entry;
                    mNextEntries.add(entry);
                    return;
                }
            }
            mScannedGroups++;
        }

        private boolean sameSignature(int[] signature, int start, int end) {
            if (signature.length != end - start) return false;
            for (int k = 0; k < signature.length; k++) if (signature[k] != mSignatures[start + k]) return false;
            return true;
        }

        private void put(int value) {
            if (mSignaturesUsed == mSignatures.length) mSignatures = java.util.Arrays.copyOf(mSignatures, mSignatures.length * 2);
            mSignatures[mSignaturesUsed++] = value;
        }

        /** Turn every scanned group into an entry, and keep only this call's entries for the next. */
        void endFrame(Frame frame) {
            for (int g = 0; g < frame.mGroupCount; g++) {
                int root = frame.mGroups[g];
                if (frame.mEntry[root] != null) continue;
                List<UrlSpan> spans = g < frame.mScannedSpans.size() ? frame.mScannedSpans.get(g) : null;
                int n = spans == null ? 0 : spans.size();
                int[] ordinals = new int[n];
                for (int k = 0; k < n; k++) ordinals[k] = frame.mScannedOrdinals.get(g).get(k);
                int[] signature = mRemember
                    ? java.util.Arrays.copyOfRange(mSignatures, frame.mSignatureStart[root], frame.mSignatureEnd[root])
                    : null;
                Entry entry = new Entry(frame.mHash[root], signature, ordinals, n == 0 ? NO_SPANS : spans.toArray(new UrlSpan[0]), frame.mBase[root]);
                frame.mEntry[root] = entry;
                if (mRemember) mNextEntries.add(entry);
            }
            List<Entry> previous = mEntries;
            mEntries = mNextEntries;
            mNextEntries = previous;
            mNextEntries.clear();
        }
    }

    private static final UrlSpan[] NO_SPANS = new UrlSpan[0];

    /** The index of the line that starts on {@code row} inside exactly the window {@code [left, right)}, or -1. */
    private static int lineBelow(List<Line> lines, int from, int row, int left, int right) {
        for (int i = from; i < lines.size(); i++) {
            Line line = lines.get(i);
            int first = line.rows[0];
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
     * an address that continues below. So may a row that looks wrapped by hand into the row below:
     * a last run of text at least {@link #MIN_HAND_WRAPPED_CELLS} cells wide, and below it a single
     * run of text starting at the same column. The row below always exists: it is the scan's first.
     */
    private static boolean rowReachesEdge(TerminalBuffer screen, Dividers dividers, int externalRow) {
        char[] cells = readCells(screen, externalRow);
        char[] below = null;
        int[] windows = dividers.windows(externalRow);
        for (int w = 0; w < windows.length; w += 2) {
            int left = windows[w];
            int right = windows[w + 1];
            if (isText(cells[right - 1])) return true;
            if (right == cells.length && right - 2 >= left && isText(cells[right - 2])) return true;

            int end = right;
            while (end > left && !isText(cells[end - 1])) end--;
            int runStart = end;
            while (runStart > left && isText(cells[runStart - 1])) runStart--;
            if (end - runStart < MIN_HAND_WRAPPED_CELLS) continue;
            int margin = left;
            while (!isText(cells[margin])) margin++;
            if (below == null) below = readCells(screen, externalRow + 1);
            int k = left;
            while (k < right && !isText(below[k])) k++;
            if (k != margin) continue;
            while (k < right && isText(below[k])) k++;
            while (k < right && !isText(below[k])) k++;
            if (k == right) return true;
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
        /** The one window of a row without dividers; callers only read it. */
        private final int[] mWholeRow;

        Dividers(TerminalBuffer screen, int first, int last, int minRun) {
            mFirst = first;
            mColumns = screen.mColumns;
            mWholeRow = new int[] {0, mColumns};
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
            if (d == null) return mWholeRow;
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
        /** Rows this line covers, in order: the first {@link #rowCount} entries. */
        int[] rows = new int[4];
        int rowCount;
        /** Characters an address from the line above already absorbed; matching starts after them. */
        int consumed;
        /** The pane window {@code [left, right)} this line was read from; the full row for wrapped lines. */
        int left;
        int right;

        Line(int left, int right) {
            this.left = left;
            this.right = right;
        }

        /** Make a pooled line empty again, for the window {@code [left, right)}. */
        void reset(int left, int right) {
            this.left = left;
            this.right = right;
            text.setLength(0);
            rowCount = 0;
            consumed = 0;
        }

        int lastRow() {
            return rows[rowCount - 1];
        }

        void appendRow(TerminalBuffer screen, int externalRow) {
            if (rowCount == rows.length) rows = java.util.Arrays.copyOf(rows, rowCount * 2);
            rows[rowCount++] = externalRow;
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
         * The row-above half of the hand-wrap rule: a single row whose last run of text, which only
         * blanks follow to the window's edge since the text is trimmed, is at least
         * {@link #MIN_HAND_WRAPPED_CELLS} cells wide. An address that is the last text on the row
         * lies inside that run, so this holds whenever the scan could follow the row by hand.
         */
        boolean mayWrapByHand() {
            int n = text.length();
            if (rowCount != 1 || n == 0) return false;
            int k = n - 1;
            while (k > 0 && text.charAt(k - 1) != ' ') k--;
            return columnEnd[n - 1] - columnStart[k] >= MIN_HAND_WRAPPED_CELLS;
        }

        /**
         * The row-below half of the hand-wrap rule: this line's text starts, on its first row, at
         * the column where {@code above}'s text starts, and is one run with nothing after it.
         */
        boolean continuesByHand(Line above) {
            int start = firstTextIndex();
            int aboveStart = above.firstTextIndex();
            if (start < 0 || aboveStart < 0 || columnStart[start] != above.columnStart[aboveStart]) return false;
            for (int k = start; k < text.length(); k++) {
                if (text.charAt(k) == ' ' || row[k] != rows[0]) return false;
            }
            return true;
        }

        /** The index of the first non-blank character, or -1. */
        int firstTextIndex() {
            int n = text.length();
            for (int i = 0; i < n; i++) if (text.charAt(i) != ' ') return i;
            return -1;
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
            return row[i] == rows[0] ? i : -1;
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
