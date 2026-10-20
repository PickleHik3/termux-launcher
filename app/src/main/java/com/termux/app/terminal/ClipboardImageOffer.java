package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.ClipboardOffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A picture on offer to a program's clipboard read: in its own type, and as PNG too when it is
 * something else, since PNG is what most programs ask for. The bytes are read only when a read
 * asks for them, which {@link TerminalClipboard} keeps off the main thread.
 */
final class ClipboardImageOffer implements ClipboardOffer {

    /** Most bytes one picture may put into a read: it is held in memory while it is sent. */
    static final int MAX_READ_BYTES = 32 * 1024 * 1024;

    /** Largest decoded picture a PNG copy is made of, in ARGB bytes. */
    static final long MAX_DECODE_BYTES = 64L * 1024 * 1024;

    static final String PNG = "image/png";

    /** Opens the picture's bytes, once per call. */
    interface Source {
        @NonNull InputStream open() throws IOException;
    }

    @NonNull private final Source mSource;
    @NonNull private final String mType;
    @NonNull private final String mText;

    /**
     * @param type the picture's own type, lower-cased
     * @param text the clip's text beside the picture, or null when it has none
     */
    ClipboardImageOffer(@NonNull Source source, @NonNull String type, @Nullable String text) {
        mSource = source;
        mType = type.toLowerCase(Locale.ROOT);
        mText = text == null ? "" : text;
    }

    /** The types a picture of {@code type} is offered in: its own, then PNG when that differs. */
    @NonNull
    static List<String> typesFor(@NonNull String type) {
        String own = type.toLowerCase(Locale.ROOT);
        if (PNG.equals(own)) return Collections.singletonList(PNG);
        List<String> types = new ArrayList<>(2);
        types.add(own);
        types.add(PNG);
        return types;
    }

    @Override
    public String text() {
        return mText;
    }

    @Override
    public List<String> types() {
        return typesFor(mType);
    }

    @Override
    public byte[] data(String mime) throws IOException {
        if (mType.equals(mime)) {
            try (InputStream in = mSource.open()) {
                return readAll(in, MAX_READ_BYTES);
            }
        }
        if (PNG.equals(mime)) return toPng();
        throw new IOException("Not on offer: " + mime);
    }

    /** The picture decoded and written out again as PNG. */
    @NonNull
    private byte[] toPng() throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = mSource.open()) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IOException("Not a picture Android can decode: " + mType);
        if ((long) bounds.outWidth * bounds.outHeight * 4 > MAX_DECODE_BYTES)
            throw new TooLargeException(bounds.outWidth + "x" + bounds.outHeight);
        Bitmap bitmap;
        try (InputStream in = mSource.open()) {
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new IOException("Not a picture Android can decode: " + mType);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new IOException("PNG encoding failed");
            if (out.size() > MAX_READ_BYTES) throw new TooLargeException(out.size() + " bytes as PNG");
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
            if (out.size() + read > max) throw new TooLargeException("over " + max + " bytes");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
