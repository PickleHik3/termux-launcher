package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The one image an AI chat gets: the photo on the left and its depth image on the right at the
 * same size, or the photo alone. Each half's long side is at most {@link #MAX_HALF_PX}; both
 * images are decoded downsampled and every bitmap is recycled before {@link #write} returns, so
 * nothing stays resident. Orientation is the file's own, as the wallpaper's channel reads it.
 */
final class SideBySideImage {

    private static final String LOG_TAG = "SideBySideImage";
    /** The long side of each half, at most. */
    static final int MAX_HALF_PX = 1024;
    static final int JPEG_QUALITY = 90;

    private SideBySideImage() {}

    /** The photo half's size: the photo's aspect, long side at most {@link #MAX_HALF_PX}, never enlarged. */
    @NonNull
    static int[] halfSize(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        int longSide = Math.max(w, h);
        if (longSide <= MAX_HALF_PX) return new int[] {w, h};
        double scale = (double) MAX_HALF_PX / longSide;
        return new int[] {Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale))};
    }

    /** The whole image's size: one half for the photo alone, two side by side with a depth image. */
    @NonNull
    static int[] sheetSize(int photoWidth, int photoHeight, boolean depth) {
        int[] half = halfSize(photoWidth, photoHeight);
        return new int[] {depth ? half[0] * 2 : half[0], half[1]};
    }

    /** The largest power of two that still decodes {@code src} at least as big as {@code want}. */
    static int sampleSize(int srcWidth, int srcHeight, int wantWidth, int wantHeight) {
        int s = 1;
        while (srcWidth / (s * 2) >= Math.max(1, wantWidth) && srcHeight / (s * 2) >= Math.max(1, wantHeight)) s *= 2;
        return s;
    }

    /**
     * Writes the image to {@code out} as a JPEG, replacing it. A depth file that is missing or
     * cannot be decoded leaves the photo alone.
     *
     * @return whether a depth half was drawn, or null when the photo could not be used
     */
    @WorkerThread
    @Nullable
    static Boolean write(@NonNull File photo, @Nullable File depth, @NonNull File out) {
        int[] photoBounds = bounds(photo);
        if (photoBounds == null) return null;
        int[] depthBounds = depth == null ? null : bounds(depth);
        int[] half = halfSize(photoBounds[0], photoBounds[1]);
        Bitmap photoBitmap = null;
        Bitmap depthBitmap = null;
        Bitmap sheet = null;
        try {
            photoBitmap = decode(photo, photoBounds, half);
            if (photoBitmap == null) return null;
            // A depth file that will not decode leaves the photo alone, not a black right half.
            if (depthBounds != null) depthBitmap = decode(depth, depthBounds, half);
            boolean withDepth = depthBitmap != null;
            int[] size = sheetSize(photoBounds[0], photoBounds[1], withDepth);
            sheet = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            sheet.eraseColor(Color.BLACK);
            Canvas canvas = new Canvas(sheet);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(photoBitmap, null, new Rect(0, 0, half[0], half[1]), paint);
            if (withDepth) canvas.drawBitmap(depthBitmap, null, new Rect(half[0], 0, half[0] * 2, half[1]), paint);
            return compress(sheet, out) ? withDepth : null;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Making the photo image failed", e);
            return null;
        } finally {
            if (photoBitmap != null) photoBitmap.recycle();
            if (depthBitmap != null) depthBitmap.recycle();
            if (sheet != null) sheet.recycle();
        }
    }

    @Nullable
    private static int[] bounds(@NonNull File file) {
        if (!file.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        return o.outWidth > 0 && o.outHeight > 0 ? new int[] {o.outWidth, o.outHeight} : null;
    }

    @Nullable
    private static Bitmap decode(@NonNull File file, @NonNull int[] bounds, @NonNull int[] want) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleSize(bounds[0], bounds[1], want[0], want[1]);
        return BitmapFactory.decodeFile(file.getPath(), o);
    }

    private static boolean compress(@NonNull Bitmap bitmap, @NonNull File out) throws IOException {
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        File staged = new File(out.getPath() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(staged, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) return false;
        }
        if (!staged.renameTo(out)) throw new IOException("cannot replace " + out);
        return true;
    }
}
