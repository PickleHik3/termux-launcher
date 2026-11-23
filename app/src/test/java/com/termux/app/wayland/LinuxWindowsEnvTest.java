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

    private static final String SOCKET = "/data/data/pkg/files/usr/tmp/tl-wayland/wayland-0";

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
}
