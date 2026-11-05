package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AssistantRowsTest {

    private static AssistantMessage user(long id, String text) {
        return new AssistantMessage(id, 1, AssistantMessage.ROLE_USER, text, AssistantMessage.STATE_COMPLETE, null, id);
    }

    private static AssistantMessage answer(long id, String text, String state, String error) {
        return new AssistantMessage(id, 1, AssistantMessage.ROLE_ASSISTANT, text, state, error, id);
    }

    private static List<AssistantRows.Row> build(List<AssistantMessage> messages, int cut, AssistantRows.Context context) {
        return AssistantRows.build(messages, cut, context);
    }

    @Test
    public void aFinishedAnswerOffersCopyShareAndTryAgainOnlyWhenNewest() {
        List<AssistantMessage> messages = Arrays.asList(
            user(1, "q1"), answer(2, "a1", AssistantMessage.STATE_COMPLETE, null),
            user(3, "q2"), answer(4, "a2", AssistantMessage.STATE_COMPLETE, null));
        List<AssistantRows.Row> rows = build(messages, -1, new AssistantRows.Context());
        assertEquals(4, rows.size());
        assertTrue(rows.get(1).copyShare);
        assertFalse("older answers keep Copy and Share but cannot be retried", rows.get(1).retry);
        assertTrue(rows.get(3).copyShare);
        assertTrue(rows.get(3).retry);
    }

    @Test
    public void anAnswerWithNoTextYetIsTheWorkingLine() {
        AssistantRows.Context context = new AssistantRows.Context();
        context.generatingId = 2;
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "", AssistantMessage.STATE_STREAMING, null)), -1, context);
        assertEquals(AssistantRows.Type.WORKING, rows.get(1).type);
    }

    @Test
    public void aStreamingAnswerShowsTheEnginesLiveTextWithNoActions() {
        AssistantRows.Context context = new AssistantRows.Context();
        context.generatingId = 2;
        context.liveText = "RAM holds what";
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "RAM", AssistantMessage.STATE_STREAMING, null)), -1, context);
        AssistantRows.Row row = rows.get(1);
        assertEquals(AssistantRows.Type.ANSWER, row.type);
        assertEquals("RAM holds what", row.text);
        assertTrue(row.streaming);
        assertFalse(row.copyShare);
        assertFalse(row.retry);
    }

    @Test
    public void aStreamingAnswerNobodyIsWritingReadsAsStopped() {
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "half an ans", AssistantMessage.STATE_STREAMING, null)), -1, new AssistantRows.Context());
        assertTrue(rows.get(1).stopped);
        assertFalse(rows.get(1).streaming);
        assertEquals("half an ans", rows.get(1).text);
    }

    @Test
    public void stoppedKeepsTheTextAndMarksIt() {
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "Hi Sam, the heater", AssistantMessage.STATE_STOPPED, null)), -1, new AssistantRows.Context());
        AssistantRows.Row row = rows.get(1);
        assertTrue(row.stopped);
        assertTrue(row.copyShare);
        assertTrue(row.retry);
        assertEquals("Hi Sam, the heater", row.text);
    }

    @Test
    public void stoppedBeforeTheFirstWordKeepsTheMessageWithTryAgainOnly() {
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "", AssistantMessage.STATE_STOPPED, null)), -1, new AssistantRows.Context());
        AssistantRows.Row row = rows.get(1);
        assertTrue(row.stopped);
        assertFalse(row.copyShare);
        assertTrue(row.retry);
    }

    @Test
    public void anErrorKeepsThePartialAnswerAndAddsAnErrorRow() {
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "The lease asks", AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_RUNTIME_DIED)),
            -1, new AssistantRows.Context());
        assertEquals(3, rows.size());
        assertEquals(AssistantRows.Type.ANSWER, rows.get(1).type);
        assertFalse(rows.get(1).copyShare);
        AssistantRows.Row error = rows.get(2);
        assertEquals(AssistantRows.Type.ERROR, error.type);
        assertEquals(AssistantRows.ErrorKind.MODEL_STOPPED, error.error);
        assertTrue(error.retry);
    }

    @Test
    public void anErrorWithNoTextIsOnlyTheErrorRow() {
        List<AssistantRows.Row> rows = build(Arrays.asList(user(1, "q"),
            answer(2, "", AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_TIMEOUT)), -1, new AssistantRows.Context());
        assertEquals(2, rows.size());
        assertEquals(AssistantRows.ErrorKind.TIMEOUT, rows.get(1).error);
    }

    @Test
    public void everyErrorCodeMapsToItsCopyAndRecovery() {
        assertEquals(AssistantRows.ErrorKind.MODEL_STOPPED, AssistantRows.errorKind(AssistantMessage.ERROR_RUNTIME_DIED));
        assertEquals(AssistantRows.ErrorKind.NOT_DOWNLOADED, AssistantRows.errorKind(AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED));
        assertEquals(AssistantRows.ErrorKind.NOT_LOADED, AssistantRows.errorKind(AssistantMessage.ERROR_MODEL_NOT_LOADED));
        assertEquals(AssistantRows.ErrorKind.MEMORY, AssistantRows.errorKind(AssistantMessage.ERROR_NOT_ENOUGH_MEMORY));
        assertEquals(AssistantRows.ErrorKind.TIMEOUT, AssistantRows.errorKind(AssistantMessage.ERROR_TIMEOUT));
        assertEquals(AssistantRows.ErrorKind.EMPTY, AssistantRows.errorKind(AssistantMessage.ERROR_EMPTY_ANSWER));
        assertEquals(AssistantRows.ErrorKind.OTHER, AssistantRows.errorKind(AssistantMessage.ERROR_OTHER));
        assertEquals(AssistantRows.ErrorKind.OTHER, AssistantRows.errorKind(null));
        assertEquals(AssistantRows.ErrorAction.CHOOSE_MODEL, AssistantRows.errorAction(AssistantRows.ErrorKind.NOT_DOWNLOADED));
        assertEquals(AssistantRows.ErrorAction.TRY_AGAIN, AssistantRows.errorAction(AssistantRows.ErrorKind.MODEL_STOPPED));
        for (AssistantRows.ErrorKind kind : AssistantRows.ErrorKind.values()) {
            assertTrue(AssistantRows.errorTitle(kind) != 0);
        }
        assertEquals(0, AssistantRows.errorBody(AssistantRows.ErrorKind.TIMEOUT, true));
        assertTrue(AssistantRows.errorBody(AssistantRows.ErrorKind.MEMORY, true)
            != AssistantRows.errorBody(AssistantRows.ErrorKind.MEMORY, false));
    }

    @Test
    public void theMemoryErrorOffersE2bOnlyWhenItIsOnThePhone() {
        AssistantRows.Context context = new AssistantRows.Context();
        context.offerE2b = true;
        List<AssistantMessage> messages = Arrays.asList(user(1, "q"),
            answer(2, "", AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_NOT_ENOUGH_MEMORY));
        assertTrue(build(messages, -1, context).get(1).offerE2b);
        assertFalse(build(messages, -1, new AssistantRows.Context()).get(1).offerE2b);
    }

    @Test
    public void theDividerGoesAboveTheFirstSentMessage() {
        List<AssistantMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(i % 2 == 0 ? user(i + 1, repeat('x', 90))
                : answer(i + 1, repeat('y', 90), AssistantMessage.STATE_COMPLETE, null));
        }
        int cut = AssistantRows.cutIndex(messages, 200);
        assertEquals(2, cut);
        List<AssistantRows.Row> rows = build(messages, cut, new AssistantRows.Context());
        assertEquals(AssistantRows.Type.CUTOFF, rows.get(2).type);
        assertEquals("u3", rows.get(3).key);
        long dividers = 0;
        for (AssistantRows.Row row : rows) if (row.type == AssistantRows.Type.CUTOFF) dividers++;
        assertEquals("at most one divider per chat", 1, dividers);
    }

    @Test
    public void noDividerWhenEverythingFits() {
        List<AssistantMessage> messages = Arrays.asList(user(1, "hi"),
            answer(2, "hello", AssistantMessage.STATE_COMPLETE, null));
        assertEquals(-1, AssistantRows.cutIndex(messages, 4096));
        for (AssistantRows.Row row : build(messages, -1, new AssistantRows.Context())) {
            assertFalse(row.type == AssistantRows.Type.CUTOFF);
        }
        assertEquals(-1, AssistantRows.cutIndex(Collections.<AssistantMessage>emptyList(), 4096));
    }

    @Test
    public void heldQuestionsShowAfterTheChatWithOneWaitingLine() {
        AssistantRows.Context context = new AssistantRows.Context();
        context.pending = Arrays.asList("How long for an egg?", "And rice?");
        List<AssistantRows.Row> rows = build(Collections.<AssistantMessage>emptyList(), -1, context);
        assertEquals(3, rows.size());
        assertEquals(AssistantRows.Type.USER, rows.get(0).type);
        assertEquals("How long for an egg?", rows.get(0).text);
        assertEquals(AssistantRows.Type.USER, rows.get(1).type);
        assertEquals(AssistantRows.Type.WAITING, rows.get(2).type);
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }
}
