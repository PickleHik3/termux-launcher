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

    private static TlstoreInstaller.AssetSource assets(byte[] script, boolean includeTrustedKey) {
        return name -> {
            if (name.equals("tlstore/tlstore")) return new ByteArrayInputStream(script);
            if (name.equals("tlstore/catalog.tsv")) return new ByteArrayInputStream(CATALOG);
            if (name.equals("tlstore/trusted.pub")) {
                if (!includeTrustedKey) throw new FileNotFoundException(name);
                return new ByteArrayInputStream(TRUSTED_KEY);
            }
            throw new FileNotFoundException(name);
        };
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File bin;
    private File libexec;
    private TlstoreInstaller installer;

    @Before public void prefix() throws IOException {
        bin = temp.newFolder("usr", "bin");
        libexec = new File(temp.getRoot(), "usr/libexec/termux-launcher/tlstore");
        installer = new TlstoreInstaller(bin, libexec, "com.termux.test",
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
            + " com.termux.test\n", text(installer.markerFile()));
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
        installer = new TlstoreInstaller(bin, libexec, "com.termux.test",
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
        installer = new TlstoreInstaller(new File(temp.getRoot(), "missing/bin"), libexec,
            "com.termux.test", assets(TLSTORE_SCRIPT, true));

        assertEquals(TlstoreInstaller.Result.NO_PREFIX, installer.install());
    }

    @Test public void aMissingTrustedKeyAssetStillInstallsEverythingElse() throws IOException {
        installer = new TlstoreInstaller(bin, libexec, "com.termux.test",
            assets(TLSTORE_SCRIPT, false));

        assertEquals(TlstoreInstaller.Result.INSTALLED, installer.install());

        assertTrue(installer.tlstoreScript().exists());
        assertTrue(Files.isSymbolicLink(installer.tlAlias().toPath()));
        assertTrue(installer.catalogFile().exists());
        assertFalse("the key may ship later", installer.trustedKeyFile().exists());
        assertTrue(installer.markerFile().exists());
    }

    private static int countTempFiles(File dir) {
        String[] names = dir.list((d, name) -> name.endsWith(".tmp"));
        return names == null ? 0 : names.length;
    }
}
