package com.termux.app.terminal;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.KittyNotification;
import com.termux.terminal.TerminalSession;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Messages a program running in the terminal sends the user, shown in the phone's notification
 * shade.
 *
 * <p>A long build, a download, an agent waiting for an answer: the program says what happened and
 * the user reads it wherever they are, without the launcher having to be on screen. Tapping the
 * message comes back to the pane that sent it.
 *
 * <p>The program can name a message, so a later one replaces it rather than piling up, and can
 * take it down again once it no longer matters.
 */
public final class ShellNotifications {

    /** The shell that sent the message the user tapped. */
    public static final String EXTRA_SESSION_HANDLE =
        "com.termux.app.extra.SHELL_NOTIFICATION_SESSION";

    /** The program's own name for the message, so it can be told the user tapped it. */
    public static final String EXTRA_NOTIFICATION_NAME =
        "com.termux.app.extra.SHELL_NOTIFICATION_NAME";

    /** Whether tapping should also go to the pane, or only tell the program. */
    public static final String EXTRA_NOTIFICATION_FOCUS =
        "com.termux.app.extra.SHELL_NOTIFICATION_FOCUS";

    /** The shade key of the message the user tapped, so it can be taken down. */
    public static final String EXTRA_NOTIFICATION_TAG =
        "com.termux.app.extra.SHELL_NOTIFICATION_TAG";

    /** The 1-based number of the button the user pressed; absent for a tap on the body. */
    public static final String EXTRA_NOTIFICATION_BUTTON =
        "com.termux.app.extra.SHELL_NOTIFICATION_BUTTON";

    /** Android shows at most three actions on a notification; further buttons are dropped. */
    private static final int MAX_ACTIONS = 3;

    private static final String ACTION_DISMISSED =
        "com.termux.app.action.SHELL_NOTIFICATION_DISMISSED";

    /** The window the notification belongs to, so a dismissal or reply finds its record. */
    public static final String EXTRA_WINDOW_KEY = "com.termux.app.extra.TERMINAL_NOTIFICATION_WINDOW";

    /** The foreground process group that asked, captured when the notification was posted. */
    public static final String EXTRA_REPLY_PGID = "com.termux.app.extra.TERMINAL_NOTIFICATION_REPLY_PGID";

    /** The name of the program that asked, captured when the notification was posted. */
    public static final String EXTRA_REPLY_PROGRAM =
        "com.termux.app.extra.TERMINAL_NOTIFICATION_REPLY_PROGRAM";

    /** A reply that was not typed because its program is no longer waiting, for the launcher to offer. */
    public static final String EXTRA_UNSENT_REPLY = "com.termux.app.extra.TERMINAL_NOTIFICATION_UNSENT_REPLY";

    /** The program that no longer waits, for the "Not sent" notice. */
    public static final String EXTRA_UNSENT_PROGRAM =
        "com.termux.app.extra.TERMINAL_NOTIFICATION_UNSENT_PROGRAM";

    /** The RemoteInput result key of the reply box. */
    static final String REPLY_KEY = "terminal_reply";

    // Channel ids are literal and shared by every edition on purpose: never derive them from the package.
    private static final String CHANNEL_ID = "termux_shell_notification_channel";

    private static final String CHANNEL_ID_URGENT = "termux_shell_notification_channel_urgent";

    static final String CHANNEL_NEEDS_INPUT = "termux_terminal_needs_input";
    static final String CHANNEL_ERRORS = "termux_terminal_errors";
    static final String CHANNEL_FINISHED = "termux_terminal_finished";
    static final String CHANNEL_BELL = "termux_terminal_bell";
    static final String CHANNEL_GROUP = "termux_terminal_notification_group";

    /** Groups the window notifications under one summary. */
    private static final String NOTIFICATION_GROUP = "termux_terminal";

    private static final String TAG_PREFIX = "terminal/";
    private static final String SUMMARY_TAG = TAG_PREFIX + "summary";
    private static final String LOG_TAG = "ShellNotifications";
    private static final int ICON_COLOR = 0xFF607D8B;

    private static final ExecutorService REPLY_EXECUTOR = Executors.newSingleThreadExecutor();

    private static boolean sChannelsEnsured;
    private static boolean sLeftoversCleared;

    /** One id is enough: the tag tells the messages apart. */
    private static final int NOTIFICATION_ID = 24112;

    /** Shells that have sent a message, so a dismissal can be reported back to the right one. */
    private static final Map<String, WeakReference<TerminalSession>> sSenders = new HashMap<>();

    private ShellNotifications() {
    }

    /** Whether a message would actually reach the shade. */
    public static boolean canPost(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Take a message out of the shade by its key. */
    public static void cancel(@NonNull Context context, @Nullable String tag) {
        if (tag == null) return;
        NotificationManager manager =
            context.getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(tag, NOTIFICATION_ID);
    }

    private static int priorityFor(int urgency) {
        switch (urgency) {
            case KittyNotification.URGENCY_LOW:
                return NotificationCompat.PRIORITY_LOW;
            case KittyNotification.URGENCY_CRITICAL:
                return NotificationCompat.PRIORITY_HIGH;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    private static synchronized void remember(@NonNull TerminalSession session) {
        sSenders.put(session.mHandle, new WeakReference<>(session));
        // Shells that have gone are of no use to a later dismissal.
        sSenders.values().removeIf(reference -> reference.get() == null);
    }

    @Nullable
    private static synchronized TerminalSession senderFor(@Nullable String handle) {
        if (handle == null) return null;
        WeakReference<TerminalSession> reference = sSenders.get(handle);
        return reference == null ? null : reference.get();
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    // --- The Poster: one notification per terminal window ---

    /** The poster the app hands to {@link TerminalNotifier}. */
    @NonNull
    public static TerminalNotifier.Poster poster(@NonNull Context context) {
        return new AndroidPoster(context.getApplicationContext());
    }

    /** The channel a kind posts on. */
    @NonNull
    static String channelIdFor(@NonNull TerminalNoticeKind kind) {
        switch (kind) {
            case NEEDS_INPUT: return CHANNEL_NEEDS_INPUT;
            case URGENT_MESSAGE: return CHANNEL_ID_URGENT;
            case ERROR: return CHANNEL_ERRORS;
            case FINISHED: return CHANNEL_FINISHED;
            case BELL: return CHANNEL_BELL;
            default: return CHANNEL_ID;
        }
    }

    /** The importance a kind's channel is created with (an existing channel keeps the user's choice). */
    static int importanceFor(@NonNull TerminalNoticeKind kind) {
        return kind == TerminalNoticeKind.NEEDS_INPUT || kind == TerminalNoticeKind.URGENT_MESSAGE
            ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT;
    }

    /** Where a notification's title comes from. */
    enum TitleSource {
        /** "<name> needs input" and its siblings. */
        STATE_WITH_NAME,
        /** The program's own title. */
        MESSAGE_TITLE,
        /** The program or window name. */
        NAME,
        /** The kind's label: nothing better is known. */
        LABEL
    }

    @NonNull
    static TitleSource titleSourceFor(@NonNull TerminalNoticeKind kind, @NonNull String messageTitle,
                                      @NonNull String name) {
        switch (kind) {
            case NEEDS_INPUT:
            case ERROR:
            case FINISHED:
            case BELL:
                return name.isEmpty() ? TitleSource.LABEL : TitleSource.STATE_WITH_NAME;
            default:
                if (!messageTitle.isEmpty()) return TitleSource.MESSAGE_TITLE;
                return name.isEmpty() ? TitleSource.LABEL : TitleSource.NAME;
        }
    }

    private static int labelRes(@NonNull TerminalNoticeKind kind) {
        switch (kind) {
            case NEEDS_INPUT: return R.string.terminal_notification_label_needs_input;
            case ERROR: return R.string.terminal_notification_label_error;
            case FINISHED: return R.string.terminal_notification_label_finished;
            case URGENT_MESSAGE: return R.string.terminal_notification_label_urgent_message;
            case BELL: return R.string.terminal_notification_label_bell;
            default: return R.string.terminal_notification_label_message;
        }
    }

    private static int channelNameRes(@NonNull TerminalNoticeKind kind) {
        switch (kind) {
            case NEEDS_INPUT: return R.string.terminal_notification_channel_needs_input;
            case ERROR: return R.string.terminal_notification_channel_errors;
            case FINISHED: return R.string.terminal_notification_channel_finished;
            case URGENT_MESSAGE: return R.string.shell_notification_channel_urgent;
            case BELL: return R.string.terminal_notification_channel_bell;
            default: return R.string.shell_notification_channel;
        }
    }

    private static int channelDescriptionRes(@NonNull TerminalNoticeKind kind) {
        switch (kind) {
            case NEEDS_INPUT: return R.string.terminal_notification_channel_needs_input_description;
            case ERROR: return R.string.terminal_notification_channel_errors_description;
            case FINISHED: return R.string.terminal_notification_channel_finished_description;
            case URGENT_MESSAGE: return R.string.shell_notification_channel_urgent_description;
            case BELL: return R.string.terminal_notification_channel_bell_description;
            default: return R.string.shell_notification_channel_description;
        }
    }

    @NonNull
    private static String titleFor(@NonNull Context context, @NonNull TerminalNoticeContent content) {
        switch (titleSourceFor(content.kind, content.messageTitle, content.name)) {
            case STATE_WITH_NAME:
                int res;
                switch (content.kind) {
                    case NEEDS_INPUT: res = R.string.notice_program_needs_input; break;
                    case ERROR: res = R.string.notice_program_error; break;
                    case FINISHED: res = R.string.notice_program_done; break;
                    default: res = R.string.notice_shell_wants_attention; break;
                }
                return context.getString(res, content.name);
            case MESSAGE_TITLE:
                return content.messageTitle;
            case NAME:
                return content.name;
            default:
                return context.getString(labelRes(content.kind));
        }
    }

    /**
     * Six channels in one "Terminal" group. Creating an existing channel only refreshes its name
     * and description, which is how the two older ones are renamed and grouped; the user's own
     * importance choice is kept.
     */
    private static synchronized void ensureChannels(@NonNull Context context) {
        if (sChannelsEnsured) return;
        sChannelsEnsured = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannelGroup(new NotificationChannelGroup(CHANNEL_GROUP,
            context.getString(R.string.terminal_notification_group)));
        for (TerminalNoticeKind kind : TerminalNoticeKind.values()) {
            NotificationChannel channel = new NotificationChannel(channelIdFor(kind),
                context.getString(channelNameRes(kind)), importanceFor(kind));
            channel.setDescription(context.getString(channelDescriptionRes(kind)));
            channel.setGroup(CHANNEL_GROUP);
            manager.createNotificationChannel(channel);
        }
    }

    /** Notifications an earlier process left behind belong to shells that are gone. */
    private static synchronized void clearLeftovers(@NonNull Context context) {
        if (sLeftoversCleared) return;
        sLeftoversCleared = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                String tag = active.getTag();
                if (tag != null && tag.startsWith(TAG_PREFIX)) manager.cancel(tag, active.getId());
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Could not clear leftover notifications", e);
        }
    }

    /** Takes a window's notification down, and the summary with the last one. */
    static void takeDown(@NonNull Context context, @NonNull String windowKey, int remaining) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.cancel(TAG_PREFIX + windowKey, NOTIFICATION_ID);
        if (remaining == 0) manager.cancel(SUMMARY_TAG, NOTIFICATION_ID);
    }

    private static final class AndroidPoster implements TerminalNotifier.Poster {
        private final Context mApp;

        AndroidPoster(Context app) {
            mApp = app;
        }

        @Override
        public boolean canPost() {
            return ShellNotifications.canPost(mApp);
        }

        @Override
        public void post(@NonNull TerminalNoticeContent content) {
            NotificationManager manager = mApp.getSystemService(NotificationManager.class);
            if (manager == null) return;
            clearLeftovers(mApp);
            ensureChannels(mApp);
            String tag = TAG_PREFIX + content.windowKey;
            String channel = channelIdFor(content.kind);
            KittyNotification kitty = content.kitty;
            String label = mApp.getString(labelRes(content.kind));
            boolean high = content.kind == TerminalNoticeKind.NEEDS_INPUT
                || content.kind == TerminalNoticeKind.URGENT_MESSAGE;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mApp, channel)
                .setSmallIcon(R.drawable.ic_service_notification)
                .setColor(ICON_COLOR)
                .setContentTitle(titleFor(mApp, content))
                .setAutoCancel(true)
                .setOnlyAlertOnce(!content.alert)
                .setGroup(NOTIFICATION_GROUP)
                .setPriority(high ? NotificationCompat.PRIORITY_HIGH
                    : kitty != null ? priorityFor(kitty.getUrgency()) : NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(new NotificationCompat.Builder(mApp, channel)
                    .setSmallIcon(R.drawable.ic_service_notification)
                    .setColor(ICON_COLOR)
                    .setContentTitle(label)
                    .build())
                .setContentIntent(contentIntent(mApp, content, tag))
                .setDeleteIntent(deleteIntent(mApp, content, tag));
            if (!content.text.isEmpty()) {
                builder.setContentText(content.text);
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(content.text));
            }
            if (!content.subText.isEmpty()) builder.setSubText(content.subText);
            if (kitty != null) {
                List<String> buttons = kitty.getButtons();
                int shown = 0;
                for (int i = 0; i < buttons.size() && shown < MAX_ACTIONS; i++) {
                    String text = buttons.get(i).trim();
                    if (text.isEmpty()) continue;
                    builder.addAction(0, text, buttonIntent(mApp, content, tag, i + 1));
                    shown++;
                }
                if (kitty.getUrgency() == KittyNotification.URGENCY_LOW) builder.setSilent(true);
                int timeout = kitty.getTimeoutMillis();
                if (timeout > 0) builder.setTimeoutAfter(timeout);
            }
            if (content.replyProgramName != null) builder.addAction(replyAction(mApp, content, tag));
            if (content.pane instanceof TerminalSession) remember((TerminalSession) content.pane);

            manager.notify(tag, NOTIFICATION_ID, builder.build());
            manager.notify(SUMMARY_TAG, NOTIFICATION_ID, new NotificationCompat.Builder(mApp, channel)
                .setSmallIcon(R.drawable.ic_service_notification)
                .setColor(ICON_COLOR)
                .setContentTitle(mApp.getString(R.string.terminal_notification_group))
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build());
        }

        @Override
        public void cancel(@NonNull String windowKey, int remaining) {
            takeDown(mApp, windowKey, remaining);
        }
    }

    @NonNull
    private static Intent openIntent(@NonNull Context context, @NonNull TerminalNoticeContent content,
                                     @NonNull String tag) {
        KittyNotification kitty = content.kitty;
        return TermuxActivity.newInstance(context)
            .putExtra(EXTRA_SESSION_HANDLE, content.paneHandle)
            .putExtra(EXTRA_NOTIFICATION_NAME, kitty == null ? "" : kitty.getId())
            .putExtra(EXTRA_NOTIFICATION_FOCUS, kitty == null || kitty.isFocusOnActivate())
            .putExtra(EXTRA_NOTIFICATION_TAG, tag);
    }

    @NonNull
    private static PendingIntent contentIntent(@NonNull Context context,
                                               @NonNull TerminalNoticeContent content, @NonNull String tag) {
        return PendingIntent.getActivity(context, tag.hashCode(), openIntent(context, content, tag),
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    @NonNull
    private static PendingIntent buttonIntent(@NonNull Context context,
                                              @NonNull TerminalNoticeContent content, @NonNull String tag,
                                              int number) {
        Intent intent = openIntent(context, content, tag).putExtra(EXTRA_NOTIFICATION_BUTTON, number);
        return PendingIntent.getActivity(context, tag.hashCode() ^ (0x100 + number), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    @NonNull
    private static PendingIntent deleteIntent(@NonNull Context context,
                                              @NonNull TerminalNoticeContent content, @NonNull String tag) {
        KittyNotification kitty = content.kitty;
        Intent intent = new Intent(context, DismissReceiver.class)
            .setAction(ACTION_DISMISSED)
            .putExtra(EXTRA_SESSION_HANDLE, content.paneHandle)
            .putExtra(EXTRA_WINDOW_KEY, content.windowKey)
            .putExtra(EXTRA_NOTIFICATION_NAME, kitty == null ? "" : kitty.getId());
        return PendingIntent.getBroadcast(context, tag.hashCode() ^ 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    /**
     * The reply box. It goes to {@link ReplyReceiver} with what the program that asked looked like
     * when the notification was posted, so a reply is typed only while that program still waits.
     * A reply box needs a mutable intent from Android 12 on.
     */
    @NonNull
    private static NotificationCompat.Action replyAction(@NonNull Context context,
                                                         @NonNull TerminalNoticeContent content,
                                                         @NonNull String tag) {
        Intent intent = new Intent(context, ReplyReceiver.class)
            .putExtra(EXTRA_SESSION_HANDLE, content.paneHandle)
            .putExtra(EXTRA_WINDOW_KEY, content.windowKey)
            .putExtra(EXTRA_NOTIFICATION_TAG, tag)
            .putExtra(EXTRA_REPLY_PGID, content.replyPgid)
            .putExtra(EXTRA_REPLY_PROGRAM, content.replyProgramName);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent pending = PendingIntent.getBroadcast(context, tag.hashCode() ^ 2, intent, flags);
        RemoteInput input = new RemoteInput.Builder(REPLY_KEY)
            .setLabel(context.getString(R.string.terminal_notification_reply_label,
                content.replyProgramName))
            .build();
        return new NotificationCompat.Action.Builder(0,
            context.getString(R.string.terminal_notification_reply), pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setAuthenticationRequired(true)
            .setShowsUserInterface(false)
            .build();
    }

    /**
     * The user swiped a message away. A program that asked to hear about it is told; one that did
     * not gets nothing. Registered in the manifest, not exported.
     */
    public static final class DismissReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_DISMISSED.equals(intent.getAction())) return;
            // The record goes with the notification; the bell mark stays until the pane is seen.
            String windowKey = intent.getStringExtra(EXTRA_WINDOW_KEY);
            if (windowKey != null) TerminalNoticeRecords.shared().remove(windowKey);
            String name = intent.getStringExtra(EXTRA_NOTIFICATION_NAME);
            if (name == null || name.isEmpty()) return;
            TerminalSession session = senderFor(intent.getStringExtra(EXTRA_SESSION_HANDLE));
            if (session != null) session.notificationClosed(name);
        }
    }

    /**
     * A reply typed into a terminal notification. It is written as typed text and Return, only
     * when the program that asked is still in the pane's foreground; otherwise the launcher opens
     * on the pane with the text so nothing lands in the wrong program. Registered in the
     * manifest, not exported.
     */
    public static final class ReplyReceiver extends BroadcastReceiver {

        private static final Handler MAIN = new Handler(Looper.getMainLooper());

        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null) return;
            android.os.Bundle results = RemoteInput.getResultsFromIntent(intent);
            CharSequence typed = results == null ? null : results.getCharSequence(REPLY_KEY);
            String windowKey = intent.getStringExtra(EXTRA_WINDOW_KEY);
            if (typed == null || windowKey == null) return;
            Context app = context.getApplicationContext();
            String handle = intent.getStringExtra(EXTRA_SESSION_HANDLE);
            String tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG);
            int capturedPgid = intent.getIntExtra(EXTRA_REPLY_PGID, 0);
            String capturedName = intent.getStringExtra(EXTRA_REPLY_PROGRAM);
            String text = typed.toString();
            String cleaned = TerminalReplyGuard.replyText(text);
            TerminalSession session = senderFor(handle);
            PendingResult result = goAsync();
            REPLY_EXECUTOR.execute(() -> {
                // The process name is read from /proc, so it is looked up off the main thread.
                int nowPgid = session == null || !session.isRunning() ? -1 : session.getForegroundProcessGroup();
                String nowName = nowPgid > 0 ? TerminalSession.programNameOf(nowPgid) : null;
                MAIN.post(() -> {
                    try {
                        if (session != null && !cleaned.isEmpty()
                            && TerminalReplyGuard.shouldSend(capturedPgid, capturedName, nowPgid, nowName)) {
                            byte[] bytes = cleaned.getBytes(StandardCharsets.UTF_8);
                            session.write(bytes, 0, bytes.length);
                        } else {
                            openWithUnsent(app, handle, tag, text, capturedName);
                        }
                        TerminalNoticeRecords.shared().remove(windowKey);
                        // A replied notification must be updated or taken down, or it keeps spinning.
                        takeDown(app, windowKey, TerminalNoticeRecords.shared().size());
                    } catch (RuntimeException e) {
                        Log.w(LOG_TAG, "Could not handle a notification reply", e);
                    } finally {
                        result.finish();
                    }
                });
            });
        }

        private static void openWithUnsent(Context app, @Nullable String handle, @Nullable String tag,
                                           String text, @Nullable String program) {
            try {
                app.startActivity(TermuxActivity.newInstance(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_SESSION_HANDLE, handle)
                    .putExtra(EXTRA_NOTIFICATION_TAG, tag)
                    .putExtra(EXTRA_UNSENT_REPLY, text)
                    .putExtra(EXTRA_UNSENT_PROGRAM, program));
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Could not open the launcher for an unsent reply", e);
            }
        }
    }
}
