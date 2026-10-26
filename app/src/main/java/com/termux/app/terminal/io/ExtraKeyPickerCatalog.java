package com.termux.app.terminal.io;

import androidx.annotation.NonNull;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The plain keys the "Choose a key" sheet lists, in the order of its category chips. App actions
 * come from the launcher's tool registry and are grouped by the picker itself.
 */
public final class ExtraKeyPickerCatalog {

    public enum Category { COMMON, NAVIGATION, SYMBOLS, OTHER, ACTIONS }

    private static final String[] COMMON = {
        "ESC", "TAB", "CTRL", "ALT", "SHIFT", "ENTER", "BKSP", "DEL", "FN", "SPACE"};
    private static final String[] NAVIGATION = {
        "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN", "INS"};
    /** Typed as text, so every one of them is also what a literal search would offer. */
    private static final String[] SYMBOLS = {
        "-", "_", "/", "|", "~", "\\", "`", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "=", "+", "[", "]", "{", "}", ";", ":", "'", "\"", ",", ".", "<", ">", "?"};

    private ExtraKeyPickerCatalog() {}

    /** The keys of a plain category; {@link Category#ACTIONS} is the registry's and is empty here. */
    @NonNull
    public static List<String> keys(@NonNull Category category) {
        switch (category) {
            case COMMON: return Collections.unmodifiableList(java.util.Arrays.asList(COMMON));
            case NAVIGATION: return Collections.unmodifiableList(java.util.Arrays.asList(NAVIGATION));
            case SYMBOLS: return Collections.unmodifiableList(java.util.Arrays.asList(SYMBOLS));
            case OTHER: return other();
            default: return Collections.emptyList();
        }
    }

    /** F1 to F12 in numeric order, then the keys the row handles itself. */
    @NonNull
    private static List<String> other() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 12; i++) keys.add("F" + i);
        Collections.addAll(keys, ExtraKeysPresets.ROW_KEYS);
        return keys;
    }

    /** Every plain key across the categories, each once, for a search to filter. */
    @NonNull
    public static List<String> allKeys() {
        List<String> all = new ArrayList<>();
        for (Category category : new Category[]{
            Category.COMMON, Category.NAVIGATION, Category.SYMBOLS, Category.OTHER}) {
            for (String key : keys(category)) if (!all.contains(key)) all.add(key);
        }
        // Anything the terminal accepts that the lists above did not name still has to be findable.
        List<String> rest = new ArrayList<>();
        for (String key : ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.keySet()) {
            if (!all.contains(key)) rest.add(key);
        }
        Collections.sort(rest);
        all.addAll(rest);
        return all;
    }

    /** Plain keys whose name contains the text, ignoring case; an empty text matches all. */
    @NonNull
    public static List<String> search(@NonNull String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String key : allKeys()) {
            if (needle.isEmpty() || key.toLowerCase(Locale.ROOT).contains(needle)) found.add(key);
        }
        return found;
    }
}
