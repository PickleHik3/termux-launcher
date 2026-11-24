package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.X11GpuProbe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class LinuxWindowsEnvTest {

    private static final String TMP = "/data/data/pkg/files/usr/tmp";
    private static final String SOCKET = TMP + "/wayland-0";

    @Test
    public void theBareNameOnlyWhenTheRuntimeDirectoryIsTheSocketsOwn() {
        assertEquals("wayland-0", LinuxWindowsEnv.waylandDisplay(TMP, "wayland-0", TMP));
        assertEquals("wayland-0", LinuxWindowsEnv.waylandDisplay(TMP + "/", "wayland-0", TMP + "//"));
        // No runtime directory (Termux's default), or another one: the path, which libwayland
        // takes without joining it to anything.
        assertEquals(SOCKET, LinuxWindowsEnv.waylandDisplay(TMP, "wayland-0", null));
        assertEquals(SOCKET, LinuxWindowsEnv.waylandDisplay(TMP, "wayland-0", ""));
        assertEquals(SOCKET, LinuxWindowsEnv.waylandDisplay(TMP, "wayland-0", TMP + "/runtime"));
        assertEquals(SOCKET, LinuxWindowsEnv.waylandDisplay(TMP, "wayland-0", "/tmp"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aRelativeSocketDirIsRefused() {
        LinuxWindowsEnv.waylandDisplay("tmp", "wayland-0", null);
    }

    @Test
    public void aSessionGetsNothingWhenTheSettingIsOff() {
        assertTrue(LinuxWindowsEnv.forSession(false, ":0", SOCKET, ":1").isEmpty());
    }

    @Test
    public void aSessionUnderX11DisplayGetsWhatItAlwaysGot() {
        assertEquals(java.util.Collections.singletonMap("DISPLAY", ":0"),
            LinuxWindowsEnv.forSession(true, ":0", null, null));
        assertTrue(LinuxWindowsEnv.forSession(true, null, null, null).isEmpty());
        // Xwayland's number without the host means nothing.
        assertTrue(LinuxWindowsEnv.forSession(true, null, null, ":1").isEmpty());
    }

    @Test
    public void aSessionWhileTheWaylandHostRunsGetsItsSocketAndItsXwayland() {
        java.util.Map<String, String> env = LinuxWindowsEnv.forSession(true, ":0", SOCKET, ":1");
        assertEquals(SOCKET, env.get("WAYLAND_DISPLAY"));
        assertEquals(":1", env.get("DISPLAY"));
        assertEquals(2, env.size());
        // Xwayland not there yet: X11 Display's server if it runs, else no DISPLAY.
        assertEquals(":0", LinuxWindowsEnv.forSession(true, ":0", SOCKET, null).get("DISPLAY"));
        assertEquals(java.util.Collections.singletonMap("WAYLAND_DISPLAY", SOCKET),
            LinuxWindowsEnv.forSession(true, null, SOCKET, ""));
    }

    @Test
    public void aSessionNeverGetsARuntimeDirectoryOrToolkitHints() {
        java.util.Map<String, String> env = LinuxWindowsEnv.forSession(true, ":0", SOCKET, ":1");
        assertFalse(env.containsKey("XDG_RUNTIME_DIR"));
        assertFalse(env.containsKey("GDK_BACKEND"));
        assertFalse(env.containsKey("QT_QPA_PLATFORM"));
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void withXwaylandTheClientGetsBothDisplays() {
        assertEquals(Arrays.asList("WAYLAND_DISPLAY=" + SOCKET, "DISPLAY=:1", "GDK_BACKEND=wayland,x11",
                "QT_QPA_PLATFORM=wayland;xcb"),
            LinuxWindowsEnv.forClient(SOCKET, 1, "turnip-zink"));
    }

    @Test
    public void withoutXwaylandThereIsNoDisplay() {
        List<String> env = LinuxWindowsEnv.forClient(SOCKET, LinuxWindowsEnv.NO_X_DISPLAY, null);
        for (String line : env) assertFalse(line, line.startsWith("DISPLAY="));
        assertTrue(env.contains("WAYLAND_DISPLAY=" + SOCKET));
    }

    @Test
    public void neverRuntimeDirNorForcedSingleBackends() {
        for (X11GpuProbe.Profile profile : X11GpuProbe.Profile.values()) {
            for (String line : LinuxWindowsEnv.forClient(SOCKET, 0, profile.id)) {
                assertFalse(line, line.startsWith("XDG_RUNTIME_DIR="));
                assertFalse(line, line.startsWith("SDL_VIDEODRIVER="));
                assertFalse(line, line.startsWith("MOZ_ENABLE_WAYLAND="));
            }
        }
    }

    @Test
    public void everyProfileAndAnUnknownOneGetTheSameLines() {
        List<String> base = LinuxWindowsEnv.forClient(SOCKET, 0, null);
        for (X11GpuProbe.Profile profile : X11GpuProbe.Profile.values()) {
            assertEquals(profile.id, base, LinuxWindowsEnv.forClient(SOCKET, 0, profile.id));
        }
        assertEquals(base, LinuxWindowsEnv.forClient(SOCKET, 0, "no-such-profile"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aRelativeSocketIsRefused() {
        LinuxWindowsEnv.forClient("wayland-0", 0, null);
    }

    @Test
    public void dumpPathsStayInsideTheDataDirectory() throws Exception {
        File data = tmp.newFolder("data");
        String inside = new File(data, "files/wayland-dumps/7.png").getPath();
        assertEquals(new File(inside).getCanonicalPath(), LinuxWindowsService.dumpPathInside(data.getPath(), inside));
        assertNull(LinuxWindowsService.dumpPathInside(data.getPath(), data.getPath() + "/../evil.png"));
        assertNull(LinuxWindowsService.dumpPathInside(data.getPath(), "/sdcard/x.png"));
        assertNull(LinuxWindowsService.dumpPathInside(data.getPath(), data.getPath()));
        assertNull(LinuxWindowsService.dumpPathInside(data.getPath(), null));
    }

    @Test
    public void turnipZinkGainsTheKopperLinesAndNothingElseDoes() {
        List<String> base = Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "VK_ICD_FILENAMES=/x");
        List<String> turnip = LinuxWindowsEnv.gpuEnv(base, "turnip-zink");
        assertEquals(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "VK_ICD_FILENAMES=/x",
            "LIBGL_KOPPER_DRI2=1", "VK_DRIVER_FILES=" + com.termux.app.x11.X11GpuProbe.FREEDRENO_ICD), turnip);
        assertEquals(2, base.size());
        for (X11GpuProbe.Profile profile : X11GpuProbe.Profile.values()) {
            if (profile == X11GpuProbe.Profile.TURNIP_ZINK) continue;
            assertEquals(profile.id, base, LinuxWindowsEnv.gpuEnv(base, profile.id));
        }
        assertEquals(base, LinuxWindowsEnv.gpuEnv(base, null));
        assertEquals(base, LinuxWindowsEnv.gpuEnv(base, "no-such-profile"));
    }

    @Test
    public void aDisplayNumberComesOutOfADisplayString() {
        assertEquals(0, LinuxWindowsEnv.displayNumber(":0"));
        assertEquals(12, LinuxWindowsEnv.displayNumber(":12"));
        assertEquals(LinuxWindowsEnv.NO_X_DISPLAY, LinuxWindowsEnv.displayNumber(null));
        assertEquals(LinuxWindowsEnv.NO_X_DISPLAY, LinuxWindowsEnv.displayNumber(""));
        assertEquals(LinuxWindowsEnv.NO_X_DISPLAY, LinuxWindowsEnv.displayNumber(":x"));
        assertEquals(LinuxWindowsEnv.NO_X_DISPLAY, LinuxWindowsEnv.displayNumber("0"));
    }
}
