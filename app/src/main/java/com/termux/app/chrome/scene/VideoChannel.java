package com.termux.app.chrome.scene;

import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A video or GIF file as a channel: a {@link SurfaceTexture} over a {@code GL_TEXTURE_EXTERNAL_OES}
 * texture that the platform decoder ({@link MediaPlayer}, muted, looping) or an
 * {@link AnimatedImageDrawable} drawn on the surface's canvas feeds. One player, one texture, one
 * surface; {@link #release} frees all three.
 *
 * <p>Playback drives the frames: {@link #update} calls {@code updateTexImage} for the frames that
 * arrived since the last tick, and {@link #frameWaiting} tells the layer whether drawing would show
 * anything new. A GIF is drawn into the surface when its drawable asked for a repaint (its own
 * frame delays), then taken in at once.</p>
 */
final class VideoChannel implements SceneChannel {

    private static final String TAG = "VideoChannel";
    /** The longest side a GIF is decoded and drawn at. */
    static final int GIF_MAX_SIDE_PX = 1920;
    /** Frames taken in per tick at most (a 60 fps file drawn at 30 delivers two). */
    private static final int MAX_DRAIN = 8;

    @NonNull private final File mFile;
    private final boolean mGif;
    private int mTexture;
    @Nullable private SurfaceTexture mSurfaceTexture;
    @Nullable private Surface mSurface;
    @Nullable private Handler mHandler;
    private int mWidth, mHeight;

    /** Frames queued by the decoder that {@link #update} has not taken yet (video only). */
    @NonNull private final AtomicInteger mPending = new AtomicInteger();
    private volatile boolean mFailed;
    /** The transform of the latest frame, reused; identity until the first one. */
    @NonNull private final float[] mTransform = new float[16];

    // Video.
    @Nullable private MediaPlayer mPlayer;
    private boolean mPrepared;
    /** The layer is drawing: the player should be running once it is prepared. */
    private boolean mWantPlay;

    // GIF.
    @Nullable private AnimatedImageDrawable mDrawable;
    private boolean mGifDue;
    private int mGifW, mGifH;

    VideoChannel(@NonNull File file) {
        mFile = file;
        mGif = VideoImport.isGif(file.getName());
        android.opengl.Matrix.setIdentityM(mTransform, 0);
    }

    @Override
    public void attach() {
        if (mTexture != 0) return;
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mTexture = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        // The scene thread has a Looper; callbacks come back on it.
        Looper looper = Looper.myLooper();
        mHandler = new Handler(looper != null ? looper : Looper.getMainLooper());
        SurfaceTexture st = new SurfaceTexture(mTexture);
        mSurfaceTexture = st;
        if (!mGif) {
            st.setOnFrameAvailableListener(t -> mPending.incrementAndGet(), mHandler);
        }
        mSurface = new Surface(st);
        mWantPlay = true;
        try {
            if (mGif) startGif(st); else startVideo();
        } catch (java.io.IOException | RuntimeException e) {
            Log.w(TAG, "could not start " + mFile.getName(), e);
            mFailed = true;
        }
    }

    // ---- Video -----------------------------------------------------------------------------

    private void startVideo() throws java.io.IOException {
        MediaPlayer mp = new MediaPlayer();
        mPlayer = mp;
        mp.setDataSource(mFile.getPath());
        mp.setSurface(mSurface);
        mp.setLooping(true);
        mp.setVolume(0f, 0f);
        mp.setOnVideoSizeChangedListener((p, w, h) -> {
            mWidth = w;
            mHeight = h;
        });
        mp.setOnPreparedListener(p -> {
            mPrepared = true;
            mWidth = p.getVideoWidth();
            mHeight = p.getVideoHeight();
            if (mWantPlay) p.start();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            Log.w(TAG, "player error " + what + "/" + extra + " on " + mFile.getName());
            mFailed = true;
            return true;
        });
        mp.prepareAsync();
    }

    // ---- GIF -------------------------------------------------------------------------------

    private void startGif(@NonNull SurfaceTexture st) throws java.io.IOException {
        Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(mFile), (decoder, info, src) -> {
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            int longest = Math.max(w, h);
            if (longest > GIF_MAX_SIDE_PX) {
                float k = GIF_MAX_SIDE_PX / (float) longest;
                decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
            }
        });
        if (!(d instanceof AnimatedImageDrawable)) {
            // A one-frame GIF decodes as a still drawable: draw it once, nothing moves.
            mGifW = Math.max(1, d.getIntrinsicWidth());
            mGifH = Math.max(1, d.getIntrinsicHeight());
            mWidth = mGifW;
            mHeight = mGifH;
            st.setDefaultBufferSize(mGifW, mGifH);
            mStill = d;
            mGifDue = true;
            return;
        }
        AnimatedImageDrawable a = (AnimatedImageDrawable) d;
        mGifW = Math.max(1, a.getIntrinsicWidth());
        mGifH = Math.max(1, a.getIntrinsicHeight());
        mWidth = mGifW;
        mHeight = mGifH;
        st.setDefaultBufferSize(mGifW, mGifH);
        a.setBounds(0, 0, mGifW, mGifH);
        a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        // The drawable advances by its own clock when drawn and asks (through scheduleSelf, then
        // invalidateSelf) to be drawn again when its next frame is due: the callback marks that.
        a.setCallback(mGifCallback);
        mDrawable = a;
        mGifDue = true;
        a.start();
    }

    @Nullable private Drawable mStill;

    private final Drawable.Callback mGifCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            mGifDue = true;
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            Handler h = mHandler;
            if (h != null) h.postAtTime(what, who, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            Handler h = mHandler;
            if (h != null) h.removeCallbacks(what, who);
        }
    };

    /** Draws the GIF's current frame into the surface and takes it into the texture. */
    private void drawGif() {
        Surface surface = mSurface;
        SurfaceTexture st = mSurfaceTexture;
        Drawable d = mDrawable != null ? mDrawable : mStill;
        if (surface == null || st == null || d == null) return;
        mGifDue = false;
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            d.draw(canvas);
        } catch (IllegalArgumentException | IllegalStateException | Surface.OutOfResourcesException e) {
            Log.w(TAG, "could not draw the GIF frame", e);
            return;
        } finally {
            if (canvas != null) surface.unlockCanvasAndPost(canvas);
        }
        if (d == mStill) mStill = null;   // a still is drawn once
        try {
            st.updateTexImage();
            st.getTransformMatrix(mTransform);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not take in the GIF frame", e);
        }
    }

    // ---- SceneChannel ------------------------------------------------------------------------

    @Override
    public int textureId() {
        return mTexture;
    }

    @Override
    public boolean external() {
        return true;
    }

    /** True when a frame is waiting: drawing the scene now would show something new. */
    boolean frameWaiting() {
        return mGif ? mGifDue : mPending.get() > 0;
    }

    /** The player or the decoder broke: the layer should go back to photo. */
    boolean failed() {
        return mFailed;
    }

    @Override
    public boolean update(long frameTimeNanos) {
        SurfaceTexture st = mSurfaceTexture;
        if (st == null) return false;
        if (mGif) {
            if (!mGifDue) return false;
            drawGif();
            return true;
        }
        int n = Math.min(mPending.getAndSet(0), MAX_DRAIN);
        if (n <= 0) return false;
        try {
            for (int i = 0; i < n; i++) st.updateTexImage();
            st.getTransformMatrix(mTransform);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not take in a video frame", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean transform(@NonNull float[] out16) {
        System.arraycopy(mTransform, 0, out16, 0, 16);
        return true;
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
    public void pause() {
        mWantPlay = false;
        MediaPlayer p = mPlayer;
        if (p != null && mPrepared) {
            try {
                if (p.isPlaying()) p.pause();
            } catch (IllegalStateException e) {
                Log.w(TAG, "pause failed", e);
            }
        }
        AnimatedImageDrawable a = mDrawable;
        if (a != null) a.stop();
    }

    @Override
    public void resume() {
        mWantPlay = true;
        MediaPlayer p = mPlayer;
        if (p != null && mPrepared) {
            try {
                if (!p.isPlaying()) p.start();
            } catch (IllegalStateException e) {
                Log.w(TAG, "resume failed", e);
            }
        }
        AnimatedImageDrawable a = mDrawable;
        if (a != null && !a.isRunning()) {
            a.start();
            mGifDue = true;
        }
    }

    @Override
    public void release() {
        MediaPlayer p = mPlayer;
        mPlayer = null;
        mPrepared = false;
        mWantPlay = false;
        if (p != null) {
            p.setOnPreparedListener(null);
            p.setOnErrorListener(null);
            p.setOnVideoSizeChangedListener(null);
            try {
                p.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "player release failed", e);
            }
        }
        AnimatedImageDrawable a = mDrawable;
        mDrawable = null;
        mStill = null;
        if (a != null) {
            a.stop();
            a.setCallback(null);
        }
        Handler h = mHandler;
        if (h != null) h.removeCallbacksAndMessages(null);
        mHandler = null;
        Surface s = mSurface;
        mSurface = null;
        if (s != null) s.release();
        SurfaceTexture st = mSurfaceTexture;
        mSurfaceTexture = null;
        if (st != null) {
            st.setOnFrameAvailableListener(null);
            st.release();
        }
        if (mTexture != 0) {
            GLES20.glDeleteTextures(1, new int[] {mTexture}, 0);
            mTexture = 0;
        }
    }
}
