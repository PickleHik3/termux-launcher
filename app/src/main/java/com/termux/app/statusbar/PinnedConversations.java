package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds the rule-matched notifications into one card per conversation. Pure, so it tests on the
 * JVM; the listener service reads the notifications and hands the pieces over.
 *
 * <p>A conversation is the package plus the first of: the notification's shortcut id (what
 * Android itself groups conversations by), its {@code EXTRA_CONVERSATION_TITLE} (a group chat's
 * name), or its sender (the title). A notification with none of the three is its own
 * conversation, so unrelated title-less notifications from one app never merge.</p>
 *
 * <p>The card shows the latest message; its count is the larger of the notifications folded into
 * it and the most messages any one of them carries in its MessagingStyle. The larger, not the sum:
 * a chat app that re-posts one notification with every unread message repeats the earlier ones,
 * and one that posts a notification per message carries one each.</p>
 */
public final class PinnedConversations {

    private PinnedConversations() {}

    /** One matched notification, before grouping. */
    public static final class Candidate {
        @NonNull public final PinnedNotification pin;
        @NonNull public final String conversation;
        /** Messages in its MessagingStyle, or 0 when it has none. */
        public final int messages;

        public Candidate(@NonNull PinnedNotification pin, @NonNull String conversation,
                         int messages) {
            this.pin = pin;
            this.conversation = conversation;
            this.messages = Math.max(0, messages);
        }
    }

    /** The conversation a notification belongs to; see the class comment for the order. */
    @NonNull
    public static String key(@NonNull String packageName, @Nullable String shortcutId,
                             @Nullable String conversationTitle, @Nullable String sender,
                             @NonNull String notificationKey) {
        String scope;
        if (!isEmpty(shortcutId)) scope = "s:" + shortcutId;
        else if (!isEmpty(conversationTitle)) scope = "c:" + conversationTitle;
        else if (!isEmpty(sender)) scope = "t:" + sender;
        else scope = "k:" + notificationKey;
        return packageName + '\n' + scope;
    }

    /**
     * One card per conversation, ordered by each conversation's latest post, oldest first — the
     * order the slot appends new pins in, so the ceiling evicts the stalest conversation.
     */
    @NonNull
    public static List<PinnedNotification> group(@NonNull List<Candidate> candidates) {
        Map<String, List<Candidate>> byConversation = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            List<Candidate> members = byConversation.get(candidate.conversation);
            if (members == null) {
                members = new ArrayList<>();
                byConversation.put(candidate.conversation, members);
            }
            members.add(candidate);
        }
        List<PinnedNotification> cards = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> entry : byConversation.entrySet()) {
            cards.add(fold(entry.getKey(), entry.getValue()));
        }
        cards.sort((a, b) -> Long.compare(a.postTime, b.postTime));
        return cards;
    }

    @NonNull
    private static PinnedNotification fold(@NonNull String conversation,
                                           @NonNull List<Candidate> members) {
        Candidate latest = members.get(0);
        int messages = 0;
        List<String> keys = new ArrayList<>();
        for (Candidate member : members) {
            if (member.pin.postTime >= latest.pin.postTime) latest = member;
            messages = Math.max(messages, member.messages);
            if (!keys.contains(member.pin.key)) keys.add(member.pin.key);
        }
        // The latest key leads, so a dismissal clears the one on screen first.
        keys.remove(latest.pin.key);
        keys.add(0, latest.pin.key);
        int count = Math.max(keys.size(), messages);
        PinnedNotification card = latest.pin.grouped(conversation, keys, count);
        if (card.avatar == null) {
            for (int i = members.size() - 1; i >= 0; i--) {
                PinnedNotification other = members.get(i).pin;
                if (other.avatar != null) {
                    return new PinnedNotification(card.key, card.packageName, card.sender,
                        card.appLabel, card.body, card.matchedRuleId, card.clearOnDismiss,
                        card.postTime, conversation, keys, count, other.avatar);
                }
            }
        }
        return card;
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
