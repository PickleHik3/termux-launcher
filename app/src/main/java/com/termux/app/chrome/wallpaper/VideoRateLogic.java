package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.VideoVariantRules;

/**
 * The Video page's rate row as plain arithmetic over a clip's state: which pills can be tapped,
 * whether a tap makes a version, and the status suffix for a ready version. Pure so it is
 * unit-tested; the engine's rules are {@link VideoVariantRules}.
 */
public final class VideoRateLogic {

    private VideoRateLogic() {}

    /** Per {@link VideoVariantRules#RATES} index: the clip can play at that rate. */
    @NonNull
    public static boolean[] enabled(float fps) {
        boolean[] out = new boolean[VideoVariantRules.RATES.length];
        for (int i = 0; i < out.length; i++) out[i] = VideoVariantRules.isAvailable(fps, VideoVariantRules.RATES[i]);
        return out;
    }

    /** True when some rate is disabled, so the page says why. */
    public static boolean showsReason(float fps) {
        for (boolean on : enabled(fps)) if (!on) return true;
        return false;
    }

    /** The N of "Clip is N fps". */
    public static int clipFps(float fps) {
        return Math.round(fps);
    }

    /** True when choosing {@code rateHz} makes a version now: it needs an encode and has no file yet. */
    public static boolean startsPrepare(float fps, boolean gif, @NonNull boolean[] ready, int rateHz) {
        int i = VideoVariantRules.rateIndex(rateHz);
        if (i < 0 || (i < ready.length && ready[i])) return false;
        return VideoVariantRules.needsEncode(fps, gif, rateHz);
    }

    /**
     * "N fps version · size" for the playing rate when its file is a made version (not the
     * original tier's file); null otherwise. {@code format} takes the rate (%1$d) and the size (%2$s).
     */
    @Nullable
    public static String statusSuffix(float fps, boolean gif, @NonNull boolean[] ready,
                                      @NonNull long[] sizeBytes, int playingRate, @NonNull String format) {
        int i = VideoVariantRules.rateIndex(playingRate);
        if (i < 0 || i >= ready.length || !ready[i]) return null;
        if (!VideoVariantRules.needsEncode(fps, gif, playingRate)) return null;
        return String.format(format, playingRate, formatSize(i < sizeBytes.length ? sizeBytes[i] : 0L));
    }

    /** The same size wording as the file hint: KB under a megabyte, else MB. */
    @NonNull
    public static String formatSize(long bytes) {
        if (bytes < 1_000_000L) return Math.max(1L, Math.round(bytes / 1000.0)) + " KB";
        return Math.round(bytes / 1_000_000.0) + " MB";
    }
}
