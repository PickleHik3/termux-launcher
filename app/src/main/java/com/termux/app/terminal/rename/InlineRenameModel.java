package com.termux.app.terminal.rename;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Focusless Unicode draft for an inline rename. Caret positions are code-point indexes, never
 * UTF-16 indexes, so a caret can never land between the halves of a surrogate pair.
 *
 * <p>Sibling of {@code FolderRenameModel}, which does the same job for the drawer's folder titles.
 * They are deliberately separate types: the folder title has one fixed cap and a folder-specific
 * empty-name fallback, while a terminal rename carries a per-target cap and treats an emptied draft
 * as "clear the name", which is a meaningful outcome rather than a rejected one.
 */
public final class InlineRenameModel {

    private final StringBuilder draft = new StringBuilder();
    private final int maxCodePoints;
    private int caret;

    public InlineRenameModel(@Nullable String initial, int maxCodePoints) {
        this.maxCodePoints = Math.max(1, maxCodePoints);
        insert(initial == null ? "" : initial);
        caret = codePointCount();
    }

    @NonNull public String text() { return draft.toString(); }
    public int caret() { return caret; }
    public int maxCodePoints() { return maxCodePoints; }
    public int codePointCount() { return draft.codePointCount(0, draft.length()); }
    public int remaining() { return maxCodePoints - codePointCount(); }
    public boolean isEmpty() { return draft.length() == 0; }

    public void insert(@NonNull String value) {
        int remaining = remaining();
        if (remaining <= 0 || value.isEmpty()) return;
        int accepted = Math.min(remaining, value.codePointCount(0, value.length()));
        int end = value.offsetByCodePoints(0, accepted);
        int utf16 = draft.offsetByCodePoints(0, Math.max(0, Math.min(caret, codePointCount())));
        draft.insert(utf16, value, 0, end);
        caret += accepted;
    }

    public void backspace() {
        if (caret <= 0) return;
        int end = draft.offsetByCodePoints(0, caret);
        int start = draft.offsetByCodePoints(0, caret - 1);
        draft.delete(start, end);
        caret--;
    }

    public void delete() {
        if (caret >= codePointCount()) return;
        int start = draft.offsetByCodePoints(0, caret);
        int end = draft.offsetByCodePoints(0, caret + 1);
        draft.delete(start, end);
    }

    public void moveCaret(int delta) {
        caret = Math.max(0, Math.min(codePointCount(), caret + delta));
    }

    public void moveCaretToStart() { caret = 0; }

    public void moveCaretToEnd() { caret = codePointCount(); }

    /** Replaces the whole draft, e.g. with a suggested name, and parks the caret at its end. */
    public void replaceAll(@Nullable String value) {
        draft.setLength(0);
        caret = 0;
        insert(value == null ? "" : value);
    }

    /** What a commit stores: trimmed, or null when the user cleared the name. */
    @Nullable
    public String committedName() {
        String trimmed = text().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
