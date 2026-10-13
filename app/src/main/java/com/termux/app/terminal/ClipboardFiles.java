package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The folders pictures from the clipboard are kept in as files: a pasted picture under the Termux
 * tmp folder, where a shell or a coding agent can open it by path, and a picture a program
 * copied under the app's cache, where the clipboard's content link points. Files are named
 * {@code clip-<yyyyMMdd-HHmmss>[-n].<ext>} after the moment they were made, and only the newest
 * few are kept: each save prunes the rest.
 *
 * <p>Plain Java on {@link File}, so the naming and the pruning are tested without a device.
 */
public final class ClipboardFiles {

    /** Pasted pictures kept under the Termux tmp folder. */
    public static final int KEEP_PASTED = 20;

    /** Copied pictures kept behind the clipboard's content link. */
    public static final int KEEP_COPIED = 5;

    private static final String PREFIX = "clip-";

    private ClipboardFiles() {}

    /** The file extension for a picture type, without the dot; {@code img} for one it does not know. */
    @NonNull
    public static String extensionFor(@Nullable String mime) {
        String type = mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) type = type.substring(0, parameters).trim();
        switch (type) {
            case "image/png": return "png";
            case "image/jpeg":
            case "image/jpg":
            case "image/pjpeg": return "jpg";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            case "image/bmp":
            case "image/x-ms-bmp": return "bmp";
            case "image/heic": return "heic";
            case "image/heif": return "heif";
            case "image/avif": return "avif";
            case "image/svg+xml": return "svg";
            case "image/tiff": return "tiff";
            case "image/x-icon":
            case "image/vnd.microsoft.icon": return "ico";
            default: break;
        }
        // An unknown image/<subtype>: its subtype, when that is a plain word, beats a made-up name.
        if (type.startsWith("image/")) {
            String subtype = type.substring("image/".length());
            if (subtype.matches("[a-z0-9]{1,8}")) return subtype;
        }
        return "img";
    }

    /** {@code clip-<yyyyMMdd-HHmmss>} for {@code nowMillis} in {@code zone}. */
    @NonNull
    static String baseName(long nowMillis, @NonNull TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        format.setTimeZone(zone);
        return PREFIX + format.format(new Date(nowMillis));
    }

    /**
     * A file in {@code dir} named for {@code nowMillis} that does not exist yet: the bare name, or
     * {@code -2}, {@code -3}… after it for pictures saved within the same second.
     */
    @NonNull
    static File newFile(@NonNull File dir, long nowMillis, @NonNull TimeZone zone, @NonNull String extension) {
        String base = baseName(nowMillis, zone);
        File file = new File(dir, base + "." + extension);
        for (int n = 2; file.exists(); n++) file = new File(dir, base + "-" + n + "." + extension);
        return file;
    }

    /**
     * Copy {@code in} to a new file in {@code dir} for a picture of type {@code mime}, then prune
     * the folder to its newest {@code keep}. A copy over {@code maxBytes} is deleted and refused.
     */
    @NonNull
    public static File save(@NonNull File dir, long nowMillis, @Nullable String mime, @NonNull InputStream in,
                            long maxBytes, int keep) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())
            throw new IOException("Cannot create " + dir);
        File file = newFile(dir, nowMillis, TimeZone.getDefault(), extensionFor(mime));
        boolean done = false;
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = in.read(buffer)) != -1; ) {
                total += read;
                if (total > maxBytes) throw new IOException("Picture larger than " + maxBytes + " bytes");
                out.write(buffer, 0, read);
            }
            done = true;
        } finally {
            if (!done) //noinspection ResultOfMethodCallIgnored
                file.delete();
        }
        prune(dir, keep, file);
        return file;
    }

    /**
     * Delete all but the newest {@code keep} {@code clip-} files in {@code dir}, newest by
     * modification time and then by name; {@code justSaved} is always kept. Other files are left.
     */
    static void prune(@NonNull File dir, int keep, @Nullable File justSaved) {
        File[] files = dir.listFiles((parent, name) -> name.startsWith(PREFIX));
        if (files == null || files.length <= keep) return;
        List<File> newestFirst = new ArrayList<>(Arrays.asList(files));
        newestFirst.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName).reversed());
        if (justSaved != null && newestFirst.remove(justSaved)) newestFirst.add(0, justSaved);
        for (int i = Math.max(keep, 0); i < newestFirst.size(); i++)
            //noinspection ResultOfMethodCallIgnored
            newestFirst.get(i).delete();
    }
}
