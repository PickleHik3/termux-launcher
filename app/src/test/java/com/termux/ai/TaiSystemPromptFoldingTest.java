package com.termux.ai;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ToolProvider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * codegemma's chat template raises "System role not supported" rather than rendering a system
 * turn; these cover the detection of that failure and the fold that lets the retry go through.
 */
public class TaiSystemPromptFoldingTest {

    @Test
    public void detectsTheCodegemmaWording() {
        assertTrue(TaiSystemPromptFolding.looksLikeSystemRoleRejection(
            new RuntimeException("System role not supported")));
        assertTrue(TaiSystemPromptFolding.looksLikeSystemRoleRejection(
            new RuntimeException("system role is not allowed for this template")));
        // Wrapped by the native binding: still found by walking the cause chain.
        assertTrue(TaiSystemPromptFolding.looksLikeSystemRoleRejection(
            new RuntimeException("native init failed", new RuntimeException("System role not supported"))));
    }

    @Test
    public void leavesUnrelatedFailuresAlone() {
        assertFalse(TaiSystemPromptFolding.looksLikeSystemRoleRejection(
            new RuntimeException("Input token ids are too long")));
        assertFalse(TaiSystemPromptFolding.looksLikeSystemRoleRejection(null));
        assertFalse(TaiSystemPromptFolding.looksLikeSystemRoleRejection(new RuntimeException("no message here") {
            @Override
            public String getMessage() {
                return null;
            }
        }));
    }

    @Test
    public void blankSystemPrompt_leavesTheRequestUntouched() {
        TaiChatRequest request = TaiChatRequest.simple("", "hi");

        TaiChatRequest folded = TaiSystemPromptFolding.foldSystemIntoFirstUserTurn(request, "  ");

        assertSame(request, folded);
    }

    @Test
    public void oneShotRequest_foldsIntoTheTrailingMessage() {
        TaiChatRequest request = TaiChatRequest.simple("Answer only in French.", "Hello there");

        TaiChatRequest folded = TaiSystemPromptFolding.foldSystemIntoFirstUserTurn(request, "Answer only in French.");

        assertEquals("", folded.systemPrompt);
        assertEquals("Answer only in French.\n\nHello there", textOf(folded.message));
        assertTrue(folded.initialMessages.isEmpty());
    }

    @Test
    public void transcriptRequest_foldsIntoTheFirstUserTurnOfTheHistory() {
        List<Message> history = new ArrayList<>();
        history.add(Message.Companion.user("first turn"));
        history.add(Message.Companion.model("first reply"));
        TaiChatRequest request = new TaiChatRequest(
            "Be terse.", history, Message.Companion.user("second turn"),
            Collections.<ToolProvider>emptyList(), true);

        TaiChatRequest folded = TaiSystemPromptFolding.foldSystemIntoFirstUserTurn(request, "Be terse.");

        assertEquals("", folded.systemPrompt);
        assertEquals("Be terse.\n\nfirst turn", textOf(folded.initialMessages.get(0)));
        assertEquals("first reply", textOf(folded.initialMessages.get(1)));
        // The trailing message is the next turn to send, untouched — the system text already
        // landed on the conversation's first turn.
        assertEquals("second turn", textOf(folded.message));
    }

    @Test
    public void preservesMultimodalContentAheadOfTheFoldedText() {
        Message imageMessage = Message.Companion.of(new Content.Text("describe this"));
        TaiChatRequest request = new TaiChatRequest(
            "System rules.", Collections.<Message>emptyList(), imageMessage,
            Collections.<ToolProvider>emptyList(), true);

        TaiChatRequest folded = TaiSystemPromptFolding.foldSystemIntoFirstUserTurn(request, "System rules.");

        List<Content> contents = folded.message.getContents().getContents();
        assertEquals(2, contents.size());
        assertEquals("System rules.\n\n", ((Content.Text) contents.get(0)).getText());
        assertEquals("describe this", ((Content.Text) contents.get(1)).getText());
    }

    private static String textOf(Message message) {
        StringBuilder builder = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text) builder.append(((Content.Text) content).getText());
        }
        return builder.toString();
    }
}
