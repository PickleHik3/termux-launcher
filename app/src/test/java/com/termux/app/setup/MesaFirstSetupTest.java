package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;
import com.termux.app.wayland.MesaWaylandOverlay;
import com.termux.app.x11.KeyboardDataInstall;

import org.junit.Test;

/** Mesa on first setup: when it is armed, installs, waits, ends for good, and its script. */
public class MesaFirstSetupTest {

    private static final long NOW = 10_000_000L;
    private static final MesaFirstSetup.Phase PENDING = MesaFirstSetup.Phase.PENDING;

    private static MesaFirstSetup.Step next(MesaWaylandOverlay.State overlay, boolean online, long failedAt) {
        return MesaFirstSetup.next(AppEdition.TERMUX, PENDING, true, false, overlay, online, NOW, failedAt);
    }

    // ---- the start gate: first setup only ----

    @Test public void onlyAStartThatInstalledTheBootstrapArmsIt() {
        assertEquals(PENDING, MesaFirstSetup.onStart(MesaFirstSetup.Phase.NOT_ARMED, true));
        // An app update, or any later start of an existing install, is never armed.
        assertEquals(MesaFirstSetup.Phase.NOT_ARMED,
            MesaFirstSetup.onStart(MesaFirstSetup.Phase.NOT_ARMED, false));
        assertEquals("a later start finishes a pending step", PENDING, MesaFirstSetup.onStart(PENDING, false));
        assertEquals(MesaFirstSetup.Step.SKIP, MesaFirstSetup.next(AppEdition.TERMUX,
            MesaFirstSetup.Phase.NOT_ARMED, true, false, MesaWaylandOverlay.State.NO_MESA, true, NOW, 0));
    }

    @Test public void aFinalPhaseNeverRunsAgainNorReinstallsAMesaTheUserRemoved() {
        for (MesaFirstSetup.Phase last : new MesaFirstSetup.Phase[] {MesaFirstSetup.Phase.DONE,
                MesaFirstSetup.Phase.GAVE_UP}) {
            // Even a bootstrap laid down again (a broken prefix reinstalled) does not re-arm it.
            assertEquals(last, MesaFirstSetup.onStart(last, true));
            for (MesaWaylandOverlay.State overlay : MesaWaylandOverlay.State.values()) {
                assertEquals(last + " " + overlay, MesaFirstSetup.Step.SKIP, MesaFirstSetup.next(
                    AppEdition.TERMUX, last, true, false, overlay, true, NOW, 0));
            }
        }
    }

    @Test public void triesEndDoneOrGivenUp() {
        assertEquals(MesaFirstSetup.Phase.DONE, MesaFirstSetup.afterTry(true, MesaWaylandOverlay.State.USE, 0));
        assertEquals(PENDING, MesaFirstSetup.afterTry(false, MesaWaylandOverlay.State.MISSING, 1));
        // Installed but for another Mesa version counts as a failed try.
        assertEquals(PENDING, MesaFirstSetup.afterTry(true, MesaWaylandOverlay.State.MISMATCH, 2));
        assertEquals(MesaFirstSetup.Phase.GAVE_UP, MesaFirstSetup.afterTry(false,
            MesaWaylandOverlay.State.MISSING, MesaFirstSetup.MAX_TRIES));
        assertEquals(MesaFirstSetup.Phase.NOT_ARMED, MesaFirstSetup.phaseOf(null));
        assertEquals(MesaFirstSetup.Phase.GAVE_UP, MesaFirstSetup.phaseOf("GAVE_UP"));
    }

    // ---- while pending ----

    @Test public void aPendingStepInstallsAMissingOrMismatchedAddOn() {
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true, 0));
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.NO_MESA, true, 0));
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISMATCH, true, 0));
        assertEquals(MesaFirstSetup.Step.DONE, next(MesaWaylandOverlay.State.USE, true, 0));
    }

    @Test public void itWaitsOfflineBeforeTheBootstrapAndBehindTheWizardsInstall() {
        assertEquals(MesaFirstSetup.Step.WAIT, next(MesaWaylandOverlay.State.MISSING, false, 0));
        assertEquals(MesaFirstSetup.Step.WAIT, MesaFirstSetup.next(AppEdition.TERMUX, PENDING, false,
            false, MesaWaylandOverlay.State.MISSING, true, NOW, 0));
        assertEquals("queued behind the Display setup", MesaFirstSetup.Step.WAIT, MesaFirstSetup.next(
            AppEdition.TERMUX, PENDING, true, true, MesaWaylandOverlay.State.MISSING, true, NOW, 0));
    }

    @Test public void aFailedTryRestsBeforeTheNext() {
        assertEquals(MesaFirstSetup.Step.WAIT, next(MesaWaylandOverlay.State.MISSING, true, NOW - 1000));
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true,
            NOW - MesaFirstSetup.RETRY_AFTER_MS));
        assertEquals("a clock set back", MesaFirstSetup.Step.INSTALL,
            next(MesaWaylandOverlay.State.MISSING, true, NOW + 5000));
    }

    @Test public void otherEditionsSkipItAndShowNoCard() {
        for (AppEdition edition : AppEdition.values()) {
            if (edition == AppEdition.TERMUX) continue;
            assertEquals(edition.name(), MesaFirstSetup.Step.SKIP, MesaFirstSetup.next(edition, PENDING,
                true, false, MesaWaylandOverlay.State.MISSING, true, NOW, 0));
            assertEquals(MesaFirstSetup.CardState.NONE, MesaFirstSetup.card(edition, PENDING, false,
                MesaWaylandOverlay.State.MISSING));
        }
    }

    @Test public void theCardShowsOnlyForAnArmedInstallAndFollowsTheWork() {
        AppEdition stock = AppEdition.TERMUX;
        assertEquals("an updated install gets no card", MesaFirstSetup.CardState.NONE,
            MesaFirstSetup.card(stock, MesaFirstSetup.Phase.NOT_ARMED, false, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.INSTALLING,
            MesaFirstSetup.card(stock, PENDING, true, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.WAITING,
            MesaFirstSetup.card(stock, PENDING, false, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.MISMATCH,
            MesaFirstSetup.card(stock, PENDING, false, MesaWaylandOverlay.State.MISMATCH));
        assertEquals(MesaFirstSetup.CardState.DONE,
            MesaFirstSetup.card(stock, MesaFirstSetup.Phase.DONE, false, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.FAILED,
            MesaFirstSetup.card(stock, MesaFirstSetup.Phase.GAVE_UP, false, MesaWaylandOverlay.State.MISSING));
    }

    // ---- the script: KeyboardDataInstall's package route ----

    private static final String PID = "/p/tmp/terl-mesa-setup.pid";

    @Test public void theScriptIsKeyboardDataInstallsRouteForMesaThenTheAddOn() {
        String lock = "-o DPkg::Lock::Timeout=600";
        String expected = "echo $$ > \"" + PID + "\"\n"
            + "export DEBIAN_FRONTEND=noninteractive TLSTORE_ASSUME_YES=1\n"
            + "if " + KeyboardDataInstall.PACMAN_PREFIX_TEST + "; then "
            + "n=0; while [ -e \"$PREFIX/var/lib/pacman/db.lck\" ] && [ $n -lt 120 ]; do sleep 5; n=$((n+1)); done"
            + " && pacman -Syu --noconfirm && pacman -S --noconfirm --needed mesa"
            + "; else "
            + "{ dpkg --configure -a --force-confdef --force-confold || true; }"
            + " && pkg install -y " + lock + " x11-repo"
            + " && pkg update"
            + " && pkg upgrade -y " + lock + " " + KeyboardDataInstall.KEEP_CONFIG_FILES
            + " && pkg install -y " + lock + " " + KeyboardDataInstall.KEEP_CONFIG_FILES + " mesa"
            + "; fi && tlstore install mesa-wayland-egl\n";
        assertEquals(expected, MesaFirstSetup.script(PID));
        // The prefix's package manager is told apart as the tree does: pacman's config in the
        // prefix and no dpkg, never by a pacman on the PATH alone.
        assertTrue(KeyboardDataInstall.PACMAN_PREFIX_TEST.contains("$PREFIX/etc/pacman.conf"));
        assertTrue(KeyboardDataInstall.PACMAN_PREFIX_TEST.contains("! command -v dpkg"));
        assertFalse("no edition's package name in it", MesaFirstSetup.script(PID).contains("com.termux"));
    }

    @Test public void aTimeoutKillsTheRunsWholeProcessGroup() {
        assertEquals(-4321, MesaFirstSetup.groupToKill("4321\n"));
        assertEquals("nothing to kill without the pid file", 0, MesaFirstSetup.groupToKill(null));
        assertEquals(0, MesaFirstSetup.groupToKill("garbage"));
        assertEquals("never init's group", 0, MesaFirstSetup.groupToKill("1"));
    }

    @Test public void theCardNamesTheDownloadsSizeWhenAptOrPacmanSaysIt() {
        assertNull(MesaFirstSetup.downloadSize("Reading package lists... Done"));
        assertEquals("112 MB", MesaFirstSetup.downloadSize(
            "Need to get 210 kB of archives.\n...\nNeed to get 112 MB of archives.\nAfter this operation"));
        assertEquals("45.67 MiB", MesaFirstSetup.downloadSize("Total Download Size:   45.67 MiB\n"));
    }

    /**
     * Mesa installs on any network (the developer, 2026-11-28): Wi-Fi or mobile data, metered or
     * not. Online means only that some network reaches the internet; nothing asks which kind.
     */
    @Test public void itInstallsOnEveryNetworkWithNoWifiOrMeteredWait() throws Exception {
        for (String name : new String[] {"MesaSetupRun.java", "MesaFirstSetup.java"}) {
            java.io.File source = new java.io.File("src/main/java/com/termux/app/setup/" + name);
            if (!source.isFile()) source = new java.io.File("app/src/main/java/com/termux/app/setup/" + name);
            String text = new String(java.nio.file.Files.readAllBytes(source.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
            for (String gate : new String[] {"NOT_METERED", "isActiveNetworkMetered", "TRANSPORT_WIFI",
                    "TYPE_WIFI", "NET_CAPABILITY_NOT_ROAMING", "RestrictBackground"}) {
                assertFalse(name + " gates on " + gate, text.contains(gate));
            }
        }
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true, 0));
    }

    private static String source(String path) throws Exception {
        java.io.File file = new java.io.File("src/main/java/" + path);
        if (!file.isFile()) file = new java.io.File("app/src/main/java/" + path);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** The launcher arms the step from the bootstrap's callback alone, with whether it installed. */
    @Test public void theLauncherStartsItOnlyFromTheBootstrapCallbackWithWhetherItInstalled() throws Exception {
        String activity = source("com/termux/app/TermuxActivity.java");
        int first = activity.indexOf("MesaSetupRun.");
        assertTrue(first >= 0);
        assertEquals("one call", first, activity.lastIndexOf("MesaSetupRun."));
        assertTrue(activity.startsWith("MesaSetupRun.start(this, bootstrapReturned[0]);", first));
        // The run waits behind the wizard's install and ends its whole group on a timeout.
        String run = source("com/termux/app/setup/MesaSetupRun.java");
        assertTrue(run.contains("SetupShellRun.isBusy()"));
        assertTrue(run.contains("\"/system/bin/setsid\""));
        assertTrue(run.contains("Os.kill(group, android.system.OsConstants.SIGKILL)"));
    }
}
