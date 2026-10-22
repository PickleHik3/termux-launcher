package com.termux.app.chrome.scene;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.FrameProbe;

import java.util.function.BooleanSupplier;

/**
 * The wallpaper scene layer: one full-window view, drawn by the launcher behind every surface, that
 * paints the Contour scene with a single RuntimeShader on a full-screen Paint (not a RenderEffect).
 * The view holds a hardware layer, so when other surfaces repaint at 90 Hz the scene is blitted from
 * its texture and the shader only runs on the frames this class asks for.
 *
 * <p>Frame policy: a {@link Choreographer} frame callback runs while the layer could be seen. A
 * reaction (see {@link SceneEvents}) animates at the full vsync rate until it has decayed; with
 * none live the callback skips frames down to the ambient rate ({@link SceneFramePolicy}). No
 * callback is posted at all while the layer is hidden, the screen is off, Lazy mode is on, or the
 * ambient rate is 0 and nothing is happening. With Remove animations on, the motion freezes.</p>
 *
 * <p>Coordinates are the window's own: the controller sizes and offsets this view to the decor, so
 * a touch's raw position and a pane's on-screen rect are scene pixels as they are.</p>
 */
public final class SceneLayerView extends View {

    /** The pane rects the basins are pressed under, and a changing set may be re-read at this pace. */
    private static final long BASIN_REFRESH_MS = 250L;
    private static final long GATE_REFRESH_MS = 1000L;

    /** Where the layer finds the open panes: screen rects as (cx, cy, hw, hh), up to the array's room. */
    public interface PaneSource {
        int paneRects(@NonNull float[] out);
    }

    private static final FrameProbe.Source FRAMES = FrameProbe.source("scene");

    @NonNull private final SceneEvents mEvents = new SceneEvents();
    @NonNull private final Paint mPaint = new Paint();
    @NonNull private final float[] mBasinScratch = new float[SceneEvents.MAX_BASINS * 4];
    @NonNull private final BooleanSupplier mLazyMode;
    @Nullable private final PaneSource mPanes;
    @Nullable private ContourProgram mProgram;
    @Nullable private SceneLowRes mLowRes;
    private final float mDensity;

    private long mPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_RATE_HZ);
    private boolean mGlassLowRes = true;
    private boolean mPosted;
    private boolean mAnimatorsOn = true;
    private boolean mLazy;
    private boolean mScreenOn = true;
    private boolean mDestroyed;
    private long mGateAtMs = Long.MIN_VALUE;
    private long mBasinAtMs = Long.MIN_VALUE;
    private long mLastDrawNs = Long.MIN_VALUE / 2;
    /** The uptime the next draw shows; frozen while no ambient motion runs. */
    private long mTimeMs;
    private long mFrozenTimeMs;
    /** The reaction uniforms need re-setting before the next draw. */
    private boolean mEventsStale = true;
    private boolean mWasActive;

    private final Choreographer.FrameCallback mFrame = this::onFrame;

    public SceneLayerView(@NonNull Context context, @Nullable PaneSource panes,
                          @NonNull BooleanSupplier lazyMode) {
        super(context);
        mPanes = panes;
        mLazyMode = lazyMode;
        mDensity = context.getResources().getDisplayMetrics().density;
        mFrozenTimeMs = SystemClock.uptimeMillis();
        mTimeMs = mFrozenTimeMs;
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // A texture of its own: other surfaces repainting do not re-run the shader.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        mPaint.setAntiAlias(false);
    }

    // ---- Configuration, from the controller's property polls ----------------------------------

    public void setRateHz(int hz) {
        long period = SceneFramePolicy.ambientPeriodNs(hz);
        if (period == mPeriodNs) return;
        if (period == Long.MAX_VALUE) mFrozenTimeMs = SystemClock.uptimeMillis();
        mPeriodNs = period;
        kick();
    }

    public void setGlassLowRes(boolean lowRes) {
        if (lowRes == mGlassLowRes) return;
        mGlassLowRes = lowRes;
        if (!lowRes) closeLowRes();
        kick();
    }

    // ---- Reactions ---------------------------------------------------------------------------

    /** Touch-down at screen px: a hill that relaxes over about a second. */
    public void touchDown(float x, float y) {
        if (!motionAllowed()) return;
        Trace.beginSection("Scene.event");
        mEvents.addHill(x, y, SystemClock.uptimeMillis());
        mEventsStale = true;
        Trace.endSection();
        kick();
    }

    /** A committed key with the cursor at screen px: a spark along the nearest contour. */
    public void spark(float x, float y) {
        if (!motionAllowed()) return;
        Trace.beginSection("Scene.event");
        mEvents.addSpark(x, y, SystemClock.uptimeMillis());
        mEventsStale = true;
        Trace.endSection();
        kick();
    }

    /** The bell of the pane whose screen rect is centred at (cx, cy) with half extents (hw, hh). */
    public void bell(float cx, float cy, float hw, float hh) {
        if (!motionAllowed()) return;
        Trace.beginSection("Scene.event");
        mEvents.addPulse(cx, cy, hw, hh, SystemClock.uptimeMillis());
        mEventsStale = true;
        Trace.endSection();
        kick();
    }

    private boolean motionAllowed() {
        refreshGates(SystemClock.uptimeMillis());
        return mAnimatorsOn && !mLazy;
    }

    // ---- Frame loop --------------------------------------------------------------------------

    private boolean shouldRun() {
        return !mDestroyed && mScreenOn && isShown() && getWindowVisibility() == VISIBLE
            && getWidth() > 0 && !mLazy;
    }

    /** Starts the callback if nothing is waiting and there is a reason to run. */
    public void kick() {
        refreshGates(SystemClock.uptimeMillis());
        if (mPosted || !shouldRun() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        mPosted = true;
        Choreographer.getInstance().postFrameCallback(mFrame);
    }

    private void refreshGates(long nowMs) {
        if (mGateAtMs != Long.MIN_VALUE && nowMs - mGateAtMs < GATE_REFRESH_MS && nowMs >= mGateAtMs) {
            return;
        }
        mGateAtMs = nowMs;
        mAnimatorsOn = ValueAnimator.areAnimatorsEnabled();
        mLazy = mLazyMode.getAsBoolean();
    }

    private void onFrame(long frameTimeNanos) {
        mPosted = false;
        long nowMs = SystemClock.uptimeMillis();
        refreshGates(nowMs);
        if (!shouldRun()) return;
        boolean motion = mAnimatorsOn;
        boolean active = motion && mEvents.active(nowMs);
        long period = motion ? mPeriodNs : Long.MAX_VALUE;
        boolean basinsMoved = false;
        if (mPanes != null && nowMs - mBasinAtMs >= BASIN_REFRESH_MS) {
            mBasinAtMs = nowMs;
            basinsMoved = refreshBasins();
        }
        if (SceneFramePolicy.shouldDraw(active, period, mLastDrawNs, frameTimeNanos)
            || basinsMoved) {
            mLastDrawNs = frameTimeNanos;
            mTimeMs = period == Long.MAX_VALUE ? mFrozenTimeMs : nowMs;
            // Re-set the reaction uniforms while they animate, once more when the last one has
            // decayed (so it ends at zero), and when the panes moved; never on a plain ambient frame.
            mEventsStale |= active || mWasActive || basinsMoved;
            mWasActive = active;
            if (mGlassLowRes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                renderLowRes(nowMs);
            }
            FRAMES.tick();
            invalidate();
        }
        if (active || period != Long.MAX_VALUE) {
            mPosted = true;
            Choreographer.getInstance().postFrameCallback(mFrame);
        }
    }

    /** Re-reads the pane rects into the basin slots; true when they differ from the last read. */
    private boolean refreshBasins() {
        int n = mPanes.paneRects(mBasinScratch);
        return mEvents.setBasins(mBasinScratch, n);
    }

    private void renderLowRes(long nowMs) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        SceneLowRes low = mLowRes;
        if (low == null) {
            low = new SceneLowRes(w, h);
            mLowRes = low;
        }
        low.render(mEvents, nowMs, mTimeMs, mDensity, mEventsStale);
    }

    private void closeLowRes() {
        SceneLowRes low = mLowRes;
        mLowRes = null;
        if (low != null) low.close();
    }

    // ---- Drawing -----------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Trace.beginSection("Scene.contour.draw");
        try {
            ContourProgram program = mProgram;
            if (program == null) {
                program = new ContourProgram(1f);
                mProgram = program;
                mPaint.setShader(program.shader);
                mEventsStale = true;
            }
            program.setSize(w, h, mDensity);
            program.setTime(mTimeMs);
            if (mEventsStale) {
                program.setEvents(mEvents, SystemClock.uptimeMillis(), mDensity);
                mEventsStale = false;
            }
            canvas.drawRect(0f, 0f, w, h, mPaint);
        } finally {
            Trace.endSection();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        closeLowRes();
        kick();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDestroyed = false;
        kick();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(mFrame);
        mPosted = false;
        closeLowRes();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        kick();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        kick();
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        mScreenOn = screenState == SCREEN_STATE_ON;
        if (mScreenOn) kick();
    }

    /** Called when the controller removes the layer: stops frames and frees the offscreen target. */
    public void destroy() {
        mDestroyed = true;
        Choreographer.getInstance().removeFrameCallback(mFrame);
        mPosted = false;
        closeLowRes();
    }
}
