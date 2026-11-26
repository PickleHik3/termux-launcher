package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class RuntimeFolderProfileTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void theShTextFillsTheFolderOnlyWhenEmpty() {
        assertEquals("# written by termux-launcher for the runtime folder; it rewrites this file.\n"
            + "# Every shell on the launcher shares one XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + "if [ -z \"${XDG_RUNTIME_DIR:-}\" ]; then\n"
            + "    XDG_RUNTIME_DIR=\"${TMPDIR:-/p/tmp}\"\n"
            + "    export XDG_RUNTIME_DIR\n"
            + "fi\n", RuntimeFolderProfile.prefixSh("/p/tmp"));
    }

    @Test public void theFishTextFillsTheFolderOnlyWhenEmpty() {
        assertEquals("# written by termux-launcher for the runtime folder; it rewrites this file.\n"
            + "# Every shell on the launcher shares one XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + "if test -z \"$XDG_RUNTIME_DIR\"\n"
            + "    if test -n \"$TMPDIR\"\n"
            + "        set -gx XDG_RUNTIME_DIR \"$TMPDIR\"\n"
            + "    else\n"
            + "        set -gx XDG_RUNTIME_DIR \"/p/tmp\"\n"
            + "    end\n"
            + "end\n", RuntimeFolderProfile.prefixFish("/p/tmp"));
    }

    @Test public void theContainerGetsAPrivateFolderMadeAtLogin() {
        assertEquals("# written by termux-launcher for the runtime folder; it rewrites this file.\n"
            + "# A login shell here gets a private XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + "if [ -z \"${XDG_RUNTIME_DIR:-}\" ]; then\n"
            + "    XDG_RUNTIME_DIR=\"${TMPDIR:-/tmp}/termux-x11-runtime\"\n"
            + "    export XDG_RUNTIME_DIR\n"
            + "    mkdir -p \"$XDG_RUNTIME_DIR\" && chmod 700 \"$XDG_RUNTIME_DIR\"\n"
            + "fi\n", RuntimeFolderProfile.containerSh());
    }

    @Test public void aLaunchBlockIsTheShTextWithoutComments() {
        assertTrue(RuntimeFolderProfile.prefixSh("/p/tmp").endsWith(RuntimeFolderProfile.prefixLaunchBlock("/p/tmp")));
    }

    @Test public void theFileIsWrittenThenKeptUnchanged() throws IOException {
        File root = temp.newFolder("root");
        String text = RuntimeFolderProfile.prefixSh("/p/tmp");

        assertEquals(DistroDisplayProfile.Result.WRITTEN,
            DistroDisplayProfile.write(root, RuntimeFolderProfile.SH_PATH, text));
        File file = new File(root, RuntimeFolderProfile.SH_PATH);
        assertTrue(file.setLastModified(1_000_000L));
        assertEquals(DistroDisplayProfile.Result.KEPT,
            DistroDisplayProfile.write(root, RuntimeFolderProfile.SH_PATH, text));
        assertEquals(1_000_000L, file.lastModified());
        assertEquals(text, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void aChangedTextIsRewrittenAndAForeignFileIsNot() throws IOException {
        File root = temp.newFolder("root");
        DistroDisplayProfile.write(root, RuntimeFolderProfile.FISH_PATH, RuntimeFolderProfile.prefixFish("/old"));
        assertEquals(DistroDisplayProfile.Result.WRITTEN,
            DistroDisplayProfile.write(root, RuntimeFolderProfile.FISH_PATH, RuntimeFolderProfile.prefixFish("/p/tmp")));

        File own = new File(root, RuntimeFolderProfile.SH_PATH);
        assertTrue(own.getParentFile().mkdirs());
        Files.write(own.toPath(), "export XDG_RUNTIME_DIR=/mine\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(DistroDisplayProfile.Result.FOREIGN,
            DistroDisplayProfile.write(root, RuntimeFolderProfile.SH_PATH, RuntimeFolderProfile.prefixSh("/p/tmp")));
        assertEquals("export XDG_RUNTIME_DIR=/mine\n",
            new String(Files.readAllBytes(own.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void aContainerWriteLandsAtTheProfilePath() throws IOException {
        File rootfs = temp.newFolder("rootfs");
        RuntimeFolderProfile.writeContainer(rootfs);
        assertEquals(RuntimeFolderProfile.containerSh(),
            new String(Files.readAllBytes(new File(rootfs, RuntimeFolderProfile.SH_PATH).toPath()),
                StandardCharsets.UTF_8));
    }
}
