package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DesktopInstallScriptTest {
    private static final DesktopCatalogue.Desktop LXQT = DesktopCatalogue.LXQT;
    private static final DesktopCatalogue.Desktop XFCE = DesktopCatalogue.XFCE;

    private static String body(DesktopCatalogue.Desktop desktop) {
        return DesktopInstallScript.body(desktop);
    }

    @Test
    public void thePackagesAreInstalledOnBothPackageManagers() {
        String text = body(LXQT);
        assertTrue(text, text.contains("apt-get install -y lxqt"));
        assertTrue(text, text.contains("pacman -S --needed --noconfirm lxqt"));
        assertTrue(text, text.contains("pkg install -y x11-repo"));
    }

    @Test
    public void pacmanRefreshesItsDatabaseBeforeInstalling() {
        String text = body(LXQT);
        int refresh = text.indexOf("pacman -Syu --noconfirm");
        assertTrue(text, refresh >= 0);
        assertTrue(text, refresh < text.indexOf("pacman -S --needed"));
        assertTrue(text, text.indexOf("apt-get update") < text.indexOf("apt-get install"));
    }

    @Test
    public void everyPackageOfTheDesktopIsInstalledInOrder() {
        String text = body(XFCE);
        String all = String.join(" ", XFCE.packages());
        assertTrue(text, text.contains("apt-get install -y " + all));
        assertTrue(text, text.contains("pacman -S --needed --noconfirm " + all));
    }

    @Test
    public void theManifestIsWrittenBeforeAnythingIsInstalled() {
        String text = body(LXQT);
        int record = text.indexOf("note lxqt");
        assertTrue(text, record >= 0);
        assertTrue(text, record < text.indexOf("x11-repo"));
        assertTrue(text, record < text.indexOf("apt-get install"));
        assertTrue(text, record < text.indexOf("pacman -S"));
        assertTrue(text, text.contains("DIR=\"$PREFIX/var/lib/termux-launcher/desktops\""));
        assertTrue(text, text.contains("MANIFEST=\"$DIR/lxqt.manifest\""));
    }

    @Test
    public void aPackageIsRecordedOnlyWhenNoneOfItsFilesIsThere() {
        String text = body(LXQT);
        assertTrue(text, text.contains(
            "{ [ -e \"$PREFIX/bin/startlxqt\" ] || [ -e \"$PREFIX/share/xsessions/lxqt.desktop\" ]; } || note lxqt"));
    }

    @Test
    public void aRetryOnlyAddsNamesAndAnEmptyRecordStillExists() {
        String text = body(LXQT);
        assertTrue(text, text.contains("grep -qxF \"$1\" \"$MANIFEST\""));
        assertTrue(text, text.contains(">> \"$MANIFEST\""));
        assertTrue(text, text.contains("touch \"$MANIFEST\""));
        assertFalse(text, text.contains("> \"$MANIFEST\"\n"));
    }

    @Test
    public void aFailedInstallStopsTheScriptWithAFailure() {
        String text = body(LXQT);
        assertTrue(text, text.contains("apt-get install -y lxqt || exit 1"));
        assertTrue(text, text.contains("pacman -S --needed --noconfirm lxqt || exit 1"));
    }

    @Test
    public void mateInstallsItsMetapackageAndRecordsItBySessionFiles() {
        String text = body(DesktopCatalogue.MATE);
        assertTrue(text, text.contains("apt-get install -y mate || exit 1"));
        assertTrue(text, text.contains("pacman -S --needed --noconfirm mate || exit 1"));
        assertTrue(text, text.contains(
            "{ [ -e \"$PREFIX/bin/mate-session\" ] || [ -e \"$PREFIX/share/xsessions/mate.desktop\" ]; } || note mate"));
        assertTrue(text, text.contains("MANIFEST=\"$DIR/mate.manifest\""));
    }

    @Test
    public void plasmaInstallsItsMetapackageAndRecordsItByX11SessionFiles() {
        String text = body(DesktopCatalogue.PLASMA);
        assertTrue(text, text.contains("apt-get install -y plasma || exit 1"));
        assertTrue(text, text.contains("pacman -S --needed --noconfirm plasma || exit 1"));
        assertTrue(text, text.contains(
            "{ [ -e \"$PREFIX/bin/startplasma-x11\" ] || [ -e \"$PREFIX/share/xsessions/plasmax11.desktop\" ]; } || note plasma"));
        assertTrue(text, text.contains("MANIFEST=\"$DIR/plasma.manifest\""));
        assertFalse(text, text.contains("wayland"));
    }

    @Test
    public void theScriptRunsUnderBashWithoutQuestions() {
        String text = DesktopInstallScript.script(LXQT);
        assertTrue(text.startsWith("#!/bin/bash\n"));
        assertTrue(text.contains("export DEBIAN_FRONTEND=noninteractive"));
    }

    @Test
    public void manifestPathIsUnderThePrefixStateFolder() {
        assertEquals("var/lib/termux-launcher/desktops/lxqt.manifest",
            DesktopCatalogue.MANIFEST_DIR + "/" + DesktopCatalogue.manifestFileName(LXQT));
    }

    @Test
    public void successNeedsExitZeroAndTheDesktopInPlace() {
        assertTrue(DesktopInstallScript.succeeded(0, true));
        assertFalse(DesktopInstallScript.succeeded(0, false));
        assertFalse(DesktopInstallScript.succeeded(1, true));
        assertFalse(DesktopInstallScript.succeeded(-9, true));
    }
}
