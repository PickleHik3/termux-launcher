package com.termux.app.settings.backup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.ai.TaiModelExport;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.BackupRestorePreferencesFragment;
import com.termux.app.notice.AppNotice;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Export AI models: copies the downloaded models into a folder the user picked in shared storage
 * ({@link TaiModelExport} has the layout). In the foreground, because a few gigabytes take minutes
 * and the user leaves Settings meanwhile; the notification shows the progress and has Cancel, and
 * the result is said in one line, as a notification and an in-app notice.
 */
public final class ModelExportService extends Service {
    private static final String LOG_TAG = "ModelExport";
    private static final String ACTION_EXPORT = ModelExportService.class.getName() + ".EXPORT";
    private static final String ACTION_CANCEL = ModelExportService.class.getName() + ".CANCEL";
    /** Set when the fragment took a persistable grant on the folder for this export, so it is released after. */
    private static final String EXTRA_RELEASE_GRANT = "release_grant";

    /** The phone's shared storage, where Downloads is: the pickers open there. */
    public static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";
    private static final String PRIMARY_VOLUME = "primary";

    private static final String CHANNEL_ID = "termux_ai_model_export";
    /** Distinct from every other notification id the app posts (24100–24113 are taken). */
    private static final int PROGRESS_ID = 24120;
    private static final int RESULT_ID = 24121;
    /** The progress notification is redrawn at most this often; the copy reports every megabyte. */
    private static final long NOTIFICATION_INTERVAL_MS = 750L;
    private static final int PROGRESS_MAX = 10_000;
    private static final String MIME_FILE = "application/octet-stream";

    private static volatile boolean sRunning;
    private static volatile boolean sCancel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-export");
        thread.setDaemon(true);
        return thread;
    });
    private long lastNotificationMs;

    /** True while an export runs, in this process. */
    public static boolean isRunning() {
        return sRunning;
    }

    /** Starts exporting into {@code tree}; {@code releaseGrant} when the caller took a persistable grant for it. */
    public static void start(@NonNull Context context, @NonNull Uri tree, boolean releaseGrant) {
        Intent intent = new Intent(context, ModelExportService.class)
            .setAction(ACTION_EXPORT)
            .setData(tree)
            .putExtra(EXTRA_RELEASE_GRANT, releaseGrant);
        ContextCompat.startForegroundService(context, intent);
    }

    /**
     * What there is to export: every readable downloaded or imported model, downloads first and the
     * registry winning on a shared id (as the model lists read them). Lists folders; reads nothing.
     */
    @NonNull
    public static List<TaiModelExport.Model> exportable(@NonNull Context context) {
        TaiModelStore store = new TaiModelStore(context);
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>(store.getDownloadedReadableModels());
        models.putAll(store.getInstalledUserModels());
        Map<String, String> exposures = new LinkedHashMap<>();
        for (String id : models.keySet()) {
            String exposure = store.getExposure(id);
            if (!TaiModelStore.EXPOSURE_SPLIT.equals(exposure)) exposures.put(id, exposure);
        }
        return TaiModelExport.plan(models.values(), store.getModelsDirectory(), exposures);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            sCancel = true;
            if (!sRunning) stopSelf(startId);
            return START_NOT_STICKY;
        }
        // Started with startForegroundService: the foreground state comes first, whatever follows.
        startForeground(PROGRESS_ID, progressNotification(getString(R.string.settings_backup_models_preparing), 0L, 0L));
        Uri tree = intent == null ? null : intent.getData();
        if (tree == null || sRunning) {
            if (!sRunning) {
                stopForeground(true);
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        boolean releaseGrant = intent.getBooleanExtra(EXTRA_RELEASE_GRANT, false);
        sRunning = true;
        sCancel = false;
        Context app = getApplicationContext();
        executor.execute(() -> {
            String message;
            try {
                message = export(app, tree);
            } catch (Throwable t) {
                // A provider that went away, a revoked folder: said, never thrown at the home screen.
                Logger.logStackTraceWithMessage(LOG_TAG, "Saving the models failed", t);
                message = getResources().getQuantityString(R.plurals.settings_backup_models_failed, 0, 0);
            } finally {
                sRunning = false;
                sCancel = false;
            }
            if (releaseGrant) releaseGrant(app, tree);
            stopForeground(true);
            postResult(message);
            String shown = message;
            new Handler(Looper.getMainLooper()).post(() -> AppNotice.show(app, shown, true));
            stopSelf();
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // A copy still running when the system stops the service ends at its next megabyte.
        sCancel = true;
        executor.shutdown();
        super.onDestroy();
    }

    /** Runs the export and returns the line that says how it ended. */
    @NonNull
    private String export(@NonNull Context app, @NonNull Uri tree) throws IOException {
        List<TaiModelExport.Model> models = exportable(app);
        if (models.isEmpty()) return getString(R.string.settings_backup_export_models_none);
        ContentResolver resolver = app.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        SafFolder root = new SafFolder(resolver, tree, rootId);
        long needed = TaiModelExport.bytesToWrite(models, root);
        long free = freeBytes(app, rootId, tree.getAuthority());
        if (free >= 0L && needed > free) {
            return getString(R.string.settings_backup_models_no_space, TaiModelExport.formatBytes(needed - free));
        }
        String place = displayName(resolver, tree, rootId);
        TaiModelExport.Result result = TaiModelExport.export(models, root, (model, done, total) -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastNotificationMs >= NOTIFICATION_INTERVAL_MS) {
                lastNotificationMs = now;
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(PROGRESS_ID, progressNotification(getString(R.string.settings_backup_models_progress,
                        model.displayName, TaiModelExport.formatBytes(done), TaiModelExport.formatBytes(total)), done, total));
                }
            }
            return !sCancel;
        });
        if (result.cancelled) {
            return getResources().getQuantityString(R.plurals.settings_backup_models_stopped, result.saved, result.saved);
        }
        if (result.error != null) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Saving the models stopped", result.error);
            return getResources().getQuantityString(R.plurals.settings_backup_models_failed, result.saved, result.saved);
        }
        return getResources().getQuantityString(R.plurals.settings_backup_models_saved, result.saved, result.saved, place);
    }

    /**
     * Free space where the folder is, or -1 when it cannot be known. Document providers do not
     * report it to apps, so it is read for the phone's own shared storage and, from Android 11, its
     * other volumes (an SD card); elsewhere the copy runs and stops cleanly if the space runs out.
     */
    private static long freeBytes(@NonNull Context context, @NonNull String treeDocumentId, @Nullable String authority) {
        if (!EXTERNAL_STORAGE_AUTHORITY.equals(authority)) return -1L;
        int colon = treeDocumentId.indexOf(':');
        String volume = colon < 0 ? treeDocumentId : treeDocumentId.substring(0, colon);
        File directory = null;
        try {
            if (PRIMARY_VOLUME.equals(volume)) {
                directory = Environment.getExternalStorageDirectory();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageManager storage = context.getSystemService(StorageManager.class);
                if (storage != null) {
                    for (StorageVolume candidate : storage.getStorageVolumes()) {
                        if (volume.equalsIgnoreCase(candidate.getUuid())) directory = candidate.getDirectory();
                    }
                }
            }
            long free = directory == null ? 0L : directory.getUsableSpace();
            return free > 0L ? free : -1L;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /** The picked folder's name ("Download"), for the result line. */
    @NonNull
    private static String displayName(@NonNull ContentResolver resolver, @NonNull Uri tree, @NonNull String rootId) {
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        try (Cursor cursor = resolver.query(document,
            new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name.trim();
            }
        } catch (RuntimeException ignored) {
            // Named from the id below.
        }
        String path = rootId.substring(rootId.indexOf(':') + 1);
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isEmpty() ? TaiModelExport.FOLDER_NAME : name;
    }

    private static void releaseGrant(@NonNull Context context, @NonNull Uri tree) {
        try {
            context.getContentResolver().releasePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
    }

    // ---- notifications ----

    private Notification progressNotification(@NonNull String text, long done, long total) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(getString(R.string.settings_backup_models_saving))
            .setContentText(text)
            .setContentIntent(openBackupRestore())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(android.R.string.cancel), cancelAction());
        if (total > 0L) builder.setProgress(PROGRESS_MAX, (int) (Math.min(done, total) * PROGRESS_MAX / total), false);
        else builder.setProgress(0, 0, true);
        return builder.build();
    }

    private void postResult(@NonNull String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.notify(RESULT_ID, new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(getString(R.string.settings_backup_models_result_title))
            .setContentText(message)
            .setContentIntent(openBackupRestore())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build());
    }

    private PendingIntent openBackupRestore() {
        Intent intent = new Intent(this, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, BackupRestorePreferencesFragment.class.getName())
            .putExtra(SettingsActivity.EXTRA_INITIAL_TITLE_RES, R.string.settings_destination_backup_restore);
        return PendingIntent.getActivity(this, PROGRESS_ID, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent cancelAction() {
        Intent intent = new Intent(this, ModelExportService.class).setAction(ACTION_CANCEL);
        return PendingIntent.getService(this, RESULT_ID, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void ensureChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
            getString(R.string.settings_backup_models_saving), NotificationManager.IMPORTANCE_LOW));
    }

    // ---- the picked folder, through the document provider ----

    /**
     * A folder in shared storage as {@link TaiModelExport} writes it. Each folder lists its children
     * once and keeps the list up to date as it writes, so a model of many files is not a query per file.
     */
    static final class SafFolder implements TaiModelExport.Folder {
        private final ContentResolver resolver;
        private final Uri tree;
        private final String documentId;
        @Nullable private Map<String, String[]> children;

        SafFolder(@NonNull ContentResolver resolver, @NonNull Uri tree, @NonNull String documentId) {
            this.resolver = resolver;
            this.tree = tree;
            this.documentId = documentId;
        }

        /** Name to {document id, MIME type, size}; size is "-1" when the provider does not say. */
        @NonNull
        private Map<String, String[]> children() throws IOException {
            if (children != null) return children;
            Map<String, String[]> listed = new LinkedHashMap<>();
            Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
            try (Cursor cursor = resolver.query(uri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                if (cursor == null) throw new IOException("Could not read the folder.");
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    if (id == null || name == null) continue;
                    String size = cursor.isNull(3) ? "-1" : Long.toString(cursor.getLong(3));
                    listed.put(name, new String[]{id, cursor.getString(2), size});
                }
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
            children = listed;
            return listed;
        }

        @Nullable
        @Override
        public TaiModelExport.Folder child(@NonNull String name, boolean create) throws IOException {
            String[] existing = children().get(name);
            if (existing != null) {
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(existing[1])
                    ? new SafFolder(resolver, tree, existing[0]) : null;
            }
            if (!create) return null;
            Uri made = createDocument(DocumentsContract.Document.MIME_TYPE_DIR, name);
            String id = DocumentsContract.getDocumentId(made);
            children().put(name, new String[]{id, DocumentsContract.Document.MIME_TYPE_DIR, "-1"});
            return new SafFolder(resolver, tree, id);
        }

        @Override
        public long size(@NonNull String name) {
            try {
                String[] existing = children().get(name);
                if (existing == null || DocumentsContract.Document.MIME_TYPE_DIR.equals(existing[1])) return -1L;
                return Long.parseLong(existing[2]);
            } catch (IOException | NumberFormatException e) {
                return -1L;
            }
        }

        @NonNull
        @Override
        public OutputStream create(@NonNull String name) throws IOException {
            String[] existing = children().get(name);
            if (existing != null) {
                if (!deleteDocument(existing[0])) throw new IOException("Could not replace " + name);
                children().remove(name);
            }
            Uri made = createDocument(MIME_FILE, name);
            children().put(name, new String[]{DocumentsContract.getDocumentId(made), MIME_FILE, "-1"});
            try {
                OutputStream out = resolver.openOutputStream(made, "w");
                if (out == null) throw new IOException("Could not write " + name);
                return out;
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void delete(@NonNull String name) {
            try {
                String[] existing = children().remove(name);
                if (existing != null) deleteDocument(existing[0]);
            } catch (IOException ignored) {
                // The folder cannot be read; there is nothing to remove from it.
            }
        }

        @NonNull
        private Uri createDocument(@NonNull String mimeType, @NonNull String name) throws IOException {
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
                Uri made = DocumentsContract.createDocument(resolver, parent, mimeType, name);
                if (made == null) throw new IOException("Could not create " + name);
                return made;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        private boolean deleteDocument(@NonNull String id) {
            try {
                return DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id));
            } catch (Exception e) {
                return false;
            }
        }
    }
}
