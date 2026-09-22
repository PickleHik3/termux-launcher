package com.termux.app.x11;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The prefix writes: what a fresh install leaves behind, what an upgrade rewrites, and the two
 * things the installer must never do — overwrite a {@code termux-x11} it did not write, or write
 * through a symlink somebody else put at the destination.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11CliInstallerTest {

    private static final byte[] LOADER = "PK a loader".getBytes(StandardCharsets.UTF_8);
    private static final String GPU_SETUP = "#!/usr/bin/env bash\necho tried every profile\n";

    private static X11CliInstaller.AssetSource assets(byte[] loader) {
        return name -> new ByteArrayInputStream(name.endsWith("loader.apk") ? loader
            : GPU_SETUP.getBytes(StandardCharsets.UTF_8));
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File bin;
    private File libexec;
    private X11CliInstaller installer;

    @Before public void prefix() throws IOException {
        bin = temp.newFolder("usr", "bin");
        libexec = new File(temp.getRoot(), "usr/libexec/termux-launcher/x11");
        installer = new X11CliInstaller(bin, libexec, "com.termux.test",
            assets(LOADER));
    }

    private static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void aFreshPrefixGetsBothCommandsTheLoaderAndTheMarker() throws IOException {
        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());

        String server = text(installer.serverScript());
        assertTrue(server.startsWith("#!"));
        assertTrue(server.contains(X11CliInstaller.MARKER_PREAMBLE));
        assertTrue("the edition's id is baked into the process name",
            server.contains("termux-x11 com.termux.test"));
        assertTrue(installer.serverScript().canExecute());
        assertTrue(installer.preferenceScript().canExecute());
        assertTrue(text(installer.preferenceScript()).contains("LoriePreferences"));
        assertTrue(installer.gpuSetupScript().canExecute());
        assertEquals("the shebang points at this prefix's bash",
            "#!" + new File(bin, "bash").getPath() + "\necho tried every profile\n",
            text(installer.gpuSetupScript()));
        assertArrayEquals(LOADER, Files.readAllBytes(installer.loaderFile().toPath()));
        assertTrue(text(installer.openboxRc()).contains("<maximized>yes</maximized>"));
        assertTrue(text(installer.openboxRc()).contains("<keybind key=\"A-Tab\">"));
        assertFalse("ART refuses a writable dex on CLASSPATH", installer.loaderFile().canWrite());
        assertEquals(X11CliInstaller.MARKER_PREAMBLE + " v" + X11CliInstaller.VERSION
            + " com.termux.test\n", text(installer.markerFile()));
        assertFalse(installer.isForeignCommand());
        assertEquals("no temp file left beside the result", 0,
            countTempFiles(bin) + countTempFiles(libexec));
    }

    @Test public void aSecondInstallIsUpToDate() {
        installer.install();

        assertEquals(X11CliInstaller.Result.UP_TO_DATE, installer.install());
    }

    @Test public void anOlderMarkerIsUpgradedInPlace() throws IOException {
        installer.install();
        // The previous launcher wrote version 2; the read-only loader is what an upgrade has to
        // be able to replace.
        Files.write(installer.markerFile().toPath(),
            (X11CliInstaller.MARKER_PREAMBLE + " v2 com.termux.test\n").getBytes(StandardCharsets.UTF_8));
        installer = new X11CliInstaller(bin, libexec, "com.termux.test",
            assets("a newer loader".getBytes(StandardCharsets.UTF_8)));

        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());

        assertEquals("a newer loader", text(installer.loaderFile()));
        assertFalse(installer.loaderFile().canWrite());
        assertTrue(text(installer.markerFile()).contains(" v" + X11CliInstaller.VERSION + " "));
    }

    @Test public void aForeignCommandIsLeftAlone() throws IOException {
        File foreign = installer.serverScript();
        Files.write(foreign.toPath(),
            "#!/bin/sh\nexec app_process com.termux.x11.Loader\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(X11CliInstaller.Result.FOREIGN_COMMAND, installer.install());

        assertTrue(installer.isForeignCommand());
        assertEquals("#!/bin/sh\nexec app_process com.termux.x11.Loader\n", text(foreign));
        assertFalse("nothing else was written either", installer.preferenceScript().exists());
        assertFalse(installer.loaderFile().exists());
    }

    @Test public void aSymlinkAtTheCommandIsForeignAndNeverWrittenThrough() throws IOException {
        File target = temp.newFile("somebody-elses-termux-x11");
        Files.write(target.toPath(), "theirs\n".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(installer.serverScript().toPath(), target.toPath());

        assertEquals(X11CliInstaller.Result.FOREIGN_COMMAND, installer.install());

        assertTrue(installer.isForeignCommand());
        assertEquals("theirs\n", text(target));
        assertTrue(Files.isSymbolicLink(installer.serverScript().toPath()));
    }

    @Test public void aSymlinkAtTheLoaderFailsRatherThanFollows() throws IOException {
        File target = temp.newFile("elsewhere.apk");
        Files.write(target.toPath(), "theirs".getBytes(StandardCharsets.UTF_8));
        assertTrue(libexec.mkdirs());
        Files.createSymbolicLink(installer.loaderFile().toPath(), target.toPath());

        assertEquals(X11CliInstaller.Result.FAILED, installer.install());

        assertEquals("theirs", text(target));
    }

    @Test public void noBinDirectoryMeansNoPrefixYet() {
        installer = new X11CliInstaller(new File(temp.getRoot(), "missing/bin"), libexec,
            "com.termux.test", assets(LOADER));

        assertEquals(X11CliInstaller.Result.NO_PREFIX, installer.install());
    }

    @Test public void uninstallTakesOursOutAndLeavesAForeignOne() throws IOException {
        installer.install();
        installer.uninstall();
        assertFalse(installer.serverScript().exists());
        assertFalse(installer.preferenceScript().exists());
        assertFalse(installer.gpuSetupScript().exists());
        assertFalse(installer.loaderFile().exists());
        assertFalse(installer.openboxRc().exists());
        assertFalse(installer.markerFile().exists());

        Files.write(installer.serverScript().toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        installer.uninstall();
        assertTrue("not ours, not touched", installer.serverScript().exists());
    }

    private static int countTempFiles(File dir) {
        String[] names = dir.list((d, name) -> name.endsWith(".tmp"));
        return names == null ? 0 : names.length;
    }

    // ---- hasKeyboardData: the probe behind item 03's stale empty-state message --------------

    @Test public void noKeyboardDataInAFreshPrefix() throws IOException {
        File prefix = temp.newFolder("fresh-prefix");
        assertFalse(X11CliInstaller.hasKeyboardData(prefix));
    }

    @Test public void theXkbDirectoryAloneCounts() throws IOException {
        File prefix = temp.newFolder("xkb-prefix");
        assertTrue(new File(prefix, "share/X11/xkb").mkdirs());
        assertTrue(X11CliInstaller.hasKeyboardData(prefix));
    }

    @Test public void theXkeyboardConfigDirectoryAloneCounts() throws IOException {
        File prefix = temp.newFolder("xkeyboard-config-prefix");
        assertTrue(new File(prefix, "share/xkeyboard-config-2").mkdirs());
        assertTrue(X11CliInstaller.hasKeyboardData(prefix));
    }

    /** The flip a `pkg install xkeyboard-config` run in a shell produces, package unchanged. */
    @Test public void installingThePackageFlipsTheAnswer() throws IOException {
        File prefix = temp.newFolder("installed-while-showing");
        assertFalse("not there yet", X11CliInstaller.hasKeyboardData(prefix));
        assertTrue(new File(prefix, "share/xkeyboard-config-2").mkdirs());
        assertTrue("the same prefix, re-read", X11CliInstaller.hasKeyboardData(prefix));
    }

    // ---- The nix edition: no bash, and the keyboard data only in the store ------------------

    /** The profile link, which is the whole of "this prefix is nix". */
    private void nixProfile(File prefix) throws IOException {
        File env = new File(prefix, "nix/store/c7ly-user-environment");
        assertTrue(new File(env, "share").mkdirs());
        File profiles = new File(prefix, "nix/var/nix/profiles/per-user/nix-on-droid");
        assertTrue(profiles.mkdirs());
        Files.createSymbolicLink(new File(profiles, "profile-2-link").toPath(),
            java.nio.file.Paths.get("/nix/store/c7ly-user-environment"));
        Files.createSymbolicLink(new File(profiles, "profile").toPath(),
            java.nio.file.Paths.get("profile-2-link"));
    }

    /** An executable in the profile's bin, reached the way a merged nix profile reaches one. */
    private File profileBin(File prefix, String name) throws IOException {
        File real = new File(prefix, "nix/store/zzzz-" + name + "/bin/" + name);
        assertTrue(real.getParentFile().mkdirs());
        Files.write(real.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(real.setExecutable(true));
        File env = new File(prefix, "nix/store/c7ly-user-environment");
        Files.createSymbolicLink(new File(env, "bin").toPath(),
            java.nio.file.Paths.get("/nix/store/zzzz-" + name + "/bin"));
        return real;
    }

    /** An {@code xkeyboard-config} store entry, as a switch leaves one. */
    private File xkeyboardConfig(File prefix, String name) {
        File xkb = new File(prefix, "nix/store/" + name + "/share/X11/xkb");
        assertTrue(xkb.mkdirs());
        return xkb;
    }

    /**
     * The server is exec'd by the launcher from Android's side, where a nix prefix's own
     * {@code sh} is a store link that cannot be followed at all; the other two are run by the
     * user from inside the environment, where it can.
     */
    @Test public void aNixPrefixGetsScriptsItCanActuallyRun() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);

        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());

        assertTrue("the launcher execs this one itself",
            text(installer.serverScript()).startsWith("#!/system/bin/sh\n"));
        String inside = "#!" + new File(bin, "sh").getPath() + "\n";
        assertTrue(text(installer.preferenceScript()).startsWith(inside));
        assertEquals(inside + "echo tried every profile\n", text(installer.gpuSetupScript()));
    }

    /**
     * The X server ships only its built-in {@code fixed} and {@code cursor}, and an old core-font
     * client quits when it cannot find the font it asked for by name. nixpkgs puts every font
     * package in a store entry of its own, so the path is gathered when the server starts.
     */
    @Test public void theNixServerScriptGathersAFontPathOutOfTheStore() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);

        installer.install();

        String server = text(installer.serverScript());
        // Where current nixpkgs puts them (font-misc-misc), and where the old xorg.* set did.
        for (String dir : new String[]{"misc", "75dpi", "100dpi", "TTF", "Type1", "cyrillic"}) {
            assertTrue(dir, server.contains("/nix/store/*/share/fonts/X11/" + dir));
            assertTrue(dir, server.contains("/nix/store/*/lib/X11/fonts/" + dir));
        }
        assertTrue("a derivation is not a font package",
            server.contains("case \"$dir\" in *.drv/*) continue ;; esac"));
        assertTrue("the server reads the index, so a directory without one is not a font dir",
            server.contains("[ -f \"$dir/fonts.dir\" ] || continue"));
        assertTrue("no font package at all leaves the server started as it always was",
            server.contains("[ -z \"$TERMUX_X11_FONT_PATH\" ] || set -- -fp "
                + "\"$TERMUX_X11_FONT_PATH\" \"$@\""));
        assertTrue("the process still shows the arguments the user asked for",
            server.contains("--nice-name=\"$TERMUX_X11_NICE_NAME\""));
    }

    /** Termux keeps its fonts where the server already looks, and its script says nothing new. */
    @Test public void theTermuxServerScriptHasNoFontPathOfItsOwn() throws IOException {
        installer.install();

        String server = text(installer.serverScript());
        assertFalse(server.contains("-fp"));
        assertFalse(server.contains("TERMUX_X11_FONT_PATH"));
        assertTrue(server.contains("--nice-name=\"termux-x11 com.termux.test $*\""));
    }

    /** Android's own shell has no {@code trap -p}, and must not be made to complain about it. */
    @Test public void theNotifyProbeSaysNothingOnAShellWithoutTrapP() {
        assertTrue(X11CliInstaller.serverScript("com.termux.test", "/system/bin/sh")
            .contains("$(trap -p USR1 2>/dev/null)"));
    }

    @Test public void theKeyboardDataIsLinkedOutOfTheStoreAndKeptCurrent() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        File first = xkeyboardConfig(prefix, "0amyq-xkeyboard-config-2.47");
        assertTrue(new File(prefix, "nix/store/0amyq-xkeyboard-config-2.47")
            .setLastModified(1_000_000L));

        assertFalse("nothing in the prefix itself", X11CliInstaller.hasKeyboardData(prefix));
        installer.install();
        File link = new File(prefix, "share/X11/xkb");
        assertTrue(Files.isSymbolicLink(link.toPath()));
        assertEquals(first.toPath(), Files.readSymbolicLink(link.toPath()));
        assertTrue("which is what the Display page reads", X11CliInstaller.hasKeyboardData(prefix));

        // A switch rebuilds the package into a store path of a new name; the next pass follows it
        // even though everything else is already up to date.
        File second = xkeyboardConfig(prefix, "bbbb-xkeyboard-config-2.48");
        assertTrue(new File(prefix, "nix/store/bbbb-xkeyboard-config-2.48")
            .setLastModified(2_000_000L));
        assertEquals(X11CliInstaller.Result.UP_TO_DATE, installer.install());
        assertEquals(second.toPath(), Files.readSymbolicLink(link.toPath()));
    }

    @Test public void aKeyboardDirectorySomebodyElsePutThereIsLeftAlone() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        xkeyboardConfig(prefix, "0amyq-xkeyboard-config-2.47");
        File theirs = new File(prefix, "share/X11/xkb");
        assertTrue(theirs.mkdirs());

        installer.install();

        assertFalse(Files.isSymbolicLink(theirs.toPath()));
        assertTrue(theirs.isDirectory());
    }

    @Test public void uninstallTakesOurKeyboardLinkOutAndLeavesARealDirectory() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        xkeyboardConfig(prefix, "0amyq-xkeyboard-config-2.47");
        installer.install();
        File link = new File(prefix, "share/X11/xkb");
        assertTrue(Files.isSymbolicLink(link.toPath()));

        installer.uninstall();
        assertFalse(Files.isSymbolicLink(link.toPath()));
        assertFalse(link.exists());
    }

    /** A symlink at that path pointing anywhere but the store is not ours to take out. */
    @Test public void someoneElsesSymlinkAtThatPathIsLeftAlone() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        xkeyboardConfig(prefix, "0amyq-xkeyboard-config-2.47");
        File theirs = temp.newFolder("their-xkb");
        File link = new File(prefix, "share/X11/xkb");
        assertTrue(link.getParentFile().mkdirs());
        Files.createSymbolicLink(link.toPath(), theirs.toPath());

        installer.install();
        installer.uninstall();

        assertTrue("still theirs, still pointing where they put it",
            Files.isSymbolicLink(link.toPath()));
        assertEquals(theirs.toPath(), Files.readSymbolicLink(link.toPath()));
    }

    /** No xkeyboard-config installed yet: nothing to point at, so nothing is pointed anywhere. */
    @Test public void withNoKeyboardDataInTheStoreNoLinkIsMade() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);

        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());

        File link = new File(prefix, "share/X11/xkb");
        assertFalse(Files.isSymbolicLink(link.toPath()));
        assertFalse("nothing dangling where the server will look", link.exists());
        assertFalse(X11CliInstaller.hasKeyboardData(prefix));
    }

    /**
     * The server execs {@code -xstartup} from Android's side and refuses an argument past about
     * 128 characters, so on nix the whole login line is baked into a wrapper and the argument is
     * that one short path.
     */
    @Test public void aNixPrefixGetsAWindowManagerWrapperWithTheWholeLoginLineInIt()
            throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        File openbox = profileBin(prefix, "openbox");
        installer = new X11CliInstaller(bin, libexec, "com.termux.test", assets(LOADER), "openbox");

        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());

        assertTrue(openbox.canExecute());
        assertEquals("#!/system/bin/sh\n"
                + X11CliInstaller.MARKER_PREAMBLE + " — do not edit; the launcher rewrites it\n"
                + "exec " + new File(prefix, "bin/login").getPath() + " openbox --config-file "
                + X11CliInstaller.OPENBOX_RC_PATH + "\n",
            text(installer.wmScript()));
        assertTrue(installer.wmScript().canExecute());
        assertTrue("the whole point of the wrapper is that this path is short",
            X11CliInstaller.WM_SCRIPT_PATH.length() < 128);
    }

    /** A window manager that is not installed, or none configured, leaves no wrapper behind. */
    @Test public void aWrapperIsTakenOutAgainWhenThereIsNoWindowManagerToRun() throws IOException {
        File prefix = new File(temp.getRoot(), "usr");
        nixProfile(prefix);
        profileBin(prefix, "openbox");
        installer = new X11CliInstaller(bin, libexec, "com.termux.test", assets(LOADER), "openbox");
        installer.install();
        assertTrue(installer.wmScript().exists());

        installer = new X11CliInstaller(bin, libexec, "com.termux.test", assets(LOADER), "");
        installer.install();

        assertFalse(installer.wmScript().exists());
    }

    @Test public void aTermuxPrefixIsLeftExactlyAsItWas() throws IOException {
        // No profile link, so nothing nix-shaped happens: the bash shebang, no xkb link, and no
        // wrapper — there the server is handed the window manager's own command line.
        installer = new X11CliInstaller(bin, libexec, "com.termux.test", assets(LOADER), "openbox");

        assertEquals(X11CliInstaller.Result.INSTALLED, installer.install());
        assertTrue(text(installer.serverScript())
            .startsWith("#!" + new File(bin, "bash").getPath() + "\n"));
        assertFalse(new File(temp.getRoot(), "usr/share/X11/xkb").exists());
        assertFalse(installer.wmScript().exists());
    }
}
