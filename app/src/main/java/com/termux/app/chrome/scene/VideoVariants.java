package com.termux.app.chrome.scene;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The stored video's frame-rate versions on disk (the rules are {@link VideoVariantRules}). The
 * video folder ({@link VideoImport#dir}) holds one import at a time: the untouched source
 * ({@code original.<ext>}, a video or a GIF), one {@code v<rate>.mp4} per prepared rate (the
 * original tier's file is a hard link or copy of the source), and {@code meta.json} with the
 * source's description. The identity shown to the user stays {@code video:<name>} in
 * {@code wallpaper_motion}; which file plays for a rate is {@link #playable}.
 *
 * <p>Contract shared between the engine (import, encode, prune, scene wiring) and the picker UI;
 * the UI reads {@link #state} and calls {@link #prepare} off the main thread, nothing else.
 */
public final class VideoVariants {

    private VideoVariants() {}

    /** Bumped (int, wrapping) whenever a version was added or the source pruned; the scene relistens to it. */
    public static final String KEY_VARIANT_REV = "wallpaper_video_variant_rev";

    /** What is on disk for the stored video; everything the rate row needs to draw itself. */
    public static final class State {
        /** The user's name for the clip (the value after {@code video:}). */
        @NonNull public final String name;
        /** The source's frame rate; a GIF's nominal one. */
        public final float fps;
        /** True when the source is a GIF (no playable original; every rate is an encode). */
        public final boolean gif;
        /** Per {@link VideoVariantRules#RATES} index: a file for that rate exists and is complete. */
        @NonNull public final boolean[] ready;
        /** Per rate index: that file's size in bytes, 0 when absent. */
        @NonNull public final long[] sizeBytes;
        /** The untouched source is still on disk. */
        public final boolean hasOriginal;
        /** The one-line description of the source ({@link VideoHint#describe}). */
        @NonNull public final String hint;

        public State(@NonNull String name, float fps, boolean gif, @NonNull boolean[] ready,
                     @NonNull long[] sizeBytes, boolean hasOriginal, @NonNull String hint) {
            this.name = name;
            this.fps = fps;
            this.gif = gif;
            this.ready = ready;
            this.sizeBytes = sizeBytes;
            this.hasOriginal = hasOriginal;
            this.hint = hint;
        }

        public boolean isReady(int rateHz) {
            int i = VideoVariantRules.rateIndex(rateHz);
            return i >= 0 && i < ready.length && ready[i];
        }

        public long sizeFor(int rateHz) {
            int i = VideoVariantRules.rateIndex(rateHz);
            return i >= 0 && i < sizeBytes.length ? sizeBytes[i] : 0L;
        }

        /** Rates the picker offers for this clip. */
        @NonNull
        public int[] availableRates() {
            return VideoVariantRules.availableRates(fps);
        }

        /** True when choosing this rate starts an encode (available, not ready, and wants one). */
        public boolean wantsPrepare(int rateHz) {
            return VideoVariantRules.needsEncode(fps, gif, rateHz) && !isReady(rateHz);
        }
    }

    /** Outcome of {@link #prepare}: ok with the file, or an error line for the user. */
    public static final class Result {
        public final boolean ok;
        @Nullable public final File file;
        @Nullable public final String error;

        Result(boolean ok, @Nullable File file, @Nullable String error) {
            this.ok = ok;
            this.file = file;
            this.error = error;
        }
    }

    /** Fraction done, 0..1, from the encoding thread. */
    public interface Progress {
        void onProgress(float fraction);
    }

    /** What is stored, or null when no video is stored (the folder is empty or has no meta). Reads disk; call off main or accept a few ms. */
    @Nullable
    public static State state(@NonNull Context context) {
        throw new UnsupportedOperationException("engine wave: not built yet");
    }

    /**
     * The file the scene plays at {@code rateHz}: the version for that rate when ready, else the
     * untouched source (for a GIF source: the GIF, which the layer plays on the CPU fallback), or
     * null when nothing is stored.
     */
    @Nullable
    public static File playable(@NonNull Context context, int rateHz) {
        throw new UnsupportedOperationException("engine wave: not built yet");
    }

    /**
     * Makes the version for {@code rateHz} from the source (blocking; minutes at most; call on a
     * worker thread), bumps {@link #KEY_VARIANT_REV} so the scene swaps to it, then prunes the
     * source when {@link VideoVariantRules#pruneOriginal} says so. A rate that is unavailable or
     * already ready returns ok at once. {@code cancel} set true stops the encode and leaves disk
     * as it was.
     */
    @NonNull
    public static Result prepare(@NonNull Context context, int rateHz, @Nullable Progress progress,
                                 @NonNull AtomicBoolean cancel) {
        throw new UnsupportedOperationException("engine wave: not built yet");
    }

    /** Removes everything in the video folder (a new import or Back to photo). */
    public static void deleteAll(@NonNull Context context) {
        throw new UnsupportedOperationException("engine wave: not built yet");
    }

    /** Bumps {@link #KEY_VARIANT_REV}. */
    public static void bumpRev(@NonNull SharedPreferences prefs) {
        prefs.edit().putInt(KEY_VARIANT_REV, prefs.getInt(KEY_VARIANT_REV, 0) + 1).apply();
    }
}
