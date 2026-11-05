package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AssistantSendQueueTest {

    private final List<String> sent = new ArrayList<>();
    private final AssistantSendQueue.Sender sender = (chatId, text) -> sent.add(chatId + ":" + text);

    @Test
    public void nothingGoesWhileTheModelCannotAnswer() {
        AssistantSendQueue queue = new AssistantSendQueue();
        queue.add(1, "How long for an egg?");
        assertFalse(queue.drain(false, false, sender));
        assertTrue(sent.isEmpty());
        assertEquals(1, queue.pending(1).size());
    }

    @Test
    public void heldQuestionsGoOldestFirstOneAtATime() {
        AssistantSendQueue queue = new AssistantSendQueue();
        queue.add(1, "first");
        queue.add(1, "second");
        assertTrue(queue.drain(true, false, sender));
        // The engine is now writing the first answer: the second keeps waiting.
        assertFalse(queue.drain(true, true, sender));
        assertEquals(1, sent.size());
        assertEquals("1:first", sent.get(0));
        assertTrue(queue.drain(true, false, sender));
        assertEquals("1:second", sent.get(1));
        assertTrue(queue.isEmpty());
    }

    @Test
    public void aRefusedQuestionStaysHeld() {
        AssistantSendQueue queue = new AssistantSendQueue();
        queue.add(1, "q");
        assertFalse(queue.drain(true, false, (chatId, text) -> false));
        assertEquals(1, queue.pending(1).size());
    }

    @Test
    public void pendingIsPerChatAndClearedWithTheChat() {
        AssistantSendQueue queue = new AssistantSendQueue();
        queue.add(1, "a");
        queue.add(2, "b");
        assertEquals(1, queue.pending(2).size());
        queue.clear(1);
        assertTrue(queue.pending(1).isEmpty());
        assertEquals(1, queue.pending(2).size());
        assertFalse(queue.isEmpty());
    }
}
