package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One rule-matched notification held in the widget slot until its dismiss control is tapped. */
public final class PinnedNotification {

    public final String key;
    public final String packageName;
    public final String sender;
    public final String appLabel;
    public final String body;
    public final String matchedRuleId;
    /** Whether the dismiss control should cancel the notification instead of only unpinning it. */
    public final boolean clearOnDismiss;
    public final long postTime;

    public PinnedNotification(@NonNull String key, @NonNull String packageName,
                              @Nullable String sender, @Nullable String appLabel,
                              @Nullable String body, @NonNull String matchedRuleId,
                              boolean clearOnDismiss, long postTime) {
        this.key = key;
        this.packageName = packageName;
        this.sender = sender == null ? "" : sender;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.body = body == null ? "" : body;
        this.matchedRuleId = matchedRuleId;
        this.clearOnDismiss = clearOnDismiss;
        this.postTime = postTime;
    }

    /** Card title: {@code sender · app}, collapsing to whichever half is known. */
    @NonNull
    public String title() {
        if (sender.isEmpty()) return appLabel;
        if (appLabel.isEmpty() || appLabel.equalsIgnoreCase(sender)) return sender;
        return sender + " · " + appLabel;
    }

    @NonNull
    public String senderOrApp() {
        return sender.isEmpty() ? appLabel : sender;
    }

    public boolean sameContentAs(@Nullable PinnedNotification other) {
        return other != null && key.equals(other.key) && sender.equals(other.sender)
            && appLabel.equals(other.appLabel) && body.equals(other.body)
            && clearOnDismiss == other.clearOnDismiss;
    }
}
