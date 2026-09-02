package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable form of one {@code extra-keys} page.
 *
 * <p>{@link com.termux.shared.termux.extrakeys.ExtraKeysInfo} parses the same property into an
 * immutable render tree; this parses it into something the row editor can reorder and write back.
 * The two stay separate on purpose: the render path must keep accepting every hand-written form
 * (bare tokens, aliases, {@code macro}, nested {@code popup}), while the editor only has to be
 * able to round-trip what it produces plus whatever it managed to read.
 *
 * <p>Serialization stays on one line, because the value is written back into a properties file.
 */
public final class ExtraKeysLayoutModel {

    /** One button: a key or a macro, an optional display override, an optional swipe-up key. */
    public static final class Key {
        @NonNull public String key;
        public boolean macro;
        @Nullable public String display;
        @Nullable public Key popup;

        public Key(@NonNull String key) {
            this.key = key;
        }

        public Key(@NonNull String key, boolean macro, @Nullable String display,
                   @Nullable Key popup) {
            this.key = key;
            this.macro = macro;
            this.display = display;
            this.popup = popup;
        }

        @NonNull
        public Key copy() {
            return new Key(key, macro, display, popup == null ? null : popup.copy());
        }

        /** What the editor shows for this key when no explicit display is set. */
        @NonNull
        public String label() {
            if (display != null && !display.isEmpty()) return display;
            return key;
        }
    }

    private final List<List<Key>> rows = new ArrayList<>();

    private ExtraKeysLayoutModel() {}

    @NonNull
    public static ExtraKeysLayoutModel empty() {
        return new ExtraKeysLayoutModel();
    }

    /**
     * Parses a property value. Anything unreadable yields an empty model rather than an exception:
     * the editor then shows an empty page, which the user can fill, instead of refusing to open.
     */
    @NonNull
    public static ExtraKeysLayoutModel parse(@Nullable String value) {
        ExtraKeysLayoutModel model = new ExtraKeysLayoutModel();
        if (value == null || value.trim().isEmpty()) return model;
        try {
            JSONArray rowsJson = new JSONArray(value);
            for (int r = 0; r < rowsJson.length(); r++) {
                JSONArray rowJson = rowsJson.optJSONArray(r);
                if (rowJson == null) continue;
                List<Key> row = new ArrayList<>();
                for (int c = 0; c < rowJson.length(); c++) {
                    Key key = parseKey(rowJson.opt(c));
                    if (key != null) row.add(key);
                }
                model.rows.add(row);
            }
        } catch (JSONException e) {
            return new ExtraKeysLayoutModel();
        }
        return model;
    }

    @Nullable
    private static Key parseKey(@Nullable Object element) {
        if (element == null) return null;
        if (element instanceof JSONObject) {
            JSONObject object = (JSONObject) element;
            String macro = object.optString("macro", null);
            String key = object.optString("key", null);
            if (macro != null && !macro.isEmpty()) {
                return new Key(macro, true, emptyToNull(object.optString("display", null)),
                    parseKey(object.opt("popup")));
            }
            if (key == null || key.isEmpty()) return null;
            return new Key(key, false, emptyToNull(object.optString("display", null)),
                parseKey(object.opt("popup")));
        }
        String literal = String.valueOf(element);
        return literal.isEmpty() ? null : new Key(literal);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @NonNull
    public List<List<Key>> rows() {
        return rows;
    }

    public int rowCount() {
        return rows.size();
    }

    public int keyCount() {
        int count = 0;
        for (List<Key> row : rows) count += row.size();
        return count;
    }

    public boolean isEmpty() {
        return keyCount() == 0;
    }

    @NonNull
    public List<Key> row(int index) {
        return rows.get(index);
    }

    public void addRow() {
        rows.add(new ArrayList<>());
    }

    public void removeRow(int index) {
        if (index >= 0 && index < rows.size()) rows.remove(index);
    }

    /** Reinserts a removed row for the editor's undo; the index is clamped into range. */
    public void insertRow(int index, @NonNull List<Key> keys) {
        int bounded = Math.max(0, Math.min(index, rows.size()));
        rows.add(bounded, new ArrayList<>(keys));
    }

    /** Swaps in a whole new row order, as the editor's list hands back after a drag. */
    public void replaceRows(@NonNull List<List<Key>> newRows) {
        rows.clear();
        for (List<Key> row : newRows) rows.add(new ArrayList<>(row));
    }

    /** Drops rows that ended up empty; a page of nothing serializes to {@code []}. */
    public void pruneEmptyRows() {
        rows.removeIf(List::isEmpty);
    }

    /** Moves a key between any two positions, rows included, for the editor's drag handler. */
    public boolean move(int fromRow, int fromIndex, int toRow, int toIndex) {
        if (fromRow < 0 || fromRow >= rows.size() || toRow < 0 || toRow >= rows.size()) return false;
        List<Key> source = rows.get(fromRow);
        if (fromIndex < 0 || fromIndex >= source.size()) return false;
        Key key = source.remove(fromIndex);
        List<Key> target = rows.get(toRow);
        int bounded = Math.max(0, Math.min(toIndex, target.size()));
        target.add(bounded, key);
        return true;
    }

    /** One line of JSON, as the properties file needs. Empty pages serialize to {@code []}. */
    @NonNull
    public String serialize() {
        JSONArray rowsJson = new JSONArray();
        for (List<Key> row : rows) {
            if (row.isEmpty()) continue;
            JSONArray rowJson = new JSONArray();
            for (Key key : row) rowJson.put(serializeKey(key));
            rowsJson.put(rowJson);
        }
        return rowsJson.toString();
    }

    @NonNull
    private static Object serializeKey(@NonNull Key key) {
        // A plain key with no display override and no popup is written as the bare string the
        // hand-written files use — the editor should not turn a readable row into object soup.
        if (!key.macro && key.display == null && key.popup == null) return key.key;
        JSONObject object = new JSONObject();
        try {
            object.put(key.macro ? "macro" : "key", key.key);
            if (key.display != null) object.put("display", key.display);
            if (key.popup != null) object.put("popup", serializeKey(key.popup));
        } catch (JSONException ignored) {
            return key.key;
        }
        return object;
    }
}
