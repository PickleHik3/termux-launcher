package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import com.termux.app.terminal.ClipboardText;

import juloo.keyboard2.KeyValue;

/** The in-app keyboard's PASTE / PASTE_PLAIN and the editing keys the drawer search still drops. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerSearchControllerTest {

    @Test public void pasteAndPasteAsPlainTextInsertTheClipboardTextSanitizedToOneLine() {
        AppDrawerSearchController search = activeSearch(() -> "one\r\ntwo");

        assertTrue(search.interceptKeyValue(
            KeyValue.getKeyByName("paste"), false, false, false));
        assertEquals("one two", search.query());

        assertTrue(search.interceptKeyValue(
            KeyValue.getKeyByName("pasteAsPlainText"), false, false, false));
        assertEquals("one twoone two", search.query());
    }

    /** Copy and cut take the whole query; select all makes the next letter replace it. */
    @Test public void copyCutAndSelectAllActOnTheQuery() {
        java.util.List<String> copied = new java.util.ArrayList<>();
        AppDrawerSearchController search = activeSearch(new ClipboardText() {
            @Override public String read() { return null; }
            @Override public void write(String text) { copied.add(text); }
        });
        search.replaceQuery("gr", 2);

        assertTrue(search.interceptKeyValue(KeyValue.getKeyByName("copy"), false, false, false));
        assertEquals("gr", search.query());
        assertEquals(java.util.Collections.singletonList("gr"), copied);

        assertTrue(
            search.interceptKeyValue(KeyValue.getKeyByName("selectAll"), false, false, false));
        assertTrue(search.interceptKeyValue(KeyValue.makeCharKey('f'), false, false, false));
        assertEquals("f", search.query());

        assertTrue(search.interceptKeyValue(KeyValue.getKeyByName("cut"), false, false, false));
        assertEquals("", search.query());
        assertEquals("f", copied.get(1));

        assertTrue(search.interceptKeyValue(KeyValue.getKeyByName("undo"), false, false, false));
        assertEquals("f", search.query());
    }

    /**
     * With the Android-keyboard search on, a stroke reaching the terminal's key channel was never
     * the text field's: it is typed into the query, not declined into the shell behind the drawer.
     */
    @Test public void theAndroidKeyboardSearchStillTypesAStrokeTheTerminalReceived() {
        AppDrawerSearchController search = activeSearch(() -> null);
        search.setTextFieldOwnsInput(true);

        assertTrue(search.handleKeyDown(KeyEvent.KEYCODE_F,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F)));
        assertTrue(search.handleKeyDown(KeyEvent.KEYCODE_F,
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F)));
        assertTrue(search.handleKeyDown(KeyEvent.KEYCODE_I,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I)));
        assertEquals("fi", search.query());

        assertTrue(search.handleKeyDown(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));
        assertEquals("f", search.query());
    }

    private static AppDrawerSearchController activeSearch(ClipboardText clipboardText) {
        AppDrawerSearchController search = new AppDrawerSearchController(clipboardText);
        search.setHost(new AppDrawerSearchController.Host() {
            @Override public boolean isSearchActive() { return true; }
            @Override public void onSearchCommitRequested() {}
            @Override public void onSearchDismissRequested() {}
        });
        return search;
    }
}
