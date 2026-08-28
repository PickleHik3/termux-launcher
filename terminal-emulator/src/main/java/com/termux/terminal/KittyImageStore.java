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
 * wants space back has the delete forms. Animation frames are the one exception — see
 * {@link #reclaimFrameBudget} — because they are the only content whose loss degrades an image
 * instead of removing it. Byte accounting uses explicit counts so the bookkeeping is testable on
 * the JVM, where {@link Bitmap} methods return defaults.</p>
 */
final class KittyImageStore {

    static final long MAX_STORED_BYTES = 32L * 1024 * 1024;
    static final int MAX_STORED_IMAGES = 256;

    /**
     * The animation frame quota, mirroring kitty's separate frame quota and sized to the device.
     *
     * <p>It has to hold a whole animation or the animation is not worth having: a 170-frame
     * 512x512 GIF is 170 MB of {@code ARGB_8888}, and a quota that seats two thirds of it buys
     * memory by handing back a logo that jumps. Frames are transient now — they die with the last
     * cell that can display them and with the session — so the ceiling can be generous on a phone
     * with memory to spare and stays at the old 64 MB on one without.</p>
     */
    static final long MAX_FRAME_BYTES = frameQuotaForThisDevice();

    private static long frameQuotaForThisDevice() {
        long floor = 64L * 1024 * 1024;
        long ceiling = 256L * 1024 * 1024;
        long totalBytes = deviceMemoryBytes();
        if (totalBytes <= 0) return floor;
        return Math.max(floor, Math.min(ceiling, totalBytes / 48));
    }

    /** Total RAM from {@code /proc/meminfo}, or 0 when it cannot be read (the JVM in tests). */
    private static long deviceMemoryBytes() {
        try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("MemTotal:")) return 0;
            String[] parts = line.trim().split("\\s+");
            return parts.length < 2 ? 0 : Long.parseLong(parts[1]) * 1024L;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Frame bytes are budgeted across every terminal in the process, not per terminal. The store
     * hangs off a {@link TerminalEmulator}, so a per-store quota multiplies by the number of open
     * panes — three sessions would nominally be owed three whole quotas of animation frames, which
     * is the one thing a generous quota must not mean. kitty's {@code storage_limit} is per
     * instance across all images; this matches it.
     */
    static final class FrameBudget {
        private long used;

        long used() {
            return used;
        }

        void spend(long delta) {
            used = Math.max(0, used + delta);
        }
    }

    /** The budget every live terminal shares. Tests construct their own store with a private one. */
    private static final FrameBudget SHARED_FRAME_BUDGET = new FrameBudget();
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

        /** Invisible placement prototypes referenced by U+10EEEE cells. */
        final List<VirtualPlacement> virtualPlacements = new ArrayList<>();

        /** Extra animation frames; index 0 is protocol frame 2. */
        final List<Frame> frames = new ArrayList<>();
        /** Frames accepted against the quota whose decode has not landed yet. */
        int pendingFrames;
        /** Where the next fold falls, so thinning walks the animation instead of eating its front. */
        int thinCursor;
        /**
         * Whether this image has ever reached a cell. Until it has, it is mid-transmission — an
         * animation is loaded frame by frame before it is placed — and its frames are not garbage
         * just because nothing displays them yet.
         */
        boolean everPlaced;
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

    static final class VirtualPlacement {
        final long placementId;
        final int sourceX;
        final int sourceY;
        final int sourceWidth;
        final int sourceHeight;
        final int columns;
        final int rows;

        VirtualPlacement(long placementId, int sourceX, int sourceY, int sourceWidth,
                         int sourceHeight, int columns, int rows) {
            this.placementId = placementId;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.columns = columns;
            this.rows = rows;
        }
    }

    /** Whether anything on either screen can still display an image; supplied by the caller. */
    interface PlacementProbe {
        boolean isPlaced(long imageId);
    }

    private final Map<Long, Entry> images = new LinkedHashMap<>();
    private final Map<Long, Long> latestIdByNumber = new HashMap<>();
    private long totalBytes;
    private final FrameBudget frameBudget;

    KittyImageStore() {
        this(SHARED_FRAME_BUDGET);
    }

    KittyImageStore(FrameBudget frameBudget) {
        this.frameBudget = frameBudget;
    }

    /** Every change to this store's frame bytes goes through here, so the two ledgers cannot drift. */
    private void spendFrameBytes(long delta) {
        totalFrameBytes += delta;
        frameBudget.spend(delta);
    }

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

    /** Add a Unicode-placeholder placement prototype; identified pairs replace their predecessor. */
    void putVirtualPlacement(Entry entry, VirtualPlacement placement) {
        if (placement.placementId != 0) {
            for (int i = entry.virtualPlacements.size() - 1; i >= 0; i--) {
                if (entry.virtualPlacements.get(i).placementId == placement.placementId)
                    entry.virtualPlacements.remove(i);
            }
        }
        entry.virtualPlacements.add(placement);
    }

    /** Find an exact virtual placement, or the first one when the placeholder carries no id. */
    static VirtualPlacement virtualPlacement(Entry entry, long placementId) {
        for (VirtualPlacement placement : entry.virtualPlacements) {
            if (placementId == 0 || placement.placementId == placementId) return placement;
        }
        return null;
    }

    /** Remove all virtual placements, or only the identified one. */
    static int removeVirtualPlacements(Entry entry, long placementId) {
        int removed = 0;
        for (int i = entry.virtualPlacements.size() - 1; i >= 0; i--) {
            if (placementId == 0 || entry.virtualPlacements.get(i).placementId == placementId) {
                entry.virtualPlacements.remove(i);
                removed++;
            }
        }
        return removed;
    }

    void remove(long id) {
        Entry removed = images.remove(id);
        if (removed == null) return;
        totalBytes -= removed.byteCount;
        for (Frame frame : removed.frames) spendFrameBytes(-frame.byteCount);
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

    /** Free stored data only when no Unicode-placeholder prototype still references it. */
    void removeIfNoVirtualPlacements(long id) {
        Entry entry = images.get(id);
        if (entry != null && entry.virtualPlacements.isEmpty()) remove(id);
    }

    /** The d=A form must retain images referenced by virtual placements. */
    void removeImagesWithoutVirtualPlacements() {
        List<Long> removable = new ArrayList<>();
        for (Entry entry : images.values()) {
            if (entry.virtualPlacements.isEmpty()) removable.add(entry.id);
        }
        for (Long id : removable) remove(id);
    }

    void clear() {
        images.clear();
        latestIdByNumber.clear();
        totalBytes = 0;
        spendFrameBytes(-totalFrameBytes);
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

    /**
     * Whether one more accepted frame would break the per-image frame count, counting frames whose
     * decode has not landed yet. A frame is charged where it is accepted and credited where it
     * commits, and the two are a decode apart: an animation arrives as one burst, and a gate that
     * counted only committed frames would wave the whole burst through against a ledger that stays
     * empty until the last of it lands.
     */
    boolean wouldExceedFrameCount(Entry entry) {
        return entry.frames.size() + entry.pendingFrames + 1 > MAX_FRAMES_PER_IMAGE;
    }

    /**
     * Whether one more committed frame would break the byte quota. Bytes are checked here rather
     * than where the frame was accepted because this is where they are allocated: a queued frame
     * holds only its compressed payload, and the decoder is one thread, so the pixels of exactly
     * one frame at a time exist outside this ledger.
     */
    boolean wouldExceedFrameBytes(int byteCount) {
        return frameBudget.used() + byteCount > MAX_FRAME_BYTES;
    }

    boolean wouldExceedFrameLimits(Entry entry, int byteCount) {
        return wouldExceedFrameBytes(byteCount) || wouldExceedFrameCount(entry);
    }

    /**
     * Make room for one more committed frame, thinning the animation being loaded rather than
     * refusing the rest of it.
     *
     * <p>Refusing is what truncates: a 170-frame 512x512 GIF is 170 MB of {@code ARGB_8888} against
     * a 64 MB quota, so the tail of it used to answer {@code ENOSPC} — suppressed, since senders
     * use {@code q=2} — and the animation played the first 38% of its loop and snapped back.
     * Thinning keeps the whole arc of the motion and spends the quota on a coarser frame rate
     * instead, which is the trade a viewer would choose: an animation that plays through at half
     * the frames still reads as the thing it is, where one that stops a third of the way through
     * does not.</p>
     */
    boolean makeRoomForFrame(Entry entry, int byteCount, PlacementProbe probe) {
        if (!wouldExceedFrameBytes(byteCount)) return true;
        // Other animations first, whole, oldest and unreachable ones before this one loses detail.
        reclaimFrameBudget(entry, byteCount, probe);
        while (wouldExceedFrameBytes(byteCount) && foldOneFrame(entry) > 0) {
            // One frame at a time, so the quota is spent to the last byte rather than halved and
            // refilled — the difference between an animation kept at the cap and one that keeps
            // throwing away half of what it just decoded.
        }
        return !wouldExceedFrameBytes(byteCount);
    }

    /**
     * Drop one frame and hand its gap to the frame before it, so the animation loses a step of
     * smoothness and none of its length or its shape. Returns the bytes reclaimed, or zero when
     * there is no extra frame left to fold.
     *
     * <p>The cursor is what keeps the thinning even. It advances to just past the survivor that
     * absorbed the gap, so a pass over the list takes every other frame and the next pass takes
     * every other survivor — rather than eating the front of the animation, which would leave a
     * skeleton that only ever collapses and is never seen getting up. The root frame is not a
     * candidate: it is what a stopped animation rests on.</p>
     */
    int foldOneFrame(Entry entry) {
        if (entry.frames.isEmpty()) return 0;
        int index = Math.floorMod(entry.thinCursor, entry.frames.size());
        Frame folded = entry.frames.remove(index);
        spendFrameBytes(-folded.byteCount);
        if (index == 0) entry.rootGapMs += folded.gapMs;
        else entry.frames.get(index - 1).gapMs += folded.gapMs;
        entry.thinCursor = index + 1;
        // The displayed frame keeps its place; a viewer on the folded one sees the frame before it.
        if (entry.currentFrame > index) entry.currentFrame--;
        return folded.byteCount;
    }

    /** Count an accepted frame against the per-image frame count until its decode commits or fails. */
    void reserveFrame(Entry entry) {
        entry.pendingFrames++;
    }

    /**
     * Release an accepted frame's count. Every path out of a frame transmission — commit, decode
     * failure, a vanished or replaced image — must release exactly once, or the count bleeds away
     * a frame at a time. The floor keeps an intervening {@link #clear} from driving it negative.
     */
    void releaseFrame(Entry entry) {
        entry.pendingFrames = Math.max(0, entry.pendingFrames - 1);
    }

    /**
     * Make room in the frame quota for {@code needed} bytes, and report whether the frame now
     * fits. Animations are dropped whole, and the newest transmission — the one being looked at —
     * is dropped last: first the ones nothing can display any more, then, if the quota is still
     * full, the oldest of the rest.
     *
     * <p>Unlike the image quota this reclaims rather than refusing. A full frame quota is normally
     * the residue of animations the client can no longer reach — a sender that transmits with
     * {@code I=} gets a fresh id per animation, so every previous one is orphaned, and the cells
     * of the older ones sit in the scrollback where their playback is not worth a byte. Refusing
     * instead turns every later animation in the session into a still image, with only a
     * suppressible {@code ENOSPC} to say why. Losing frames costs an image its motion, not its
     * pixels: it keeps displaying its root frame, and kitty is harsher here — it deletes whole
     * images, which leaves blank cells behind.</p>
     */
    boolean reclaimFrameBudget(Entry keep, int needed, PlacementProbe probe) {
        dropFramesUntilItFits(keep, needed, probe, true);
        dropFramesUntilItFits(keep, needed, probe, false);
        return !wouldExceedFrameLimits(keep, needed);
    }

    /** Oldest transmission first, so the animation on screen now is the last one to lose motion. */
    private void dropFramesUntilItFits(Entry keep, int needed, PlacementProbe probe,
                                       boolean unreachableOnly) {
        for (Entry entry : images.values()) {
            if (!wouldExceedFrameLimits(keep, needed)) return;
            if (entry == keep || entry.frames.isEmpty()) continue;
            if (unreachableOnly && (!entry.virtualPlacements.isEmpty()
                || (probe != null && probe.isPlaced(entry.id)))) continue;
            dropFrames(entry);
        }
    }

    /**
     * Drop an entry's extra frames and stop its animation, leaving the root frame as what it
     * displays. Returns the bytes reclaimed. The bitmaps are dropped, never recycled: a compose or
     * a placement rasterization on the decode worker may still be reading one.
     */
    int dropFrames(Entry entry) {
        int freed = 0;
        for (Frame frame : entry.frames) freed += frame.byteCount;
        entry.frames.clear();
        spendFrameBytes(-freed);
        entry.animationState = ANIMATION_STOPPED;
        entry.currentFrame = 0;
        entry.currentLoop = 0;
        entry.thinCursor = 0;
        entry.frameShownAtUptime = 0;
        // Only the root frame's gap is left to count.
        entry.animationDurationMs = entry.rootGapMs;
        return freed;
    }

    void addFrame(Entry entry, Bitmap bitmap, int byteCount, int gapMs) {
        gapMs = Math.max(0, gapMs);
        entry.frames.add(new Frame(bitmap, gapMs, byteCount));
        entry.animationDurationMs += gapMs;
        spendFrameBytes(byteCount);
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
        spendFrameBytes(byteCount - frame.byteCount);
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
            spendFrameBytes(-promoted.byteCount);
            totalBytes += promoted.byteCount - entry.byteCount;
            entry.bitmap = promoted.bitmap;
            entry.byteCount = promoted.byteCount;
            entry.rootGapMs = promoted.gapMs;
        } else {
            Frame removed = entry.frames.remove(number - 2);
            removedGap = removed.gapMs;
            spendFrameBytes(-removed.byteCount);
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

    /**
     * Move an animation to where it would have reached by {@code now} had it never been suspended,
     * without stepping through every frame it missed. Whole cycles collapse into the loop counter,
     * so the walk is bounded by one cycle however long the suspension lasted. Returns whether the
     * displayed frame changed, so the caller knows whether to composite.
     */
    static boolean catchUpAnimation(Entry entry, long now) {
        long elapsed = now - entry.frameShownAtUptime;
        if (elapsed <= 0) return false;
        if (!isAnimatable(entry)) {
            entry.frameShownAtUptime = now;
            return false;
        }
        int before = entry.currentFrame;
        long duration = entry.animationDurationMs;
        long cycles = elapsed / duration;
        elapsed %= duration;
        if (entry.maxLoops != 0 && cycles > 0) {
            entry.currentLoop = (int) Math.min(entry.maxLoops, entry.currentLoop + cycles);
            if (!isAnimatable(entry)) {
                // It ran out of loops while suspended, so it rests where it was left.
                entry.frameShownAtUptime = now;
                return false;
            }
        }
        int count = frameCount(entry);
        // What is left is under one cycle, so one pass over the frames consumes it.
        for (int guard = count; guard > 0; guard--) {
            int gap = frameGap(entry, entry.currentFrame + 1);
            if (elapsed < gap) break;
            elapsed -= gap;
            int next = (entry.currentFrame + 1) % count;
            if (next == 0) {
                if (entry.animationState == ANIMATION_LOADING) break;
                if (entry.maxLoops != 0 && ++entry.currentLoop >= entry.maxLoops) break;
            }
            entry.currentFrame = next;
        }
        entry.frameShownAtUptime = now - elapsed;
        return entry.currentFrame != before;
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
