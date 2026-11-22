package com.termux.app.chrome.scene;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Where shaders and the motion choice live (spec decision 11), and the pending marker of decision 8.
 *
 * <pre>
 * files/wallpaper/shaders/&lt;id&gt;/source.glsl   the user's text, as pasted
 * files/wallpaper/shaders/&lt;id&gt;/source.prev.glsl  the text a Send to AI paste replaced, until its Undo, Done or Close
 * files/wallpaper/shaders/&lt;id&gt;/meta.json     {"name", "quality": full|half|quarter, "rate": 15|30|60, "texture": file name or absent, "depth": file name or absent}
 * files/wallpaper/shaders/&lt;id&gt;/&lt;texture&gt;     the iChannel0 image (the photo), when one is picked
 * files/wallpaper/shaders/&lt;id&gt;/&lt;depth&gt;       the iChannel1 image (its depth image), when there is one
 * files/wallpaper/motion-pending            written on apply, cleared after 3 s of drawing
 * </pre>
 *
 * <p>The choice itself is {@link TERMUX_APP#KEY_WALLPAPER_MOTION} ({@link WallpaperMotion}). The
 * picker (wave C) applies a shader with {@link #apply} after {@link ShaderCheck} passed.</p>
 */
public final class ShaderStore {

    static final String WALLPAPER_DIR = "wallpaper";
    static final String SHADERS_DIR = "shaders";
    public static final String SOURCE_FILE = "source.glsl";
    public static final String META_FILE = "meta.json";
    /** The source a Send to AI paste replaced, kept for its Undo. */
    public static final String PREVIOUS_SOURCE_FILE = "source.prev.glsl";
    static final String PENDING_FILE = "motion-pending";
    /**
     * Written by every {@link #apply} (wall time), so re-applying the shader already in use (same
     * {@code wallpaper_motion} string, which SharedPreferences does not report as a change) still
     * reaches the scene's preference listener.
     */
    public static final String KEY_MOTION_APPLIED_AT = "wallpaper_motion_applied_at";
    /** The name a depth image made on the phone is written under, inside the shader's folder. */
    public static final String MADE_DEPTH_FILE = "made-depth.png";
    /** A shader or meta file longer than this is not read (a pasted shader is a few KB). */
    static final long MAX_TEXT_BYTES = 1L << 20;

    private ShaderStore() {}

    /** A shader's settings; immutable. */
    public static final class Meta {
        @NonNull public final String name;
        @NonNull public final ShaderSafety.Quality quality;
        public final int rateHz;
        /** The iChannel0 image's file name inside the shader's folder, or null. */
        @Nullable public final String texture;
        /** The iChannel1 (depth) image's file name inside the shader's folder, or null. */
        @Nullable public final String depth;

        public Meta(@NonNull String name, @NonNull ShaderSafety.Quality quality, int rateHz,
                    @Nullable String texture, @Nullable String depth) {
            this.name = name;
            this.quality = quality;
            this.rateHz = rateHz == 15 || rateHz == 60 ? rateHz : 30;
            this.texture = WallpaperMotion.safeName(texture) ? texture : null;
            this.depth = WallpaperMotion.safeName(depth) ? depth : null;
        }

        @NonNull
        public static Meta defaults(@NonNull String name) {
            return new Meta(name, ShaderSafety.Quality.HALF, 30, null, null);
        }

        @NonNull
        public static Meta parse(@Nullable String json, @NonNull String fallbackName) {
            if (json == null) return defaults(fallbackName);
            try {
                JSONObject o = new JSONObject(json);
                return new Meta(o.optString("name", fallbackName),
                    ShaderSafety.Quality.parse(o.optString("quality", "half")),
                    o.optInt("rate", 30), o.has("texture") ? o.optString("texture", null) : null,
                    o.has("depth") ? o.optString("depth", null) : null);
            } catch (JSONException e) {
                return defaults(fallbackName);
            }
        }

        @NonNull
        public String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("quality", quality.key());
                o.put("rate", rateHz);
                if (texture != null) o.put("texture", texture);
                if (depth != null) o.put("depth", depth);
                return o.toString(2);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @NonNull
    public static File shadersDir(@NonNull Context context) {
        return new File(new File(context.getFilesDir(), WALLPAPER_DIR), SHADERS_DIR);
    }

    @NonNull
    public static File shaderDir(@NonNull Context context, @NonNull String id) {
        if (!WallpaperMotion.safeName(id)) throw new IllegalArgumentException("bad shader id");
        return new File(shadersDir(context), id);
    }

    @NonNull
    public static File sourceFile(@NonNull Context context, @NonNull String id) {
        return new File(shaderDir(context, id), SOURCE_FILE);
    }

    @NonNull
    public static File metaFile(@NonNull Context context, @NonNull String id) {
        return new File(shaderDir(context, id), META_FILE);
    }

    /** The shader's iChannel0 image, or null when it has none. */
    @Nullable
    public static File textureFile(@NonNull Context context, @NonNull String id, @NonNull Meta meta) {
        return meta.texture == null ? null : new File(shaderDir(context, id), meta.texture);
    }

    /** The shader's iChannel1 (depth) image, or null when it has none. */
    @Nullable
    public static File depthFile(@NonNull Context context, @NonNull String id, @NonNull Meta meta) {
        return meta.depth == null ? null : new File(shaderDir(context, id), meta.depth);
    }

    /** Whether {@code name} is the file a depth image made on the phone is written under. */
    public static boolean isMadeDepth(@Nullable String name) {
        return MADE_DEPTH_FILE.equals(name);
    }

    @WorkerThread
    @NonNull
    public static Meta readMeta(@NonNull Context context, @NonNull String id) {
        return Meta.parse(readText(metaFile(context, id)), id);
    }

    @WorkerThread
    public static void writeMeta(@NonNull Context context, @NonNull String id, @NonNull Meta meta)
        throws IOException {
        writeText(metaFile(context, id), meta.toJson());
    }

    @WorkerThread
    public static void writeSource(@NonNull Context context, @NonNull String id, @NonNull String source)
        throws IOException {
        writeText(sourceFile(context, id), source);
    }

    @NonNull
    public static File previousSourceFile(@NonNull Context context, @NonNull String id) {
        return new File(shaderDir(context, id), PREVIOUS_SOURCE_FILE);
    }

    /**
     * Keeps the saved source beside it before another replaces it, written as the source is.
     *
     * @return false when there was no source to keep
     */
    @WorkerThread
    public static boolean keepPreviousSource(@NonNull Context context, @NonNull String id) throws IOException {
        String source = readText(sourceFile(context, id));
        if (source == null) return false;
        writeText(previousSourceFile(context, id), source);
        return true;
    }

    /** The kept source becomes the source again, in one rename. @return false when none was kept */
    @WorkerThread
    public static boolean restorePreviousSource(@NonNull Context context, @NonNull String id) {
        File previous = previousSourceFile(context, id);
        return previous.isFile() && previous.renameTo(sourceFile(context, id));
    }

    @WorkerThread
    public static void dropPreviousSource(@NonNull Context context, @NonNull String id) {
        //noinspection ResultOfMethodCallIgnored
        previousSourceFile(context, id).delete();
    }

    /** The file's text, or null when it cannot be read. */
    @Nullable
    public static String readText(@NonNull File file) {
        if (!file.isFile() || file.length() > MAX_TEXT_BYTES) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) file.length());
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void writeText(@NonNull File file, @NonNull String text) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        File staged = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(staged, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!staged.renameTo(file)) throw new IOException("cannot replace " + file);
    }

    // ---- Saved shaders: list, new id, delete, texture (the picker's needs) ---------------------

    /** The video rate (15 / 30 / 60): a key of the app's own preferences, read by the video source. */
    public static final String KEY_VIDEO_RATE = "wallpaper_video_rate_hz";

    /** One saved shader as the list shows it. */
    public static final class Entry {
        @NonNull public final String id;
        @NonNull public final String name;

        public Entry(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** A new shader id: safe as a folder name, unique in practice. */
    @NonNull
    public static String newId() {
        return "s" + Long.toString(System.currentTimeMillis(), 36)
            + Integer.toString(new java.util.Random().nextInt(36 * 36), 36);
    }

    /** The saved shaders (those with a source file), by name. */
    @WorkerThread
    @NonNull
    public static java.util.List<Entry> list(@NonNull Context context) {
        java.util.ArrayList<Entry> out = new java.util.ArrayList<>();
        File[] dirs = shadersDir(context).listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            String id = dir.getName();
            if (!dir.isDirectory() || !WallpaperMotion.safeName(id) || !new File(dir, SOURCE_FILE).isFile()) continue;
            out.add(new Entry(id, readMeta(context, id).name));
        }
        java.util.Collections.sort(out, (a, b) -> {
            int c = a.name.compareToIgnoreCase(b.name);
            return c != 0 ? c : a.id.compareTo(b.id);
        });
        return out;
    }

    /** Removes the shader's folder: source, settings, photo and depth image. */
    @WorkerThread
    public static void delete(@NonNull Context context, @NonNull String id) {
        File dir = shaderDir(context, id);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    /**
     * Copies the picked image into the shader's folder as its iChannel0 texture and returns the
     * file name to store in {@link Meta#texture}. The previous texture file goes. The stored name
     * is never the shader's depth image ({@code depthName}) nor {@link #MADE_DEPTH_FILE}.
     *
     * @param displayName the picked file's name, used (cleaned) as the stored name
     */
    @WorkerThread
    @NonNull
    public static String importTexture(@NonNull Context context, @NonNull String id, @NonNull android.net.Uri uri,
                                       @Nullable String displayName, @Nullable String previous,
                                       @Nullable String depthName) throws IOException {
        return importImage(context, id, uri, textureName(displayName), "photo-", previous, depthName);
    }

    /**
     * Copies the picked image into the shader's folder as its iChannel1 depth image and returns the
     * file name to store in {@link Meta#depth}. The previous depth file goes. The stored name is
     * never the photo's ({@code photoName}) nor {@link #MADE_DEPTH_FILE}.
     */
    @WorkerThread
    @NonNull
    public static String importDepth(@NonNull Context context, @NonNull String id, @NonNull android.net.Uri uri,
                                     @Nullable String displayName, @Nullable String previousDepth,
                                     @Nullable String photoName) throws IOException {
        return importImage(context, id, uri, textureName(displayName), "depth-", previousDepth, photoName);
    }

    /** The one copy: the cleaned name, prefixed when it would take the other image's name. */
    @NonNull
    private static String importImage(@NonNull Context context, @NonNull String id, @NonNull android.net.Uri uri,
                                      @NonNull String cleaned, @NonNull String clashPrefix,
                                      @Nullable String previous, @Nullable String other) throws IOException {
        String name = cleaned;
        if (name.equals(other) || isMadeDepth(name)) {
            int keep = Math.min(name.length(), 64 - clashPrefix.length());
            name = textureName(clashPrefix + name.substring(name.length() - keep));
        }
        File dir = shaderDir(context, id);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        File target = new File(dir, name);
        File staged = new File(dir, name + ".tmp");
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(staged, false)) {
            if (in == null) throw new IOException("cannot open " + uri);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (previous != null && !previous.equals(name) && WallpaperMotion.safeName(previous)) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, previous).delete();
        }
        if (!staged.renameTo(target)) throw new IOException("cannot replace " + target);
        return name;
    }

    /** Deletes the image file {@code name} (photo or depth) of shader {@code id}. */
    @WorkerThread
    public static void removeTexture(@NonNull Context context, @NonNull String id, @Nullable String name) {
        if (name == null || !WallpaperMotion.safeName(name)) return;
        //noinspection ResultOfMethodCallIgnored
        new File(shaderDir(context, id), name).delete();
    }

    /** A stored texture name from a picked file's name: one safe segment that cannot clash with the shader files. */
    @NonNull
    static String textureName(@Nullable String displayName) {
        StringBuilder b = new StringBuilder();
        String raw = displayName == null ? "" : displayName.trim();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            b.append(c == '/' || c == '\\' || c == ':' || c < 0x20 ? '_' : c);
        }
        String n = b.toString();
        if (n.length() > 64) n = n.substring(n.length() - 64);
        if (n.isEmpty() || n.startsWith(".") || n.equals(SOURCE_FILE) || n.equals(META_FILE) || n.equals(MADE_DEPTH_FILE)
            || n.equals(PREVIOUS_SOURCE_FILE)
            || n.endsWith(".tmp")) {
            return "texture";
        }
        return WallpaperMotion.safeName(n) ? n : "texture";
    }

    public static int readVideoRate(@NonNull SharedPreferences prefs) {
        int v = prefs.getInt(KEY_VIDEO_RATE, 30);
        return v == 15 || v == 60 ? v : 30;
    }

    public static void writeVideoRate(@NonNull SharedPreferences prefs, int hz) {
        prefs.edit().putInt(KEY_VIDEO_RATE, hz == 15 || hz == 60 ? hz : 30).apply();
    }

    // ---- The motion choice and the pending marker --------------------------------------------

    @NonNull
    public static WallpaperMotion readMotion(@NonNull SharedPreferences prefs) {
        return WallpaperMotion.parse(prefs.getString(TERMUX_APP.KEY_WALLPAPER_MOTION,
            TERMUX_APP.DEFAULT_VALUE_WALLPAPER_MOTION));
    }

    /**
     * Makes {@code motion} the active choice. A moving choice writes the pending marker first, so a
     * crash before it has drawn {@link ShaderSafety#PENDING_CLEAR_MS} reverts to photo at the next
     * start; a photo clears it.
     */
    public static void apply(@NonNull Context context, @NonNull SharedPreferences prefs,
                             @NonNull WallpaperMotion motion) {
        if (motion.moves()) writePending(context);
        else clearPending(context);
        prefs.edit().putString(TERMUX_APP.KEY_WALLPAPER_MOTION, motion.format())
            .putLong(KEY_MOTION_APPLIED_AT, System.currentTimeMillis()).apply();
    }

    @NonNull
    static File pendingFile(@NonNull Context context) {
        return new File(new File(context.getFilesDir(), WALLPAPER_DIR), PENDING_FILE);
    }

    static void writePending(@NonNull Context context) {
        File file = pendingFile(context);
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write('1');
        } catch (IOException ignored) {
            // No marker: a crash would not revert, which is how it was before the guard.
        }
    }

    static boolean hasPending(@NonNull Context context) {
        return pendingFile(context).isFile();
    }

    static void clearPending(@NonNull Context context) {
        //noinspection ResultOfMethodCallIgnored
        pendingFile(context).delete();
    }
}
