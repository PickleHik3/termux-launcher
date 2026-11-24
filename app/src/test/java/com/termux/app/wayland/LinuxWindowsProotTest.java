package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LinuxWindowsProotTest {

    private static final String SOCKET = "/data/data/pkg/files/usr/tmp/wayland-0";

    @Test
    public void nothingIsBound_aDefaultLoginHasThePrefixAtItsOwnPath() {
        assertTrue(LinuxWindowsProot.binds(SOCKET).isEmpty());
    }

    @Test
    public void theEnvironmentNamesTheSocketAtTheSamePathAsOutsideAndXwayland() {
        assertEquals(Arrays.asList("WAYLAND_DISPLAY=/data/data/pkg/files/usr/tmp/wayland-0", "DISPLAY=:2",
                "GDK_BACKEND=wayland,x11", "QT_QPA_PLATFORM=wayland;xcb"),
            LinuxWindowsProot.env(SOCKET, 2));
    }

    @Test
    public void theRuntimeDirectoryIsLeftToTheContainer() {
        for (String line : LinuxWindowsProot.env(SOCKET, 0)) {
            assertFalse(line, line.startsWith("XDG_RUNTIME_DIR="));
        }
    }

    @Test
    public void noDisplayLineWithoutXwayland() {
        for (String line : LinuxWindowsProot.env(SOCKET, LinuxWindowsEnv.NO_X_DISPLAY)) {
            assertFalse(line, line.startsWith("DISPLAY="));
        }
    }

    @Test
    public void noGpuVariableAndNoPrefixPathReachesTheContainer() {
        List<String> all = new java.util.ArrayList<>(LinuxWindowsProot.env(SOCKET, 1));
        all.addAll(LinuxWindowsProot.binds(SOCKET));
        for (String line : all) {
            assertFalse(line, line.startsWith("LD_LIBRARY_PATH="));
            assertFalse(line, line.startsWith("LIBGL_KOPPER_DRI2"));
            assertFalse(line, line.startsWith("MESA_LOADER_DRIVER_OVERRIDE"));
            assertFalse(line, line.startsWith("VK_DRIVER_FILES"));
            assertFalse(line, line.startsWith("VK_ICD_FILENAMES"));
            assertFalse(line, line.startsWith("GALLIUM_DRIVER"));
            // The socket is the one prefix path a container is told.
            if (!line.startsWith("WAYLAND_DISPLAY=")) assertFalse(line, line.contains("/usr/"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aRelativeSocketPathIsRefused() {
        LinuxWindowsProot.env("wayland-0", 1);
    }
}
