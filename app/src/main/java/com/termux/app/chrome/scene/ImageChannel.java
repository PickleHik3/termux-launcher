package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * An image file as a channel: decoded once (the longer side at most {@link #MAX_SIDE_PX}), flipped
 * so its bottom row is {@code v = 0} (Shadertoy's default), uploaded with the size cap, mipmaps and wrap
 * its factory names ({@link #photo}, {@link #depth}), and the bitmap let go at once. A file that cannot be decoded leaves the channel black.
 */
final class ImageChannel implements SceneChannel {

    static final int MAX_SIDE_PX = 2048;
    static final int DEPTH_MAX_SIDE_PX = 1024;
    private static final String TAG = "ImageChannel";

    @NonNull private final File mFile;
    private final int mMaxSidePx;
    private final boolean mMipmaps;
    private final int mWrapMode;
    private int mTexture;
    private int mWidth, mHeight;

    ImageChannel(@NonNull File file, int maxSidePx, boolean mipmaps, int wrapMode) {
        mFile = file;
        mMaxSidePx = maxSidePx;
        mMipmaps = mipmaps;
        mWrapMode = wrapMode;
    }

    /** A photo: longer side at most 2048 px, mipmapped, repeating. */
    @NonNull
    static ImageChannel photo(@NonNull File file) {
        return new ImageChannel(file, MAX_SIDE_PX, true, GLES20.GL_REPEAT);
    }

    /** A depth image: longer side at most 1024 px, no mipmaps, clamped to its edge. */
    @NonNull
    static ImageChannel depth(@NonNull File file) {
        return new ImageChannel(file, DEPTH_MAX_SIDE_PX, false, GLES20.GL_CLAMP_TO_EDGE);
    }

    /** The power-of-two divisor that brings the longer side to at most {@code maxSide}. */
    static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        if (maxSide <= 0) return sample;
        while (Math.max(width, height) / sample > maxSide) sample *= 2;
        return sample;
    }

    @Override
    public void attach() {
        if (mTexture != 0) return;
        Bitmap bitmap = decode(mFile, mMaxSidePx);
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mTexture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, mWrapMode);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, mWrapMode);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        if (bitmap == null) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.allocateDirect(4));
            return;
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            mMipmaps ? GLES20.GL_LINEAR_MIPMAP_LINEAR : GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        if (mMipmaps) GLES30.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        bitmap.recycle();
    }

    @androidx.annotation.Nullable
    private static Bitmap decode(@NonNull File file, int maxSidePx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSidePx);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), opts);
            if (decoded == null) return null;
            Matrix flip = new Matrix();
            flip.preScale(1f, -1f);
            Bitmap flipped = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(),
                flip, true);
            if (flipped != decoded) decoded.recycle();
            return flipped;
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "could not decode the channel image", e);
            return null;
        }
    }

    @Override
    public int textureId() {
        return mTexture;
    }

    @Override
    public boolean external() {
        return false;
    }

    @Override
    public boolean update(long frameTimeNanos) {
        return false;
    }

    @Override
    public int width() {
        return mWidth;
    }

    @Override
    public int height() {
        return mHeight;
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void release() {
        if (mTexture == 0) return;
        GLES20.glDeleteTextures(1, new int[] {mTexture}, 0);
        mTexture = 0;
    }
}
