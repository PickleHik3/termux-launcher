package com.termux.app.wayland;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Feasibility probe (debug only), launcher side: draws a HardwareBuffer the host process handed
 * over, wrapped with {@code Bitmap.wrapHardwareBuffer} (API 29) and drawn in a plain
 * {@link #onDraw}, over the wallpaper, with its own alpha. The host may rewrite the buffer after
 * it was wrapped ({@link #onHostFrame}); each rewrite invalidates. Logs under TLWaylandAHB.
 */
@SuppressLint("ViewConstructor")
final class AhbTestView extends View {

    private static final String LOG_TAG = AhbProbe.LOG_TAG;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mDst = new RectF();
    /** The status line, rebuilt in place each draw. */
    private final StringBuilder mLine = new StringBuilder(64);
    @Nullable private Bitmap mBitmap;
    @Nullable private HardwareBuffer mBuffer;
    private String mExperiment = "";
    private int mHostFrame;
    private int mLastDrawnHostFrame = -1;
    private int mDrawn;
    /** onDraw calls that saw a host frame the previous one had not. */
    private int mChangesSeen;

    AhbTestView(Context context) {
        super(context);
        mText.setColor(Color.WHITE);
        mText.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        mText.setTextSize(14 * context.getResources().getDisplayMetrics().scaledDensity);
    }

    /** Wraps and shows buffer; the view owns it from here. */
    void setBuffer(@NonNull HardwareBuffer buffer, String experiment) {
        release();
        mBuffer = buffer;
        mExperiment = experiment;
        mHostFrame = 0;
        mLastDrawnHostFrame = -1;
        mDrawn = 0;
        mChangesSeen = 0;
        String id = experiment.toUpperCase();
        Log.i(LOG_TAG, id + " view: API " + Build.VERSION.SDK_INT + "; buffer " + buffer.getWidth() + "x"
            + buffer.getHeight() + " format " + buffer.getFormat() + " usage 0x" + Long.toHexString(buffer.getUsage()));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(LOG_TAG, id + " view: wrap NOT possible: Bitmap.wrapHardwareBuffer needs API 29");
        } else {
            try {
                mBitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
            } catch (RuntimeException e) {
                Log.i(LOG_TAG, id + " view: wrapHardwareBuffer threw " + e);
            }
            Bitmap b = mBitmap;
            if (b == null) {
                Log.i(LOG_TAG, id + " view: wrap FAILED (null)");
            } else {
                Log.i(LOG_TAG, id + " view: wrap ok: config " + b.getConfig() + " hasAlpha " + b.hasAlpha() + " premultiplied "
                    + b.isPremultiplied() + " " + b.getWidth() + "x" + b.getHeight() + " colorSpace " + b.getColorSpace());
            }
        }
        invalidate();
    }

    /** The host rewrote the buffer for frame. */
    void onHostFrame(int frame) {
        mHostFrame = frame;
        invalidate();
    }

    /** Logs the view's side of the run. */
    void logSummary(String hostMessage) {
        Log.i(LOG_TAG, mExperiment.toUpperCase() + " view summary: host: " + hostMessage + "; view drew " + mDrawn
            + " frames; draws that saw a new host frame " + mChangesSeen + "; last host frame " + mHostFrame
            + "; hardware canvas " + isHardwareAccelerated());
    }

    void release() {
        if (mBitmap != null) mBitmap.recycle();
        mBitmap = null;
        if (mBuffer != null) mBuffer.close();
        mBuffer = null;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Bitmap b = mBitmap;
        if (b == null) return;
        if (!canvas.isHardwareAccelerated()) {
            // A hardware bitmap cannot be drawn on a software canvas.
            canvas.drawText(mExperiment + ": software canvas, cannot draw", 16, getHeight() / 2f, mText);
            return;
        }
        float scale = Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight());
        float w = b.getWidth() * scale;
        float h = b.getHeight() * scale;
        mDst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(b, null, mDst, mBitmapPaint);
        mDrawn++;
        if (mHostFrame != mLastDrawnHostFrame) {
            if (mLastDrawnHostFrame >= 0) mChangesSeen++;
            mLastDrawnHostFrame = mHostFrame;
        }
        mLine.setLength(0);
        mLine.append(mExperiment).append(" host frame ").append(mHostFrame).append(", drawn ").append(mDrawn)
            .append(" (tap to close)");
        canvas.drawText(mLine, 0, mLine.length(), 16, mText.getTextSize() * 1.5f, mText);
        if (mDrawn % 60 == 0) {
            Log.i(LOG_TAG, mExperiment.toUpperCase() + " view: drawn " + mDrawn + ", host frame " + mHostFrame);
        }
    }
}
