package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Editing the card's text: what a tap does to the dictation, the microphone and the cleanup, that
 * the keyboard's slot is taken once and always given back, and which touches end the edit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class VoiceCardEditTest {

    private static final int SLOP = 10;

    @Test
    public void aTapWhileListeningTakesTheDictationThenPausesAndDropsTheCleanup() {
        Fixture f = new Fixture();
        f.dictation.append("open the page");
        f.edit.onTextTapped(5);
        // The dictation was the user's before the microphone closed, so its end takes no cleanup.
        assertEquals(Arrays.asList("stop:byHand", "drop"), f.host.calls);
        assertTrue(f.edit.isActive());
        assertSame(f.slotOwner(), f.host.slot);
        assertEquals(5, f.card.caret);
        assertEquals("open the page", f.card.draft);
        assertTrue(f.host.inAppShown);
    }

    @Test
    public void enterKeepsTheEditGivesTheSlotBackAndTheCardShowsThePair() {
        Fixture f = new Fixture();
        f.settle("open page");
        f.edit.onTextTapped(4);
        f.edit.handleCodePoint(' ', false);
        f.edit.handleCodePoint('t', false);
        f.edit.handleCodePoint('h', false);
        f.edit.handleCodePoint('e', false);
        assertTrue(f.edit.handleKeyDown(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertFalse(f.edit.isActive());
        assertNull(f.host.slot);
        assertEquals(1, f.host.restores);
        assertEquals("open the page", f.dictation.result());
        assertEquals("open page", f.card.editedFrom);
        assertEquals("open the page", f.card.shown);
        assertEquals(R.string.voice_input_edited, f.card.status);
    }

    @Test
    public void anUnchangedEditShowsTheTextAsItWas() {
        Fixture f = new Fixture();
        f.settle("hello");
        f.edit.onTextTapped(2);
        f.edit.end();
        assertNull(f.card.editedFrom);
        assertEquals("hello", f.card.shown);
        assertEquals(0, f.card.status);
    }

    @Test
    public void abandonGivesTheSlotBackAndKeepsNothing() {
        Fixture f = new Fixture();
        f.settle("hello");
        f.edit.onTextTapped(5);
        f.edit.handleCodePoint('!', false);
        f.edit.abandon();
        assertNull(f.host.slot);
        assertTrue(f.card.abandoned);
        assertEquals("hello", f.dictation.result());
    }

    @Test
    public void aTapOutsideTheTextEndsTheEditButADragAKeyOrTheTextDoNot() {
        Fixture f = new Fixture();
        f.settle("hello");
        f.edit.onTextTapped(5);
        // On the text: the caret's business, not an ending.
        f.card.onText = true;
        touch(f.edit, 10, 10, 10, 10);
        assertTrue(f.edit.isActive());
        f.card.onText = false;
        // On a key.
        f.host.onKeyboard = true;
        touch(f.edit, 10, 10, 10, 10);
        assertTrue(f.edit.isActive());
        f.host.onKeyboard = false;
        // A drag, a scroll of the terminal behind.
        touch(f.edit, 10, 10, 10, 10 + SLOP * 3);
        assertTrue(f.edit.isActive());
        // A tap anywhere else.
        touch(f.edit, 10, 10, 12, 11);
        assertFalse(f.edit.isActive());
        assertNull(f.host.slot);
    }

    @Test
    public void aSecondTapMovesTheCaretAndAccessibilityGoesToTheEnd() {
        Fixture f = new Fixture();
        f.settle("hello world");
        f.edit.onTextTapped(-1);
        assertEquals(11, f.card.caret);
        f.edit.onTextTapped(3);
        assertEquals(3, f.card.caret);
        assertEquals(1, f.host.claims);
    }

    @Test
    public void withTheInAppKeyboardOffThePhonesKeyboardComesUp() {
        Fixture f = new Fixture();
        f.host.inAppEnabled = false;
        f.settle("hello");
        f.edit.onTextTapped(0);
        assertTrue(f.host.systemShown);
        f.edit.end();
        assertTrue(f.host.systemReleased);
    }

    @Test
    public void aPhraseThatArrivesWhileEditingJoinsTheDraftAndSettlesAsTheEdit() {
        Fixture f = new Fixture();
        f.dictation.append("note to self");
        f.edit.onTextTapped(0);
        f.edit.handleCodePoint('N', false);
        f.edit.handleKeyDown(KeyEvent.KEYCODE_FORWARD_DEL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL));
        String piece = f.dictation.append("buy milk");
        assertTrue(f.edit.onPhrase(piece));
        assertEquals("Note to self buy milk", f.card.draft);
        f.edit.end();
        f.dictation.onStopped();
        f.dictation.onSettled(f.dictation.raw());
        f.edit.onSettled();
        assertEquals("Note to self buy milk", f.dictation.result());
        assertEquals("note to self buy milk", f.card.editedFrom);
        assertEquals(R.string.voice_input_edited, f.card.status);
        assertFalse(f.edit.onPhrase(" late"));
    }

    @Test
    public void nothingOpensOnceTheTextIsUsedOrWithoutACard() {
        Fixture f = new Fixture();
        f.settle("hello");
        f.dictation.press(VoiceDictation.Use.INSERT);
        f.edit.onTextTapped(1);
        assertFalse(f.edit.isActive());
        Fixture g = new Fixture();
        g.host.card = null;
        g.settle("hello");
        g.edit.onTextTapped(1);
        assertFalse(g.edit.isActive());
        assertNull(g.host.slot);
    }

    private static void touch(VoiceCardEdit edit, float x, float y, float upX, float upY) {
        edit.onWindowTouch(MotionEvent.ACTION_DOWN, x, y);
        edit.onWindowTouch(MotionEvent.ACTION_MOVE, upX, upY);
        edit.onWindowTouch(MotionEvent.ACTION_UP, upX, upY);
    }

    private static final class Fixture {
        final VoiceDictation dictation = new VoiceDictation();
        final FakeCard card = new FakeCard();
        final FakeHost host = new FakeHost(dictation, card);
        final VoiceCardEdit edit = new VoiceCardEdit(dictation, host, () -> null, SLOP);

        Fixture() {
            dictation.start("");
        }

        void settle(String text) {
            dictation.append(text);
            dictation.onStopped();
            dictation.onSettled(text);
        }

        /** The interceptor the editor installed: the only non-null value the slot ever held. */
        TerminalKeyEventHandler.KeyValueInterceptor slotOwner() {
            return host.lastClaimed;
        }
    }

    private static final class FakeHost implements VoiceCardEdit.Host {
        final VoiceDictation dictation;
        @Nullable FakeCard card;
        final List<String> calls = new ArrayList<>();
        @Nullable TerminalKeyEventHandler.KeyValueInterceptor slot;
        @Nullable TerminalKeyEventHandler.KeyValueInterceptor lastClaimed;
        int claims;
        int restores;
        boolean inAppEnabled = true;
        boolean inAppShown;
        boolean systemShown;
        boolean systemReleased;
        boolean onKeyboard;

        FakeHost(VoiceDictation dictation, FakeCard card) {
            this.dictation = dictation;
            this.card = card;
        }

        @Nullable @Override public VoiceCardEdit.Card card() { return card; }

        @Override public void stopListening() {
            calls.add(dictation.isByHand() ? "stop:byHand" : "stop");
        }

        @Override public void dropCleanup() { calls.add("drop"); }

        @Override public boolean ensureTypingKeyboard() {
            inAppShown = inAppEnabled;
            return inAppEnabled;
        }

        @Override public void showSystemKeyboard() { systemShown = true; }

        @Override public void releaseSystemKeyboard() { systemReleased = systemShown; }

        @Override public void claimTyping(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor) {
            slot = interceptor;
            if (interceptor != null) {
                claims++;
                lastClaimed = interceptor;
            } else {
                restores++;
            }
        }

        @Override public boolean isOnTypingKeyboard(float rawX, float rawY) { return onKeyboard; }
    }

    private static final class FakeCard implements VoiceCardEdit.Card {
        boolean onText;
        @Nullable String draft;
        int caret = -1;
        @Nullable String editedFrom;
        @Nullable String shown;
        boolean abandoned;
        int status;

        @Override public boolean isOnText(float rawX, float rawY) { return onText; }

        @Override public void showDraft(@NonNull VoiceEditModel model) {
            draft = model.text();
            caret = model.caretUtf16();
        }

        @Override public void endEdit(@Nullable String editedFrom, @NonNull String shown) {
            this.editedFrom = editedFrom;
            this.shown = shown;
        }

        @Override public void abandonEdit() { abandoned = true; }

        @Override public void showEdited(@NonNull String editedFrom, @NonNull String edited) {
            this.editedFrom = editedFrom;
            this.shown = edited;
        }

        @Override public void setStatus(int status) { this.status = status; }

        @Override public int offsetOnAdjacentLine(int caretUtf16, int direction) { return -1; }
    }
}
