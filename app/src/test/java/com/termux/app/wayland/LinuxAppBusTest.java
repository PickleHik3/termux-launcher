package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.sound.LauncherSound;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LinuxAppBusTest {

    @Test public void anAppIsHandedTheHostsBusInPlaceOfTheLaunchers() {
        String[] env = {"PATH=/p/bin", "DBUS_SESSION_BUS_ADDRESS=unix:path=/t/.X11-unix/termux-launcher-bus",
            LauncherSound.DISTRO_BUS_VARIABLE + "=unix:path=/tmp/.X11-unix/termux-launcher-bus",
            "PULSE_SERVER=unix:/s/native", "DBUS_SESSION_BUS_PID=12"};
        List<String> out = Arrays.asList(LinuxAppBus.withBus(env));
        assertEquals(Arrays.asList("PATH=/p/bin", "PULSE_SERVER=unix:/s/native",
            "DBUS_SESSION_BUS_ADDRESS=" + LinuxAppBus.ADDRESS,
            LauncherSound.DISTRO_BUS_VARIABLE + "=" + LinuxAppBus.DISTRO_ADDRESS), out);
    }

    @Test public void anAppWithNoBusGetsTheHostsToo() {
        List<String> out = Arrays.asList(LinuxAppBus.withBus(new String[] {"HOME=/h"}));
        assertTrue(out.contains("DBUS_SESSION_BUS_ADDRESS=" + LinuxAppBus.ADDRESS));
        assertEquals(2, LinuxAppBus.withBus(null).length);
    }

    @Test public void theBusItselfStartsWithNoSessionBusVariable() {
        List<String> out = LinuxAppBus.withoutBus(new String[] {"HOME=/h",
            "DBUS_SESSION_BUS_ADDRESS=x", "DBUS_SESSION_BUS_WINDOWID=1", null});
        assertEquals(Arrays.asList("HOME=/h"), out);
    }

    @Test public void theSocketSitsBesideTheXSocketsWhereADistroSeesIt() {
        assertEquals(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/.X11-unix/" + LinuxAppBus.NAME,
            LinuxAppBus.SOCKET);
        assertEquals("unix:path=" + LinuxAppBus.SOCKET, LinuxAppBus.ADDRESS);
        assertEquals("unix:path=/tmp/.X11-unix/" + LinuxAppBus.NAME, LinuxAppBus.DISTRO_ADDRESS);
        assertFalse(LinuxAppBus.NAME.equals("termux-launcher-bus"));
    }

    @Test public void theBusRunsInTheForegroundAndSaysWhenItListens() {
        assertEquals(Arrays.asList("/d", "--config-file=/c", "--nofork", "--nopidfile",
            "--print-address=1"), LinuxAppBus.argv("/d", "/c"));
    }
}
