package com.termux.app.chrome.scene;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
        /** False for a one-frame GIF, which stays a still and is never encoded. */
        public final boolean animated;
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
            this(name, fps, gif, true, ready, sizeBytes, hasOriginal, hint);
        }

        public State(@NonNull String name, float fps, boolean gif, boolean animated, @NonNull boolean[] ready,
                     @NonNull long[] sizeBytes, boolean hasOriginal, @NonNull String hint) {
            this.name = name;
            this.fps = fps;
            this.gif = gif;
            this.animated = animated;
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
            if (gif && !animated) return false;
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
        File dir = VideoImport.dir(context.getApplicationContext());
        cleanTmp(dir);
        Meta meta = Meta.read(dir);
        if (meta == null) return null;
        boolean[] ready = new boolean[VideoVariantRules.RATES.length];
        long[] sizes = new long[ready.length];
        for (int i = 0; i < ready.length; i++) {
            File f = new File(dir, VideoVariantRules.variantFileName(VideoVariantRules.RATES[i]));
            ready[i] = f.isFile() && f.length() > 0L;
            sizes[i] = ready[i] ? f.length() : 0L;
        }
        return new State(meta.name, meta.fps, meta.gif, meta.animated, ready, sizes, findOriginal(dir) != null, meta.hint);
    }

    /**
     * The file the scene plays at {@code rateHz}: the version for that rate when ready, else the
     * untouched source (for a GIF source: the GIF, which the layer plays on the CPU fallback), or
     * null when nothing is stored.
     */
    @Nullable
    public static File playable(@NonNull Context context, int rateHz) {
        File dir = VideoImport.dir(context.getApplicationContext());
        File variant = new File(dir, VideoVariantRules.variantFileName(rateHz));
        if (variant.isFile() && variant.length() > 0L) return variant;
        return findOriginal(dir);
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
        Context app = context.getApplicationContext();
        if (!acquire(cancel)) return new Result(false, null, "Cancelled.");
        try {
            File dir = VideoImport.dir(app);
            cleanTmp(dir);
            Meta meta = Meta.read(dir);
            if (meta == null) return new Result(false, null, "No video is stored.");
            File target = new File(dir, VideoVariantRules.variantFileName(rateHz));
            if (!VideoVariantRules.isAvailable(meta.fps, rateHz)) return new Result(true, findOriginal(dir), null);
            if (target.isFile() && target.length() > 0L) return new Result(true, target, null);
            if (meta.gif && !meta.animated) return new Result(true, findOriginal(dir), null);
            if (findOriginal(dir) == null) return new Result(false, null, "The original video is gone.");
            boolean made = makeVariant(app, dir, meta, rateHz,
                progress == null ? null : progress::onProgress, cancel);
            if (cancel.get()) return new Result(false, null, "Cancelled.");
            if (!made) return new Result(false, null, "Could not prepare the " + rateHz + " fps version.");
            finishRound(app, dir, meta);
            return new Result(true, target, null);
        } finally {
            LOCK.unlock();
        }
    }

    /** Removes everything in the video folder (a new import or Back to photo). */
    public static void deleteAll(@NonNull Context context) {
        if (!acquire(null)) return;
        try {
            deleteAllLocked(context.getApplicationContext());
        } finally {
            LOCK.unlock();
        }
    }

    // ---- engine internals (shared with VideoImport) -------------------------------------------

    private static final String TAG = "VideoVariants";
    static final String META_FILE = "meta.json";
    static final String ORIGINAL = "original";

    /** One prepare or import at a time per process; a second caller waits. */
    static final ReentrantLock LOCK = new ReentrantLock();

    /** Takes {@link #LOCK}, giving up (false) when {@code cancel} is set while waiting. */
    static boolean acquire(@Nullable AtomicBoolean cancel) {
        try {
            while (!LOCK.tryLock(200L, TimeUnit.MILLISECONDS)) {
                if (cancel != null && cancel.get()) return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The source's description, {@code meta.json} in the video folder. */
    static final class Meta {
        @NonNull final String name;
        @NonNull final String mime;
        final int width, height;
        final float fps;
        final long durationMs, sizeBytes;
        final boolean gif;
        /** A GIF with more than one frame; a still GIF is never encoded. */
        final boolean animated;
        @Nullable final Boolean hardware;
        @NonNull final String hint;

        Meta(@NonNull String name, @NonNull String mime, int width, int height, float fps, long durationMs,
             long sizeBytes, boolean gif, boolean animated, @Nullable Boolean hardware, @NonNull String hint) {
            this.name = name;
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.durationMs = durationMs;
            this.sizeBytes = sizeBytes;
            this.gif = gif;
            this.animated = animated;
            this.hardware = hardware;
            this.hint = hint;
        }

        @NonNull
        String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("mime", mime);
                o.put("width", width);
                o.put("height", height);
                o.put("fps", (double) fps);
                o.put("durationMs", durationMs);
                o.put("sizeBytes", sizeBytes);
                o.put("gif", gif);
                o.put("animated", animated);
                if (hardware != null) o.put("hardware", hardware.booleanValue());
                o.put("hint", hint);
                return o.toString(2);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Null when the text is not a description with a name. */
        @Nullable
        static Meta parse(@Nullable String json) {
            if (json == null) return null;
            try {
                JSONObject o = new JSONObject(json);
                String name = o.optString("name", "");
                if (name.isEmpty()) return null;
                boolean gif = o.optBoolean("gif", false);
                return new Meta(name, o.optString("mime", ""), o.optInt("width", 0), o.optInt("height", 0),
                    (float) o.optDouble("fps", 0.0), o.optLong("durationMs", 0L), o.optLong("sizeBytes", 0L), gif,
                    o.optBoolean("animated", gif), o.has("hardware") ? o.optBoolean("hardware") : null,
                    o.optString("hint", ""));
            } catch (JSONException e) {
                return null;
            }
        }

        @Nullable
        static Meta read(@NonNull File dir) {
            File f = new File(dir, META_FILE);
            if (!f.isFile()) return null;
            try (InputStream in = new FileInputStream(f)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not read " + META_FILE, e);
                return null;
            }
        }

        boolean write(@NonNull File dir) {
            File tmp = new File(dir, META_FILE + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp, false)) {
                out.write(toJson().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not write " + META_FILE, e);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            return tmp.renameTo(new File(dir, META_FILE));
        }
    }

    /** The untouched source ({@code original.<ext>}), or null. */
    @Nullable
    static File findOriginal(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith(ORIGINAL + ".") && !n.endsWith(".tmp") && f.isFile()) return f;
        }
        return null;
    }

    /** {@code original.<ext>} for a stored name: the name's own extension when plain, else mp4 (gif for a GIF). */
    @NonNull
    static String originalFileName(@NonNull String name, boolean gif) {
        if (gif) return ORIGINAL + ".gif";
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        boolean plain = !ext.isEmpty() && ext.length() <= 4 && ext.matches("[a-z0-9]+") && !ext.equals("gif")
            && !ext.equals("tmp");
        return ORIGINAL + "." + (plain ? ext : "mp4");
    }

    /** Deletes half-written encodes. */
    static void cleanTmp(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            if (f.getName().endsWith(".tmp")) f.delete();
        }
    }

    static void deleteAllLocked(@NonNull Context app) {
        File[] files = VideoImport.dir(app).listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /**
     * Creates the file for {@code rateHz} next to the source: a hard link (else a copy) when it is
     * a video's original tier, an encode otherwise. The lock is held. False on failure or cancel;
     * nothing partial stays.
     */
    static boolean makeVariant(@NonNull Context app, @NonNull File dir, @NonNull Meta meta, int rateHz,
                               @Nullable GifToVideo.Progress progress, @NonNull AtomicBoolean cancel) {
        File original = findOriginal(dir);
        if (original == null) return false;
        File target = new File(dir, VideoVariantRules.variantFileName(rateHz));
        File tmp = new File(dir, target.getName() + ".tmp");
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        if (!meta.gif && rateHz == VideoVariantRules.originalRate(meta.fps)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            try {
                Os.link(original.getPath(), target.getPath());
                return true;
            } catch (Exception e) {
                Log.w(TAG, "hard link failed, copying", e);
            }
            try {
                copyFile(original, tmp);
            } catch (IOException e) {
                Log.w(TAG, "could not copy the source", e);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            return tmp.renameTo(target);
        }
        GifToVideo.Output out;
        if (meta.gif) {
            int maxSide = Math.min(VideoChannel.GIF_MAX_SIDE_PX, VideoImport.screenLongSide(app));
            out = GifToVideo.convert(original, tmp, maxSide, meta.durationMs, rateHz, progress, cancel);
        } else {
            out = VideoTranscode.convert(original, tmp, rateHz, progress, cancel);
        }
        if (out == null || cancel.get()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return true;
    }

    private static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to, false)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** After a version was added: prune the source when every available rate has its file, then bump the revision. */
    static void finishRound(@NonNull Context app, @NonNull File dir, @NonNull Meta meta) {
        boolean[] ready = new boolean[VideoVariantRules.RATES.length];
        for (int i = 0; i < ready.length; i++) {
            File f = new File(dir, VideoVariantRules.variantFileName(VideoVariantRules.RATES[i]));
            ready[i] = f.isFile() && f.length() > 0L;
        }
        if (VideoVariantRules.pruneOriginal(meta.fps, ready) && !(meta.gif && !meta.animated)) {
            File original = findOriginal(dir);
            //noinspection ResultOfMethodCallIgnored
            if (original != null) original.delete();
        }
        SharedPreferences prefs = prefs(app);
        if (prefs != null) bumpRev(prefs);
    }

    @Nullable
    static SharedPreferences prefs(@NonNull Context app) {
        TermuxAppSharedPreferences p = TermuxAppSharedPreferences.build(app, false);
        return p == null ? null : p.getSharedPreferences();
    }

    /** Bumps {@link #KEY_VARIANT_REV}. */
    public static void bumpRev(@NonNull SharedPreferences prefs) {
        prefs.edit().putInt(KEY_VARIANT_REV, prefs.getInt(KEY_VARIANT_REV, 0) + 1).apply();
    }
}
