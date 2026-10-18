package com.termux.app.terminal.inappkeyboard.voice;

import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import java.util.Collections;
import java.util.List;

/**
 * Editing the dictation card's text by hand: a tap on the text opens it with the caret where the
 * finger was, and the in-app keyboard (or the phone's, or a hardware one) types into it without
 * any focus changing, the way the terminal rename chip is typed into.
 *
 * <p>The edit wins over everything still to come for the dictation. A tap while listening pauses
 * first; a cleanup still to come or running is dropped, so its answer can never land over the
 * edit; an early ✓ or Copy is forgotten. Phrases still transcribing join the end of the draft.
 *
 * <p>While the editor is open it owns the in-app keyboard's interceptor slot, and every way out
 * gives the slot back before anything else runs, so ✓ never types into the card's own editor:
 * Enter, Back, a tap outside the text, the card's buttons, × and a swipe. Only × and the swipe
 * ({@link #abandon}) discard; everything else keeps the edit. What it became is the dictation's
 * ({@link VoiceDictation#commitEdit}), and the card shows it plain with undo back to the text
 * before the edit.
 *
 * <p>Main thread. The activity's side is {@link Host}; the card's is {@link Card}.
 */
public final class VoiceCardEdit implements VoiceEditController.Host {

    /** The activity's side: the microphone, the cleanup, and the keyboards. */
    public interface Host {
        /** The dictation card while it is up, or {@code null}. */
        @Nullable Card card();

        /** Closes the microphone if it is open; phrases already captured still arrive. */
        void stopListening();

        /**
         * Drops the cleanup still to come or running for this dictation. When the session has
         * already ended and only the cleanup was left, the text settles now as it stands.
         */
        void dropCleanup();

        /** Brings the in-app keyboard up; false when the user has it off. */
        boolean ensureTypingKeyboard();

        /** Shows the phone's keyboard, its insets accepted first ({@code onSystemImeRequested}). */
        void showSystemKeyboard();

        /** Puts the phone's keyboard down again if {@link #showSystemKeyboard} raised it. */
        void releaseSystemKeyboard();

        /** Installs {@code interceptor} in the in-app keyboard's slot, or with null hands the slot to whoever owns it now. */
        void claimTyping(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor);

        /** Whether a raw screen point is on a keyboard the edit is typed with: a tap there is not "outside". */
        boolean isOnTypingKeyboard(float rawX, float rawY);
    }

    /** The card's side; {@link VoiceListeningIndicator} is it. */
    public interface Card {
        /** Whether a raw screen point is on the card's text. */
        boolean isOnText(float rawX, float rawY);

        /** The editor has opened, or its draft or caret changed: the text with the caret and the cleanup's marks. */
        void showDraft(@NonNull VoiceEditModel model);

        /**
         * The editor has closed on {@code shown}. {@code editedFrom} is the text before the edit
         * when the edit changed it (undo goes back to it), or null when it did not.
         */
        void endEdit(@Nullable String editedFrom, @NonNull String shown);

        /** The editor has gone with the card's text (×, a swipe): stop editing, show nothing new. */
        void abandonEdit();

        /** The text has settled with an edit in it: the edit plain, undo back to {@code editedFrom}. */
        void showEdited(@NonNull String editedFrom, @NonNull String edited);

        void setStatus(@StringRes int status);

        /** The UTF-16 offset one laid-out line above or below {@code caretUtf16}, or -1. */
        int offsetOnAdjacentLine(int caretUtf16, int direction);
    }

    @NonNull private final VoiceDictation dictation;
    @NonNull private final Host host;
    @NonNull private final VoiceEditController controller;
    /** How far a finger may move and still be a tap, in pixels. */
    private final int touchSlop;

    /** A finger came down outside the text and the keyboards while editing, and has not moved off as a drag. */
    private boolean outsideTap;
    private float downX;
    private float downY;

    public VoiceCardEdit(@NonNull VoiceDictation dictation, @NonNull Host host,
                         @NonNull ClipboardText clipboard, int touchSlop) {
        this.dictation = dictation;
        this.host = host;
        this.controller = new VoiceEditController(clipboard);
        this.touchSlop = Math.max(0, touchSlop);
    }

    public boolean isActive() {
        return controller.isActive();
    }

    /**
     * A tap on the card's text, at {@code shownOffset} in the text it shows ({@code -1}: the end,
     * from an accessibility click). Opens the editor there, or moves the caret when it is open.
     */
    public void onTextTapped(int shownOffset) {
        if (controller.isActive()) {
            controller.moveCaretTo(shownOffset < 0 ? Integer.MAX_VALUE : shownOffset);
            showKeyboard();
            return;
        }
        Card card = host.card();
        if (card == null || !dictation.beginEdit()) return;
        // In this order: the dictation is the user's before the microphone closes, so a session
        // that ends at once already settles without a cleanup.
        host.stopListening();
        host.dropCleanup();
        String text = dictation.editText();
        // A cleanup on screen keeps its changed and added words marked while the user edits it.
        String cleanedFrom = dictation.cleanedFrom();
        List<VoiceEditModel.Mark> marks = cleanedFrom == null
            ? Collections.emptyList() : VoiceEditModel.cleanupMarks(cleanedFrom, text);
        controller.begin(text, shownOffset < 0 ? text.length() : shownOffset, marks, this);
        host.claimTyping(controller);
        showKeyboard();
    }

    private void showKeyboard() {
        if (!host.ensureTypingKeyboard()) host.showSystemKeyboard();
    }

    /** Ends the edit, keeping it: Enter or Back elsewhere, a button of the card, the activity leaving. */
    public void end() {
        controller.end();
    }

    /** Ends the edit without keeping it: the card and its text are going (×, a swipe, the card closing). */
    public void abandon() {
        controller.abandon();
    }

    /** A phrase that finished transcribing while the editor is open. @return false when it is not open. */
    public boolean onPhrase(@NonNull String piece) {
        if (!controller.isActive()) return false;
        controller.appendPhrase(piece);
        return true;
    }

    /** The text has settled; an edit kept before it did is now shown as the dictation's edit. */
    public void onSettled() {
        if (controller.isActive()) return;
        Card card = host.card();
        String from = dictation.editedFrom();
        String result = dictation.result();
        if (card == null || from == null || result == null) return;
        card.showEdited(from, result);
        card.setStatus(R.string.voice_input_edited);
    }

    /** Hardware and external-keyboard strokes while the editor is open. */
    public boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        return controller.handleKeyDown(keyCode, event);
    }

    /** System-IME committed text while the editor is open. */
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        return controller.handleCodePoint(codePoint, ctrlDown);
    }

    /**
     * Every touch of the window, seen before any view: a tap (not a drag or a scroll) that starts
     * outside the card's text and off the keyboards ends the edit, kept, on its release, before
     * the view under it acts on the tap.
     */
    public void onWindowTouch(int action, float rawX, float rawY) {
        if (!controller.isActive()) {
            outsideTap = false;
            return;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                Card card = host.card();
                outsideTap = card != null && !card.isOnText(rawX, rawY) && !host.isOnTypingKeyboard(rawX, rawY);
                downX = rawX;
                downY = rawY;
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (outsideTap && (Math.abs(rawX - downX) > touchSlop || Math.abs(rawY - downY) > touchSlop))
                    outsideTap = false;
                break;
            case MotionEvent.ACTION_UP:
                if (outsideTap) end();
                outsideTap = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                outsideTap = false;
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ VoiceEditController.Host

    @Override
    public void onDraftChanged(@NonNull VoiceEditModel model) {
        Card card = host.card();
        if (card != null) card.showDraft(model);
    }

    @Override
    public int offsetOnAdjacentLine(int caretUtf16, int direction) {
        Card card = host.card();
        return card == null ? -1 : card.offsetOnAdjacentLine(caretUtf16, direction);
    }

    @Override
    public void onEditEnded(@Nullable String text) {
        // The slot first: whatever runs next (✓ above all) must find its real owner in it.
        host.claimTyping(null);
        host.releaseSystemKeyboard();
        outsideTap = false;
        Card card = host.card();
        if (text == null) {
            if (card != null) card.abandonEdit();
            return;
        }
        boolean changed = dictation.commitEdit(text);
        if (card == null) return;
        card.endEdit(changed ? dictation.editedFrom() : null, dictation.editText());
        if (changed && dictation.phase() == VoiceDictation.Phase.WAITING) card.setStatus(R.string.voice_input_edited);
    }
}
