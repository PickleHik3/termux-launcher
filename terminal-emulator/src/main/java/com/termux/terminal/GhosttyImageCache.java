package com.termux.terminal;

import java.util.ArrayList;

/**
 * The bitmaps a Ghostty pane draws its kitty images from: one per image on screen, copied out of
 * Ghostty's store and kept only while it stays on screen.
 *
 * <p>Ghostty holds every image's pixels in its own store (bounded by
 * {@link KittyImageStore#MAX_STORED_BYTES} per screen). A bitmap here is a second copy, so it is
 * kept only for images drawn in the last two frames, and every byte of it is charged to
 * {@link KittyImageStore.FrameBudget}, the process-wide budget the Java core's animation frames
 * share, bounded by {@link KittyImageStore#MAX_FRAME_BYTES}. An image that does not fit after
 * everything not drawn this frame is dropped is not drawn. {@link #release} gives everything back
 * (pane closed, pane hidden, memory trimmed).
 *
 * <p>Pixels are copied in bands of rows through a small shared buffer, only when an image is new
 * here or its generation moved (a retransmission or an animation frame), and only between writes:
 * Ghostty's pixel pointers are borrowed until its next write or tick.
 *
 * <p>Main thread only. {@code B} is {@code android.graphics.Bitmap} in the app; tests use their own.
 */
final class GhosttyImageCache<B> {

    /** Where the pixels come from: the Ghostty core. */
    interface Source {
        /** Width, height and generation into out[0..2]; false when the image has no pixels. */
        boolean imageInfo(int imageId, long[] out);

        /** Rows as straight ARGB into {@code argb}; rows copied, or -1 if gone or regenerated. */
        int copyImageRows(int imageId, long generation, int firstRow, int rows, int[] argb);

        /** The image's generation, 0 when not stored. */
        long imageGeneration(int imageId);
    }

    /** Makes and fills bitmaps. */
    interface Bitmaps<B> {
        /** A mutable bitmap of this size, or null when there is no memory for it. */
        B create(int width, int height);

        /** {@code Bitmap.setPixels(argb, 0, width, 0, y, width, rows)}. */
        void setRows(B bitmap, int[] argb, int width, int y, int rows);
    }

    /** One image's bitmap. */
    static final class Entry<B> {
        int imageId;
        long generation;
        int width, height;
        long bytes;
        B bitmap;
        /** The frame it was last drawn in, and the frame its generation was last checked in. */
        int usedFrame, checkedFrame;
    }

    /** Pixels per copied band: 64 KiB of ints, grown only for an image wider than this. */
    static final int BAND_PIXELS = 16 * 1024;
    /** Shared by every pane's cache; all of them run on the main thread. */
    private static int[] sBand;

    private final Source mSource;
    private final Bitmaps<B> mBitmaps;
    private final KittyImageStore.FrameBudget mBudget;
    private final long mLimit;
    private final ArrayList<Entry<B>> mEntries = new ArrayList<>();
    private final long[] mInfo = new long[3];
    private long mBytes;
    private int mFrame;

    GhosttyImageCache(Source source, Bitmaps<B> bitmaps, KittyImageStore.FrameBudget budget, long limit) {
        mSource = source;
        mBitmaps = bitmaps;
        mBudget = budget;
        mLimit = limit;
    }

    /** Start frame {@code frame}: drop what was not drawn in this frame or the one before. */
    void beginFrame(int frame) {
        mFrame = frame;
        for (int i = mEntries.size() - 1; i >= 0; i--) {
            Entry<B> entry = mEntries.get(i);
            if (entry.usedFrame < frame - 1) drop(i);
        }
    }

    /**
     * The bitmap for an image drawn this frame, its pixels at Ghostty's current generation, or null
     * when the image has no pixels or no room. Checks Ghostty once per image per frame.
     */
    Entry<B> get(int imageId) {
        int index = indexOf(imageId);
        Entry<B> entry = index < 0 ? null : mEntries.get(index);
        if (entry != null && entry.checkedFrame == mFrame) {
            entry.usedFrame = mFrame;
            return entry.bitmap == null ? null : entry;
        }
        if (!mSource.imageInfo(imageId, mInfo)) {
            if (index >= 0) drop(index);
            return null;
        }
        int width = (int) mInfo[0], height = (int) mInfo[1];
        long generation = mInfo[2];
        if (entry != null && entry.generation == generation && entry.bitmap != null) {
            entry.usedFrame = entry.checkedFrame = mFrame;
            return entry;
        }
        long bytes = 4L * width * height;
        if (entry == null || entry.width != width || entry.height != height || entry.bitmap == null) {
            // A new bitmap: charge it first, making room from images not drawn this frame.
            if (index >= 0) drop(index);
            if (!makeRoom(bytes)) return null;
            B bitmap = mBitmaps.create(width, height);
            if (bitmap == null) return null;
            entry = new Entry<>();
            entry.imageId = imageId;
            entry.width = width;
            entry.height = height;
            entry.bytes = bytes;
            entry.bitmap = bitmap;
            mEntries.add(entry);
            mBytes += bytes;
            mBudget.spend(bytes);
        }
        entry.usedFrame = entry.checkedFrame = mFrame;
        if (!copy(entry, generation)) {
            drop(mEntries.indexOf(entry));
            return null;
        }
        entry.generation = generation;
        return entry;
    }

    /** Keep an image's bitmap through this frame without checking it (a replayed row draws it). */
    void touch(int imageId) {
        int index = indexOf(imageId);
        if (index >= 0) mEntries.get(index).usedFrame = mFrame;
    }

    /** The generation this frame drew an image at, or Ghostty's current one if it did not. */
    long generationOf(int imageId) {
        int index = indexOf(imageId);
        if (index >= 0) {
            Entry<B> entry = mEntries.get(index);
            if (entry.checkedFrame == mFrame) return entry.generation;
        }
        return mSource.imageGeneration(imageId);
    }

    /** Give back every bitmap and its budget. */
    void release() {
        for (int i = mEntries.size() - 1; i >= 0; i--) drop(i);
    }

    /** Bytes of bitmaps held, all charged to the frame budget. */
    long bytes() {
        return mBytes;
    }

    int size() {
        return mEntries.size();
    }

    long limit() {
        return mLimit;
    }

    KittyImageStore.FrameBudget budget() {
        return mBudget;
    }

    private boolean makeRoom(long bytes) {
        if (bytes > mLimit) return false;
        // Oldest first among the images this frame has not drawn.
        while (mBudget.used() + bytes > mLimit) {
            int victim = -1;
            for (int i = 0; i < mEntries.size(); i++) {
                Entry<B> entry = mEntries.get(i);
                if (entry.usedFrame >= mFrame) continue;
                if (victim < 0 || entry.usedFrame < mEntries.get(victim).usedFrame) victim = i;
            }
            if (victim < 0) return false;
            drop(victim);
        }
        return true;
    }

    private boolean copy(Entry<B> entry, long generation) {
        int width = entry.width;
        int capacity = Math.max(BAND_PIXELS, width);
        if (sBand == null || sBand.length < capacity) sBand = new int[capacity];
        int bandRows = Math.max(1, capacity / width);
        for (int y = 0; y < entry.height; ) {
            int rows = Math.min(bandRows, entry.height - y);
            int copied = mSource.copyImageRows(entry.imageId, generation, y, rows, sBand);
            if (copied <= 0) return false;
            mBitmaps.setRows(entry.bitmap, sBand, width, y, copied);
            y += copied;
        }
        return true;
    }

    private int indexOf(int imageId) {
        for (int i = 0, n = mEntries.size(); i < n; i++)
            if (mEntries.get(i).imageId == imageId) return i;
        return -1;
    }

    private void drop(int index) {
        if (index < 0) return;
        Entry<B> entry = mEntries.remove(index);
        mBytes -= entry.bytes;
        mBudget.spend(-entry.bytes);
        // Not recycled: a recorded row may still draw it until its next recording.
        entry.bitmap = null;
    }
}
