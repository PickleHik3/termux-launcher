package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Desktop files in, drawer entries out: what is shown, what is skipped, what gets run — for the
 * prefix and for the distro containers beside it.
 */
public class LinuxAppCatalogTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File write(File dir, String name, String content) throws IOException {
        File file = new File(dir, name);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** Plain fixture directories, read as the prefix's own. */
    private static List<LinuxAppCatalog.Root> host(File... dirs) {
        return LinuxAppCatalog.prefixRoots(Arrays.asList(dirs));
    }

    /**
     * A container fixture: a {@code containers/<name>/rootfs} tree with a passwd of its own, read
     * the way the launcher reads a real one.
     */
    private ProotDistro.Container container(File containers, String name, String passwd)
            throws IOException {
        File rootfs = new File(containers, name + "/rootfs");
        assertTrue(new File(rootfs, "usr/share/applications").mkdirs());
        write(rootfs, "etc/passwd", passwd);
        ProotDistro.Container found = ProotDistro.byName(ProotDistro.containers(containers), name);
        assertNotNull(found);
        return found;
    }

    @Test public void applicationsAreListedSortedWithTheirExecCleanedUp() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "firefox.desktop", "[Desktop Entry]\nType=Application\nName=Firefox\n"
            + "Exec=firefox %u\nIcon=firefox\nComment=Browse the web\n");
        write(dir, "org.kde.kate.desktop", "[Desktop Entry]\nType=Application\nName=Kate\n"
            + "Exec=kate -b %U\nIcon=kate\n");
        write(dir, "feh.desktop", "[Desktop Entry]\nType=Application\nName=feh\n"
            + "Exec=feh --start-at %f\nIcon=/usr/share/feh.png\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(host(dir));

        assertEquals(Arrays.asList("feh", "Firefox", "Kate"),
            Arrays.asList(apps.get(0).name, apps.get(1).name, apps.get(2).name));
        assertEquals("firefox", apps.get(1).id);
        assertEquals("firefox", apps.get(1).exec);
        assertEquals("Browse the web", apps.get(1).comment);
        assertEquals("org.kde.kate", apps.get(2).id);
        assertEquals("kate -b", apps.get(2).exec);
        assertEquals("feh --start-at", apps.get(0).exec);
        assertEquals("/usr/share/feh.png", apps.get(0).icon);
    }

    @Test public void startupWmClassIsReadWhereTheAppDeclaresOne() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "code-oss.desktop", "[Desktop Entry]\nType=Application\nName=Code\n"
            + "Exec=code-oss %F\nIcon=code\nStartupWMClass=Code\n");
        write(dir, "firefox.desktop", "[Desktop Entry]\nType=Application\nName=Firefox\n"
            + "Exec=firefox %u\nIcon=firefox\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(host(dir));

        assertEquals("Code", LinuxAppCatalog.find(apps, "code-oss").startupWmClass);
        assertEquals("", LinuxAppCatalog.find(apps, "firefox").startupWmClass);
    }

    /**
     * Was {@code hiddenTerminalAndNonApplicationEntriesAreSkipped}: a {@code Terminal=true} entry
     * used to be dropped along with {@code NoDisplay}/{@code Hidden}. D5 reverses that — it is
     * shown and carries {@link LinuxAppCatalog.LinuxApp#terminal} instead — so this now asserts
     * htop survives while the two "not in a menu" markers still drop their entries.
     */
    @Test public void hiddenAndNonApplicationEntriesAreSkippedButATerminalOneIsShownAndFlagged()
            throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "hidden.desktop", "[Desktop Entry]\nType=Application\nName=H\nExec=h\nNoDisplay=true\n");
        write(dir, "gone.desktop", "[Desktop Entry]\nType=Application\nName=G\nExec=g\nHidden=true\n");
        write(dir, "htop.desktop", "[Desktop Entry]\nType=Application\nName=htop\nExec=htop\nTerminal=true\n");
        write(dir, "link.desktop", "[Desktop Entry]\nType=Link\nName=L\nURL=http://x\n");
        write(dir, "noexec.desktop", "[Desktop Entry]\nType=Application\nName=N\n");
        write(dir, "notes.txt", "[Desktop Entry]\nType=Application\nName=T\nExec=t\n");
        write(dir, "shown.desktop", "[Desktop Entry]\nType=Application\nName=Shown\nExec=shown\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(host(dir));

        assertEquals(2, apps.size());
        LinuxAppCatalog.LinuxApp htop = LinuxAppCatalog.find(apps, "htop");
        assertNotNull(htop);
        assertTrue("Terminal=true is carried, not dropped", htop.terminal);
        assertNotNull(LinuxAppCatalog.find(apps, "shown"));
        assertFalse("a normal entry is not flagged terminal",
            LinuxAppCatalog.find(apps, "shown").terminal);
    }

    @Test public void tryExecNamingAMissingBinaryHidesTheEntry() throws IOException {
        File dir = temp.newFolder("applications");
        File present = new File(dir, "present-bin");
        Files.write(present.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        present.setExecutable(true);
        write(dir, "present.desktop", "[Desktop Entry]\nType=Application\nName=P\nExec=present-bin\nTryExec=present-bin\n");
        write(dir, "missing.desktop", "[Desktop Entry]\nType=Application\nName=M\nExec=m\nTryExec=/nonexistent/m\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(host(dir));

        assertEquals(1, apps.size());
        assertEquals("present", apps.get(0).id);
    }

    @Test public void onlyTheDesktopEntryGroupIsRead() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "a.desktop", "[Desktop Entry]\nType=Application\nName=Real\nExec=real\n"
            + "[Desktop Action new-window]\nName=New Window\nExec=real --new-window\n");

        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.scan(host(dir)).get(0);

        assertEquals("Real", app.name);
        assertEquals("real", app.exec);
    }

    @Test public void anEarlierDirectoryWinsOverALaterOneForTheSameId() throws IOException {
        File system = temp.newFolder("share", "applications");
        File local = temp.newFolder("local", "share", "applications");
        write(system, "x.desktop", "[Desktop Entry]\nType=Application\nName=System\nExec=x\n");
        write(local, "x.desktop", "[Desktop Entry]\nType=Application\nName=Local\nExec=x\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(host(system, local));

        assertEquals(1, apps.size());
        assertEquals("System", apps.get(0).name);
        assertNotNull(LinuxAppCatalog.find(apps, "X"));
        assertNull(LinuxAppCatalog.find(apps, "y"));
    }

    @Test public void fieldCodesGoAndDoubledPercentStays() {
        assertEquals("app --file", LinuxAppCatalog.stripFieldCodes("app --file %F"));
        assertEquals("app 100%", LinuxAppCatalog.stripFieldCodes("app 100%%"));
        assertEquals("app -x", LinuxAppCatalog.stripFieldCodes("app %i -x %c %k"));
    }

    @Test public void theSignatureChangesWhenAFileIsAddedAndIsStableOtherwise() throws Exception {
        File dir = temp.newFolder("applications");
        write(dir, "a.desktop", "[Desktop Entry]\nType=Application\nName=A\nExec=a\n");
        long before = LinuxAppCatalog.signature(host(dir));
        assertEquals(before, LinuxAppCatalog.signature(host(dir)));

        write(dir, "b.desktop", "[Desktop Entry]\nType=Application\nName=B\nExec=b\n");

        org.junit.Assert.assertNotEquals(before, LinuxAppCatalog.signature(host(dir)));
    }

    /**
     * A directory that did not exist when the drawer last looked: writing the first desktop file
     * by hand creates it and the file in one go, and the rescan on resume or on the drawer
     * opening has only this number to tell it something happened.
     */
    @Test public void theSignatureChangesWhenAnAbsentDirectoryAppears() throws Exception {
        File dir = new File(temp.getRoot(), "later/local/share/applications");
        assertFalse("not there when the drawer last looked", dir.exists());
        long before = LinuxAppCatalog.signature(host(dir));
        assertEquals("and still not there", before, LinuxAppCatalog.signature(host(dir)));

        write(dir, "probe.desktop",
            "[Desktop Entry]\nType=Application\nName=Probe\nExec=probe\n");

        long after = LinuxAppCatalog.signature(host(dir));
        org.junit.Assert.assertNotEquals(before, after);
        assertEquals(1, LinuxAppCatalog.scan(host(dir)).size());

        // And an empty directory appearing is not the same reading as no directory either.
        File empty = new File(temp.getRoot(), "empty/applications");
        long absent = LinuxAppCatalog.signature(host(empty));
        assertTrue(empty.mkdirs());
        org.junit.Assert.assertNotEquals(absent, LinuxAppCatalog.signature(host(empty)));
    }

    @Test public void iconFilesAreFoundInHicolorThenPixmaps() throws IOException {
        File prefix = temp.newFolder("usr");
        File big = new File(prefix, "share/icons/hicolor/128x128/apps"); big.mkdirs();
        File small = new File(prefix, "share/icons/hicolor/48x48/apps"); small.mkdirs();
        File pixmaps = new File(prefix, "share/pixmaps"); pixmaps.mkdirs();
        write(small, "firefox.png", "png");
        write(big, "firefox.png", "png");
        write(pixmaps, "feh.png", "png");
        write(big, "vector.svg", "svg");

        assertEquals(new File(big, "firefox.png"), LinuxAppIcons.find("firefox", prefix));
        assertEquals(new File(pixmaps, "feh.png"), LinuxAppIcons.find("feh", prefix));
        assertEquals(new File(pixmaps, "feh.png"), LinuxAppIcons.find("feh.png", prefix));
        // SVG is found too now (LinuxAppIcons D6) — a PNG at the same size still wins, but this
        // app has none at 128x128, so its SVG there is picked up rather than skipped.
        assertEquals(new File(big, "vector.svg"), LinuxAppIcons.find("vector", prefix));
        assertNull(LinuxAppIcons.find("", prefix));
        assertEquals(new File(big, "firefox.png"),
            LinuxAppIcons.find(new File(big, "firefox.png").getPath(), prefix));
    }

    // --- distro containers -----------------------------------------------------------------

    private static final String PASSWD_WITH_USER =
        "root:x:0:0:root:/root:/bin/bash\n"
        + "daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n"
        + "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n"
        + "amal:x:1000:1000::/home/amal:/bin/bash\n"
        + "second:x:1001:1001::/home/second:/bin/bash\n";

    @Test public void aContainerAppIsListedUnderItsOwnNameWithAContainerQualifiedId() throws IOException {
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        write(debian.root, "usr/share/applications/typora.desktop",
            "[Desktop Entry]\nType=Application\nName=Typora\nExec=typora %U\nIcon=typora\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(debian));

        assertEquals(1, apps.size());
        // The tile says what the app calls itself; the container lives in the id, not the name.
        assertEquals("Typora", apps.get(0).name);
        assertEquals("distro:debian:typora", apps.get(0).id);
        assertEquals("typora", apps.get(0).desktopFile);
        assertEquals("debian", apps.get(0).container.name);
        assertEquals("debian", X11Apps.containerOf(apps.get(0).id));
        assertEquals("typora", X11Apps.desktopFileOf(apps.get(0).id));
    }

    @Test public void theSameDesktopFileInThePrefixAndInTwoContainersIsThreeEntries() throws IOException {
        File prefixApps = temp.newFolder("share", "applications");
        write(prefixApps, "firefox.desktop",
            "[Desktop Entry]\nType=Application\nName=Firefox\nExec=firefox\n");
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        ProotDistro.Container arch = container(containers, "archlinux", PASSWD_WITH_USER);
        write(debian.root, "usr/share/applications/firefox.desktop",
            "[Desktop Entry]\nType=Application\nName=Firefox\nExec=firefox\n");
        write(arch.root, "usr/share/applications/firefox.desktop",
            "[Desktop Entry]\nType=Application\nName=Firefox\nExec=firefox\n");

        List<LinuxAppCatalog.Root> roots = new ArrayList<>(host(prefixApps));
        roots.addAll(LinuxAppCatalog.rootsOf(debian));
        roots.addAll(LinuxAppCatalog.rootsOf(arch));
        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(roots);

        assertEquals(3, apps.size());
        // All three are called Firefox, so the id breaks the tie and the order never wobbles.
        assertEquals(Arrays.asList("distro:archlinux:firefox", "distro:debian:firefox", "firefox"),
            Arrays.asList(apps.get(0).id, apps.get(1).id, apps.get(2).id));
        // Each one is still reachable by its own id, which is what a pin stores.
        assertNotNull(LinuxAppCatalog.find(apps, "firefox"));
        assertEquals("debian", LinuxAppCatalog.find(apps, "distro:debian:firefox").container.name);
    }

    @Test public void aContainerAppRunsThroughProotDistroAndKeepsItsOwnQuoting() throws IOException {
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        write(debian.root, "usr/share/applications/greet.desktop",
            "[Desktop Entry]\nType=Application\nName=Greet\nExec=greet --title 'My App' %f\n");

        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(debian)).get(0);

        assertEquals("greet --title 'My App'", app.exec);
        assertEquals("proot-distro login debian -u amal --shared-x11"
            + " -e DISPLAY=${DISPLAY:-:0} -e MOZ_USE_XINPUT2=1"
            + " -- /bin/sh -c 'greet --title '\\''My App'\\'''", app.command());
        // What a later phase appends goes to the app, inside the wrapper.
        assertTrue(app.commandWith("--no-sandbox")
            .endsWith("-- /bin/sh -c 'greet --title '\\''My App'\\'' --no-sandbox'"));
    }

    /**
     * D5: a {@code Terminal=true} entry inside a container still builds the same
     * {@code proot-distro login} wrapper as any other container app — it is the tap routing that
     * changes (a pane, not the display), never the command a terminal app would run.
     */
    @Test public void aContainerTerminalAppKeepsTheOrdinaryLoginWrappedCommand() throws IOException {
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        write(debian.root, "usr/share/applications/htop.desktop",
            "[Desktop Entry]\nType=Application\nName=htop\nExec=htop\nTerminal=true\n");

        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(debian)).get(0);

        assertTrue(app.terminal);
        assertEquals("distro:debian:htop", app.id);
        assertEquals("proot-distro login debian -u amal --shared-x11"
            + " -e DISPLAY=${DISPLAY:-:0} -e MOZ_USE_XINPUT2=1"
            + " -- /bin/sh -c 'htop'", app.command());
    }

    @Test public void aPrefixAppStillRunsItsOwnCommandUnwrapped() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "feh.desktop", "[Desktop Entry]\nType=Application\nName=feh\nExec=feh --start-at %f\n");

        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.scan(host(dir)).get(0);

        assertEquals("feh --start-at", app.command());
        assertEquals("feh --start-at --no-sandbox", app.commandWith("--no-sandbox"));
    }

    @Test public void theSignatureMovesWhenAContainerIsInstalledOrItsAppsChange() throws IOException {
        File prefixApps = temp.newFolder("share", "applications");
        File containers = temp.newFolder("containers");
        long empty = LinuxAppCatalog.signature(host(prefixApps));

        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        List<LinuxAppCatalog.Root> withContainer = new ArrayList<>(host(prefixApps));
        withContainer.addAll(LinuxAppCatalog.rootsOf(debian));
        long installed = LinuxAppCatalog.signature(withContainer);
        org.junit.Assert.assertNotEquals(empty, installed);

        write(debian.root, "usr/share/applications/gimp.desktop",
            "[Desktop Entry]\nType=Application\nName=GIMP\nExec=gimp\n");

        org.junit.Assert.assertNotEquals(installed, LinuxAppCatalog.signature(withContainer));
    }

    @Test public void aContainerIconIsFoundInsideItsRootfs() throws IOException {
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        write(debian.root, "usr/share/icons/hicolor/128x128/apps/typora.png", "png");
        write(debian.root, "usr/share/pixmaps/greet.png", "png");
        write(debian.root, "usr/share/applications/typora.desktop",
            "[Desktop Entry]\nType=Application\nName=Typora\nExec=typora\nIcon=typora\n");
        // An Icon= path is absolute in the container's world, so it is read through the rootfs.
        write(debian.root, "usr/share/applications/greet.desktop",
            "[Desktop Entry]\nType=Application\nName=Greet\nExec=greet\nIcon=/usr/share/pixmaps/greet.png\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(debian));

        assertEquals(new File(debian.root, "usr/share/icons/hicolor/128x128/apps/typora.png"),
            LinuxAppIcons.find(LinuxAppCatalog.find(apps, "distro:debian:typora")));
        assertEquals(new File(debian.root, "usr/share/pixmaps/greet.png"),
            LinuxAppIcons.find(LinuxAppCatalog.find(apps, "distro:debian:greet")));
    }

    @Test public void aContainerAlsoOffersItsUsersOwnApplicationsDirectory() throws IOException {
        File containers = temp.newFolder("containers");
        ProotDistro.Container debian = container(containers, "debian", PASSWD_WITH_USER);
        write(debian.root, "home/amal/.local/share/applications/mine.desktop",
            "[Desktop Entry]\nType=Application\nName=Mine\nExec=mine\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(debian));

        assertEquals(1, apps.size());
        assertEquals("distro:debian:mine", apps.get(0).id);
    }
}
