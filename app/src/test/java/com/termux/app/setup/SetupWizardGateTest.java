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
}
