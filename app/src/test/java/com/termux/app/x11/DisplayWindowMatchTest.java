package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DisplayWindowMatchTest {

    private static X11WindowList.Window w(int id, String wmClass) {
        return new X11WindowList.Window(id, "t", wmClass);
    }

    @Test public void startupWmClassWinsOverTheExec() {
        List<X11WindowList.Window> windows = Arrays.asList(w(1, "firefox"), w(2, "Navigator"));
        assertEquals(2, DisplayWindowMatch.windowFor(windows, "navigator", "firefox", "x.desktop"));
    }

    @Test public void theExecProgramPastEnvAndAssignmentsIsTheClass() {
        List<X11WindowList.Window> windows = Arrays.asList(w(7, "Firefox"));
        assertEquals(7, DisplayWindowMatch.windowFor(windows, "", "env FOO=1 /usr/bin/firefox", "a.desktop"));
    }

    @Test public void theDesktopIdsLastSegmentIsTheLastResort() {
        List<X11WindowList.Window> windows = Arrays.asList(w(3, "mousepad"));
        assertEquals(3, DisplayWindowMatch.windowFor(windows, "", "", "org.xfce.mousepad.desktop"));
    }

    @Test public void theLastMatchIsPreferred() {
        List<X11WindowList.Window> windows = Arrays.asList(w(1, "mousepad"), w(2, "other"), w(3, "mousepad"));
        assertEquals(3, DisplayWindowMatch.windowFor(windows, "mousepad", "", ""));
    }

    @Test public void noMatchIsMinusOne() {
        assertEquals(-1, DisplayWindowMatch.windowFor(Arrays.asList(w(1, "xterm")), "", "mousepad", "mousepad.desktop"));
    }

    @Test public void anEmptyClassIsNeverMatched() {
        assertEquals(-1, DisplayWindowMatch.windowFor(Arrays.asList(w(1, "")), "", "", ""));
    }
}
