package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class X11StartFailureTest {
    private static final String GENERIC = "The display stopped as it started.";

    @Test public void picksTheLastErrorLineAndSkipsStackFrames() {
        String err = "Starting\njava.lang.UnsatisfiedLinkError: Failed to load native library"
            + " for CmdEntryPoint.start\n\tat com.termux.x11.Loader.main(Loader.java:1)\n\n";
        assertEquals("java.lang.UnsatisfiedLinkError: Failed to load native library for"
            + " CmdEntryPoint.start", X11StartFailure.reason(err, GENERIC));
    }

    @Test public void readsTheSocketLine() {
        String err = "Cannot establish any listening sockets - Make sure an X server isn't"
            + " already running\n";
        assertEquals("Cannot establish any listening sockets - Make sure an X server isn't"
            + " already running", X11StartFailure.reason(err, GENERIC));
    }

    @Test public void fallsBackToTheGenericLine() {
        assertEquals(GENERIC, X11StartFailure.reason("", GENERIC));
        assertEquals(GENERIC, X11StartFailure.reason(null, GENERIC));
        assertEquals(GENERIC, X11StartFailure.reason("starting\nbye\n", GENERIC));
    }
}
