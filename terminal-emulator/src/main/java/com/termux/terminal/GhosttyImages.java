package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitty graphics for one Ghostty pane, as the renderer asks for them.
 *
 * <ul>
 *   <li><b>Placements.</b> Ghostty's render state lists every image on screen with its layer, z,
 *   cell, offset, size and source rectangle. The list is read again only when its generation
 *   moves, into reused {@link KittyPlacement}s that {@code KittyLayerPainter} draws in the layer
 *   Ghostty names: below the cell backgrounds, below the text, or above it.</li>
 *   <li><b>Unicode placeholders.</b> Their entries in the list are not drawn as placements: the
 *   placeholder cells draw them in their row's image node, as on the Java core, through
 *   {@link #placeholder}, which answers from Ghostty's virtual placements (read again only when
 *   the store's generation moves). Those rows are re-recorded when Ghostty's placements or image
 *   generation moves ({@link #placementsGeneration}, {@link #imageGeneration}).</li>
 *   <li><b>Animation.</b> Each drawn frame ticks Ghostty's animations while the pane is visible,
 *   and posts one redraw for when the next frame is due, never sooner than
 *   {@link #MIN_REDRAW_MILLIS}. Nothing playing, or the pane hidden, posts nothing.</li>
 *   <li><b>Bitmaps</b> live in a {@link GhosttyImageCache}: on screen only, released when the
 *   pane is hidden or closed and when memory is trimmed.</li>
 * </ul>
 *
 * <p>Main thread only, between writes.
 */
final class GhosttyImages implements GhosttyMemory.ImageTrim {

    /** The shortest gap between two animation redraws: one frame at 120 Hz. */
    static final long MIN_REDRAW_MILLIS = 8;

    private final GhosttyCore mCore;
    private final TerminalOutput mOutput;
    private final TerminalEmulator.Clock mClock;
    private final GhosttyImageCache<Bitmap> mCache;

    private int mFrame;
    private final long[] mFrameState = new long[GhosttyNative.FRAME_SLOTS];
    private boolean mHavePlacements;
    private long mPlacementsGeneration;
    private int[] mPlacementInts = new int[GhosttyNative.KP_INTS * 8];
    private final ArrayList<KittyPlacement> mPlacements = new ArrayList<>();
    private final ArrayList<KittyPlacement> mPool = new ArrayList<>();

    private boolean mHaveVirtuals;
    private boolean mVirtualsStale = true;
    private long mStorageGeneration;
    private int[] mVirtualInts = new int[GhosttyNative.KV_INTS * 8];
    private int mVirtualCount;

    private boolean mVisible = true;
    /** When the posted redraw is due, or -1 when none is posted. */
    private long mRedrawAt = -1;
    private final Runnable mRedraw = this::redraw;

    GhosttyImages(GhosttyCore core, TerminalOutput output, TerminalEmulator.Clock clock,
                  GhosttyImageCache<Bitmap> cache) {
        mCore = core;
        mOutput = output;
        mClock = clock;
        mCache = cache;
        // Lives exactly as long as the pane's Ghostty core, unlike the cache's own release, which
        // also runs when the pane is hidden; so trims reach the cache whenever it holds bitmaps.
        GhosttyMemory.addImageTrim(this);
    }

    /** The app's cache: Android bitmaps charged to the process-wide frame budget. */
    static GhosttyImageCache<Bitmap> bitmapCache(GhosttyCore core) {
        return new GhosttyImageCache<>(core, ANDROID_BITMAPS, KittyImageStore.sharedFrameBudget(),
            KittyImageStore.MAX_FRAME_BYTES);
    }

    private static final GhosttyImageCache.Bitmaps<Bitmap> ANDROID_BITMAPS = new GhosttyImageCache.Bitmaps<Bitmap>() {
        @Override
        public Bitmap create(int width, int height) {
            try {
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError | IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public void setRows(Bitmap bitmap, int[] argb, int width, int y, int rows) {
            bitmap.setPixels(argb, 0, width, 0, y, width, rows);
        }
    };

    /**
     * Once per drawn frame, before anything is drawn: tick animations, and put the placements
     * reaching into {@code rowCount} rows from external row {@code topRow} in {@code out}, with
     * their bitmaps. Ghostty's rows are the active screen, so its viewport row is our external row.
     */
    void collect(int topRow, int rowCount, int cellWidth, int cellHeight, List<KittyPlacement> out) {
        out.clear();
        mFrame++;
        mCache.beginFrame(mFrame);
        long now = mClock.nowMillis();
        if (!mCore.kittyFrame(now, mVisible, mFrameState)) return;
        long placements = mFrameState[GhosttyNative.FRAME_PLACEMENTS_GENERATION];
        long storage = mFrameState[GhosttyNative.FRAME_STORAGE_GENERATION];
        if (!mHaveVirtuals || storage != mStorageGeneration) {
            mStorageGeneration = storage;
            mVirtualsStale = true;
        }
        if (!mHavePlacements || placements != mPlacementsGeneration) {
            mPlacementsGeneration = placements;
            mHavePlacements = true;
            readPlacements(cellWidth, cellHeight);
        }
        for (int i = 0, n = mPlacements.size(); i < n; i++) {
            KittyPlacement placement = mPlacements.get(i);
            if (!placement.intersectsRows(topRow, rowCount)) continue;
            int imageId = (int) placement.imageId;
            if (placement.layer == KittyPlacement.LAYER_PLACEHOLDER) {
                // Drawn by its row; kept here so a replayed row's bitmap is not dropped.
                mCache.touch(imageId);
                continue;
            }
            GhosttyImageCache.Entry<Bitmap> entry = mCache.get(imageId);
            if (entry == null) continue;
            placement.bitmap = entry.bitmap;
            out.add(placement);
        }
        schedule(mFrameState[GhosttyNative.FRAME_NEXT_DUE], now);
    }

    private void readPlacements(int cellWidth, int cellHeight) {
        int count = mCore.kittyPlacements(mPlacementInts);
        if (count * GhosttyNative.KP_INTS > mPlacementInts.length) {
            mPlacementInts = new int[count * GhosttyNative.KP_INTS];
            count = Math.min(count, mCore.kittyPlacements(mPlacementInts));
        }
        mPlacements.clear();
        int[] p = mPlacementInts;
        for (int i = 0; i < count; i++) {
            int base = i * GhosttyNative.KP_INTS;
            long imageId = p[base + GhosttyNative.KP_IMAGE_ID] & 0xffffffffL;
            long placementId = p[base + GhosttyNative.KP_PLACEMENT_ID] & 0xffffffffL;
            KittyPlacement placement = i < mPool.size() ? mPool.get(i) : null;
            if (placement == null || placement.imageId != imageId || placement.placementId != placementId) {
                placement = new KittyPlacement(imageId, placementId);
                if (i < mPool.size()) mPool.set(i, placement); else mPool.add(placement);
            }
            boolean virtual = p[base + GhosttyNative.KP_KIND] == GhosttyNative.KIND_VIRTUAL;
            placement.layer = virtual ? KittyPlacement.LAYER_PLACEHOLDER : p[base + GhosttyNative.KP_LAYER];
            placement.z = p[base + GhosttyNative.KP_Z];
            placement.column = p[base + GhosttyNative.KP_COL];
            placement.row = p[base + GhosttyNative.KP_ROW];
            placement.columns = Math.max(1, p[base + GhosttyNative.KP_GRID_COLS]);
            placement.rows = Math.max(1, p[base + GhosttyNative.KP_GRID_ROWS]);
            placement.offsetX = p[base + GhosttyNative.KP_OFFSET_X];
            placement.offsetY = p[base + GhosttyNative.KP_OFFSET_Y];
            placement.width = p[base + GhosttyNative.KP_WIDTH];
            placement.height = p[base + GhosttyNative.KP_HEIGHT];
            placement.sourceX = p[base + GhosttyNative.KP_SRC_X];
            placement.sourceY = p[base + GhosttyNative.KP_SRC_Y];
            placement.sourceWidth = p[base + GhosttyNative.KP_SRC_W];
            placement.sourceHeight = p[base + GhosttyNative.KP_SRC_H];
            placement.cellWidth = Math.max(1, cellWidth);
            placement.cellHeight = Math.max(1, cellHeight);
            placement.bitmap = null;
            mPlacements.add(placement);
        }
    }

    /**
     * Resolve the virtual placement a U+10EEEE cell names (any of the image's when
     * {@code placementId} is 0, as on the Java core), with its bitmap.
     */
    boolean placeholder(long imageId, long placementId, KittyImagePlaceholder out) {
        if (out == null || imageId <= 0 || imageId > 0xffffffffL) return false;
        if (mVirtualsStale) readVirtuals();
        int[] v = mVirtualInts;
        for (int i = 0; i < mVirtualCount; i++) {
            int base = i * GhosttyNative.KV_INTS;
            if ((v[base + GhosttyNative.KV_IMAGE_ID] & 0xffffffffL) != imageId) continue;
            if (placementId != 0 && (v[base + GhosttyNative.KV_PLACEMENT_ID] & 0xffffffffL) != placementId) continue;
            GhosttyImageCache.Entry<Bitmap> entry = mCache.get((int) imageId);
            if (entry == null) return false;
            int[] crop = KittyGraphicsProtocol.computeCrop(entry.width, entry.height,
                v[base + GhosttyNative.KV_SRC_X], v[base + GhosttyNative.KV_SRC_Y],
                v[base + GhosttyNative.KV_SRC_W], v[base + GhosttyNative.KV_SRC_H]);
            if (crop == null) return false;
            out.bitmap = entry.bitmap;
            out.sourceX = crop[0];
            out.sourceY = crop[1];
            out.sourceWidth = crop[2];
            out.sourceHeight = crop[3];
            out.columns = v[base + GhosttyNative.KV_COLUMNS];
            out.rows = v[base + GhosttyNative.KV_ROWS];
            out.generation = entry.generation;
            return true;
        }
        return false;
    }

    private void readVirtuals() {
        mVirtualsStale = false;
        mHaveVirtuals = true;
        int count = mCore.kittyVirtualPlacements(mVirtualInts);
        if (count * GhosttyNative.KV_INTS > mVirtualInts.length) {
            mVirtualInts = new int[count * GhosttyNative.KV_INTS];
            count = Math.min(count, mCore.kittyVirtualPlacements(mVirtualInts));
        }
        mVirtualCount = count;
    }

    /** The image's generation for a row deciding whether to record its image node again. */
    long imageGeneration(long imageId) {
        if (imageId <= 0 || imageId > 0xffffffffL) return 0;
        return mCache.generationOf((int) imageId);
    }

    /** Moves whenever Ghostty's list of images on screen does; compared for equality only. */
    long placementsGeneration() {
        return mPlacementsGeneration;
    }

    private void redraw() {
        mRedrawAt = -1;
        mOutput.onScreenChanged();
    }

    /** Post one redraw for the next animation frame, unless one is already due by then. */
    private void schedule(long nextDue, long now) {
        if (!mVisible || nextDue < 0) return;
        long at = Math.max(nextDue, now + MIN_REDRAW_MILLIS);
        if (mRedrawAt >= now && mRedrawAt <= at) return;
        if (mRedrawAt >= 0) mOutput.cancelTerminalUpdateDelayed(mRedraw);
        mRedrawAt = at;
        mOutput.postTerminalUpdateDelayed(mRedraw, at - now);
    }

    private void cancelRedraw() {
        if (mRedrawAt < 0) return;
        mRedrawAt = -1;
        mOutput.cancelTerminalUpdateDelayed(mRedraw);
    }

    /** A hidden pane neither ticks nor keeps bitmaps; the next drawn frame picks up again. */
    void setVisible(boolean visible) {
        if (mVisible == visible) return;
        mVisible = visible;
        if (!visible) {
            cancelRedraw();
            mCache.release();
        }
    }

    /** Memory is short: give the bitmaps back; the next drawn frame copies what it needs. */
    void trim() {
        mCache.release();
    }

    @Override
    public void trimImages() {
        trim();
    }

    /** The pane is closing. */
    void release() {
        GhosttyMemory.removeImageTrim(this);
        cancelRedraw();
        mCache.release();
        mPlacements.clear();
        mPool.clear();
    }

    /** For tests: the bitmap cache. */
    GhosttyImageCache<Bitmap> cache() {
        return mCache;
    }
}
