package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The draft of a dictation being edited by hand on the card ({@link VoiceEditController}): the
 * text and a caret, nothing else. The caret is a code-point index, never a UTF-16 one, so it can
 * never land between the halves of a surrogate pair; {@link #caretUtf16()} is what the view draws
 * it at.
 *
 * <p>The text may hold line breaks (a Polished cleanup keeps its paragraphs and list items); they
 * are characters like any other here, and Home and End go to the start and end of the line the
 * caret is on.
 *
 * <p>Pure: no Android, testable on the JVM.
 */
public final class VoiceEditModel {

    private final StringBuilder draft = new StringBuilder();
    private int caret;

    /** {@code initial} with the caret at UTF-16 offset {@code caretUtf16}, clamped into it. */
    public VoiceEditModel(@Nullable String initial, int caretUtf16) {
        draft.append(initial == null ? "" : initial.replace("\r", ""));
        setCaretUtf16(caretUtf16);
    }

    @NonNull public String text() { return draft.toString(); }

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
        draft.insert(caretUtf16(), clean);
        caret += clean.codePointCount(0, clean.length());
    }

    public void backspace() {
        if (caret <= 0) return;
        int end = caretUtf16();
        int start = draft.offsetByCodePoints(0, caret - 1);
        draft.delete(start, end);
        caret--;
    }

    public void delete() {
        if (caret >= codePointCount()) return;
        int start = caretUtf16();
        int end = draft.offsetByCodePoints(0, caret + 1);
        draft.delete(start, end);
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
     * as it would have joined the text, and the caret stays where the user put it.
     */
    public void appendPhrase(@NonNull String piece) {
        String joined = joinOnto(draft.toString(), piece);
        draft.setLength(0);
        draft.append(joined);
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
