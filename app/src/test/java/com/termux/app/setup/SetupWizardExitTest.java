package com.termux.app.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SetupWizardExitTest {

    @Test
    public void skipKeepsTheChoicesAndOffersTheCardsLikeFinish() {
        assertTrue(SetupWizardExit.appliesChoices(SetupWizardExit.Way.SKIP));
        assertTrue(SetupWizardExit.offersCards(SetupWizardExit.Way.SKIP));
        assertFalse(SetupWizardExit.restoresSnapshot(SetupWizardExit.Way.SKIP));
        assertTrue(SetupWizardExit.appliesChoices(SetupWizardExit.Way.FINISH));
        assertTrue(SetupWizardExit.offersCards(SetupWizardExit.Way.FINISH));
    }

    @Test
    public void keepPutsTheSnapshotBackAndOffersNoCards() {
        assertTrue(SetupWizardExit.restoresSnapshot(SetupWizardExit.Way.KEEP));
        assertFalse(SetupWizardExit.appliesChoices(SetupWizardExit.Way.KEEP));
        assertFalse(SetupWizardExit.offersCards(SetupWizardExit.Way.KEEP));
    }
}
