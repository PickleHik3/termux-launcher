package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synchronous durable store. Writes commit before the in-memory snapshot is replaced. */
public final class LauncherWidgetRepository {
    public interface Storage {
        @Nullable String read();
        boolean write(@NonNull String value);
    }

    private static final int SCHEMA_VERSION = 1;
    private static final String PREFS = "launcher_widget_repository";
    private static final String KEY_STATE = "state";

    private final Storage storage;
    private LinkedHashMap<Integer, LauncherWidgetRecord> records = new LinkedHashMap<>();
    @Nullable private WidgetAddTransaction pending;

    public LauncherWidgetRepository(@NonNull Storage storage) {
        this.storage = storage;
        load(storage.read());
    }

    @NonNull
    public static LauncherWidgetRepository create(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new LauncherWidgetRepository(new Storage() {
            @Override public String read() { return preferences.getString(KEY_STATE, null); }
            @Override public boolean write(@NonNull String value) {
                return preferences.edit().putString(KEY_STATE, value).commit();
            }
        });
    }

    @NonNull
    public synchronized List<LauncherWidgetRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    @Nullable
    public synchronized LauncherWidgetRecord get(int appWidgetId) {
        return records.get(appWidgetId);
    }

    @Nullable
    public synchronized WidgetAddTransaction pending() { return pending; }

    public synchronized boolean putRecord(@NonNull LauncherWidgetRecord record) {
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        LauncherWidgetRecord existing = next.get(record.appWidgetId);
        if (existing != null && (!existing.provider.equals(record.provider)
            || existing.profileSerial != record.profileSerial)) {
            throw new IllegalArgumentException("app-widget ID already belongs to another provider");
        }
        next.put(record.appWidgetId, record);
        return commit(next, pending);
    }

    public synchronized boolean removeRecord(int appWidgetId) {
        if (!records.containsKey(appWidgetId)) return true;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(appWidgetId);
        return commit(next, pending);
    }

    public synchronized boolean setPending(@NonNull WidgetAddTransaction transaction) {
        if (pending != null && !pending.token.equals(transaction.token)) {
            throw new IllegalStateException("another widget add is pending");
        }
        LauncherWidgetRecord record = records.get(transaction.appWidgetId);
        if (record != null && record.state != LauncherWidgetRecord.State.DELETING) {
            throw new IllegalArgumentException("pending ID is already active");
        }
        return commit(records, transaction);
    }

    public synchronized boolean clearPending(@NonNull String token) {
        if (pending == null) return true;
        if (!pending.token.equals(token)) return false;
        return commit(records, null);
    }

    /** Atomically makes the configured widget active and consumes its durable transaction. */
    public synchronized boolean finalizeActive(@NonNull String token,
                                               @NonNull LauncherWidgetRecord record) {
        if (pending == null || !pending.token.equals(token)
            || pending.appWidgetId != record.appWidgetId) return false;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(record.appWidgetId, record);
        return commit(next, null);
    }

    /** First phase of abandoning an allocated pending ID. */
    public synchronized boolean beginPendingDeletion(@NonNull WidgetAddTransaction transaction) {
        LauncherWidgetRecord deleting = new LauncherWidgetRecord(transaction.appWidgetId,
            transaction.provider, transaction.profileSerial, LauncherWidgetRecord.State.DELETING,
            transaction.requestedOptions(), null);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(deleting.appWidgetId, deleting);
        return commit(next, pending);
    }

    /** Completes per-ID deletion and clears only the matching pending transaction. */
    public synchronized boolean completeDeletion(int appWidgetId, @Nullable String token) {
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(appWidgetId);
        WidgetAddTransaction nextPending = pending;
        if (pending != null && pending.appWidgetId == appWidgetId
            && (token == null || pending.token.equals(token))) nextPending = null;
        return commit(next, nextPending);
    }

    @NonNull
    public synchronized String serialize() { return encode(records, pending); }

    private boolean commit(Map<Integer, LauncherWidgetRecord> next,
                           @Nullable WidgetAddTransaction nextPending) {
        String encoded = encode(next, nextPending);
        if (!storage.write(encoded)) return false;
        records = new LinkedHashMap<>(next);
        pending = nextPending;
        return true;
    }

    private void load(@Nullable String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(encoded);
            if (root.optInt("version", 0) != SCHEMA_VERSION) return;
            LinkedHashMap<Integer, LauncherWidgetRecord> loaded = new LinkedHashMap<>();
            JSONArray array = root.optJSONArray("records");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    LauncherWidgetRecord record = decodeRecord(array.getJSONObject(i));
                    LauncherWidgetRecord old = loaded.put(record.appWidgetId, record);
                    if (old != null) throw new JSONException("duplicate widget ID");
                }
            }
            WidgetAddTransaction loadedPending = root.has("pending")
                ? decodeTransaction(root.getJSONObject("pending")) : null;
            records = loaded;
            pending = loadedPending;
        } catch (JSONException | IllegalArgumentException ignored) {
            records = new LinkedHashMap<>();
            pending = null;
        }
    }

    private static String encode(Map<Integer, LauncherWidgetRecord> values,
                                 @Nullable WidgetAddTransaction transaction) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            for (LauncherWidgetRecord record : values.values()) array.put(encodeRecord(record));
            root.put("records", array);
            if (transaction != null) root.put("pending", encodeTransaction(transaction));
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to serialize launcher widgets", e);
        }
    }

    private static JSONObject encodeRecord(LauncherWidgetRecord record) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", record.appWidgetId);
        value.put("provider", record.provider.flattenToString());
        value.put("profile", record.profileSerial);
        value.put("state", record.state.name());
        value.put("options", encodeBundle(record.sizeOptions()));
        if (record.lastRenderFailure != null) value.put("failure", record.lastRenderFailure);
        return value;
    }

    private static LauncherWidgetRecord decodeRecord(JSONObject value) throws JSONException {
        ComponentName provider = ComponentName.unflattenFromString(value.getString("provider"));
        if (provider == null) throw new JSONException("invalid provider");
        return new LauncherWidgetRecord(value.getInt("id"), provider, value.getLong("profile"),
            LauncherWidgetRecord.State.valueOf(value.getString("state")),
            decodeBundle(value.optJSONObject("options")), value.optString("failure", null));
    }

    private static JSONObject encodeTransaction(WidgetAddTransaction value) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("token", value.token);
        out.put("id", value.appWidgetId);
        out.put("provider", value.provider.flattenToString());
        out.put("profile", value.profileSerial);
        out.put("stage", value.stage.name());
        out.put("options", encodeBundle(value.requestedOptions()));
        out.put("started", value.startedAtMillis);
        return out;
    }

    private static WidgetAddTransaction decodeTransaction(JSONObject value) throws JSONException {
        ComponentName provider = ComponentName.unflattenFromString(value.getString("provider"));
        if (provider == null) throw new JSONException("invalid provider");
        return new WidgetAddTransaction(value.getString("token"), value.getInt("id"), provider,
            value.getLong("profile"), WidgetAddTransaction.Stage.valueOf(value.getString("stage")),
            decodeBundle(value.optJSONObject("options")), value.getLong("started"));
    }

    private static JSONObject encodeBundle(Bundle bundle) throws JSONException {
        JSONObject out = new JSONObject();
        ArrayList<String> keys = new ArrayList<>(bundle.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object value = bundle.get(key);
            if (value instanceof Integer || value instanceof Long || value instanceof Double
                || value instanceof Boolean || value instanceof String) out.put(key, value);
            else if (value instanceof Float) out.put(key, ((Float) value).doubleValue());
            else if (AppWidgetManager.OPTION_APPWIDGET_SIZES.equals(key)
                && value instanceof ArrayList) {
                JSONArray sizes = encodeSizeList((ArrayList<?>) value);
                if (sizes != null) out.put(key, sizes);
            }
        }
        return out;
    }

    @Nullable
    private static JSONArray encodeSizeList(@NonNull ArrayList<?> values) throws JSONException {
        JSONArray out = new JSONArray();
        for (Object value : values) {
            if (!(value instanceof SizeF)) return null;
            SizeF size = (SizeF) value;
            out.put(new JSONObject().put("width", size.getWidth())
                .put("height", size.getHeight()));
        }
        return out;
    }

    private static Bundle decodeBundle(@Nullable JSONObject value) throws JSONException {
        Bundle out = new Bundle();
        if (value == null) return out;
        java.util.Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object item = value.get(key);
            if (item instanceof Integer) out.putInt(key, (Integer) item);
            else if (item instanceof Long) out.putLong(key, (Long) item);
            else if (item instanceof Double) out.putDouble(key, (Double) item);
            else if (item instanceof Boolean) out.putBoolean(key, (Boolean) item);
            else if (item instanceof String) out.putString(key, (String) item);
            else if (item instanceof JSONArray) {
                JSONArray encodedSizes = (JSONArray) item;
                ArrayList<SizeF> sizes = new ArrayList<>();
                for (int i = 0; i < encodedSizes.length(); i++) {
                    JSONObject size = encodedSizes.getJSONObject(i);
                    sizes.add(new SizeF((float) size.getDouble("width"),
                        (float) size.getDouble("height")));
                }
                out.putParcelableArrayList(key, sizes);
            }
        }
        return out;
    }
}
