package com.termux.app.chrome.wallpaper.living;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Where living stills live and how to find one (living-stills.md, Part C.5): one folder per photo
 * under {@code files/wallpaper/living/}, named by the first 16 hex digits of the SHA-256 of the
 * photo's bytes, so the same photo always lands in the same folder.
 */
public final class LivingStills {
    private LivingStills() {}

    public static final String ROOT = "wallpaper/living";

    @NonNull
    public static File root(@NonNull Context context) {
        return new File(context.getFilesDir(), ROOT);
    }

    @NonNull
    public static File directoryFor(@NonNull File root, @NonNull String hash) {
        return new File(root, hash);
    }

    /** The manifest for this photo, or {@code null} when it has none (or a half-built one). Reads the photo to hash it. */
    @Nullable
    public static Manifest find(@NonNull Context context, @NonNull File photo) {
        try {
            return Manifest.load(directoryFor(root(context), hash16(photo)));
        } catch (IOException e) {
            return null;
        }
    }

    /** The manifest for a {@code living:<hash>} wallpaper id, or {@code null}. */
    @Nullable
    public static Manifest findByHash(@NonNull Context context, @NonNull String hash) {
        if (!hash.matches("[0-9a-f]{16}")) return null;
        return Manifest.load(directoryFor(root(context), hash));
    }

    /** First 16 hex digits of the SHA-256 of the file's bytes. */
    @NonNull
    public static String hash16(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.US, "%02x", d[i] & 0xFF));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
