package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The learned {@link TapModel}s, one per layout and rendered geometry, and their file form.
 *
 * <p>Holds only per-key aggregates: a tap count and two summed offsets. Never a key sequence,
 * never a character. Bounded to {@link #MAX_ENTRIES} geometries; the least recently used one
 * is dropped first. Reading and serialising happen on the caller's thread; the caller decides
 * where the file write runs.
 */
public final class TapModelStore {

    static final int MAX_ENTRIES = 8;
    static final int VERSION = 1;

    private static final class Entry {
        final TapModel model;
        long lastUsed;

        Entry(TapModel model, long lastUsed) {
            this.model = model;
            this.lastUsed = lastUsed;
        }
    }

    private final Map<String, Entry> mEntries = new LinkedHashMap<>();
    private boolean mDirty;

    public TapModelStore() {
    }

    /** Loads the store from {@code file}; a missing or unreadable file yields an empty store. */
    @NonNull
    public static TapModelStore load(@Nullable File file) {
        TapModelStore store = new TapModelStore();
        if (file == null || !file.isFile())
            return store;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = readAll(in);
            store.readJson(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException ignored) {
            // A damaged file is not worth a crash on a key press; start over.
            store.mEntries.clear();
        }
        store.mDirty = false;
        return store;
    }

    /**
     * The live model for {@code key}, created fresh when absent or when the stored model was
     * learned on a grid with a different number of keys.
     */
    @NonNull
    public TapModel modelFor(@NonNull String key, int keyCount, long now) {
        Entry entry = mEntries.get(key);
        if (entry == null || entry.model.keyCount() != keyCount) {
            entry = new Entry(new TapModel(keyCount), now);
            mEntries.put(key, entry);
            mDirty = true;
            evict();
        } else if (entry.lastUsed != now) {
            entry.lastUsed = now;
        }
        return entry.model;
    }

    public int entryCount() {
        return mEntries.size();
    }

    /** Total taps learned across every stored geometry. */
    public float totalTaps() {
        float total = 0f;
        for (Entry entry : mEntries.values()) total += entry.model.totalTaps();
        return total;
    }

    public boolean isDirty() {
        return mDirty;
    }

    public void markDirty() {
        mDirty = true;
    }

    public void clear() {
        mEntries.clear();
        mDirty = true;
    }

    /** Serialises the store; call on the thread that owns the models. Clears the dirty flag. */
    @NonNull
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("v", VERSION);
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, Entry> e : mEntries.entrySet()) {
                TapModel model = e.getValue().model;
                if (model.isEmpty())
                    continue;
                JSONObject entry = new JSONObject();
                entry.put("t", e.getValue().lastUsed);
                entry.put("n", toArray(model.counts()));
                entry.put("x", toArray(model.sumX()));
                entry.put("y", toArray(model.sumY()));
                entries.put(e.getKey(), entry);
            }
            root.put("entries", entries);
            mDirty = false;
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes {@code json} to {@code file} atomically. Safe to call off the main thread. */
    public static void write(@NonNull File file, @NonNull String json) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("cannot create " + parent);
        File tmp = new File(parent, file.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(file)) {
            if (!file.delete() || !tmp.renameTo(file))
                throw new IOException("cannot replace " + file);
        }
    }

    /** Removes the file; the store's in-memory contents are untouched. */
    public static boolean delete(@Nullable File file) {
        return file != null && (!file.exists() || file.delete());
    }

    private void readJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("v", -1) != VERSION)
            return;
        JSONObject entries = root.optJSONObject("entries");
        if (entries == null)
            return;
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = entries.getJSONObject(key);
            float[] n = toFloats(entry.getJSONArray("n"));
            float[] x = toFloats(entry.getJSONArray("x"));
            float[] y = toFloats(entry.getJSONArray("y"));
            if (n.length == 0 || n.length != x.length || n.length != y.length)
                continue;
            mEntries.put(key, new Entry(new TapModel(n, x, y), entry.optLong("t", 0L)));
        }
        evict();
    }

    private void evict() {
        while (mEntries.size() > MAX_ENTRIES) {
            String oldest = null;
            long oldestUsed = Long.MAX_VALUE;
            for (Map.Entry<String, Entry> e : mEntries.entrySet()) {
                if (e.getValue().lastUsed < oldestUsed) {
                    oldestUsed = e.getValue().lastUsed;
                    oldest = e.getKey();
                }
            }
            mEntries.remove(oldest);
            mDirty = true;
        }
    }

    private static JSONArray toArray(float[] values) throws JSONException {
        JSONArray array = new JSONArray();
        for (float v : values) array.put((double) v);
        return array;
    }

    private static float[] toFloats(JSONArray array) throws JSONException {
        float[] out = new float[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.getDouble(i);
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        int total = 0;
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) > 0) {
            byte[] chunk = new byte[read];
            System.arraycopy(buf, 0, chunk, 0, read);
            chunks.add(chunk);
            total += read;
            if (total > 4 * 1024 * 1024)
                throw new IOException("tap model file too large");
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, out, pos, chunk.length);
            pos += chunk.length;
        }
        return out;
    }
}
