package com.termux.app.chrome;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;

/**
 * The one pool every glass surface's frozen pages layer draws its bytes from, sized by the
 * phone's RAM (ADR 0029). A surface holds its pages in a layer of its own, but the layers of all
 * surfaces in the window together never pass {@link Tier#poolBytes}; a surface the pool has no
 * room for that frame keeps the picture it last held. Pure apart from {@link #read}, which
 * reads the RAM once per process.
 */
final class GlassPagePool {

    /** One tier: the scale pages are captured at (a page is 1/scale of its size) and the pool. */
    static final class Tier {
        final int scale;
        final long poolBytes;

        Tier(int scale, long poolBytes) {
            this.scale = scale;
            this.poolBytes = poolBytes;
        }
    }

    /** Pages captured at a quarter of their size, on a phone with more than 4 GiB of RAM. */
    static final int QUARTER_SCALE = 4;
    /** Pages captured at an eighth of their size, on 4 GiB or less or a low-RAM device. */
    static final int EIGHTH_SCALE = 8;

    /** More than this much RAM, and not a low-RAM device, takes the quarter tier. */
    static final long QUARTER_ABOVE_RAM_BYTES = 4L * 1024L * 1024L * 1024L;

    /*
     * Both pools are sized for nine slots: three surfaces (the dock, the nav strip and a float)
     * that each hold three whole pages. A slot is a 1080 x 2400 page and a 16 px gutter each
     * side, 1112 x 2432 px, 4 bytes a pixel:
     *   quarter: 278 x 608 x 4 = 676,096 B a slot, 9 slots = 6,084,864 B, pool 6 MiB = 6,291,456 B
     *   eighth:  139 x 304 x 4 = 169,024 B a slot, 9 slots = 1,521,216 B, pool 2 MiB = 2,097,152 B
     * (a surface's wall decor is a strip a few slots' worth smaller, and takes from the same pool).
     */
    static final long QUARTER_POOL_BYTES = 6L * 1024L * 1024L;
    static final long EIGHTH_POOL_BYTES = 2L * 1024L * 1024L;

    private static final Tier QUARTER = new Tier(QUARTER_SCALE, QUARTER_POOL_BYTES);
    private static final Tier EIGHTH = new Tier(EIGHTH_SCALE, EIGHTH_POOL_BYTES);

    /** The tier for a phone with {@code totalMemBytes} of RAM. Pure. */
    @NonNull
    static Tier tier(long totalMemBytes, boolean lowRamDevice) {
        return !lowRamDevice && totalMemBytes > QUARTER_ABOVE_RAM_BYTES ? QUARTER : EIGHTH;
    }

    // The smaller tier until the RAM is read: a pool never larger than the phone can spare.
    private static volatile Tier sTier = EIGHTH;
    private static boolean sRead;

    /** The tier in force: the one {@link #read} chose. */
    @NonNull
    static Tier current() {
        return sTier;
    }

    /** Reads the phone's RAM and low-RAM flag, once per process, and settles the tier. */
    static synchronized void read(@NonNull Context context) {
        if (sRead) return;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        sTier = tier(info.totalMem, am.isLowRamDevice());
        sRead = true;
    }

    /** The picture is kept and a new one is captured beside it while the old one is drawn. */
    static final int CLAIM_BESIDE = 0;
    /** The held picture goes first; the new one then fits. */
    static final int CLAIM_REPLACE = 1;
    /** No room this frame: the surface keeps the picture it last held. */
    static final int CLAIM_NONE = 2;

    /**
     * What a surface asking for {@code wantBytes} gets: {@code othersBytes} is what the other
     * surfaces hold (and have in flight), {@code ownHeldBytes} what it holds itself. Pure.
     */
    static int claim(long othersBytes, long ownHeldBytes, long wantBytes, long poolBytes) {
        if (othersBytes + ownHeldBytes + wantBytes <= poolBytes) return CLAIM_BESIDE;
        if (othersBytes + wantBytes <= poolBytes) return CLAIM_REPLACE;
        return CLAIM_NONE;
    }

    /**
     * The order surfaces are served the pool in, into {@code out}: those over the wall where the
     * pages move first, each group in its own order. Pure.
     */
    static void order(@NonNull boolean[] over, int count, @NonNull int[] out) {
        int n = 0;
        for (int i = 0; i < count; i++) if (over[i]) out[n++] = i;
        for (int i = 0; i < count; i++) if (!over[i]) out[n++] = i;
    }

    /** Whether the rect (left, top, right, bottom) overlaps the wall's, both in window px. Pure. */
    static boolean overlaps(int left, int top, int right, int bottom,
                            int wallLeft, int wallTop, int wallRight, int wallBottom) {
        return left < right && top < bottom
            && left < wallRight && right > wallLeft && top < wallBottom && bottom > wallTop;
    }

    /**
     * Whether a trim level lets go of the held pages pictures: real pressure
     * ({@code TRIM_MEMORY_RUNNING_LOW} and above, which includes the UI hiding and the process
     * moving to the background). The pictures are small and the next crossing takes them again.
     */
    static boolean trimDropsPages(int trimLevel) {
        return trimLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
    }

    private GlassPagePool() {}
}
