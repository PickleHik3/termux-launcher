package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The pinned card's pure pieces: the age label, the conversation grouping, the swipe arithmetic
 * and the slot's height budget.
 */
public class PinnedCardLogicTest {

    private static final long MINUTE = 60_000L;
    private static final long NOW = 1_000_000_000_000L;

    // ---- Relative time ----------------------------------------------------

    @Test
    public void ageReadsInWholeUnits() {
        assertEquals("now", PinnedRelativeTime.format(NOW, NOW));
        assertEquals("now", PinnedRelativeTime.format(NOW, NOW - 59_000L));
        assertEquals("1m", PinnedRelativeTime.format(NOW, NOW - MINUTE));
        assertEquals("4m", PinnedRelativeTime.format(NOW, NOW - 4 * MINUTE - 30_000L));
        assertEquals("59m", PinnedRelativeTime.format(NOW, NOW - 59 * MINUTE));
        assertEquals("2h", PinnedRelativeTime.format(NOW, NOW - 125 * MINUTE));
        assertEquals("1d", PinnedRelativeTime.format(NOW, NOW - 25 * 60 * MINUTE));
        assertEquals("a post from the future is now", "now",
            PinnedRelativeTime.format(NOW, NOW + 5 * MINUTE));
        assertEquals("no post time at all is now", "now", PinnedRelativeTime.format(NOW, 0L));
    }

    @Test
    public void theLabelIsRefreshedAtMostOnceAMinute() {
        assertEquals(MINUTE, PinnedRelativeTime.msUntilChange(NOW, NOW));
        assertEquals(MINUTE, PinnedRelativeTime.msUntilChange(NOW, NOW - 30_000L));
        assertEquals("hours change on the hour", 55 * MINUTE,
            PinnedRelativeTime.msUntilChange(NOW, NOW - 65 * MINUTE));
    }

    // ---- Grouping ---------------------------------------------------------

    private static PinnedNotification pin(String key, String sender, String body, long time) {
        return new PinnedNotification(key, "com.chat", sender, "Chat", body, "rule", false, time);
    }

    @Test
    public void theConversationKeyPrefersShortcutThenConversationTitleThenSender() {
        assertEquals("com.chat\ns:amma",
            PinnedConversations.key("com.chat", "amma", "Family", "Amma", "k1"));
        assertEquals("com.chat\nc:Family",
            PinnedConversations.key("com.chat", "", "Family", "Amma", "k1"));
        assertEquals("com.chat\nt:Amma",
            PinnedConversations.key("com.chat", null, null, "Amma", "k1"));
        assertEquals("nothing to go by: the notification is its own conversation",
            "com.chat\nk:k1", PinnedConversations.key("com.chat", null, " ", "", "k1"));
        assertNotEquals(PinnedConversations.key("com.a", null, null, "Amma", "k"),
            PinnedConversations.key("com.b", null, null, "Amma", "k"));
    }

    @Test
    public void oneConversationFoldsIntoOneCardShowingTheLatestAndCountingEveryMessage() {
        List<PinnedConversations.Candidate> candidates = Arrays.asList(
            new PinnedConversations.Candidate(pin("k1", "Amma", "Boarding", 100L), "c:amma", 0),
            new PinnedConversations.Candidate(pin("k2", "Ravi", "Lunch?", 150L), "c:ravi", 0),
            new PinnedConversations.Candidate(pin("k3", "Amma", "Landed", 200L), "c:amma", 0));
        List<PinnedNotification> cards = PinnedConversations.group(candidates);
        assertEquals(2, cards.size());
        PinnedNotification amma = cards.get(1);
        assertEquals("ordered by each conversation's latest post", "c:ravi",
            cards.get(0).conversationId);
        assertEquals("the latest message is shown and opened", "k3", amma.key);
        assertEquals("Landed", amma.body);
        assertEquals("every key goes on dismissal, the latest first",
            Arrays.asList("k3", "k1"), amma.keys);
        assertEquals(2, amma.count);
        assertEquals(1, cards.get(0).count);
        assertEquals(Collections.singletonList("k2"), cards.get(0).keys);
    }

    @Test
    public void aMessagingStyleCountWinsOverTheNotificationCount() {
        List<PinnedNotification> cards = PinnedConversations.group(Collections.singletonList(
            new PinnedConversations.Candidate(pin("k1", "Amma", "Landed", 100L), "c:amma", 5)));
        assertEquals("one notification carrying five messages is five", 5, cards.get(0).count);
    }

    // ---- Swipe ------------------------------------------------------------

    @Test
    public void theAxisIsDecidedOncePastTheSlop() {
        assertEquals(PinnedSwipe.Axis.UNDECIDED, PinnedSwipe.decide(5f, 4f, 8f));
        assertEquals(PinnedSwipe.Axis.HORIZONTAL, PinnedSwipe.decide(12f, 6f, 8f));
        assertEquals(PinnedSwipe.Axis.HORIZONTAL, PinnedSwipe.decide(-12f, 6f, 8f));
        assertEquals(PinnedSwipe.Axis.VERTICAL, PinnedSwipe.decide(6f, -12f, 8f));
        assertEquals("a diagonal is the run's, not the card's",
            PinnedSwipe.Axis.VERTICAL, PinnedSwipe.decide(10f, 10f, 8f));
    }

    @Test
    public void aReleaseDismissesPastAThirdOrOnAFlingTheWayTheCardMoved() {
        float width = 300f;
        float minFling = 50f;
        assertTrue(PinnedSwipe.commits(106f, width, 0f, 0f, minFling));
        assertTrue(PinnedSwipe.commits(-106f, width, 0f, 0f, minFling));
        assertFalse(PinnedSwipe.commits(100f, width, 0f, 0f, minFling));
        assertTrue("a fling from a short way", PinnedSwipe.commits(30f, width, 400f, 0f, minFling));
        assertFalse("a fling back the other way springs back",
            PinnedSwipe.commits(30f, width, -400f, 0f, minFling));
        assertFalse("a slow release springs back",
            PinnedSwipe.commits(30f, width, 200f, 0f, minFling));
        assertFalse("a fling more vertical than sideways is not a dismissal",
            PinnedSwipe.commits(30f, width, 400f, 600f, minFling));
    }

    @Test
    public void theCardFadesAsItGoes() {
        assertEquals(1f, PinnedSwipe.alphaFor(0f, 300f), 0f);
        assertEquals(.5f, PinnedSwipe.alphaFor(-150f, 300f), .001f);
        assertEquals(0f, PinnedSwipe.alphaFor(400f, 300f), 0f);
    }

    // ---- Slot height budget -----------------------------------------------

    private static final float DENSITY = 2.625f; // pong

    @Test
    public void theCardAndMediaColumnKeepItsAirInsideTheSlot() {
        int slot = Math.round(68 * DENSITY);
        int air = Math.round(TopPaneSlotBudget.AIR_DP * DENSITY);
        TopPaneSlotBudget.Column column = TopPaneSlotBudget.layout(
            TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, 1, slot, DENSITY);
        assertTrue("air above the card", column.cardsTop >= air);
        int bottom = column.mediaTop + column.mediaHeight;
        assertTrue("air below the strip", slot - bottom >= air);
        assertEquals("the strip keeps its height",
            Math.round(MediaWidgetView.STRIP_HEIGHT_DP * DENSITY), column.mediaHeight);
        assertTrue("the card gave way, not the air",
            column.cardsHeight < Math.round(PinnedNotificationsView.CONTENTION_CARD_HEIGHT_DP * DENSITY));
        assertTrue("and still reads as a card", column.cardsHeight >= Math.round(30 * DENSITY));
        assertTrue("the gap stays at least its floor", column.mediaTop - (column.cardsTop
            + column.cardsHeight) >= Math.round(TopPaneSlotBudget.CONTENTION_GAP_MIN_DP * DENSITY));
        assertEquals("centred: the air is symmetric", column.cardsTop, slot - bottom, 1);
    }

    @Test
    public void cardsAloneAreCentredWithTheSameAir() {
        int slot = Math.round(68 * DENSITY);
        int air = Math.round(TopPaneSlotBudget.AIR_DP * DENSITY);
        TopPaneSlotBudget.Column one = TopPaneSlotBudget.layout(TopPaneSlotMode.NOTIFICATIONS, 1,
            slot, DENSITY);
        assertEquals(Math.round(TopPaneSlotBudget.SINGLE_CARD_DP * DENSITY), one.cardsHeight);
        assertEquals(one.cardsTop, slot - one.cardsTop - one.cardsHeight, 1);

        TopPaneSlotBudget.Column rows = TopPaneSlotBudget.layout(TopPaneSlotMode.NOTIFICATIONS, 3,
            slot, DENSITY);
        assertEquals("two or more rows fill what the air leaves", air, rows.cardsTop);
        assertEquals(slot - 2 * air, rows.cardsHeight);
    }

    @Test
    public void theCapsuleSlotGetsTheSameAirAndAFullerCard() {
        int docked = TopPaneSlotBudget.layout(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, 1,
            Math.round(68 * DENSITY), DENSITY).cardsHeight;
        int capsule = TopPaneSlotBudget.layout(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, 1,
            Math.round(72 * DENSITY), DENSITY).cardsHeight;
        assertTrue(capsule > docked);
        assertEquals(0, TopPaneSlotBudget.layout(TopPaneSlotMode.CLOCK_ONLY, 0, 180, DENSITY)
            .cardsHeight);
    }
}
