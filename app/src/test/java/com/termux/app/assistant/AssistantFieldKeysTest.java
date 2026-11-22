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
import android.app.Application;
import org.robolectric.annotation.Config;

/** The launcher keyboard's keys edit the composer's field, and Enter sends. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
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

    /** The field rule on a real EditText: edit keys and Ctrl chords act on the selection. */
    @Test
    public void editKeysAndCtrlChordsActOnTheFieldsSelection() {
        List<String> copied = new ArrayList<>();
        AssistantFieldKeys keys = new AssistantFieldKeys(composer::field, f -> composer.submit(),
            new com.termux.app.terminal.ClipboardText() {
                @Override public String read() { return "pq"; }
                @Override public void write(@NonNull String text) { copied.add(text); }
            });
        composer.setText("hello world");
        composer.field().setSelection(0, 5);

        assertTrue(keys.interceptKeyValue(KeyValue.getKeyByName("copy"), false, false, false));
        assertEquals("hello", copied.get(0));
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('x'), true, false, false));
        assertEquals(" world", composer.text());
        assertEquals("hello", copied.get(1));

        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('a'), true, false, false));
        assertEquals(0, composer.field().getSelectionStart());
        assertEquals(6, composer.field().getSelectionEnd());
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('c'), true, false, false));
        assertEquals(" world", copied.get(2));
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('v'), true, false, false));
        assertEquals("pq", composer.text());

        // An unmapped chord and a tool key change nothing.
        assertTrue(keys.interceptKeyValue(KeyValue.makeCharKey('q'), true, false, false));
        assertTrue(keys.interceptKeyValue(
            KeyValue.makeLauncherToolKey("window.next", "»", 0), false, false, false));
        assertEquals("pq", composer.text());
        assertEquals(3, copied.size());
    }

    /**
     * The keys as the in-app keyboard really sends them, through its handler: a latched Ctrl turns
     * a letter into a key event ({@code KeyModifier}) before the field sees it. Typed "se", then
     * Ctrl+A and x, leaves "x"; copy, paste, cut and undo follow; nothing reaches the terminal.
     */
    @Test
    public void ctrlChordsStruckOnTheKeyboardEditTypedText() {
        List<String> copied = new ArrayList<>();
        AssistantFieldKeys keys = new AssistantFieldKeys(composer::field, f -> composer.submit(),
            new com.termux.app.terminal.ClipboardText() {
                @Override public String read() {
                    return copied.isEmpty() ? null : copied.get(copied.size() - 1);
                }
                @Override public void write(@NonNull String text) { copied.add(text); }
            });
        int[] terminalReads = {0};
        List<String> hostCalls = new ArrayList<>();
        com.termux.app.terminal.inappkeyboard.HostActions host =
            (com.termux.app.terminal.inappkeyboard.HostActions) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{com.termux.app.terminal.inappkeyboard.HostActions.class},
                (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (!method.getName().equals("onKeyboardModifiersChanged"))
                        hostCalls.add(method.getName());
                    return null;
                });
        com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler handler =
            new com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler(
                () -> { terminalReads[0]++; return null; },
                () -> { terminalReads[0]++; return null; },
                host, new android.os.Handler(android.os.Looper.getMainLooper()));
        handler.setKeyValueInterceptor(keys);
        composer.field().requestFocus();

        strike(handler, 's');
        strike(handler, 'e');
        assertEquals("se", composer.text());
        strike(handler, 'a', KeyValue.Modifier.CTRL);
        strike(handler, 'x');
        assertEquals("Ctrl+A, then a letter, replaces the text", "x", composer.text());

        strike(handler, 'a', KeyValue.Modifier.CTRL);
        strike(handler, 'c', KeyValue.Modifier.CTRL);
        assertEquals(List.of("x"), copied);
        strike(handler, 'v', KeyValue.Modifier.CTRL);
        assertEquals("pasting over the selection", "x", composer.text());
        strike(handler, 'a', KeyValue.Modifier.CTRL);
        strike(handler, 'x', KeyValue.Modifier.CTRL);
        assertEquals("", composer.text());
        assertEquals(List.of("x", "x"), copied);
        strike(handler, 'v', KeyValue.Modifier.CTRL);
        strike(handler, 'v', KeyValue.Modifier.CTRL);
        assertEquals("xx", composer.text());

        assertEquals("nothing reaches the terminal", 0, terminalReads[0]);
        assertTrue("nothing acts on the terminal: " + hostCalls, hostCalls.isEmpty());
    }

    /** A key tapped under latched modifiers, modified and delivered as {@code Pointers} does. */
    private static void strike(com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler handler,
                               char c, KeyValue.Modifier... modifiers) {
        juloo.keyboard2.Pointers.Modifiers held = juloo.keyboard2.Pointers.Modifiers.EMPTY;
        for (KeyValue.Modifier modifier : modifiers)
            held = held.with_extra_mod(KeyValue.makeInternalModifier(modifier));
        handler.key_up(juloo.keyboard2.KeyModifier.modify(KeyValue.makeCharKey(c), held, null), held);
    }
}
