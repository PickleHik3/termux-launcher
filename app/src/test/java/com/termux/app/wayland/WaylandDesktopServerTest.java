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

    @Test public void theServerIsRootfulAtTheWindowsLogicalSizeAndNeverResets() {
        // No -hidpi: the root is the logical size the launcher configures (hostScale), which is
        // the view's pixels x factor / stop, never the view magnified.
        assertEquals(Arrays.asList(":3", "-geometry", "432x965", "-noreset"),
            WaylandDesktopServer.serverArgs(3, 432, 965));
        assertEquals(Arrays.asList(":3", "-noreset"), WaylandDesktopServer.serverArgs(3, 0, 965));
        assertFalse(WaylandDesktopServer.serverArgs(3, 432, 965).contains("-rootless"));
        assertFalse(WaylandDesktopServer.serverArgs(3, 432, 965).contains("-hidpi"));
    }

    @Test public void everyStopDrawsAtTheWholeFactorAtOrAboveIt() {
        // GTK and Qt scale widgets by whole factors only: a stop between two draws at the upper
        // one, so stepping 200% -> 175% never halves the panel (pong, 2026-11-26).
        assertEquals(1, WaylandDesktopServer.drawFactor(1f));
        assertEquals(2, WaylandDesktopServer.drawFactor(1.25f));
        assertEquals(2, WaylandDesktopServer.drawFactor(1.5f));
        assertEquals(2, WaylandDesktopServer.drawFactor(1.75f));
        assertEquals(2, WaylandDesktopServer.drawFactor(2f));
        assertEquals(3, WaylandDesktopServer.drawFactor(2.5f));
        assertEquals(1, WaylandDesktopServer.drawFactor(Float.NaN));
        assertEquals(1, WaylandDesktopServer.drawFactor(0f));
    }

    @Test public void theWindowShowsTheFactorScaledDownToTheStop() {
        // hostScale = stop / factor: whole stops 1:1, the rest in (0.5, 1), all in 120ths.
        assertEquals(1f, WaylandDesktopServer.hostScale(1f), 0f);
        assertEquals(0.625f, WaylandDesktopServer.hostScale(1.25f), 0f);
        assertEquals(0.75f, WaylandDesktopServer.hostScale(1.5f), 0f);
        assertEquals(0.875f, WaylandDesktopServer.hostScale(1.75f), 0f);
        assertEquals(1f, WaylandDesktopServer.hostScale(2f), 0f);
        assertEquals(2.5f / 3f, WaylandDesktopServer.hostScale(2.5f), 1e-6f);
        assertEquals(1f, WaylandDesktopServer.hostScale(Float.NaN), 0f);
        for (int step : com.termux.app.x11.DisplayScaleSteps.STEPS) {
            float h = WaylandDesktopServer.hostScale(step / 100f) * 120f;
            assertEquals("stop " + step, Math.round(h), h, 1e-3f);
        }
    }

    @Test public void theRootIsTheViewAtTheFactorAndShowsAtTheStop() {
        // Pong's desktop view, 1038x1328 px: the root (the logical size at hostScale) drawn at
        // the factor, shown in the view, is the stop. 200% stays the view's own pixels.
        int[] views = {1038, 1328};
        assertArrayEquals(views, WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(2f)));
        assertArrayEquals(views, WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(1f)));
        assertArrayEquals(new int[] {1186, 1518},
            WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(1.75f)));
        assertArrayEquals(new int[] {1384, 1771},
            WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(1.5f)));
        assertArrayEquals(new int[] {1661, 2125},
            WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(1.25f)));
        assertArrayEquals(new int[] {1246, 1594},
            WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(2.5f)));
        for (int step : com.termux.app.x11.DisplayScaleSteps.STEPS) {
            float stop = step / 100f;
            int root = WaylandDesktopServer.logicalSize(1038, 1328, WaylandDesktopServer.hostScale(stop))[0];
            // One root pixel per 1/factor desktop point, the root fitted to the view's 1038 px.
            float shown = WaylandDesktopServer.drawFactor(stop) * 1038f / root;
            assertEquals("stop " + step, stop, shown, 0.002f);
        }
    }

    @Test public void theDesktopIsToldItsFactorTheWayADenseMonitorIs() {
        // Widgets and text at the factor; the view scales the rest. Xft.dpi 96 x factor.
        assertEquals(Arrays.asList("GDK_SCALE=3", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=72"),
            WaylandDesktopServer.scaleEnv(2.5f));
        assertEquals(Arrays.asList("GDK_SCALE=2", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=48"),
            WaylandDesktopServer.scaleEnv(2f));
        assertEquals(Arrays.asList("GDK_SCALE=2", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=48"),
            WaylandDesktopServer.scaleEnv(1.75f));
        assertEquals(Arrays.asList("GDK_SCALE=1", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=24"),
            WaylandDesktopServer.scaleEnv(1f));
        assertEquals(Arrays.asList("GDK_SCALE=1", "GDK_DPI_SCALE=1", "XCURSOR_SIZE=24"),
            WaylandDesktopServer.scaleEnv(Float.NaN));
        assertEquals(288, WaylandDesktopServer.xftDpi(2.5f));
        assertEquals(192, WaylandDesktopServer.xftDpi(1.5f));
        assertEquals(192, WaylandDesktopServer.xftDpi(2f));
        assertEquals(96, WaylandDesktopServer.xftDpi(0f));
    }

    @Test public void aRootfulXwaylandsWindowIsADesktopByItsTitleAlone() {
        assertTrue(WaylandDesktopServer.isRootfulTitle("Xwayland on :2"));
        assertTrue(WaylandDesktopServer.isRootfulTitle("Xwayland on :12 - ([ctrl]+[shift] grabs mouse and keyboard)"));
        assertFalse(WaylandDesktopServer.isRootfulTitle("Xwayland on :"));
        assertFalse(WaylandDesktopServer.isRootfulTitle("Xwayland on :x"));
        assertFalse(WaylandDesktopServer.isRootfulTitle("gedit"));
        assertFalse(WaylandDesktopServer.isRootfulTitle(""));
        assertFalse(WaylandDesktopServer.isRootfulTitle(null));
    }

    @Test public void theStartBlockStartsWaitsWatchesAndKeepsWaylandOutOfTheDesktop() {
        String block = WaylandDesktopServer.startBlock("/p/bin/Xwayland", "/p/tmp/tl-wayland/wayland-0", 3, 432, 965, 2.5f);
        assertTrue(block, block.contains("WAYLAND_DISPLAY='/p/tmp/tl-wayland/wayland-0' '/p/bin/Xwayland'"
            + " :3 -geometry 432x965 -noreset -displayfd 3 3>\"$tl_desktop_ready\" &\n"));
        // Xft.dpi on the desktop's own server, once it answered, only where xrdb is installed.
        assertTrue(block, block.contains("if command -v xrdb >/dev/null 2>&1; then\n"
            + "echo 'Xft.dpi: 288' | DISPLAY=:3 xrdb -nocpp -merge\nfi\n"));
        assertTrue(block.indexOf("exit " + WaylandDesktopServer.EXIT_NO_SCREEN) < block.indexOf("xrdb"));
        assertTrue(block.contains("tl_desktop_ready='" + WaylandDesktopServer.readyPath(3) + "'\n"));
        // The wait is bounded and ends early when the server exits.
        assertTrue(block.contains("kill -0 \"$tl_desktop_x\" 2>/dev/null && [ \"$tl_desktop_n\" -lt "
            + WaylandDesktopServer.READY_STEPS + " ]"));
        assertTrue(block.contains("exit " + WaylandDesktopServer.EXIT_NO_SCREEN + "\n"));
        // The watcher ends the server once the session (which the script becomes) is gone, and
        // the session once the server is gone: closing the desktop's window ends Xwayland, which
        // the desktop never reaps, so a zombie counts as gone (#391).
        assertTrue(block, block.contains("tl_live() { { read -r tl_s < \"/proc/$1/stat\"; } 2>/dev/null || return 1;"
            + " case \"$tl_s\" in *') Z '*) return 1;; esac; }\n"));
        assertTrue(block, block.contains("(while tl_live $$ && tl_live \"$tl_desktop_x\"; do sleep "
            + WaylandDesktopServer.WATCH_PERIOD_S + "; done\n"
            + "kill \"$tl_desktop_x\" 2>/dev/null\n"
            + "if tl_live $$; then\n"
            + "kill $$ 2>/dev/null\n"));
        assertTrue("insisted on after a bounded wait", block.contains("kill -9 $$ 2>/dev/null\n"));
        // The watcher starts only after the server answered.
        assertTrue(block.indexOf("exit " + WaylandDesktopServer.EXIT_NO_SCREEN) < block.indexOf("(while"));
        assertFalse(block.contains("export WAYLAND_DISPLAY='"));
    }

    @Test public void insideTheDesktopEveryToolkitDrawsOnItsXServer() {
        // #391: with WAYLAND_DISPLAY merely unset, GTK found $XDG_RUNTIME_DIR/wayland-0 and the
        // panel and notifyd opened as Linux windows in the row. GTK and Qt are pinned to X11, and
        // WAYLAND_DISPLAY names a socket that does not exist, so no Wayland client inside the
        // desktop can fall back to wayland-0 (XDG_RUNTIME_DIR stays the shells' own).
        String block = WaylandDesktopServer.startBlock("/p/bin/Xwayland", "/p/tmp/wayland-0", 3, 432, 965, 2f);
        assertTrue(block, block.endsWith("export GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb WAYLAND_DISPLAY="
            + WaylandDesktopServer.NO_WAYLAND_DISPLAY + "\n"
            + "unset tl_desktop_ready tl_desktop_n\n"));
        assertFalse(WaylandDesktopServer.NO_WAYLAND_DISPLAY.startsWith("wayland-"));
        assertFalse(block.contains("XDG_RUNTIME_DIR"));
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

    @Test public void xfconfCarriesTheLaunchersScaleAndOnlyWhenItIsInstalled() {
        String w = WaylandDesktopServer.xfconfWrites(2.5f);
        assertTrue(w.startsWith("if command -v xfconf-query >/dev/null 2>&1; then\n"));
        // GTK draws everything, text included, at the factor; the view scales the rest.
        assertTrue(w.contains("-c xsettings -n -t int -p /Xft/DPI -s 96 "));
        assertTrue(w.contains("-c xsettings -n -t int -p /Gdk/WindowScalingFactor -s 3 "));
        assertTrue(w.endsWith("fi\n"));
        // Idempotent: the same scale gives the same text, and an unusable one is scale 1.
        assertEquals(w, WaylandDesktopServer.xfconfWrites(2.5f));
        assertTrue(WaylandDesktopServer.xfconfWrites(Float.NaN).contains("/Xft/DPI -s 96 "));
    }

    @Test public void xfconfNeverHalvesTheDesktopBetweenWholeStops() {
        // 175% was factor 1 at DPI 168: text right, panel and icons at half (pong, 2026-11-26).
        for (float stop : new float[] {1.25f, 1.5f, 1.75f, 2f}) {
            String w = WaylandDesktopServer.xfconfWrites(stop);
            assertTrue(w, w.contains("/Gdk/WindowScalingFactor -s 2 "));
            assertTrue(w, w.contains("/Xft/DPI -s 96 "));
        }
        assertTrue(WaylandDesktopServer.xfconfWrites(1f).contains("/Gdk/WindowScalingFactor -s 1 "));
    }

    @Test public void theStartBlockForXfceNeedsABusAndRecordsIt() {
        String b = WaylandDesktopServer.desktopScaleBlock(WaylandDesktopServer.KIND_XFCE, 3f, 4);
        assertTrue(b.startsWith("if [ -n \"$DBUS_SESSION_BUS_ADDRESS\" ]; then\n"));
        assertTrue(b.contains("echo \"$DBUS_SESSION_BUS_ADDRESS\" > \"${TMPDIR:-/tmp}/tl-desktop-x4.bus\"\n"));
        assertTrue(b.contains("-p /Xft/DPI -s 96 "));
        assertEquals("", WaylandDesktopServer.desktopScaleBlock("lxqt", 3f, 4));
        assertEquals("", WaylandDesktopServer.desktopScaleBlock(null, 3f, 4));
    }

    @Test public void aNewStopReachesTheRunningDesktopOnItsRecordedBus() {
        String s = WaylandDesktopServer.liveScaleScript(WaylandDesktopServer.KIND_XFCE, 2f, 4);
        assertTrue(s.startsWith("tl_bus=$(cat \"${TMPDIR:-/tmp}/tl-desktop-x4.bus\" 2>/dev/null)\n"));
        assertTrue(s.contains("[ -n \"$tl_bus\" ] || exit 0\n"));
        assertTrue(s.indexOf("export DBUS_SESSION_BUS_ADDRESS=\"$tl_bus\"") < s.indexOf("xfconf-query -c"));
        assertTrue(s.contains("-p /Xft/DPI -s 96 "));
        assertEquals("", WaylandDesktopServer.liveScaleScript(null, 2f, 4));
    }

    @Test
    public void anXfceDesktopTakesItsScaleFromXfconfNotTheExports() {
        // GTK would take GDK_SCALE and the xfconf DPI both: text twice too big (pong, 2026-11-25).
        assertEquals(java.util.Arrays.asList("XCURSOR_SIZE=72"),
            WaylandDesktopServer.scaleEnv(2.5f, WaylandDesktopServer.KIND_XFCE));
        assertEquals(WaylandDesktopServer.scaleEnv(2.5f), WaylandDesktopServer.scaleEnv(2.5f, "mate"));
        assertTrue(WaylandDesktopServer.scaleEnv(2.5f, null).contains("GDK_SCALE=3"));
    }
}
