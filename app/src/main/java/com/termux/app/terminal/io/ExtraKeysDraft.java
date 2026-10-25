package com.termux.app.terminal.io;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor's working copy of the toolbar: one or two pages of rows of keys, and every edit the
 * page editor and its sheets can make.
 *
 * <p>The runtime knows exactly two pages ({@code extra-keys} and {@code extra-keys2}), and page one
 * always exists. Page two exists in the draft once it holds keys or the reader added it, and goes
 * again when it is removed; an absent page two is saved as the empty page, which the toolbar
 * treats as no page. Nothing here touches Android, so the rules are tested on the JVM and the
 * fragment only applies the answers.
 */
public final class ExtraKeysDraft {

    /** The most pages the toolbar pager can show. */
    public static final int MAX_PAGES = 2;

    /** A deep copy of the draft, taken before an edit so the editor can offer Undo. */
    public static final class Snapshot {
        private final List<ExtraKeysLayoutModel> pages;

        private Snapshot(@NonNull List<ExtraKeysLayoutModel> pages) {
            this.pages = pages;
        }
    }

    private final List<ExtraKeysLayoutModel> pages = new ArrayList<>();

    /**
     * Builds a draft from the loaded pages, in order. Page one is kept even when empty; a later
     * page is kept only when it holds keys, so an empty page two reads as absent.
     */
    @NonNull
    public static ExtraKeysDraft of(@NonNull List<ExtraKeysLayoutModel> loaded) {
        ExtraKeysDraft draft = new ExtraKeysDraft();
        for (int i = 0; i < loaded.size() && i < MAX_PAGES; i++) {
            ExtraKeysLayoutModel model = loaded.get(i);
            if (i == 0 || !model.isEmpty()) draft.pages.add(copyOf(model));
        }
        if (draft.pages.isEmpty()) draft.pages.add(ExtraKeysLayoutModel.empty());
        return draft;
    }

    private ExtraKeysDraft() {}

    // ---------------------------------------------------------------- pages

    public int pageCount() {
        return pages.size();
    }

    @NonNull
    public ExtraKeysLayoutModel page(int index) {
        return pages.get(index);
    }

    public boolean canAddPage() {
        return pages.size() < MAX_PAGES;
    }

    /** Adds an empty page at the end and returns its index, or -1 when both pages exist. */
    public int addPage() {
        if (!canAddPage()) return -1;
        pages.add(ExtraKeysLayoutModel.empty());
        return pages.size() - 1;
    }

    public boolean canMovePage(int index, int delta) {
        int to = index + delta;
        return index >= 0 && index < pages.size() && to >= 0 && to < pages.size() && to != index;
    }

    /** Swaps the page with its neighbour. */
    public boolean movePage(int index, int delta) {
        if (!canMovePage(index, delta)) return false;
        ExtraKeysLayoutModel moved = pages.remove(index);
        pages.add(index + delta, moved);
        return true;
    }

    /** Whether Remove page does anything: a lone empty page has nothing to remove. */
    public boolean canRemovePage(int index) {
        if (index < 0 || index >= pages.size()) return false;
        return pages.size() > 1 || !pages.get(index).isEmpty();
    }

    /**
     * Removes a page. With two pages the other one stays (and becomes page one when the first
     * goes); the last page is emptied instead, because page one always exists.
     *
     * @return the index to show afterwards.
     */
    public int removePage(int index) {
        if (!canRemovePage(index)) return Math.max(0, Math.min(index, pages.size() - 1));
        if (pages.size() > 1) {
            pages.remove(index);
        } else {
            pages.set(0, ExtraKeysLayoutModel.empty());
        }
        return Math.min(index, pages.size() - 1);
    }

    /** Replaces a page's whole content, as a preset does. */
    public void setPage(int index, @NonNull ExtraKeysLayoutModel model) {
        pages.set(index, model);
    }

    // ----------------------------------------------------------------- rows

    /** Adds an empty row at the end of the page and returns its index. */
    public int addRow(int page) {
        ExtraKeysLayoutModel model = pages.get(page);
        model.addRow();
        return model.rowCount() - 1;
    }

    public boolean canMoveRow(int page, int row, int delta) {
        ExtraKeysLayoutModel model = pages.get(page);
        int to = row + delta;
        return row >= 0 && row < model.rowCount() && to >= 0 && to < model.rowCount() && to != row;
    }

    public boolean moveRow(int page, int row, int delta) {
        if (!canMoveRow(page, row, delta)) return false;
        List<List<ExtraKeysLayoutModel.Key>> rows = pages.get(page).rows();
        rows.add(row + delta, rows.remove(row));
        return true;
    }

    public void removeRow(int page, int row) {
        pages.get(page).removeRow(row);
    }

    // ----------------------------------------------------------------- keys

    /** Appends a key to a row. A page with no rows gets its first one. */
    public void addKey(int page, int row, @NonNull ExtraKeysLayoutModel.Key key) {
        ExtraKeysLayoutModel model = pages.get(page);
        if (model.rowCount() == 0) model.addRow();
        int target = Math.max(0, Math.min(row, model.rowCount() - 1));
        model.row(target).add(key);
    }

    /** Where a key sits in a page, as {row, index}, or null when it is not on it. */
    public static int[] locate(@NonNull ExtraKeysLayoutModel model,
                               @NonNull ExtraKeysLayoutModel.Key key) {
        for (int r = 0; r < model.rowCount(); r++) {
            List<ExtraKeysLayoutModel.Key> row = model.row(r);
            for (int i = 0; i < row.size(); i++) {
                if (row.get(i) == key) return new int[]{r, i};
            }
        }
        return null;
    }

    public boolean canMoveKey(int page, @NonNull ExtraKeysLayoutModel.Key key, int delta) {
        int[] at = locate(pages.get(page), key);
        if (at == null) return false;
        int to = at[1] + delta;
        return delta != 0 && to >= 0 && to < pages.get(page).row(at[0]).size();
    }

    /** Moves a key one step along its row (never into another row). */
    public boolean moveKey(int page, @NonNull ExtraKeysLayoutModel.Key key, int delta) {
        if (!canMoveKey(page, key, delta)) return false;
        int[] at = locate(pages.get(page), key);
        List<ExtraKeysLayoutModel.Key> row = pages.get(page).row(at[0]);
        row.add(at[1] + delta, row.remove(at[1]));
        return true;
    }

    public boolean removeKey(int page, @NonNull ExtraKeysLayoutModel.Key key) {
        int[] at = locate(pages.get(page), key);
        if (at == null) return false;
        pages.get(page).row(at[0]).remove(at[1]);
        return true;
    }

    // ----------------------------------------------------------------- undo

    @NonNull
    public Snapshot snapshot() {
        List<ExtraKeysLayoutModel> copy = new ArrayList<>(pages.size());
        for (ExtraKeysLayoutModel model : pages) copy.add(copyOf(model));
        return new Snapshot(copy);
    }

    public void restore(@NonNull Snapshot snapshot) {
        pages.clear();
        for (ExtraKeysLayoutModel model : snapshot.pages) pages.add(copyOf(model));
    }

    // ---------------------------------------------------------------- saving

    /**
     * The property value to write for a page index: the page's keys, or the empty page when the
     * draft has no such page. Empty rows are left out, as {@link ExtraKeysLayoutModel#serialize}
     * always does.
     */
    @NonNull
    public String valueFor(int pageIndex) {
        return pageIndex < pages.size() ? pages.get(pageIndex).serialize() : "[]";
    }

    /** Both property values in one string, to compare a draft against the state it started from. */
    @NonNull
    public String fingerprint() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < MAX_PAGES; i++) out.append(valueFor(i)).append('\n');
        return out.toString();
    }

    @NonNull
    private static ExtraKeysLayoutModel copyOf(@NonNull ExtraKeysLayoutModel model) {
        List<List<ExtraKeysLayoutModel.Key>> rows = new ArrayList<>(model.rowCount());
        for (List<ExtraKeysLayoutModel.Key> row : model.rows()) {
            List<ExtraKeysLayoutModel.Key> keys = new ArrayList<>(row.size());
            for (ExtraKeysLayoutModel.Key key : row) keys.add(key.copy());
            rows.add(keys);
        }
        ExtraKeysLayoutModel copy = ExtraKeysLayoutModel.empty();
        copy.replaceRows(rows);
        return copy;
    }
}
