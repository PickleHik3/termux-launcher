package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dictation marks (protocol draft v1, 2026-09-28): a program that sets private mode 7727 together
 * with bracketed paste (2004) is told which input the dictation typed, so it can show "listening"
 * and make a dictated phrase glow as it lands. Every mark is {@code OSC 7727 ; verb [; key=value]
 * ST}:
 * <ul>
 *   <li>{@code listen} — the microphone has opened;</li>
 *   <li>{@code phrase;id=N} — the very next bracketed paste is dictated phrase N (from 1, rising
 *       per terminal session);</li>
 *   <li>{@code end}, or {@code end;reason=cancel} — the microphone has closed, or the dictation was
 *       discarded or failed.</li>
 * </ul>
 * A session without both modes gets exactly the bytes it got before marks existed: no mark, and
 * the text written as is, not bracketed. {@code replace} is never sent: the one cleanup pass
 * lands in the panel before the text is used, so nothing is ever retyped in the terminal.
 *
 * <p>Main thread only. {@code S} is the terminal session; the {@link Io} reads its modes and writes
 * to it, so the formatting and the counting stay testable without one.
 */
public final class DictationMarks<S> {

    /** Reads and writes one terminal session. */
    public interface Io<S> {
        /** Whether {@code session} is running with both mode 7727 and bracketed paste set right now. */
        boolean acceptsMarks(@NonNull S session);

        /** Writes {@code data} to {@code session}'s program, as typing would. */
        void write(@NonNull S session, @NonNull String data);
    }

    static final String OSC = "\033]7727;";
    static final String ST = "\033\\";
    static final String PASTE_START = "\033[200~";
    static final String PASTE_END = "\033[201~";

    private final Io<S> io;
    /** The last phrase id sent to each session; a session never seen has sent none. */
    private final Map<S, Integer> lastIds = new WeakHashMap<>();
    /** The session told {@code listen} and not yet {@code end}, or {@code null}. */
    @Nullable private S listening;

    public DictationMarks(@NonNull Io<S> io) {
        this.io = io;
    }

    @NonNull
    static String listenMark() {
        return OSC + "listen" + ST;
    }

    @NonNull
    static String phraseMark(int id) {
        return OSC + "phrase;id=" + id + ST;
    }

    @NonNull
    static String endMark(boolean cancelled) {
        return OSC + (cancelled ? "end;reason=cancel" : "end") + ST;
    }

    /**
     * {@code text} as a bracketed paste. An ESC would let the text end the paste early; the
     * sanitiser has already taken every one out, so this only guards the bracket.
     */
    @NonNull
    static String bracketed(@NonNull String text) {
        return PASTE_START + text.replace("\033", "") + PASTE_END;
    }

    /**
     * The microphone has opened with {@code session} in front ({@code null}: no shell in front).
     * Ends a listen still open on another session first, so each {@code listen} has one {@code end}.
     */
    public void listen(@Nullable S session) {
        if (listening != null) end(false);
        if (session == null || !io.acceptsMarks(session)) return;
        listening = session;
        io.write(session, listenMark());
    }

    /**
     * The microphone has closed ({@code cancelled}: the dictation was discarded or failed). Only
     * the session told {@code listen} hears it, and only while it still takes marks: a program that
     * has turned the mode off, or exited, would get the mark as typed garbage. No-op with no
     * listen open.
     */
    public void end(boolean cancelled) {
        S session = listening;
        listening = null;
        if (session != null && io.acceptsMarks(session)) io.write(session, endMark(cancelled));
    }

    /**
     * Types dictated {@code text} into {@code session}: marked as the session's next phrase and
     * bracketed when it takes marks, written as is otherwise.
     */
    public void type(@NonNull S session, @NonNull String text) {
        if (!io.acceptsMarks(session)) {
            io.write(session, text);
            return;
        }
        Integer last = lastIds.get(session);
        int id = last == null ? 1 : last + 1;
        lastIds.put(session, id);
        io.write(session, phraseMark(id) + bracketed(text));
    }
}
