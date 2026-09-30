package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * The pure rules of the live frame's radii (animated-wallpaper SPEC §3.4): which of the radii the
 * visible surfaces ask for get a live frame, how a surface finds its frame, how far a live frame
 * is downsampled, and how the buffer ring turns. No Android types beyond annotations, so a plain
 * JUnit test drives it.
 */
public final class LiveRadii {

    /** At most this many radii are rendered live per wallpaper frame. */
    public static final int MAX_LIVE_RADII = 3;
    /** Radii this close (dp) share one live frame. */
    public static final float MERGE_DP = 3f;
    /** From this radius (dp) up the live frame is downsampled by 4 per side, below it by 2. */
    public static final float QUARTER_FROM_DP = 12f;
    /** Buffers per live radius. */
    public static final int RING = 3;

    private LiveRadii() {}

    /** Per-side downsample of a live frame at {@code radiusDp}: 4 from 12 dp up, else 2. */
    public static int divisor(float radiusDp) {
        return radiusDp >= QUARTER_FROM_DP ? 4 : 2;
    }

    /** The ring slot after {@code slot}. */
    public static int nextSlot(int slot) {
        return (slot + 1) % RING;
    }

    /**
     * Reduces the radii the readers use to the live set: radii of 0 or less (the backdrop draws
     * its shader directly) are ignored, radii within {@link #MERGE_DP} of each other share the
     * middle one, and when more than {@link #MAX_LIVE_RADII} groups remain the ones used by the
     * fewest requests go (the higher radius goes first in a tie).
     *
     * @param requested the radii in dp, one entry per user; duplicates weigh a group
     * @param out       receives the live radii ascending; at least {@link #MAX_LIVE_RADII} long
     * @return how many entries of {@code out} were written
     */
    public static int merge(@NonNull float[] requested, int n, @NonNull float[] out) {
        float[] sorted = new float[Math.max(0, n)];
        int m = 0;
        for (int i = 0; i < n; i++) {
            float r = requested[i];
            if (r > 0f && !Float.isNaN(r)) {
                int j = m++;
                while (j > 0 && sorted[j - 1] > r) {
                    sorted[j] = sorted[j - 1];
                    j--;
                }
                sorted[j] = r;
            }
        }
        float[] reps = new float[m];
        int[] counts = new int[m];
        int groups = 0;
        int i = 0;
        while (i < m) {
            int j = i + 1;
            while (j < m && sorted[j] - sorted[i] <= MERGE_DP) j++;
            reps[groups] = sorted[i + (j - i) / 2];
            counts[groups] = j - i;
            groups++;
            i = j;
        }
        while (groups > MAX_LIVE_RADII) {
            int drop = 0;
            for (int g = 1; g < groups; g++) {
                if (counts[g] <= counts[drop]) drop = g;
            }
            for (int g = drop; g < groups - 1; g++) {
                reps[g] = reps[g + 1];
                counts[g] = counts[g + 1];
            }
            groups--;
        }
        System.arraycopy(reps, 0, out, 0, groups);
        return groups;
    }

    /** The index of the live radius that serves {@code radiusDp}, or -1 when none is close enough. */
    public static int match(@NonNull float[] live, int count, float radiusDp) {
        if (!(radiusDp > 0f)) return -1;
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float gap = Math.abs(live[i] - radiusDp);
            if (gap <= MERGE_DP && gap < bestGap) {
                best = i;
                bestGap = gap;
            }
        }
        return best;
    }
}
