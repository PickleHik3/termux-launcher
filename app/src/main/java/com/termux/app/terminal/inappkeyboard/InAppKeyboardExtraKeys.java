package com.termux.app.terminal.inappkeyboard;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;

/**
 * Static catalog and placement policy for the in-app keyboard's optional extra keys, ported
 * from Unexpected-Keyboard's {@code ExtraKeysPreference} (static logic only, terminal-trimmed).
 *
 * <p>Enabled keys are merged into the layout by {@code LayoutModifier.modify}: a {@code loc}
 * slot declared in the layout XML is kept in place when its key is enabled, otherwise the key
 * is added at the position returned by {@link #keyPreferredPos}.
 */
public final class InAppKeyboardExtraKeys {

    private InAppKeyboardExtraKeys() {}

    /** Key names selectable by the user, in settings-display order. */
    private static final String[] EXTRA_KEYS = {
        "tab",
        "esc",
        "capslock",
        "compose",
        "home",
        "end",
        "page_up",
        "page_down",
        "copy",
        "paste",
        "cut",
        "selectAll",
        "undo",
        "redo",
        "delete_word",
        "forward_delete_word",
        "shareText",
        "pasteAsPlainText",
        "switch_greekmath",
        "meta",
        "alt",
        "superscript",
        "subscript",
        "f11_placeholder",
        "f12_placeholder",
        "menu",
        "scroll_lock",
        "€",
        "ß",
        "£",
        "§",
        "†",
        "ª",
        "º",
        "accent_aigu",
        "accent_grave",
        "accent_circonflexe",
        "accent_tilde",
        "accent_cedille",
        "accent_trema",
        "accent_ring",
        "accent_caron",
        "accent_macron",
        "accent_ogonek",
        "accent_breve",
        "accent_dot_above",
        "accent_double_aigu",
        "accent_slash",
        "accent_bar",
    };

    /** The selectable key names in canonical (display) order. */
    public static String[] catalog() {
        return EXTRA_KEYS.clone();
    }

    /**
     * Whether a key is enabled when the user never chose a selection. A terminal-first
     * selection: the navigation keys the terminal's extra-keys bar already covers stay off,
     * while the clipboard editing keys are on out of the box.
     */
    public static boolean defaultEnabled(String name) {
        switch (name) {
            case "tab":
            case "esc":
            case "capslock":
            case "copy":
            case "paste":
            case "cut":
            case "alt":
                return true;
            default:
                return false;
        }
    }

    /** Comma-joined default-enabled names, the resolution of the "never chose" sentinel. */
    public static String defaultStoredValue() {
        StringBuilder result = new StringBuilder();
        for (String name : EXTRA_KEYS) {
            if (!defaultEnabled(name)) continue;
            if (result.length() > 0) result.append(',');
            result.append(name);
        }
        return result.toString();
    }

    /** Label shown for a key in settings. Glyph names render via the keyboard's special font. */
    public static String displayName(String name) {
        switch (name) {
            case "f11_placeholder": return "F11";
            case "f12_placeholder": return "F12";
        }
        KeyValue kv = KeyValue.getKeyByName(name);
        String label = kv == null ? null : kv.getString();
        return label == null || label.isEmpty() ? name : label;
    }

    /**
     * Maps the
     * {@link TermuxPreferenceConstants.TERMUX_APP#DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS} sentinel
     * (and {@code null}) to {@link #defaultStoredValue()}; explicit selections — including the
     * empty "none enabled" string — pass through unchanged.
     */
    public static String effectiveStoredValue(String storedCsv) {
        if (storedCsv == null
            || TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS
                .equals(storedCsv))
            return defaultStoredValue();
        return storedCsv;
    }

    /**
     * Parses the stored comma-joined list of enabled key names into the map consumed by
     * {@code LayoutModifier.LayoutOptions}. The never-chose sentinel (and {@code null})
     * resolve to the defaults; an empty string means "none enabled".
     */
    public static Map<KeyValue, KeyboardData.PreferredPos> resolve(String storedCsv) {
        storedCsv = effectiveStoredValue(storedCsv);
        Map<KeyValue, KeyboardData.PreferredPos> result =
            new HashMap<KeyValue, KeyboardData.PreferredPos>();
        if (storedCsv.isEmpty())
            return result;
        Set<String> known = new HashSet<>(Arrays.asList(EXTRA_KEYS));
        for (String name : storedCsv.split(",")) {
            if (!known.contains(name)) continue;
            KeyValue kv = KeyValue.getKeyByName(name);
            if (kv != null)
                result.put(kv, keyPreferredPos(name));
        }
        return result;
    }

    /** Place an extra key next to the key specified by the first argument, on
        bottom-right preferably or on the bottom-left. If the specified key is not
        on the layout, place on the specified row and column. */
    static KeyboardData.PreferredPos mkPreferredPos(String nextToKey, int row, int col,
                                                    boolean preferBottomRight) {
        KeyValue nextTo = (nextToKey == null) ? null : KeyValue.getKeyByName(nextToKey);
        int d1, d2; // Preferred direction and fallback direction
        if (preferBottomRight) { d1 = 4; d2 = 3; } else { d1 = 3; d2 = 4; }
        return new KeyboardData.PreferredPos(nextTo,
            new KeyboardData.KeyPos[]{
                new KeyboardData.KeyPos(row, col, d1),
                new KeyboardData.KeyPos(row, col, d2),
                new KeyboardData.KeyPos(row, -1, d1),
                new KeyboardData.KeyPos(row, -1, d2),
                new KeyboardData.KeyPos(-1, -1, -1),
            });
    }

    /** Preferred layout position for an extra key that is not already on the layout. */
    public static KeyboardData.PreferredPos keyPreferredPos(String keyName) {
        switch (keyName) {
            case "cut": return mkPreferredPos("x", 2, 2, true);
            case "copy": return mkPreferredPos("c", 2, 3, true);
            case "paste": return mkPreferredPos("v", 2, 4, true);
            case "undo": return mkPreferredPos("z", 2, 1, true);
            case "selectAll": return mkPreferredPos("a", 1, 0, true);
            case "redo": return mkPreferredPos("y", 0, 5, true);
            case "f11_placeholder": return mkPreferredPos("9", 0, 8, false);
            case "f12_placeholder": return mkPreferredPos("0", 0, 9, false);
            case "delete_word": return mkPreferredPos("backspace", -1, -1, false);
            case "forward_delete_word": return mkPreferredPos("backspace", -1, -1, true);
        }
        return KeyboardData.PreferredPos.DEFAULT;
    }
}
