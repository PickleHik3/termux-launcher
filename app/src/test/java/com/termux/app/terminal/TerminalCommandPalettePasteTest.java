package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import juloo.keyboard2.KeyValue;

/**
 * The palette's own paste key, in {@link TerminalCommandPaletteController}'s normal (list) mode.
 *
 * <p>The palette binds the activity's real layout through a {@link FakeCommandPaletteHost}
 * ({@link TerminalSheetPromptsTest} lays out the same activity for the sheet plane), and uses the
 * package-private constructor that injects a fake {@link ClipboardText} rather than the real
 * {@code ClipboardManager}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalCommandPalettePasteTest {

    @Test
    public void pasteAndPasteAsPlainTextInsertTheClipboardTextSanitizedToOneLine() {
        TermuxActivity activity = laidOutActivity();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(hostOver(activity), () -> "one\r\ntwo");
        palette.show();
        assertTrue(palette.isOpen());

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("paste"), false, false, false));
        assertEquals("one two", palette.query());

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("pasteAsPlainText"), false, false, false));
        assertEquals("one twoone two", palette.query());
    }

    /**
     * The field rule in the query: copy and cut take the whole query, select all makes the next
     * letter replace it, Ctrl+Z and Ctrl+Y undo and redo, and an unmapped chord or a tool key does
     * nothing.
     */
    @Test
    public void editKeysAndCtrlChordsActOnTheQuery() {
        TermuxActivity activity = laidOutActivity();
        java.util.List<String> copied = new java.util.ArrayList<>();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(hostOver(activity), new ClipboardText() {
                @Override public String read() { return "pq"; }
                @Override public void write(String text) { copied.add(text); }
            });
        palette.show();
        palette.interceptKeyValue(KeyValue.makeCharKey('g'), false, false, false);

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("copy"), false, false, false));
        assertEquals("g", copied.get(0));
        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("selectAll"), false, false, false));
        palette.interceptKeyValue(KeyValue.makeCharKey('s'), false, false, false);
        assertEquals("s", palette.query());

        assertTrue(palette.interceptKeyValue(KeyValue.makeCharKey('x'), true, false, false));
        assertEquals("", palette.query());
        assertEquals("s", copied.get(1));
        palette.interceptKeyValue(KeyValue.makeCharKey('v'), true, false, false);
        assertEquals("pq", palette.query());
        palette.interceptKeyValue(KeyValue.makeCharKey('z'), true, false, false);
        assertEquals("", palette.query());
        palette.interceptKeyValue(KeyValue.makeCharKey('y'), true, false, false);
        assertEquals("pq", palette.query());
        palette.interceptKeyValue(KeyValue.getKeyByName("cut"), false, false, false);
        palette.interceptKeyValue(KeyValue.getKeyByName("undo"), false, false, false);
        assertEquals("pq", palette.query());
        palette.interceptKeyValue(KeyValue.getKeyByName("redo"), false, false, false);
        assertEquals("", palette.query());
        palette.interceptKeyValue(KeyValue.getKeyByName("undo"), false, false, false);

        assertTrue(palette.interceptKeyValue(KeyValue.makeCharKey('q'), true, false, false));
        assertTrue(palette.interceptKeyValue(
            KeyValue.makeLauncherToolKey("window.next", "»", 0), false, false, false));
        assertEquals("pq", palette.query());
        assertTrue(palette.isOpen());
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    private static FakeCommandPaletteHost hostOver(TermuxActivity activity) {
        FakeCommandPaletteHost host = new FakeCommandPaletteHost(activity);
        host.viewRoot = activity.getWindow().getDecorView();
        return host;
    }
}
