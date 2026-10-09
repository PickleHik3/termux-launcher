package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * What the server is handed as {@code -xstartup}. Everywhere but nix that is the window manager's
 * own command line; on nix the server execs it from Android's side and refuses any argument past
 * about 128 characters, so it is the wrapper's short path and the command lives inside the file.
 */
public class X11WindowManagerTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static File executable(File file) throws IOException {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setExecutable(true));
        return file;
    }

    /** A Termux prefix: the window manager sits in the prefix's own bin. */
    private File termuxPrefix() throws IOException {
        File prefix = temp.newFolder("termux", "usr");
        executable(new File(prefix, "bin/openbox"));
        return prefix;
    }

    /** A nix prefix: the profile's bin is a store link, and that is where openbox is. */
    private File nixPrefix() throws IOException {
        File prefix = temp.newFolder("nix", "usr");
        executable(new File(prefix, "nix/store/zzzz-openbox/bin/openbox"));
        File env = new File(prefix, "nix/store/c7ly-user-environment");
        assertTrue(env.mkdirs());
        Files.createSymbolicLink(new File(env, "bin").toPath(),
            Paths.get("/nix/store/zzzz-openbox/bin"));
        File profiles = new File(prefix, "nix/var/nix/profiles/per-user/nix-on-droid");
        assertTrue(profiles.mkdirs());
        Files.createSymbolicLink(new File(profiles, "profile-2-link").toPath(),
            Paths.get("/nix/store/c7ly-user-environment"));
        Files.createSymbolicLink(new File(profiles, "profile").toPath(), Paths.get("profile-2-link"));
        return prefix;
    }

    /**
     * The server ends the display the moment its {@code -xstartup} child exits or is signalled, so
     * that child is the wrapper on every edition, never the window manager itself.
     */
    @Test public void everyEditionIsHandedTheWrapper() throws IOException {
        File prefix = termuxPrefix();
        File wrapper = executable(new File(prefix, "bin/termux-x11-wm"));

        assertEquals(wrapper.getPath(), X11WindowManager.xstartup("openbox", prefix, wrapper));
        assertEquals(wrapper.getPath(),
            X11WindowManager.xstartup("openbox --config-file /my/rc.xml", prefix, wrapper));
        // What the wrapper carries: the command itself here, a login line on nix.
        assertEquals("openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH,
            X11WindowManager.startCommand("openbox", prefix));
        // A command of the user's own is run as written, config file and all.
        assertEquals("openbox --config-file /my/rc.xml",
            X11WindowManager.startCommand("openbox --config-file /my/rc.xml", prefix));
    }

    /**
     * No wrapper written yet — an install from before there was one on this edition. Termux can
     * still be handed the command, which is what it always was; the manager is then the server's
     * own child, and nothing will kill it, because it is the wrapper that records the pid a stop
     * aims at.
     */
    @Test public void termuxWithNoWrapperFallsBackToTheCommandItself() throws IOException {
        File prefix = termuxPrefix();
        File wrapper = new File(prefix, "bin/termux-x11-wm");

        assertEquals("openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH,
            X11WindowManager.xstartup("openbox", prefix, wrapper));
    }

    @Test public void nothingConfiguredAndNothingInstalledAreBothNoWindowManager()
            throws IOException {
        File prefix = termuxPrefix();
        File wrapper = new File(prefix, "bin/termux-x11-wm");

        assertNull(X11WindowManager.xstartup("", prefix, wrapper));
        assertNull(X11WindowManager.xstartup("   ", prefix, wrapper));
        assertNull(X11WindowManager.xstartup("i3", prefix, wrapper));
    }

    @Test public void nixIsHandedTheWrapperAndNothingElse() throws IOException {
        File prefix = nixPrefix();
        File wrapper = executable(new File(prefix, "bin/termux-x11-wm"));

        String xstartup = X11WindowManager.xstartup("openbox", prefix, wrapper);

        assertEquals(wrapper.getPath(), xstartup);
        assertTrue("the server refuses an argument past about 128 characters",
            X11CliInstaller.WM_SCRIPT_PATH.length() < 128);
        // The command the wrapper carries goes through login, which is the whole nix reason for it.
        assertEquals(new File(prefix, "bin/login").getPath() + " openbox --config-file "
                + X11CliInstaller.OPENBOX_RC_PATH,
            X11WindowManager.startCommand("openbox", prefix));
    }

    /** No wrapper on disk: the display starts without a manager rather than with a bad argument. */
    @Test public void nixWithNoWrapperStartsWithoutAWindowManager() throws IOException {
        File prefix = nixPrefix();
        File wrapper = new File(prefix, "bin/termux-x11-wm");

        assertNull(X11WindowManager.xstartup("openbox", prefix, wrapper));
    }

    /** The profile is where a nix login finds it; the prefix's own bin holds nothing to run. */
    @Test public void nixLooksForTheBinaryInTheProfile() throws IOException {
        File prefix = nixPrefix();
        File wrapper = executable(new File(prefix, "bin/termux-x11-wm"));

        assertNull(X11WindowManager.xstartup("i3", prefix, wrapper));
        assertEquals(wrapper.getPath(), X11WindowManager.xstartup("openbox", prefix, wrapper));
    }

    /**
     * D1: a desktop stops the one pid the launcher's own wrapper wrote down, and nothing else.
     * No pattern is matched against anything — the display server's own command line carries the
     * window manager's command as its {@code -xstartup} argument, and a manager the user started
     * for themselves is never in that file.
     */
    @Test public void onlyThePidTheLauncherWroteDownIsStopped() {
        String stop = X11WindowManager.stopCommand("openbox --config-file "
            + X11CliInstaller.OPENBOX_RC_PATH);
        assertNotNull(stop);
        assertEquals("wm=$(cat '" + X11WindowManager.WM_PID_PATH + "' 2>/dev/null)\n"
            + "[ -n \"$wm\" ] && kill \"$wm\" 2>/dev/null\n"
            + "rm -f '" + X11WindowManager.WM_PID_PATH + "'", stop);
        assertFalse("nothing is matched by name or by command line", stop.contains("pkill"));

        // Any manager the launcher started is ours, however it is configured — the file says so.
        assertNotNull(X11WindowManager.stopCommand("i3"));
        assertNull("but there may be no manager at all", X11WindowManager.stopCommand(null));
    }

    /**
     * The race this cost a device to find (pong, 2026-09-22). Tapping a desktop with no display
     * running starts one, and the launch follows the server coming up — which is before the
     * wrapper the server execs has started the manager and written its pid. Reading the file
     * straight away found nothing, so our openbox survived, xfwm4 found the screen taken and
     * refused, and XFCE came up under the launcher's maximise-everything rule: exactly what D1
     * exists to prevent. The stop now waits for the file rather than assuming it is there.
     */
    /**
     * The race this cost two device runs to pin down (pong, 2026-09-22). Tapping a desktop with no
     * display running starts one, and the launch follows the server reporting itself up — which is
     * before it has forked the wrapper that starts the manager and writes its pid. The first
     * attempt at this waited for the <em>file</em>, bounded at two seconds, and still lost: the
     * file's own timestamp showed it written after the stop had given up. So the wait is for a
     * manager that answers, and it is long enough to mean it.
     */
    @Test public void aDesktopThatJustStartedTheDisplayWaitsForTheManagerToBeUp() {
        String stop = X11WindowManager.stopCommand("openbox", true);
        assertNotNull(stop);
        // Not "the file exists" — a file can name a process that has gone.
        assertTrue(stop, stop.contains("kill -0 \"$wm\" 2>/dev/null && break"));
        // Bounded, so a manager that never comes cannot hold a desktop up for ever.
        assertTrue(stop, stop.contains("[ \"$i\" -lt " + X11WindowManager.AWAIT_MANAGER_TRIES + " ]"));
        assertTrue("fifteen seconds, not two — two is what lost on the device",
            X11WindowManager.AWAIT_MANAGER_TRIES >= 100);
        // The wait comes before the kill, or it would be no wait at all.
        assertTrue(stop, stop.indexOf("&& break") < stop.indexOf("] && kill \"$wm\""));
    }

    /**
     * And it is spent only where there is something to wait for. With the display already up the
     * file is either there — the loop would end on its first look anyway — or there is genuinely no
     * manager of ours, where waiting could only delay the desktop the user asked for.
     */
    @Test public void aDesktopOnARunningDisplayWaitsForNothing() {
        String stop = X11WindowManager.stopCommand("openbox", false);
        assertNotNull(stop);
        assertFalse(stop, stop.contains("while"));
        assertFalse(stop, stop.contains("sleep"));
        assertEquals("and it is exactly what the one-argument form builds",
            X11WindowManager.stopCommand("openbox"), stop);
    }

    /** An empty pid file — no manager of ours running — kills nothing at all. */
    @Test public void nothingRecordedMeansNothingIsKilled() {
        String stop = X11WindowManager.stopCommand("openbox");
        assertNotNull(stop);
        assertTrue(stop.contains("[ -n \"$wm\" ] && kill"));
    }
}
