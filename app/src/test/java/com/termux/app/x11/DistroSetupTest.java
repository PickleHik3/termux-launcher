package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the launcher reads a container as still needing, which is the whole of what the Display
 * place's offer is decided from. Every reading here is taken against a fixture rootfs; nothing in
 * {@link DistroSetup} runs a process.
 */
public class DistroSetupTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    /** A container directory with a rootfs, and by default nothing else in it. */
    private File rootfs(File containers, String name) throws IOException {
        File rootfs = new File(containers, name + "/rootfs");
        assertTrue(rootfs.mkdirs());
        return rootfs;
    }

    private void touch(File rootfs, String path) throws IOException {
        File file = new File(rootfs, path);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.write(file.toPath(), "x".getBytes(StandardCharsets.UTF_8));
    }

    private void apt(File rootfs) throws IOException {
        touch(rootfs, "usr/bin/apt-get");
    }

    private void user(File rootfs) throws IOException {
        File passwd = new File(rootfs, "etc/passwd");
        passwd.getParentFile().mkdirs();
        Files.write(passwd.toPath(), ("root:x:0:0:root:/root:/bin/bash\n"
            + "user:x:1000:1000::/home/user:/bin/bash\n").getBytes(StandardCharsets.UTF_8));
    }

    private void fonts(File rootfs) throws IOException {
        touch(rootfs, "usr/share/fonts/X11/misc/fixed.pcf.gz");
    }

    private List<String> keys(List<DistroSetup.Step> steps) {
        List<String> out = new java.util.ArrayList<>();
        for (DistroSetup.Step step : steps) out.add(step.key);
        return out;
    }

    // --- when a container is "not set up" ---------------------------------------------------

    @Test public void nothingInstalledAtAllIsTheWholeFlow() throws IOException {
        File containers = temp.newFolder("containers");

        DistroSetup.Readiness readiness = DistroSetup.read(containers);

        assertNull(readiness.container);
        assertTrue(readiness.needsSetup());
        assertEquals(Arrays.asList("distro", "user", "fonts"), keys(readiness.missing));
    }

    @Test public void aDirectoryThatWasNeverThereReadsTheSameAsAnEmptyOne() {
        assertEquals("|distro,user,fonts",
            DistroSetup.read(new File(temp.getRoot(), "never")).signature());
    }

    @Test public void aRootOnlyContainerNeedsAnAccount() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        fonts(rootfs);

        DistroSetup.Readiness readiness = DistroSetup.read(containers);

        assertEquals("debian", readiness.container);
        assertEquals(Collections.singletonList("user"), keys(readiness.missing));
    }

    @Test public void aContainerWithNoXFontsNeedsThem() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);

        assertEquals(Collections.singletonList("fonts"),
            keys(DistroSetup.read(containers).missing));
    }

    @Test public void anEmptyFontDirectoryIsNotFonts() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);
        assertTrue(new File(rootfs, "usr/share/fonts/X11/misc").mkdirs());

        assertEquals(Collections.singletonList("fonts"),
            keys(DistroSetup.read(containers).missing));
    }

    @Test public void fontsOutsideDebiansLayoutStillCount() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);
        touch(rootfs, "usr/share/fonts/misc/fixed.pcf.gz");

        assertFalse(DistroSetup.read(containers).needsSetup());
    }

    @Test public void aContainerWithAnAccountAndFontsIsSetUp() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);
        fonts(rootfs);

        assertFalse(DistroSetup.read(containers).needsSetup());
    }

    @Test public void oneWorkingContainerSilencesTheOfferForTheOthers() throws IOException {
        File containers = temp.newFolder("containers");
        File bare = rootfs(containers, "aaa-bare");
        apt(bare);
        File ready = rootfs(containers, "debian");
        apt(ready);
        user(ready);
        fonts(ready);

        assertFalse(DistroSetup.read(containers).needsSetup());
    }

    @Test public void aContainerWithNoAptIsLeftAlone() throws IOException {
        File containers = temp.newFolder("containers");
        rootfs(containers, "archlinux");

        DistroSetup.Readiness readiness = DistroSetup.read(containers);

        assertFalse(readiness.needsSetup());
        assertNull(readiness.container);
    }

    @Test public void anAptContainerIsStillFoundBesideOneThatIsNot() throws IOException {
        File containers = temp.newFolder("containers");
        rootfs(containers, "archlinux");
        File debian = rootfs(containers, "debian");
        apt(debian);

        assertEquals("debian", DistroSetup.read(containers).container);
    }

    // --- the dismissal signature -------------------------------------------------------------

    @Test public void theSignatureNamesTheSituationNotTheMoment() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);

        assertEquals("debian|user,fonts", DistroSetup.read(containers).signature());

        user(rootfs);
        assertEquals("debian|fonts", DistroSetup.read(containers).signature());
    }

    @Test public void installingAContainerChangesTheSignatureFromHavingNone() throws IOException {
        File containers = temp.newFolder("containers");
        assertEquals("|distro,user,fonts", DistroSetup.read(containers).signature());

        apt(rootfs(containers, "debian"));
        assertEquals("debian|user,fonts", DistroSetup.read(containers).signature());
    }
}
