package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import juloo.keyboard2.KeyValue;

/**
 * Which keyboard values the Display place types into X and which it hands back to the launcher.
 *
 * <p>The space bar's corners carry {@code tool:} keys — window switching, the palette — and its
 * south swipe the keyboard's own layout switch. Neither is typing: swallowing them made those
 * gestures dead for as long as the display was up.
 */
public class X11KeyboardBridgeTest {

    @Test
    public void launcherToolsGoBackToTheHost() {
        assertTrue(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("tool:window.next")));
        assertTrue(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("tool:window.previous")));
        assertTrue(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("tool:app.command_palette")));
    }

    @Test
    public void keyboardEventsStayWithTheKeyboardExceptTheActionKey() {
        assertTrue(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("switch_backward")));
        assertTrue(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("config")));
        // Enter on a field is for the X client.
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("action")));
    }

    @Test
    public void typingIsTheDisplays() {
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("a")));
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("space")));
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("backspace")));
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("esc")));
        assertFalse(X11KeyboardBridge.isLauncherSide(KeyValue.getKeyByName("ctrl")));
    }

    @Test
    public void theEditKeysAreTheChordsADesktopAppExpects() {
        assertEquals(chord(KeyEvent.KEYCODE_C), press("copy"));
        assertEquals(chord(KeyEvent.KEYCODE_X), press("cut"));
        assertEquals(chord(KeyEvent.KEYCODE_A), press("selectAll"));
        assertEquals(chord(KeyEvent.KEYCODE_Z), press("undo"));
        assertEquals(chord(KeyEvent.KEYCODE_Y), press("redo"));
    }

    @Test
    public void pasteOffersAndroidsClipToXBeforeTheChord() {
        RecordingSink sink = new RecordingSink();
        assertTrue(bridge(sink).interceptKeyValue(KeyValue.getKeyByName("paste"),
            false, false, false));
        List<String> expected = new ArrayList<>();
        expected.add("clip");
        expected.addAll(chord(KeyEvent.KEYCODE_V));
        assertEquals(expected, sink.events);
    }

    @Test
    public void aHeldModifierRidesAlongWithTheChord() {
        RecordingSink sink = new RecordingSink();
        // Shift+copy is Ctrl+Shift+C, which is what an X terminal emulator copies with.
        bridge(sink).interceptKeyValue(KeyValue.getKeyByName("copy"), false, false, true);
        assertEquals(Arrays.asList(
            "down " + KeyEvent.KEYCODE_CTRL_LEFT,
            "down " + KeyEvent.KEYCODE_SHIFT_LEFT,
            "down " + KeyEvent.KEYCODE_C,
            "up " + KeyEvent.KEYCODE_C,
            "up " + KeyEvent.KEYCODE_SHIFT_LEFT,
            "up " + KeyEvent.KEYCODE_CTRL_LEFT), sink.events);
    }

    @Test
    public void pasteAsPlainTextTypesTheClipboard() {
        RecordingSink sink = new RecordingSink();
        sink.clipboard = "ls -la";
        assertTrue(bridge(sink).interceptKeyValue(KeyValue.getKeyByName("pasteAsPlainText"),
            false, false, false));
        assertEquals(Collections.singletonList("text ls -la"), sink.events);
    }

    @Test
    public void anEmptyClipboardTypesNothing() {
        RecordingSink sink = new RecordingSink();
        sink.clipboard = null;
        assertTrue(bridge(sink).interceptKeyValue(KeyValue.getKeyByName("pasteAsPlainText"),
            false, false, false));
        assertEquals(Collections.emptyList(), sink.events);
    }

    @Test
    public void selectionActionsStaySwallowed() {
        RecordingSink sink = new RecordingSink();
        assertTrue(bridge(sink).interceptKeyValue(KeyValue.getKeyByName("selection_cancel"),
            false, false, false));
        assertEquals(Collections.emptyList(), sink.events);
    }

    @Test
    public void withNoDisplayTheValueGoesOnToTheTerminal() {
        RecordingSink sink = new RecordingSink();
        sink.connected = false;
        assertFalse(bridge(sink).interceptKeyValue(KeyValue.getKeyByName("paste"),
            false, false, false));
        assertFalse("no page attached", X11KeyboardBridge.over(() -> null)
            .interceptKeyValue(KeyValue.getKeyByName("paste"), false, false, false));
        assertEquals(Collections.emptyList(), sink.events);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static X11KeyboardBridge bridge(@NonNull RecordingSink sink) {
        return X11KeyboardBridge.over(() -> sink);
    }

    /** The events one unmodified Ctrl chord leaves behind, in order. */
    private static List<String> chord(int keyCode) {
        return Arrays.asList(
            "down " + KeyEvent.KEYCODE_CTRL_LEFT,
            "down " + keyCode,
            "up " + keyCode,
            "up " + KeyEvent.KEYCODE_CTRL_LEFT);
    }

    private static List<String> press(@NonNull String keyName) {
        RecordingSink sink = new RecordingSink();
        assertTrue(keyName, bridge(sink).interceptKeyValue(KeyValue.getKeyByName(keyName),
            false, false, false));
        return sink.events;
    }

    private static final class RecordingSink implements X11KeyboardBridge.Sink {

        final List<String> events = new ArrayList<>();
        boolean connected = true;
        @Nullable String clipboard = "";

        @Override public void key(int keyCode, boolean down) {
            events.add((down ? "down " : "up ") + keyCode);
        }

        @Override public void text(@NonNull String text) {
            events.add("text " + text);
        }

        @Override public boolean connected() {
            return connected;
        }

        @Override public void refreshClipboard() {
            events.add("clip");
        }

        @Nullable @Override public String clipboardText() {
            return clipboard;
        }
    }
}
