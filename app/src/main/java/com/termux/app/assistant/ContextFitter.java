package com.termux.app.assistant;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chooses which messages of a chat go to the model: the newest ones that fit the serving window
 * less room for the answer. Pure; nothing is stored.
 *
 * <p>Tokens are estimated, not counted: {@code SentencePieceBpeTokenizer} has no public API and
 * lives with the runtime, and loading the Gemma vocabulary in the app process is model work. The
 * estimate errs high so the prompt never overflows: an ASCII character is a third of a token
 * (English averages about a quarter), any other character is a whole token (CJK and emoji are
 * about one), and every message adds {@link #MESSAGE_OVERHEAD_TOKENS} for the turn markers.
 */
public final class ContextFitter {
    /** Tokens reserved for the answer, unless that is more than a quarter of the window. */
    public static final int ANSWER_RESERVE_TOKENS = 1024;
    public static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private ContextFitter() {
    }

    /** One message as it is sent. */
    public static final class Sent {
        @NonNull public final String role;
        @NonNull public final String text;

        Sent(@NonNull String role, @NonNull String text) {
            this.role = role;
            this.text = text;
        }
    }

    public static final class Result {
        /** The messages to send, oldest first. */
        @NonNull public final List<Sent> sent;
        /**
         * Index, in the list given to {@link #fit}, of the first message sent when older messages
         * were left out; {@code -1} when everything that could be sent is. The divider goes above it.
         */
        public final int cutIndex;
        /** True when the newest user message alone was over budget and was cut from its start. */
        public final boolean newestTruncated;

        Result(@NonNull List<Sent> sent, int cutIndex, boolean newestTruncated) {
            this.sent = sent;
            this.cutIndex = cutIndex;
            this.newestTruncated = newestTruncated;
        }
    }

    public static int answerReserve(int windowTokens) {
        return Math.max(0, Math.min(ANSWER_RESERVE_TOKENS, windowTokens / 4));
    }

    public static int estimateTokens(@NonNull String text) {
        int ascii = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 128) ascii++;
            else other++;
        }
        return (ascii + 2) / 3 + other + MESSAGE_OVERHEAD_TOKENS;
    }

    /**
     * A message is sent when it is from the user, or an answer with text that finished or was
     * stopped. Errors, waiting and still-streaming answers are not.
     */
    public static boolean isSendable(@NonNull AssistantMessage m) {
        if (m.isUser()) return !m.text.trim().isEmpty();
        return !m.text.isEmpty()
            && (AssistantMessage.STATE_COMPLETE.equals(m.state) || AssistantMessage.STATE_STOPPED.equals(m.state));
    }

    /**
     * @param messages    the chat up to and including the newest user message, oldest first
     * @param windowTokens the serving window
     */
    @NonNull
    public static Result fit(@NonNull List<AssistantMessage> messages, int windowTokens) {
        int budget = Math.max(1, windowTokens - answerReserve(windowTokens));
        int newest = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isUser() && isSendable(messages.get(i))) {
                newest = i;
                break;
            }
        }
        if (newest < 0) return new Result(Collections.<Sent>emptyList(), -1, false);

        List<Sent> reversed = new ArrayList<>();
        AssistantMessage last = messages.get(newest);
        String lastText = last.text;
        boolean truncated = false;
        int used = estimateTokens(lastText);
        if (used > budget) {
            lastText = tailThatFits(lastText, budget);
            truncated = true;
            used = budget;
        }
        reversed.add(new Sent(last.role, lastText));
        int first = newest;
        boolean skipped = false;
        for (int i = newest - 1; i >= 0; i--) {
            AssistantMessage m = messages.get(i);
            if (!isSendable(m)) continue;
            if (skipped) continue;
            int cost = estimateTokens(m.text);
            if (used + cost > budget) {
                skipped = true;
                continue;
            }
            used += cost;
            reversed.add(new Sent(m.role, m.text));
            first = i;
        }
        Collections.reverse(reversed);
        return new Result(reversed, skipped ? first : -1, truncated);
    }

    /** The longest end of the text that fits the budget, never starting inside a surrogate pair. */
    @NonNull
    private static String tailThatFits(@NonNull String text, int budget) {
        int lo = 0;
        int hi = text.length();
        // The smallest start index whose tail fits; the estimate only grows as the tail grows.
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (estimateTokens(text.substring(mid)) <= budget) hi = mid;
            else lo = mid + 1;
        }
        if (lo < text.length() && Character.isLowSurrogate(text.charAt(lo))) lo++;
        return text.substring(lo);
    }
}
