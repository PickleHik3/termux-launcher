package com.termux.shared.termux.extrakeys;

import androidx.annotation.NonNull;

/**
 * The code points a key cap uses for an icon drawn from a drawable instead of a font glyph: one
 * table, read by this module's key maps and by the app, which registers the drawable behind each
 * one with {@code NerdFontSpans.registerDrawableSymbol}. They sit in the plane-16 private-use
 * block, outside every Nerd Font range, so nothing the symbols face draws can collide with them.
 *
 * <p>Stored key rows carry these as literal {@code display} characters once a key picker has
 * written one, so a number here is never reused for another icon.
 */
public final class ExtraKeyIcons {

    private ExtraKeyIcons() {}

    public static final int ASSISTANT = 0x10FF00;
    public static final int PAGE_SWITCHER = 0x10FF01;
    public static final int HOME = 0x10FF02;
    public static final int TERMINAL = 0x10FF03;
    public static final int DISPLAY = 0x10FF04;
    public static final int MOUSE = 0x10FF05;
    public static final int KEYBOARD = 0x10FF06;
    public static final int APPS = 0x10FF07;
    public static final int MIC = 0x10FF08;
    public static final int SPLIT = 0x10FF09;
    public static final int SESSIONS = 0x10FF0A;
    public static final int PASTE = 0x10FF0B;
    public static final int WINDOW_ADD = 0x10FF0C;
    public static final int WINDOW_CLOSE = 0x10FF0D;
    public static final int COMMAND = 0x10FF0E;
    public static final int ARROW_LEFT = 0x10FF10;
    public static final int ARROW_RIGHT = 0x10FF11;
    public static final int ARROW_UP = 0x10FF12;
    public static final int ARROW_DOWN = 0x10FF13;
    public static final int ENTER = 0x10FF14;
    public static final int TAB = 0x10FF15;
    public static final int BACKSPACE = 0x10FF16;
    public static final int DELETE_FORWARD = 0x10FF17;
    public static final int MENU = 0x10FF18;
    public static final int SCROLL = 0x10FF19;
    public static final int KEY_HOME = 0x10FF1A;
    public static final int KEY_END = 0x10FF1B;
    public static final int PAGE_UP = 0x10FF1C;
    public static final int PAGE_DOWN = 0x10FF1D;
    public static final int DOCK = 0x10FF1E;
    public static final int ZOOM_IN = 0x10FF1F;
    public static final int ZOOM_OUT = 0x10FF20;
    public static final int LINK = 0x10FF21;
    public static final int SELECT = 0x10FF22;
    public static final int SELECT_ALL = 0x10FF23;
    public static final int QUICK_SELECT = 0x10FF24;
    public static final int FIND = 0x10FF25;
    public static final int SHARE = 0x10FF26;
    public static final int RESET = 0x10FF27;
    public static final int EDIT = 0x10FF28;
    public static final int FLOAT_WINDOW = 0x10FF29;
    public static final int ADD = 0x10FF2A;
    public static final int CLONE = 0x10FF2B;
    public static final int NEXT = 0x10FF2C;
    public static final int PREVIOUS = 0x10FF2D;
    public static final int CLOSE = 0x10FF2E;
    public static final int SWITCH_INDEX = 0x10FF2F;
    public static final int LOAD = 0x10FF30;
    public static final int SAVE = 0x10FF31;
    public static final int LAYOUT = 0x10FF32;
    public static final int ROTATE = 0x10FF33;
    public static final int EQUALIZE = 0x10FF34;
    public static final int CONSUME = 0x10FF35;
    public static final int EXPEL = 0x10FF36;
    public static final int MOVE_WINDOW = 0x10FF37;
    public static final int CLIPBOARD = 0x10FF38;
    public static final int COPY = 0x10FF39;
    public static final int WALLPAPER = 0x10FF3A;
    public static final int CURSOR_TRAIL = 0x10FF3B;
    public static final int APPEARANCE = 0x10FF3C;
    public static final int FONT = 0x10FF3D;
    public static final int SETTINGS = 0x10FF3E;
    public static final int HELP = 0x10FF3F;
    public static final int MINIMAL = 0x10FF40;
    public static final int KEY_INSPECTOR = 0x10FF41;
    public static final int GO_PAGE = 0x10FF42;
    // Append only: stored rows carry these numbers.

    /** Every code point above; the app registers a drawable for each. */
    public static final int[] ALL = {
        ASSISTANT, PAGE_SWITCHER, HOME, TERMINAL, DISPLAY, MOUSE, KEYBOARD, APPS, MIC, SPLIT,
        SESSIONS, PASTE, WINDOW_ADD, WINDOW_CLOSE, COMMAND, ARROW_LEFT, ARROW_RIGHT, ARROW_UP,
        ARROW_DOWN, ENTER, TAB, BACKSPACE, DELETE_FORWARD, MENU, SCROLL, KEY_HOME, KEY_END,
        PAGE_UP, PAGE_DOWN,
        DOCK, ZOOM_IN, ZOOM_OUT, LINK, SELECT, SELECT_ALL, QUICK_SELECT, FIND, SHARE, RESET, EDIT,
        FLOAT_WINDOW, ADD, CLONE, NEXT, PREVIOUS, CLOSE, SWITCH_INDEX, LOAD, SAVE, LAYOUT, ROTATE,
        EQUALIZE, CONSUME, EXPEL, MOVE_WINDOW, CLIPBOARD, COPY, WALLPAPER, CURSOR_TRAIL, APPEARANCE,
        FONT, SETTINGS, HELP, MINIMAL, KEY_INSPECTOR, GO_PAGE,
    };

    /** The one-character label that draws {@code codePoint}'s icon. */
    @NonNull
    public static String string(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /**
     * The displays this app once shipped for a key, each with the icon that replaced it:
     * {key spec, icon, old display...}. A tool spec is {@code tool:<name>}; a plain key is its
     * name. Nerd code points are written as escapes (U+Fxxxx as surrogate pairs).
     */
    private static final Object[][] OLD_DEFAULTS = {
        {"tool:wall.widgets", HOME, "", ""},
        {"tool:wall.terminal", TERMINAL, ""},
        {"tool:wall.display", DISPLAY, ""},
        {"tool:mouse.toggle", MOUSE, "󰍽", ""},
        {"tool:keyboard.cycle_form", KEYBOARD, ""},
        {"tool:app.open_app_drawer", APPS, "󰀻"},
        {"tool:voice.dictate", MIC, "", "󰍬"},
        {"tool:pane.split", SPLIT, "󱇚"},
        {"tool:window.new", WINDOW_ADD, "󱂬"},
        {"tool:session.browser", SESSIONS, "󰉹"},
        {"tool:clipboard.paste", PASTE, "󰆒"},
        {"tool:app.command_palette", COMMAND, ""},
        {"tool:session.new", ADD, "\uDB81\uDF5C"},
        {"tool:pane.split_vertical", SPLIT, "\uDB82\uDFCC"},
        {"tool:app.open_settings", SETTINGS, "\uf013"},
        {"KEYBOARD", KEYBOARD, "󰥻", "⌨"},
        {"LEFT", ARROW_LEFT, "←"},
        {"RIGHT", ARROW_RIGHT, "→"},
        {"UP", ARROW_UP, "↑"},
        {"DOWN", ARROW_DOWN, "↓"},
        {"ENTER", ENTER, "↲"},
        {"TAB", TAB, "↹"},
        {"BKSP", BACKSPACE, "⌫"},
        {"DEL", DELETE_FORWARD, "⌦"},
        {"DRAWER", MENU, "☰"},
        {"PASTE", PASTE, "⎘"},
        {"SCROLL", SCROLL, "⇳"},
        {"HOME", KEY_HOME, "⇱"},
        {"END", KEY_END, "⇲"},
        {"PGUP", PAGE_UP, "⇑"},
        {"PGDN", PAGE_DOWN, "⇓"},
    };

    private static final java.util.HashMap<String, Integer> sOldDefaults = new java.util.HashMap<>();

    static {
        for (Object[] row : OLD_DEFAULTS)
            for (int i = 2; i < row.length; i++)
                sOldDefaults.put(row[0] + "\u0000" + row[i], (Integer) row[1]);
    }

    /**
     * A stored {@code display} as it draws now. When it is exactly what this app once shipped for
     * this same key, the key's icon; anything else, including the same glyph on another key, is
     * the user's own and comes back unchanged. Nothing stored is rewritten: this is read-time.
     *
     * @param keySpec the key's name or {@code tool:...} spec; null or a macro (several tokens, or
     *                an argument-less lookup that finds nothing) never matches.
     */
    @androidx.annotation.Nullable
    public static String effectiveDisplay(@androidx.annotation.Nullable String keySpec,
                                          @androidx.annotation.Nullable String display) {
        if (keySpec == null || display == null || display.isEmpty() || keySpec.indexOf(' ') >= 0)
            return display;
        String spec = keySpec;
        if (spec.startsWith("tool:")) {
            int arguments = spec.indexOf(':', 5);
            if (arguments > 0) spec = spec.substring(0, arguments);
        }
        Integer icon = sOldDefaults.get(spec + "\u0000" + display);
        return icon == null ? display : string(icon);
    }

    /** Whether {@code label} holds any of this table's icons, which a screen reader cannot say. */
    public static boolean containsIcon(@androidx.annotation.Nullable CharSequence label) {
        if (label == null) return false;
        int index = 0;
        while (index < label.length()) {
            int codePoint = Character.codePointAt(label, index);
            if (isIcon(codePoint)) return true;
            index += Character.charCount(codePoint);
        }
        return false;
    }

    /** Whether {@code codePoint} is one of this table's icons. */
    public static boolean isIcon(int codePoint) {
        for (int icon : ALL) if (icon == codePoint) return true;
        return false;
    }
}
