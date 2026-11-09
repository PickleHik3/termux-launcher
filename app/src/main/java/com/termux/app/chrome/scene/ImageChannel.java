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
 * so its bottom row is {@code v = 0} (Shadertoy's default), uploaded with mipmaps and repeat wrap,
 * and the bitmap let go at once. A file that cannot be decoded leaves the channel black.
 */
final class ImageChannel implements SceneChannel {

    static final int MAX_SIDE_PX = 2048;
    private static final String TAG = "ImageChannel";

    @NonNull private final File mFile;
    private int mTexture;
    private int mWidth, mHeight;

    ImageChannel(@NonNull File file) {
        mFile = file;
    }

    @Override
    public void attach() {
        if (mTexture != 0) return;
        Bitmap bitmap = decode(mFile);
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mTexture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        if (bitmap == null) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.allocateDirect(4));
            return;
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES30.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        bitmap.recycle();
    }

    @androidx.annotation.Nullable
    private static Bitmap decode(@NonNull File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE_PX) sample *= 2;
            opts.inSampleSize = sample;
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
