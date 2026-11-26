package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesktopRemoveScriptTest {
    private static final DesktopCatalogue.Desktop XFCE = DesktopCatalogue.XFCE;
    private static final DesktopCatalogue.Desktop LXQT = DesktopCatalogue.LXQT;

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static List<String> plan(DesktopCatalogue.Desktop desktop, List<String> manifest,
                                     Set<String> claimed) {
        return DesktopRemoveScript.packagesToRemove(desktop, manifest, claimed);
    }

    @Test
    public void theManifestIsWhatComesOut() {
        assertEquals(Arrays.asList("lxqt"),
            plan(LXQT, Arrays.asList("lxqt"), Collections.emptySet()));
    }

    @Test
    public void aSharedPackageIsKept() {
        List<String> out = plan(XFCE, Arrays.asList("xfce4", "ristretto", "mousepad"), set("ristretto"));
        assertEquals(Arrays.asList("xfce4", "mousepad"), out);
    }

    @Test
    public void aStarterAppIsKept() {
        Set<String> claimed = DesktopRemoveScript.claimed(XFCE, d -> null, d -> false,
            GuiAppsSetup.termuxSetupPackages());
        List<String> out = plan(XFCE, Arrays.asList("xfce4", "mousepad", "xfdesktop"), claimed);
        assertEquals(Arrays.asList("xfce4", "xfdesktop"), out);
    }

    @Test
    public void everySetupPackageIsAClaim() {
        Set<String> setup = GuiAppsSetup.termuxSetupPackages();
        for (String name : new String[]{"firefox", "pcmanfm", "mousepad", "xfce4-terminal", "dbus",
            "openbox", "pulseaudio", "xkeyboard-config", "xwayland", "fcitx5"}) {
            assertTrue(name, setup.contains(name));
        }
        // The desktop starter is the desktop itself, not a claim against it.
        assertFalse(setup.contains("xfce4"));
    }

    @Test
    public void anotherInstalledDesktopClaimsItsCataloguePackagesAndItsManifest() {
        Set<String> claimed = DesktopRemoveScript.claimed(XFCE,
            d -> d == LXQT ? Arrays.asList("lxqt", "extra-thing") : null,
            d -> d == LXQT, Collections.emptySet());
        assertTrue(claimed.contains("lxqt"));
        assertTrue(claimed.contains("extra-thing"));
        assertFalse(claimed.contains("xfce4"));
    }

    @Test
    public void aDesktopThatIsNotInstalledClaimsNothing() {
        Set<String> claimed = DesktopRemoveScript.claimed(XFCE,
            d -> Arrays.asList("lxqt"), d -> false, Collections.emptySet());
        assertTrue(claimed.isEmpty());
    }

    @Test
    public void theDesktopItselfNeverClaimsAgainstItself() {
        Set<String> claimed = DesktopRemoveScript.claimed(XFCE,
            d -> d == XFCE ? Arrays.asList("xfce4") : null, d -> true, Collections.emptySet());
        assertFalse(claimed.contains("xfce4"));
    }

    @Test
    public void withoutAManifestTheWholeCatalogueListGoesLikeTheXfceOfBeforeRecords() {
        List<String> out = plan(XFCE, null, set("mousepad", "pavucontrol"));
        List<String> expected = new ArrayList<>(Arrays.asList(XFCE.packages()));
        expected.remove("mousepad");
        expected.remove("pavucontrol");
        assertEquals(expected, out);
    }

    @Test
    public void anEmptyManifestStillTakesTheDesktopItselfAndNothingElse() {
        assertEquals(Arrays.asList("xfce4"), plan(XFCE, Collections.emptyList(), Collections.emptySet()));
    }

    @Test
    public void theDesktopItselfIsTakenEvenWhenTheManifestLacksIt() {
        assertEquals(Arrays.asList("xfce4", "xfdesktop"),
            plan(XFCE, Arrays.asList("xfdesktop"), Collections.emptySet()));
    }

    @Test
    public void aNameTheManifestHoldsTwiceIsListedOnce() {
        assertEquals(Arrays.asList("lxqt"),
            plan(LXQT, Arrays.asList("lxqt", "lxqt"), Collections.emptySet()));
    }

    @Test
    public void manifestTextIsOneNamePerLineBlankLinesIgnored() {
        assertEquals(Arrays.asList("a", "b"), DesktopRemoveScript.parseManifest("a\n\n b \r\n"));
        assertTrue(DesktopRemoveScript.parseManifest("").isEmpty());
    }

    @Test
    public void aPackageNameThatIsNotAPlainNameNeverReachesTheShell() {
        List<String> out = plan(LXQT, Arrays.asList("lxqt", "a;rm -rf ~", "$(x)", "ok-pkg+1.2"),
            Collections.emptySet());
        assertEquals(Arrays.asList("lxqt", "ok-pkg+1.2"), out);
    }

    private static String body(boolean settings, String... pkgs) {
        return DesktopRemoveScript.body(XFCE, Arrays.asList(pkgs), settings);
    }

    @Test
    public void aptRemovesOnlyThePlannedPackagesAndThenWhatNothingNeeds() {
        String text = body(false, "xfce4", "xfdesktop");
        assertTrue(text, text.contains("apt-get remove -y"));
        assertTrue(text, text.contains("apt-get autoremove -y"));
        // A package that would drag an outside package along is skipped, never removed.
        assertTrue(text, text.contains("apt-get -s remove"));
        assertTrue(text, text.contains("xfce4 xfdesktop"));
    }

    @Test
    public void pacmanRemovesWithItsDependenciesOnlyWhenNothingExplicitNeedsThem() {
        String text = body(false, "xfce4", "xfdesktop");
        assertTrue(text, text.contains("pacman -Rs --noconfirm"));
        assertFalse(text, text.contains("-Rns"));
        assertFalse(text, text.contains("-Rdd"));
        assertFalse(text, text.contains("--cascade"));
        assertTrue(text, text.indexOf("[ -x \"$PREFIX/bin/pacman\" ]") < text.indexOf("pacman -Rs"));
    }

    @Test
    public void thePlannedPackagesAreInstalledOnesOnly() {
        String text = body(false, "xfce4");
        assertTrue(text, text.contains("dpkg -s"));
        assertTrue(text, text.contains("pacman -Qq"));
    }

    @Test
    public void settingsFoldersGoOnlyWithTheTick() {
        String without = body(false, "xfce4");
        String with = body(true, "xfce4");
        assertFalse(without, without.contains(".config/xfce4"));
        assertFalse(without, without.contains("rm -rf"));
        assertTrue(with, with.contains("rm -rf \"$HOME/.config/xfce4\""));
    }

    @Test
    public void plasmaLooseSettingsFilesGoWithTheTickToo() {
        String text = DesktopRemoveScript.body(DesktopCatalogue.PLASMA, Arrays.asList("plasma"), true);
        assertTrue(text, text.contains("rm -rf \"$HOME/.config/kdeglobals\""));
        assertTrue(text, text.contains("rm -rf \"$HOME/.config/kwinrc\""));
        assertTrue(text, text.contains("rm -rf \"$HOME/.config/plasma-org.kde.plasma.desktop-appletsrc\""));
    }

    @Test
    public void theManifestGoesAfterTheRemovalSucceeded() {
        String text = body(false, "xfce4");
        assertTrue(text, text.indexOf("rm -f \"$MANIFEST\"") > text.indexOf("autoremove"));
        assertTrue(text, text.contains(DesktopCatalogue.MANIFEST_DIR));
        assertTrue(text, text.contains("xfce.manifest"));
    }

    @Test
    public void aRunningDesktopIsStoppedFirst() {
        String text = body(false, "xfce4");
        assertTrue(text, text.contains("kill"));
        assertTrue(text, text.indexOf("kill") < text.indexOf("apt-get"));
    }

    @Test
    public void aFailedStepEndsTheScriptNonZero() {
        assertTrue(body(true, "xfce4").contains("|| exit 1"));
    }

    @Test
    public void noPackagesStillDeletesTheManifest() {
        String text = DesktopRemoveScript.body(LXQT, Collections.emptyList(), false);
        assertFalse(text, text.contains("apt-get remove"));
        assertTrue(text, text.contains("rm -f \"$MANIFEST\""));
    }

    @Test
    public void scriptStartsWithAShebangAndAsksNothing() {
        String text = DesktopRemoveScript.script(LXQT, Arrays.asList("lxqt"), false);
        assertTrue(text.startsWith("#!/bin/bash\n"));
        assertTrue(text, text.contains("DEBIAN_FRONTEND=noninteractive"));
    }

    @Test
    public void aRemovalCountsAsDoneWhenItExitedZeroAndTheDesktopIsGone() {
        assertTrue(DesktopRemoveScript.succeeded(0, false));
        assertFalse(DesktopRemoveScript.succeeded(0, true));
        assertFalse(DesktopRemoveScript.succeeded(1, false));
    }
}
