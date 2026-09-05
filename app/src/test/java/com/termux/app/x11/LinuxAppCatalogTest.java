package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

/** Desktop files in, drawer entries out: what is shown, what is skipped, what gets run. */
public class LinuxAppCatalogTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File write(File dir, String name, String content) throws IOException {
        File file = new File(dir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test public void applicationsAreListedSortedWithTheirExecCleanedUp() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "firefox.desktop", "[Desktop Entry]\nType=Application\nName=Firefox\n"
            + "Exec=firefox %u\nIcon=firefox\nComment=Browse the web\n");
        write(dir, "org.kde.kate.desktop", "[Desktop Entry]\nType=Application\nName=Kate\n"
            + "Exec=kate -b %U\nIcon=kate\n");
        write(dir, "feh.desktop", "[Desktop Entry]\nType=Application\nName=feh\n"
            + "Exec=feh --start-at %f\nIcon=/usr/share/feh.png\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(Collections.singletonList(dir));

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

    @Test public void hiddenTerminalAndNonApplicationEntriesAreSkipped() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "hidden.desktop", "[Desktop Entry]\nType=Application\nName=H\nExec=h\nNoDisplay=true\n");
        write(dir, "gone.desktop", "[Desktop Entry]\nType=Application\nName=G\nExec=g\nHidden=true\n");
        write(dir, "htop.desktop", "[Desktop Entry]\nType=Application\nName=htop\nExec=htop\nTerminal=true\n");
        write(dir, "link.desktop", "[Desktop Entry]\nType=Link\nName=L\nURL=http://x\n");
        write(dir, "noexec.desktop", "[Desktop Entry]\nType=Application\nName=N\n");
        write(dir, "notes.txt", "[Desktop Entry]\nType=Application\nName=T\nExec=t\n");
        write(dir, "shown.desktop", "[Desktop Entry]\nType=Application\nName=Shown\nExec=shown\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(Collections.singletonList(dir));

        assertEquals(1, apps.size());
        assertEquals("shown", apps.get(0).id);
    }

    @Test public void tryExecNamingAMissingBinaryHidesTheEntry() throws IOException {
        File dir = temp.newFolder("applications");
        File present = new File(dir, "present-bin");
        Files.write(present.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        present.setExecutable(true);
        write(dir, "present.desktop", "[Desktop Entry]\nType=Application\nName=P\nExec=present-bin\nTryExec=present-bin\n");
        write(dir, "missing.desktop", "[Desktop Entry]\nType=Application\nName=M\nExec=m\nTryExec=/nonexistent/m\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(Collections.singletonList(dir));

        assertEquals(1, apps.size());
        assertEquals("present", apps.get(0).id);
    }

    @Test public void onlyTheDesktopEntryGroupIsRead() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "a.desktop", "[Desktop Entry]\nType=Application\nName=Real\nExec=real\n"
            + "[Desktop Action new-window]\nName=New Window\nExec=real --new-window\n");

        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.scan(Collections.singletonList(dir)).get(0);

        assertEquals("Real", app.name);
        assertEquals("real", app.exec);
    }

    @Test public void anEarlierDirectoryWinsOverALaterOneForTheSameId() throws IOException {
        File system = temp.newFolder("share", "applications");
        File local = temp.newFolder("local", "share", "applications");
        write(system, "x.desktop", "[Desktop Entry]\nType=Application\nName=System\nExec=x\n");
        write(local, "x.desktop", "[Desktop Entry]\nType=Application\nName=Local\nExec=x\n");

        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(Arrays.asList(system, local));

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
        long before = LinuxAppCatalog.signature(Collections.singletonList(dir));
        assertEquals(before, LinuxAppCatalog.signature(Collections.singletonList(dir)));

        write(dir, "b.desktop", "[Desktop Entry]\nType=Application\nName=B\nExec=b\n");

        org.junit.Assert.assertNotEquals(before, LinuxAppCatalog.signature(Collections.singletonList(dir)));
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
        assertNull("svg is not rendered", LinuxAppIcons.find("vector", prefix));
        assertNull(LinuxAppIcons.find("", prefix));
        assertEquals(new File(big, "firefox.png"),
            LinuxAppIcons.find(new File(big, "firefox.png").getPath(), prefix));
    }
}
