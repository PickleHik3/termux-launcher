package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The kitty graphics Tier-2 image store: decoded images kept by client-chosen id so later
 * {@code a=p} commands can place them without retransmission.
 *
 * <p>All methods run on the terminal's serialized update thread. An image is {@link #reserve}d
 * synchronously when its transmission completes — that fixes its id, number, dimensions, and byte
 * budget while the pixel decode still runs on the worker — and {@link #complete}d when the decoded
 * bitmap arrives. A placement command later in the stream can therefore resolve the image and its
 * dimensions synchronously even though the pixels may still be in flight.</p>
 *
 * <p>The store is bounded in both image count and decoded bytes, and a full store answers
 * {@code ENOSPC} rather than evicting: escape sequences are untrusted input, and a client that
 * wants space back has the delete forms. Byte accounting uses explicit counts so the bookkeeping
 * is testable on the JVM, where {@link Bitmap} methods return defaults.</p>
 */
final class KittyImageStore {

    static final long MAX_STORED_BYTES = 32L * 1024 * 1024;
    static final int MAX_STORED_IMAGES = 256;

    static final class Entry {
        final long id;
        final long number;
        final int width;
        final int height;
        /** Null while the decode is in flight or after it failed. */
        Bitmap bitmap;
        boolean completed;
        int byteCount;

        Entry(long id, long number, int width, int height, int byteCount) {
            this.id = id;
            this.number = number;
            this.width = width;
            this.height = height;
            this.byteCount = byteCount;
        }
    }

    private final Map<Long, Entry> images = new LinkedHashMap<>();
    private final Map<Long, Long> latestIdByNumber = new HashMap<>();
    private long totalBytes;

    /** The id an {@code i=}/{@code I=} pair refers to, or 0 when it resolves to nothing. */
    long resolveId(long imageId, long number) {
        if (imageId != 0) return imageId;
        if (number != 0) {
            Long latest = latestIdByNumber.get(number);
            if (latest != null) return latest;
        }
        return 0;
    }

    /** The smallest positive id not currently stored, for {@code I=} transmissions without {@code i=}. */
    long assignFreeId() {
        long candidate = 1;
        while (images.containsKey(candidate)) candidate++;
        return candidate;
    }

    boolean wouldExceedLimits(long id, int byteCount) {
        Entry replaced = images.get(id);
        long newTotal = totalBytes - (replaced == null ? 0 : replaced.byteCount) + byteCount;
        int newCount = images.size() + (replaced == null ? 1 : 0);
        return newTotal > MAX_STORED_BYTES || newCount > MAX_STORED_IMAGES;
    }

    /** Record an accepted transmission before its decode finishes, replacing any previous image with this id. */
    void reserve(long id, long number, int width, int height, int byteCount) {
        remove(id);
        images.put(id, new Entry(id, number, width, height, byteCount));
        if (number != 0) latestIdByNumber.put(number, id);
        totalBytes += byteCount;
    }

    /**
     * Attach the decoded bitmap to its reservation. Returns false when the reservation is gone —
     * deleted or replaced while the decode ran — in which case the caller still owns the bitmap.
     */
    boolean complete(long id, Bitmap bitmap, int byteCount) {
        Entry entry = images.get(id);
        if (entry == null || entry.completed) return false;
        entry.bitmap = bitmap;
        entry.completed = true;
        totalBytes += byteCount - entry.byteCount;
        entry.byteCount = byteCount;
        return true;
    }

    /** Drop a failed transmission's reservation so its budget is released. */
    void abandon(long id) {
        Entry entry = images.get(id);
        if (entry != null && !entry.completed) remove(id);
    }

    Entry get(long id) {
        return images.get(id);
    }

    void remove(long id) {
        Entry removed = images.remove(id);
        if (removed == null) return;
        totalBytes -= removed.byteCount;
        // Stored bitmaps are dropped, never recycled: a placement rasterization on the decode
        // worker may still be reading one, and the garbage collector reclaims them safely.
        if (removed.number != 0 && Long.valueOf(id).equals(latestIdByNumber.get(removed.number))) {
            latestIdByNumber.remove(removed.number);
            // An older image with the same number becomes the latest again.
            for (Entry entry : images.values()) {
                if (entry.number == removed.number) latestIdByNumber.put(removed.number, entry.id);
            }
        }
    }

    void clear() {
        images.clear();
        latestIdByNumber.clear();
        totalBytes = 0;
    }

    long totalBytes() {
        return totalBytes;
    }

    int count() {
        return images.size();
    }
}
