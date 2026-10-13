package com.termux.app.terminal;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The photos on the keycap strip's contact chips: the picture set for a contact in the Contacts
 * app, decoded small and round, for the few contacts the strip shows.
 *
 * <p>The contact cache keeps only each photo's thumbnail uri; the strip asks here for the photos
 * of the contacts it shows. A miss decodes on the contacts worker
 * ({@link CommandPaletteContacts#runInBackground}) and answers on the main thread, and the chip
 * shows its monogram until then. Anything that goes wrong (no photo, a file that will not decode,
 * access revoked) answers nothing, so the monogram simply stays.
 *
 * <p>A bounded store, per the performance rules: at most {@link #MAX_PHOTOS} photos and
 * {@link #BUDGET_BYTES} in all, the photo shown least recently going first. Each photo is a
 * square {@link #targetPx} on its side, so a reopened palette draws from here without decoding
 * again. Main thread only, apart from the decode itself.
 */
final class CommandPaletteContactPhotos {

    /** Photos kept: the strip shows at most four, so this covers several palettes' worth. */
    static final int MAX_PHOTOS = 16;
    /**
     * Bytes kept, at most. Sixteen photos at a 4.0 density (112 px squares, 49 KB each) come to
     * about 0.8 MB; a denser screen keeps fewer photos rather than more memory.
     */
    static final long BUDGET_BYTES = 1024L * 1024L;
    /** Decoded at twice the mark's pixel size, so the photo stays sharp scaled into it. */
    private static final float OVERSAMPLE = 2f;

    /** Answered on the main thread, and only when a photo decoded. */
    interface Callback {
        void onContactPhoto(long contactId, @NonNull Drawable photo);
    }

    private static final class Kept {
        final Drawable photo;
        final long bytes;

        Kept(@NonNull Drawable photo, long bytes) {
            this.photo = photo;
            this.bytes = bytes;
        }
    }

    /** Access order, so the eldest entry is the photo shown least recently. */
    private final LinkedHashMap<String, Kept> mPhotos =
        new LinkedHashMap<>(MAX_PHOTOS, 0.75f, true);
    /** Decodes in flight, so a strip rebuilt while one runs does not queue a second. */
    private final Set<String> mPending = new HashSet<>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private long mBytes;

    /** The decoded photo for this contact and uri, or null when there is none yet. */
    @Nullable
    Drawable cached(long contactId, @Nullable String uri) {
        if (uri == null || uri.isEmpty()) return null;
        Kept kept = mPhotos.get(keyOf(contactId, uri));
        return kept == null ? null : kept.photo;
    }

    /**
     * Decodes this contact's photo in the background unless it is cached or already on its way;
     * {@code callback} hears of it on the main thread, and only if it decodes.
     */
    void request(@NonNull Context context, long contactId, @Nullable String uri,
                 @NonNull Callback callback) {
        if (uri == null || uri.isEmpty()) return;
        String key = keyOf(contactId, uri);
        if (mPhotos.containsKey(key) || !mPending.add(key)) return;
        Context app = context.getApplicationContext();
        ContentResolver resolver = app.getContentResolver();
        Resources resources = app.getResources();
        int target = targetPx(resources.getDisplayMetrics().density);
        CommandPaletteContacts.runInBackground(() -> {
            Bitmap bitmap = decode(resolver, uri, target);
            mMain.post(() -> {
                mPending.remove(key);
                if (bitmap == null) return;
                RoundedBitmapDrawable photo =
                    RoundedBitmapDrawableFactory.create(resources, bitmap);
                photo.setCircular(true);
                photo.setAntiAlias(true);
                put(key, photo, bitmap.getAllocationByteCount());
                callback.onContactPhoto(contactId, photo);
            });
        });
    }

    /** Keeps a decoded photo, then lets the least recently shown go until both bounds hold. */
    void put(@NonNull String key, @NonNull Drawable photo, long bytes) {
        Kept previous = mPhotos.put(key, new Kept(photo, bytes));
        if (previous != null) mBytes -= previous.bytes;
        mBytes += bytes;
        Iterator<Map.Entry<String, Kept>> eldest = mPhotos.entrySet().iterator();
        while ((mPhotos.size() > MAX_PHOTOS || mBytes > BUDGET_BYTES) && eldest.hasNext()) {
            Map.Entry<String, Kept> entry = eldest.next();
            if (entry.getKey().equals(key)) continue;
            mBytes -= entry.getValue().bytes;
            eldest.remove();
        }
    }

    int size() {
        return mPhotos.size();
    }

    long bytes() {
        return mBytes;
    }

    /** One photo per contact and picture: a changed photo has a new uri, so it decodes afresh. */
    @NonNull
    static String keyOf(long contactId, @NonNull String uri) {
        return contactId + " " + uri;
    }

    /** The side of a decoded photo, in pixels, at {@code density}. */
    static int targetPx(float density) {
        return Math.max(1, Math.round(CommandPaletteView.MARK_SIZE * density * OVERSAMPLE));
    }

    /**
     * The largest power of two that still leaves the shorter side at least {@code target}: the
     * decode reads no more pixels than it needs, and the scale after it only ever shrinks.
     */
    static int sampleSize(int width, int height, int target) {
        int side = Math.min(width, height);
        int sample = 1;
        while (target > 0 && side / (sample * 2) >= target) sample *= 2;
        return sample;
    }

    /**
     * The photo at {@code uri}, centre-cropped to a square no larger than {@code target} on its
     * side; null when it cannot be read or decoded.
     */
    @Nullable
    static Bitmap decode(@NonNull ContentResolver resolver, @NonNull String uri, int target) {
        try {
            Uri parsed = Uri.parse(uri);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(parsed)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target);
            Bitmap decoded;
            try (InputStream in = resolver.openInputStream(parsed)) {
                if (in == null) return null;
                decoded = BitmapFactory.decodeStream(in, null, options);
            }
            return decoded == null ? null : squareAt(decoded, target);
        } catch (IOException | RuntimeException e) {
            // A missing file, revoked access (SecurityException) or a provider that refuses:
            // the chip keeps its monogram.
            return null;
        }
    }

    @NonNull
    private static Bitmap squareAt(@NonNull Bitmap decoded, int target) {
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        int side = Math.min(width, height);
        if (width == height && side <= target) return decoded;
        Matrix matrix = new Matrix();
        if (side > target) matrix.setScale(target / (float) side, target / (float) side);
        Bitmap square = Bitmap.createBitmap(decoded, (width - side) / 2, (height - side) / 2,
            side, side, matrix, true);
        if (square != decoded) decoded.recycle();
        return square;
    }
}
