package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetupCardsTest {

    private static final Set<String> NONE = new HashSet<>();

    private static List<SetupCards.Card> build(boolean desktop, boolean ai, boolean home,
                                               SetupCards.Stored stored, String... done) {
        return SetupCards.build(new SetupCards.Facts(desktop, ai, home), stored,
            new HashSet<>(Arrays.asList(done)));
    }

    private static String ids(List<SetupCards.Card> cards) {
        StringBuilder out = new StringBuilder();
        for (SetupCards.Card card : cards) out.append(card.id).append(',');
        return out.toString();
    }

    @Test
    public void everyoneGetsPermissionCardsAndGuide() {
        assertEquals("shizuku,accessibility,notifications,notificationAccess,pinned,guide,",
            ids(build(false, false, false, new SetupCards.Stored())));
    }

    @Test
    public void optionalCardsFollowTheirFacts() {
        assertEquals("shizuku,accessibility,notifications,notificationAccess,pinned,display,ai,home,guide,",
            ids(build(true, true, true, new SetupCards.Stored())));
        assertTrue(ids(build(true, false, false, new SetupCards.Stored())).contains("display"));
        assertFalse(ids(build(true, false, false, new SetupCards.Stored())).contains("ai"));
    }

    @Test
    public void systemDoneIsRecomputedNotStored() {
        SetupCards.Stored stored = new SetupCards.Stored();
        List<SetupCards.Card> cards = build(false, false, false, stored, SetupCards.SHIZUKU);
        assertEquals(SetupCards.State.DONE, cards.get(0).state);
        assertEquals(1, cards.get(0).completed);
        assertEquals(SetupCards.State.OPEN, cards.get(1).state);
        assertFalse(stored.toJson().contains("shizuku"));
    }

    @Test
    public void dismissedCardsAreNotOpenAndDoneBeatsDismissed() {
        SetupCards.Stored stored = new SetupCards.Stored();
        stored.setDismissed(SetupCards.ACCESSIBILITY, true);
        stored.setDismissed(SetupCards.SHIZUKU, true);
        List<SetupCards.Card> cards = build(false, false, false, stored, SetupCards.SHIZUKU);
        assertEquals(SetupCards.State.DONE, cards.get(0).state);
        assertEquals(SetupCards.State.DISMISSED, cards.get(1).state);
        assertEquals(4, SetupCards.openCount(cards));
    }

    @Test
    public void manualStepsCountOnTheStubCards() {
        SetupCards.Stored stored = new SetupCards.Stored();
        stored.completeStep(SetupCards.DISPLAY, 0);
        stored.completeStep(SetupCards.DISPLAY, 1);
        SetupCards.Card display = build(true, false, false, stored).get(5);
        assertEquals(SetupCards.DISPLAY, display.id);
        assertEquals(2, display.completed);
        assertEquals(SetupCards.State.OPEN, display.state);
        stored.completeStep(SetupCards.DISPLAY, 2);
        assertEquals(SetupCards.State.DONE, build(true, false, false, stored).get(5).state);
    }

    @Test
    public void dueNeedsTheWizardDoneAndAnOpenCard() {
        List<SetupCards.Card> open = build(false, false, false, new SetupCards.Stored());
        assertTrue(SetupCards.isDue(true, open));
        assertFalse(SetupCards.isDue(false, open));
        SetupCards.Stored stored = new SetupCards.Stored();
        for (SetupCards.Card card : open) stored.setDismissed(card.id, true);
        assertFalse(SetupCards.isDue(true, build(false, false, false, stored)));
    }

    @Test
    public void storedRoundTripsAndSurvivesGarbage() {
        SetupCards.Stored stored = new SetupCards.Stored();
        stored.setDismissed(SetupCards.AI, true);
        stored.completeStep(SetupCards.AI, 1);
        SetupCards.Stored back = SetupCards.Stored.parse(stored.toJson());
        assertTrue(back.isDismissed(SetupCards.AI));
        assertEquals(1, back.manualCount(SetupCards.AI, 2));
        assertFalse(SetupCards.Stored.parse("not json").isDismissed(SetupCards.AI));
        assertFalse(SetupCards.Stored.parse(null).isDismissed(SetupCards.AI));
    }

    @Test
    public void reopenMakesADismissedCardOpenAgain() {
        SetupCards.Stored stored = new SetupCards.Stored();
        stored.setDismissed(SetupCards.PINNED, true);
        stored.setDismissed(SetupCards.PINNED, false);
        assertEquals(SetupCards.State.OPEN, build(false, false, false, stored).get(4).state);
    }
}
