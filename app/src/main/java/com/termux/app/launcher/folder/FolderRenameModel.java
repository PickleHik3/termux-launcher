package com.termux.app.launcher.folder;

import androidx.annotation.NonNull;

/** Focusless Unicode title model. Caret positions are code-point indexes, never UTF-16 indexes. */
public final class FolderRenameModel {
    public static final int MAX_CODE_POINTS = 40;
    private final StringBuilder draft = new StringBuilder();
    private int caret;

    public FolderRenameModel(String initial) {
        insert(initial == null ? "" : initial);
        caret = codePointCount();
    }

    @NonNull public String text() { return draft.toString(); }
    public int caret() { return caret; }
    public int codePointCount() { return draft.codePointCount(0, draft.length()); }

    public void insert(@NonNull String value) {
        int remaining = MAX_CODE_POINTS - codePointCount();
        if (remaining <= 0 || value.isEmpty()) return;
        int accepted = Math.min(remaining, value.codePointCount(0, value.length()));
        int end = value.offsetByCodePoints(0, accepted);
        int utf16 = draft.offsetByCodePoints(0, Math.max(0, Math.min(caret, codePointCount())));
        draft.insert(utf16, value.substring(0, end));
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

    @NonNull public String committedTitle() {
        String trimmed = text().trim();
        return trimmed.isEmpty() ? "Folder" : trimmed;
    }
}
