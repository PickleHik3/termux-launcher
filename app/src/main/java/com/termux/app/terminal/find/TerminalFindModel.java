package com.termux.app.terminal.find;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Everything a find session decides, with no Android and no views in it.
 *
 * <p>The session has three modes and they are the whole state machine. {@link Mode#TYPING} builds
 * the query and re-searches on every keystroke, so matches light up as the query is typed.
 * {@link Mode#NAVIGATE} is vim's normal mode over the transcript: {@code n}/{@code N} walk matches,
 * motions move a copy-mode cursor. {@link Mode#SELECT} is the same with a selection anchored, so
 * every motion extends it until it is yanked or dropped.</p>
 *
 * <p>Rows are transcript rows in the emulator's coordinate system — negative above the screen,
 * {@code 0..rows-1} on it — so a row here can be handed straight to the view without translation.</p>
 */
public final class TerminalFindModel {

    /** Beyond this the session stops collecting; a query matching everything is not a search. */
    public static final int MAX_MATCHES = 500;

    public enum Mode { TYPING, NAVIGATE, SELECT }

    /** What the caller has to do after a key: nothing, redraw, or the session is over. */
    public enum Result { IGNORED, HANDLED, YANKED, CLOSED }

    /** Selection shapes, mirroring vim's v, V and Ctrl-V. */
    public enum Selection { NONE, CHAR, LINE, BLOCK }

    /** One row of the searched snapshot. */
    public static final class Line {
        public final int row;
        @NonNull public final String text;

        public Line(int row, @NonNull String text) {
            this.row = row;
            this.text = text;
        }
    }

    /** One match, on one row, columns inclusive. */
    public static final class Match {
        public final int row;
        public final int startColumn;
        public final int endColumn;

        Match(int row, int startColumn, int endColumn) {
            this.row = row;
            this.startColumn = startColumn;
            this.endColumn = endColumn;
        }
    }

    @NonNull private final List<Line> lines;
    @NonNull private final StringBuilder query = new StringBuilder();
    @NonNull private final List<Match> matches = new ArrayList<>();
    @NonNull private Mode mode = Mode.TYPING;
    @NonNull private Selection selection = Selection.NONE;
    private int current = -1;
    private int cursorRow;
    private int cursorColumn;
    private int anchorRow;
    private int anchorColumn;
    /** Set by a first {@code g}, cleared by whatever follows it. */
    private boolean pendingG;
    @Nullable private String yanked;

    public TerminalFindModel(@NonNull List<Line> lines) {
        this.lines = new ArrayList<>(lines);
        this.cursorRow = lines.isEmpty() ? 0 : lines.get(lines.size() - 1).row;
    }

    @NonNull public String query() { return query.toString(); }
    @NonNull public Mode mode() { return mode; }
    @NonNull public Selection selection() { return selection; }
    @NonNull public List<Match> matches() { return Collections.unmodifiableList(matches); }
    public int currentIndex() { return current; }
    public int cursorRow() { return cursorRow; }
    public int cursorColumn() { return cursorColumn; }
    public int anchorRow() { return anchorRow; }
    public int anchorColumn() { return anchorColumn; }
    @Nullable public String yankedText() { return yanked; }

    /** The row the caller should scroll into view, or null when nothing needs revealing. */
    @Nullable
    public Integer focusRow() {
        if (mode != Mode.TYPING) return cursorRow;
        if (current < 0 || current >= matches.size()) return null;
        return matches.get(current).row;
    }

    /** "3/17", or "0/0" for a query with no hits, or empty while the query is. */
    @NonNull
    public String counter() {
        if (query.length() == 0) return "";
        return (matches.isEmpty() ? 0 : current + 1) + "/" + matches.size();
    }

    // ------------------------------------------------------------------------------ query editing

    public Result typeText(@NonNull String text) {
        if (mode != Mode.TYPING || text.isEmpty()) return Result.IGNORED;
        query.append(text);
        research();
        return Result.HANDLED;
    }

    public Result backspace() {
        if (mode != Mode.TYPING) return Result.IGNORED;
        if (query.length() == 0) return Result.CLOSED;
        query.deleteCharAt(query.length() - 1);
        research();
        return Result.HANDLED;
    }

    /** ⏎ leaves the query behind and drops the cursor on the current match. */
    public Result commitQuery() {
        if (mode != Mode.TYPING) return Result.IGNORED;
        mode = Mode.NAVIGATE;
        Match match = currentMatch();
        if (match != null) {
            cursorRow = match.row;
            cursorColumn = match.startColumn;
        }
        return Result.HANDLED;
    }

    /** Back to editing the query, keeping what was typed. */
    public Result editQuery() {
        mode = Mode.TYPING;
        selection = Selection.NONE;
        return Result.HANDLED;
    }

    /**
     * Escape unwinds one layer at a time — selection, then navigation, then the session — so a
     * mis-started block select costs one key rather than the whole search.
     */
    public Result escape() {
        if (mode == Mode.SELECT) {
            selection = Selection.NONE;
            mode = Mode.NAVIGATE;
            return Result.HANDLED;
        }
        return Result.CLOSED;
    }

    // -------------------------------------------------------------------------- match navigation

    /** Walks matches, wrapping at both ends the way vim's n/N does. */
    public Result step(int delta) {
        if (matches.isEmpty()) return Result.HANDLED;
        int size = matches.size();
        current = ((current + delta) % size + size) % size;
        Match match = matches.get(current);
        if (mode != Mode.TYPING) {
            cursorRow = match.row;
            cursorColumn = match.startColumn;
        }
        return Result.HANDLED;
    }

    // ---------------------------------------------------------------------------- vim key intake

    /**
     * One key in navigate or select mode. Characters only; the caller maps its own key events and
     * soft keys onto these so all three input channels agree on what a key means.
     *
     * @param ctrl whether Ctrl was held, which is the only modifier that changes a meaning here.
     */
    public Result command(char key, boolean ctrl) {
        if (mode == Mode.TYPING) return Result.IGNORED;
        if (ctrl) {
            if (key == 'v' || key == 'V') return startSelection(Selection.BLOCK);
            return Result.IGNORED;
        }
        boolean hadPendingG = pendingG;
        pendingG = false;
        if (hadPendingG && key == 'g') return moveTo(firstRow(), 0);
        switch (key) {
            case 'n': return step(1);
            case 'N': return step(-1);
            case 'h': return moveTo(cursorRow, cursorColumn - 1);
            case 'l': return moveTo(cursorRow, cursorColumn + 1);
            case 'j': return moveTo(cursorRow + 1, cursorColumn);
            case 'k': return moveTo(cursorRow - 1, cursorColumn);
            case '0': return moveTo(cursorRow, 0);
            case '$': return moveTo(cursorRow, lastColumn(cursorRow));
            case 'w': return moveTo(cursorRow, nextWord(cursorRow, cursorColumn));
            case 'b': return moveTo(cursorRow, previousWord(cursorRow, cursorColumn));
            case 'g': pendingG = true; return Result.HANDLED;
            case 'G': return moveTo(lastRow(), 0);
            case 'v': return startSelection(Selection.CHAR);
            case 'V': return startSelection(Selection.LINE);
            case 'y': return yank();
            case '/': return editQuery();
            default: return Result.IGNORED;
        }
    }

    private Result startSelection(@NonNull Selection shape) {
        if (selection == shape) {
            // The same key again drops the selection, exactly as it does in vim.
            selection = Selection.NONE;
            mode = Mode.NAVIGATE;
            return Result.HANDLED;
        }
        if (selection == Selection.NONE) {
            anchorRow = cursorRow;
            anchorColumn = cursorColumn;
        }
        selection = shape;
        mode = Mode.SELECT;
        return Result.HANDLED;
    }

    private Result moveTo(int row, int column) {
        cursorRow = clamp(row, firstRow(), lastRow());
        cursorColumn = Math.max(0, Math.min(column, Math.max(0, lastColumn(cursorRow))));
        return Result.HANDLED;
    }

    /**
     * Yanks the selection, or the current match's whole line when nothing is selected, and ends the
     * session — a yank with nowhere to put the text is not worth a mode.
     */
    public Result yank() {
        yanked = selectedText();
        return yanked == null ? Result.HANDLED : Result.YANKED;
    }

    @Nullable
    public String selectedText() {
        if (selection == Selection.NONE) {
            String line = textOf(cursorRow);
            return line == null ? null : trimTrailing(line);
        }
        int firstRow = Math.min(anchorRow, cursorRow);
        int lastRow = Math.max(anchorRow, cursorRow);
        StringBuilder out = new StringBuilder();
        for (int row = firstRow; row <= lastRow; row++) {
            String line = textOf(row);
            if (line == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(trimTrailing(sliceForRow(line, row, firstRow, lastRow)));
        }
        return out.toString();
    }

    @NonNull
    private String sliceForRow(@NonNull String line, int row, int firstRow, int lastRow) {
        switch (selection) {
            case LINE:
                return line;
            case BLOCK: {
                int from = Math.min(anchorColumn, cursorColumn);
                int to = Math.max(anchorColumn, cursorColumn);
                return substring(line, from, to);
            }
            default: {
                boolean forward = cursorRow > anchorRow
                    || (cursorRow == anchorRow && cursorColumn >= anchorColumn);
                int startColumn = forward ? anchorColumn : cursorColumn;
                int endColumn = forward ? cursorColumn : anchorColumn;
                int from = row == firstRow ? startColumn : 0;
                int to = row == lastRow ? endColumn : Math.max(0, line.length() - 1);
                return substring(line, from, to);
            }
        }
    }

    // --------------------------------------------------------------------------------- searching

    private void research() {
        matches.clear();
        current = -1;
        String needle = query.toString().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return;
        for (Line line : lines) {
            String haystack = line.text.toLowerCase(Locale.ROOT);
            int from = 0;
            while (matches.size() < MAX_MATCHES) {
                int index = haystack.indexOf(needle, from);
                if (index < 0) break;
                matches.add(new Match(line.row, index, index + needle.length() - 1));
                from = index + 1;
            }
            if (matches.size() >= MAX_MATCHES) break;
        }
        if (matches.isEmpty()) return;
        // Start on the last match: the newest output is where the eye already is, and it is what
        // the old sheet's newest-first list put at the top.
        current = matches.size() - 1;
    }

    @Nullable
    private Match currentMatch() {
        return current >= 0 && current < matches.size() ? matches.get(current) : null;
    }

    // ---------------------------------------------------------------------------------- geometry

    private int firstRow() { return lines.isEmpty() ? 0 : lines.get(0).row; }

    private int lastRow() { return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).row; }

    @Nullable
    private String textOf(int row) {
        int index = row - firstRow();
        return index >= 0 && index < lines.size() ? lines.get(index).text : null;
    }

    private int lastColumn(int row) {
        String line = textOf(row);
        return line == null ? 0 : Math.max(0, line.length() - 1);
    }

    private int nextWord(int row, int column) {
        String line = textOf(row);
        if (line == null) return column;
        int i = column;
        while (i < line.length() && !Character.isWhitespace(line.charAt(i))) i++;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return Math.min(i, Math.max(0, line.length() - 1));
    }

    private int previousWord(int row, int column) {
        String line = textOf(row);
        if (line == null) return column;
        int i = Math.min(column, line.length()) - 1;
        while (i > 0 && Character.isWhitespace(line.charAt(i))) i--;
        while (i > 0 && !Character.isWhitespace(line.charAt(i - 1))) i--;
        return Math.max(0, i);
    }

    @NonNull
    private static String substring(@NonNull String line, int from, int to) {
        int start = Math.max(0, Math.min(from, line.length()));
        int end = Math.max(start, Math.min(to + 1, line.length()));
        return line.substring(start, end);
    }

    @NonNull
    private static String trimTrailing(@NonNull String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') end--;
        return value.substring(0, end);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
