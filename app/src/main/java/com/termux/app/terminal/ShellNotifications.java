package com.termux.app.terminal;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.KittyNotification;
import com.termux.terminal.TerminalSession;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

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

    private static final String ACTION_DISMISSED =
        "com.termux.app.action.SHELL_NOTIFICATION_DISMISSED";

    private static final String CHANNEL_ID = "termux_shell_notification_channel";

    private static final String CHANNEL_ID_URGENT = "termux_shell_notification_channel_urgent";

    /** One id is enough: the tag tells the messages apart. */
    private static final int NOTIFICATION_ID = 24112;

    /** Shells that have sent a message, so a dismissal can be reported back to the right one. */
    private static final Map<String, WeakReference<TerminalSession>> sSenders = new HashMap<>();

    /** Numbers the messages a program did not name, so two of them do not replace each other. */
    private static int sUnnamedCount;

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

    /**
     * Show one message.
     *
     * @param terminalVisible whether the launcher is on screen at all
     * @param senderInFront whether the pane that sent it is the one the user is looking at
     * @return the shade key it was posted under, or null when it was not shown
     */
    @Nullable
    public static String post(@NonNull Context context, @NonNull TerminalSession session,
                              @NonNull KittyNotification notification,
                              boolean terminalVisible, boolean senderInFront) {
        if (!shouldShow(notification, terminalVisible, senderInFront)) return null;
        Context app = context.getApplicationContext();
        if (!canPost(app)) return null;

        String tag = tagFor(session, notification.getId());
        boolean urgent = notification.getUrgency() == KittyNotification.URGENCY_CRITICAL;
        ensureChannels(app);

        String title = notification.getTitle().trim();
        String body = notification.getBody().trim();
        if (title.isEmpty()) {
            // A message with only a body still needs a headline, and its own words beat ours.
            title = body;
            body = "";
        }
        if (title.isEmpty()) return null;

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(app, urgent ? CHANNEL_ID_URGENT : CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_service_notification)
                .setColor(0xFF607D8B)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(priorityFor(notification.getUrgency()))
                .setContentIntent(tapIntent(app, session, notification, tag))
                .setDeleteIntent(dismissIntent(app, session, notification, tag));
        if (!body.isEmpty()) {
            builder.setContentText(body);
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }
        if (notification.getUrgency() == KittyNotification.URGENCY_LOW) builder.setSilent(true);
        int timeout = notification.getTimeoutMillis();
        if (timeout > 0) builder.setTimeoutAfter(timeout);
        if (timeout == KittyNotification.TIMEOUT_NEVER) builder.setOngoing(false);

        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null) return null;
        remember(session);
        manager.notify(tag, NOTIFICATION_ID, builder.build());
        return tag;
    }

    /** Take down the message the program named, without telling it anything. */
    public static void close(@NonNull Context context, @NonNull TerminalSession session,
                             @NonNull String name) {
        cancel(context, tagFor(session, name));
    }

    /** Take a message out of the shade by its key. */
    public static void cancel(@NonNull Context context, @Nullable String tag) {
        if (tag == null) return;
        NotificationManager manager =
            context.getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(tag, NOTIFICATION_ID);
    }

    /**
     * The occasion a program asked for: some messages are only worth showing when the user is not
     * already looking at the pane that sent them.
     */
    public static boolean shouldShow(@NonNull KittyNotification notification,
                                     boolean terminalVisible, boolean senderInFront) {
        switch (notification.getOccasion()) {
            case KittyNotification.OCCASION_INVISIBLE:
                return !terminalVisible;
            case KittyNotification.OCCASION_UNFOCUSED:
                return !terminalVisible || !senderInFront;
            default:
                return true;
        }
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

    /**
     * A message's key in the shade. The program's own name for it is part of the key, so sending
     * the same name twice replaces the message instead of stacking another one up.
     */
    @NonNull
    private static synchronized String tagFor(@NonNull TerminalSession session, @NonNull String name) {
        if (name.isEmpty()) return session.mHandle + "/#" + (++sUnnamedCount);
        return session.mHandle + "/" + name;
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

    @NonNull
    private static PendingIntent tapIntent(@NonNull Context context, @NonNull TerminalSession session,
                                           @NonNull KittyNotification notification, @NonNull String tag) {
        // The request code keeps one message's intent from replacing another's; the intent itself
        // stays a plain "open the launcher" one so nothing else has to know about this action.
        Intent intent = TermuxActivity.newInstance(context)
            .putExtra(EXTRA_SESSION_HANDLE, session.mHandle)
            .putExtra(EXTRA_NOTIFICATION_NAME, notification.getId())
            .putExtra(EXTRA_NOTIFICATION_FOCUS, notification.isFocusOnActivate())
            .putExtra(EXTRA_NOTIFICATION_TAG, tag);
        return PendingIntent.getActivity(context, tag.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    @NonNull
    private static PendingIntent dismissIntent(@NonNull Context context, @NonNull TerminalSession session,
                                               @NonNull KittyNotification notification, @NonNull String tag) {
        Intent intent = new Intent(context, DismissReceiver.class)
            .setAction(ACTION_DISMISSED)
            .putExtra(EXTRA_SESSION_HANDLE, session.mHandle)
            .putExtra(EXTRA_NOTIFICATION_NAME, notification.getId());
        return PendingIntent.getBroadcast(context, tag.hashCode() ^ 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    /**
     * Two places for these messages in the phone's notification settings: the ordinary ones and
     * the ones a program marked urgent, so the user can silence one without losing the other.
     */
    private static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.shell_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.shell_notification_channel_description));
            manager.createNotificationChannel(channel);
        }
        if (manager.getNotificationChannel(CHANNEL_ID_URGENT) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_URGENT,
                context.getString(R.string.shell_notification_channel_urgent),
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(
                context.getString(R.string.shell_notification_channel_urgent_description));
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * The user swiped a message away. A program that asked to hear about it is told; one that did
     * not gets nothing. Registered in the manifest, not exported.
     */
    public static final class DismissReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_DISMISSED.equals(intent.getAction())) return;
            String name = intent.getStringExtra(EXTRA_NOTIFICATION_NAME);
            if (name == null || name.isEmpty()) return;
            TerminalSession session = senderFor(intent.getStringExtra(EXTRA_SESSION_HANDLE));
            if (session != null) session.notificationClosed(name);
        }
    }
}
