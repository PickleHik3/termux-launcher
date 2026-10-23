package com.termux.app.chrome.scene;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.FrameProbe;

import java.util.function.BooleanSupplier;

/**
 * The wallpaper scene layer: one full-window {@link SurfaceView} at the default Z order, so the
 * display composites the launcher window over it and a scene frame never repaints the window. The
 * Contour scene is one RuntimeShader on a full-surface Paint, drawn into the view's Surface by a
 * dedicated {@link HardwareRenderer} whose content root is a single {@link RenderNode}: a frame
 * updates the uniforms, re-records that node and asks for {@code syncAndDraw}; the GPU work runs
 * on the renderer's own thread. No view of the launcher window is invalidated for a scene frame.
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
public final class SceneLayerView extends SurfaceView implements SurfaceHolder.Callback {

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
    @NonNull private final RenderNode mNode = new RenderNode("SceneContour");
    @Nullable private HardwareRenderer mRenderer;
    private int mSurfaceW, mSurfaceH;
    private boolean mSurfaceReady;
    /** The surface has no frame of ours yet (new or resized): draw once even when ambient is off. */
    private boolean mSurfaceBlank;
    @NonNull private final float[] mBasinScratch = new float[SceneEvents.MAX_BASINS * 4];
    @NonNull private final BooleanSupplier mLazyMode;
    @Nullable private final PaneSource mPanes;
    @Nullable private ContourProgram mProgram;
    @Nullable private SceneLowRes mLowRes;
    private final float mDensity;

    private long mPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_RATE_HZ);
    private long mGlassPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_GLASS_RATE_HZ);
    private long mLastLowNs = Long.MIN_VALUE / 2;
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
    private long mDrawCount;

    private final Choreographer.FrameCallback mFrame = this::onFrame;

    public SceneLayerView(@NonNull Context context, @Nullable PaneSource panes,
                          @NonNull BooleanSupplier lazyMode) {
        super(context);
        mPanes = panes;
        mLazyMode = lazyMode;
        mDensity = context.getResources().getDisplayMetrics().density;
        mFrozenTimeMs = SystemClock.uptimeMillis();
        mTimeMs = mFrozenTimeMs;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Default Z order on purpose (no setZOrderOnTop / media overlay): the surface sits below
        // the window, which the display composites over it.
        getHolder().addCallback(this);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> publishRect());
        mPaint.setAntiAlias(false);
    }

    private final int[] mScreenLoc = new int[2];

    /** Tells the glass where on screen the live frame lies: this layer's rect. */
    private void publishRect() {
        getLocationOnScreen(mScreenLoc);
        LiveSceneFrame.setRect(mScreenLoc[0], mScreenLoc[1], mScreenLoc[0] + getWidth(),
            mScreenLoc[1] + getHeight());
    }

    // ---- Configuration, from the controller's property polls ----------------------------------

    public void setRateHz(int hz) {
        long period = SceneFramePolicy.ambientPeriodNs(hz);
        if (period == mPeriodNs) return;
        if (period == Long.MAX_VALUE) mFrozenTimeMs = SystemClock.uptimeMillis();
        mPeriodNs = period;
        kick();
    }

    public void setGlassRateHz(int hz) {
        mGlassPeriodNs = SceneFramePolicy.ambientPeriodNs(hz);
    }

    public void setGlassLowRes(boolean lowRes) {
        if (lowRes == mGlassLowRes) return;
        mGlassLowRes = lowRes;
        if (!lowRes) closeLowRes();
        kick();
    }

    /** Scene frames drawn so far, for the invalidation diagnostic. */
    public long drawCount() {
        return mDrawCount;
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
            && mSurfaceReady && !mLazy;
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
            || basinsMoved || mSurfaceBlank) {
            mSurfaceBlank = false;
            mLastDrawNs = frameTimeNanos;
            mTimeMs = period == Long.MAX_VALUE ? mFrozenTimeMs : nowMs;
            // Re-set the reaction uniforms while they animate, once more when the last one has
            // decayed (so it ends at zero), and when the panes moved; never on a plain ambient frame.
            mEventsStale |= active || mWasActive || basinsMoved;
            mWasActive = active;
            if (mGlassLowRes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && SceneFramePolicy.shouldDraw(false, mGlassPeriodNs, mLastLowNs, frameTimeNanos)) {
                mLastLowNs = frameTimeNanos;
                renderLowRes(nowMs);
            }
            FRAMES.tick();
            mDrawCount++;
            drawScene(frameTimeNanos);
        }
        if (active || period != Long.MAX_VALUE) {
            mPosted = true;
            // At rest the main thread sleeps until the next ambient frame is due instead of
            // waking on every vsync just to skip it.
            long delayMs = SceneFramePolicy.nextCallbackDelayMs(active, period, mLastDrawNs,
                frameTimeNanos);
            if (delayMs > 0L) {
                Choreographer.getInstance().postFrameCallbackDelayed(mFrame, delayMs);
            } else {
                Choreographer.getInstance().postFrameCallback(mFrame);
            }
        }
    }

    /** Re-reads the pane rects into the basin slots; true when they differ from the last read. */
    private boolean refreshBasins() {
        int n = mPanes.paneRects(mBasinScratch);
        return mEvents.setBasins(mBasinScratch, n);
    }

    private void renderLowRes(long nowMs) {
        int w = mSurfaceW, h = mSurfaceH;
        if (w <= 0 || h <= 0) return;
        SceneLowRes low = mLowRes;
        if (low == null) {
            low = new SceneLowRes(w, h, new android.os.Handler(android.os.Looper.getMainLooper()));
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

    /** One scene frame: uniforms, one node re-recorded, one render request. Main thread. */
    private void drawScene(long frameTimeNanos) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        HardwareRenderer renderer = mRenderer;
        int w = mSurfaceW, h = mSurfaceH;
        if (renderer == null || !mSurfaceReady || w <= 0 || h <= 0) return;
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
            Canvas c = mNode.beginRecording(w, h);
            c.drawRect(0f, 0f, w, h, mPaint);
            mNode.endRecording();
            renderer.createRenderRequest().setVsyncTime(frameTimeNanos).syncAndDraw();
        } finally {
            Trace.endSection();
        }
    }

    // ---- Surface lifecycle ---------------------------------------------------------------------

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        Trace.beginSection("Scene.surface");
        try {
            HardwareRenderer renderer = mRenderer;
            if (renderer == null) {
                renderer = new HardwareRenderer();
                renderer.setName("SceneContour");
                renderer.setOpaque(true);
                mNode.setPosition(0, 0, 1, 1);
                renderer.setContentRoot(mNode);
                mRenderer = renderer;
            }
            renderer.setSurface(holder.getSurface());
            mSurfaceReady = true;
            mSurfaceBlank = true;
            mEventsStale = true;
        } finally {
            Trace.endSection();
        }
        kick();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        Trace.beginSection("Scene.surface");
        try {
            mSurfaceW = width;
            mSurfaceH = height;
            mNode.setPosition(0, 0, width, height);
            mSurfaceBlank = true;
            closeLowRes();
        } finally {
            Trace.endSection();
        }
        kick();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Trace.beginSection("Scene.surface");
        try {
            mSurfaceReady = false;
            Choreographer.getInstance().removeFrameCallback(mFrame);
            mPosted = false;
            HardwareRenderer renderer = mRenderer;
            if (renderer != null) renderer.setSurface(null);
            closeLowRes();
        } finally {
            Trace.endSection();
        }
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

    /** Called when the controller removes the layer: stops frames, frees the renderer and the offscreen target. */
    public void destroy() {
        mDestroyed = true;
        mSurfaceReady = false;
        Choreographer.getInstance().removeFrameCallback(mFrame);
        mPosted = false;
        closeLowRes();
        HardwareRenderer renderer = mRenderer;
        mRenderer = null;
        if (renderer != null) {
            Trace.beginSection("Scene.surface");
            renderer.destroy();
            Trace.endSection();
        }
        mNode.discardDisplayList();
    }
}
