package com.termux.app.assistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Assistant's chats, in an app-private SQLite file that nothing outside the app process can
 * read (model requests never see it). Same shape as the notification store: a raw database opened
 * lazily, no open helper.
 *
 * <p>A message left {@code streaming} by a process that died is {@code stopped} the next time the
 * file is opened, keeping whatever text had been saved. A {@code waiting} answer (a question held
 * for the model) stays waiting.
 */
public final class AssistantChatStore {
    private static final String LOG_TAG = "AssistantChatStore";
    static final String DB_NAME = "assistant_chats.db";
    static final int DB_VERSION = 1;
    /** Longest chat title, ellipsis included. */
    public static final int TITLE_MAX_CHARS = 60;

    private static AssistantChatStore sInstance;

    private final File dbFile;
    private SQLiteDatabase db;

    public AssistantChatStore(@NonNull File dbFile) {
        this.dbFile = dbFile;
    }

    public static synchronized AssistantChatStore getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new AssistantChatStore(context.getApplicationContext().getDatabasePath(DB_NAME));
        }
        return sInstance;
    }

    /** Closes the store and resets the singleton. Intended for unit tests. */
    public static synchronized void resetForTesting() {
        if (sInstance != null) {
            sInstance.close();
            sInstance = null;
        }
    }

    public synchronized void close() {
        if (db != null) {
            try {
                db.close();
            } catch (RuntimeException ignored) {
            }
            db = null;
        }
    }

    @Nullable
    private synchronized SQLiteDatabase getDb() {
        if (db != null && db.isOpen()) return db;
        try {
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            SQLiteDatabase opened = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            opened.execSQL("PRAGMA foreign_keys = ON");
            if (opened.getVersion() < 1) {
                opened.execSQL("CREATE TABLE IF NOT EXISTS chats ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', "
                    + "created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)");
                opened.execSQL("CREATE TABLE IF NOT EXISTS messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
                    + "role TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, "
                    + "error_code TEXT, created_ms INTEGER NOT NULL)");
                opened.execSQL("CREATE INDEX IF NOT EXISTS messages_chat ON messages(chat_id, id)");
                opened.execSQL("CREATE INDEX IF NOT EXISTS chats_updated ON chats(updated_ms)");
                opened.setVersion(DB_VERSION);
            }
            ContentValues stopped = new ContentValues();
            stopped.put("state", AssistantMessage.STATE_STOPPED);
            opened.update("messages", stopped, "state = ?",
                new String[]{AssistantMessage.STATE_STREAMING});
            db = opened;
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Could not open the chat database: " + e.getMessage());
            db = null;
        }
        return db;
    }

    /** Makes a new, empty chat; its title comes from the first user message. Returns its id, or -1. */
    public synchronized long createChat(long nowMs) {
        SQLiteDatabase database = getDb();
        if (database == null) return -1L;
        ContentValues values = new ContentValues();
        values.put("title", "");
        values.put("created_ms", nowMs);
        values.put("updated_ms", nowMs);
        return database.insert("chats", null, values);
    }

    @Nullable
    public synchronized AssistantChat chat(long id) {
        SQLiteDatabase database = getDb();
        if (database == null) return null;
        try (Cursor c = database.rawQuery(
            "SELECT id, title, created_ms, updated_ms FROM chats WHERE id = ?",
            new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? readChat(c) : null;
        }
    }

    /**
     * Chats newest-updated first. With a query, only those whose title or any message contains it,
     * ignoring case.
     */
    @NonNull
    public synchronized List<AssistantChat> listChats(@Nullable String query) {
        return listChats(query, Integer.MAX_VALUE);
    }

    @NonNull
    public synchronized List<AssistantChat> latestChats(int n) {
        return listChats(null, n);
    }

    @NonNull
    private List<AssistantChat> listChats(@Nullable String query, int limit) {
        List<AssistantChat> out = new ArrayList<>();
        SQLiteDatabase database = getDb();
        if (database == null || limit <= 0) return out;
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<Long> inMessages = new HashSet<>();
        if (!needle.isEmpty()) {
            // Matched here rather than with LIKE, which only folds ASCII case.
            try (Cursor c = database.rawQuery("SELECT chat_id, text FROM messages", null)) {
                while (c.moveToNext()) {
                    if (c.getString(1).toLowerCase(Locale.ROOT).contains(needle)) {
                        inMessages.add(c.getLong(0));
                    }
                }
            }
        }
        try (Cursor c = database.rawQuery(
            "SELECT id, title, created_ms, updated_ms FROM chats ORDER BY updated_ms DESC, id DESC", null)) {
            while (c.moveToNext() && out.size() < limit) {
                AssistantChat chat = readChat(c);
                if (!needle.isEmpty() && !chat.title.toLowerCase(Locale.ROOT).contains(needle)
                    && !inMessages.contains(chat.id)) continue;
                out.add(chat);
            }
        }
        return out;
    }

    @NonNull
    public synchronized List<AssistantMessage> messages(long chatId) {
        List<AssistantMessage> out = new ArrayList<>();
        SQLiteDatabase database = getDb();
        if (database == null) return out;
        try (Cursor c = database.rawQuery(
            "SELECT id, chat_id, role, text, state, error_code, created_ms FROM messages "
                + "WHERE chat_id = ? ORDER BY id", new String[]{String.valueOf(chatId)})) {
            while (c.moveToNext()) out.add(readMessage(c));
        }
        return out;
    }

    /** The oldest answer still waiting for the model, in any chat, or null. */
    @Nullable
    public synchronized AssistantMessage oldestWaiting() {
        SQLiteDatabase database = getDb();
        if (database == null) return null;
        try (Cursor c = database.rawQuery(
            "SELECT id, chat_id, role, text, state, error_code, created_ms FROM messages "
                + "WHERE state = ? ORDER BY id LIMIT 1", new String[]{AssistantMessage.STATE_WAITING})) {
            return c.moveToFirst() ? readMessage(c) : null;
        }
    }

    @Nullable
    public synchronized AssistantMessage message(long messageId) {
        SQLiteDatabase database = getDb();
        if (database == null) return null;
        try (Cursor c = database.rawQuery(
            "SELECT id, chat_id, role, text, state, error_code, created_ms FROM messages WHERE id = ?",
            new String[]{String.valueOf(messageId)})) {
            return c.moveToFirst() ? readMessage(c) : null;
        }
    }

    /**
     * Adds a message to a chat and bumps the chat to the top. The first user message of an untitled
     * chat becomes its title. Returns the message id, or -1 when the chat is gone.
     */
    public synchronized long appendMessage(long chatId, @NonNull String role, @NonNull String text,
                                           @NonNull String state, long nowMs) {
        SQLiteDatabase database = getDb();
        AssistantChat chat = chat(chatId);
        if (database == null || chat == null) return -1L;
        ContentValues values = new ContentValues();
        values.put("chat_id", chatId);
        values.put("role", role);
        values.put("text", text);
        values.put("state", state);
        values.put("created_ms", nowMs);
        long id = database.insert("messages", null, values);
        if (id < 0) return -1L;
        ContentValues chatValues = new ContentValues();
        chatValues.put("updated_ms", nowMs);
        if (AssistantMessage.ROLE_USER.equals(role) && chat.title.isEmpty()) {
            chatValues.put("title", titleFrom(text));
        }
        database.update("chats", chatValues, "id = ?", new String[]{String.valueOf(chatId)});
        return id;
    }

    /** Sets a message's text, state and error code, and bumps its chat. */
    public synchronized boolean updateMessage(long messageId, @NonNull String text,
                                              @NonNull String state, @Nullable String errorCode,
                                              long nowMs) {
        SQLiteDatabase database = getDb();
        if (database == null) return false;
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("state", state);
        if (errorCode == null) values.putNull("error_code");
        else values.put("error_code", errorCode);
        if (database.update("messages", values, "id = ?", new String[]{String.valueOf(messageId)}) == 0) {
            return false;
        }
        database.execSQL("UPDATE chats SET updated_ms = ? WHERE id = "
            + "(SELECT chat_id FROM messages WHERE id = ?)", new Object[]{nowMs, messageId});
        return true;
    }

    /** Removes a chat and its messages. */
    public synchronized void deleteChat(long chatId) {
        SQLiteDatabase database = getDb();
        if (database == null) return;
        // Explicit, so it does not depend on foreign keys being enforced.
        database.delete("messages", "chat_id = ?", new String[]{String.valueOf(chatId)});
        database.delete("chats", "id = ?", new String[]{String.valueOf(chatId)});
    }

    /** Removes every chat; the later settings row calls this. */
    public synchronized void deleteAllChats() {
        SQLiteDatabase database = getDb();
        if (database == null) return;
        database.delete("messages", null, null);
        database.delete("chats", null, null);
    }

    /** First line of the text, whitespace collapsed, at most {@link #TITLE_MAX_CHARS} characters. */
    @NonNull
    static String titleFrom(@NonNull String text) {
        String line = text.trim();
        int newline = line.indexOf('\n');
        if (newline >= 0) line = line.substring(0, newline);
        line = line.replaceAll("\\s+", " ").trim();
        if (line.length() <= TITLE_MAX_CHARS) return line;
        int end = TITLE_MAX_CHARS - 1;
        if (Character.isHighSurrogate(line.charAt(end - 1))) end--;
        return line.substring(0, end).trim() + "…";
    }

    @NonNull
    private static AssistantChat readChat(@NonNull Cursor c) {
        return new AssistantChat(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3));
    }

    @NonNull
    private static AssistantMessage readMessage(@NonNull Cursor c) {
        return new AssistantMessage(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
            c.getString(4), c.isNull(5) ? null : c.getString(5), c.getLong(6));
    }
}
