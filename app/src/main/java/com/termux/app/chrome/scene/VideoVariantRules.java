package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * Which frame-rate versions of a stored video exist for a clip, pure arithmetic so it can be
 * tested without a device. The picker's rate row is {@link #RATES}; the wallpaper draws at the
 * chosen rate and plays a file whose frame rate matches it, so a 60 fps clip shown at 15 Hz is
 * re-encoded at 15 fps once rather than decoded at 60 forever (pong: the decoder costs about a
 * sixth of a core at 1080p60 whatever the draw rate).
 *
 * <p>For a clip of frame rate {@code fps}: a rate below it needs a re-encoded version; the lowest
 * rate at or above it plays the untouched file (that rate is the clip's {@link #originalRate
 * original tier}); rates above that are unavailable, since frames cannot be invented and the
 * scene draws a video only when a frame arrived anyway. A GIF has no playable original, so every
 * available rate is an encode and the GIF is the source until it is pruned.
 */
public final class VideoVariantRules {

    private VideoVariantRules() {}

    /** The picker's rates, ascending. */
    public static final int[] RATES = {15, 30, 60};

    /** A 30.3 fps clip counts as 30; a 24 fps clip does not count as 15. */
    static final int TOLERANCE_FPS = 2;

    /** The rate that plays the untouched clip: the lowest rate at or above {@code fps} (60 for anything faster). */
    public static int originalRate(float fps) {
        for (int r : RATES) {
            if (fps <= r + TOLERANCE_FPS) return r;
        }
        return RATES[RATES.length - 1];
    }

    /** The rates the picker offers for this clip: everything up to and including the original tier. */
    @NonNull
    public static int[] availableRates(float fps) {
        int top = originalRate(fps);
        int n = 0;
        for (int r : RATES) if (r <= top) n++;
        return Arrays.copyOf(RATES, n);
    }

    public static boolean isAvailable(float fps, int rateHz) {
        for (int r : availableRates(fps)) if (r == rateHz) return true;
        return false;
    }

    /** The highest available rate not above the requested one (15 when the request is below everything). */
    public static int clampRate(float fps, int rateHz) {
        int best = RATES[0];
        for (int r : availableRates(fps)) if (r <= rateHz) best = r;
        return best;
    }

    /**
     * True when playing at {@code rateHz} wants a re-encoded file: the rate is below the original
     * tier, or the source is a GIF (which MediaPlayer cannot play at all). Higher-than-original
     * rates return false: they are unavailable, not encodable.
     */
    public static boolean needsEncode(float fps, boolean gif, int rateHz) {
        if (!isAvailable(fps, rateHz)) return false;
        return gif || rateHz < originalRate(fps);
    }

    /**
     * True once the untouched source can go: every available rate has its own file. For a video
     * the original-tier file is a copy of the source, so dropping the source loses nothing; for a GIF every available rate has been encoded.
     */
    public static boolean pruneOriginal(float fps, @NonNull boolean[] readyByRateIndex) {
        int[] avail = availableRates(fps);
        for (int i = 0; i < RATES.length; i++) {
            boolean wanted = false;
            for (int r : avail) if (r == RATES[i]) wanted = true;
            if (wanted && (i >= readyByRateIndex.length || !readyByRateIndex[i])) return false;
        }
        return true;
    }

    /** Index of {@code rateHz} in {@link #RATES}, or -1. */
    public static int rateIndex(int rateHz) {
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rateHz) return i;
        return -1;
    }

    /** The file name of the version for a rate, next to the source in the video folder. */
    @NonNull
    public static String variantFileName(int rateHz) {
        return "v" + rateHz + ".mp4";
    }
}
