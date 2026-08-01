package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** Animation frames have their own larger quota, mirroring kitty's separate frame quota. */
    static final long MAX_FRAME_BYTES = 64L * 1024 * 1024;
    static final int MAX_FRAMES_PER_IMAGE = 512;
    /** The gap kitty gives a transmitted frame that did not specify one. */
    static final int DEFAULT_FRAME_GAP_MS = 40;

    /** Animation states, matching the protocol's {@code s} values: 1 stop, 2 loading, 3 running. */
    static final int ANIMATION_STOPPED = 1;
    static final int ANIMATION_LOADING = 2;
    static final int ANIMATION_RUNNING = 3;

    /** One extra animation frame; the root frame's pixels are the entry's own bitmap. */
    static final class Frame {
        Bitmap bitmap;
        int gapMs;
        int byteCount;

        Frame(Bitmap bitmap, int gapMs, int byteCount) {
            this.bitmap = bitmap;
            this.gapMs = gapMs;
            this.byteCount = byteCount;
        }
    }

    static final class Entry {
        final long id;
        final long number;
        final int width;
        final int height;
        /** Null while the decode is in flight or after it failed. */
        Bitmap bitmap;
        boolean completed;
        int byteCount;

        /** Extra animation frames; index 0 is protocol frame 2. */
        final List<Frame> frames = new ArrayList<>();
        /** The root frame's gap; the protocol sets it via {@code a=a,r=1,z=...}. */
        int rootGapMs;
        int animationState;
        /** 0-based index of the displayed frame; 0 is the root frame. */
        int currentFrame;
        int currentLoop;
        /** 0 loops forever; set from {@code v} as {@code v - 1}, matching kitty. */
        int maxLoops;
        /** Uptime when the current frame became current, the base for the next flip deadline. */
        long frameShownAtUptime;
        /** Sum of all frame gaps; an all-gapless animation must never spin. */
        long animationDurationMs;

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
        for (Frame frame : removed.frames) totalFrameBytes -= frame.byteCount;
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
        totalFrameBytes = 0;
    }

    long totalBytes() {
        return totalBytes;
    }

    int count() {
        return images.size();
    }

    // ------------------------------------------------------------------ animation frames

    private long totalFrameBytes;

    /** Frame count including the root frame, so protocol frame numbers are 1..frameCount. */
    static int frameCount(Entry entry) {
        return 1 + entry.frames.size();
    }

    /** The pixels of 1-based protocol frame {@code number}, or null while its decode is in flight. */
    static Bitmap frameBitmap(Entry entry, int number) {
        if (number == 1) return entry.bitmap;
        return entry.frames.get(number - 2).bitmap;
    }

    static int frameGap(Entry entry, int number) {
        if (number == 1) return entry.rootGapMs;
        return entry.frames.get(number - 2).gapMs;
    }

    static void setFrameGap(Entry entry, int number, int gapMs) {
        gapMs = Math.max(0, gapMs);
        int previous;
        if (number == 1) {
            previous = entry.rootGapMs;
            entry.rootGapMs = gapMs;
        } else {
            Frame frame = entry.frames.get(number - 2);
            previous = frame.gapMs;
            frame.gapMs = gapMs;
        }
        entry.animationDurationMs += gapMs - previous;
    }

    boolean wouldExceedFrameLimits(Entry entry, int byteCount) {
        return totalFrameBytes + byteCount > MAX_FRAME_BYTES
            || entry.frames.size() + 1 > MAX_FRAMES_PER_IMAGE;
    }

    void addFrame(Entry entry, Bitmap bitmap, int byteCount, int gapMs) {
        gapMs = Math.max(0, gapMs);
        entry.frames.add(new Frame(bitmap, gapMs, byteCount));
        entry.animationDurationMs += gapMs;
        totalFrameBytes += byteCount;
    }

    /** Replace the pixels of 1-based frame {@code number} after an edit or composition. */
    void replaceFrameBitmap(Entry entry, int number, Bitmap bitmap, int byteCount) {
        if (number == 1) {
            totalBytes += byteCount - entry.byteCount;
            entry.bitmap = bitmap;
            entry.byteCount = byteCount;
            return;
        }
        Frame frame = entry.frames.get(number - 2);
        totalFrameBytes += byteCount - frame.byteCount;
        frame.bitmap = bitmap;
        frame.byteCount = byteCount;
    }

    /**
     * Delete 1-based frame {@code number}, kitty's {@code d=f} semantics: deleting the root
     * promotes frame 2 to root, and the current-frame index follows the surviving frames.
     * Returns false when the image has no extra frames to delete.
     */
    boolean removeFrame(Entry entry, int number) {
        if (entry.frames.isEmpty()) return false;
        number = Math.min(Math.max(1, number), frameCount(entry));
        int removedGap;
        if (number == 1) {
            Frame promoted = entry.frames.remove(0);
            removedGap = entry.rootGapMs;
            // The promoted frame's bytes move from the frame quota to the image quota.
            totalFrameBytes -= promoted.byteCount;
            totalBytes += promoted.byteCount - entry.byteCount;
            entry.bitmap = promoted.bitmap;
            entry.byteCount = promoted.byteCount;
            entry.rootGapMs = promoted.gapMs;
        } else {
            Frame removed = entry.frames.remove(number - 2);
            removedGap = removed.gapMs;
            totalFrameBytes -= removed.byteCount;
        }
        entry.animationDurationMs = Math.max(0, entry.animationDurationMs - removedGap);
        int removedIndex = number - 1;
        if (entry.currentFrame > entry.frames.size()) {
            entry.currentFrame = entry.frames.size();
        } else if (removedIndex < entry.currentFrame) {
            entry.currentFrame--;
        }
        return true;
    }

    /** Whether this entry can advance at all, kitty's {@code image_is_animatable}. */
    static boolean isAnimatable(Entry entry) {
        return (entry.animationState == ANIMATION_LOADING || entry.animationState == ANIMATION_RUNNING)
            && !entry.frames.isEmpty()
            && entry.animationDurationMs > 0
            && (entry.maxLoops == 0 || entry.currentLoop < entry.maxLoops);
    }

    /**
     * Advance a running animation to the frame due at {@code now}, skipping gapless and
     * still-decoding frames, counting loops, and waiting at the end in loading mode — a port of
     * kitty's {@code scan_active_animations} for one image. Returns whether the frame changed.
     */
    static boolean advanceAnimation(Entry entry, long now) {
        if (!isAnimatable(entry)) return false;
        if (now < entry.frameShownAtUptime + frameGap(entry, entry.currentFrame + 1)) return false;
        int count = frameCount(entry);
        int guard = count;
        int index = entry.currentFrame;
        do {
            int next = (index + 1) % count;
            if (next == 0) {
                if (entry.animationState == ANIMATION_LOADING) return false;
                if (entry.maxLoops != 0 && ++entry.currentLoop >= entry.maxLoops) return false;
            }
            index = next;
            if (--guard < 0) return false;
        } while (frameGap(entry, index + 1) == 0);
        if (index == entry.currentFrame) {
            entry.frameShownAtUptime = now;
            return false;
        }
        entry.currentFrame = index;
        entry.frameShownAtUptime = now;
        return true;
    }

    /** The uptime the entry's next flip is due at, or -1 when it will not animate on its own. */
    static long nextAnimationDeadline(Entry entry) {
        if (!isAnimatable(entry)) return -1;
        // At the last frame in loading mode the animation waits for more frames rather than
        // looping; scheduling a wake-up for it would spin. Adding a frame re-arms the scheduler.
        if (entry.animationState == ANIMATION_LOADING && entry.currentFrame == frameCount(entry) - 1)
            return -1;
        return entry.frameShownAtUptime + frameGap(entry, entry.currentFrame + 1);
    }

    /** All stored entries, for the animation scheduler's scan. */
    Iterable<Entry> entries() {
        return images.values();
    }

    long totalFrameBytes() {
        return totalFrameBytes;
    }
}
