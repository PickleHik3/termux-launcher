package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.KeyValue;

/** The launcher keyboard's keys edit the composer's field, and Enter sends. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantFieldKeysTest {

    private AssistantComposer composer;
    private AssistantFieldKeys keys;
    private final List<String> sent = new ArrayList<>();
    private String clipboard = null;

    @Before
    public void setUp() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        composer = new AssistantComposer(context, AssistantUi.Palette.read(new View(context)));
        composer.setListener(new AssistantComposer.Listener() {
            @Override public void onSend(@NonNull String text) { sent.add(text); }
            @Override public void onStop() { sent.add("stop"); }
            @Override public void onMic() { }
            @Override public void onCancelDictation() { }
            @Override public void onDoneDictation() { }
            @Override public void onFieldFocused(boolean focused) { }
        });
        keys = new AssistantFieldKeys(composer::field, f -> composer.submit(), () -> clipboard);
    }

    private boolean press(KeyValue v) {
        return keys.interceptKeyValue(v, false, false, false);
    }

    private void type(String s) {
        for (char c : s.toCharArray()) press(KeyValue.makeCharKey(c));
    }

    private void caret(int pos) {
        composer.field().setSelection(pos);
    }

    @Test
    public void charsInsertAtTheCaret() {
        composer.setText("ac");
        caret(1);
        type("b");
        assertEquals("abc", composer.text());
        assertEquals(2, composer.field().getSelectionStart());
    }

    @Test
    public void aTypedCharReplacesTheSelection() {
        composer.setText("hello");
        composer.field().setSelection(1, 4);
        type("a");
        assertEquals("hao", composer.text());
    }

    @Test
    public void spaceAndStringsInsert() {
        type("a");
        press(KeyValue.getKeyByName("space"));
        press(KeyValue.makeStringKey("bc"));
        assertEquals("a bc", composer.text());
    }

    @Test
    public void ctrlAndAltCharsAreSwallowedNotInserted() {
        composer.setText("x");
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('c'), true, false, false));
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('c'), false, true, false));
        assertEquals("x", composer.text());
    }

    @Test
    public void backspaceDeletesTheSelectionOrTheCharBeforeTheCaret() {
        composer.setText("abcd");
        press(KeyValue.getKeyByName("backspace"));
        assertEquals("abc", composer.text());
        composer.field().setSelection(0, 2);
        press(KeyValue.getKeyByName("backspace"));
        assertEquals("c", composer.text());
        caret(0);
        press(KeyValue.getKeyByName("backspace"));
        assertEquals("c", composer.text());
    }

    @Test
    public void backspaceTakesAWholeSurrogatePair() {
        composer.setText("a😀");
        press(KeyValue.getKeyByName("backspace"));
        assertEquals("a", composer.text());
    }

    @Test
    public void forwardDeleteRemovesTheCharAfterTheCaret() {
        composer.setText("abc");
        caret(1);
        press(KeyValue.getKeyByName("delete"));
        assertEquals("ac", composer.text());
    }

    @Test
    public void cursorSliderAndArrowsMoveTheCaret() {
        composer.setText("abcdef");
        press(KeyValue.sliderKey(KeyValue.Slider.Cursor_left, 3));
        assertEquals(3, composer.field().getSelectionStart());
        press(KeyValue.sliderKey(KeyValue.Slider.Cursor_right, 1));
        assertEquals(4, composer.field().getSelectionStart());
        press(KeyValue.keyeventKey("l", KeyEvent.KEYCODE_DPAD_LEFT, 0));
        assertEquals(3, composer.field().getSelectionStart());
        press(KeyValue.keyeventKey("r", KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertEquals(4, composer.field().getSelectionStart());
    }

    @Test
    public void theCaretNeverStopsInsideASurrogatePair() {
        composer.setText("😀b");
        caret(0);
        press(KeyValue.keyeventKey("r", KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertEquals(2, composer.field().getSelectionStart());
    }

    @Test
    public void pastePutsPlainTextAtTheCaretAndKeepsNewlines() {
        composer.setText("ad");
        caret(1);
        clipboard = "b\r\nc";
        press(KeyValue.getKeyByName("paste"));
        assertEquals("ab\ncd", composer.text());
    }

    @Test
    public void enterSendsANonBlankFieldAndShiftEnterInsertsANewline() {
        composer.setText("hi");
        press(KeyValue.keyeventKey("e", KeyEvent.KEYCODE_ENTER, 0));
        assertEquals(List.of("hi"), sent);
        assertTrue(keys.interceptKeyValue(
            KeyValue.keyeventKey("e", KeyEvent.KEYCODE_ENTER, 0), false, false, true));
        assertEquals("hi\n", composer.text());
        assertEquals(1, sent.size());
    }

    @Test
    public void actionKeyBehavesAsEnter() {
        composer.setText("go");
        press(KeyValue.getKeyByName("action"));
        assertEquals(List.of("go"), sent);
    }

    @Test
    public void enterOnABlankFieldOrWhileGeneratingDoesNothing() {
        composer.setText("  ");
        press(KeyValue.keyeventKey("e", KeyEvent.KEYCODE_ENTER, 0));
        composer.setText("hi");
        composer.setGenerating(true);
        press(KeyValue.keyeventKey("e", KeyEvent.KEYCODE_ENTER, 0));
        assertTrue(sent.isEmpty());
        assertEquals("hi", composer.text());
    }

    @Test
    public void unknownKeysAreSwallowed() {
        assertTrue(press(KeyValue.keyeventKey("f", KeyEvent.KEYCODE_F5, 0)));
        assertTrue(press(KeyValue.getKeyByName("esc")));
    }
}
