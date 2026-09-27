package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * What a dictation session has typed into the terminal, and whether the line is still exactly
 * that, so the cleanup at the end may swap it in place (spec D5): erasing what was typed is only
 * safe while nothing else has reached the shell since — no Enter, no other key, no cursor move.
 *
 * <p>"Nothing else" is judged by the session's last-write mark
 * ({@code TerminalSession.getLastWriteUptimeMs()}): the host records it right after each voice
 * write, and any later key, paste or terminal reply moves it. A reply the terminal sends on its
 * own (a cursor report a program asked for) moves it too; that only ever costs a swap, never
 * text, since the cleaned text is then offered in the panel instead. A phrase that went anywhere
 * but the terminal (the palette, a search field) rules the swap out for the session.
 *
 * <p>Main thread only.
 */
public final class VoiceTypedLine {

    /** Backspace as the in-app keyboard sends it: DEL, which shells and TUIs erase one character on. */
    static final char ERASE = 0x7f;

    private final StringBuilder typed = new StringBuilder();
    private long writeMark;
    private boolean offTerminal;

    /** Forgets the last session's line. */
    public void reset() {
        typed.setLength(0);
        writeMark = 0L;
        offTerminal = false;
    }

    /**
     * {@code text} has just been typed, exactly as written (leading space included).
     *
     * @param toTerminal false when it went to an interceptor instead of the shell
     * @param writeMark the terminal session's last-write mark right after the write
     */
    public void onTyped(@NonNull String text, boolean toTerminal, long writeMark) {
        typed.append(text);
        if (toTerminal) this.writeMark = writeMark;
        else offTerminal = true;
    }

    /** Everything the session typed, as typed. */
    @NonNull
    public String text() {
        return typed.toString();
    }

    public boolean isEmpty() {
        return typed.length() == 0;
    }

    /** Whether some phrase went to an interceptor rather than the shell: nothing to swap or replace there. */
    public boolean wentOffTerminal() {
        return offTerminal;
    }

    /**
     * True when the line can be swapped in place: everything went to the terminal, the session
     * is still running, and its last-write mark is still the one the last voice write left.
     */
    public boolean isUntouched(long currentWriteMark, boolean targetRunning) {
        return !isEmpty() && !offTerminal && targetRunning && writeMark != 0L && currentWriteMark == writeMark;
    }

    /** How many {@link #ERASE}s take the typed text back off the line: one per code point. */
    public int eraseCount() {
        return typed.codePointCount(0, typed.length());
    }

    /** One {@link #ERASE} per code point of what the session typed, then {@code replacement}. */
    @NonNull
    public String swapSequence(@NonNull String replacement) {
        int count = eraseCount();
        StringBuilder out = new StringBuilder(count + replacement.length());
        for (int i = 0; i < count; i++) out.append(ERASE);
        out.append(replacement);
        return out.toString();
    }

    /** The swap has been written: the line is now {@code replacement}, as of {@code writeMark}. */
    public void onSwapped(@NonNull String replacement, long writeMark) {
        typed.setLength(0);
        typed.append(replacement);
        this.writeMark = writeMark;
    }
}
