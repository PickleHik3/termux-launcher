package com.termux.app.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SetupWizardGateTest {

    @Test
    public void freshInstallShowsAsNotAnUpdater() {
        SetupWizardGate.Decision d = SetupWizardGate.decide(0, false, false);
        assertTrue(d.show);
        assertFalse(d.updater);
    }

    @Test
    public void tourOrUseAsMakesAnUpdater() {
        assertTrue(SetupWizardGate.decide(0, true, false).updater);
        assertTrue(SetupWizardGate.decide(0, false, true).updater);
        assertTrue(SetupWizardGate.decide(0, false, false, true).updater);
        assertFalse(SetupWizardGate.decide(0, false, false, false).updater);
    }

    @Test
    public void currentVersionDoesNotShowAgain() {
        assertFalse(SetupWizardGate.decide(SetupWizardGate.CURRENT, true, true).show);
        assertTrue(SetupWizardGate.decide(SetupWizardGate.CURRENT - 1, true, true).show);
    }

    @Test
    public void migratesOnlyAnUpdaterWhoseWizardIsDueAndNotYetMigrated() {
        assertTrue(SetupWizardGate.shouldMigrate(SetupWizardGate.decide(0, true, false), false));
        assertTrue(SetupWizardGate.shouldMigrate(SetupWizardGate.decide(0, false, true), false));
        assertFalse(SetupWizardGate.shouldMigrate(SetupWizardGate.decide(0, true, false), true));
        assertFalse(SetupWizardGate.shouldMigrate(SetupWizardGate.decide(0, false, false), false));
        assertFalse(SetupWizardGate.shouldMigrate(
            SetupWizardGate.decide(SetupWizardGate.CURRENT, true, true), false));
    }

    @Test
    public void wizardWaitsOnlyWhileTheBootstrapIsMissingAndUnsettled() {
        // Already installed: open at once.
        assertFalse(SetupWizardGate.waitsForBootstrap(true, false));
        // Installing on a fresh install: wait.
        assertTrue(SetupWizardGate.waitsForBootstrap(false, false));
        // The install failed or was given up on, or the backstop found none running: open anyway.
        assertFalse(SetupWizardGate.waitsForBootstrap(false, true));
        assertFalse(SetupWizardGate.waitsForBootstrap(true, true));
    }

    // comesFirst: one case per way out of the wizard and of its card sheet.

    @Test
    public void wizardDueOrWaitingForTheBootstrapComesFirst() {
        // Waiting for the bootstrap, up, or the process died mid-wizard (due again at the start).
        assertTrue(SetupWizardGate.comesFirst(true, false, false, false));
        assertTrue(SetupWizardGate.comesFirst(true, true, false, false));
    }

    @Test
    public void finishOrSkipWithCardsDueHoldsOnlyWhileTheSheetIsUp() {
        assertTrue(SetupWizardGate.comesFirst(false, true, false, false));
        // The sheet marked its cards seen when it opened; once closed (Back, Open launcher, the
        // editor stepping in) nothing is due and nothing waits.
        assertFalse(SetupWizardGate.comesFirst(false, false, false, false));
    }

    @Test
    public void finishOrSkipWithNoCardDueAndKeepHoldNothing() {
        assertFalse(SetupWizardGate.comesFirst(false, false, false, false));
    }

    @Test
    public void dueCardsHoldOnlyUntilTheLaunchHasLooked() {
        // A launcher start before its check has run: the sheet is about to open.
        assertTrue(SetupWizardGate.comesFirst(false, false, true, true));
        // The check ran and could not open them (the editor was up), a card first appearing
        // mid-session, or an activity rebuilt from saved state: they wait for the next start
        // and hold nothing back meanwhile.
        assertFalse(SetupWizardGate.comesFirst(false, false, true, false));
    }
}
