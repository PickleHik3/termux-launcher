package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One distinct message from a notification, as the history stores and returns it.
 *
 * <p>A notification is not one row. An InboxStyle bundle carries a line per email and a
 * MessagingStyle one a message per chat line, so {@link #explode} turns each into its own row and
 * they share the notification's {@link #key}. {@link #removedTime} is when the notification left
 * the shade, kept on the row rather than as a second event; zero means it is still there.
 */
public final class LauncherCtlNotificationEvent {
    private static final DateTimeFormatter LINE_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /** One line of a MessagingStyle conversation. */
    public static final class Message {
        @Nullable public final String sender;
        @Nullable public final String text;
        /** Milliseconds since the epoch, or 0 when the app gave none. */
        public final long time;

        public Message(@Nullable String sender, @Nullable String text, long time) {
            this.sender = sender;
            this.text = text;
            this.time = time;
        }
    }

    public final long id;
    public final String key;
    public final String packageName;
    @Nullable public final String appLabel;
    @Nullable public final String conversation;
    @Nullable public final String title;
    @Nullable public final String sender;
    @Nullable public final String text;
    @Nullable public final String subText;
    @Nullable public final String category;
    @Nullable public final String channelId;
    /** When the message was sent when the app says so, otherwise when the notification posted. */
    public final long messageTime;
    public final long postTime;
    public final long recordedTime;
    public final long removedTime;
    /** Whether {@link #messageTime} came from the app, which makes it part of the identity. */
    public final boolean explicitTime;

    public LauncherCtlNotificationEvent(long id, @NonNull String key, @NonNull String packageName,
                                        @Nullable String appLabel, @Nullable String conversation,
                                        @Nullable String title, @Nullable String sender,
                                        @Nullable String text, @Nullable String subText,
                                        @Nullable String category, @Nullable String channelId,
                                        long messageTime, long postTime, long recordedTime,
                                        long removedTime, boolean explicitTime) {
        this.id = id;
        this.key = key;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.conversation = conversation;
        this.title = title;
        this.sender = sender;
        this.text = text;
        this.subText = subText;
        this.category = category;
        this.channelId = channelId;
        this.messageTime = messageTime;
        this.postTime = postTime;
        this.recordedTime = recordedTime;
        this.removedTime = removedTime;
        this.explicitTime = explicitTime;
    }

    /**
     * Splits one notification into a row per distinct message.
     *
     * <p>MessagingStyle messages win over InboxStyle lines, which win over the single body. For a
     * plain notification the expanded text replaces the collapsed one when there is any: it is the
     * same message with nothing cut off.
     */
    @NonNull
    public static List<LauncherCtlNotificationEvent> explode(
            @NonNull String key, @NonNull String packageName, @Nullable String appLabel,
            @Nullable String category, @Nullable String channelId, @Nullable String conversation,
            @Nullable String title, @Nullable String text, @Nullable String bigText,
            @Nullable String subText, long postTime,
            @Nullable List<String> lines, @Nullable List<Message> messages) {
        List<LauncherCtlNotificationEvent> rows = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            for (Message message : messages) {
                if (isBlank(message.text)) continue;
                boolean explicit = message.time > 0;
                rows.add(new LauncherCtlNotificationEvent(-1, key, packageName, appLabel, conversation,
                    title, message.sender, message.text, subText, category, channelId,
                    explicit ? message.time : postTime, postTime, 0, 0, explicit));
            }
        } else if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (isBlank(line)) continue;
                rows.add(new LauncherCtlNotificationEvent(-1, key, packageName, appLabel, conversation,
                    title, null, line, subText, category, channelId, postTime, postTime, 0, 0, false));
            }
        } else {
            String body = !isBlank(bigText) ? bigText : text;
            if (!isBlank(body) || !isBlank(title)) {
                rows.add(new LauncherCtlNotificationEvent(-1, key, packageName, appLabel, conversation,
                    title, null, body, subText, category, channelId, postTime, postTime, 0, 0, false));
            }
        }
        return rows;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    /** The same row with the given transform applied to every piece of readable text. */
    @NonNull
    public LauncherCtlNotificationEvent mapText(@NonNull java.util.function.UnaryOperator<String> transform) {
        return new LauncherCtlNotificationEvent(id, key, packageName, appLabel, transform.apply(conversation),
            transform.apply(title), transform.apply(sender), transform.apply(text),
            transform.apply(subText), category, channelId, messageTime, postTime, recordedTime,
            removedTime, explicitTime);
    }

    /**
     * Identity of the content, not of the notification: the key is left out so a summary and its
     * child saying the same thing hash alike. The message time joins in only when the app supplied
     * it, since a plain notification's post time moves on every update.
     */
    @NonNull
    public String contentHash() {
        StringBuilder builder = new StringBuilder();
        for (String part : new String[]{packageName, conversation, title, sender, text, subText}) {
            builder.append(part == null ? "" : part).append('\u0001');
        }
        if (explicitTime) builder.append(messageTime);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                .digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(builder.toString().hashCode());
        }
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject data = new JSONObject();
        if (id >= 0) data.put("id", id);
        data.put("time", messageTime);
        data.put("timeIso", Instant.ofEpochMilli(messageTime).toString());
        data.put("package", packageName);
        data.put("app", appLabel == null ? packageName : appLabel);
        data.put("conversation", orNull(conversation));
        data.put("title", orNull(title));
        data.put("sender", orNull(sender));
        data.put("text", orNull(text));
        data.put("subText", orNull(subText));
        data.put("category", orNull(category));
        data.put("channel", orNull(channelId));
        data.put("key", key);
        data.put("postTime", postTime);
        data.put("removedTime", removedTime > 0 ? (Object) removedTime : JSONObject.NULL);
        return data;
    }

    /**
     * The compact form for people and agents: {@code time · app · title — text}. The sender is put
     * in front of the text when the title does not already name them, and the conversation
     * title leads when a chat has one; line breaks become spaces so it stays one line.
     */
    @NonNull
    public String toLine() {
        String when = LINE_TIME.format(Instant.ofEpochMilli(messageTime).atZone(ZoneId.systemDefault()));
        String app = appLabel == null || appLabel.isEmpty() ? packageName : appLabel;
        String head = !isBlank(conversation) ? conversation : title;
        String body = text;
        if (!isBlank(sender) && !sender.equals(head) && !isBlank(body)) body = sender + ": " + body;
        else if (!isBlank(sender) && isBlank(body)) body = sender;
        if (isBlank(head) && !isBlank(title)) head = title;
        StringBuilder line = new StringBuilder(when).append(" · ").append(app);
        if (!isBlank(head)) line.append(" · ").append(oneLine(head));
        if (!isBlank(body)) line.append(isBlank(head) ? " · " : " — ").append(oneLine(body));
        return line.toString();
    }

    @NonNull
    private static String oneLine(@NonNull String value) {
        return value.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
    }

    @NonNull
    private static Object orNull(@Nullable String value) {
        return value == null ? JSONObject.NULL : value;
    }
}
