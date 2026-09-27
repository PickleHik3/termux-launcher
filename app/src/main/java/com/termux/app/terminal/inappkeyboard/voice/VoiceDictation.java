package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One dictation in the panel, from the first phrase to the button that uses it (agreed flow,
 * 2026-09-27): nothing reaches the terminal while the user speaks. Phrases collect here as heard;
 * once listening has stopped and the one cleanup pass (if any) has landed, the text waits for the
 * panel's ✓ (insert once, at the cursor) or Copy. A press that comes early — while listening,
 * transcribing or cleaning up — is remembered and carried out the moment the text settles, so
 * the user never has to press twice.
 *
 * <p>Pure and main-thread only; the host owns the microphone, the cleanup and the panel.
 */
public final class VoiceDictation {

    /** Where the dictation stands. */
    public enum Phase {
        /** No dictation, or the last one has been closed. */
        IDLE,
        /** The microphone is open. */
        LISTENING,
        /** The microphone has closed; phrases are still transcribing or the cleanup is running. */
        FINISHING,
        /** The text is final and waits in the panel for ✓, Copy or ×. */
        WAITING,
        /** ✓ or Copy has used the text; the panel is only saying so. */
        USED
    }

    /** What a panel button does with the text. */
    public enum Use {
        /** ✓: type it once where the keyboard would type, never with an Enter. */
        INSERT,
        /** Copy: put it on the clipboard. */
        COPY
    }

    private final StringBuilder raw = new StringBuilder();
    private Phase phase = Phase.IDLE;
    @Nullable private Use pending;
    @Nullable private String result;

    /**
     * The microphone has opened. {@code base} is text already waiting in the panel that the new
     * dictation carries on from ({@code ""} for a fresh one); new phrases join onto it.
     */
    public void start(@NonNull String base) {
        raw.setLength(0);
        raw.append(base.trim());
        phase = Phase.LISTENING;
        pending = null;
        result = null;
    }

    /**
     * One transcript, sanitised ({@link VoiceTextSanitizer}) and joined on with a space after
     * earlier text.
     *
     * @return the piece as it joins on, leading space included, or {@code ""} when it was dropped
     *     (non-speech, or a late phrase after the text settled or was discarded)
     */
    @NonNull
    public String append(@NonNull String transcript) {
        if (phase != Phase.LISTENING && phase != Phase.FINISHING) return "";
        String piece = VoiceTextSanitizer.join(raw.length() > 0, VoiceTextSanitizer.clean(transcript.trim()));
        raw.append(piece);
        return piece;
    }

    /** Everything heard so far, as heard. */
    @NonNull
    public String raw() {
        return raw.toString();
    }

    public boolean isEmpty() {
        return raw.length() == 0;
    }

    /** The microphone has closed; what is left is transcribing and, maybe, the cleanup. */
    public void onStopped() {
        if (phase == Phase.LISTENING) phase = Phase.FINISHING;
    }

    /**
     * The text is final: the cleaned text when the pass was accepted, the raw text otherwise.
     *
     * @return a press made while the text was still coming, to be carried out now on
     *     {@link #result()}, or {@code null} to leave it waiting for one
     */
    @Nullable
    public Use onSettled(@NonNull String text) {
        result = text;
        Use early = pending;
        pending = null;
        phase = early != null ? Phase.USED : Phase.WAITING;
        return early;
    }

    /**
     * ✓ or Copy was pressed.
     *
     * @return true when it is to be carried out now on {@link #result()}; false when it has been
     *     remembered until the text settles, or ignored (nothing to use, or already used)
     */
    public boolean press(@NonNull Use use) {
        switch (phase) {
            case WAITING:
                phase = Phase.USED;
                return true;
            case LISTENING:
            case FINISHING:
                pending = use;
                return false;
            default:
                return false;
        }
    }

    /** The press waiting for the text to settle, if any. */
    @Nullable
    public Use pending() {
        return pending;
    }

    /** The final text, once {@link #onSettled} has run; {@code null} before. */
    @Nullable
    public String result() {
        return result;
    }

    /**
     * What a new dictation started now carries on from: the waiting text, or what has been heard
     * when the cleanup is dropped for it. {@code ""} once the text has been used.
     */
    @NonNull
    public String carryOver() {
        switch (phase) {
            case WAITING:
                return result == null ? raw.toString() : result;
            case FINISHING:
                return raw.toString();
            default:
                return "";
        }
    }

    @NonNull
    public Phase phase() {
        return phase;
    }

    /** × on the panel, a swipe, or the pill closing: the text is gone. */
    public void clear() {
        raw.setLength(0);
        phase = Phase.IDLE;
        pending = null;
        result = null;
    }
}
