package com.termux.app.chrome.scene;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A scene drawn into a view, for the wallpaper picker's thumbnails and its large preview: one
 * {@link DesignSceneProgram} on a Paint shader at this view's own size (API 33+; on lower APIs it
 * draws nothing). It animates at {@link #FPS} only while attached, shown and with animations
 * enabled ({@link ValueAnimator#areAnimatorsEnabled}); otherwise it shows its t = 0 frame, and it
 * returns to t = 0 whenever it starts again. To stay cheap it renders at reduced resolution
 * ({@link #MAX_RENDER_SIDE_PX} on the longer side) and lets the view scale it up.
 */
public final class SceneThumbView extends View {

    public static final int FPS = 15;
    static final long FRAME_MS = 1000L / FPS;
    /** The longer side of the shader's render size; a bigger view is scaled up from this. */
    public static final int MAX_RENDER_SIDE_PX = 480;

    @Nullable private String mKind;
    private float mSpeed = 1f, mDensityKnob = 0f;
    private int mPalette = 0;
    @Nullable private SceneProgram mProgram;
    /** The photo of a photo scene, decoded while this view draws it and let go when it detaches. */
    @Nullable private SceneArtwork mArtwork;
    @NonNull private final Paint mPaint = new Paint();
    private boolean mRunning;
    private boolean mShowStill = true;
    private long mStartMs;
    @NonNull private final Runnable mTick = this::tick;

    public SceneThumbView(@NonNull Context context) {
        this(context, null);
    }

    public SceneThumbView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaint.setAntiAlias(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The scene (a {@link LiveWallpaperChoice#USER_KINDS} kind, or null for blank) and its knobs. */
    public void setScene(@Nullable String kind, float speed, float density, int palette) {
        String k = kind != null && ScenePrograms.isKind(kind) ? kind : null;
        boolean kindChanged = k == null ? mKind != null : !k.equals(mKind);
        mKind = k;
        mSpeed = LiveWallpaperChoice.clampSpeed(speed);
        mDensityKnob = LiveWallpaperChoice.clampDensity(density);
        mPalette = LiveWallpaperChoice.clampPalette(palette);
        if (kindChanged) {
            mProgram = null;
            releaseArtwork();
        }
        else if (mProgram != null) mProgram.setKnobs(mSpeed, mDensityKnob, mPalette);
        restart();
    }

    /** The scene and knobs of {@code choice}; none draws nothing. */
    public void setChoice(@NonNull LiveWallpaperChoice choice) {
        setScene(choice.scene, choice.speed, choice.density, choice.palette);
    }

    // ---- Running only while it can be seen ---------------------------------------------------------

    private boolean shouldRun() {
        return mKind != null && LiveWallpaperChoice.isSupported() && isAttachedToWindow() && isShown()
            && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
    }

    private void restart() {
        removeCallbacks(mTick);
        mRunning = false;
        if (mProgram != null) mProgram.restart();
        mShowStill = true;
        if (shouldRun()) {
            mRunning = true;
            mShowStill = false;
            mStartMs = SystemClock.uptimeMillis();
            postOnAnimationDelayed(mTick, FRAME_MS);
        }
        invalidate();
    }

    private void tick() {
        if (!mRunning) return;
        if (!shouldRun()) {
            restart();
            return;
        }
        invalidate();
        postOnAnimationDelayed(mTick, FRAME_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restart();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mTick);
        mRunning = false;
        // A photo scene's program holds the decoded photo: drop both; the next draw rebuilds them.
        if (mArtwork != null && mArtwork.heldBytes() > 0L) {
            mProgram = null;
            releaseArtwork();
        }
    }

    private void releaseArtwork() {
        if (mArtwork != null) mArtwork.release();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (mRunning != shouldRun()) restart();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mRunning != shouldRun()) restart();
    }

    // ---- Drawing -----------------------------------------------------------------------------------

    /** The shader's render size for a view of {@code w} x {@code h}: the longer side at most {@link #MAX_RENDER_SIDE_PX}. */
    static float renderScale(int w, int h) {
        int longest = Math.max(w, h);
        return longest <= MAX_RENDER_SIDE_PX ? 1f : MAX_RENDER_SIDE_PX / (float) longest;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (mKind == null || w <= 0 || h <= 0 || !LiveWallpaperChoice.isSupported()) return;
        draw33(canvas, w, h);
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void draw33(@NonNull Canvas canvas, int w, int h) {
        SceneProgram program = mProgram;
        if (program == null) {
            if (mArtwork == null) mArtwork = new SceneArtwork(getContext(), SceneArtwork.COASTAL_SUNSET_ASSET);
            program = ScenePrograms.factory(mKind, new FilamentSource(), mArtwork).create(1f);
            program.setKnobs(mSpeed, mDensityKnob, mPalette);
            mProgram = program;
            mPaint.setShader(program.shader());
        }
        float scale = renderScale(w, h);
        int rw = Math.max(1, Math.round(w * scale)), rh = Math.max(1, Math.round(h * scale));
        program.setSize(rw, rh, getResources().getDisplayMetrics().density);
        // The clock starts at the first drawn frame after a restart (t = 0) and runs from there.
        program.setTime(mShowStill ? 0L : SystemClock.uptimeMillis() - mStartMs);
        int save = canvas.save();
        canvas.scale(w / (float) rw, h / (float) rh);
        canvas.drawRect(0f, 0f, rw, rh, mPaint);
        canvas.restoreToCount(save);
    }
}
