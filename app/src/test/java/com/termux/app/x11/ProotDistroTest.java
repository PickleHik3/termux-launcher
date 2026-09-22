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
import java.util.Arrays;
import java.util.List;

/** Which containers are installed, who a login runs as, and the line that runs an app in one. */
public class ProotDistroTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File rootfs(File containers, String name) throws IOException {
        File rootfs = new File(containers, name + "/rootfs");
        assertTrue(rootfs.mkdirs());
        return rootfs;
    }

    private void write(File dir, String path, String content) throws IOException {
        File file = new File(dir, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    // --- which containers are there --------------------------------------------------------

    @Test public void onlyDirectoriesWithARootfsCount() throws IOException {
        File containers = temp.newFolder("containers");
        rootfs(containers, "debian");
        rootfs(containers, "archlinux");
        assertTrue(new File(containers, "half-downloaded").mkdirs());
        write(containers, "notes.txt", "not a container");

        List<ProotDistro.Container> found = ProotDistro.containers(containers);

        assertEquals(Arrays.asList("archlinux", "debian"),
            Arrays.asList(found.get(0).name, found.get(1).name));
        assertNull(ProotDistro.byName(found, "half-downloaded"));
    }

    @Test public void nothingIsListedWhereProotDistroWasNeverUsed() {
        assertTrue(ProotDistro.containers(new File(temp.getRoot(), "never")).isEmpty());
    }

    @Test public void aContainerKnowsWhereItsDesktopFilesAndIconsLive() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", "root:x:0:0:root:/root:/bin/bash\n"
            + "amal:x:1000:1000::/home/amal:/bin/bash\n");

        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals(Arrays.asList(
                new File(rootfs, "usr/share/applications"),
                new File(rootfs, "usr/local/share/applications"),
                new File(rootfs, "home/amal/.local/share/applications")),
            debian.applicationDirs());
        assertEquals(new File(rootfs, "usr"), debian.iconPrefix());
        assertEquals(new File(rootfs, "usr/share/pixmaps/x.png"),
            debian.inside("/usr/share/pixmaps/x.png"));
    }

    @Test public void thePrefixIsItsOwnContainerAndResolvesPathsAsTheyAre() {
        ProotDistro.Container prefix = ProotDistro.Container.PREFIX;

        assertTrue(prefix.isPrefix());
        assertEquals(2, prefix.applicationDirs().size());
        assertTrue(prefix.applicationDirs().get(0).getPath().endsWith("/share/applications"));
        assertEquals(prefix.root, prefix.iconPrefix());
        assertEquals(new File("/usr/share/pixmaps/x.png"), prefix.inside("/usr/share/pixmaps/x.png"));
    }

    /**
     * A whole desktop puts its entry in {@code xsessions}, a different directory from
     * {@code applications} and the only thing that says the entry is a desktop rather than an app.
     * Both levels of {@code XDG_DATA_DIRS} are read, as every display manager reads them.
     */
    @Test public void aContainerKnowsWhereItsDesktopSessionsLive() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", "root:x:0:0:root:/root:/bin/bash\n"
            + "amal:x:1000:1000::/home/amal:/bin/bash\n");

        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals(Arrays.asList(
                new File(rootfs, "usr/share/xsessions"),
                new File(rootfs, "usr/local/share/xsessions")),
            debian.sessionDirs());
        // Applications are untouched by this — the list they were is the list they are.
        assertEquals(Arrays.asList(
                new File(rootfs, "usr/share/applications"),
                new File(rootfs, "usr/local/share/applications"),
                new File(rootfs, "home/amal/.local/share/applications")),
            debian.applicationDirs());
    }

    @Test public void thePrefixKeepsItsSessionsBesideItsApplications() {
        List<File> dirs = ProotDistro.Container.PREFIX.sessionDirs();

        assertEquals(2, dirs.size());
        assertTrue(dirs.get(0).getPath().endsWith("/share/xsessions"));
        assertTrue(dirs.get(1).getPath().endsWith("/local/share/xsessions"));
    }

    // --- who the login runs as -------------------------------------------------------------

    @Test public void theFirstOrdinaryUserIsWhoWeLogInAs() {
        ProotDistro.User user = ProotDistro.parsePasswd(
            "root:x:0:0:root:/root:/bin/bash\n"
            + "systemd-network:x:998:998::/:/usr/sbin/nologin\n"
            + "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n"
            + "second:x:1001:1001::/home/second:/bin/bash\n"
            + "amal:x:1000:1000::/home/amal:/bin/bash\n");

        assertEquals("amal", user.name);
        assertEquals("/home/amal", user.home);
    }

    @Test public void prootDistrosOwnAndroidUidAccountIsNotWhoWeLogInAs() {
        // proot-distro writes one of these into every container so files show an owner; its uid is
        // the launcher's Android uid, which on a phone sorts below the user the person made, and
        // its shell refuses the login outright. Picking it is why a container app never opened.
        ProotDistro.User user = ProotDistro.parsePasswd(
            "root:x:0:0:root:/root:/bin/bash\n"
            + "aid_u0_a330:x:10330:10330:Termux:/:/sbin/nologin\n"
            + "amalv:x:10331:10331::/home/amalv:/bin/bash\n");

        assertEquals("amalv", user.name);
        assertEquals("/home/amalv", user.home);
    }

    @Test public void anAccountThatCannotBeLoggedIntoIsSkippedWhateverItsShellIsCalled() {
        ProotDistro.User user = ProotDistro.parsePasswd(
            "root:x:0:0:root:/root:/bin/bash\n"
            + "locked:x:1000:1000::/home/locked:/bin/false\n"
            + "barred:x:1001:1001::/home/barred:/usr/sbin/nologin\n"
            + "amal:x:1002:1002::/home/amal:/bin/bash\n");

        assertEquals("amal", user.name);
    }

    @Test public void aContainerWhoseOnlyOrdinaryAccountsRefuseLoginsIsRoot() {
        ProotDistro.User user = ProotDistro.parsePasswd(
            "root:x:0:0:root:/root:/bin/bash\n"
            + "aid_u0_a330:x:10330:10330:Termux:/:/sbin/nologin\n");

        assertEquals("root", user.name);
        assertEquals("/root", user.home);
    }

    @Test public void aContainerWithNoOrdinaryUserIsRoot() {
        ProotDistro.User user = ProotDistro.parsePasswd(
            "root:x:0:0:root:/root:/bin/bash\n"
            + "daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n"
            + "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n");

        assertEquals("root", user.name);
        assertEquals("/root", user.home);
    }

    @Test public void aMissingOrMalformedPasswdIsAlsoRoot() throws IOException {
        File containers = temp.newFolder("containers");
        File bare = rootfs(containers, "bare");
        assertEquals("root", ProotDistro.loginUser(bare).name);

        File odd = rootfs(containers, "odd");
        write(odd, "etc/passwd", "# a comment\nnot:enough\namal:x:notanumber:1000::/home/amal:/bin/sh\n");
        assertEquals("root", ProotDistro.loginUser(odd).name);
    }

    // --- the account a setup script recorded -----------------------------------------------

    private static final String PASSWD =
        "root:x:0:0:root:/root:/bin/bash\n"
        + "amal:x:1000:1000::/home/amal:/bin/bash\n"
        + "second:x:1001:1001::/home/second:/bin/bash\n"
        + "barred:x:1002:1002::/home/barred:/usr/sbin/nologin\n";

    @Test public void theRecordedAccountWinsEvenOverALowerUid() {
        ProotDistro.User user = ProotDistro.recordedUser("second\n", PASSWD);

        assertNotNull(user);
        assertEquals("second", user.name);
        assertEquals("/home/second", user.home);
    }

    @Test public void aRecordNamingAnAccountNotInPasswdFallsBack() {
        assertNull(ProotDistro.recordedUser("nosuchuser\n", PASSWD));
    }

    @Test public void aRecordNamingRootFallsBack() {
        assertNull(ProotDistro.recordedUser("root\n", PASSWD));
    }

    @Test public void aRecordNamingAnAccountThatCannotBeLoggedIntoFallsBack() {
        assertNull(ProotDistro.recordedUser("barred\n", PASSWD));
    }

    @Test public void aMissingRecordFallsBack() {
        assertNull(ProotDistro.recordedUser(null, PASSWD));
    }

    @Test public void anEmptyOrWhitespaceRecordFallsBack() {
        assertNull(ProotDistro.recordedUser("\n", PASSWD));
        assertNull(ProotDistro.recordedUser("   \n", PASSWD));
    }

    @Test public void aRecordWithExtraWordsFallsBack() {
        assertNull(ProotDistro.recordedUser("amal extra\n", PASSWD));
    }

    @Test public void aRecordWithUppercaseFallsBack() {
        assertNull(ProotDistro.recordedUser("Amal\n", PASSWD));
    }

    @Test public void aRecordThatIsJustGarbageFallsBack() {
        assertNull(ProotDistro.recordedUser("!!not-a-name!!\n", PASSWD));
    }

    @Test public void theContainerListingPrefersTheRecordedAccountToDiscovery() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", PASSWD);
        write(containers, "debian/launcher-user", "second\n");

        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals("second", debian.user);
        assertEquals("/home/second", debian.home);
    }

    @Test public void theContainerListingFallsBackWhenTheRecordDoesNotCheckOut() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", PASSWD);
        write(containers, "debian/launcher-user", "nosuchuser\n");

        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals("amal", debian.user);
    }

    @Test public void theContainerListingIsUnchangedWithNoRecordFile() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", PASSWD);

        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals("amal", debian.user);
    }

    // --- the line that runs an app ---------------------------------------------------------

    @Test public void theLoginLineSharesX11AndCarriesTheDisplayIn() throws IOException {
        File containers = temp.newFolder("containers");
        File rootfs = rootfs(containers, "debian");
        write(rootfs, "etc/passwd", "root:x:0:0:root:/root:/bin/bash\n"
            + "amal:x:1000:1000::/home/amal:/bin/bash\n");
        ProotDistro.Container debian = ProotDistro.byName(ProotDistro.containers(containers), "debian");

        assertNotNull(debian);
        assertEquals("proot-distro login debian -u amal --shared-x11"
                + " -e DISPLAY=${DISPLAY:-:0} -e MOZ_USE_XINPUT2=1 -- /bin/sh -c 'gimp'",
            ProotDistro.loginCommand(debian, "gimp"));
        // Nothing of the host's environment crosses, so what a prefix app gets exported is
        // passed in by hand instead.
        for (String variable : X11LinuxAppRunner.TOUCH_ENV) {
            assertTrue(variable + " has to be carried into the container too",
                ProotDistro.FORWARDED_ENV.contains(variable));
        }
    }

    @Test public void thePrefixIsNotWrappedAtAll() {
        assertEquals("gimp", ProotDistro.loginCommand(ProotDistro.Container.PREFIX, "gimp"));
    }

    @Test public void aSingleQuoteInTheCommandSurvivesTheWrapper() {
        assertEquals("'plain'", ProotDistro.singleQuote("plain"));
        // Close, escape, reopen: the only way a POSIX shell takes a quote inside quotes.
        assertEquals("'it'\\''s'", ProotDistro.singleQuote("it's"));
        assertEquals("'say \"hi\"'", ProotDistro.singleQuote("say \"hi\""));
    }
}
