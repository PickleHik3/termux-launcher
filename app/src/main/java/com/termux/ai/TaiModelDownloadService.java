package com.termux.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The foreground anchor for model downloads. It does no transfer work itself: {@link TaiDownloadEngine}
 * owns the queue and the workers, and this service exists so the process is a foreground one while
 * they run, and so there is a notification to look at. It attaches to the engine when it starts,
 * shows one grouped notification (a summary and one child per item, each with Pause/Resume and
 * Cancel), and stops itself when the engine reports nothing left to run.
 *
 * <p>START_STICKY: if the system kills the process mid-transfer, it restarts this service with a
 * null intent; {@link #onStartCommand} then reconciles (orphaned records become paused/app_closed)
 * and the engine resumes them on an unmetered network. See the engine's class comment for why
 * this, rather than a job, is the resumption path.
 */
public final class TaiModelDownloadService extends Service implements TaiDownloadHub.Listener, TaiDownloadEngine.IdleListener {
    /** Run the queue (the engine has something queued, or the system restarted us). */
    public static final String ACTION_SYNC = "com.termux.ai.action.DOWNLOAD_SYNC";
    public static final String ACTION_PAUSE = "com.termux.ai.action.DOWNLOAD_PAUSE";
    public static final String ACTION_RESUME = "com.termux.ai.action.DOWNLOAD_RESUME";
    public static final String ACTION_CANCEL = "com.termux.ai.action.DOWNLOAD_CANCEL";
    public static final String EXTRA_MODEL_ID = "model_id";

    /**
     * The settings page the notification opens: the Model centre, where every download has its
     * row with pause, resume and cancel. Named rather than referenced so this service (which the
     * API and the CLI also start) does not load the settings UI classes.
     */
    public static final String MODEL_CENTRE_FRAGMENT = "com.termux.app.fragments.settings.termux.TaiModelCentreFragment";

    private static final String CHANNEL_ID = "termux_ai_model_downloads";
    private static final String GROUP_KEY = "termux_ai_model_downloads";
    private static final int SUMMARY_ID = 24100;
    /** Every child uses this id with the model id as its tag, so ids never collide or drift. */
    private static final int CHILD_ID = 24101;
    /** A child's text is refreshed at most this often unless its status changed. */
    private static final long CHILD_REFRESH_MS = 1_000L;

    private TaiDownloadEngine engine;
    private TaiDownloadHub hub;
    private int lastStartId;
    private final Map<String, Long> childUpdatedMs = new HashMap<>();
    private final Map<String, String> childStatus = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        startForeground(SUMMARY_ID, summaryNotification(getString(R.string.termux_ai_download_notification_preparing), true));
        engine = TaiDownloadEngine.getInstance(this);
        hub = engine.hub();
        hub.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        // Every start re-asserts the foreground state: the first one must within a few seconds
        // of startForegroundService, and a later one is free.
        startForeground(SUMMARY_ID, summaryNotification(getString(R.string.termux_ai_download_notification_preparing), true));
        String action = intent == null ? ACTION_SYNC : intent.getAction();
        String modelId = intent == null ? null : intent.getStringExtra(EXTRA_MODEL_ID);
        if (intent == null) {
            // A sticky restart after the process died: whatever was in flight has no worker now.
            engine.reconcile();
        } else if (ACTION_PAUSE.equals(action) && modelId != null) {
            engine.pause(modelId, TaiModelStore.PAUSED_USER);
        } else if (ACTION_RESUME.equals(action) && modelId != null) {
            engine.resume(modelId);
        } else if (ACTION_CANCEL.equals(action) && modelId != null) {
            engine.cancel(modelId);
        }
        engine.attachService(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (hub != null) hub.removeListener(this);
        if (engine != null) engine.detachService();
        super.onDestroy();
    }

    @Override
    public void onDownloadsIdle() {
        // Post the final state of every child first, then drop the foreground summary: a finished
        // or paused item keeps its (non-ongoing) child so the person can see it ended or tap Resume.
        List<TaiDownloadHub.Snapshot> downloads = hub.snapshot();
        renderChildren(downloads, true);
        boolean anyChild = false;
        for (TaiDownloadHub.Snapshot item : downloads) {
            if (showsChild(item)) anyChild = true;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (anyChild) manager.notify(SUMMARY_ID, summaryNotification(summaryText(downloads), false));
            else manager.cancel(SUMMARY_ID);
        }
        stopSelfResult(lastStartId);
    }

    @Override
    public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
        if (!engine.hasRunningWorkers()) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.notify(SUMMARY_ID, summaryNotification(summaryText(downloads), true));
        renderChildren(downloads, false);
    }

    private void renderChildren(@NonNull List<TaiDownloadHub.Snapshot> downloads, boolean force) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        long now = SystemClock.elapsedRealtime();
        Set<String> seen = new HashSet<>();
        for (TaiDownloadHub.Snapshot item : downloads) {
            seen.add(item.modelId);
            if (!showsChild(item)) {
                if (childStatus.remove(item.modelId) != null) manager.cancel(item.modelId, CHILD_ID);
                continue;
            }
            boolean statusChanged = !item.status.equals(childStatus.get(item.modelId));
            Long updated = childUpdatedMs.get(item.modelId);
            if (!force && !statusChanged && updated != null && now - updated < CHILD_REFRESH_MS) continue;
            childUpdatedMs.put(item.modelId, now);
            childStatus.put(item.modelId, item.status);
            manager.notify(item.modelId, CHILD_ID, childNotification(item));
        }
        // Records dropped from the store (the model was deleted) lose their child too.
        for (String modelId : new HashSet<>(childStatus.keySet())) {
            if (!seen.contains(modelId)) {
                childStatus.remove(modelId);
                manager.cancel(modelId, CHILD_ID);
            }
        }
    }

    /** Cancelled and unknown-state records have nothing to say; everything else gets a child. */
    private static boolean showsChild(@NonNull TaiDownloadHub.Snapshot item) {
        return item.isLive() || item.isPaused()
            || TaiModelStore.STATE_FAILED.equals(item.status)
            || TaiModelStore.STATE_INSTALLED.equals(item.status);
    }

    @NonNull
    private String summaryText(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
        int downloading = 0, waiting = 0, paused = 0;
        for (TaiDownloadHub.Snapshot item : downloads) {
            if (TaiModelStore.STATE_DOWNLOADING.equals(item.status) || TaiModelStore.STATE_VERIFYING.equals(item.status)) downloading++;
            else if (TaiModelStore.STATE_QUEUED.equals(item.status)) waiting++;
            else if (item.isPaused()) paused++;
        }
        StringBuilder text = new StringBuilder();
        if (downloading > 0) text.append(getResources().getQuantityString(R.plurals.termux_ai_download_notification_downloading, downloading, downloading));
        if (waiting > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append(getResources().getQuantityString(R.plurals.termux_ai_download_notification_waiting, waiting, waiting));
        }
        if (paused > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append(getResources().getQuantityString(R.plurals.termux_ai_download_notification_paused, paused, paused));
        }
        return text.length() == 0 ? getString(R.string.termux_ai_download_notification_done) : text.toString();
    }

    private Notification summaryNotification(@NonNull String text, boolean ongoing) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(getString(R.string.termux_ai_download_notification_title))
            .setContentText(text)
            .setContentIntent(openModelCentre())
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private Notification childNotification(@NonNull TaiDownloadHub.Snapshot item) {
        boolean ongoing = item.isLive();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(item.displayName)
            .setContentText(childText(item))
            .setContentIntent(openModelCentre())
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        if (TaiModelStore.STATE_DOWNLOADING.equals(item.status) && item.totalBytes > 0L) {
            builder.setProgress(10000, (int) (item.bytesRead * 10000L / item.totalBytes), false);
        } else if (TaiModelStore.STATE_VERIFYING.equals(item.status) && item.totalBytes > 0L && item.verifiedBytes > 0L) {
            builder.setProgress(10000, (int) (Math.min(item.verifiedBytes, item.totalBytes) * 10000L / item.totalBytes), false);
        } else if (ongoing) {
            builder.setProgress(0, 0, true);
        }
        if (item.isLive()) {
            builder.addAction(0, getString(R.string.termux_ai_download_notification_pause), serviceAction(ACTION_PAUSE, item.modelId));
            builder.addAction(0, getString(R.string.termux_ai_download_notification_cancel), serviceAction(ACTION_CANCEL, item.modelId));
        } else if (item.isPaused()) {
            builder.addAction(0, getString(R.string.termux_ai_download_notification_resume), serviceAction(ACTION_RESUME, item.modelId));
            builder.addAction(0, getString(R.string.termux_ai_download_notification_cancel), serviceAction(ACTION_CANCEL, item.modelId));
        } else if (TaiModelStore.STATE_FAILED.equals(item.status)) {
            builder.addAction(0, getString(R.string.termux_ai_download_notification_retry), serviceAction(ACTION_RESUME, item.modelId));
            builder.addAction(0, getString(R.string.termux_ai_download_notification_cancel), serviceAction(ACTION_CANCEL, item.modelId));
        }
        return builder.build();
    }

    @NonNull
    private String childText(@NonNull TaiDownloadHub.Snapshot item) {
        switch (item.status) {
            case TaiModelStore.STATE_QUEUED:
                return item.queuePosition > 0
                    ? getString(R.string.termux_ai_download_notification_queued_at, item.queuePosition)
                    : getString(R.string.termux_ai_download_notification_queued);
            case TaiModelStore.STATE_DOWNLOADING: {
                StringBuilder text = new StringBuilder();
                if (item.totalBytes > 0L) {
                    text.append(formatPercent(item.bytesRead, item.totalBytes)).append(" · ")
                        .append(formatBytes(item.bytesRead)).append(" / ").append(formatBytes(item.totalBytes));
                } else {
                    text.append(formatBytes(item.bytesRead));
                }
                if (item.bytesPerSecond >= 1024.0) text.append(" · ").append(formatBytes((long) item.bytesPerSecond)).append("/s");
                if (item.etaSeconds > 0L) text.append(" · ").append(formatEta(item.etaSeconds));
                return text.toString();
            }
            case TaiModelStore.STATE_VERIFYING:
                return getString(R.string.termux_ai_download_notification_verifying);
            case TaiModelStore.STATE_PAUSED:
                return pausedText(item);
            case TaiModelStore.STATE_INSTALLED:
                return getString(R.string.termux_ai_download_notification_installed);
            case TaiModelStore.STATE_FAILED:
                return getString(R.string.termux_ai_download_notification_failed, formatError(item.error));
            default:
                return item.status;
        }
    }

    @NonNull
    private String pausedText(@NonNull TaiDownloadHub.Snapshot item) {
        String progress = item.totalBytes > 0L
            ? formatBytes(item.bytesRead) + " / " + formatBytes(item.totalBytes) + " · "
            : "";
        switch (item.pausedReason) {
            case TaiModelStore.PAUSED_APP_CLOSED:
                return progress + getString(R.string.termux_ai_download_notification_paused_app_closed);
            case TaiModelStore.PAUSED_NETWORK:
                return progress + getString(R.string.termux_ai_download_notification_paused_network);
            case TaiModelStore.PAUSED_NO_SPACE:
                return getString(R.string.termux_ai_download_notification_paused_no_space,
                    formatBytes(item.requiredBytes), formatBytes(item.freeBytes));
            default:
                return progress + getString(R.string.termux_ai_download_notification_paused_user);
        }
    }

    @NonNull
    private String formatError(@NonNull String error) {
        switch (error) {
            case "insecure_url":
                return "Insecure URL: HTTPS required";
            case "":
                return "Download failed";
            default:
                return error;
        }
    }

    private PendingIntent openModelCentre() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, MODEL_CENTRE_FRAGMENT);
        intent.putExtra(SettingsActivity.EXTRA_INITIAL_TITLE_RES, R.string.tai_model_centre_title);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** A Pause/Resume/Cancel action for one model. The data URI makes each (action, model) pair a
     *  distinct Intent, so one PendingIntent never overwrites another's extras. */
    private PendingIntent serviceAction(@NonNull String action, @NonNull String modelId) {
        Intent intent = new Intent(this, TaiModelDownloadService.class);
        intent.setAction(action);
        intent.setData(Uri.parse("tai-download://" + Uri.encode(modelId) + "/" + action));
        intent.putExtra(EXTRA_MODEL_ID, modelId);
        return PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void ensureChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "TAI model downloads", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progress for Termux AI model downloads");
        manager.createNotificationChannel(channel);
    }

    private static String formatPercent(long value, long total) {
        if (total <= 0) return "";
        return String.format(Locale.US, "%.0f%%", (double) value * 100.0 / (double) total);
    }

    private static String formatEta(long seconds) {
        if (seconds < 60L) return "~" + seconds + " s";
        if (seconds < 3600L) return "~" + (seconds + 30L) / 60L + " min";
        return String.format(Locale.US, "~%d h %d min", seconds / 3600L, (seconds % 3600L) / 60L);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
