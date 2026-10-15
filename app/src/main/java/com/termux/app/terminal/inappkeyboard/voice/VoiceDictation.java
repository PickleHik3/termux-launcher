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
 * <p>The user may also edit the text by hand on the card ({@link #beginEdit}, {@link #commitEdit}).
 * That edit wins over everything still to come for this dictation: a pending press is dropped,
 * the cleanup and the command formatter are skipped ({@link #isByHand}), and phrases that were
 * still transcribing join the end of the edited text. Undo stays a two-state toggle: after an
 * edit it switches between the text as it stood before the edit and the edit, and an earlier
 * cleanup's undo is folded into that (the pre-edit text may itself be the cleaned one).
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
        /** The text is final and waits in the panel for ✓, Copy, undo, resume or ×. */
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
     * The undo pair: {@link #after} is the cleanup's text (the model's, or the command
     * formatter's) or the user's edit; {@link #before} is what undo goes back to, the text as it
     * went into the cleanup or as it stood before the edit. Both null while there is nothing to
     * undo.
     */
    @Nullable private String before;
    @Nullable private String after;
    /** The pair is an edit by hand rather than a cleanup. */
    private boolean pairIsEdit;
    /** Undo has put {@link #before} back; {@link #toggleUndo} again redoes it. */
    private boolean undone;
    /** The user has opened the editor on this dictation: no cleanup or formatter may replace the text. */
    private boolean byHand;
    /** An edit kept before the text settled; late phrases join onto it, and it is the text once settled. */
    @Nullable private String handText;

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
        forgetEdits();
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
        if (handText != null) handText = VoiceEditModel.joinOnto(handText, piece);
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
     * The text is final as heard: no cleanup, or nothing better came back.
     *
     * @return a press made while the text was still coming, to be carried out now on
     *     {@link #result()}, or {@code null} to leave it waiting for one
     */
    @Nullable
    public Use onSettled(@NonNull String text) {
        String hand = handText;
        handText = null;
        if (hand != null) {
            // Edited before it settled: the edit is the text, and undo goes back to it as heard.
            setPair(text, hand, true);
            return settle(hand);
        }
        setPair(null, null, false);
        return settle(text);
    }

    /**
     * The text is final as {@code cleanedText}, a cleanup of {@link #raw()} that undo can take
     * back. Returns what {@link #onSettled} does. A dictation the user has edited by hand takes no
     * cleanup: it settles as {@link #onSettled} on what was heard, the edit kept.
     */
    @Nullable
    public Use onCleaned(@NonNull String cleanedText) {
        if (byHand) return onSettled(raw.toString());
        setPair(raw.toString(), cleanedText, false);
        return settle(cleanedText);
    }

    private void setPair(@Nullable String from, @Nullable String to, boolean edit) {
        boolean pair = from != null && to != null && !from.equals(to);
        before = pair ? from : null;
        after = pair ? to : null;
        pairIsEdit = pair && edit;
        undone = false;
    }

    @Nullable
    private Use settle(@NonNull String text) {
        result = text;
        Use early = pending;
        pending = null;
        phase = early != null ? Phase.USED : Phase.WAITING;
        return early;
    }

    /** Whether the waiting text is a cleanup or an edit that undo (or, once undone, redo) can switch. */
    public boolean canUndo() {
        return before != null && after != null && phase == Phase.WAITING;
    }

    /** Whether what undo switches is an edit by hand rather than a cleanup. */
    public boolean isEditPair() {
        return pairIsEdit && before != null;
    }

    /** Whether undo has put the text back as it went into the cleanup. */
    public boolean isUndone() {
        return undone;
    }

    /**
     * Undo, or redo after an undo: the waiting text goes back to what went into the cleanup (as
     * heard, or as carried on from) or to the text before the edit, or forward to the cleaned or
     * edited text again. ✓, Copy and a resumed dictation all take the text as it now stands.
     *
     * @return false when there is nothing to switch
     */
    public boolean toggleUndo() {
        if (!canUndo()) return false;
        undone = !undone;
        result = undone ? before : after;
        return true;
    }

    // ------------------------------------------------------------------ editing by hand

    /** Whether the text can be opened in the editor: while it is being heard, finished or waits; not once used. */
    public boolean canEdit() {
        return phase == Phase.LISTENING || phase == Phase.FINISHING || phase == Phase.WAITING;
    }

    /**
     * The user has opened the editor: from now on this dictation is theirs. A press waiting for
     * the text is dropped (they changed their mind by editing), and no cleanup or formatter may
     * replace the text ({@link #isByHand}).
     *
     * @return false when the text cannot be edited ({@link #canEdit})
     */
    public boolean beginEdit() {
        if (!canEdit()) return false;
        byHand = true;
        pending = null;
        return true;
    }

    /** Whether the user has opened the editor on this dictation since it started. */
    public boolean isByHand() {
        return byHand;
    }

    /** The text the editor opens on and shows: an edit not yet settled, the waiting text, or what has been heard. */
    @NonNull
    public String editText() {
        if (handText != null) return handText;
        if (result != null) return result;
        return raw.toString();
    }

    /**
     * The editor has closed on {@code text}. While the text waits it becomes the text, and undo
     * switches between it and the text before it; before the text has settled it is kept and
     * becomes the text when it does ({@link #onSettled}).
     *
     * @return true when the text changed
     */
    public boolean commitEdit(@NonNull String text) {
        switch (phase) {
            case WAITING: {
                String current = result == null ? "" : result;
                if (text.equals(current)) return false;
                setPair(current, text, true);
                result = text;
                return true;
            }
            case LISTENING:
            case FINISHING:
                if (text.equals(editText())) return false;
                handText = text;
                return true;
            default:
                return false;
        }
    }

    /**
     * What undo would go back to from an edit, for the panel to show the pair: the text as heard
     * while an edit waits for the text to settle, the text before the edit once it has; {@code null}
     * when the text is not an edit.
     */
    @Nullable
    public String editedFrom() {
        if (handText != null) return raw.toString();
        return isEditPair() ? before : null;
    }

    /**
     * What the cleanup took in, while the text the editor opens on ({@link #editText}) is that
     * cleanup's answer, so the editor can keep its marks; {@code null} when the text is anything
     * else: as heard, undone, or an edit.
     */
    @Nullable
    public String cleanedFrom() {
        if (handText != null || pairIsEdit || undone || before == null || after == null) return null;
        return after.equals(result) ? before : null;
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
                return handText != null ? handText : raw.toString();
            default:
                return "";
        }
    }

    @NonNull
    public Phase phase() {
        return phase;
    }

    /** The pill's ×, a swipe, or the pill closing: the text is gone. */
    public void clear() {
        raw.setLength(0);
        phase = Phase.IDLE;
        pending = null;
        result = null;
        forgetEdits();
    }

    private void forgetEdits() {
        before = null;
        after = null;
        pairIsEdit = false;
        undone = false;
        byHand = false;
        handText = null;
    }
}
