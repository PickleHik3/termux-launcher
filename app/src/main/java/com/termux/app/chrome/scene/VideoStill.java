package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

/**
 * The still of a video or GIF wallpaper (spec decision 7): one frame, centre-cropped to the screen
 * the way the layer draws it. The live frame lives in the scene thread's GL context and its
 * external texture is bound to that context only, so the still is not drawn through the passthrough
 * program: the retriever (video, a frame about half a second in, past a fade-in) or the image
 * decoder (GIF, first frame) hands over a bitmap and it is cropped here.
 */
final class VideoStill {

    private static final String TAG = "VideoStill";

    private VideoStill() {}

    /** A mutable software ARGB_8888 bitmap of {@code w} x {@code h}, or null when no frame could be read. */
    @WorkerThread
    @Nullable
    static Bitmap render(@NonNull File file, int w, int h) {
        int[] size = SceneStill.fitSize(w, h);
        Bitmap frame = VideoImport.isGif(file.getName()) ? gifFrame(file, size) : videoFrame(file);
        if (frame == null) return null;
        try {
            return coverCrop(frame, size[0], size[1]);
        } finally {
            frame.recycle();
        }
    }

    @Nullable
    private static Bitmap videoFrame(@NonNull File file) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getPath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = d == null ? 0L : Long.parseLong(d.trim());
            long atUs = durationMs > 1000L ? 500_000L : 0L;
            return r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not read a frame of " + file.getName(), e);
            return null;
        } finally {
            try {
                r.release();
            } catch (IOException ignored) {
                // Nothing left to free.
            }
        }
    }

    @Nullable
    private static Bitmap gifFrame(@NonNull File file, @NonNull int[] size) {
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                int sample = 1;
                while (longest / sample > SceneStill.MAX_SIDE_PX) sample *= 2;
                if (sample > 1) decoder.setTargetSampleSize(sample);
            });
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not read the first frame of " + file.getName(), e);
            return null;
        }
    }

    @NonNull
    private static Bitmap coverCrop(@NonNull Bitmap src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate((w - src.getWidth() * scale) / 2f, (h - src.getHeight() * scale) / 2f);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }
}
