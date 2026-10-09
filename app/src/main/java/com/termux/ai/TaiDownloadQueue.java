package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The download queue's rules, as pure functions over the persisted records, so the scheduler in
 * {@link TaiDownloadEngine} is a thin loop around them and the rules can be tested without threads.
 *
 * <p>A record's place in line is its {@code queuedAtMs}: resume puts it at the back, "start now"
 * at the front. {@code startedAtMs} says how long a running transfer has held its slot, which is
 * what "start now" swaps out (the oldest one). Neither field is shown to the user.
 */
public final class TaiDownloadQueue {
    public static final int DEFAULT_PARALLEL = 2;
    public static final int MIN_PARALLEL = 1;
    public static final int MAX_PARALLEL = 3;

    /** The disk is never filled to the last byte: keep 500 MB or 5% of the volume, whichever is more. */
    static final long RESERVE_FLOOR_BYTES = 500L * 1024L * 1024L;
    static final double RESERVE_FRACTION = 0.05;

    private TaiDownloadQueue() {}

    /** The parallel-download setting is 1 to 3; anything else reads as the nearest bound. */
    public static int clampParallel(int requested) {
        return Math.max(MIN_PARALLEL, Math.min(MAX_PARALLEL, requested));
    }

    /** Every {@code queued} record, first in line first. Ties keep the stored order. */
    @NonNull
    public static List<JSONObject> queuedInOrder(@NonNull JSONArray records) {
        List<JSONObject> queued = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item != null && TaiModelStore.STATE_QUEUED.equals(item.optString("status", ""))) queued.add(item);
        }
        Collections.sort(queued, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(queuePosition(a), queuePosition(b));
            }
        });
        return queued;
    }

    /** The queued records the scheduler should start now: as many as free slots allow, in order,
     *  never one that already has a worker. */
    @NonNull
    public static List<JSONObject> nextToStart(@NonNull JSONArray records, @NonNull Set<String> runningIds, int limit) {
        int free = clampParallel(limit) - runningIds.size();
        List<JSONObject> start = new ArrayList<>();
        if (free <= 0) return start;
        for (JSONObject item : queuedInOrder(records)) {
            if (runningIds.contains(item.optString("id", ""))) continue;
            start.add(item);
            if (start.size() >= free) break;
        }
        return start;
    }

    /** A {@code queuedAtMs} that puts a record ahead of every queued one. */
    public static long frontOfQueue(@NonNull JSONArray records) {
        long front = System.currentTimeMillis();
        for (JSONObject item : queuedInOrder(records)) front = Math.min(front, queuePosition(item));
        return front - 1L;
    }

    /** The running record that has held its slot longest, or null when nothing runs. */
    @Nullable
    public static JSONObject oldestRunning(@NonNull JSONArray records, @NonNull Set<String> runningIds) {
        JSONObject oldest = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || !runningIds.contains(item.optString("id", ""))) continue;
            if (oldest == null || item.optLong("startedAtMs", Long.MAX_VALUE) < oldest.optLong("startedAtMs", Long.MAX_VALUE)) {
                oldest = item;
            }
        }
        return oldest;
    }

    /** Records that say a transfer is in flight but have no worker behind them: the process died
     *  under them. A {@code queued} record is not an orphan; nothing had started, so it can wait. */
    @NonNull
    public static List<JSONObject> orphaned(@NonNull JSONArray records, @NonNull Set<String> runningIds) {
        List<JSONObject> orphans = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "");
            boolean inFlight = TaiModelStore.STATE_DOWNLOADING.equals(status) || TaiModelStore.STATE_VERIFYING.equals(status);
            if (inFlight && !runningIds.contains(item.optString("id", ""))) orphans.add(item);
        }
        return orphans;
    }

    /** The engine's private reason for a worker it stopped to make room for a "start now"; the
     *  record is re-queued the moment the worker has written it. Listed here so a process death
     *  in that moment leaves a record auto-resume picks up, not one that waits for a tap. */
    static final String PAUSED_SWAP_OUT = "swap_out";

    /** Paused because the app closed or the network dropped: these continue by themselves on an
     *  unmetered network. A user pause, or a full disk, waits for the person. */
    public static boolean autoResumable(@NonNull JSONObject record) {
        if (!TaiModelStore.STATE_PAUSED.equals(record.optString("status", ""))) return false;
        String reason = record.optString("pausedReason", "");
        return TaiModelStore.PAUSED_APP_CLOSED.equals(reason) || TaiModelStore.PAUSED_NETWORK.equals(reason)
            || PAUSED_SWAP_OUT.equals(reason);
    }

    static long queuePosition(@NonNull JSONObject record) {
        return record.optLong("queuedAtMs", record.optLong("updatedAtMs", 0L));
    }

    // ---- free space ----

    /** The bytes to leave untouched on a volume of {@code totalSpaceBytes}. */
    public static long reserveBytes(long totalSpaceBytes) {
        return Math.max(RESERVE_FLOOR_BYTES, (long) (Math.max(0L, totalSpaceBytes) * RESERVE_FRACTION));
    }

    /** What a free-space check decided, with the numbers a "Needs 3.7 GB, 1.2 GB free" line shows. */
    public static final class SpaceCheck {
        public final boolean fits;
        /** The bytes still to write plus the reserve; what the disk must have free. */
        public final long requiredBytes;
        public final long freeBytes;

        SpaceCheck(boolean fits, long requiredBytes, long freeBytes) {
            this.fits = fits;
            this.requiredBytes = requiredBytes;
            this.freeBytes = freeBytes;
        }
    }

    /**
     * Whether the rest of a transfer fits. {@code expectedTotalBytes} is the file's size when known
     * (0 or less when it is not, in which case only the reserve is asked for), and
     * {@code alreadyOnDiskBytes} is what the partial file already holds, which does not need
     * space a second time.
     */
    @NonNull
    public static SpaceCheck checkSpace(long usableBytes, long totalSpaceBytes, long expectedTotalBytes, long alreadyOnDiskBytes) {
        long remaining = expectedTotalBytes > 0L ? Math.max(0L, expectedTotalBytes - Math.max(0L, alreadyOnDiskBytes)) : 0L;
        long required = remaining + reserveBytes(totalSpaceBytes);
        return new SpaceCheck(usableBytes >= required, required, Math.max(0L, usableBytes));
    }
}
