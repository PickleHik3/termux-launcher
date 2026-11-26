package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.CardFacts;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AssistantCardFactsTest {

    private static AssistantMessage message(String role, int length) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < length; i++) text.append('x');
        return new AssistantMessage(1, 1, role, text.toString(), AssistantMessage.STATE_COMPLETE,
            null, 0);
    }

    @Test public void noMessagesIsNoFacts() {
        assertNull(AssistantCardFacts.of(new ArrayList<>()));
    }

    @Test public void keepsTheLastBubblesWithWhoSpokeAndHowMuch() {
        List<AssistantMessage> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) all.add(message(AssistantMessage.ROLE_ASSISTANT, 10));
        all.add(message(AssistantMessage.ROLE_USER, AssistantCardFacts.FULL_LINE_CHARS * 3));
        CardFacts.Assistant facts = AssistantCardFacts.of(all);
        assertEquals(CardFacts.MAX_BUBBLES, facts.fill.length);
        assertTrue(facts.fromUser[facts.fromUser.length - 1]);
        assertFalse(facts.fromUser[0]);
        assertEquals(1f, facts.fill[facts.fill.length - 1], 0f);
    }
}
