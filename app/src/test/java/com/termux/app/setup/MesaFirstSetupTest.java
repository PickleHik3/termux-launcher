package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;
import com.termux.app.wayland.MesaWaylandOverlay;

import org.junit.Test;

/** Mesa on first setup: when it installs, waits, skips, and what its card says. */
public class MesaFirstSetupTest {

    private static final long NOW = 10_000_000L;

    private static MesaFirstSetup.Step next(MesaWaylandOverlay.State overlay, boolean online, long failedAt) {
        return MesaFirstSetup.next(AppEdition.TERMUX, true, overlay, online, NOW, failedAt);
    }

    @Test public void afterTheBootstrapAMissingAddOnInstalls() {
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true, 0));
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.NO_MESA, true, 0));
        // An add-on built for another Mesa is brought up to the installed one.
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISMATCH, true, 0));
    }

    @Test public void matchingVersionsAreLeftAlone() {
        assertEquals(MesaFirstSetup.Step.DONE, next(MesaWaylandOverlay.State.USE, true, 0));
        assertEquals(MesaFirstSetup.Step.DONE, next(MesaWaylandOverlay.State.USE, false, NOW));
    }

    @Test public void offlineOrBeforeTheBootstrapItWaits() {
        assertEquals(MesaFirstSetup.Step.WAIT, next(MesaWaylandOverlay.State.MISSING, false, 0));
        assertEquals(MesaFirstSetup.Step.WAIT, MesaFirstSetup.next(AppEdition.TERMUX, false,
            MesaWaylandOverlay.State.MISSING, true, NOW, 0));
    }

    @Test public void aFailedTryRestsBeforeTheNext() {
        assertEquals(MesaFirstSetup.Step.WAIT, next(MesaWaylandOverlay.State.MISSING, true, NOW - 1000));
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true,
            NOW - MesaFirstSetup.RETRY_AFTER_MS));
        // A clock set back does not keep it waiting for ever.
        assertEquals(MesaFirstSetup.Step.INSTALL, next(MesaWaylandOverlay.State.MISSING, true, NOW + 5000));
    }

    @Test public void otherEditionsSkipItAndShowNoCard() {
        for (AppEdition edition : AppEdition.values()) {
            if (edition == AppEdition.TERMUX) continue;
            MesaFirstSetup.Step step = MesaFirstSetup.next(edition, true,
                MesaWaylandOverlay.State.MISSING, true, NOW, 0);
            assertEquals(edition.name(), MesaFirstSetup.Step.SKIP, step);
            assertEquals(MesaFirstSetup.CardState.NONE,
                MesaFirstSetup.card(step, false, MesaWaylandOverlay.State.MISSING));
        }
    }

    @Test public void theCardFollowsTheWork() {
        assertEquals(MesaFirstSetup.CardState.INSTALLING, MesaFirstSetup.card(
            MesaFirstSetup.Step.INSTALL, true, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.WAITING, MesaFirstSetup.card(
            MesaFirstSetup.Step.WAIT, false, MesaWaylandOverlay.State.MISSING));
        assertEquals(MesaFirstSetup.CardState.MISMATCH, MesaFirstSetup.card(
            MesaFirstSetup.Step.WAIT, false, MesaWaylandOverlay.State.MISMATCH));
        assertEquals(MesaFirstSetup.CardState.DONE, MesaFirstSetup.card(
            MesaFirstSetup.Step.DONE, false, MesaWaylandOverlay.State.USE));
    }

    @Test public void theScriptInstallsTermuxsMesaThenTheAddOnWithoutAsking() {
        assertTrue(MesaFirstSetup.SCRIPT.contains("pkg install -y mesa"));
        assertTrue(MesaFirstSetup.SCRIPT.contains("pacman -S --needed --noconfirm mesa"));
        assertTrue(MesaFirstSetup.SCRIPT.contains("tlstore install mesa-wayland-egl"));
        assertTrue(MesaFirstSetup.SCRIPT.contains("TLSTORE_ASSUME_YES=1"));
        assertTrue(MesaFirstSetup.SCRIPT.indexOf("mesa;") < MesaFirstSetup.SCRIPT.indexOf("tlstore install"));
        // No edition's package name is written into it: the shell's own prefix is used.
        assertFalse(MesaFirstSetup.SCRIPT.contains("com.termux"));
    }
}
