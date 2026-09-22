package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
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

    @Test public void termuxIsHandedTheCommandItself() throws IOException {
        File prefix = termuxPrefix();
        File wrapper = new File(prefix, "bin/termux-x11-wm");

        assertEquals("openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH,
            X11WindowManager.xstartup("openbox", prefix, wrapper));
        // A command of the user's own is run as written, config file and all.
        assertEquals("openbox --config-file /my/rc.xml",
            X11WindowManager.xstartup("openbox --config-file /my/rc.xml", prefix, wrapper));
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
        // The command the wrapper carries is the same one Termux is handed directly.
        assertEquals("openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH,
            X11WindowManager.command("openbox", prefix));
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
     * D1: only the manager this launcher started may be stopped for a desktop, and the
     * configuration file it was handed is the whole of what tells it apart.
     */
    @Test public void onlyOurOwnWindowManagerCanBeStopped() {
        assertEquals("pkill -x -f 'openbox --config-file " + X11CliInstaller.OPENBOX_RC_PATH + "'",
            X11WindowManager.stopCommand("openbox --config-file "
                + X11CliInstaller.OPENBOX_RC_PATH));

        assertNull("a user's own openbox is not ours to stop",
            X11WindowManager.stopCommand("openbox"));
        assertNull("nor one pointed at a configuration of their own",
            X11WindowManager.stopCommand("openbox --config-file /somewhere/else/rc.xml"));
        assertNull("any other manager is run as written and looks exactly like theirs",
            X11WindowManager.stopCommand("i3"));
        assertNull("and there may be no manager at all", X11WindowManager.stopCommand(null));
    }

    /**
     * The match is the whole command line and nothing less: the display server's own carries the
     * very same window-manager command as its {@code -xstartup} argument, and has to survive a
     * desktop starting.
     */
    @Test public void theStopMatchesTheWholeCommandLineAndNothingLess() {
        String stop = X11WindowManager.stopCommand("openbox --config-file "
            + X11CliInstaller.OPENBOX_RC_PATH);
        assertNotNull(stop);
        assertTrue(stop.startsWith("pkill -x -f "));
    }
}
