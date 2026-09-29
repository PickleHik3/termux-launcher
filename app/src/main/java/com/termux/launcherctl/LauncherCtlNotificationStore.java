package com.termux.launcherctl;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The per-app notification history that agents in the shell query through {@code launcherctl
 * notifications}.
 *
 * <p>One row per distinct message (see {@link LauncherCtlNotificationEvent#explode}), kept in
 * SQLite under {@code ~/.launcherctl}. Removal is a timestamp on the existing row. Rows age out
 * by the retention setting rather than by count; the prune runs on the write path but at most
 * once an hour, so a chatty app does not pay for it on every post.
 *
 * <p>The store does not decide what is recorded: the listener only hands over notifications from
 * apps the user enabled. Masking of one-time codes happens here, at write time, so an unmasked
 * code never reaches the disk.
 */
public final class LauncherCtlNotificationStore {
    private static final String LOG_TAG = "LauncherCtlNotifStore";
    private static final String TABLE_NAME = "notification_history";
    /** The table this store replaced; dropped by the migration. */
    private static final String LEGACY_TABLE_NAME = "notification_events";
    private static final int DB_VERSION = 2;
    /** Safety ceiling behind the age limit, so one app posting in a loop cannot fill the disk. */
    static final int MAX_ROWS = 50_000;
    static final long PRUNE_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
    /** The same content from another notification key inside this window is one message seen twice. */
    static final long CROSS_KEY_DEDUPE_MS = TimeUnit.MINUTES.toMillis(2);
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 1000;
    private static final int DEFAULT_WRITE_TIMEOUT_MS = 5_000;

    private static LauncherCtlNotificationStore sInstance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launcherctl-notif-store");
        t.setDaemon(true);
        return t;
    });

    private SQLiteDatabase db;
    private long lastPruneMs;

    /** What a read asks for. Null or zero fields are not filtered on. */
    public static final class Filter {
        /** A package name, or a label matched case-insensitively as a substring. */
        @Nullable public String app;
        public long sinceMs;
        public long untilMs;
        @Nullable public String query;
        public int limit = DEFAULT_LIMIT;
    }

    private LauncherCtlNotificationStore() {
    }

    public static synchronized LauncherCtlNotificationStore getInstance() {
        if (sInstance == null) {
            sInstance = new LauncherCtlNotificationStore();
        }
        return sInstance;
    }

    /**
     * Closes the store and resets the singleton. Intended for unit tests.
     */
    public static synchronized void resetForTesting() {
        if (sInstance != null) {
            sInstance.close();
            sInstance = null;
        }
    }

    /**
     * Records the rows of a posted notification asynchronously. Safe to call from the
     * notification listener callback thread.
     */
    public void persistPosted(@NonNull List<LauncherCtlNotificationEvent> events,
                              boolean maskCodes, int retentionDays) {
        if (events.isEmpty()) return;
        executor.submit(() -> {
            try {
                insertPosted(events, maskCodes, retentionDays, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to persist notification: " + e.getMessage());
            }
        });
    }

    /**
     * Stamps a notification's rows as gone from the shade, asynchronously. Safe to call from the
     * notification listener callback thread.
     */
    public void persistRemoved(@Nullable String key) {
        if (key == null) return;
        executor.submit(() -> {
            try {
                markRemoved(key, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to record notification removal: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronously records rows, skipping any already there, and returns how many were new.
     * Exposed for unit tests and for callers that need the write done before they query.
     *
     * <p>A row is a repeat when the same notification key already holds the same content and that
     * copy is still in the shade, or its time is the app's own (a chat line re-posted after the
     * user swiped the notification away is still the same chat line). The same content from another
     * key within {@link #CROSS_KEY_DEDUPE_MS} is a repeat too: a group summary and its child, or
     * one message pushed through two channels.
     */
    public synchronized int insertPosted(@NonNull List<LauncherCtlNotificationEvent> events,
                                         boolean maskCodes, int retentionDays, long nowMs) {
        SQLiteDatabase database = getDb();
        if (database == null) return 0;
        int inserted = 0;
        try {
            for (LauncherCtlNotificationEvent raw : events) {
                LauncherCtlNotificationEvent event = maskCodes
                    ? raw.mapText(LauncherCtlNotificationMasker::mask) : raw;
                String hash = event.contentHash();
                if (isRepeat(database, event, hash, nowMs)) continue;
                database.execSQL(
                    "INSERT INTO " + TABLE_NAME + " (" +
                        "notif_key, package_name, app_label, conversation, title, sender, text, " +
                        "sub_text, category, channel_id, message_time, post_time, recorded_time, " +
                        "removed_time, content_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)",
                    new Object[]{event.key, event.packageName, event.appLabel, event.conversation,
                        event.title, event.sender, event.text, event.subText, event.category,
                        event.channelId, event.messageTime, event.postTime, nowMs, hash});
                inserted++;
            }
            maybePrune(database, retentionDays, nowMs);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to insert notification rows: " + e.getMessage());
        }
        return inserted;
    }

    private boolean isRepeat(SQLiteDatabase database, LauncherCtlNotificationEvent event,
                             String hash, long nowMs) {
        String sameKey = "notif_key = ? AND content_hash = ?"
            + (event.explicitTime ? "" : " AND removed_time IS NULL");
        if (exists(database, sameKey, new String[]{event.key, hash})) return true;
        return exists(database,
            "package_name = ? AND content_hash = ? AND recorded_time >= ?",
            new String[]{event.packageName, hash, String.valueOf(nowMs - CROSS_KEY_DEDUPE_MS)});
    }

    private static boolean exists(SQLiteDatabase database, String where, String[] args) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM " + TABLE_NAME + " WHERE " + where + " LIMIT 1", args)) {
            return cursor.moveToFirst();
        }
    }

    /** Sets the removal time on the rows of a notification still in the shade. */
    public synchronized void markRemoved(@NonNull String key, long nowMs) {
        SQLiteDatabase database = getDb();
        if (database == null) return;
        try {
            database.execSQL("UPDATE " + TABLE_NAME + " SET removed_time = ? " +
                "WHERE notif_key = ? AND removed_time IS NULL", new Object[]{nowMs, key});
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to stamp notification removal: " + e.getMessage());
        }
    }

    private void maybePrune(SQLiteDatabase database, int retentionDays, long nowMs) {
        if (lastPruneMs != 0 && nowMs - lastPruneMs < PRUNE_INTERVAL_MS) return;
        lastPruneMs = nowMs;
        pruneNow(database, retentionDays, nowMs);
    }

    /** Deletes rows recorded before the retention window, then anything past the row ceiling. */
    synchronized void pruneNow(int retentionDays, long nowMs) {
        SQLiteDatabase database = getDb();
        if (database != null) pruneNow(database, retentionDays, nowMs);
    }

    private void pruneNow(SQLiteDatabase database, int retentionDays, long nowMs) {
        try {
            long cutoff = nowMs - TimeUnit.DAYS.toMillis(Math.max(1, retentionDays));
            database.execSQL("DELETE FROM " + TABLE_NAME + " WHERE recorded_time < ?", new Object[]{cutoff});
            database.execSQL("DELETE FROM " + TABLE_NAME + " WHERE id <= (SELECT id FROM " + TABLE_NAME +
                " ORDER BY id DESC LIMIT 1 OFFSET ?)", new Object[]{MAX_ROWS});
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to prune notification history: " + e.getMessage());
        }
    }

    /**
     * Deletes every recorded row.
     *
     * <p>Called when the user turns notification history off. Leaving the previously captured
     * message bodies on disk would make the switch a promise about future notifications only,
     * which is not what turning it off means.
     */
    public synchronized void clearAll() {
        clear(null);
    }

    /**
     * Deletes the rows of one app (a package name, or a label as in {@link Filter#app}), or of all
     * apps when {@code app} is null or empty. Returns how many rows went.
     */
    public synchronized int clear(@Nullable String app) {
        SQLiteDatabase database = getDb();
        if (database == null) return 0;
        try {
            if (app == null || app.trim().isEmpty()) {
                int before = (int) count(database, "");
                database.execSQL("DELETE FROM " + TABLE_NAME);
                return before;
            }
            List<String> args = new ArrayList<>();
            String where = appClause(app, args);
            int before = (int) count(database, " WHERE " + where, args.toArray(new String[0]));
            database.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + where, args.toArray());
            return before;
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to clear notification history: " + e.getMessage());
            return 0;
        }
    }

    /** Rows newest first. The limit is clamped to {@code 1..MAX_LIMIT}. */
    @NonNull
    public synchronized List<LauncherCtlNotificationEvent> query(@NonNull Filter filter) {
        List<LauncherCtlNotificationEvent> rows = new ArrayList<>();
        SQLiteDatabase database = getDb();
        if (database == null) return rows;
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (filter.app != null && !filter.app.trim().isEmpty()) {
            where.append(" AND ").append(appClause(filter.app, args));
        }
        if (filter.sinceMs > 0) {
            where.append(" AND message_time >= ?");
            args.add(String.valueOf(filter.sinceMs));
        }
        if (filter.untilMs > 0) {
            where.append(" AND message_time <= ?");
            args.add(String.valueOf(filter.untilMs));
        }
        if (filter.query != null && !filter.query.trim().isEmpty()) {
            String pattern = likePattern(filter.query.trim());
            where.append(" AND (title LIKE ? ESCAPE '\\' OR text LIKE ? ESCAPE '\\' OR sub_text LIKE ? ESCAPE '\\'" +
                " OR conversation LIKE ? ESCAPE '\\' OR sender LIKE ? ESCAPE '\\' OR app_label LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 6; i++) args.add(pattern);
        }
        int limit = Math.max(1, Math.min(MAX_LIMIT, filter.limit));
        args.add(String.valueOf(limit));
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + where +
                " ORDER BY message_time DESC, id DESC LIMIT ?", args.toArray(new String[0]))) {
            while (cursor.moveToNext()) rows.add(fromCursor(cursor));
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to query notification history: " + e.getMessage());
        }
        return rows;
    }

    /** Recorded apps as {@code [{package, app, count, lastSeen}]}, most recently seen first. */
    @NonNull
    public synchronized JSONArray queryApps() {
        JSONArray apps = new JSONArray();
        SQLiteDatabase database = getDb();
        if (database == null) return apps;
        try (Cursor cursor = database.rawQuery(
                "SELECT package_name, MAX(app_label), COUNT(*), MAX(message_time) FROM " + TABLE_NAME +
                    " GROUP BY package_name ORDER BY MAX(message_time) DESC", null)) {
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                String pkg = cursor.getString(0);
                String label = cursor.getString(1);
                item.put("package", pkg);
                item.put("app", label == null ? pkg : label);
                item.put("count", cursor.getLong(2));
                item.put("lastSeen", cursor.getLong(3));
                item.put("lastSeenIso", java.time.Instant.ofEpochMilli(cursor.getLong(3)).toString());
                apps.put(item);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to list recorded apps: " + e.getMessage());
        }
        return apps;
    }

    public synchronized long countAll() {
        SQLiteDatabase database = getDb();
        return database == null ? 0 : count(database, "");
    }

    private static long count(SQLiteDatabase database, String whereClause, String... args) {
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME + whereClause, args)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to count notification rows: " + e.getMessage());
            return 0;
        }
    }

    /** The package, or a label fragment, as one SQL condition; its arguments are appended to {@code args}. */
    private static String appClause(String app, List<String> args) {
        String trimmed = app.trim();
        args.add(trimmed);
        args.add(likePattern(trimmed.toLowerCase(Locale.ROOT)));
        return "(package_name = ? OR LOWER(app_label) LIKE ? ESCAPE '\\')";
    }

    private static String likePattern(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static LauncherCtlNotificationEvent fromCursor(Cursor cursor) {
        return new LauncherCtlNotificationEvent(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("notif_key")),
            cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
            cursor.getString(cursor.getColumnIndexOrThrow("app_label")),
            cursor.getString(cursor.getColumnIndexOrThrow("conversation")),
            cursor.getString(cursor.getColumnIndexOrThrow("title")),
            cursor.getString(cursor.getColumnIndexOrThrow("sender")),
            cursor.getString(cursor.getColumnIndexOrThrow("text")),
            cursor.getString(cursor.getColumnIndexOrThrow("sub_text")),
            cursor.getString(cursor.getColumnIndexOrThrow("category")),
            cursor.getString(cursor.getColumnIndexOrThrow("channel_id")),
            cursor.getLong(cursor.getColumnIndexOrThrow("message_time")),
            cursor.getLong(cursor.getColumnIndexOrThrow("post_time")),
            cursor.getLong(cursor.getColumnIndexOrThrow("recorded_time")),
            cursor.isNull(cursor.getColumnIndexOrThrow("removed_time")) ? 0
                : cursor.getLong(cursor.getColumnIndexOrThrow("removed_time")),
            false);
    }

    private synchronized SQLiteDatabase getDb() {
        File dbFile = LauncherCtlStorage.getDatabaseFile();
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(LOG_TAG, "Failed to create database parent directory");
        }
        if (db == null || !db.isOpen()) {
            try {
                db = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
                migrate(db);
                deleteLegacyJsonl();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to open notification database: " + e.getMessage());
            }
        }
        return db;
    }

    /**
     * Brings the file up to {@link #DB_VERSION}. Version 1 (unstamped, 0) was the write-only event
     * table; its rows held every app's messages because the feature was on for all or none, so they
     * are dropped rather than carried into a per-app history that never asked for them.
     */
    private void migrate(SQLiteDatabase database) {
        if (database.getVersion() < DB_VERSION) {
            database.execSQL("DROP TABLE IF EXISTS " + LEGACY_TABLE_NAME);
            database.setVersion(DB_VERSION);
        }
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "notif_key TEXT NOT NULL, " +
                "package_name TEXT NOT NULL, " +
                "app_label TEXT, " +
                "conversation TEXT, " +
                "title TEXT, " +
                "sender TEXT, " +
                "text TEXT, " +
                "sub_text TEXT, " +
                "category TEXT, " +
                "channel_id TEXT, " +
                "message_time INTEGER NOT NULL, " +
                "post_time INTEGER, " +
                "recorded_time INTEGER NOT NULL, " +
                "removed_time INTEGER, " +
                "content_hash TEXT NOT NULL)"
        );
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_history_time ON " + TABLE_NAME + "(message_time DESC)");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_history_pkg ON " + TABLE_NAME + "(package_name, message_time DESC)");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_history_key ON " + TABLE_NAME + "(notif_key, content_hash)");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_history_recorded ON " + TABLE_NAME + "(recorded_time)");
    }

    /**
     * The old design mirrored every event into {@code notifications.jsonl}, whose lines outlived
     * the retention window. Nothing writes it now; remove what earlier builds left behind.
     */
    private void deleteLegacyJsonl() {
        File jsonl = LauncherCtlStorage.getLegacyNotificationsJsonlFile();
        deleteQuietly(jsonl);
        deleteQuietly(new File(jsonl.getParentFile(), jsonl.getName() + ".1"));
    }

    private static void deleteQuietly(File file) {
        try {
            if (file.exists() && !file.delete()) {
                Log.w(LOG_TAG, "Failed to delete " + file.getName());
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to delete " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Waits for queued async writes to finish. Exposed for unit tests.
     */
    public void awaitWritesForTesting(long timeoutMs) throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Closes the database and executor. The store should not be used after this
     * call outside of tests.
     */
    public synchronized void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(DEFAULT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (db != null && db.isOpen()) {
            db.close();
            db = null;
        }
    }
}
