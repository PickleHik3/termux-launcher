package com.termux.app.chrome.scene;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copies a picked video or GIF into {@code files/wallpaper/video/} (SAF grants do not survive
 * reliably) and describes it. One file at a time: a new import deletes the others. Call on a
 * worker thread.
 */
public final class VideoImport {

    static final String VIDEO_DIR = "video";
    /** Larger files are refused (spec decision 9). */
    static final long MAX_BYTES = 150L * 1000L * 1000L;
    private static final String TAG = "VideoImport";
    private static final String STAGED = "import.tmp";
    private static final String CONVERTED = "convert.tmp";
    private static final int MAX_NAME = 100;

    private VideoImport() {}

    /** The outcome: {@link #name} (the value after {@code video:}) and {@link #hint} when ok, else {@link #error}. */
    public static final class Result {
        public final boolean ok;
        @Nullable public final String name;
        @Nullable public final String hint;
        @Nullable public final String error;

        private Result(boolean ok, @Nullable String name, @Nullable String hint, @Nullable String error) {
            this.ok = ok;
            this.name = name;
            this.hint = hint;
            this.error = error;
        }

        static Result success(@NonNull String name, @NonNull String hint) {
            return new Result(true, name, hint, null);
        }

        static Result failure(@NonNull String error) {
            return new Result(false, null, null, error);
        }
    }

    @NonNull
    public static File dir(@NonNull Context context) {
        return new File(new File(context.getFilesDir(), ShaderStore.WALLPAPER_DIR), VIDEO_DIR);
    }

    /** The stored file for {@code name}. */
    @NonNull
    public static File file(@NonNull Context context, @NonNull String name) {
        return new File(dir(context), name);
    }

    /** True for a GIF, by file name. */
    public static boolean isGif(@Nullable String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    /**
     * A name that {@link WallpaperMotion#video} accepts, from the picked file's display name:
     * letters, digits, space, dot, dash and underscore kept, anything else an underscore, no leading
     * dot, at most 100 characters with the extension kept. A GIF mime gets {@code .gif} when the
     * name lacks it. Never empty.
     */
    @NonNull
    static String safeName(@Nullable String display, @Nullable String mime) {
        String base = display == null ? "" : display;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            boolean ok = c < 128 && (Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '-' || c == '_');
            sb.append(ok ? c : '_');
        }
        String name = sb.toString().trim();
        while (name.startsWith(".")) name = name.substring(1);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 8) ext = name.substring(dot);
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME - ext.length()) + ext;
        if (name.isEmpty()) name = "video";
        if ("image/gif".equals(mime) && !isGif(name)) {
            name = (name.length() > MAX_NAME - 4 ? name.substring(0, MAX_NAME - 4) : name) + ".gif";
        } else if (!"image/gif".equals(mime) && !isGif(name) && ext.isEmpty()) {
            name = (name.length() > MAX_NAME - 4 ? name.substring(0, MAX_NAME - 4) : name) + ".mp4";
        }
        return WallpaperMotion.safeName(name) ? name : "video.mp4";
    }

    /** Copies {@code uri} in, replaces the previous file, and describes the new one. */
    @WorkerThread
    @NonNull
    public static Result importFile(@NonNull Context context, @NonNull Uri uri) {
        return importFile(context, uri, null);
    }

    /**
     * As above; an animated GIF is converted to an MP4 (a few seconds), reporting the fraction done
     * to {@code progress} on this thread. A conversion that fails keeps the GIF.
     */
    @WorkerThread
    @NonNull
    public static Result importFile(@NonNull Context context, @NonNull Uri uri,
                                    @Nullable GifToVideo.Progress progress) {
        Context app = context.getApplicationContext();
        String display = null;
        long declared = -1L;
        try (Cursor c = app.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) display = c.getString(0);
                if (!c.isNull(1)) declared = c.getLong(1);
            }
        } catch (RuntimeException ignored) {
            // A file: Uri or a provider without the columns: the last path segment names it.
        }
        if (display == null) display = uri.getLastPathSegment();
        if (declared > MAX_BYTES) return Result.failure("That file is over 150 MB.");
        String mime = app.getContentResolver().getType(uri);
        File dir = dir(app);
        if (!dir.isDirectory() && !dir.mkdirs()) return Result.failure("Could not make the video folder.");
        File staged = new File(dir, STAGED);
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) return Result.failure("Could not open that file.");
            long copied = copy(in, staged);
            if (copied < 0) {
                //noinspection ResultOfMethodCallIgnored
                staged.delete();
                return Result.failure("That file is over 150 MB.");
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not copy " + uri, e);
            //noinspection ResultOfMethodCallIgnored
            staged.delete();
            return Result.failure("Could not read that file.");
        }
        boolean gif = "image/gif".equals(mime) || isGif(display) || sniffGif(staged);
        String name = safeName(display, gif ? "image/gif" : mime);
        VideoHint.GifInfo gifInfo = gif ? gifInfo(staged) : null;
        VideoHint.Fields fields = gif ? gifFields(gifInfo) : videoFields(staged, app);
        if (!gif && fields.width <= 0) {
            //noinspection ResultOfMethodCallIgnored
            staged.delete();
            return Result.failure("That file is not a video this phone can play.");
        }
        if (gif && fields.width <= 0) {
            //noinspection ResultOfMethodCallIgnored
            staged.delete();
            return Result.failure("That file is not a GIF.");
        }
        fields.sizeBytes = staged.length();
        File source = staged;
        File converted = new File(dir, CONVERTED);
        if (gif && gifInfo != null && VideoHint.isAnimated(gifInfo.frames)) {
            int maxSide = Math.min(VideoChannel.GIF_MAX_SIDE_PX, screenLongSide(app));
            GifToVideo.Output out = GifToVideo.convert(staged, converted, maxSide, fields.durationMs, progress,
                new AtomicBoolean());
            if (out != null) {
                VideoHint.Fields v = new VideoHint.Fields();
                v.convertedFromGif = true;
                v.mime = out.mime;
                v.width = out.width;
                v.height = out.height;
                v.durationMs = out.durationMs;
                v.sizeBytes = out.file.length();
                v.hardware = hardwareDecoder(out.mime);
                fields = v;
                source = converted;
                name = mp4Name(name);
                //noinspection ResultOfMethodCallIgnored
                staged.delete();
            }
        }
        File target = new File(dir, name);
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                //noinspection ResultOfMethodCallIgnored
                if (!f.equals(source)) f.delete();
            }
        }
        if (!source.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            source.delete();
            return Result.failure("Could not keep that file.");
        }
        return Result.success(name, VideoHint.describe(fields));
    }

    /** Bytes copied, or -1 when the stream is longer than {@link #MAX_BYTES}. */
    private static long copy(@NonNull InputStream in, @NonNull File to) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = new FileOutputStream(to, false)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) return -1L;
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    private static boolean sniffGif(@NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] h = new byte[3];
            return in.read(h) == 3 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    /** The stored GIF's name with {@code .mp4} in place of its extension. */
    @NonNull
    static String mp4Name(@NonNull String gifName) {
        String base = isGif(gifName) ? gifName.substring(0, gifName.length() - 4) : gifName;
        if (base.isEmpty()) base = "video";
        String name = base + ".mp4";
        return WallpaperMotion.safeName(name) ? name : "video.mp4";
    }

    private static int screenLongSide(@NonNull Context context) {
        android.util.DisplayMetrics m = context.getResources().getDisplayMetrics();
        return Math.max(2, Math.max(m.widthPixels, m.heightPixels));
    }

    @Nullable
    private static VideoHint.GifInfo gifInfo(@NonNull File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return VideoHint.GifInfo.read(in);
        } catch (IOException e) {
            Log.w(TAG, "could not read the GIF header", e);
            return null;
        }
    }

    @NonNull
    private static VideoHint.Fields gifFields(@Nullable VideoHint.GifInfo info) {
        VideoHint.Fields f = new VideoHint.Fields();
        f.mime = "image/gif";
        if (info != null) {
            f.width = info.width;
            f.height = info.height;
            f.durationMs = info.durationMs;
        }
        return f;
    }

    @NonNull
    private static VideoHint.Fields videoFields(@NonNull File file, @NonNull Context context) {
        VideoHint.Fields f = new VideoHint.Fields();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getPath());
            f.mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            // The retriever's own mime is the container's; the track's is what the decoder needs.
            String trackMime = trackMime(file);
            if (trackMime != null) f.mime = trackMime;
            int w = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (rotation == 90 || rotation == 270) {
                int t = w;
                w = h;
                h = t;
            }
            f.width = w;
            f.height = h;
            f.durationMs = parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            float fps = parseFloat(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));
            if (fps <= 0f && f.durationMs > 0L) {
                long frames = parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));
                if (frames > 0L) fps = frames * 1000f / f.durationMs;
            }
            f.frameRate = fps;
            if (f.mime != null) f.hardware = hardwareDecoder(f.mime);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not read the video's details", e);
        } finally {
            try {
                r.release();
            } catch (IOException ignored) {
                // Nothing left to free.
            }
        }
        return f;
    }

    @Nullable
    private static String trackMime(@NonNull File file) {
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        try {
            ex.setDataSource(file.getPath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String m = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("video/")) return m;
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not read the video track", e);
        } finally {
            ex.release();
        }
        return null;
    }

    /** Whether a hardware decoder exists for {@code mime}; null when the codec list names none. */
    @Nullable
    static Boolean hardwareDecoder(@NonNull String mime) {
        List<String> names = new ArrayList<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mime)) {
                        names.add(info.getName());
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not list the codecs", e);
        }
        return VideoHint.hardwareFor(names);
    }

    private static int parseInt(@Nullable String s) {
        return (int) parseLong(s);
    }

    private static long parseLong(@Nullable String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static float parseFloat(@Nullable String s) {
        if (s == null) return 0f;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
