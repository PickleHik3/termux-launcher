package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import juloo.keyboard2.KeyValue;

/** What each key does to the card editor's draft, and that every way out ends it exactly once. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class VoiceEditControllerTest {

    @Test
    public void inAppKeysTypeAtTheCaretAndEnterKeepsTheText() {
        Host host = new Host();
        VoiceEditController controller = new VoiceEditController();
        controller.begin("open page", 5, host);
        assertTrue(controller.interceptKeyValue(KeyValue.makeStringKey("the "), false, false, false));
        assertEquals("open the page", controller.model().text());
        assertTrue(controller.interceptKeyValue(KeyValue.getKeyByName("backspace"), false, false, false));
        assertEquals("open thepage", controller.model().text());
        assertTrue(controller.interceptKeyValue(KeyValue.getKeyByName("enter"), false, false, false));
        assertEquals(1, host.endings);
        assertEquals("open thepage", host.text);
        assertFalse(controller.isActive());
        // Nothing is claimed once it has ended: the keys go back to where they were going.
        assertFalse(controller.interceptKeyValue(KeyValue.makeStringKey("x"), false, false, false));
    }

    @Test
    public void hardwareArrowsMoveAndBackAndEscapeKeep() {
        for (int exit : new int[] {KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER}) {
            Host host = new Host();
            VoiceEditController controller = new VoiceEditController();
            controller.begin("ab", 2, host);
            assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, down(KeyEvent.KEYCODE_DPAD_LEFT)));
            assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_X, down(KeyEvent.KEYCODE_X)));
            assertEquals("axb", controller.model().text());
            assertTrue(controller.handleKeyDown(exit, down(exit)));
            assertEquals(1, host.endings);
            assertEquals("axb", host.text);
        }
    }

    @Test
    public void upAndDownAskTheViewForTheNextLine() {
        Host host = new Host();
        host.adjacent = 7;
        VoiceEditController controller = new VoiceEditController();
        controller.begin("first\nsecond", 2, host);
        controller.handleKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, down(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(1, host.lastDirection);
        assertEquals(7, controller.model().caretUtf16());
        host.adjacent = -1;
        controller.handleKeyDown(KeyEvent.KEYCODE_DPAD_UP, down(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(-1, host.lastDirection);
        assertEquals(7, controller.model().caretUtf16());
    }

    @Test
    public void systemImeTextTypesAndItsEnterKeeps() {
        Host host = new Host();
        VoiceEditController controller = new VoiceEditController();
        assertFalse(controller.handleCodePoint('a', false));
        controller.begin("", 0, host);
        assertTrue(controller.handleCodePoint('h', false));
        assertTrue(controller.handleCodePoint('i', false));
        // A chord is not text.
        assertTrue(controller.handleCodePoint('c', true));
        assertEquals("hi", controller.model().text());
        assertTrue(controller.handleCodePoint('\n', false));
        assertEquals("hi", host.text);
        assertFalse(controller.handleCodePoint('b', false));
    }

    @Test
    public void pasteGoesInOnOneLine() {
        Host host = new Host();
        VoiceEditController controller = new VoiceEditController(() -> "one\r\ntwo");
        controller.begin("", 0, host);
        controller.interceptKeyValue(KeyValue.getKeyByName("paste"), false, false, false);
        assertEquals("one two", controller.model().text());
    }

    @Test
    public void abandonEndsOnceWithoutText() {
        Host host = new Host();
        VoiceEditController controller = new VoiceEditController();
        controller.begin("text", 0, host);
        controller.abandon();
        controller.end();
        controller.abandon();
        assertEquals(1, host.endings);
        assertNull(host.text);
    }

    @Test
    public void aSecondBeginKeepsTheFirstEdit() {
        Host first = new Host();
        VoiceEditController controller = new VoiceEditController();
        controller.begin("one", 3, first);
        controller.handleCodePoint('!', false);
        controller.begin("two", 0, new Host());
        assertEquals(1, first.endings);
        assertEquals("one!", first.text);
    }

    @Test
    public void tapsMoveTheCaretAndLatePhrasesJoinTheEnd() {
        Host host = new Host();
        VoiceEditController controller = new VoiceEditController();
        controller.begin("open the", 8, host);
        controller.moveCaretTo(4);
        controller.appendPhrase(" page");
        controller.handleCodePoint('X', false);
        assertEquals("openX the page", controller.model().text());
        assertTrue(host.draws >= 4);
    }

    private static KeyEvent down(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    private static final class Host implements VoiceEditController.Host {
        int endings;
        int draws;
        @Nullable String text;
        int adjacent = -1;
        int lastDirection;

        @Override
        public void onDraftChanged(@NonNull VoiceEditModel model) {
            draws++;
        }

        @Override
        public int offsetOnAdjacentLine(int caretUtf16, int direction) {
            lastDirection = direction;
            return adjacent;
        }

        @Override
        public void onEditEnded(@Nullable String text) {
            endings++;
            this.text = text;
        }
    }
}
