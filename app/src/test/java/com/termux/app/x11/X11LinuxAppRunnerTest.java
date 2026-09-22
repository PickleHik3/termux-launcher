package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/** The script a drawer tap runs, and which GPU profile it gets. */
public class X11LinuxAppRunnerTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static LinuxAppCatalog.LinuxApp app(String exec) {
        return new LinuxAppCatalog.LinuxApp("firefox", "Firefox", exec, "firefox", "");
    }

    /** A whole desktop in the prefix, as a session root's parse hands one over. */
    private static LinuxAppCatalog.LinuxApp session(String file, String exec, String desktopNames) {
        return session(ProotDistro.Container.PREFIX, file, exec, desktopNames);
    }

    private static LinuxAppCatalog.LinuxApp session(ProotDistro.Container container, String file,
                                                    String exec, String desktopNames) {
        return new LinuxAppCatalog.LinuxApp(container, file, file, exec, "", "", "", false,
            true, desktopNames);
    }

    @Test public void theScriptSetsTheDisplayTheEnvironmentAndRunsTheCommand() {
        String script = X11LinuxAppRunner.script(app("firefox --new-window"), ":1",
            Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform"));

        assertEquals("export DISPLAY=:1\nexport MOZ_USE_XINPUT2=1\n"
            + "export MESA_LOADER_DRIVER_OVERRIDE=zink\n"
            + "export TU_DEBUG=noconform\ncd \"$HOME\"\nexec firefox --new-window\n", script);
    }

    @Test public void theNoSandboxOverloadAppendsTheFlagToTheAppsOwnCommand() {
        // D7's retry: the app's exec line, not the whole script, gets the extra flag.
        String script = X11LinuxAppRunner.script(app("firefox --new-window"), ":1",
            Collections.emptyList(), true);
        assertTrue(script.endsWith("exec firefox --new-window --no-sandbox\n"));
        // The plain overload is unaffected — same output as before the flag existed.
        assertEquals(X11LinuxAppRunner.script(app("firefox --new-window"), ":1", Collections.emptyList()),
            X11LinuxAppRunner.script(app("firefox --new-window"), ":1", Collections.emptyList(), false));
    }

    @Test public void everyAppIsRunWithTouchThroughXInput2() {
        // Without it Firefox takes the server's pointer emulation and a finger cannot scroll.
        assertTrue(X11LinuxAppRunner.TOUCH_ENV.contains("MOZ_USE_XINPUT2=1"));
        assertTrue("even with no GPU profile installed",
            X11LinuxAppRunner.script(app("firefox"), ":1", Collections.emptyList())
                .contains("\nexport MOZ_USE_XINPUT2=1\n"));
    }

    @Test public void onlyAnInstalledGpuProfileIsUsedAndSoftwareMeansNothingExtra() {
        X11GpuProbe.Inputs adreno = new X11GpuProbe.Inputs();
        adreno.kgsl = true;
        adreno.vulkanVendor = "adreno";
        assertTrue("nothing installed: no exports that would break GL",
            X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(adreno)).isEmpty());

        adreno.mesaDri = true;
        adreno.icdFiles = Collections.singletonList("freedreno_icd.aarch64.json");
        assertEquals(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform"),
            X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(adreno)));

        X11GpuProbe.Inputs emulator = new X11GpuProbe.Inputs();
        emulator.eglVendor = "emulation";
        emulator.mesaDri = true;
        assertTrue(X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(emulator)).isEmpty());
    }

    // --- whole desktop sessions ---------------------------------------------------------------

    @Test public void aDesktopGetsTheSessionEnvironmentAnAppDoesNot() {
        String script = X11LinuxAppRunner.sessionScript(session("xfce", "startxfce4", "XFCE"),
            ":1", Collections.emptyList(), null);

        // Everything an app's script does, first and unchanged.
        assertTrue(script.startsWith("export DISPLAY=:1\nexport MOZ_USE_XINPUT2=1\ncd \"$HOME\"\n"));
        // Then the three things only a desktop needs, the directory made before it is used.
        assertTrue(script.contains(
            "export XDG_RUNTIME_DIR=\"${TMPDIR:-/tmp}/termux-x11-runtime\"\n"
            + "mkdir -p \"$XDG_RUNTIME_DIR\" && chmod 700 \"$XDG_RUNTIME_DIR\"\n"
            + "export XDG_SESSION_TYPE=x11\n"
            + "export XDG_CURRENT_DESKTOP='XFCE'\n"));
        assertTrue("the desktop is what the shell becomes", script.endsWith("exec startxfce4\n"));
        // An app's own script is untouched by any of it.
        assertFalse(X11LinuxAppRunner.script(app("firefox"), ":1", Collections.emptyList())
            .contains("XDG_"));
    }

    @Test public void aDesktopThatNamesNoDesktopNamesIsToldNothingAboutItself() {
        // Nine of the nineteen session files in Termux's x11 repository have no DesktopNames key;
        // an empty XDG_CURRENT_DESKTOP would be worse than none, so the line simply is not there.
        String script = X11LinuxAppRunner.sessionScript(session("i3", "i3", ""), ":1",
            Collections.emptyList(), null);
        assertFalse(script.contains("XDG_CURRENT_DESKTOP"));
        assertTrue(script.contains("export XDG_SESSION_TYPE=x11\n"));
    }

    @Test public void onlyADesktopKnownToStartNoBusIsGivenOne() {
        // XFCE starts none on the X11 path, so it is wrapped — but only when there is no bus
        // already and dbus-launch is actually installed, which comes from another repository.
        String xfce = X11LinuxAppRunner.sessionScript(session("xfce", "startxfce4", "XFCE"), ":1",
            Collections.emptyList(), null);
        assertTrue(xfce.contains("if [ -z \"$DBUS_SESSION_BUS_ADDRESS\" ]"
            + " && command -v dbus-launch > /dev/null 2>&1; then\n"
            + "exec dbus-launch --exit-with-session startxfce4\n"
            + "fi\n"
            + "exec startxfce4\n"));

        // LXQt and MATE start their own; a second bus is a second bus, not a spare.
        for (LinuxAppCatalog.LinuxApp own : Arrays.asList(
                session("lxqt", "startlxqt", "LXQt"),
                session("mate", "mate-session", "MATE"),
                session("openbox", "/data/data/com.termux/files/usr/bin/openbox-session", ""))) {
            String script = X11LinuxAppRunner.sessionScript(own, ":1", Collections.emptyList(), null);
            assertFalse(own.name + " must not be handed a bus", script.contains("dbus-launch"));
            assertFalse(X11LinuxAppRunner.needsOwnBus(own));
        }
        // An absolute Exec is still recognised by the program it names, the way Debian writes it.
        assertTrue(X11LinuxAppRunner.needsOwnBus(session("xfce", "/usr/bin/startxfce4", "XFCE")));
        // An ordinary app is never wrapped, whatever it is called.
        assertFalse(X11LinuxAppRunner.needsOwnBus(app("startxfce4")));
    }

    @Test public void ourOwnWindowManagerStandsDownBeforeTheDesktopStarts() {
        String stop = X11WindowManager.stopCommand(
            "openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH);
        assertNotNull(stop);
        String script = X11LinuxAppRunner.sessionScript(session("xfce", "startxfce4", "XFCE"),
            ":1", Collections.emptyList(), stop);

        int stopAt = script.indexOf(stop);
        assertTrue("the manager is stopped", stopAt > 0);
        assertTrue("and stopped before the desktop starts",
            stopAt < script.indexOf("exec startxfce4"));
        // Nothing is stopped when there is no manager at all.
        assertFalse(X11LinuxAppRunner.sessionScript(session("xfce", "startxfce4", "XFCE"), ":1",
            Collections.emptyList(), null).contains("kill"));
    }

    @Test public void theShellWritesDownThePidItIsAboutToBecome() {
        // The only handle there is on something already running: the shell records its own pid and
        // then execs, so that number is the desktop's.
        String script = X11LinuxAppRunner.sessionScript(session("xfce", "startxfce4", "XFCE"),
            ":1", Collections.emptyList(), null);
        assertTrue(script.contains("echo $$ > '" + X11LinuxAppRunner.SESSION_PID_PATH + "'\n"));
        assertTrue(script.indexOf("echo $$") < script.indexOf("exec startxfce4"));

        String stop = X11LinuxAppRunner.stopSessionScript();
        assertTrue(stop.startsWith("pid=$(cat '" + X11LinuxAppRunner.SESSION_PID_PATH
            + "' 2>/dev/null)\n"));
        assertTrue("asked first, so the desktop closes its own clients",
            stop.contains("kill \"$pid\" 2>/dev/null\n"));
        assertTrue("and waited for, so nothing starts into its teardown",
            stop.contains("while [ \"$n\" -lt 40 ] && kill -0 \"$pid\" 2>/dev/null; do\n"));
        assertTrue("insisted on afterwards", stop.contains("kill -9 \"$pid\" 2>/dev/null\n"));
    }

    @Test public void aContainersDesktopIsGivenItsEnvironmentInsideTheContainer() throws IOException {
        // No host variable crosses a proot-distro login that is not forwarded with -e, so the
        // session's environment has to be set on the far side of it.
        ProotDistro.Container debian = debian();
        String script = X11LinuxAppRunner.sessionScript(
            session(debian, "xfce", "startxfce4", "XFCE"), ":1", Collections.emptyList(), null);

        int login = script.indexOf("exec proot-distro login debian ");
        assertTrue("the desktop is run through the container's own login", login > 0);
        assertTrue("the pid is written on the host side, before the login",
            script.indexOf("echo $$") < login);
        assertTrue("and XDG_CURRENT_DESKTOP inside it",
            script.indexOf("XDG_CURRENT_DESKTOP") > login);
        // The whole body is one quoted word for the container's own shell.
        assertTrue(script.contains("-- /bin/sh -c 'export XDG_RUNTIME_DIR="));
        assertTrue(script.endsWith("exec startxfce4\n'\n"));
    }

    @Test public void theWindowManagerComesBackOnTheSameDisplayAndSaysWhatItIs() {
        // The shell execs into the manager, so $$ is the manager's own pid — which is the one and
        // only thing the next desktop will stop.
        assertEquals("export DISPLAY=:1\n"
                + "echo $$ > '" + X11WindowManager.WM_PID_PATH + "'\n"
                + "exec openbox --config-file /rc.xml\n",
            X11LinuxAppRunner.windowManagerScript("openbox --config-file /rc.xml", ":1"));
    }

    /** A container fixture, read the way the launcher reads a real one. */
    private ProotDistro.Container debian() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = new File(containers, "debian/rootfs");
        assertTrue(new File(rootfs, "usr/share/xsessions").mkdirs());
        write(rootfs, "etc/passwd", "root:x:0:0:root:/root:/bin/bash\n");
        ProotDistro.Container found =
            ProotDistro.byName(ProotDistro.containers(containers), "debian");
        assertNotNull(found);
        return found;
    }

    private static void write(File dir, String path, String contents) throws IOException {
        File file = new File(dir, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }
}
