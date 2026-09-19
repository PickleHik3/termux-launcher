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
 * D9: what the launcher reads a container as still needing, and the script it would run about it.
 * Every reading here is taken against a fixture rootfs; nothing in {@link DistroSetup} runs a
 * process, so the only things left to a device are whether the commands succeed.
 */
public class DistroSetupTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final DistroSetup.Messages MESSAGES = new DistroSetup.Messages(
        "Setting up.", "Getting Linux.", "Making your account.", "Installing fonts.",
        "Installing graphics support.", "Installing apps.",
        "Linux could not be downloaded.", "Your account could not be made.",
        "The fonts could not be installed.", "Graphics support could not be installed.",
        "The apps could not be installed.", "Nothing is lost — it isn't wasted.", "Ready.");

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
        assertTrue(readiness.startsFromNothing());
        assertEquals(Arrays.asList("distro", "user", "fonts"), keys(readiness.missing));
        assertEquals("debian", readiness.targetContainer());
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
        assertFalse(readiness.startsFromNothing());
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

        DistroSetup.Readiness readiness = DistroSetup.read(containers);

        assertFalse(readiness.needsSetup());
        assertTrue(DistroSetup.plan(readiness).isEmpty());
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

    // --- what the run would do ---------------------------------------------------------------

    @Test public void thePlanAlwaysEndsWithGraphicsAndTheStarterApps() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);

        assertEquals(Arrays.asList("fonts", "graphics", "apps"),
            keys(DistroSetup.plan(DistroSetup.read(containers))));
    }

    @Test public void aFreshPhoneGetsProotDistroTheContainerTheAccountAndTheRest() throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        assertTrue(script.contains(
            "command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro"));
        assertTrue(script.contains("] || proot-distro install debian"));
        assertTrue(script.contains("[ -d '" + containers.getAbsolutePath() + "/debian/rootfs' ]"));
        assertTrue(script.contains("id user >/dev/null 2>&1 || useradd -m -s "));
        assertTrue(script.contains("apt-get install -y xfonts-base fonts-dejavu-core"));
        assertTrue(script.contains("apt-get install -y libgl1 libgl1-mesa-dri"));
        assertTrue(script.contains("apt-get install -y x11-apps mousepad"));
    }

    @Test public void everyContainerStepRunsInsideTheContainerThatWasFound() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "trixie");
        apt(rootfs);

        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        assertFalse(script.contains("proot-distro install"));
        assertFalse(script.contains("debian"));
        // account, the one package-list refresh, fonts, graphics, apps.
        assertEquals(5, script.split("proot-distro login trixie -- /bin/sh -c ", -1).length - 1);
    }

    @Test public void thePackageListIsRefreshedOnceAndItsFailureIsNotTheStepsFailure()
            throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        assertEquals(1, script.split("apt-get update", -1).length - 1);
        assertTrue(script.contains("'apt-get update' || true"));
    }

    @Test public void aStepThatFailsSaysSoAndStopsThere() throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        for (String message : Arrays.asList(MESSAGES.distroFailed, MESSAGES.userFailed,
                MESSAGES.fontsFailed, MESSAGES.graphicsFailed, MESSAGES.appsFailed)) {
            assertTrue(message, script.contains("|| _fail '" + message + "'"));
        }
        assertTrue(script.contains("_fail() {"));
        assertTrue(script.contains("exit 1; }"));
    }

    @Test public void theFailureAlsoSaysTheRunCanBeStartedAgain() throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        // An apostrophe in product copy is the one thing that can break a generated shell line.
        assertTrue(script.contains("'Nothing is lost — it isn'\\''t wasted.'"));
    }

    @Test public void everyStepSaysWhatItIsDoingBeforeItDoesIt() throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        assertTrue(script.indexOf("_say '" + MESSAGES.gettingDistro + "'")
            < script.indexOf("proot-distro install"));
        assertTrue(script.indexOf("_say '" + MESSAGES.installingFonts + "'")
            < script.indexOf("xfonts-base"));
        assertTrue(script.trim().endsWith("_say '" + MESSAGES.done + "'"));
    }

    @Test public void aContainerThatNeedsNothingProducesNoCommands() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        apt(rootfs);
        user(rootfs);
        fonts(rootfs);

        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        assertFalse(script.contains("proot-distro"));
        assertFalse(script.contains("apt-get"));
    }

    @Test public void theStepsAreSafeToRunTwice() throws IOException {
        File containers = temp.newFolder("containers");
        String script = DistroSetup.script(DistroSetup.read(containers), MESSAGES);

        // Each step either asks first or is idempotent on its own: an install that is already
        // there is what makes "start it again" pick up where it stopped.
        assertTrue(script.contains("command -v proot-distro"));
        assertTrue(script.contains("[ -d "));
        assertTrue(script.contains("id user >/dev/null 2>&1 ||"));
        assertEquals(3, script.split("apt-get install -y", -1).length - 1);
    }
}
