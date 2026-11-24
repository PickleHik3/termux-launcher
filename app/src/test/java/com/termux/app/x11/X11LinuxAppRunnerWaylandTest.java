package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wayland.WaylandAppRunner;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What an app's script exports when it opens under Wayland Display. */
public class X11LinuxAppRunnerWaylandTest {

    private static LinuxAppCatalog.LinuxApp app(String exec) {
        return new LinuxAppCatalog.LinuxApp("gedit", "Text Editor", exec, "gedit", "");
    }

    @Test public void theSocketIsAnAbsolutePathUnderThePrefixTmp() {
        assertEquals(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/wayland-0",
            WaylandAppRunner.socketPath());
        assertTrue(WaylandAppRunner.socketPath().startsWith("/"));
    }

    @Test public void theEnvironmentIsTheWindowsEnvThenTheAppsOwnLines() {
        List<String> appEnv = Arrays.asList("GALLIUM_DRIVER=zink", "GDK_SCALE=2");
        List<String> env = X11LinuxAppRunner.waylandEnv("/p/tmp/wayland-0", ":1", null, appEnv);
        assertEquals(Arrays.asList("WAYLAND_DISPLAY=/p/tmp/wayland-0", "DISPLAY=:1",
            "GDK_BACKEND=wayland,x11", "QT_QPA_PLATFORM=wayland;xcb",
            "GALLIUM_DRIVER=zink", "GDK_SCALE=2"), env);
        // The input list is not touched.
        assertEquals(2, appEnv.size());
    }

    @Test public void noXwaylandDisplayLeavesDisplayOut() {
        for (String line : X11LinuxAppRunner.waylandEnv("/s/wayland-0", null, null, Collections.emptyList())) {
            assertFalse(line, line.startsWith("DISPLAY="));
        }
    }

    @Test public void theRuntimeDirectoryIsLeftAlone() {
        List<String> env = X11LinuxAppRunner.waylandEnv("/s/wayland-0", null, null, Collections.emptyList());
        for (String line : env) assertFalse(line, line.startsWith("XDG_RUNTIME_DIR="));
        String script = X11LinuxAppRunner.script(app("gedit"), ":2", env);
        assertFalse(script.contains("XDG_RUNTIME_DIR"));
    }

    @Test public void theScriptExportsTheXwaylandDisplayAndTheSocketBeforeTheGpuLines() {
        List<String> env = X11LinuxAppRunner.waylandEnv("/s/wayland-0", ":2", null,
            Collections.singletonList("MESA_LOADER_DRIVER_OVERRIDE=zink"));
        String script = X11LinuxAppRunner.script(app("gedit --new-window"), ":2", env);
        assertEquals("export DISPLAY=:2\nexport MOZ_USE_XINPUT2=1\n"
            + "export WAYLAND_DISPLAY=/s/wayland-0\nexport DISPLAY=:2\n"
            + "export GDK_BACKEND=wayland,x11\nexport QT_QPA_PLATFORM=wayland;xcb\n"
            + "export MESA_LOADER_DRIVER_OVERRIDE=zink\n" + X11LinuxAppRunner.cursorBlock()
            + X11LinuxAppRunner.inputMethodBlock(":2")
            + "cd \"$HOME\"\n"
            + "exec gedit --new-window\n", script);
    }
}
