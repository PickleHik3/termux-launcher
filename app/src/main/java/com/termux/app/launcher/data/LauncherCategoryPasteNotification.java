package com.termux.app.launcher.data;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The return leg of the "copy a prompt into an AI chat" route, as a notification.
 *
 * <p>A dialog cannot survive the round trip: the user leaves for a chat app, and Settings — with
 * its dialog — is gone by the time they come back with an answer on the clipboard. An ongoing
 * notification with an inline reply does survive it, so the answer can be pasted straight from the
 * shade without re-entering Settings (which would re-copy the prompt over their clipboard).
 */
public final class LauncherCategoryPasteNotification {

    public static final String ACTION_REPLY =
        "com.termux.app.launcher.action.CATEGORY_PASTE_REPLY";
    public static final String ACTION_DISMISS =
        "com.termux.app.launcher.action.CATEGORY_PASTE_DISMISS";
    public static final String KEY_REPLY_TEXT = "category_paste_reply";

    private static final String CHANNEL_ID = "termux_launcher_category_paste";
    private static final int NOTIFICATION_ID = 24111;

    private LauncherCategoryPasteNotification() {
    }

    /**
     * Posts the persistent paste-back notification.
     *
     * @return false when notifications are blocked, so the caller knows the dialog is the only
     *     route left on this device.
     */
    public static boolean post(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!canPost(app)) return false;
        ensureChannel(app);

        NotificationCompat.Builder builder = base(app)
            .setContentTitle(app.getString(R.string.settings_app_drawer_category_paste_notification_title))
            .setContentText(app.getString(R.string.settings_app_drawer_category_paste_notification_text))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                app.getString(R.string.settings_app_drawer_category_paste_notification_text)))
            .setOngoing(true)
            .addAction(replyAction(app))
            .addAction(new NotificationCompat.Action.Builder(0,
                app.getString(R.string.settings_app_drawer_category_paste_notification_dismiss),
                broadcast(app, ACTION_DISMISS, 2)).build());
        return notify(app, builder);
    }

    /** @return true when a POST_NOTIFICATIONS-gated notification would actually be shown. */
    public static boolean canPost(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void cancel(@NonNull Context context) {
        NotificationManager manager =
            context.getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    /**
     * Replaces the ongoing notification with a terminal one. Re-posting under the same id rather
     * than cancelling is deliberate: the system keeps the reply field spinning until the
     * notification it belongs to is updated, and the user needs to read the outcome.
     */
    private static void showOutcome(@NonNull Context context, @NonNull String text, boolean retryable) {
        Context app = context.getApplicationContext();
        if (!canPost(app)) return;
        ensureChannel(app);
        NotificationCompat.Builder builder = base(app)
            .setContentTitle(app.getString(R.string.settings_app_drawer_category_paste_notification_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(retryable)
            .setAutoCancel(!retryable);
        if (retryable) {
            // A reply that parsed to nothing is the user's most likely mistake, so the field stays
            // available instead of forcing them back into Settings to re-copy the prompt.
            builder.addAction(replyAction(app));
            builder.addAction(new NotificationCompat.Action.Builder(0,
                app.getString(R.string.settings_app_drawer_category_paste_notification_dismiss),
                broadcast(app, ACTION_DISMISS, 2)).build());
        }
        notify(app, builder);
    }

    @NonNull
    private static NotificationCompat.Builder base(@NonNull Context context) {
        Intent settingsIntent = new Intent(context, SettingsActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    @NonNull
    private static NotificationCompat.Action replyAction(@NonNull Context context) {
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.settings_app_drawer_category_paste_notification_hint))
            .build();
        return new NotificationCompat.Action.Builder(0,
            context.getString(R.string.settings_app_drawer_category_paste_notification_reply),
            broadcast(context, ACTION_REPLY, 1))
            .addRemoteInput(remoteInput)
            // The reply carries the text, so the intent must stay mutable.
            .setAllowGeneratedReplies(false)
            .build();
    }

    @NonNull
    private static PendingIntent broadcast(@NonNull Context context, @NonNull String action, int requestCode) {
        Intent intent = new Intent(context, ActionReceiver.class).setAction(action);
        // RemoteInput results are written into the PendingIntent's intent, so the reply one must be
        // mutable; the dismiss one has nothing to fill in and stays immutable.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (ACTION_REPLY.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                flags |= PendingIntent.FLAG_MUTABLE;
        } else {
            flags |= immutableFlag();
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static boolean notify(@NonNull Context context, @NonNull NotificationCompat.Builder builder) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        manager.notify(NOTIFICATION_ID, builder.build());
        return true;
    }

    private static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.settings_app_drawer_category_paste_notification_channel),
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(
            R.string.settings_app_drawer_category_paste_notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    /** Handles the notification's two buttons. Registered in the manifest, not exported. */
    public static final class ActionReceiver extends BroadcastReceiver {

        private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "category-paste-reply");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            Context app = context.getApplicationContext();
            if (ACTION_DISMISS.equals(intent.getAction())) {
                cancel(app);
                return;
            }
            if (!ACTION_REPLY.equals(intent.getAction())) return;

            String reply = replyText(intent);
            if (reply == null || reply.trim().isEmpty()) {
                showOutcome(app, app.getString(
                    R.string.settings_app_drawer_category_paste_notification_empty), true);
                return;
            }

            // The merge walks the package manager and rewrites a config file: far too slow for a
            // receiver's main-thread window, so the broadcast is held open across the work.
            final PendingResult pendingResult = goAsync();
            EXECUTOR.execute(() -> {
                try {
                    LinkedHashSet<String> known = LauncherCategoryPasteImporter.knownPackages(app);
                    LauncherCategoryPasteImporter.Result result =
                        LauncherCategoryPasteImporter.apply(app, known, reply);
                    if (result.isFailure()) {
                        showOutcome(app, app.getString(
                            R.string.settings_app_drawer_category_sort_failed, result.errorMessage), true);
                    } else if (result.applied == 0) {
                        showOutcome(app, app.getString(
                            R.string.settings_app_drawer_category_paste_notification_empty), true);
                    } else {
                        String text = app.getString(R.string.settings_app_drawer_category_sort_done,
                            result.applied, result.categories);
                        if (result.ignored > 0)
                            text += " · " + app.getString(
                                R.string.settings_app_drawer_category_sort_ignored_lines, result.ignored);
                        showOutcome(app, text, false);
                    }
                } catch (Throwable error) {
                    showOutcome(app, app.getString(R.string.settings_app_drawer_category_sort_failed,
                        error.getMessage() == null ? error.toString() : error.getMessage()), true);
                } finally {
                    pendingResult.finish();
                }
            });
        }

        @Nullable
        private static String replyText(@NonNull Intent intent) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            if (results == null) return null;
            CharSequence text = results.getCharSequence(KEY_REPLY_TEXT);
            return text == null ? null : text.toString();
        }
    }
}
