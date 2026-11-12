package com.termux.x11;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What a picture on the Android clipboard is offered to X as, and how its bytes are made when an
 * X client asks for them. The same rules as the terminal's {@code ClipboardImageOffer} (which
 * lives in the app module, out of this module's reach): the picture's own type, and PNG too when
 * it is something else, since PNG is what most X programs ask for. The bytes are read only when
 * a client asks, off the main thread, and never kept after the answer is sent.
 */
final class ClipboardImageTargets {

    /** Most bytes one picture may put into an answer: it is held in memory while it is sent. */
    static final int MAX_BYTES = 32 * 1024 * 1024;

    /** Largest decoded picture a PNG copy is made of, in ARGB bytes. */
    static final long MAX_DECODE_BYTES = 64L * 1024 * 1024;

    static final String PNG = "image/png";

    private ClipboardImageTargets() {}

    /** Whether a clip of these MIME types carries text X is offered (the text path's own test). */
    static boolean hasText(@Nullable String[] mimeTypes) {
        if (mimeTypes == null) return false;
        for (String type : mimeTypes)
            if ("text/plain".equals(type) || "text/html".equals(type)) return true;
        return false;
    }

    /**
     * The clip's picture type, lower-cased, or null when it holds no picture. It may be a
     * wildcard such as {@code image/*}, which names no type an X client could ask for, so
     * {@link #targetsFor} offers it as PNG only.
     */
    @Nullable
    static String imageType(@Nullable String[] mimeTypes) {
        if (mimeTypes == null) return null;
        for (String type : mimeTypes) {
            if (type == null) continue;
            String lower = type.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("image/") || lower.length() == "image/".length()) continue;
            return lower;
        }
        return null;
    }

    /** The image targets X is offered for a picture of {@code imageType}: its own, then PNG. */
    @NonNull
    static List<String> targetsFor(@Nullable String imageType) {
        if (imageType == null) return Collections.emptyList();
        if (PNG.equals(imageType) || imageType.indexOf('*') >= 0) return Collections.singletonList(PNG);
        List<String> targets = new ArrayList<>(2);
        targets.add(imageType);
        targets.add(PNG);
        return targets;
    }

    /** The image targets as the server reads them in an announce: one per line. */
    @NonNull
    static String announceList(@NonNull List<String> targets) {
        return String.join("\n", targets);
    }

    /** Whether {@code target} is one of the image targets a picture of {@code imageType} offers. */
    static boolean offers(@Nullable String imageType, @Nullable String target) {
        return target != null && targetsFor(imageType).contains(target);
    }

    /** Whether answering {@code target} means re-encoding the picture rather than copying it. */
    static boolean needsConversion(@NonNull String imageType, @NonNull String target) {
        return !imageType.equals(target);
    }

    /** Whether a picture of {@code size} bytes may be sent. */
    static boolean withinCap(long size) {
        return size >= 0 && size <= MAX_BYTES;
    }

    /** Whether a picture of these dimensions may be decoded for a PNG copy. */
    static boolean decodable(int width, int height) {
        return width > 0 && height > 0 && (long) width * height * 4 <= MAX_DECODE_BYTES;
    }

    /**
     * The bytes answering {@code target} for the picture at {@code uri}, or an empty array — a
     * refusal — when it cannot be read, decoded or fits no cap. Blocks: call it off the main
     * thread. The caller sends the bytes and drops them.
     */
    @NonNull
    static byte[] bytesFor(@NonNull ContentResolver resolver, @NonNull Uri uri,
                           @NonNull String imageType, @NonNull String target) {
        try {
            if (!needsConversion(imageType, target)) {
                try (InputStream in = resolver.openInputStream(uri)) {
                    return in == null ? new byte[0] : readAll(in, MAX_BYTES);
                }
            }
            return toPng(resolver, uri);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w("CLIP", "clipboard picture not sent as " + target + ": " + e);
            return new byte[0];
        }
    }

    @NonNull
    private static byte[] toPng(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return new byte[0];
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (!decodable(bounds.outWidth, bounds.outHeight))
            throw new IOException("cannot decode " + bounds.outWidth + "x" + bounds.outHeight);
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            bitmap = in == null ? null : BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new IOException("not a picture Android can decode");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new IOException("PNG encoding failed");
            if (!withinCap(out.size())) throw new IOException(out.size() + " bytes as PNG");
            return out.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }

    /** All of {@code in}, refused once it passes {@code max} bytes. */
    @NonNull
    static byte[] readAll(@NonNull InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        for (int read; (read = in.read(buffer)) != -1; ) {
            if (out.size() + (long) read > max) throw new IOException("over " + max + " bytes");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
