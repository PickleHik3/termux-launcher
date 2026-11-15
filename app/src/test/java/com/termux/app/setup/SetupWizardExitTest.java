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
        assertTrue(SetupWizardExit.marksCardsSeen(SetupWizardExit.Way.KEEP));
        assertFalse(SetupWizardExit.marksCardsSeen(SetupWizardExit.Way.FINISH));
        assertFalse(SetupWizardExit.marksCardsSeen(SetupWizardExit.Way.SKIP));
    }

    @Test
    public void afterKeepNoCardIsDueAtTheNextStart() {
        // What Keep stores: a known seen set holding every card that exists now.
        java.util.List<SetupCards.Card> cards = SetupCards.build(
            new SetupCards.Facts(true, true, true), new SetupCards.Stored(),
            new java.util.HashSet<>());
        SetupCards.Stored kept = new SetupCards.Stored();
        assertTrue(kept.markSeen(cards));
        assertFalse(SetupCards.isDueForLaunch(true, cards, true, kept));
        // What Finish and Skip store: an empty known seen set, so the cards are due.
        SetupCards.Stored finished = new SetupCards.Stored();
        assertTrue(finished.startSeen());
        assertTrue(SetupCards.isDueForLaunch(true, cards, true, finished));
    }
}
