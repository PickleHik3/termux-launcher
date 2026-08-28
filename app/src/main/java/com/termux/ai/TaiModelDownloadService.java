package com.termux.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaiModelDownloadService extends Service {
    public static final String ACTION_DOWNLOAD = "com.termux.ai.action.DOWNLOAD_MODEL";
    public static final String EXTRA_TRANSFER_ID = "transfer_id";
    public static final String EXTRA_MODEL_ID = "model_id";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_OUTPUT_PATH = "output_path";
    public static final String EXTRA_DISPLAY_NAME = "display_name";
    public static final String EXTRA_LICENSE = "license";
    public static final String EXTRA_CAPABILITIES = "capabilities";
    public static final String EXTRA_AUTH_TOKEN = "auth_token";
    public static final String EXTRA_BACKEND = "backend";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_ARCHITECTURE = "architecture";
    public static final String EXTRA_QUANTIZATION = "quantization";
    public static final String EXTRA_CONTEXT_WINDOW = "context_window";
    public static final String EXTRA_RECOMMENDED_RAM_GB = "recommended_ram_gb";
    public static final String EXTRA_SHA256 = "sha256";
    public static final String EXTRA_EXPECTED_SIZE_BYTES = "expected_size_bytes";
    public static final String EXTRA_RUNTIME_PROFILE = "runtime_profile";

    private static final String CHANNEL_ID = "termux_ai_model_downloads";
    private static final int NOTIFICATION_ID = 24100;
    private static final Set<String> CANCELLED_MODELS = ConcurrentHashMap.newKeySet();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile int activeDownloads;
    private long lastNotificationUpdateMs;
    private String lastNotificationStatus = "";

    public static void requestCancel(@NonNull String modelId) { CANCELLED_MODELS.add(modelId); }
    public static boolean isCancelled(@NonNull String modelId) { return CANCELLED_MODELS.contains(modelId); }
    public static void clearCancellation(@NonNull String modelId) { CANCELLED_MODELS.remove(modelId); }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        if (activeDownloads <= 0) {
            startForeground(NOTIFICATION_ID, buildNotification("TAI model download", "Preparing download", 0L, 0L, true));
        }
        if (intent == null || !ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        activeDownloads++;
        executor.execute(() -> {
            runIntentDownload(intent);
            if (--activeDownloads <= 0) {
                stopForeground(true);
                stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runIntentDownload(@NonNull Intent intent) {
        String transferId = intent.getStringExtra(EXTRA_TRANSFER_ID);
        String modelId = intent.getStringExtra(EXTRA_MODEL_ID);
        String url = intent.getStringExtra(EXTRA_URL);
        String outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH);
        if (transferId == null || modelId == null || url == null || outputPath == null) return;
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        String[] rawCapabilities = intent.getStringArrayExtra(EXTRA_CAPABILITIES);
        if (rawCapabilities != null) {
            for (String capability : rawCapabilities) {
                if (capability != null && !capability.trim().isEmpty()) capabilities.add(capability);
            }
        }

        TaiModelStore store = new TaiModelStore(this);
        TaiModelDownloader downloader = new TaiModelDownloader(this, store);
        try {
            TaiModelProfile runtimeProfile = null;
            String rawProfile = intent.getStringExtra(EXTRA_RUNTIME_PROFILE);
            if (rawProfile != null && !rawProfile.trim().isEmpty()) {
                try {
                    runtimeProfile = TaiModelProfile.fromJson(new JSONObject(rawProfile));
                } catch (Exception ignored) {
                }
            }
            downloader.runDownload(
                transferId,
                modelId,
                url,
                new File(outputPath),
                valueOrEmpty(intent.getStringExtra(EXTRA_DISPLAY_NAME)),
                valueOrEmpty(intent.getStringExtra(EXTRA_LICENSE)),
                capabilities,
                valueOrEmpty(intent.getStringExtra(EXTRA_BACKEND)),
                valueOrEmpty(intent.getStringExtra(EXTRA_FORMAT)),
                valueOrEmpty(intent.getStringExtra(EXTRA_ARCHITECTURE)),
                valueOrEmpty(intent.getStringExtra(EXTRA_QUANTIZATION)),
                intent.getIntExtra(EXTRA_CONTEXT_WINDOW, 4096),
                intent.getIntExtra(EXTRA_RECOMMENDED_RAM_GB, 0),
                valueOrEmpty(intent.getStringExtra(EXTRA_SHA256)),
                intent.getLongExtra(EXTRA_EXPECTED_SIZE_BYTES, 0L),
                intent.getStringExtra(EXTRA_AUTH_TOKEN),
                runtimeProfile,
                this::updateProgressNotification
            );
        } finally {
            clearCancellation(modelId);
        }
    }

    private void updateProgressNotification(@NonNull JSONObject transfer) {
        String modelId = transfer.optString("modelId", "model");
        String status = transfer.optString("status", TaiModelStore.STATE_DOWNLOADING);
        long bytesRead = transfer.optLong("bytesRead", 0L);
        long totalBytes = transfer.optLong("totalBytes", 0L);
        long now = SystemClock.elapsedRealtime();
        boolean terminal = TaiModelStore.STATE_INSTALLED.equals(status)
            || TaiModelStore.STATE_FAILED.equals(status)
            || TaiModelStore.STATE_CANCELLED.equals(status);
        boolean statusChanged = !status.equals(lastNotificationStatus);
        if (!terminal && !statusChanged && now - lastNotificationUpdateMs < 750L) return;
        lastNotificationUpdateMs = now;
        lastNotificationStatus = status;
        String title = "TAI downloading " + modelId;
        String text;
        if (TaiModelStore.STATE_INSTALLED.equals(status)) {
            title = "TAI model downloaded";
            text = modelId + " is ready";
        } else if (TaiModelStore.STATE_FAILED.equals(status)) {
            title = "TAI model download failed";
            text = formatErrorForNotification(transfer.optString("error", "Download failed"));
        } else if (TaiModelStore.STATE_CANCELLED.equals(status)) {
            title = "TAI model download cancelled";
            text = modelId + " can be retried later";
        } else if (TaiModelStore.STATE_VERIFYING.equals(status)) {
            text = "Verifying downloaded model";
        } else if (totalBytes > 0L) {
            text = formatPercent(bytesRead, totalBytes) + " - " + formatBytes(bytesRead) + " of " + formatBytes(totalBytes);
        } else {
            text = "Downloading " + formatBytes(bytesRead);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(title, text, bytesRead, totalBytes,
                TaiModelStore.STATE_QUEUED.equals(status)
                    || TaiModelStore.STATE_DOWNLOADING.equals(status)
                    || TaiModelStore.STATE_VERIFYING.equals(status)));
        }
    }

    @NonNull
    private String formatErrorForNotification(@NonNull String error) {
        switch (error) {
            case "insecure_url":
                return "Insecure URL: HTTPS required";
            default:
                return error;
        }
    }

    private Notification buildNotification(String title, String text, long bytesRead, long totalBytes, boolean ongoing) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        if (ongoing) {
            if (totalBytes > 0L) {
                builder.setProgress(10000, (int) (bytesRead * 10000L / totalBytes), false);
            } else {
                builder.setProgress(0, 0, true);
            }
        }
        return builder.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "TAI model downloads", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progress for Termux AI model downloads");
        manager.createNotificationChannel(channel);
    }

    private String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private String formatPercent(long value, long total) {
        if (total <= 0) return "";
        return String.format(Locale.US, "%.1f%%", (double) value * 100.0 / (double) total);
    }

    private String formatBytes(long bytes) {
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
