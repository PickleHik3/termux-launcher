package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
