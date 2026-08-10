package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * What the open palette does with a code point committed by a system IME.
 *
 * <p>Third-party keyboards never send the overlay a {@link android.view.KeyEvent}: they commit
 * plain text through the {@code InputConnection}, which lands in
 * {@code TerminalView#inputCodePoint} and then on the view client. Without this route the palette
 * looks unfocused on such a keyboard — modifier chords still open it, since those do arrive as key
 * events, but nothing typed afterwards reaches it.
 *
 * <p>Every raw decision lives here rather than in the controller, because the controller needs a
 * live {@code TermuxActivity} and cannot be unit-tested; the controller is left holding only
 * routing. Same split as {@link CommandPaletteCaptureModel}.
 */
public final class CommandPaletteSoftKeyDecision {

    /**
     * {@link #IGNORE} is the only outcome that lets the code point through to the shell. While the
     * palette is up everything else is claimed, matching the {@code default:} case of the in-app
     * keyboard's interceptor: nothing may leak into the terminal behind the overlay.
     */
    public enum Action { IGNORE, SWALLOW, APPEND, COMMIT, BACKSPACE, COLLAPSE }

    private CommandPaletteSoftKeyDecision() {}

    /**
     * @param open      whether the palette is showing; a closed palette claims nothing.
     * @param capturing whether the palette is in capture mode, where committed text is a dead end:
     *                  a binding needs a key code and a modifier state, and neither survives the
     *                  trip through the {@code InputConnection}. Swallowed rather than guessed at.
     * @param ctrlDown  a latched or held Ctrl, already applied by
     *                  {@code TerminalView#sendTextToTerminal} before the code point arrives.
     *                  Swallowed without appending, as the hardware path does.
     */
    @NonNull
    public static Action decide(boolean open, boolean capturing, int codePoint, boolean ctrlDown) {
        if (!open) return Action.IGNORE;
        if (capturing) return Action.SWALLOW;
        if (ctrlDown) return Action.SWALLOW;
        // The AOSP keyboard and its descendants send ⏎ as text rather than as KEYCODE_ENTER, so
        // enter reaches the palette only here — see TerminalView#sendTextToTerminal's own note.
        if (codePoint == '\r' || codePoint == '\n') return Action.COMMIT;
        // Defensive: most IMEs delete through deleteSurroundingText, which arrives as KEYCODE_DEL.
        if (codePoint == 127 || codePoint == 8) return Action.BACKSPACE;
        if (codePoint == 27) return Action.COLLAPSE;
        if (codePoint >= ' ') return Action.APPEND;
        return Action.SWALLOW;
    }
}
