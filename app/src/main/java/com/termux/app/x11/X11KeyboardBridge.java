package com.termux.app.x11;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.x11.LorieView;

import java.nio.charset.StandardCharsets;

import juloo.keyboard2.KeyValue;

/**
 * Types from the launcher's own keyboard into X.
 *
 * <p>The in-app keyboard speaks {@link KeyValue}, so while the Display page is showing it is
 * intercepted here instead of reaching the terminal — the same seam the command palette uses.
 * Characters and strings go over the server's text path, which handles anything the keyboard can
 * produce including emoji; keys that have no character go over the key path with their Android
 * keycode, which is what the server's own XKB mapping expects.
 *
 * <p>The extra-keys row keeps working because it emits the same values, so Esc, Tab, Ctrl and the
 * arrows reach X without a second mapping table.
 */
public final class X11KeyboardBridge implements TerminalKeyEventHandler.KeyValueInterceptor {

    @NonNull private final Supplier display;

    /** Where the live view comes from; it changes as the page attaches and detaches. */
    public interface Supplier {
        @Nullable LorieView displayView();
    }

    public X11KeyboardBridge(@NonNull Supplier display) {
        this.display = display;
    }

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        LorieView view = display.displayView();
        if (view == null || !view.connected()) return false;
        switch (value.getKind()) {
            case Char:
                // With a modifier held the character is not the point — the keycode is, so X can
                // build Ctrl+C rather than receiving the control character itself.
                if (ctrl || alt) return sendModified(view, keyCodeForChar(value.getChar()), ctrl,
                    alt, shift);
                sendText(view, String.valueOf(value.getChar()));
                return true;
            case String:
                if (ctrl || alt) return true;
                sendText(view, value.getString());
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR:
                        return sendModified(view, KeyEvent.KEYCODE_SPACE, ctrl, alt, shift);
                    case BACKSPACE:
                        return sendModified(view, KeyEvent.KEYCODE_DEL, ctrl, alt, shift);
                    case DELETE_WORD:
                        return sendModified(view, KeyEvent.KEYCODE_DEL, true, alt, shift);
                    case FORWARD_DELETE_WORD:
                        return sendModified(view, KeyEvent.KEYCODE_FORWARD_DEL, true, alt, shift);
                    default:
                        return true;
                }
            case Keyevent:
                return sendModified(view, value.getKeyevent(), ctrl, alt, shift);
            case Modifier:
                // The keyboard tracks its own modifier state and tells us on the next value; a
                // bare modifier press has nothing to send.
                return true;
            default:
                return true;
        }
    }

    private void sendText(@NonNull LorieView view, @Nullable String text) {
        if (text == null || text.isEmpty()) return;
        view.sendTextEvent(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Press and release {@code keyCode} inside whatever modifiers are held. The modifiers are
     * pressed and released around it so the server sees a complete chord and is never left with
     * a stuck Ctrl when the page goes away mid-stroke.
     */
    private boolean sendModified(@NonNull LorieView view, int keyCode, boolean ctrl, boolean alt,
                                 boolean shift) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return true;
        if (ctrl) view.sendKeyEvent(0, KeyEvent.KEYCODE_CTRL_LEFT, true);
        if (alt) view.sendKeyEvent(0, KeyEvent.KEYCODE_ALT_LEFT, true);
        if (shift) view.sendKeyEvent(0, KeyEvent.KEYCODE_SHIFT_LEFT, true);
        view.sendKeyEvent(0, keyCode, true);
        view.sendKeyEvent(0, keyCode, false);
        if (shift) view.sendKeyEvent(0, KeyEvent.KEYCODE_SHIFT_LEFT, false);
        if (alt) view.sendKeyEvent(0, KeyEvent.KEYCODE_ALT_LEFT, false);
        if (ctrl) view.sendKeyEvent(0, KeyEvent.KEYCODE_CTRL_LEFT, false);
        return true;
    }

    /** The keycode a character sits on, for the chords that need one. */
    private static int keyCodeForChar(char c) {
        if (c >= 'a' && c <= 'z') return KeyEvent.KEYCODE_A + (c - 'a');
        if (c >= 'A' && c <= 'Z') return KeyEvent.KEYCODE_A + (c - 'A');
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        switch (c) {
            case ' ': return KeyEvent.KEYCODE_SPACE;
            case '\t': return KeyEvent.KEYCODE_TAB;
            case '\n':
            case '\r': return KeyEvent.KEYCODE_ENTER;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case ',': return KeyEvent.KEYCODE_COMMA;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case '=': return KeyEvent.KEYCODE_EQUALS;
            case '[': return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']': return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\': return KeyEvent.KEYCODE_BACKSLASH;
            case ';': return KeyEvent.KEYCODE_SEMICOLON;
            case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case '`': return KeyEvent.KEYCODE_GRAVE;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
