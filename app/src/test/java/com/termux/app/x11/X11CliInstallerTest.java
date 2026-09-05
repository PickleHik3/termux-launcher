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
        assertTrue(X11CliInstaller.openboxRcContent(7).contains("<margins><top>7</top>"));
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
}
