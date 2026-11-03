package com.termux.app.chrome.scene;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.FrameProbe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * The wallpaper scene layer: one full-window {@link SurfaceView} at the default Z order, so the
 * display composites the launcher window over it and a scene frame never repaints the window. The
 * Contour scene is one RuntimeShader on a full-surface Paint, drawn into the view's Surface by a
 * dedicated {@link HardwareRenderer} whose content root is a single {@link RenderNode}: a frame
 * updates the uniforms, re-records that node and asks for {@code syncAndDraw}; the GPU work runs
 * on the renderer's own thread. No view of the launcher window is invalidated for a scene frame.
 *
 * <p>Threads. Everything that draws lives on one dedicated {@link HandlerThread} (the
 * <i>scene thread</i>): its own {@link Choreographer}, the renderer, the node, the program, the
 * low-resolution pass and its images, and a private {@link SceneEvents}. The main thread only
 * (a) keeps the gates (visible, screen on, Lazy mode, animations), (b) hands over reactions
 * through a lock-free {@link SceneEventRing}, (c) polls the pane rects a few times a second (they
 * can only be read on the main thread) and copies a changed set under a tiny lock, and (d)
 * marshals surface lifecycle. Config is volatile fields. A reaction wakes the scene thread only
 * when it has gone idle ({@link #mAwake}); while it runs, the next frame finds the record.</p>
 *
 * <p>Surface lifecycle: {@code surfaceCreated}/{@code surfaceChanged} post to the scene thread;
 * {@code surfaceDestroyed} and {@link #destroy} post and <b>wait</b> (bounded) for the scene thread
 * to let go of the surface, because the surface must not be touched once they return. A frame
 * callback re-checks the surface flag the scene thread itself owns, so nothing draws to a surface
 * after that runnable has run.</p>
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
    /** The longest the main thread waits for the scene thread to let go of a surface. */
    private static final long HANDOFF_WAIT_MS = 1000L;

    /** Where the layer finds the open panes: screen rects as (cx, cy, hw, hh), up to the array's room. */
    public interface PaneSource {
        int paneRects(@NonNull float[] out);
    }

    private static final FrameProbe.Source FRAMES = FrameProbe.source("scene");

    // ---- Main thread ---------------------------------------------------------------------------

    @NonNull private final SceneEventRing mRing = new SceneEventRing();
    @NonNull private final Handler mMain = new Handler(Looper.getMainLooper());
    /** Only compares pane rect sets, so a changed set is the only one handed over. */
    @NonNull private final SceneEvents mBasinProbe = new SceneEvents();
    @NonNull private final float[] mBasinScratch = new float[SceneEvents.MAX_BASINS * 4];
    @NonNull private final BooleanSupplier mLazyMode;
    @Nullable private final PaneSource mPanes;
    @NonNull private final int[] mScreenLoc = new int[2];
    private final float mDensity;
    @NonNull private final String mKind;
    /** What the Filament scene spells and draws with; unused by Contour. */
    @NonNull private final FilamentSource mFilament = new FilamentSource();
    @NonNull private final ScenePrograms.Factory mPrograms;
    private boolean mFilamentKind() { return ScenePrograms.FILAMENT.equals(mKind); }
    @Nullable private Loop mLoop;
    private boolean mAnimatorsOn = true;
    private boolean mLazy;
    private boolean mScreenOn = true;
    private boolean mDestroyed;
    private boolean mMainSurfaceReady;
    private boolean mPolling;
    private long mGateAtMs = Long.MIN_VALUE;

    // ---- Shared between the threads ------------------------------------------------------------

    /** The layer could be seen and may draw; written by the main thread, read by the scene thread. */
    private volatile boolean mRun;
    /** Ambient motion may run (animations are on). */
    private volatile boolean mMotion = true;
    private volatile long mPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_RATE_HZ);
    private volatile long mGlassPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_GLASS_RATE_HZ);
    private volatile boolean mGlassLowRes = true;
    /** The scene thread has a frame callback pending or is mid-frame: it will see new work by itself. */
    private volatile boolean mAwake;
    private final AtomicBoolean mKickPending = new AtomicBoolean();
    private volatile long mDrawCount;
    /** Changed pane rects, copied by the main thread, taken by the scene thread, under {@link #mBasinLock}. */
    @NonNull private final Object mBasinLock = new Object();
    @NonNull private final float[] mBasinBox = new float[SceneEvents.MAX_BASINS * 4];
    private int mBasinBoxCount;
    private volatile boolean mBasinDirty;

    @NonNull private final Runnable mPoll = this::poll;

    /** {@code kind}: {@link ScenePrograms#CONTOUR} or {@link ScenePrograms#FILAMENT}. */
    public SceneLayerView(@NonNull Context context, @NonNull String kind,
                          @Nullable PaneSource panes, @NonNull BooleanSupplier lazyMode) {
        super(context);
        mKind = kind;
        mPrograms = ScenePrograms.factory(kind, mFilament);
        mPanes = panes;
        mLazyMode = lazyMode;
        mDensity = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Default Z order on purpose (no setZOrderOnTop / media overlay): the surface sits below
        // the window, which the display composites over it.
        getHolder().addCallback(this);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> publishRect());
    }

    /** Tells the glass where on screen the live frame lies: this layer's rect. */
    private void publishRect() {
        getLocationOnScreen(mScreenLoc);
        LiveSceneFrame.setRoot(getRootView());
        LiveSceneFrame.setRect(mScreenLoc[0], mScreenLoc[1], mScreenLoc[0] + getWidth(),
            mScreenLoc[1] + getHeight());
    }

    // ---- Configuration, from the controller's property polls ----------------------------------

    public void setRateHz(int hz) {
        long period = SceneFramePolicy.ambientPeriodNs(hz);
        if (period == mPeriodNs) return;
        mPeriodNs = period;
        kick();
    }

    public void setGlassRateHz(int hz) {
        mGlassPeriodNs = SceneFramePolicy.ambientPeriodNs(hz);
    }

    public void setGlassLowRes(boolean lowRes) {
        if (lowRes == mGlassLowRes) return;
        mGlassLowRes = lowRes;
        if (!lowRes) {
            Loop loop = mLoop;
            if (loop != null) loop.handler.post(loop::closeLowRes);
        }
        kick();
    }

    /** Scene frames drawn so far, for the invalidation diagnostic. */
    public long drawCount() {
        return mDrawCount;
    }

    // ---- Reactions: main thread, no lock, no allocation ---------------------------------------

    /** Touch-down at screen px: a hill that relaxes over about a second. */
    public void touchDown(float x, float y) {
        if (!motionAllowed()) return;
        mRing.push(SceneEventRing.HILL, x, y, 0f, 0f, SystemClock.uptimeMillis());
        wake();
    }

    /** A committed key with the cursor at screen px: a spark along the nearest contour. */
    public void spark(float x, float y) {
        if (!motionAllowed()) return;
        mRing.push(SceneEventRing.SPARK, x, y, 0f, 0f, SystemClock.uptimeMillis());
        wake();
    }

    /** How long the scene holds still after a key, so typing never shares the render thread. */
    static final long TYPING_HOLD_MS = 1500L;
    /** Uptime until which ambient motion and the glass pass hold still; written on main. */
    private volatile long mHoldUntilMs;

    /**
     * A key was committed: ambient motion and the glass pass stop until the typing pauses for
     * {@link #TYPING_HOLD_MS}. Touch and bell reactions still play. No wake: a running loop sees
     * the hold on its next frame, and an idle one has nothing to stop.
     */
    public void holdForTyping() {
        mHoldUntilMs = SystemClock.uptimeMillis() + TYPING_HOLD_MS;
    }

    /** The bell of the pane whose screen rect is centred at (cx, cy) with half extents (hw, hh). */
    public void bell(float cx, float cy, float hw, float hh) {
        if (!motionAllowed()) return;
        mRing.push(SceneEventRing.PULSE, cx, cy, hw, hh, SystemClock.uptimeMillis());
        wake();
    }

    /** Which scene this layer runs. */
    @NonNull
    public String kind() {
        return mKind;
    }

    /**
     * Filament only: the focused window's name changed (the window chip's text). The name is
     * published at once; a record asks the scene thread to redraw and to play the cathode cycle,
     * which with animations off is skipped (the new name shows at once).
     */
    public void setWindowName(@Nullable String name, @Nullable android.graphics.Typeface typeface) {
        if (!mFilamentKind()) return;
        mFilament.setTypeface(typeface);
        String old = mFilament.name();
        if (name == null ? old == null : name.equals(old)) return;
        mFilament.setName(name);
        mRing.push(SceneEventRing.FOCUS, 0f, 0f, 0f, 0f, SystemClock.uptimeMillis());
        wake();
    }

    private boolean motionAllowed() {
        refreshGates(SystemClock.uptimeMillis());
        return mAnimatorsOn && !mLazy;
    }

    // ---- Gates and waking the scene thread -----------------------------------------------------

    /** Re-reads the gates and wakes the scene thread when there is a reason to run. */
    public void kick() {
        refreshGates(SystemClock.uptimeMillis());
        updateRun();
    }

    private void refreshGates(long nowMs) {
        if (mGateAtMs != Long.MIN_VALUE && nowMs - mGateAtMs < GATE_REFRESH_MS && nowMs >= mGateAtMs) {
            return;
        }
        mGateAtMs = nowMs;
        boolean animators = ValueAnimator.areAnimatorsEnabled();
        boolean lazy = mLazyMode.getAsBoolean();
        boolean changed = animators != mAnimatorsOn || lazy != mLazy;
        mAnimatorsOn = animators;
        mLazy = lazy;
        mMotion = animators;
        if (changed) updateRun();
    }

    /** Recomputes whether the layer could be seen; starts the pane poll and wakes the scene thread when it can. */
    private void updateRun() {
        boolean run = !mDestroyed && mScreenOn && isShown() && getWindowVisibility() == VISIBLE
            && mMainSurfaceReady && !mLazy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        mRun = run;
        if (!run) return;
        if (!mPolling) {
            mPolling = true;
            mMain.post(mPoll);
        }
        wake();
    }

    /** Pane rects change rarely and only the main thread can read them: poll here, hand over changes. */
    private void poll() {
        // mPolling stays set while this runs, so updateRun() does not post a second poll loop.
        refreshGates(SystemClock.uptimeMillis());
        updateRun();
        if (!mRun) {
            mPolling = false;
            return;
        }
        if (mPanes != null) {
            int n = mPanes.paneRects(mBasinScratch);
            if (mBasinProbe.setBasins(mBasinScratch, n)) {
                synchronized (mBasinLock) {
                    mBasinBoxCount = Math.min(n, SceneEvents.MAX_BASINS);
                    System.arraycopy(mBasinScratch, 0, mBasinBox, 0, mBasinBoxCount * 4);
                    mBasinDirty = true;
                }
                wake();
            }
        }
        mPolling = true;
        mMain.postDelayed(mPoll, BASIN_REFRESH_MS);
    }

    /**
     * Tells an idle scene thread there is work. A running one (callback pending) needs nothing: the
     * record is already published. The check is safe against the scene thread going idle at the same
     * time because it clears {@link #mAwake} first and re-reads the ring and the run flag after.
     */
    private void wake() {
        Loop loop = mLoop;
        if (loop == null || !mRun || mAwake) return;
        if (mKickPending.compareAndSet(false, true)) loop.handler.post(loop.kick);
    }

    // ---- The scene thread --------------------------------------------------------------------

    /** Everything that draws. Fields below are touched only on the scene thread unless noted. */
    private final class Loop {
        final HandlerThread thread;
        final Handler handler;
        @Nullable private Choreographer mChoreographer;
        final Runnable kick = () -> {
            mKickPending.set(false);
            start();
        };
        private final Choreographer.FrameCallback mFrame = this::onFrame;
        @NonNull private final SceneEvents mEvents = new SceneEvents();
        @NonNull private final Paint mPaint = new Paint();
        @NonNull private final RenderNode mNode = new RenderNode("SceneContour");
        @NonNull private final float[] mBasinTake = new float[SceneEvents.MAX_BASINS * 4];
        @Nullable private HardwareRenderer mRenderer;
        @Nullable private SceneProgram mProgram;
        @Nullable private SceneLowRes mLowRes;
        private int mSurfaceW, mSurfaceH;
        private boolean mSurfaceReady;
        /** The surface has no frame of ours yet (new or resized): draw once even when ambient is off. */
        private boolean mSurfaceBlank;
        private boolean mPosted;
        private long mLastDrawNs = Long.MIN_VALUE / 2;
        private long mLastLowNs = Long.MIN_VALUE / 2;
        /** The uptime the next draw shows; frozen while no ambient motion runs. */
        private long mTimeMs;
        private long mFrozenTimeMs = SystemClock.uptimeMillis();
        private boolean mFrozen;
        /** Time spent frozen so far: the scene clock resumes where it stopped instead of jumping. */
        private long mFrozenTotalMs;
        /** The reaction uniforms need re-setting before the next draw. */
        private boolean mEventsStale = true;
        private boolean mWasActive;

        Loop() {
            thread = new HandlerThread("SceneThread", Process.THREAD_PRIORITY_DISPLAY);
            thread.start();
            handler = new Handler(thread.getLooper());
            mTimeMs = mFrozenTimeMs;
            mPaint.setAntiAlias(false);
        }

        @NonNull
        private Choreographer choreographer() {
            Choreographer c = mChoreographer;
            if (c == null) {
                c = Choreographer.getInstance();   // this thread's, as it has its own Looper
                mChoreographer = c;
            }
            return c;
        }

        /** Posts the frame callback if nothing is waiting and there is a reason to run. */
        void start() {
            if (mPosted) return;
            if (!mRun || !mSurfaceReady || mSurfaceW <= 0) {
                mAwake = false;
                return;
            }
            mAwake = true;
            mPosted = true;
            choreographer().postFrameCallback(mFrame);
        }

        private void onFrame(long frameTimeNanos) {
            mPosted = false;
            if (!mRun || !mSurfaceReady) {
                mAwake = false;
                return;
            }
            long nowMs = SystemClock.uptimeMillis();
            if (!mRing.isEmpty()) {
                Trace.beginSection("Scene.event");
                mRing.drainInto(mEvents);
                mEventsStale = true;
                Trace.endSection();
            }
            boolean motion = mMotion;
            if (!motion && mEvents.focusAgeMs(nowMs) >= 0L) {
                // Animations are off: the new name shows at once, drawn once even though ambient is frozen.
                mEvents.clearFocus();
                mSurfaceBlank = true;
            }
            boolean active = motion && mEvents.active(nowMs);
            long holdUntil = mHoldUntilMs;
            boolean held = nowMs < holdUntil;
            long period = motion && !held ? mPeriodNs : Long.MAX_VALUE;
            if (period == Long.MAX_VALUE) {
                if (!mFrozen) mFrozenTimeMs = nowMs;
                mFrozen = true;
            } else {
                if (mFrozen) mFrozenTotalMs += nowMs - mFrozenTimeMs;
                mFrozen = false;
            }
            boolean basinsMoved = false;
            if (mBasinDirty) {
                int n;
                synchronized (mBasinLock) {
                    n = mBasinBoxCount;
                    System.arraycopy(mBasinBox, 0, mBasinTake, 0, n * 4);
                    mBasinDirty = false;
                }
                basinsMoved = mEvents.setBasins(mBasinTake, n);
            }
            if (SceneFramePolicy.shouldDraw(active, period, mLastDrawNs, frameTimeNanos)
                || basinsMoved || mSurfaceBlank) {
                mSurfaceBlank = false;
                mLastDrawNs = frameTimeNanos;
                mTimeMs = (period == Long.MAX_VALUE ? mFrozenTimeMs : nowMs) - mFrozenTotalMs;
                // Re-set the reaction uniforms while they animate, once more when the last one has
                // decayed (so it ends at zero), and when the panes moved; never on a plain ambient frame.
                mEventsStale |= active || mWasActive || basinsMoved;
                mWasActive = active;
                if (mGlassLowRes && !held
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
                // At rest the thread sleeps until the next ambient frame is due instead of
                // waking on every vsync just to skip it.
                long delayMs = SceneFramePolicy.nextCallbackDelayMs(active, period, mLastDrawNs,
                    frameTimeNanos);
                if (delayMs > 0L) choreographer().postFrameCallbackDelayed(mFrame, delayMs);
                else choreographer().postFrameCallback(mFrame);
            } else if (held && motion) {
                // Typing: sleep until the hold ends, then pick the ambient motion back up.
                mPosted = true;
                choreographer().postFrameCallbackDelayed(mFrame, Math.max(1L, holdUntil - nowMs));
            } else {
                // Going idle. Clear the flag, then look again: a record or a rect pushed just
                // before the clear saw the flag set and posted nothing.
                mAwake = false;
                if (!mRing.isEmpty() || mBasinDirty) start();
            }
        }

        private void renderLowRes(long nowMs) {
            int w = mSurfaceW, h = mSurfaceH;
            if (w <= 0 || h <= 0) return;
            SceneLowRes low = mLowRes;
            if (low == null) {
                low = new SceneLowRes(w, h, handler, mPrograms);
                mLowRes = low;
            }
            low.render(mEvents, nowMs, mTimeMs, mDensity, mEventsStale);
        }

        void closeLowRes() {
            SceneLowRes low = mLowRes;
            mLowRes = null;
            if (low != null) low.close();
        }

        /** One scene frame: uniforms, one node re-recorded, one render request. */
        private void drawScene(long frameTimeNanos) {
            HardwareRenderer renderer = mRenderer;
            int w = mSurfaceW, h = mSurfaceH;
            if (renderer == null || !mSurfaceReady || w <= 0 || h <= 0) return;
            Trace.beginSection("Scene.contour.draw");
            try {
                SceneProgram program = mProgram;
                if (program == null) {
                    program = mPrograms.create(1f);
                    mProgram = program;
                    mPaint.setShader(program.shader());
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

        // ---- Surface lifecycle, run here from the main thread's posts ---------------------------

        void attachSurface(@NonNull android.view.Surface surface) {
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
                renderer.setSurface(surface);
                mSurfaceReady = true;
                mSurfaceBlank = true;
                mEventsStale = true;
            } finally {
                Trace.endSection();
            }
            start();
        }

        void resize(int width, int height) {
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
            start();
        }

        /** After this runs the surface is not touched again until the next {@link #attachSurface}. */
        void detachSurface() {
            Trace.beginSection("Scene.surface");
            try {
                mSurfaceReady = false;
                if (mChoreographer != null) mChoreographer.removeFrameCallback(mFrame);
                mPosted = false;
                mAwake = false;
                HardwareRenderer renderer = mRenderer;
                if (renderer != null) renderer.setSurface(null);
                closeLowRes();
            } finally {
                Trace.endSection();
            }
        }

        /** Frees the renderer and the offscreen target; the thread is quit by the caller. */
        void teardown() {
            detachSurface();
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

    /** Runs {@code r} on the scene thread and waits (bounded) for it: the surface must be let go before a callback returns. */
    private void runAndWait(@NonNull Loop loop, @NonNull Runnable r) {
        CountDownLatch done = new CountDownLatch(1);
        boolean posted = loop.handler.post(() -> {
            try {
                r.run();
            } finally {
                done.countDown();
            }
        });
        if (!posted) return;
        try {
            done.await(HANDOFF_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Surface lifecycle (main thread) -------------------------------------------------------

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || mDestroyed) return;
        Loop loop = mLoop;
        if (loop == null) {
            loop = new Loop();
            mLoop = loop;
        }
        mMainSurfaceReady = true;
        final Loop l = loop;
        final android.view.Surface surface = holder.getSurface();
        loop.handler.post(() -> l.attachSurface(surface));
        updateRun();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Loop loop = mLoop;
        if (loop == null) return;
        publishRect();
        loop.handler.post(() -> loop.resize(width, height));
        updateRun();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        mMainSurfaceReady = false;
        mRun = false;
        Loop loop = mLoop;
        if (loop != null) runAndWait(loop, loop::detachSurface);
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
        mRun = false;
        mMain.removeCallbacks(mPoll);
        mPolling = false;
        Loop loop = mLoop;
        if (loop != null) loop.handler.post(loop::closeLowRes);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateRun();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRun();
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        mScreenOn = screenState == SCREEN_STATE_ON;
        updateRun();
    }

    /** Called when the controller removes the layer: stops frames, frees the renderer and the offscreen target. */
    public void destroy() {
        mDestroyed = true;
        mRun = false;
        mMainSurfaceReady = false;
        mMain.removeCallbacks(mPoll);
        mPolling = false;
        Loop loop = mLoop;
        mLoop = null;
        if (loop != null) {
            runAndWait(loop, loop::teardown);
            loop.thread.quitSafely();
        }
    }
}
