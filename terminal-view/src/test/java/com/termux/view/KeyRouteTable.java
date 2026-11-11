package com.termux.view;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

import com.termux.terminal.GhosttyKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The table of key routes the parity tests walk: every way a key reaches a session through the view
 * (hardware, extra keys, in-app keyboard code points, IME text, alternate scroll), crossed with keys,
 * modifiers and terminal state. A row knows how to drive its route on a {@link KeyRouteRig} and what
 * library event the Fast engine is expected to build for it.
 */
final class KeyRouteTable {

    private KeyRouteTable() {}

    // ---- terminal state ------------------------------------------------------------------------

    static final int[] KITTY_FLAGS = {0, 1, 3, 5, 15, 31};

    static final class State {
        final int kitty;
        final boolean decckm, deckpam;

        State(int kitty, boolean decckm, boolean deckpam) {
            this.kitty = kitty;
            this.decckm = decckm;
            this.deckpam = deckpam;
        }

        boolean modes() { return decckm || deckpam; }

        String label() {
            return "kitty=" + kitty + " decckm=" + (decckm ? "on" : "off") + " deckpam=" + (deckpam ? "on" : "off");
        }

        String escapes() {
            return (kitty != 0 ? "\u001b[>" + kitty + "u" : "") + (decckm ? "\u001b[?1h" : "") + (deckpam ? "\u001b=" : "");
        }
    }

    static List<State> states() {
        List<State> all = new ArrayList<>();
        for (int kitty : KITTY_FLAGS)
            for (int c = 0; c < 2; c++)
                for (int p = 0; p < 2; p++)
                    all.add(new State(kitty, c == 1, p == 1));
        return all;
    }

    // ---- what a route builds -----------------------------------------------------------------

    /** One library key event, as {@code TerminalEmulator#sendLibraryKey} takes it. */
    static final class Ev {
        final int key, action, mods, consumed, unshifted;
        final String text;

        Ev(int key, int action, int mods, int consumed, int unshifted, String text) {
            this.key = key;
            this.action = action;
            this.mods = mods;
            this.consumed = consumed;
            this.unshifted = unshifted;
            this.text = text;
        }
    }

    interface Step { void run(KeyRouteRig rig); }

    static final class Row {
        final String route, key, mods;
        final Step step;
        final List<Ev> expected;

        Row(String route, String key, String mods, Step step, List<Ev> expected) {
            this.route = route;
            this.key = key;
            this.mods = mods;
            this.step = step;
            this.expected = expected;
        }
    }

    // ---- keys and modifiers ------------------------------------------------------------------

    private static final class Key {
        final String name;
        final int code, ghostty;
        final int forcedMeta;
        final boolean sensitive;

        Key(String name, int code, int ghostty, int forcedMeta, boolean sensitive) {
            this.name = name;
            this.code = code;
            this.ghostty = ghostty;
            this.forcedMeta = forcedMeta;
            this.sensitive = sensitive;
        }
    }

    private static final int SHIFT_META = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
    private static final int CTRL_META = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
    private static final int ALT_META = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
    private static final int SUPER_META = KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;

    private static final Key[] KEYS = {
        new Key("a", KeyEvent.KEYCODE_A, GhosttyKeys.KEY_A, 0, false),
        new Key("A", KeyEvent.KEYCODE_A, GhosttyKeys.KEY_A, KeyEvent.META_CAPS_LOCK_ON, false),
        new Key("1", KeyEvent.KEYCODE_1, GhosttyKeys.KEY_DIGIT_1, 0, false),
        new Key("!", KeyEvent.KEYCODE_1, GhosttyKeys.KEY_DIGIT_1, SHIFT_META, false),
        new Key("space", KeyEvent.KEYCODE_SPACE, GhosttyKeys.KEY_SPACE, 0, false),
        new Key("Enter", KeyEvent.KEYCODE_ENTER, GhosttyKeys.KEY_ENTER, 0, false),
        new Key("Tab", KeyEvent.KEYCODE_TAB, GhosttyKeys.KEY_TAB, 0, false),
        new Key("Escape", KeyEvent.KEYCODE_ESCAPE, GhosttyKeys.KEY_ESCAPE, 0, false),
        new Key("Backspace", KeyEvent.KEYCODE_DEL, GhosttyKeys.KEY_BACKSPACE, 0, false),
        new Key("Delete", KeyEvent.KEYCODE_FORWARD_DEL, GhosttyKeys.KEY_DELETE, 0, false),
        new Key("Up", KeyEvent.KEYCODE_DPAD_UP, GhosttyKeys.KEY_ARROW_UP, 0, true),
        new Key("Down", KeyEvent.KEYCODE_DPAD_DOWN, GhosttyKeys.KEY_ARROW_DOWN, 0, true),
        new Key("Left", KeyEvent.KEYCODE_DPAD_LEFT, GhosttyKeys.KEY_ARROW_LEFT, 0, true),
        new Key("Right", KeyEvent.KEYCODE_DPAD_RIGHT, GhosttyKeys.KEY_ARROW_RIGHT, 0, true),
        new Key("Home", KeyEvent.KEYCODE_MOVE_HOME, GhosttyKeys.KEY_HOME, 0, true),
        new Key("End", KeyEvent.KEYCODE_MOVE_END, GhosttyKeys.KEY_END, 0, true),
        new Key("PageUp", KeyEvent.KEYCODE_PAGE_UP, GhosttyKeys.KEY_PAGE_UP, 0, false),
        new Key("F1", KeyEvent.KEYCODE_F1, GhosttyKeys.KEY_F1, 0, false),
        new Key("F5", KeyEvent.KEYCODE_F5, GhosttyKeys.KEY_F5, 0, false),
        new Key("F12", KeyEvent.KEYCODE_F12, GhosttyKeys.KEY_F12, 0, false),
        new Key("numpad 5", KeyEvent.KEYCODE_NUMPAD_5, GhosttyKeys.KEY_NUMPAD_5, 0, true),
        new Key("numpad Enter", KeyEvent.KEYCODE_NUMPAD_ENTER, GhosttyKeys.KEY_NUMPAD_ENTER, 0, true),
        new Key("/", KeyEvent.KEYCODE_SLASH, GhosttyKeys.KEY_SLASH, 0, false),
        new Key("[", KeyEvent.KEYCODE_LEFT_BRACKET, GhosttyKeys.KEY_BRACKET_LEFT, 0, false),
    };

    private static Key key(String name) {
        for (Key k : KEYS) if (k.name.equals(name)) return k;
        throw new IllegalArgumentException(name);
    }

    /** A modifier set: the meta bits on the event, the sticky extra-keys modifiers, and what the library should see. */
    private static final class Mod {
        final String name;
        final int meta;
        final boolean stickyCtrl, stickyAlt;
        final int ghostty;

        Mod(String name, int meta, boolean stickyCtrl, boolean stickyAlt, int ghostty) {
            this.name = name;
            this.meta = meta;
            this.stickyCtrl = stickyCtrl;
            this.stickyAlt = stickyAlt;
            this.ghostty = ghostty;
        }

        boolean shift() { return (ghostty & GhosttyKeys.MOD_SHIFT) != 0; }
    }

    private static final Mod NONE = new Mod("none", 0, false, false, 0);
    private static final Mod SHIFT = new Mod("shift", SHIFT_META, false, false, GhosttyKeys.MOD_SHIFT);
    private static final Mod CTRL = new Mod("ctrl", CTRL_META, false, false, GhosttyKeys.MOD_CTRL);
    private static final Mod ALT = new Mod("alt", ALT_META, false, false, GhosttyKeys.MOD_ALT);
    private static final Mod CTRL_SHIFT = new Mod("ctrl+shift", CTRL_META | SHIFT_META, false, false,
        GhosttyKeys.MOD_CTRL | GhosttyKeys.MOD_SHIFT);
    private static final Mod CTRL_ALT = new Mod("ctrl+alt", CTRL_META | ALT_META, false, false,
        GhosttyKeys.MOD_CTRL | GhosttyKeys.MOD_ALT);
    private static final Mod SUPER = new Mod("super", SUPER_META, false, false, GhosttyKeys.MOD_SUPER);
    private static final Mod STICKY_CTRL = new Mod("sticky ctrl", 0, true, false, GhosttyKeys.MOD_CTRL);
    private static final Mod STICKY_ALT = new Mod("sticky alt", 0, false, true, GhosttyKeys.MOD_ALT);

    private static final Mod[] HARDWARE_MODS = {NONE, SHIFT, CTRL, ALT, CTRL_SHIFT, CTRL_ALT, SUPER, STICKY_CTRL, STICKY_ALT};
    private static final Mod[] EXTRA_KEY_MODS = {NONE, SHIFT, CTRL, ALT, CTRL_SHIFT, CTRL_ALT};
    private static final Mod[] SMALL_MODS = {NONE, CTRL, SHIFT};

    // ---- the rows ------------------------------------------------------------------------------

    static List<Row> rows(State state) {
        List<Row> rows = new ArrayList<>();
        boolean modes = state.modes();
        for (Key k : KEYS) {
            if (modes && !k.sensitive) continue;
            for (Mod m : HARDWARE_MODS) rows.add(hardware(k, m));
            if (isRepeatKey(k)) for (Mod m : SMALL_MODS) rows.add(repeat(k, m));
            if (isReleaseKey(k)) for (Mod m : SMALL_MODS) rows.add(release(k, m));
            if (isExtraKey(k)) for (Mod m : EXTRA_KEY_MODS) rows.add(extraKey(k, m));
            if (isScrollKey(k)) for (Mod m : EXTRA_KEY_MODS) rows.add(handleKeyCode(k, m));
        }
        if (!modes) {
            for (int[] cp : CODE_POINTS) {
                for (int source = 0; source < 2; source++)
                    for (Mod m : new Mod[] {NONE, CTRL, ALT, CTRL_ALT, STICKY_CTRL, STICKY_ALT})
                        rows.add(codePoint(cp, source == 0 ? TerminalView.KEY_EVENT_SOURCE_SOFT_KEYBOARD
                            : TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, m));
            }
            for (String text : new String[] {"a", "A", "\n", "\t", "é"})
                for (Mod m : new Mod[] {NONE, STICKY_CTRL, STICKY_ALT, new Mod("sticky shift", 0, false, false, 0)}) {
                    if (m.name.equals("sticky shift") && !Character.isLetter(text.charAt(0)) || text.equals("é") && m.name.equals("sticky shift"))
                        continue;
                    rows.add(commit(text, m));
                }
            for (String text : new String[] {"x", "é", "\n", "\t"})
                rows.add(multiple(text));
        }
        return rows;
    }

    private static boolean isRepeatKey(Key k) {
        return k.name.equals("a") || k.name.equals("Enter") || k.name.equals("Up") || k.name.equals("F5");
    }

    private static boolean isReleaseKey(Key k) {
        return isRepeatKey(k) || k.name.equals("Escape");
    }

    private static boolean isExtraKey(Key k) {
        switch (k.name) {
            case "Enter": case "Tab": case "Escape": case "Backspace": case "Delete": case "Up": case "Down":
            case "Left": case "Right": case "Home": case "End": case "PageUp": case "F1": case "F5": case "F12":
                return true;
            default:
                return false;
        }
    }

    private static boolean isScrollKey(Key k) {
        switch (k.name) {
            case "Up": case "Down": case "Left": case "Right": case "Home": case "End": case "PageUp": case "F1":
                return true;
            default:
                return false;
        }
    }

    // ---- hardware ------------------------------------------------------------------------------

    /** What the layout types for {@code keyCode} under {@code meta}: 0 for nothing, a C0 or DEL character, or a dead key. */
    private static int typed(KeyEvent event, int meta) {
        int c = event.getUnicodeChar(meta);
        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) return 0;
        return c < 0x20 || c == 0x7f ? 0 : c;
    }

    private static String str(int cp) {
        return cp == 0 ? "" : new String(Character.toChars(cp));
    }

    private static KeyEvent hwEvent(int action, Key k, int meta, int repeat) {
        return KeyRouteRig.event(action, k.code, repeat, meta);
    }

    /** The event the Fast route builds from a key event, written from the spec rather than from the route's code. */
    private static Ev expectedFromEvent(KeyEvent event, Key k, int action, int mods, boolean shiftHeld) {
        int unshifted = typed(event, 0);
        int effective = event.getMetaState() & ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
        if (shiftHeld) effective |= SHIFT_META;
        int text = typed(event, effective);
        int consumed = shiftHeld && text != unshifted ? GhosttyKeys.MOD_SHIFT : 0;
        return new Ev(k.ghostty, action, mods, consumed, unshifted, str(text));
    }

    private static int capsBits(Key k) {
        return (k.forcedMeta & KeyEvent.META_CAPS_LOCK_ON) != 0 ? GhosttyKeys.MOD_CAPS_LOCK : 0;
    }

    private static Row hardware(Key k, Mod m) {
        int meta = m.meta | k.forcedMeta;
        KeyEvent event = hwEvent(KeyEvent.ACTION_DOWN, k, meta, 0);
        boolean shiftHeld = (meta & SHIFT_META) != 0;
        int mods = m.ghostty | capsBits(k) | ((k.forcedMeta & SHIFT_META) != 0 ? GhosttyKeys.MOD_SHIFT : 0);
        Step step = rig -> {
            rig.sticky(m.stickyCtrl, m.stickyAlt, false);
            rig.view.onKeyDown(k.code, hwEvent(KeyEvent.ACTION_DOWN, k, meta, 0));
            rig.sticky(false, false, false);
        };
        return new Row("hardware key", k.name, m.name, step,
            list(expectedFromEvent(event, k, GhosttyKeys.ACTION_PRESS, mods, shiftHeld)));
    }

    private static Row repeat(Key k, Mod m) {
        int meta = m.meta | k.forcedMeta;
        KeyEvent event = hwEvent(KeyEvent.ACTION_DOWN, k, meta, 1);
        boolean shiftHeld = (meta & SHIFT_META) != 0;
        int mods = m.ghostty | capsBits(k) | ((k.forcedMeta & SHIFT_META) != 0 ? GhosttyKeys.MOD_SHIFT : 0);
        Step step = rig -> rig.view.onKeyDown(k.code, hwEvent(KeyEvent.ACTION_DOWN, k, meta, 1));
        return new Row("hardware key repeat", k.name, m.name, step,
            list(expectedFromEvent(event, k, GhosttyKeys.ACTION_REPEAT, mods, shiftHeld)));
    }

    private static Row release(Key k, Mod m) {
        int meta = m.meta | k.forcedMeta;
        int mods = m.ghostty | capsBits(k) | ((k.forcedMeta & SHIFT_META) != 0 ? GhosttyKeys.MOD_SHIFT : 0);
        KeyEvent event = hwEvent(KeyEvent.ACTION_UP, k, meta, 0);
        Step step = rig -> {
            rig.view.onKeyDown(k.code, hwEvent(KeyEvent.ACTION_DOWN, k, meta, 0));
            rig.drain();
            rig.view.onKeyUp(k.code, hwEvent(KeyEvent.ACTION_UP, k, meta, 0));
        };
        return new Row("hardware key release", k.name, m.name, step,
            list(new Ev(k.ghostty, GhosttyKeys.ACTION_RELEASE, mods, 0, typed(event, 0), "")));
    }

    // ---- extra keys ----------------------------------------------------------------------------

    private static KeyEvent extraEvent(Key k, int meta) {
        // As TerminalExtraKeys builds it: an ACTION_UP-typed event, no device, the sticky state as meta.
        return new KeyEvent(0, 0, KeyEvent.ACTION_UP, k.code, 0, meta);
    }

    private static Row extraKey(Key k, Mod m) {
        KeyEvent event = extraEvent(k, m.meta);
        Step step = rig -> rig.view.onKeyDown(k.code, extraEvent(k, m.meta));
        return new Row("extra-keys button", k.name, m.name, step,
            list(expectedFromEvent(event, k, GhosttyKeys.ACTION_PRESS, m.ghostty, m.shift())));
    }

    // ---- alternate scroll and other handleKeyCode callers --------------------------------------

    private static Row handleKeyCode(Key k, Mod m) {
        int keyMod = (m.ghostty & GhosttyKeys.MOD_CTRL) != 0 ? com.termux.terminal.KeyHandler.KEYMOD_CTRL : 0;
        if ((m.ghostty & GhosttyKeys.MOD_ALT) != 0) keyMod |= com.termux.terminal.KeyHandler.KEYMOD_ALT;
        if ((m.ghostty & GhosttyKeys.MOD_SHIFT) != 0) keyMod |= com.termux.terminal.KeyHandler.KEYMOD_SHIFT;
        final int mod = keyMod;
        Step step = rig -> rig.view.handleKeyCode(k.code, mod);
        // Shift+PageUp scrolls the scrollback instead of typing, on both engines.
        boolean scrolls = k.name.equals("PageUp") && m.shift();
        return new Row("handleKeyCode", k.name, m.name, step,
            scrolls ? new ArrayList<>() : list(new Ev(k.ghostty, GhosttyKeys.ACTION_PRESS, m.ghostty, 0, 0, "")));
    }

    // ---- code points (in-app keyboard, soft keyboard source) -----------------------------------

    /** code point, library key, unshifted, shift needed. */
    private static final int[][] CODE_POINTS = {
        {'a', GhosttyKeys.KEY_A, 'a', 0},
        {'A', GhosttyKeys.KEY_A, 'a', 1},
        {'1', GhosttyKeys.KEY_DIGIT_1, '1', 0},
        {'!', GhosttyKeys.KEY_DIGIT_1, '1', 1},
        {' ', GhosttyKeys.KEY_SPACE, ' ', 0},
        {13, GhosttyKeys.KEY_ENTER, 0, 0},
        {9, GhosttyKeys.KEY_TAB, 0, 0},
        {27, GhosttyKeys.KEY_ESCAPE, 0, 0},
        {127, GhosttyKeys.KEY_BACKSPACE, 0, 0},
        {8, GhosttyKeys.KEY_BACKSPACE, 0, 0},
        {'/', GhosttyKeys.KEY_SLASH, '/', 0},
        {'[', GhosttyKeys.KEY_BRACKET_LEFT, '[', 0},
        {0xe9, GhosttyKeys.KEY_UNIDENTIFIED, 0xe9, 0},
    };

    private static String cpName(int cp) {
        if (cp < 0x20 || cp == 0x7f) return "code point " + cp;
        return "'" + new String(Character.toChars(cp)) + "'";
    }

    private static Ev codePointEvent(int[] cp, Mod m) {
        boolean c0 = cp[0] < 0x20 || cp[0] == 0x7f;
        int shift = cp[3] == 1 ? GhosttyKeys.MOD_SHIFT : 0;
        return new Ev(cp[1], GhosttyKeys.ACTION_PRESS, m.ghostty | shift, shift, cp[2], c0 ? "" : str(cp[0]));
    }

    private static Row codePoint(int[] cp, int source, Mod m) {
        Step step = rig -> {
            rig.sticky(m.stickyCtrl, m.stickyAlt, false);
            rig.view.inputCodePoint(source, cp[0], m == CTRL || m == CTRL_ALT, m == ALT || m == CTRL_ALT);
            rig.sticky(false, false, false);
        };
        String route = source == TerminalView.KEY_EVENT_SOURCE_SOFT_KEYBOARD ? "inputCodePoint soft keyboard"
            : "inputCodePoint in-app keyboard";
        return new Row(route, cpName(cp[0]), m.name, step, list(codePointEvent(cp, m)));
    }

    // ---- IME text and raw characters -------------------------------------------------------------

    private static int[] codePointRow(int cp) {
        for (int[] row : CODE_POINTS) if (row[0] == cp) return row;
        throw new IllegalArgumentException("" + cp);
    }

    private static Row commit(String text, Mod m) {
        boolean stickyShift = m.name.equals("sticky shift");
        Step step = rig -> {
            rig.sticky(m.stickyCtrl, m.stickyAlt, stickyShift);
            InputConnection connection = rig.inputConnection();
            connection.commitText(text, 1);
            rig.sticky(false, false, false);
        };
        int cp = text.codePointAt(0);
        if (stickyShift) cp = Character.toUpperCase(cp);
        if (cp == '\n') cp = 13;
        return new Row("IME commitText", describe(text), m.name, step, list(codePointEvent(codePointRow(cp), m)));
    }

    private static Row multiple(String text) {
        Step step = rig -> rig.view.onKeyDown(KeyEvent.KEYCODE_UNKNOWN, new KeyEvent(0L, text, 0, 0));
        int cp = text.codePointAt(0);
        if (cp == '\n') cp = 13;
        int[] r = cp == 'x' ? new int[] {'x', GhosttyKeys.KEY_A + ('x' - 'a'), 'x', 0} : codePointRow(cp);
        return new Row("ACTION_MULTIPLE characters", describe(text), "none", step, list(codePointEvent(r, NONE)));
    }

    private static String describe(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) out.append(c == '\n' ? "\\n" : c == '\t' ? "\\t" : String.valueOf(c));
        return out.append('"').toString();
    }

    private static List<Ev> list(Ev ev) {
        List<Ev> l = new ArrayList<>();
        l.add(ev);
        return l;
    }

    // ---- running the table -----------------------------------------------------------------------

    /** One row's outcome. */
    static final class Result {
        final String route, key, mods, state, bytes;

        Result(String route, String key, String mods, String state, String bytes) {
            this.route = route;
            this.key = key;
            this.mods = mods;
            this.state = state;
            this.bytes = bytes;
        }

        /** The row as the golden file keeps it. */
        String line() {
            return id() + " | " + printable(bytes);
        }

        String id() {
            return route + " | " + key + " | " + mods + " | " + state;
        }
    }

    /** Every row through the real view entry points, on an engine of the given kind. */
    static List<Result> run(boolean fast, String inputProperty) {
        List<Result> results = new ArrayList<>();
        for (State state : states()) {
            KeyRouteRig rig = new KeyRouteRig(fast, inputProperty);
            rig.feed(state.escapes());
            rig.drain();
            for (Row row : rows(state)) {
                rig.drain();
                row.step.run(rig);
                results.add(new Result(row.route, row.key, row.mods, state.label(), rig.drain()));
            }
        }
        return results;
    }

    /**
     * What the library produces for the events each row is expected to build: a second Fast emulator in the
     * same state, sendLibraryKey called directly.
     */
    static List<Result> reference() {
        List<Result> results = new ArrayList<>();
        for (State state : states()) {
            KeyRouteRig ref = new KeyRouteRig(true, null);
            ref.feed(state.escapes());
            ref.drain();
            for (Row row : rows(state)) {
                ref.drain();
                for (Ev ev : row.expected)
                    ref.emulator.sendLibraryKey(ev.key, ev.action, ev.mods, ev.consumed, ev.unshifted, ev.text, false);
                results.add(new Result(row.route, row.key, row.mods, state.label(), ref.drain()));
            }
        }
        return results;
    }

    // ---- bytes, printably ----------------------------------------------------------------------

    /** Bytes (one char each) in the notation of the differences note: ESC as \e, other controls as ^X or \xNN. */
    static String printable(String bytes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length(); i++) {
            char c = bytes.charAt(i);
            if (c == 0x1b) out.append("\\e");
            else if (c == 0x7f) out.append("\\x7f");
            else if (c == 0) out.append("^@");
            else if (c < 0x20) out.append('^').append((char) (c + 64));
            else if (c == '\\') out.append("\\\\");
            else if (c == '^') out.append("\\^");
            else if (c == '|') out.append("\\|");
            else if (c > 0x7e) out.append(String.format("\\x%02x", (int) c));
            else out.append(c);
        }
        return out.toString();
    }
}
