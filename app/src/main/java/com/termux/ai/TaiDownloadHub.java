package com.termux.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What every screen that shows downloads subscribes to: one snapshot of all records, pushed on
 * the main thread whenever something moves, instead of each screen polling preferences.
 *
 * <p>The persisted records in {@link TaiModelStore} stay the truth (the API, the CLI and a
 * restart read them); this overlays the live byte counter the workers report between writes, a
 * smoothed rate and an ETA per item. Emissions are held to about five a second per item: a
 * worker reports every chunk, a bar cannot show more than that, and a busier listener would only
 * burn the main thread. A status change always goes out at once.
 */
public final class TaiDownloadHub {
    /** Emissions for one item are at least this far apart unless its status changed. */
    static final long MIN_EMIT_INTERVAL_MS = 200L;

    public interface Listener {
        /** All download records, on the main thread, oldest first. */
        void onDownloadsChanged(@NonNull List<Snapshot> downloads);
    }

    /** One record as a screen wants it: the persisted fields plus the live rate. */
    public static final class Snapshot {
        public final String id;
        public final String modelId;
        public final String displayName;
        /** One of the {@code TaiModelStore.STATE_*} values. */
        public final String status;
        /** One of the {@code TaiModelStore.PAUSED_*} values when {@link #status} is paused, else "". */
        public final String pausedReason;
        public final String error;
        public final long bytesRead;
        /** The full size when known, else -1 or 0. */
        public final long totalBytes;
        /** Smoothed; 0 while nothing moves. */
        public final double bytesPerSecond;
        /** Seconds left at the current rate, -1 when unknown. */
        public final long etaSeconds;
        /** The sidecar or package file in flight, or "". */
        public final String currentFile;
        /** Bytes hashed so far while verifying. */
        public final long verifiedBytes;
        /** For a no_space pause: what the disk must have free, and what it has. */
        public final long requiredBytes;
        public final long freeBytes;
        public final LinkedHashSet<String> capabilities;
        public final long updatedAtMs;
        /** 1-based place among queued items; 0 when not queued. */
        public final int queuePosition;
        /** The persisted record, for fields a screen needs that are not lifted here. */
        public final JSONObject record;

        Snapshot(@NonNull JSONObject record, double bytesPerSecond, long etaSeconds, int queuePosition) {
            this.record = record;
            this.id = record.optString("id", "");
            this.modelId = record.optString("modelId", "");
            String name = record.optString("displayName", "");
            this.displayName = name.isEmpty() ? modelId : name;
            this.status = record.optString("status", "");
            this.pausedReason = record.optString("pausedReason", "");
            this.error = record.optString("error", "");
            this.bytesRead = record.optLong("bytesRead", 0L);
            this.totalBytes = record.optLong("totalBytes", -1L);
            this.bytesPerSecond = bytesPerSecond;
            this.etaSeconds = etaSeconds;
            this.currentFile = record.optString("currentFile", "");
            this.verifiedBytes = record.optLong("verifiedBytes", 0L);
            this.requiredBytes = record.optLong("requiredBytes", 0L);
            this.freeBytes = record.optLong("freeBytes", 0L);
            this.updatedAtMs = record.optLong("updatedAtMs", 0L);
            this.queuePosition = queuePosition;
            LinkedHashSet<String> caps = new LinkedHashSet<>();
            JSONArray array = record.optJSONArray("capabilities");
            if (array != null) for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) caps.add(value);
            }
            this.capabilities = caps;
        }

        public boolean isLive() {
            return TaiModelStore.isLiveDownloadState(status);
        }

        public boolean isPaused() {
            return TaiModelStore.STATE_PAUSED.equals(status);
        }

        public boolean isSpeech() {
            return capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        }
    }

    private static volatile TaiDownloadHub instance;

    private final Context appContext;
    private final TaiModelStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Live records by transfer id, newer than what preferences hold between 1 MB writes. */
    private final Map<String, JSONObject> live = new HashMap<>();
    private final Map<String, TaiDownloadRate> rates = new HashMap<>();
    private final Map<String, Long> lastEmitMs = new HashMap<>();
    private boolean emitScheduled;
    private final Runnable emitRunnable = this::emitNow;

    private TaiDownloadHub(@NonNull Context context) {
        appContext = context.getApplicationContext();
        store = new TaiModelStore(appContext);
    }

    @NonNull
    public static TaiDownloadHub get(@NonNull Context context) {
        Context application = context.getApplicationContext();
        TaiDownloadHub hub = instance;
        if (hub == null || hub.appContext != application) {
            synchronized (TaiDownloadHub.class) {
                hub = instance;
                // The Application only changes between tests; a hub over a dead one reads the
                // wrong preferences.
                if (hub == null || hub.appContext != application) instance = hub = new TaiDownloadHub(context);
            }
        }
        return hub;
    }

    /** Forgets the process singleton so a test gets a fresh hub over a fresh application. */
    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTesting() {
        instance = null;
    }

    /** Subscribes and, on the main thread, hands the listener the current snapshot right away. */
    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
        mainHandler.post(() -> {
            if (listeners.contains(listener)) listener.onDownloadsChanged(snapshot());
        });
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** All records as of now, oldest first. Safe from any thread. */
    @NonNull
    public List<Snapshot> snapshot() {
        JSONArray stored = store.getDownloads();
        List<JSONObject> records = new ArrayList<>();
        synchronized (this) {
            for (int i = 0; i < stored.length(); i++) {
                JSONObject item = stored.optJSONObject(i);
                if (item == null) continue;
                JSONObject fresh = live.get(item.optString("id", ""));
                // The live copy wins only while it is newer; after a restart the stored record is
                // what the reconcile wrote and the stale in-memory one (if any) must not undo it.
                records.add(fresh != null && fresh.optLong("updatedAtMs", 0L) >= item.optLong("updatedAtMs", 0L) ? fresh : item);
            }
            // Records dropped from the store (a deleted model) are dropped here too.
            List<String> gone = new ArrayList<>(live.keySet());
            for (JSONObject item : records) gone.remove(item.optString("id", ""));
            for (String id : gone) {
                live.remove(id);
                rates.remove(id);
                lastEmitMs.remove(id);
            }
            List<Snapshot> snapshots = new ArrayList<>(records.size());
            Map<String, Integer> positions = new HashMap<>();
            JSONArray ordered = new JSONArray();
            for (JSONObject item : records) ordered.put(item);
            int position = 1;
            for (JSONObject queued : TaiDownloadQueue.queuedInOrder(ordered)) positions.put(queued.optString("id", ""), position++);
            for (JSONObject item : records) {
                String id = item.optString("id", "");
                TaiDownloadRate rate = rates.get(id);
                double speed = 0.0;
                long eta = -1L;
                if (rate != null && TaiModelStore.STATE_DOWNLOADING.equals(item.optString("status", ""))) {
                    speed = rate.bytesPerSecond();
                    long total = item.optLong("totalBytes", -1L);
                    eta = total > 0L ? rate.etaSeconds(total - item.optLong("bytesRead", 0L)) : -1L;
                }
                Integer place = positions.get(id);
                snapshots.add(new Snapshot(item, speed, eta, place == null ? 0 : place));
            }
            return Collections.unmodifiableList(snapshots);
        }
    }

    /** Feeds one record as a worker (or the scheduler) has it now. Any thread. */
    public void publish(@NonNull JSONObject record) {
        String id = record.optString("id", "");
        if (id.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        boolean immediate;
        synchronized (this) {
            JSONObject previous = live.get(id);
            String status = record.optString("status", "");
            boolean statusChanged = previous == null || !status.equals(previous.optString("status", ""));
            live.put(id, record);
            TaiDownloadRate rate = rates.get(id);
            if (rate == null) rates.put(id, rate = new TaiDownloadRate());
            if (TaiModelStore.STATE_DOWNLOADING.equals(status)) {
                if (statusChanged) rate.reset();
                rate.update(record.optLong("bytesRead", 0L), now);
            } else {
                rate.reset();
            }
            Long last = lastEmitMs.get(id);
            immediate = statusChanged || last == null || now - last >= MIN_EMIT_INTERVAL_MS;
            if (immediate) lastEmitMs.put(id, now);
            if (emitScheduled) return;
            emitScheduled = true;
        }
        if (immediate) mainHandler.post(emitRunnable);
        else mainHandler.postDelayed(emitRunnable, MIN_EMIT_INTERVAL_MS);
    }

    /** Re-reads the store and tells listeners; for changes made to records behind the hub's back
     *  (a model deleted, a record dropped). Any thread. */
    public void refresh() {
        synchronized (this) {
            if (emitScheduled) return;
            emitScheduled = true;
        }
        mainHandler.post(emitRunnable);
    }

    private void emitNow() {
        synchronized (this) {
            emitScheduled = false;
        }
        if (listeners.isEmpty()) return;
        List<Snapshot> snapshots = snapshot();
        for (Listener listener : listeners) listener.onDownloadsChanged(snapshots);
    }

    /** The smoothed rate for one transfer, for callers outside a listener (the notification). */
    public double bytesPerSecond(@Nullable String transferId) {
        if (transferId == null) return 0.0;
        synchronized (this) {
            TaiDownloadRate rate = rates.get(transferId);
            return rate == null ? 0.0 : rate.bytesPerSecond();
        }
    }
}
