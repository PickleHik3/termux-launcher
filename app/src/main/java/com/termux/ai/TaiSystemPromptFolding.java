package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Some chat templates reject a system-role message outright — codegemma's among them, which
 * raises a jinja {@code raise_exception} the LiteRT-LM native layer surfaces as a
 * {@link RuntimeException} out of {@code Engine.createConversation}. When that happens the system
 * text is folded into the first user turn instead: {@link LiteRtTaiRuntime} remembers the verdict
 * per model (see {@link TaiRuntimeHistory#recordSystemRoleUnsupported}) so later conversations with
 * that model go straight to the folded form without paying for the failed attempt again.
 */
final class TaiSystemPromptFolding {
    private TaiSystemPromptFolding() {
    }

    /**
     * Whether {@code error} looks like a chat template rejecting a system-role message (the
     * wording HF/Gemma-family jinja templates raise via {@code raise_exception(...)} is typically
     * "System role not supported"). Walks the cause chain since the native binding sometimes wraps
     * the original error.
     */
    static boolean looksLikeSystemRoleRejection(@Nullable Throwable error) {
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth < 8) {
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("system") && lower.contains("role")
                    && (lower.contains("not supported") || lower.contains("not allowed")
                        || lower.contains("unsupported"))) {
                    return true;
                }
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }

    /**
     * {@code request} with an empty system prompt and {@code systemPrompt} prefixed onto the first
     * user turn instead — a clear separator (system text, a blank line, then the turn's own text),
     * the way a person would type both into one message. Folds into the first user-role message in
     * {@link TaiChatRequest#initialMessages} when there is one, otherwise into the trailing
     * {@link TaiChatRequest#message} (the only turn a one-shot {@code chat()}/{@code complete()}
     * call carries). {@code request} unchanged when {@code systemPrompt} is blank.
     */
    @NonNull
    static TaiChatRequest foldSystemIntoFirstUserTurn(@NonNull TaiChatRequest request, @NonNull String systemPrompt) {
        String trimmedSystem = systemPrompt.trim();
        if (trimmedSystem.isEmpty()) return request;
        List<Message> initialMessages = request.initialMessages;
        for (int i = 0; i < initialMessages.size(); i++) {
            if (initialMessages.get(i).getRole() == Role.USER) {
                List<Message> folded = new ArrayList<>(initialMessages);
                folded.set(i, withPrefixedText(folded.get(i), trimmedSystem));
                return withFolding(request, folded, request.message);
            }
        }
        // No user turn in the replayed history (a one-shot request, or history that starts on a
        // different role): the trailing message is the only turn left to carry the system text.
        return withFolding(request, initialMessages, withPrefixedText(request.message, trimmedSystem));
    }

    @NonNull
    private static TaiChatRequest withFolding(
        @NonNull TaiChatRequest request,
        @NonNull List<Message> initialMessages,
        @NonNull Message message
    ) {
        return new TaiChatRequest(
            "",
            initialMessages,
            message,
            request.tools,
            request.reusableConversation,
            request.messagesJson,
            request.toolDefinitions,
            request.toolChoice,
            request.stopSequences
        );
    }

    @NonNull
    private static Message withPrefixedText(@NonNull Message message, @NonNull String systemPrompt) {
        List<Content> contents = new ArrayList<>();
        contents.add(new Content.Text(systemPrompt + "\n\n"));
        contents.addAll(message.getContents().getContents());
        return new Message(message.getRole(), Contents.Companion.of(contents), message.getToolCalls(), message.getChannels());
    }
}
