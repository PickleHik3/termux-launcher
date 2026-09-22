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
 *
 * <p>Everything leaves through a {@link Sink}, so the mapping — and the chords the edit keys
 * become — can be read back in a test without a native {@link LorieView} behind it.
 */
public final class X11KeyboardBridge implements TerminalKeyEventHandler.KeyValueInterceptor {

    @NonNull private final Sinks sinks;

    /** Everything the bridge does to the display. */
    public interface Sink {
        /** Press ({@code down}) or release one Android keycode. */
        void key(int keyCode, boolean down);

        /** Type text, whatever the keyboard can produce, emoji included. */
        void text(@NonNull String text);

        /** Whether there is a display server on the other end. */
        boolean connected();

        /** Offer the newest Android clip to X, so a paste chord pastes what was last copied. */
        void refreshClipboard();

        /** Android's clipboard text, for the paste that types instead of chording. */
        @Nullable String clipboardText();
    }

    /** Where the live sink comes from; it changes as the page attaches and detaches. */
    public interface Sinks {
        @Nullable Sink sink();
    }

    /** Where the live view comes from; it changes as the page attaches and detaches. */
    public interface Supplier {
        @Nullable LorieView displayView();
    }

    public X11KeyboardBridge(@NonNull Supplier display) {
        this.sinks = () -> {
            LorieView view = display.displayView();
            return view == null ? null : new ViewSink(view);
        };
    }

    /** For the test that reads the chords back; production goes through the view. */
    @NonNull
    static X11KeyboardBridge over(@NonNull Sinks sinks) {
        return new X11KeyboardBridge(sinks);
    }

    private X11KeyboardBridge(@NonNull Sinks sinks) {
        this.sinks = sinks;
    }

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        if (isLauncherSide(value)) return false;
        Sink view = sinks.sink();
        if (view == null || !view.connected()) return false;
        switch (value.getKind()) {
            case Event:
                // Only the action key is something X should see; the keyboard's other events
                // are answered above.
                return sendModified(view, KeyEvent.KEYCODE_ENTER, ctrl, alt, shift);
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
                return sendEditing(view, value.getEditing(), ctrl, alt, shift);
            case Keyevent:
                return sendModified(view, value.getKeyevent(), ctrl, alt, shift);
            case Slider:
                return sendSlider(view, value.getSlider(), value.getSliderRepeat(), ctrl, alt,
                    shift);
            case Modifier:
                // The keyboard tracks its own modifier state and tells us on the next value; a
                // bare modifier press has nothing to send.
                return true;
            default:
                return true;
        }
    }

    /**
     * Values that belong to the launcher rather than to whatever is typing: a {@code tool:} key
     * (window switching, the palette, the places) and the keyboard's own events (layout switch,
     * hide, settings). They go on to the host, which knows it is on the Display place; taking
     * them here would make the space bar's corners dead while the display is up.
     */
    static boolean isLauncherSide(@NonNull KeyValue value) {
        switch (value.getKind()) {
            case Launcher_tool:
                return true;
            // A macro is expanded by the handler, and each key it yields comes back through
            // here; the compose values only move the host's compose indicator.
            case Macro:
            case Compose_pending:
            case Placeholder:
                return true;
            case Event:
                return value.getEvent() != KeyValue.Event.ACTION;
            default:
                return false;
        }
    }

    /**
     * The edit keys are the chords a desktop app expects: copy Ctrl+C, cut Ctrl+X, paste Ctrl+V,
     * select all Ctrl+A, undo Ctrl+Z, redo Ctrl+Y. A held Shift or Alt rides along, so Shift+copy
     * is Ctrl+Shift+C — the copy chord of every X terminal emulator.
     *
     * <p>Paste as plain text types the clipboard instead, which is the way into an app where
     * Ctrl+V means something else of its own.
     */
    private boolean sendEditing(@NonNull Sink view, @NonNull KeyValue.Editing editing,
                                boolean ctrl, boolean alt, boolean shift) {
        switch (editing) {
            case SPACE_BAR:
                return sendModified(view, KeyEvent.KEYCODE_SPACE, ctrl, alt, shift);
            case BACKSPACE:
                return sendModified(view, KeyEvent.KEYCODE_DEL, ctrl, alt, shift);
            case DELETE_WORD:
                return sendModified(view, KeyEvent.KEYCODE_DEL, true, alt, shift);
            case FORWARD_DELETE_WORD:
                return sendModified(view, KeyEvent.KEYCODE_FORWARD_DEL, true, alt, shift);
            case COPY:
                return sendModified(view, KeyEvent.KEYCODE_C, true, alt, shift);
            case CUT:
                return sendModified(view, KeyEvent.KEYCODE_X, true, alt, shift);
            case PASTE:
                // X pastes its own selection, so it has to be told what Android holds now — a
                // copy made in another app is otherwise a paste of whatever X had last.
                view.refreshClipboard();
                return sendModified(view, KeyEvent.KEYCODE_V, true, alt, shift);
            case SELECT_ALL:
                return sendModified(view, KeyEvent.KEYCODE_A, true, alt, shift);
            case UNDO:
                return sendModified(view, KeyEvent.KEYCODE_Z, true, alt, shift);
            case REDO:
                return sendModified(view, KeyEvent.KEYCODE_Y, true, alt, shift);
            case PASTE_PLAIN:
                sendText(view, view.clipboardText());
                return true;
            default:
                // Selection actions and Android's own context-menu entries mean nothing to X.
                return true;
        }
    }

    /**
     * A space-bar swipe: the cursor moves one arrow key per tick. The sign of {@code repeat} is
     * the keyboard's — negative means the finger came back the other way — and the selection
     * sliders hold Shift so an X text field extends its selection the way a desktop does.
     */
    private boolean sendSlider(@NonNull Sink view, @NonNull KeyValue.Slider slider, int repeat,
                               boolean ctrl, boolean alt, boolean shift) {
        if (repeat == 0) return true;
        boolean reverse = repeat < 0;
        int keyCode;
        boolean selecting = false;
        switch (slider) {
            case Cursor_left:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Cursor_right:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Cursor_up:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
                break;
            case Cursor_down:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case Selection_cursor_left:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                selecting = true;
                break;
            case Selection_cursor_right:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                selecting = true;
                break;
            default:
                return true;
        }
        int count = Math.abs(repeat);
        for (int i = 0; i < count; i++)
            sendModified(view, keyCode, ctrl, alt, shift || selecting);
        return true;
    }

    private void sendText(@NonNull Sink view, @Nullable String text) {
        if (text == null || text.isEmpty()) return;
        view.text(text);
    }

    /**
     * Press and release {@code keyCode} inside whatever modifiers are held. The modifiers are
     * pressed and released around it so the server sees a complete chord and is never left with
     * a stuck Ctrl when the page goes away mid-stroke.
     */
    private boolean sendModified(@NonNull Sink view, int keyCode, boolean ctrl, boolean alt,
                                 boolean shift) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return true;
        if (ctrl) view.key(KeyEvent.KEYCODE_CTRL_LEFT, true);
        if (alt) view.key(KeyEvent.KEYCODE_ALT_LEFT, true);
        if (shift) view.key(KeyEvent.KEYCODE_SHIFT_LEFT, true);
        view.key(keyCode, true);
        view.key(keyCode, false);
        if (shift) view.key(KeyEvent.KEYCODE_SHIFT_LEFT, false);
        if (alt) view.key(KeyEvent.KEYCODE_ALT_LEFT, false);
        if (ctrl) view.key(KeyEvent.KEYCODE_CTRL_LEFT, false);
        return true;
    }

    /** The sink the launcher runs on: the Display page's own view. */
    static final class ViewSink implements Sink {

        @NonNull private final LorieView view;

        ViewSink(@NonNull LorieView view) {
            this.view = view;
        }

        @Override public void key(int keyCode, boolean down) {
            view.sendKeyEvent(0, keyCode, down);
        }

        @Override public void text(@NonNull String text) {
            view.sendTextEvent(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override public boolean connected() {
            return view.connected();
        }

        @Override public void refreshClipboard() {
            view.checkForClipboardChange();
        }

        @Nullable @Override public String clipboardText() {
            return com.termux.shared.interact.ShareUtils.getTextStringFromClipboardIfSet(
                view.getContext(), true);
        }
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
