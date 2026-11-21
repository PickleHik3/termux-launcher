package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ContextFitterTest {
    private static long nextId = 1;

    private static AssistantMessage user(String text) {
        return new AssistantMessage(nextId++, 1, AssistantMessage.ROLE_USER, text, AssistantMessage.STATE_COMPLETE, null, 0);
    }

    private static AssistantMessage answer(String text, String state) {
        return new AssistantMessage(nextId++, 1, AssistantMessage.ROLE_ASSISTANT, text, state, null, 0);
    }

    private static String chars(int n) {
        return new String(new char[n]).replace('\0', 'a');
    }

    @Test
    public void emptyChatSendsNothing() {
        ContextFitter.Result r = ContextFitter.fit(new ArrayList<AssistantMessage>(), 4096);
        assertTrue(r.sent.isEmpty());
        assertEquals(-1, r.cutIndex);
    }

    @Test
    public void everythingFitsSendsAllInOrder() {
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("hello"));
        chat.add(answer("hi", AssistantMessage.STATE_COMPLETE));
        chat.add(user("how are you"));
        ContextFitter.Result r = ContextFitter.fit(chat, 4096);
        assertEquals(3, r.sent.size());
        assertEquals("hello", r.sent.get(0).text);
        assertEquals("how are you", r.sent.get(2).text);
        assertEquals(-1, r.cutIndex);
        assertFalse(r.newestTruncated);
    }

    @Test
    public void cutInTheMiddleKeepsTheNewestWholeMessages() {
        // Window 2048: reserve 512, budget 1536. Each 900-char message costs 304.
        List<AssistantMessage> chat = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            chat.add(user(chars(900)));
            chat.add(answer(chars(900), AssistantMessage.STATE_COMPLETE));
        }
        chat.add(user("last"));
        ContextFitter.Result r = ContextFitter.fit(chat, 2048);
        // History goes in whole turns of 608: last (6) + two turns = 1222 fits; a third would
        // pass 1536.
        assertEquals(5, r.sent.size());
        assertEquals(chat.size() - 5, r.cutIndex);
        assertEquals(AssistantMessage.ROLE_USER, r.sent.get(0).role);
        assertEquals("last", r.sent.get(4).text);
        assertEquals(chars(900), r.sent.get(0).text);
        assertFalse(r.newestTruncated);
    }

    @Test
    public void aMessageThatDoesNotFitStopsTheWalkEvenIfOlderOnesWould() {
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("tiny"));
        chat.add(answer(chars(6000), AssistantMessage.STATE_COMPLETE));
        chat.add(user("question"));
        ContextFitter.Result r = ContextFitter.fit(chat, 2048);
        assertEquals(1, r.sent.size());
        assertEquals(2, r.cutIndex);
    }

    @Test
    public void singleHugeMessageIsCutFromItsStart() {
        String huge = "START" + chars(20000) + "END";
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("older"));
        chat.add(answer("reply", AssistantMessage.STATE_COMPLETE));
        chat.add(user(huge));
        ContextFitter.Result r = ContextFitter.fit(chat, 2048);
        assertEquals(1, r.sent.size());
        assertTrue(r.newestTruncated);
        String sent = r.sent.get(0).text;
        assertTrue(sent.endsWith("END"));
        assertFalse(sent.startsWith("START"));
        assertTrue(ContextFitter.estimateTokens(sent) <= 1536);
        assertEquals(2, r.cutIndex);
    }

    @Test
    public void truncationNeverSplitsASurrogatePair() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) sb.appendCodePoint(0x1F600);
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user(sb.toString()));
        ContextFitter.Result r = ContextFitter.fit(chat, 2048);
        String sent = r.sent.get(0).text;
        assertTrue(r.newestTruncated);
        assertTrue(Character.isHighSurrogate(sent.charAt(0)));
        assertEquals(sent.length() / 2, sent.codePointCount(0, sent.length()));
    }

    @Test
    public void errorWaitingAndEmptyAnswersAreLeftOut() {
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("one"));
        chat.add(answer("boom", AssistantMessage.STATE_ERROR));
        chat.add(answer("", AssistantMessage.STATE_STOPPED));
        chat.add(answer("partial", AssistantMessage.STATE_STOPPED));
        chat.add(user("two"));
        ContextFitter.Result r = ContextFitter.fit(chat, 4096);
        assertEquals(3, r.sent.size());
        assertEquals("partial", r.sent.get(1).text);
        assertEquals(-1, r.cutIndex);
    }

    @Test
    public void aQuestionWhoseAnswerFailedIsLeftOutSoRolesAlternate() {
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("q1"));
        chat.add(answer("boom", AssistantMessage.STATE_ERROR));
        chat.add(user("q2"));
        chat.add(answer("a2", AssistantMessage.STATE_COMPLETE));
        chat.add(user("q3"));
        ContextFitter.Result r = ContextFitter.fit(chat, 4096);
        assertEquals(3, r.sent.size());
        assertEquals("q2", r.sent.get(0).text);
        assertEquals("a2", r.sent.get(1).text);
        assertEquals("q3", r.sent.get(2).text);
        for (int i = 0; i < r.sent.size(); i++) {
            assertEquals(i % 2 == 0 ? AssistantMessage.ROLE_USER : AssistantMessage.ROLE_ASSISTANT,
                r.sent.get(i).role);
        }
    }

    @Test
    public void newestUserMessageAnchorsEvenWhenAnAnswerFollows() {
        List<AssistantMessage> chat = new ArrayList<>();
        chat.add(user("q"));
        chat.add(answer("failed", AssistantMessage.STATE_ERROR));
        ContextFitter.Result r = ContextFitter.fit(chat, 4096);
        assertEquals(1, r.sent.size());
        assertEquals("q", r.sent.get(0).text);
    }

    @Test
    public void estimateIsConservativeForNonAscii() {
        assertEquals(4 + 4, ContextFitter.estimateTokens("hello world".substring(0, 0) + "你好世界"));
        assertEquals(ContextFitter.MESSAGE_OVERHEAD_TOKENS, ContextFitter.estimateTokens(""));
        assertEquals(30 / 3 + ContextFitter.MESSAGE_OVERHEAD_TOKENS, ContextFitter.estimateTokens(chars(30)));
    }

    @Test
    public void reserveIsAQuarterOfASmallWindow() {
        assertEquals(1024, ContextFitter.answerReserve(32768));
        assertEquals(512, ContextFitter.answerReserve(2048));
        assertEquals(0, ContextFitter.answerReserve(0));
    }
}
