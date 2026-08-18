package com.termux.launcherctl;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Persists notification posted/removed events to SQLite and to a JSONL stream.
 *
 * <p>SQLite is authoritative; {@code notifications.jsonl} is a compatibility and
 * debug append-only stream as described in the launcherctl agent platform plan.
 */
public final class LauncherCtlNotificationStore {
    private static final String LOG_TAG = "LauncherCtlNotifStore";
    private static final String TABLE_NAME = "notification_events";
    private static final int MAX_ROWS = 10_000;
    static final long MAX_JSONL_BYTES = 4L * 1024 * 1024;
    private static final int DEFAULT_WRITE_TIMEOUT_MS = 5_000;

    private static LauncherCtlNotificationStore sInstance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launcherctl-notif-store");
        t.setDaemon(true);
        return t;
    });

    private SQLiteDatabase db;

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
     * Persists a notification-posted event asynchronously. Safe to call from the
     * notification listener callback thread.
     */
    public void persistPosted(JSONObject notificationJson) {
        persistEventAsync("posted", notificationJson);
    }

    /**
     * Persists a notification-removed event asynchronously. Safe to call from the
     * notification listener callback thread.
     */
    public void persistRemoved(JSONObject notificationJson) {
        persistEventAsync("removed", notificationJson);
    }

    private void persistEventAsync(String eventType, JSONObject notificationJson) {
        if (notificationJson == null) {
            return;
        }
        executor.submit(() -> {
            try {
                insertEvent(new LauncherCtlNotificationEvent(eventType, System.currentTimeMillis(), notificationJson));
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to persist " + eventType + " event: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronously inserts an event. Exposed for unit tests and for callers that
     * need to wait for persistence before querying.
     */
    public synchronized void insertEvent(LauncherCtlNotificationEvent event) {
        if (event == null) {
            return;
        }
        SQLiteDatabase database = getDb();
        if (database == null) {
            return;
        }

        try {
            database.execSQL(
                "INSERT INTO " + TABLE_NAME + " (" +
                    "event_type, notification_key, package_name, title, text, sub_text, big_text, " +
                    "category, post_time, is_ongoing, is_clearable, event_time, payload) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                    event.eventType,
                    event.key,
                    event.packageName,
                    event.title,
                    event.text,
                    event.subText,
                    event.bigText,
                    event.category,
                    event.postTime,
                    event.isOngoing ? 1 : 0,
                    event.isClearable ? 1 : 0,
                    event.eventTime,
                    event.notification.toString()
                }
            );
            appendJsonlEvent(event);
            pruneOldRows();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to insert notification event: " + e.getMessage());
        }
    }

    /**
     * Deletes everything this store has written: rows, the live JSONL stream and its rotation.
     *
     * <p>Called when the user turns notification history off. Leaving the previously captured
     * message bodies on disk would make the switch a promise about future notifications only,
     * which is not what turning it off means.
     */
    public synchronized void clearAll() {
        try {
            SQLiteDatabase database = getDb();
            if (database != null) database.execSQL("DELETE FROM " + TABLE_NAME);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to clear notification history rows: " + e.getMessage());
        }
        File jsonlFile = LauncherCtlStorage.getNotificationsJsonlFile();
        deleteQuietly(jsonlFile);
        deleteQuietly(new File(jsonlFile.getParentFile(), jsonlFile.getName() + ".1"));
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

    public List<LauncherCtlNotificationEvent> queryRecent(int limit) {
        return query("SELECT * FROM " + TABLE_NAME + " ORDER BY event_time DESC LIMIT ?",
            new String[]{String.valueOf(limit)});
    }

    public List<LauncherCtlNotificationEvent> querySince(long sinceMs, int limit) {
        return query("SELECT * FROM " + TABLE_NAME + " WHERE event_time >= ? ORDER BY event_time DESC LIMIT ?",
            new String[]{String.valueOf(sinceMs), String.valueOf(limit)});
    }

    public List<LauncherCtlNotificationEvent> querySearch(String query, int limit) {
        if (query == null || query.isEmpty()) {
            return new ArrayList<>();
        }
        String pattern = "%" + query + "%";
        return query("SELECT * FROM " + TABLE_NAME + " WHERE " +
                "title LIKE ? OR text LIKE ? OR sub_text LIKE ? OR big_text LIKE ? OR " +
                "package_name LIKE ? OR notification_key LIKE ? " +
                "ORDER BY event_time DESC LIMIT ?",
            new String[]{pattern, pattern, pattern, pattern, pattern, pattern, String.valueOf(limit)});
    }

    public JSONObject queryStats(Long sinceMs) throws JSONException {
        String where = sinceMs != null ? " WHERE event_time >= ?" : "";
        String[] args = sinceMs != null ? new String[]{String.valueOf(sinceMs)} : new String[]{};

        JSONObject stats = new JSONObject();
        stats.put("total", count(where, args));
        stats.put("posted", count(where.isEmpty() ? " WHERE event_type = ?" : where + " AND event_type = ?",
            appendArg(args, "posted")));
        stats.put("removed", count(where.isEmpty() ? " WHERE event_type = ?" : where + " AND event_type = ?",
            appendArg(args, "removed")));

        JSONArray packages = new JSONArray();
        Cursor cursor = null;
        try {
            cursor = getDb().rawQuery(
                "SELECT package_name, COUNT(*) AS cnt FROM " + TABLE_NAME + where +
                    " GROUP BY package_name ORDER BY cnt DESC LIMIT 20", args);
            while (cursor != null && cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                String pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
                item.put("packageName", pkg == null ? JSONObject.NULL : pkg);
                item.put("count", cursor.getLong(cursor.getColumnIndexOrThrow("cnt")));
                packages.put(item);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to query package stats: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        stats.put("packages", packages);
        if (sinceMs != null) {
            stats.put("since", sinceMs.longValue());
        }
        return stats;
    }

    private long count(String where, String[] args) {
        Cursor cursor = null;
        try {
            cursor = getDb().rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME + where, args);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to count notification events: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    private static String[] appendArg(String[] args, String extra) {
        String[] result = new String[args.length + 1];
        System.arraycopy(args, 0, result, 0, args.length);
        result[args.length] = extra;
        return result;
    }

    private List<LauncherCtlNotificationEvent> query(String sql, String[] args) {
        List<LauncherCtlNotificationEvent> events = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getDb().rawQuery(sql, args);
            while (cursor != null && cursor.moveToNext()) {
                events.add(cursorToEvent(cursor));
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to query notification events: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return events;
    }

    private LauncherCtlNotificationEvent cursorToEvent(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        String eventType = cursor.getString(cursor.getColumnIndexOrThrow("event_type"));
        long eventTime = cursor.getLong(cursor.getColumnIndexOrThrow("event_time"));
        String payload = cursor.getString(cursor.getColumnIndexOrThrow("payload"));
        JSONObject notification;
        try {
            notification = new JSONObject(payload);
        } catch (JSONException e) {
            notification = new JSONObject();
        }
        return new LauncherCtlNotificationEvent(id, eventType, eventTime, notification);
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
                createTables(db);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to open notification database: " + e.getMessage());
            }
        }
        return db;
    }

    private void createTables(SQLiteDatabase database) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "event_type TEXT NOT NULL, " +
                "notification_key TEXT, " +
                "package_name TEXT, " +
                "title TEXT, " +
                "text TEXT, " +
                "sub_text TEXT, " +
                "big_text TEXT, " +
                "category TEXT, " +
                "post_time INTEGER, " +
                "is_ongoing INTEGER, " +
                "is_clearable INTEGER, " +
                "event_time INTEGER NOT NULL, " +
                "payload TEXT)"
        );
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_notification_events_time ON " + TABLE_NAME + "(event_time DESC)"
        );
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_notification_events_pkg ON " + TABLE_NAME + "(package_name)"
        );
    }

    private void appendJsonlEvent(LauncherCtlNotificationEvent event) {
        File jsonlFile = LauncherCtlStorage.getNotificationsJsonlFile();
        rotateJsonlIfLarge(jsonlFile);
        try (FileOutputStream fos = new FileOutputStream(jsonlFile, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write(event.toJson().toString());
            writer.newLine();
        } catch (IOException | JSONException e) {
            Log.w(LOG_TAG, "Failed to append notification JSONL event: " + e.getMessage());
        }
    }

    /**
     * Keeps the append-only stream bounded at one live file plus one rotated file.
     *
     * <p>SQLite is capped by {@link #pruneOldRows()}, but the JSONL mirror had no ceiling at all: a
     * chatty app could grow it until internal storage filled, and every line in it is notification
     * content that outlives the SQLite retention window. Rotation happens by rename so a reader
     * that already opened the stream keeps reading a consistent file, and the previous rotation is
     * dropped rather than kept forever.
     */
    private void rotateJsonlIfLarge(File jsonlFile) {
        try {
            if (!jsonlFile.isFile() || jsonlFile.length() < MAX_JSONL_BYTES) return;
            File rotated = new File(jsonlFile.getParentFile(), jsonlFile.getName() + ".1");
            if (rotated.exists() && !rotated.delete()) {
                Log.w(LOG_TAG, "Failed to remove previous notification JSONL rotation");
            }
            if (!jsonlFile.renameTo(rotated) && !jsonlFile.delete()) {
                Log.w(LOG_TAG, "Failed to rotate notification JSONL stream");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to rotate notification JSONL stream: " + e.getMessage());
        }
    }

    private void pruneOldRows() {
        try {
            long count;
            Cursor cursor = getDb().rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
            if (cursor == null) {
                return;
            }
            try {
                cursor.moveToFirst();
                count = cursor.getLong(0);
            } finally {
                cursor.close();
            }
            if (count <= MAX_ROWS) {
                return;
            }
            getDb().execSQL(
                "DELETE FROM " + TABLE_NAME + " WHERE id <= (" +
                    "SELECT id FROM " + TABLE_NAME + " ORDER BY event_time DESC LIMIT 1 OFFSET ?)",
                new Object[]{MAX_ROWS}
            );
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to prune old notification rows: " + e.getMessage());
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
