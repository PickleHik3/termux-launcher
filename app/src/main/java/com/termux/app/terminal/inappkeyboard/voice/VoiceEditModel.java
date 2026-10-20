package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The draft of a dictation being edited by hand on the card ({@link VoiceEditController}): the
 * text, a caret and the cleanup's marks. The caret is a code-point index, never a UTF-16 one, so
 * it can never land between the halves of a surrogate pair; {@link #caretUtf16()} is what the view
 * draws it at.
 *
 * <p>The marks are the words the cleanup changed or added ({@link #cleanupMarks}), kept in the
 * accent colour while the user edits. They move with the text the way a span set
 * {@code SPAN_EXCLUSIVE_EXCLUSIVE} does: typing inside a marked word grows the mark, typing at
 * either edge of it does not, deleting takes the deleted part out of it, and a mark left empty is
 * dropped. The cleanup's removed words are not in the draft, so they have no mark.
 *
 * <p>The text may hold line breaks (a Polished cleanup keeps its paragraphs and list items); they
 * are characters like any other here, and Home and End go to the start and end of the line the
 * caret is on.
 *
 * <p>Pure: no Android, testable on the JVM.
 */
public final class VoiceEditModel {

    /** A word the cleanup changed or added: UTF-16 offsets {@code [start, end)} into the draft. */
    public static final class Mark {
        public final int start;
        public final int end;
        /** {@link VoiceWordDiff.Kind#CHANGED} or {@link VoiceWordDiff.Kind#ADDED}. */
        @NonNull public final VoiceWordDiff.Kind kind;

        public Mark(int start, int end, @NonNull VoiceWordDiff.Kind kind) {
            this.start = start;
            this.end = end;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Mark)) return false;
            Mark mark = (Mark) other;
            return start == mark.start && end == mark.end && kind == mark.kind;
        }

        @Override
        public int hashCode() {
            return (start * 31 + end) * 31 + kind.hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return kind + "[" + start + "," + end + ")";
        }
    }

    private final StringBuilder draft = new StringBuilder();
    private int caret;
    /** In text order, never an empty range. */
    private final List<Mark> marks = new ArrayList<>();

    /** {@code initial} with the caret at UTF-16 offset {@code caretUtf16}, clamped into it. */
    public VoiceEditModel(@Nullable String initial, int caretUtf16) {
        this(initial, caretUtf16, Collections.emptyList());
    }

    /**
     * {@code initial} with the caret at UTF-16 offset {@code caretUtf16}, clamped into it, and
     * {@code initialMarks} (offsets into {@code initial}, in order) on it. A mark out of range is
     * clamped; one left empty is dropped.
     */
    public VoiceEditModel(@Nullable String initial, int caretUtf16, @NonNull List<Mark> initialMarks) {
        draft.append(initial == null ? "" : initial);
        for (Mark mark : initialMarks) {
            int start = Math.max(0, Math.min(mark.start, draft.length()));
            int end = Math.max(start, Math.min(mark.end, draft.length()));
            if (start < end) marks.add(new Mark(start, end, mark.kind));
        }
        // A carriage return is never kept; taking it out moves the marks like any delete.
        for (int at = draft.lastIndexOf("\r"); at >= 0; at = draft.lastIndexOf("\r", at - 1)) {
            deleteRange(at, at + 1);
        }
        setCaretUtf16(caretUtf16);
    }

    @NonNull public String text() { return draft.toString(); }

    /** The cleanup's marks as the draft now stands, in text order: what the view paints. */
    @NonNull public List<Mark> marks() { return Collections.unmodifiableList(marks); }

    /** The caret as a code-point index. */
    public int caret() { return caret; }

    /** The caret as a UTF-16 offset into {@link #text()}, for the view. */
    public int caretUtf16() { return draft.offsetByCodePoints(0, caret); }

    public int codePointCount() { return draft.codePointCount(0, draft.length()); }

    public boolean isEmpty() { return draft.length() == 0; }

    /** Puts the caret at UTF-16 {@code offset}: a tap, or a line up or down. Clamped; a split pair goes after it. */
    public void setCaretUtf16(int offset) {
        int clamped = Math.max(0, Math.min(offset, draft.length()));
        caret = draft.codePointCount(0, clamped);
    }

    /** Types {@code value} at the caret, which moves past it. A carriage return is never kept. */
    public void insert(@NonNull String value) {
        String clean = value.replace("\r", "");
        if (clean.isEmpty()) return;
        insertAt(caretUtf16(), clean);
        caret += clean.codePointCount(0, clean.length());
    }

    public void backspace() {
        if (caret <= 0) return;
        int end = caretUtf16();
        int start = draft.offsetByCodePoints(0, caret - 1);
        deleteRange(start, end);
        caret--;
    }

    public void delete() {
        if (caret >= codePointCount()) return;
        int start = caretUtf16();
        int end = draft.offsetByCodePoints(0, caret + 1);
        deleteRange(start, end);
    }

    public void moveCaret(int delta) {
        caret = Math.max(0, Math.min(codePointCount(), caret + delta));
    }

    /** Home: the start of the caret's line. */
    public void moveCaretToLineStart() {
        int at = caretUtf16();
        int lineStart = draft.lastIndexOf("\n", at - 1) + 1;
        setCaretUtf16(lineStart);
    }

    /** End: the end of the caret's line, before its line break. */
    public void moveCaretToLineEnd() {
        int at = caretUtf16();
        int lineEnd = draft.indexOf("\n", at);
        setCaretUtf16(lineEnd < 0 ? draft.length() : lineEnd);
    }

    /**
     * A phrase that was still transcribing when the editor opened: it joins the end of the draft
     * as it would have joined the text, unmarked (it is speech, not cleanup), and the caret stays
     * where the user put it.
     */
    public void appendPhrase(@NonNull String piece) {
        // joinOnto only ever adds to the end, so this is an insert there.
        String joined = joinOnto(draft.toString(), piece);
        insertAt(draft.length(), joined.substring(draft.length()));
    }

    /** Inserts {@code value} at UTF-16 {@code at}; a mark grows only when {@code at} is strictly inside it. */
    private void insertAt(int at, @NonNull String value) {
        int length = value.length();
        if (length == 0) return;
        draft.insert(at, value);
        for (int k = 0; k < marks.size(); k++) {
            Mark mark = marks.get(k);
            if (at <= mark.start) {
                marks.set(k, new Mark(mark.start + length, mark.end + length, mark.kind));
            } else if (at < mark.end) {
                marks.set(k, new Mark(mark.start, mark.end + length, mark.kind));
            }
        }
    }

    /** Deletes UTF-16 {@code [start, end)}: each mark loses what it had of it, later ones shift, empty ones go. */
    private void deleteRange(int start, int end) {
        if (start >= end) return;
        draft.delete(start, end);
        for (int k = marks.size() - 1; k >= 0; k--) {
            Mark mark = marks.get(k);
            int from = afterDelete(mark.start, start, end);
            int to = afterDelete(mark.end, start, end);
            if (from >= to) marks.remove(k);
            else if (from != mark.start || to != mark.end) marks.set(k, new Mark(from, to, mark.kind));
        }
    }

    private static int afterDelete(int offset, int start, int end) {
        if (offset <= start) return offset;
        if (offset >= end) return offset - (end - start);
        return start;
    }

    /**
     * The words the cleanup changed or added, as marks on {@code cleaned}, the text it produced
     * from {@code raw}: the words {@code VoiceTranscriptPanel#renderCleaned} paints in the accent,
     * found in {@code cleaned} itself, in order. The removed words are raw words, not in
     * {@code cleaned}, so they have none. The panel lays the words out with one space or the op's
     * line breaks, which need not be the cleaned text's own whitespace (runs of spaces, a third
     * line break, a tab), so each word is found in {@code cleaned} rather than laid out from the ops.
     */
    @NonNull
    public static List<Mark> cleanupMarks(@NonNull String raw, @NonNull String cleaned) {
        List<Mark> found = new ArrayList<>();
        int searchFrom = 0;
        for (VoiceWordDiff.Op op : VoiceWordDiff.diff(raw, cleaned)) {
            if (op.kind == VoiceWordDiff.Kind.REMOVED) continue;
            // Every other op is the cleaned text's next word, with only whitespace before it.
            int start = cleaned.indexOf(op.word, searchFrom);
            if (start < 0) break;
            int end = start + op.word.length();
            if (op.kind == VoiceWordDiff.Kind.CHANGED || op.kind == VoiceWordDiff.Kind.ADDED) {
                found.add(new Mark(start, end, op.kind));
            }
            searchFrom = end;
        }
        return found;
    }

    /**
     * {@code piece} (a phrase, its leading space or not) joined onto the end of {@code text}: one
     * space between them, none when {@code text} is empty or already ends in whitespace (a space
     * the user typed, or a line break).
     */
    @NonNull
    public static String joinOnto(@NonNull String text, @NonNull String piece) {
        String trimmed = piece.trim();
        if (trimmed.isEmpty()) return text;
        if (text.isEmpty() || Character.isWhitespace(text.charAt(text.length() - 1))) return text + trimmed;
        return text + " " + trimmed;
    }

    /**
     * Where a tap on the card's text lands in {@code shown}, the text the editor opens on.
     * {@code displayedOffset} is the TextView's offset under the finger; what is displayed is
     * {@code shown} itself, plus the ghost bar after it while a phrase transcribes, unless the
     * cleanup's marks are up ({@code ops} non-null): then the display is the merged word list,
     * removed words included, laid out by {@code VoiceTranscriptPanel#renderCleaned} (one space
     * between words, or the op's line breaks). A tap inside a kept word lands at the same place in
     * that word of {@code shown}; a tap on a removed word or between words lands at the end of the
     * kept word before it; anything past the last word, the ghost included, lands at the end.
     */
    public static int shownOffset(@Nullable List<VoiceWordDiff.Op> ops, @NonNull String shown,
                                  int displayedOffset) {
        if (displayedOffset < 0) return shown.length();
        if (ops == null) return Math.min(displayedOffset, shown.length());
        int display = 0;
        int searchFrom = 0;
        int lastKeptEnd = 0;
        boolean first = true;
        for (VoiceWordDiff.Op op : ops) {
            if (!first) {
                int separator = op.breaks > 0 ? op.breaks : 1;
                if (displayedOffset < display + separator) return lastKeptEnd;
                display += separator;
            }
            first = false;
            int length = op.word.length();
            if (op.kind == VoiceWordDiff.Kind.REMOVED) {
                if (displayedOffset <= display + length) return lastKeptEnd;
                display += length;
                continue;
            }
            int start = shown.indexOf(op.word, searchFrom);
            if (start < 0) start = Math.min(searchFrom, shown.length());
            if (displayedOffset <= display + length) {
                return Math.min(shown.length(), start + (displayedOffset - display));
            }
            lastKeptEnd = Math.min(shown.length(), start + length);
            searchFrom = lastKeptEnd;
            display += length;
        }
        return shown.length();
    }
}
