package com.termux.app.statusbar;

import android.graphics.drawable.Icon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * One pinned card in the widget slot: a rule-matched conversation, shown by its latest message.
 *
 * <p>Several notifications from the same conversation ({@link PinnedConversations#key}) fold into
 * one card. {@link #key} is the latest one's, which is what a tap opens; {@link #keys} are all of
 * them, which is what a dismissal clears; {@link #count} is how many messages the card stands for.
 */
public final class PinnedNotification {

    public final String key;
    public final String packageName;
    public final String sender;
    public final String appLabel;
    public final String body;
    public final String matchedRuleId;
    /** Whether dismissing the pin should cancel the notification instead of only unpinning it. */
    public final boolean clearOnDismiss;
    public final long postTime;
    /** The conversation this card stands for; stable while new messages arrive in it. */
    public final String conversationId;
    /** Every notification key folded into this card, {@link #key} included. */
    public final List<String> keys;
    /** Messages the card stands for; the unread chip shows from two on. */
    public final int count;
    /** The sender's picture (MessagingStyle person icon, else the large icon), or null. */
    @Nullable public final Icon avatar;

    public PinnedNotification(@NonNull String key, @NonNull String packageName,
                              @Nullable String sender, @Nullable String appLabel,
                              @Nullable String body, @NonNull String matchedRuleId,
                              boolean clearOnDismiss, long postTime) {
        this(key, packageName, sender, appLabel, body, matchedRuleId, clearOnDismiss, postTime,
            null, null, 1, null);
    }

    public PinnedNotification(@NonNull String key, @NonNull String packageName,
                              @Nullable String sender, @Nullable String appLabel,
                              @Nullable String body, @NonNull String matchedRuleId,
                              boolean clearOnDismiss, long postTime,
                              @Nullable String conversationId, @Nullable List<String> keys,
                              int count, @Nullable Icon avatar) {
        this.key = key;
        this.packageName = packageName;
        this.sender = sender == null ? "" : sender;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.body = body == null ? "" : body;
        this.matchedRuleId = matchedRuleId;
        this.clearOnDismiss = clearOnDismiss;
        this.postTime = postTime;
        this.conversationId = conversationId == null || conversationId.isEmpty()
            ? key : conversationId;
        this.keys = keys == null || keys.isEmpty()
            ? Collections.singletonList(key) : Collections.unmodifiableList(keys);
        this.count = Math.max(1, count);
        this.avatar = avatar;
    }

    /** This card folded over a whole conversation. */
    @NonNull
    public PinnedNotification grouped(@NonNull String conversation, @NonNull List<String> allKeys,
                                      int messages) {
        return new PinnedNotification(key, packageName, sender, appLabel, body, matchedRuleId,
            clearOnDismiss, postTime, conversation, allKeys, messages, avatar);
    }

    /** Card title: {@code sender · app}, collapsing to whichever half is known. */
    @NonNull
    public String title() {
        if (sender.isEmpty()) return appLabel;
        if (appLabel.isEmpty() || appLabel.equalsIgnoreCase(sender)) return sender;
        return sender + " · " + appLabel;
    }

    /** The card's bold line: the sender, or the app's name when the notification has no title. */
    @NonNull
    public String senderOrApp() {
        return sender.isEmpty() ? appLabel : sender;
    }

    public boolean sameContentAs(@Nullable PinnedNotification other) {
        return other != null && key.equals(other.key) && sender.equals(other.sender)
            && appLabel.equals(other.appLabel) && body.equals(other.body)
            && clearOnDismiss == other.clearOnDismiss && postTime == other.postTime
            && conversationId.equals(other.conversationId) && keys.equals(other.keys)
            && count == other.count && (avatar == null) == (other.avatar == null);
    }
}
