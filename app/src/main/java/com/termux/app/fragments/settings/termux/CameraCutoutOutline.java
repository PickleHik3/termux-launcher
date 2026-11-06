package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.app.chrome.CameraHoleGeometry;
import com.termux.app.chrome.CutoutShape;
import com.termux.app.statusbar.StatusRowKeepOut;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The camera's outline over a Settings page while one of the camera gap's sliders moves: the
 * keep-out box as a stroke in the accent colour, at the camera's real position, so the user can
 * line a slider up with the lens. It is a {@link android.view.ViewOverlay} drawable on the page's
 * own decor view, so it can never reach the home's window, and it cannot stick: {@link #hide} runs
 * on release, on pause and when the page's view goes, and a backstop removes it
 * {@link #BACKSTOP_MS} after the last change whatever else happens.
 *
 * <p>Settings may not draw into the cutout area; while the outline shows the window is allowed to
 * ({@code layoutInDisplayCutoutMode} short edges) and put back on hide.
 */
final class CameraCutoutOutline {

    static final long BACKSTOP_MS = 1500L;
    private static final float STROKE_DP = 2f;

    @NonNull private final Activity mActivity;
    @NonNull private final TermuxAppSharedPreferences mPreferences;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mBackstop = this::hide;
    @NonNull private final OutlineDrawable mDrawable;
    @Nullable private View mDecor;
    private int mPreviousCutoutMode = Integer.MIN_VALUE;

    CameraCutoutOutline(@NonNull Activity activity, @NonNull TermuxAppSharedPreferences preferences) {
        mActivity = activity;
        mPreferences = preferences;
        float density = activity.getResources().getDisplayMetrics().density;
        mDrawable = new OutlineDrawable(STROKE_DP * density, MaterialColors.getColor(
            activity.getWindow().getDecorView(),
            androidx.appcompat.R.attr.colorPrimary, 0xFFFFFFFF));
    }

    /** Shows the outline at the box as the preferences now stand, or moves it there. */
    void showOrUpdate() {
        Window window = mActivity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (decor == null || decor.getWidth() <= 0) {
            hide();
            return;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        int[] box = insets == null ? null : box(insets, decor.getWidth());
        if (box == null) {
            hide();
            return;
        }
        if (mDecor == null) {
            allowCutout(window);
            mDecor = decor;
            decor.getOverlay().add(mDrawable);
        }
        int[] where = new int[2];
        decor.getLocationOnScreen(where);
        mDrawable.setBounds(0, 0, decor.getWidth(), decor.getHeight());
        mDrawable.setBox(box[0] - where[0], box[1] - where[1], box[2] - where[0], box[3] - where[1]);
        mHandler.removeCallbacks(mBackstop);
        mHandler.postDelayed(mBackstop, BACKSTOP_MS);
    }

    /** Takes the outline away; safe to call at any time, any number of times. */
    void hide() {
        mHandler.removeCallbacks(mBackstop);
        View decor = mDecor;
        if (decor == null) return;
        mDecor = null;
        decor.getOverlay().remove(mDrawable);
        restoreCutout(mActivity.getWindow());
    }

    @Nullable
    private int[] box(@NonNull WindowInsetsCompat insets, int windowWidthPx) {
        float density = mActivity.getResources().getDisplayMetrics().density;
        android.graphics.Rect shape = CutoutShape.top(insets.getDisplayCutout(), insets);
        StatusRowKeepOut.Mode mode = StatusRowKeepOut.Mode.fromPreference(
            mPreferences.getStatusBarCutoutMode());
        return CameraHoleGeometry.keepOutBox(mode, shape != null,
            shape == null ? 0 : shape.left, shape == null ? 0 : shape.top,
            shape == null ? 0 : shape.right, shape == null ? 0 : shape.bottom,
            Math.max(0, insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.statusBars()).top),
            Math.round(mPreferences.getStatusBarCutoutOffsetX() * density),
            Math.round(mPreferences.getStatusBarCutoutOffsetY() * density),
            mPreferences.getStatusBarCutoutScale(),
            Math.round(mPreferences.getStatusBarCutoutMargin() * density),
            Math.round(CameraHoleGeometry.CUSTOM_BASE_DP * density), windowWidthPx);
    }

    private void allowCutout(@Nullable Window window) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams params = window.getAttributes();
        mPreviousCutoutMode = params.layoutInDisplayCutoutMode;
        if (mPreviousCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
            return;
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(params);
    }

    private void restoreCutout(@Nullable Window window) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P
            || mPreviousCutoutMode == Integer.MIN_VALUE) return;
        WindowManager.LayoutParams params = window.getAttributes();
        if (params.layoutInDisplayCutoutMode != mPreviousCutoutMode) {
            params.layoutInDisplayCutoutMode = mPreviousCutoutMode;
            window.setAttributes(params);
        }
        mPreviousCutoutMode = Integer.MIN_VALUE;
    }

    /** The box's stroke, rounded to its own half-height; no allocation once built. */
    private static final class OutlineDrawable extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBox = new RectF();

        OutlineDrawable(float strokePx, int colour) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(strokePx);
            mPaint.setColor(colour);
        }

        void setBox(float left, float top, float right, float bottom) {
            if (mBox.left == left && mBox.top == top && mBox.right == right
                && mBox.bottom == bottom) return;
            mBox.set(left, top, right, bottom);
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mBox.isEmpty()) return;
            float radius = Math.min(mBox.width(), mBox.height()) / 2f;
            canvas.drawRoundRect(mBox, radius, radius, mPaint);
        }

        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
