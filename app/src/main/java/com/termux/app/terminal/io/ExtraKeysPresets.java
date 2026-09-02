package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The vocabulary the extra-keys editor offers without a search: whole-row presets and the keys
 * worth one tap each.
 *
 * <p>Kept apart from the views so the lists can be tested and so the action picker and the editor
 * page agree on which names exist. {@code CTRL}, {@code ALT}, {@code SHIFT} and {@code FN} are the
 * row's modifier toggles and live outside
 * {@link com.termux.shared.termux.extrakeys.ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS}, which
 * is why the search-only picker used to have no way to offer them (issue #22).
 */
public final class ExtraKeysPresets {

    /** One preset row set: a name resource and the property value it stands for. */
    public static final class Preset {
        public final int titleRes;
        @NonNull public final String pageValue;

        Preset(int titleRes, @NonNull String pageValue) {
            this.titleRes = titleRes;
            this.pageValue = pageValue;
        }

        @NonNull
        public ExtraKeysLayoutModel model() {
            return ExtraKeysLayoutModel.parse(pageValue);
        }
    }

    /** One quick-add key: the name the row understands and the label the chip shows. */
    public static final class QuickKey {
        @NonNull public final String key;
        @NonNull public final String label;

        QuickKey(@NonNull String key, @NonNull String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** Upstream Termux's default row, the one issue #22 asked for. */
    public static final String CLASSIC_TERMUX =
        "[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]";

    /** Upstream's documented two-row layout: navigation on top, modifiers and arrows below. */
    public static final String TWO_ROWS =
        "[['ESC','/','-','HOME','UP','END','PGUP'],"
            + "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";

    public static final String[] MODIFIER_KEYS = {"CTRL", "ALT", "SHIFT", "FN"};

    /** Keys the row handles itself rather than sending to the terminal. */
    public static final String[] ROW_KEYS = {"KEYBOARD", "PASTE", "SCROLL"};

    private static final List<QuickKey> QUICK_KEYS = Collections.unmodifiableList(Arrays.asList(
        new QuickKey("CTRL", "CTRL"),
        new QuickKey("ALT", "ALT"),
        new QuickKey("SHIFT", "SHIFT"),
        new QuickKey("FN", "FN"),
        new QuickKey("ESC", "ESC"),
        new QuickKey("TAB", "TAB"),
        new QuickKey("ENTER", "ENTER"),
        new QuickKey("BKSP", "BKSP"),
        new QuickKey("DEL", "DEL"),
        new QuickKey("UP", "↑"),
        new QuickKey("DOWN", "↓"),
        new QuickKey("LEFT", "←"),
        new QuickKey("RIGHT", "→"),
        new QuickKey("HOME", "HOME"),
        new QuickKey("END", "END"),
        new QuickKey("PGUP", "PGUP"),
        new QuickKey("PGDN", "PGDN"),
        new QuickKey("-", "-"),
        new QuickKey("_", "_"),
        new QuickKey("/", "/"),
        new QuickKey("|", "|"),
        new QuickKey("~", "~"),
        new QuickKey("KEYBOARD", "⌨")));

    private ExtraKeysPresets() {}

    @NonNull
    public static List<QuickKey> quickKeys() {
        return QUICK_KEYS;
    }

    /**
     * The presets offered for a page. "Launcher default" is the shipped value of that page, so on
     * page two it is the empty page the launcher ships with.
     */
    @NonNull
    public static List<Preset> presetsForPage(int page) {
        String shipped = page == 0
            ? TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS
            : TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2;
        return Arrays.asList(
            new Preset(com.termux.R.string.settings_extra_keys_preset_launcher, shipped),
            new Preset(com.termux.R.string.settings_extra_keys_preset_classic, CLASSIC_TERMUX),
            new Preset(com.termux.R.string.settings_extra_keys_preset_two_rows, TWO_ROWS),
            new Preset(com.termux.R.string.settings_extra_keys_preset_empty, "[]"));
    }

    public static boolean isModifier(@Nullable String key) {
        return contains(MODIFIER_KEYS, key);
    }

    public static boolean isRowKey(@Nullable String key) {
        return contains(ROW_KEYS, key);
    }

    private static boolean contains(@NonNull String[] names, @Nullable String key) {
        if (key == null) return false;
        for (String name : names) if (name.equals(key)) return true;
        return false;
    }
}
