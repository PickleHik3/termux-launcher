package com.termux.app.store;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The prefix writes: what a fresh install leaves behind, what an upgrade rewrites, and the two
 * things the installer must never do — overwrite a {@code tlstore}/{@code tl}/{@code tls} it did
 * not write, or fail the whole install because the signing key has not shipped yet.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TlstoreInstallerTest {

    // The real asset (built on feat/tlstore-cli) carries the marker in its own first lines, the
    // way X11's generated scripts do; the installer copies its bytes through unchanged.
    private static final byte[] TLSTORE_SCRIPT =
        ("#!/usr/bin/env sh\n" + TlstoreInstaller.MARKER_PREAMBLE + "\necho tlstore\n")
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] CATALOG =
        "# tlstore catalog\tserial=2026090601\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUSTED_KEY =
        "untrusted comment: test key\nRWtesttesttesttesttesttesttesttesttesttesttesttest\n"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOTD =
        "#!/system/bin/sh\nprintf '%s\\n' 'Welcome to Termux Launcher.'\n"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] TLSTORE_UI = "ELF-fixture-arm64-v8a".getBytes(StandardCharsets.UTF_8);

    private static TlstoreInstaller.AssetSource assets(byte[] script, boolean includeTrustedKey) {
        return assets(script, includeTrustedKey, MOTD);
    }

    private static TlstoreInstaller.AssetSource assets(byte[] script, boolean includeTrustedKey,
                                                        byte[] motd) {
        return assets(script, includeTrustedKey, motd, null);
    }

    /** {@code tlstoreUi}, when not null, is served under {@code tlstore/tlstore-ui-arm64-v8a}. */
    private static TlstoreInstaller.AssetSource assets(byte[] script, boolean includeTrustedKey,
                                                        byte[] motd, byte[] tlstoreUi) {
        return name -> {
            if (name.equals("tlstore/tlstore")) return new ByteArrayInputStream(script);
            if (name.equals("tlstore/catalog.tsv")) return new ByteArrayInputStream(CATALOG);
            if (name.equals("tlstore/trusted.pub")) {
                if (!includeTrustedKey) throw new FileNotFoundException(name);
                return new ByteArrayInputStream(TRUSTED_KEY);
            }
            if (name.equals("motd.sh")) return new ByteArrayInputStream(motd);
            if (name.equals("tlstore/tlstore-ui-arm64-v8a")) {
                if (tlstoreUi == null) throw new FileNotFoundException(name);
                return new ByteArrayInputStream(tlstoreUi);
            }
            throw new FileNotFoundException(name);
        };
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File bin;
    private File libexec;
    /** {@code ~/.termux}; its parent ({@code home}) is the simulated home directory. */
    private File dataHome;
    private TlstoreInstaller installer;

    @Before public void prefix() throws IOException {
        bin = temp.newFolder("usr", "bin");
        libexec = new File(temp.getRoot(), "usr/libexec/termux-launcher/tlstore");
        File home = temp.newFolder("home");
        dataHome = new File(home, ".termux");
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true));
    }

    private static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void aFreshPrefixGetsTheCliTheAliasesAndTheCatalog() throws IOException {
        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(TLSTORE_SCRIPT, Files.readAllBytes(installer.tlstoreScript().toPath()));
        assertFalse(Files.isSymbolicLink(installer.tlstoreScript().toPath()));
        assertTrue(installer.tlstoreScript().canExecute());

        assertTrue(Files.isSymbolicLink(installer.tlAlias().toPath()));
        assertEquals("tlstore", Files.readSymbolicLink(installer.tlAlias().toPath()).toString());
        assertTrue(Files.isSymbolicLink(installer.tlsAlias().toPath()));
        assertEquals("tlstore", Files.readSymbolicLink(installer.tlsAlias().toPath()).toString());

        assertArrayEquals(CATALOG, Files.readAllBytes(installer.catalogFile().toPath()));
        assertFalse(installer.catalogFile().canExecute());
        assertArrayEquals(TRUSTED_KEY, Files.readAllBytes(installer.trustedKeyFile().toPath()));

        assertEquals(TlstoreInstaller.MARKER_PREAMBLE + " v" + TlstoreInstaller.VERSION
            + " com.termux.test 0.0-test\n", text(installer.markerFile()));
        assertNull(installer.foreignCommandName());
        assertEquals("no temp file left beside the result", 0,
            countTempFiles(bin) + countTempFiles(libexec));
    }

    @Test public void aSecondInstallIsUpToDate() {
        installer.install();

        assertEquals(TlstoreInstaller.Result.UP_TO_DATE, installer.install());
    }

    @Test public void aBumpedVersionRewritesInPlace() throws IOException {
        installer.install();
        // A previous launcher wrote an older version; the marker mismatch must trigger a rewrite.
        Files.write(installer.markerFile().toPath(),
            (TlstoreInstaller.MARKER_PREAMBLE + " v0 com.termux.test\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] newerScript = ("#!/usr/bin/env sh\n" + TlstoreInstaller.MARKER_PREAMBLE
            + "\necho newer\n").getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(newerScript, true));

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(newerScript, Files.readAllBytes(installer.tlstoreScript().toPath()));
        assertTrue(text(installer.markerFile()).contains(" v" + TlstoreInstaller.VERSION + " "));
    }

    @Test public void aForeignTlIsLeftAloneAndNothingIsOverwritten() throws IOException {
        File foreignTl = installer.tlAlias();
        Files.write(foreignTl.toPath(), "#!/bin/sh\necho mine\n".getBytes(StandardCharsets.UTF_8));

        TlstoreInstaller.Result result = installer.install();

        assertEquals(TlstoreInstaller.Result.Kind.FOREIGN_COMMAND, result.kind);
        assertEquals("tl", result.foreignCommand);
        assertEquals("#!/bin/sh\necho mine\n", text(foreignTl));
        assertFalse("nothing else was written either", installer.tlstoreScript().exists());
        assertFalse(installer.tlsAlias().exists());
        assertFalse(installer.catalogFile().exists());
    }

    @Test public void noBinDirectoryMeansNoPrefixYet() {
        installer = new TlstoreInstaller(new File(temp.getRoot(), "missing/bin"), libexec, dataHome,
            "com.termux.test", "0.0-test", assets(TLSTORE_SCRIPT, true));

        assertEquals(TlstoreInstaller.Result.NO_PREFIX, installer.install());
    }

    @Test public void aMissingTrustedKeyAssetStillInstallsEverythingElse() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, false));

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertTrue(installer.tlstoreScript().exists());
        assertTrue(Files.isSymbolicLink(installer.tlAlias().toPath()));
        assertTrue(installer.catalogFile().exists());
        assertFalse("the key may ship later", installer.trustedKeyFile().exists());
        assertTrue(installer.markerFile().exists());
    }

    @Test public void aFreshInstallWritesTheMotdWithTheAssetBytesAndMode() throws IOException {
        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(MOTD, Files.readAllBytes(installer.motdFile().toPath()));
        assertFalse(Files.isSymbolicLink(installer.motdFile().toPath()));
        assertTrue(installer.motdFile().canExecute());
    }

    @Test public void aChangedMotdAssetRewritesAnUntouchedFile() throws IOException {
        installer.install();
        // A previous launcher wrote an older version; the marker mismatch must trigger a rewrite,
        // the same way it does for the CLI script in aBumpedVersionRewritesInPlace.
        Files.write(installer.markerFile().toPath(),
            (TlstoreInstaller.MARKER_PREAMBLE + " v0 com.termux.test\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] newerMotd = "#!/system/bin/sh\nprintf '%s\\n' 'newer motd'\n"
            .getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, newerMotd));

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(newerMotd, Files.readAllBytes(installer.motdFile().toPath()));
    }

    @Test public void aUserEditedMotdIsLeftAloneAcrossAVersionBump() throws IOException {
        installer.install();
        Files.write(installer.motdFile().toPath(), "# mine now\n".getBytes(StandardCharsets.UTF_8));
        Files.write(installer.markerFile().toPath(),
            (TlstoreInstaller.MARKER_PREAMBLE + " v0 com.termux.test\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] newerMotd = "#!/system/bin/sh\nprintf '%s\\n' 'newer motd'\n"
            .getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, newerMotd));

        TlstoreInstaller.Result result = installer.install();

        assertEquals(TlstoreInstaller.Result.INSTALLED, result);
        assertEquals("the user's own motd.sh must survive", "# mine now\n",
            text(installer.motdFile()));
    }

    @Test public void aMissingHomeDirectorySkipsTheMotdButInstallsTheRest() throws IOException {
        File noHomeYetDataHome = new File(temp.getRoot(), "no-home-yet/.termux");
        installer = new TlstoreInstaller(bin, libexec, noHomeYetDataHome, "com.termux.test",
            "0.0-test", assets(TLSTORE_SCRIPT, true));

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertFalse("nothing to write into; the home directory itself is missing",
            noHomeYetDataHome.exists());
        assertTrue(installer.tlstoreScript().exists());
        assertTrue(installer.catalogFile().exists());
        assertTrue(installer.markerFile().exists());
    }

    // --- tlstore-ui ---

    @Test public void aBundledUiBinaryIsInstalledAndExecutable() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(TLSTORE_UI, Files.readAllBytes(installer.tlstoreUiFile().toPath()));
        assertFalse(Files.isSymbolicLink(installer.tlstoreUiFile().toPath()));
        assertTrue(installer.tlstoreUiFile().canExecute());
    }

    @Test public void aDeviceAbiWithNoBundledUiBinaryGetsNothingWritten() throws IOException {
        // The APK only carries arm64-v8a; this "device" only reports x86_64.
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"x86_64"});

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertFalse("nothing to fall back from but the list, so nothing is written",
            installer.tlstoreUiFile().exists());
        // Everything else still installs; a missing tlstore-ui is not a failure.
        assertTrue(installer.tlstoreScript().exists());
    }

    @Test public void aChangedUiBinaryIsRewrittenOnAVersionBump() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});
        installer.install();
        Files.write(installer.markerFile().toPath(),
            (TlstoreInstaller.MARKER_PREAMBLE + " v0 com.termux.test\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] newerUi = "ELF-fixture-newer".getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, newerUi), new String[]{"arm64-v8a"});

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertArrayEquals(newerUi, Files.readAllBytes(installer.tlstoreUiFile().toPath()));
    }

    @Test public void aForeignUiBinaryIsLeftAloneAcrossAVersionBump() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});
        installer.install();
        Files.write(installer.tlstoreUiFile().toPath(), "not ours".getBytes(StandardCharsets.UTF_8));
        Files.write(installer.markerFile().toPath(),
            (TlstoreInstaller.MARKER_PREAMBLE + " v0 com.termux.test\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] newerUi = "ELF-fixture-newer".getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, newerUi), new String[]{"arm64-v8a"});

        TlstoreInstaller.Result result = installer.install();

        assertEquals(TlstoreInstaller.Result.INSTALLED, result);
        assertEquals("the foreign file must survive", "not ours",
            text(installer.tlstoreUiFile()));
    }

    // --- tlstore-ui refresh on every start, even when the outer marker is already up to date ---

    @Test public void anUnchangedUiBinaryIsUntouchedOnAnUpToDateInstall() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});
        installer.install();
        long mtimeBefore = installer.tlstoreUiFile().lastModified();

        // Same applicationId, same versionName, same bundled binary: the outer marker matches,
        // so this is the UP_TO_DATE path — the one that used to skip tlstore-ui entirely.
        assertEquals(TlstoreInstaller.Result.UP_TO_DATE, installer.install());

        assertArrayEquals(TLSTORE_UI, Files.readAllBytes(installer.tlstoreUiFile().toPath()));
        assertEquals("untouched, not just byte-identical", mtimeBefore,
            installer.tlstoreUiFile().lastModified());
    }

    @Test public void aChangedUiBinaryIsRewrittenEvenWithTheSameVersionName() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});
        installer.install();
        // Same applicationId and versionName as a fresh build during development would carry —
        // the outer marker does not change, only the bundled asset's bytes do.
        byte[] newerUi = "ELF-fixture-newer-same-versionName".getBytes(StandardCharsets.UTF_8);
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, newerUi), new String[]{"arm64-v8a"});

        assertEquals(TlstoreInstaller.Result.UP_TO_DATE, installer.install());

        assertArrayEquals("a changed binary must reach the phone even without a version bump",
            newerUi, Files.readAllBytes(installer.tlstoreUiFile().toPath()));
    }

    @Test public void aForeignUiBinaryIsLeftAloneOnAnUpToDateInstall() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});
        installer.install();
        Files.write(installer.tlstoreUiFile().toPath(), "not ours".getBytes(StandardCharsets.UTF_8));
        // Same applicationId/versionName/asset as the first install; only the on-disk file
        // changed, out from under this class — the outer marker still matches.
        installer = new TlstoreInstaller(bin, libexec, dataHome, "com.termux.test", "0.0-test",
            assets(TLSTORE_SCRIPT, true, MOTD, TLSTORE_UI), new String[]{"arm64-v8a"});

        assertEquals(TlstoreInstaller.Result.UP_TO_DATE, installer.install());

        assertEquals("the foreign file must survive", "not ours",
            text(installer.tlstoreUiFile()));
    }

    private static int countTempFiles(File dir) {
        String[] names = dir.list((d, name) -> name.endsWith(".tmp"));
        return names == null ? 0 : names.length;
    }
}
