package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DisplayStartWithTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final DisplayStartWith.Choice DESKTOP = DisplayStartWith.parse("desktop:debian", "");
    private static final DisplayStartWith.Choice COMMAND = DisplayStartWith.parse("command", "xterm");
    private static final DisplayStartWith.Choice NOTHING = DisplayStartWith.parse("", "xterm");

    @Test
    public void parseReadsEachKind() {
        assertEquals(DisplayStartWith.Kind.DESKTOP, DESKTOP.kind);
        assertEquals("debian", DESKTOP.distro);
        assertEquals(DisplayStartWith.Kind.COMMAND, COMMAND.kind);
        assertEquals("xterm", COMMAND.command);
        assertEquals(DisplayStartWith.Kind.NOTHING, NOTHING.kind);
    }

    @Test
    public void parseTreatsEmptyDesktopOrCommandAsNothing() {
        assertEquals(DisplayStartWith.Kind.NOTHING, DisplayStartWith.parse("desktop:", "").kind);
        assertEquals(DisplayStartWith.Kind.NOTHING, DisplayStartWith.parse("command", "  ").kind);
        assertEquals(DisplayStartWith.Kind.NOTHING, DisplayStartWith.parse(null, null).kind);
        assertEquals(DisplayStartWith.Kind.NOTHING, DisplayStartWith.parse("garbage", "x").kind);
        assertEquals("desktop:debian", DisplayStartWith.desktopValue("debian"));
    }

    @Test
    public void nothingChosenStartsNothingAndMarksNothing() {
        DisplayStartWith.Decision d = DisplayStartWith.decide(NOTHING, "10:5", null, false);
        assertEquals(DisplayStartWith.Action.NONE, d.action);
        assertFalse(d.mark);
    }

    @Test
    public void newServerStartsTheChoiceAndMarksIt() {
        DisplayStartWith.Decision desktop = DisplayStartWith.decide(DESKTOP, "10:5", null, false);
        assertEquals(DisplayStartWith.Action.START_DESKTOP, desktop.action);
        assertTrue(desktop.mark);
        DisplayStartWith.Decision command = DisplayStartWith.decide(COMMAND, "10:5", "9:1", false);
        assertEquals(DisplayStartWith.Action.START_COMMAND, command.action);
        assertTrue(command.mark);
    }

    @Test
    public void markedServerNeverStartsTwice() {
        assertEquals(DisplayStartWith.Action.NONE,
            DisplayStartWith.decide(DESKTOP, "10:5", "10:5", false).action);
        assertEquals(DisplayStartWith.Action.NONE,
            DisplayStartWith.decide(COMMAND, "10:5", "10:5", false).action);
    }

    @Test
    public void reusedPidWithANewStartTimeIsANewServer() {
        assertEquals(DisplayStartWith.Action.START_DESKTOP,
            DisplayStartWith.decide(DESKTOP, "10:900", "10:5", false).action);
    }

    @Test
    public void noServerFoundStartsNothing() {
        DisplayStartWith.Decision d = DisplayStartWith.decide(DESKTOP, null, null, false);
        assertEquals(DisplayStartWith.Action.NONE, d.action);
        assertFalse(d.mark);
    }

    @Test
    public void desktopAlreadyOnTheDisplayIsMarkedNotStarted() {
        DisplayStartWith.Decision d = DisplayStartWith.decide(DESKTOP, "10:5", null, true);
        assertEquals(DisplayStartWith.Action.NONE, d.action);
        assertTrue(d.mark);
        // A command is not a desktop: it still runs beside one.
        assertEquals(DisplayStartWith.Action.START_COMMAND,
            DisplayStartWith.decide(COMMAND, "10:5", null, true).action);
    }

    @Test
    public void distroLabelsReadAsNames() {
        assertEquals("Debian", DisplayStartWith.distroLabel("debian"));
        assertEquals("Arch Linux", DisplayStartWith.distroLabel("archlinux"));
        assertEquals("Ubuntu", DisplayStartWith.distroLabel("ubuntu"));
    }

    @Test
    public void onlyDistrosWithStartxfce4OfferADesktop() throws Exception {
        File debian = tmp.newFolder("debian", "rootfs");
        File ubuntu = tmp.newFolder("ubuntu", "rootfs");
        File bin = new File(debian, "usr/bin");
        assertTrue(bin.mkdirs());
        assertTrue(new File(bin, "startxfce4").createNewFile());
        List<ProotDistro.Container> containers = Arrays.asList(
            new ProotDistro.Container("debian", debian, "root", "/root"),
            new ProotDistro.Container("ubuntu", ubuntu, "root", "/root"));
        assertEquals(Arrays.asList("debian"), DisplayStartWith.distrosWithDesktop(containers));
    }

    @Test
    public void markerRoundTrips() {
        File marker = new File(tmp.getRoot(), "sub/marker");
        assertNull(DisplayStartWith.readMarker(marker));
        DisplayStartWith.writeMarker(marker, "10:5");
        assertEquals("10:5", DisplayStartWith.readMarker(marker));
    }

    @Test
    public void commandRunsInItsOwnShell() {
        LinuxAppCatalog.LinuxApp app = DisplayStartWith.commandApp("xterm & firefox");
        assertEquals("sh -c 'xterm & firefox'", app.command());
    }

    @Test
    public void startTimeIsReadPastANameWithSpaces() {
        String stat = "1234 (termux x11) S 1 1234 1234 0 -1 4194560 100 0 0 0 1 2 0 0 20 0 3 0 "
            + "987654 12345678 900";
        assertEquals("987654", X11ServerProcesses.startTimeOf(stat));
        assertNull(X11ServerProcesses.startTimeOf("garbage"));
        assertNull(X11ServerProcesses.startTimeOf(null));
    }
}
