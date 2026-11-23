package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Stop finds every display server of this package by the name its script gives it. */
public class X11ServerProcessesTest {

    @Test public void aServerTheLauncherStartedMatches() {
        assertTrue(X11ServerProcesses.isServer(
            "termux-x11 com.termux :0 -legacy-drawing -xstartup openbox", "com.termux"));
    }

    @Test public void aServerAShellStartedMatches() {
        assertTrue(X11ServerProcesses.isServer("termux-x11 com.termux :0", "com.termux"));
        assertTrue(X11ServerProcesses.isServer("termux-x11 com.termux", "com.termux"));
    }

    @Test public void anotherEditionsServerDoesNotMatch() {
        assertFalse(X11ServerProcesses.isServer("termux-x11 com.termux.nix :0", "com.termux"));
    }

    @Test public void theScriptAndOtherProcessesDoNotMatch() {
        assertFalse(X11ServerProcesses.isServer(
            "/data/data/com.termux/files/usr/bin/bash", "com.termux"));
        assertFalse(X11ServerProcesses.isServer("com.termux", "com.termux"));
        assertFalse(X11ServerProcesses.isServer("", "com.termux"));
        assertFalse(X11ServerProcesses.isServer(null, "com.termux"));
    }
}
