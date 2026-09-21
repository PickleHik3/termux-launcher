package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * The nix profile read as one more place apps come from: which directories are listed, how a
 * desktop file that is really a store link is opened, and the one line that runs the app.
 */
public class NixContainerTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File prefix;
    private File profile;

    private static void link(File at, String target) throws IOException {
        assertTrue(at.getParentFile().isDirectory() || at.getParentFile().mkdirs());
        Files.createSymbolicLink(at.toPath(), Paths.get(target));
    }

    private static File write(File file, String content) throws IOException {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** The same shape as the phone's: a two-hop profile over a user environment of store links. */
    private ProotDistro.Container nix() throws IOException {
        prefix = temp.newFolder("files", "usr");
        File env = new File(prefix, "nix/store/c7ly-user-environment");
        File path = new File(prefix, "nix/store/sl25-nix-on-droid-path");
        assertTrue(new File(path, "share/applications").mkdirs());
        assertTrue(new File(path, "bin").mkdirs());
        assertTrue(env.mkdirs());
        link(new File(env, "share"), "/nix/store/sl25-nix-on-droid-path/share");
        link(new File(env, "bin"), "/nix/store/sl25-nix-on-droid-path/bin");
        File profiles = new File(prefix, "nix/var/nix/profiles/per-user/nix-on-droid");
        assertTrue(profiles.mkdirs());
        link(new File(profiles, "profile-2-link"), "/nix/store/c7ly-user-environment");
        link(new File(profiles, "profile"), "profile-2-link");
        profile = NixProfile.profile(prefix);
        assertNotNull(profile);
        return ProotDistro.Container.nix(prefix, profile);
    }

    @Test public void itIsNeitherThePrefixNorADistro() throws IOException {
        ProotDistro.Container nix = nix();
        assertEquals(ProotDistro.Container.Kind.NIX, nix.kind);
        assertFalse(nix.isPrefix());
        // No name, so an app's id stays the bare desktop-file name pins are already keyed on.
        assertEquals("", nix.name);
        assertEquals("xterm", X11Apps.qualify(nix.name, "xterm"));
    }

    @Test public void theApplicationDirsAreTheProfilesAndTheUsersOwn() throws IOException {
        ProotDistro.Container nix = nix();
        List<File> dirs = nix.applicationDirs();
        assertEquals(2, dirs.size());
        assertEquals(new File(prefix, "nix/store/sl25-nix-on-droid-path/share/applications"),
            dirs.get(0));
        assertEquals(new File(prefix.getParentFile(), "home/.local/share/applications"),
            dirs.get(1));
    }

    @Test public void anAbsolutePathIsRewrittenAndFollowed() throws IOException {
        ProotDistro.Container nix = nix();
        File icon = write(new File(prefix, "nix/store/zxyn-xterm-410/share/pixmaps/xterm.png"), "x");
        assertEquals(icon, nix.inside("/nix/store/zxyn-xterm-410/share/pixmaps/xterm.png"));
        // An Android path is visible inside the proot exactly as it is.
        assertEquals(new File("/data/data/com.example/files/a.png"),
            nix.inside("/data/data/com.example/files/a.png"));
    }

    @Test public void aDesktopFileThatIsAStoreLinkIsOpenedThroughIt() throws IOException {
        ProotDistro.Container nix = nix();
        File real = write(new File(prefix,
            "nix/store/zxyn-xterm-410/share/applications/xterm.desktop"), "[Desktop Entry]\n");
        File listed = new File(prefix,
            "nix/store/sl25-nix-on-droid-path/share/applications/xterm.desktop");
        link(listed, "/nix/store/zxyn-xterm-410/share/applications/xterm.desktop");

        assertFalse("dangling from Android's side until it is rewritten", listed.canRead());
        assertEquals(real, nix.readable(listed));
        assertTrue(nix.readable(listed).canRead());
    }

    @Test public void aLoginRunsTheCommandInsideTheNixEnvironment() throws IOException {
        ProotDistro.Container nix = nix();
        assertEquals(new File(prefix, "bin/login").getPath() + " sh -c 'xterm'",
            ProotDistro.loginCommand(nix, "xterm"));
    }

    @Test public void theCommandsOwnQuotingSurvivesTheTrip() throws IOException {
        ProotDistro.Container nix = nix();
        assertEquals(new File(prefix, "bin/login").getPath()
                + " sh -c 'sh -c '\\''echo hi there'\\'''",
            ProotDistro.loginCommand(nix, "sh -c 'echo hi there'"));
    }

    /** Field codes are gone before the command is ever wrapped; the wrapping only quotes. */
    @Test public void fieldCodesAreStrippedBeforeTheLoginWrapsIt() throws IOException {
        ProotDistro.Container nix = nix();
        String exec = LinuxAppCatalog.stripFieldCodes("gimp-2.10 %U --new-instance %f");
        assertEquals("gimp-2.10 --new-instance", exec);
        assertEquals(new File(prefix, "bin/login").getPath()
            + " sh -c 'gimp-2.10 --new-instance'", ProotDistro.loginCommand(nix, exec));
    }

    /**
     * The whole way through, with the shape the phone has: the desktop file listed in the merged
     * profile is a link into the package, its {@code TryExec} is a bare name only the profile's
     * bin knows about, and its icon is a file the package ships.
     */
    @Test public void aStoreLinkedDesktopFileIsListedWithItsIcon() throws IOException {
        ProotDistro.Container nix = nix();
        File xterm = new File(prefix, "nix/store/zxyn-xterm-410");
        write(new File(xterm, "share/applications/xterm.desktop"),
            "[Desktop Entry]\nType=Application\nName=XTerm\nExec=xterm\nTryExec=xterm\n"
                + "Icon=xterm-color\nTerminal=false\n");
        link(new File(prefix, "nix/store/sl25-nix-on-droid-path/share/applications/xterm.desktop"),
            "/nix/store/zxyn-xterm-410/share/applications/xterm.desktop");
        assertTrue(write(new File(xterm, "bin/xterm"), "#!/bin/sh\n").setExecutable(true));
        link(new File(prefix, "nix/store/sl25-nix-on-droid-path/bin/xterm"),
            "/nix/store/zxyn-xterm-410/bin/xterm");
        File icon = write(new File(prefix,
            "nix/store/sl25-nix-on-droid-path/share/icons/locolor/48x48/apps/xterm-color.png"), "p");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(nix));

        assertEquals(1, apps.size());
        LinuxAppCatalog.LinuxApp app = apps.get(0);
        assertEquals("XTerm", app.name);
        assertEquals("xterm", app.id);
        assertEquals("xterm", app.exec);
        assertFalse(app.terminal);
        assertEquals(icon, LinuxAppIcons.find(app));
    }

    /** A TryExec naming a binary the profile does not have is an entry that is not listed. */
    @Test public void aDesktopFileForSomethingUninstalledIsSkipped() throws IOException {
        ProotDistro.Container nix = nix();
        write(new File(prefix, "nix/store/sl25-nix-on-droid-path/share/applications/gone.desktop"),
            "[Desktop Entry]\nType=Application\nName=Gone\nExec=gone\nTryExec=gone\n");

        assertTrue(LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(nix)).isEmpty());
    }

    /** The runner's own script is unchanged: it exports, cds, and execs whatever it is given. */
    @Test public void theRunnerScriptExportsTheDisplayAroundTheLogin() throws IOException {
        ProotDistro.Container nix = nix();
        write(new File(prefix, "nix/store/sl25-nix-on-droid-path/share/applications/xterm.desktop"),
            "[Desktop Entry]\nType=Application\nName=XTerm\nExec=xterm\n");
        List<LinuxAppCatalog.LinuxApp> apps =
            LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(nix));
        assertEquals(1, apps.size());
        String script = X11LinuxAppRunner.script(apps.get(0), ":0", java.util.Collections.emptyList());
        assertTrue(script.startsWith("export DISPLAY=:0\n"));
        assertTrue(script.contains("cd \"$HOME\"\n"));
        assertTrue(script.endsWith(
            "exec " + new File(prefix, "bin/login").getPath() + " sh -c 'xterm'\n"));
    }
}
