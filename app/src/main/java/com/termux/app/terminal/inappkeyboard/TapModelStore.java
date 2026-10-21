package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.touch.GridMemory;
import com.termux.app.terminal.inappkeyboard.touch.KeyLog;
import com.termux.app.terminal.inappkeyboard.touch.LearningGate;
import com.termux.app.terminal.inappkeyboard.touch.PassBarCounts;
import com.termux.app.terminal.inappkeyboard.touch.TapModel;
import com.termux.app.terminal.inappkeyboard.touch.TouchEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The touch engine's state and what it learned, one {@link GridMemory} per layout and rendered
 * geometry, and their file form.
 *
 * <p>Holds per-key aggregates and per-key unordered buckets: never a key sequence, never a
 * character. Bounded to {@link #MAX_ENTRIES} geometries; the least recently used one is dropped
 * first. Reading and serialising happen on the caller's thread; the caller decides where the
 * file write runs.
 *
 * <p>An older version's file is dropped on load and overwritten by the first save. Version 2
 * changed which keys count, and with it every geometry signature. Version 3 changed what a
 * take-back is (a late retype after up to 8 Backspaces), stores vertical offsets in key heights,
 * names grids by key structure rather than pixel size and counts take-backs by the key hit, so
 * the warm-up and its baseline start again under the new rule.
 */
public final class TapModelStore {

    static final int MAX_ENTRIES = 8;
    static final int VERSION = 3;

    private static final class Entry {
        final GridMemory grid;
        long lastUsed;

        Entry(GridMemory grid, long lastUsed) {
            this.grid = grid;
            this.lastUsed = lastUsed;
        }
    }

    private final Map<String, Entry> mEntries = new LinkedHashMap<>();
    private TouchEngine mEngine = new TouchEngine();
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
            store.mEngine = new TouchEngine();
        }
        store.mDirty = false;
        return store;
    }

    @NonNull
    public TouchEngine engine() {
        return mEngine;
    }

    /**
     * The live memory for {@code key}, created fresh when absent or when the stored one was
     * learned on a grid with a different number of keys.
     */
    @NonNull
    public GridMemory gridFor(@NonNull String key, int keyCount, long now) {
        Entry entry = mEntries.get(key);
        if (entry == null || entry.grid.keyCount() != keyCount) {
            entry = new Entry(new GridMemory(keyCount), now);
            mEntries.put(key, entry);
            mDirty = true;
            evict();
        } else if (entry.lastUsed != now) {
            entry.lastUsed = now;
        }
        return entry.grid;
    }

    public int entryCount() {
        return mEntries.size();
    }

    /** Total weight learned across every stored geometry, after forgetting. */
    public float totalTaps() {
        float total = 0f;
        for (Entry entry : mEntries.values()) total += entry.grid.model().totalTaps();
        return total;
    }

    /** Taps learned since the last reset, for Settings. */
    public long tapsLearned() {
        return mEngine.tapsLearned();
    }

    /** One of {@link LearningGate}'s states. */
    public int state() {
        return mEngine.gate().state();
    }

    public boolean isDirty() {
        return mDirty;
    }

    public void markDirty() {
        mDirty = true;
    }

    /** Forgets everything: what was learned and the engine's state. */
    public void clear() {
        mEntries.clear();
        mEngine = new TouchEngine();
        mDirty = true;
    }

    /** Serialises the store; call on the thread that owns it. Clears the dirty flag. */
    @NonNull
    public String toJson() {
        return snapshot().toJson();
    }

    /**
     * Copies everything worth saving, for {@link Snapshot#toJson()} to serialise on another
     * thread; call on the thread that owns the store. Only array copies happen here, a few tens
     * of kilobytes. Clears the dirty flag.
     */
    @NonNull
    public Snapshot snapshot() {
        List<String> keys = new ArrayList<>();
        List<Long> lastUsed = new ArrayList<>();
        List<GridMemory> grids = new ArrayList<>();
        for (Map.Entry<String, Entry> e : mEntries.entrySet()) {
            GridMemory grid = e.getValue().grid;
            if (grid.isEmpty())
                continue;
            keys.add(e.getKey());
            lastUsed.add(e.getValue().lastUsed);
            grids.add(copyOf(grid, mEngine.epoch()));
        }
        mDirty = false;
        return new Snapshot(copyOf(mEngine), keys, lastUsed, grids);
    }

    /** A copy of the store taken on its owning thread, safe to serialise anywhere. */
    public static final class Snapshot {
        private final TouchEngine mEngine;
        private final List<String> mKeys;
        private final List<Long> mLastUsed;
        private final List<GridMemory> mGrids;

        private Snapshot(TouchEngine engine, List<String> keys, List<Long> lastUsed,
                         List<GridMemory> grids) {
            mEngine = engine;
            mKeys = keys;
            mLastUsed = lastUsed;
            mGrids = grids;
        }

        /** The file form. Safe off the main thread: nothing here is shared with the store. */
        @NonNull
        public String toJson() {
            return serialise(mEngine, mKeys, mLastUsed, mGrids);
        }
    }

    private static TouchEngine copyOf(TouchEngine engine) {
        LearningGate gate = engine.gate();
        LearningGate gateCopy = new LearningGate(gate.state(), gate.warmUpTaps(),
            gate.warmUpTakeBacks(), gate.baseline(), gate.window(), gate.windowNext(),
            gate.windowFill());
        return new TouchEngine(gateCopy, engine.tapsLearned(), engine.switchedOn(),
            engine.epoch());
    }

    private static GridMemory copyOf(GridMemory grid, int epoch) {
        TapModel model = grid.model();
        KeyLog log = grid.log();
        PassBarCounts counts = grid.counts();
        // Counts from an earlier warm-up are cleared, never written.
        counts.sync(epoch);
        return new GridMemory(new TapModel(model.counts(), model.sumX(), model.sumY()),
            new KeyLog(log.keyCount(), log.samples(), log.sampleFill(), log.takeBacks(),
                log.takeBackFill()),
            new PassBarCounts(model.keyCount(), counts.epoch(), counts.warmTaps(),
                counts.activeTaps(), counts.warmTakeBacks(), counts.activeTakeBacks(),
                counts.warmTakeBacksHit(), counts.activeTakeBacksHit()));
    }

    private static String serialise(TouchEngine engine, List<String> keys, List<Long> lastUsed,
                                    List<GridMemory> grids) {
        try {
            JSONObject root = new JSONObject();
            root.put("v", VERSION);
            root.put("engine", engineJson(engine));
            JSONObject entries = new JSONObject();
            for (int i = 0; i < grids.size(); i++) {
                GridMemory grid = grids.get(i);
                TapModel model = grid.model();
                KeyLog log = grid.log();
                PassBarCounts counts = grid.counts();
                JSONObject entry = new JSONObject();
                entry.put("t", (long) lastUsed.get(i));
                entry.put("n", toArray(model.counts()));
                entry.put("x", toArray(model.sumX()));
                entry.put("y", toArray(model.sumY()));
                entry.put("ls", encode(log.samples()));
                entry.put("lf", encode(log.sampleFill()));
                entry.put("tb", encode(log.takeBacks()));
                entry.put("tf", encode(log.takeBackFill()));
                entry.put("count_epoch", counts.epoch());
                entry.put("taps_warm", toArray(counts.warmTaps()));
                entry.put("taps_active", toArray(counts.activeTaps()));
                entry.put("takebacks_warm", toArray(counts.warmTakeBacks()));
                entry.put("takebacks_active", toArray(counts.activeTakeBacks()));
                entry.put("takebacks_hit_warm", toArray(counts.warmTakeBacksHit()));
                entry.put("takebacks_hit_active", toArray(counts.activeTakeBacksHit()));
                entries.put(keys.get(i), entry);
            }
            root.put("entries", entries);
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

    private static JSONObject engineJson(TouchEngine engine) throws JSONException {
        LearningGate gate = engine.gate();
        JSONObject json = new JSONObject();
        json.put("on", engine.switchedOn());
        json.put("learned", engine.tapsLearned());
        json.put("epoch", engine.epoch());
        json.put("state", gate.state());
        json.put("wt", gate.warmUpTaps());
        json.put("wb", gate.warmUpTakeBacks());
        json.put("base", (double) gate.baseline());
        JSONArray window = new JSONArray();
        for (long word : gate.window()) window.put(word);
        json.put("win", window);
        json.put("wn", gate.windowNext());
        json.put("wf", gate.windowFill());
        return json;
    }

    private static TouchEngine readEngine(@Nullable JSONObject json) throws JSONException {
        if (json == null)
            return new TouchEngine();
        JSONArray array = json.optJSONArray("win");
        long[] window = new long[array == null ? 0 : array.length()];
        for (int i = 0; i < window.length; i++) window[i] = array.getLong(i);
        LearningGate gate = new LearningGate(json.optInt("state", -1), json.optInt("wt", -1),
            json.optInt("wb", -1), (float) json.optDouble("base", -1d), window,
            json.optInt("wn", -1), json.optInt("wf", -1));
        return new TouchEngine(gate, json.optLong("learned", 0L), json.optBoolean("on", false),
            json.optInt("epoch", 0));
    }

    private void readJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("v", -1) != VERSION)
            return;
        mEngine = readEngine(root.optJSONObject("engine"));
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
            KeyLog log = new KeyLog(n.length, decode(entry.optString("ls", null)),
                decode(entry.optString("lf", null)), decode(entry.optString("tb", null)),
                decode(entry.optString("tf", null)));
            PassBarCounts counts = new PassBarCounts(n.length, entry.optInt("count_epoch", 0),
                toInts(entry.optJSONArray("taps_warm")),
                toInts(entry.optJSONArray("taps_active")),
                toInts(entry.optJSONArray("takebacks_warm")),
                toInts(entry.optJSONArray("takebacks_active")),
                toInts(entry.optJSONArray("takebacks_hit_warm")),
                toInts(entry.optJSONArray("takebacks_hit_active")));
            mEntries.put(key, new Entry(new GridMemory(new TapModel(n, x, y), log, counts),
                entry.optLong("t", 0L)));
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

    /** Shorts as big-endian bytes in Base64, far smaller and faster than a JSON array. */
    private static String encode(short[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 2);
        for (short v : values) buffer.putShort(v);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    @Nullable
    private static short[] decode(@Nullable String text) {
        if (text == null)
            return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length % 2 != 0)
            return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short[] out = new short[bytes.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = buffer.getShort();
        return out;
    }

    private static JSONArray toArray(float[] values) throws JSONException {
        JSONArray array = new JSONArray();
        for (float v : values) array.put((double) v);
        return array;
    }

    private static JSONArray toArray(int[] values) {
        JSONArray array = new JSONArray();
        for (int v : values) array.put(v);
        return array;
    }

    @Nullable
    private static int[] toInts(@Nullable JSONArray array) throws JSONException {
        if (array == null)
            return null;
        int[] out = new int[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.getInt(i);
        return out;
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
