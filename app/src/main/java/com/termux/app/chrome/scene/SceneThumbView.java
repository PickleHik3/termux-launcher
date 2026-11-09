package com.termux.app.chrome.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A shader's still in a view, for the wallpaper picker's thumbnails and its preview: rendered once
 * by {@link SceneStill} on a background thread at this view's size (the longer side at most
 * {@link #MAX_RENDER_SIDE_PX}) and scaled up to the view. It does not animate. The bitmap is let
 * go when the view detaches and rendered again when it is shown again.
 */
public final class SceneThumbView extends View {

    private static final String TAG = "SceneThumbView";
    /** The longer side of the still's render size; a bigger view is scaled up from this. */
    public static final int MAX_RENDER_SIDE_PX = 480;
    /** One renderer for every thumb: stills are drawn one after the other, never at once. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SceneThumb");
        t.setDaemon(true);
        return t;
    });

    @Nullable private String mSource;
    @Nullable private File mChannel0;
    @Nullable private Bitmap mBitmap;
    /** Moves with every new source or size, so a late render for an old one is dropped. */
    private int mGeneration;
    private boolean mRendering;
    @NonNull private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final Rect mDst = new Rect();

    public SceneThumbView(@NonNull Context context) {
        this(context, null);
    }

    public SceneThumbView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The shader text to show the still of (null for blank) and its iChannel0 image. */
    public void setShader(@Nullable String source, @Nullable File channel0) {
        if (java.util.Objects.equals(source, mSource) && java.util.Objects.equals(channel0, mChannel0)) return;
        mSource = source;
        mChannel0 = channel0;
        dropBitmap();
        requestRender();
    }

    /**
     * The AGSL scenes' entry point, kept for the picker until wave C replaces it: there are no
     * scene kinds any more, so it shows nothing.
     */
    public void setScene(@Nullable String kind, float speed, float density, int palette) {
        setShader(null, null);
    }

    /** As {@link #setScene}: shows nothing. */
    public void setChoice(@NonNull LiveWallpaperChoice choice) {
        setShader(null, null);
    }

    /** The still's render scale for a view of {@code w} x {@code h}: the longer side at most {@link #MAX_RENDER_SIDE_PX}. */
    static float renderScale(int w, int h) {
        int longest = Math.max(w, h);
        return longest <= MAX_RENDER_SIDE_PX ? 1f : MAX_RENDER_SIDE_PX / (float) longest;
    }

    private void dropBitmap() {
        mGeneration++;
        mRendering = false;
        mBitmap = null;
        invalidate();
    }

    private void requestRender() {
        final String source = mSource;
        int w = getWidth(), h = getHeight();
        if (source == null || mBitmap != null || mRendering || w <= 0 || h <= 0 || !isAttachedToWindow()) {
            return;
        }
        float scale = renderScale(w, h);
        final int rw = Math.max(1, Math.round(w * scale)), rh = Math.max(1, Math.round(h * scale));
        final File channel0 = mChannel0;
        final int generation = mGeneration;
        mRendering = true;
        WORKER.execute(() -> {
            Bitmap still = null;
            try {
                still = SceneStill.render(source, channel0, rw, rh);
            } catch (RuntimeException e) {
                Log.w(TAG, "thumbnail still failed", e);
            }
            final Bitmap done = still;
            post(() -> {
                if (generation != mGeneration) return;
                mRendering = false;
                mBitmap = done;
                invalidate();
            });
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dropBitmap();
        requestRender();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestRender();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dropBitmap();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Bitmap bitmap = mBitmap;
        if (bitmap == null) return;
        mDst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(bitmap, null, mDst, mPaint);
    }
}
