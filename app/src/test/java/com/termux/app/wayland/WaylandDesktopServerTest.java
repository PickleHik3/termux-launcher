package com.termux.app.wayland;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The policy of a desktop's own X server under Wayland Display. */
public class WaylandDesktopServerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Set<Integer> set(Integer... n) {
        return new HashSet<>(Arrays.asList(n));
    }

    @Test public void theFirstFreeNumberAfterX11DisplaysIsTaken() {
        assertEquals(1, WaylandDesktopServer.pickDisplay(set(), -1));
        // X11 Display's :0 is never taken, even when nothing holds it.
        assertEquals(1, WaylandDesktopServer.pickDisplay(set(), 0));
    }

    @Test public void theHostsOwnXwaylandAndLockedNumbersAreSkipped() {
        assertEquals(2, WaylandDesktopServer.pickDisplay(set(), 1));
        assertEquals(3, WaylandDesktopServer.pickDisplay(set(0, 2), 1));
        assertEquals(4, WaylandDesktopServer.pickDisplay(set(1, 2, 3), -1));
    }

    @Test public void noFreeNumberIsNoDisplay() {
        Set<Integer> all = new HashSet<>();
        for (int n = 0; n <= WaylandDesktopServer.LAST_DISPLAY; n++) all.add(n);
        assertEquals(WaylandDesktopServer.NO_DISPLAY, WaylandDesktopServer.pickDisplay(all, -1));
    }

    @Test public void locksAndSocketsNameTheirDisplay() {
        assertEquals(3, WaylandDesktopServer.lockDisplay(".X3-lock"));
        assertEquals(12, WaylandDesktopServer.lockDisplay(".X12-lock"));
        assertEquals(-1, WaylandDesktopServer.lockDisplay(".X-lock"));
        assertEquals(-1, WaylandDesktopServer.lockDisplay(".Xa-lock"));
        assertEquals(-1, WaylandDesktopServer.lockDisplay(".X11-unix"));
        assertEquals(4, WaylandDesktopServer.socketDisplay("X4"));
        assertEquals(-1, WaylandDesktopServer.socketDisplay("X"));
        assertEquals(-1, WaylandDesktopServer.socketDisplay("X4.tmp"));
    }

    @Test public void takenDisplaysAreReadFromTheLocksAndTheSocketDirectory() throws Exception {
        File dir = tmp.getRoot();
        assertTrue(new File(dir, ".X1-lock").createNewFile());
        assertTrue(new File(dir, "termux-x11-session.log").createNewFile());
        File sockets = new File(dir, ".X11-unix");
        assertTrue(sockets.mkdir());
        assertTrue(new File(sockets, "X2").createNewFile());
        assertEquals(set(1, 2), WaylandDesktopServer.takenDisplays(dir));
        assertEquals(3, WaylandDesktopServer.pickDisplay(WaylandDesktopServer.takenDisplays(dir), -1));
        assertEquals(set(), WaylandDesktopServer.takenDisplays(new File(dir, "missing")));
    }

    @Test public void theStartSizeIsTheLogicalSizeTheHostConfigures() {
        assertArrayEquals(new int[] {432, 965}, WaylandDesktopServer.logicalSize(1080, 2412, 2.5f));
        assertArrayEquals(new int[] {1080, 2412}, WaylandDesktopServer.logicalSize(1080, 2412, 0f));
        assertArrayEquals(new int[] {1, 1}, WaylandDesktopServer.logicalSize(0, 0, 2f));
    }

    @Test public void theServerIsRootfulAtTheWindowsPixelsAndNeverResets() {
        // -hidpi: Xwayland takes the window's fractional scale, so its root is the window's
        // pixels drawn 1:1 rather than the logical size magnified.
        assertEquals(Arrays.asList(":3", "-geometry", "432x965", "-hidpi", "-noreset"),
            WaylandDesktopServer.serverArgs(3, 432, 965));
        assertEquals(Arrays.asList(":3", "-hidpi", "-noreset"), WaylandDesktopServer.serverArgs(3, 0, 965));
        assertFalse(WaylandDesktopServer.serverArgs(3, 432, 965).contains("-rootless"));
    }

    @Test public void theDesktopIsToldItsScaleTheWayTheSharedServerTellsApps() {
        // As xscale.rs: widgets at the whole part, text at the full scale, Xft.dpi 96 x scale.
        assertEquals(Arrays.asList("GDK_SCALE=2", "GDK_DPI_SCALE=1.25", "XCURSOR_SIZE=60"),
            WaylandDesktopServer.scaleEnv(2.5f));
        assertEquals(Arrays.asList("GDK_SCALE=2", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=48"),
            WaylandDesktopServer.scaleEnv(2f));
        assertEquals(Arrays.asList("GDK_SCALE=1", "GDK_DPI_SCALE=1.75", "XCURSOR_SIZE=42"),
            WaylandDesktopServer.scaleEnv(1.75f));
        assertEquals(Arrays.asList("GDK_SCALE=1", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=24"),
            WaylandDesktopServer.scaleEnv(Float.NaN));
        assertEquals(240, WaylandDesktopServer.xftDpi(2.5f));
        assertEquals(144, WaylandDesktopServer.xftDpi(1.5f));
        assertEquals(96, WaylandDesktopServer.xftDpi(0f));
    }

    @Test public void theStartBlockStartsWaitsWatchesAndKeepsWaylandOutOfTheDesktop() {
        String block = WaylandDesktopServer.startBlock("/p/bin/Xwayland", "/p/tmp/tl-wayland/wayland-0", 3, 432, 965, 2.5f);
        assertTrue(block, block.contains("WAYLAND_DISPLAY='/p/tmp/tl-wayland/wayland-0' '/p/bin/Xwayland'"
            + " :3 -geometry 432x965 -hidpi -noreset -displayfd 3 3>\"$tl_desktop_ready\" &\n"));
        // Xft.dpi on the desktop's own server, once it answered, only where xrdb is installed.
        assertTrue(block, block.contains("if command -v xrdb >/dev/null 2>&1; then\n"
            + "echo 'Xft.dpi: 240' | DISPLAY=:3 xrdb -nocpp -merge\nfi\n"));
        assertTrue(block.indexOf("exit " + WaylandDesktopServer.EXIT_NO_SCREEN) < block.indexOf("xrdb"));
        assertTrue(block.contains("tl_desktop_ready='" + WaylandDesktopServer.readyPath(3) + "'\n"));
        // The wait is bounded and ends early when the server exits.
        assertTrue(block.contains("kill -0 \"$tl_desktop_x\" 2>/dev/null && [ \"$tl_desktop_n\" -lt "
            + WaylandDesktopServer.READY_STEPS + " ]"));
        assertTrue(block.contains("exit " + WaylandDesktopServer.EXIT_NO_SCREEN + "\n"));
        // The watcher ends the server once the session (which the script becomes) is gone.
        assertTrue(block.contains("(while kill -0 $$ 2>/dev/null && kill -0 \"$tl_desktop_x\" 2>/dev/null; do sleep "
            + WaylandDesktopServer.WATCH_PERIOD_S + "; done; kill \"$tl_desktop_x\" 2>/dev/null) &\n"));
        // The watcher starts only after the server answered, and the desktop sees no Wayland.
        assertTrue(block.indexOf("exit " + WaylandDesktopServer.EXIT_NO_SCREEN) < block.indexOf("(while"));
        assertTrue(block.endsWith("unset WAYLAND_DISPLAY GDK_BACKEND QT_QPA_PLATFORM tl_desktop_ready tl_desktop_n\n"));
        assertFalse(block.contains("export WAYLAND_DISPLAY"));
    }

    @Test public void theServersWindowIsNamedAfterItsDesktop() {
        Map<Integer, String> names = new HashMap<>();
        names.put(3, "Xfce Session");
        assertEquals("Xfce Session", WaylandDesktopServer.tabTitle("Xwayland on :3", names));
        assertEquals("Xfce Session", WaylandDesktopServer.tabTitle(
            "Xwayland on :3 - ([ctrl]+[shift] grabs mouse and keyboard)", names));
        // Another server's window, or any other title, is left as it is.
        assertEquals("Xwayland on :4", WaylandDesktopServer.tabTitle("Xwayland on :4", names));
        assertEquals("Xwayland on :", WaylandDesktopServer.tabTitle("Xwayland on :", names));
        assertEquals("gedit", WaylandDesktopServer.tabTitle("gedit", names));
        assertEquals(null, WaylandDesktopServer.tabTitle(null, Collections.emptyMap()));
    }

    @Test public void aForgottenDesktopNoLongerNamesTheWindow() {
        WaylandDesktopServer.named(7, "LXQt Desktop");
        assertEquals("LXQt Desktop", WaylandDesktopServer.tabTitle("Xwayland on :7"));
        assertTrue(WaylandDesktopServer.isDesktopWindow("Xwayland on :7"));
        assertFalse(WaylandDesktopServer.isDesktopWindow("Xwayland on :8"));
        assertFalse(WaylandDesktopServer.isDesktopWindow(null));
        WaylandDesktopServer.forget(7);
        assertEquals("Xwayland on :7", WaylandDesktopServer.tabTitle("Xwayland on :7"));
        assertFalse(WaylandDesktopServer.isDesktopWindow("Xwayland on :7"));
    }
}
