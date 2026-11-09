package com.termux.app.chrome.scene;

import android.animation.ValueAnimator;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.FrameProbe;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * The wallpaper scene layer: one full-window {@link SurfaceView} at the default Z order, so the
 * display composites the launcher window over it and a scene frame never repaints the window. The
 * scene is the user's fragment shader ({@link ShaderPrelude}), drawn by OpenGL ES 3 on a context of
 * the layer's own: into a texture at the render scale ({@link ShaderSafety.Quality}), then
 * stretched onto the view's {@code Surface} and swapped. No view of the launcher window is
 * invalidated for a scene frame.
 *
 * <p>Threads. Everything that draws lives on one dedicated {@link HandlerThread} (the
 * <i>scene thread</i>): its own {@link Choreographer}, the EGL context ({@link SceneGl}), the
 * program, the channels, the low-resolution glass pass and its images, and the shader's inputs.
 * The main thread only (a) keeps the gates (visible, screen on, Lazy mode, animations), (b) hands
 * over the pointer through a lock-free {@link SceneEventRing}, (c) polls the keyboard height a few
 * times a second, and (d) marshals surface lifecycle. Config is volatile fields. A touch wakes the
 * scene thread only when it has gone idle ({@link #mAwake}); while it runs, the next frame finds
 * the record.</p>
 *
 * <p>Surface lifecycle: {@code surfaceCreated}/{@code surfaceChanged} post to the scene thread;
 * {@code surfaceDestroyed} and {@link #destroy} post and <b>wait</b> (bounded) for the scene thread
 * to let go of the surface, because the surface must not be touched once they return. A frame
 * callback re-checks the surface flag the scene thread itself owns, so nothing draws to a surface
 * after that runnable has run.</p>
 *
 * <p>Frame policy: a {@link Choreographer} frame callback runs while the layer could be seen and
 * skips frames down to the shader's rate ({@link SceneFramePolicy}). No callback is posted at all
 * while the layer is hidden, the screen is off, Lazy mode is on, or nothing moves (animations off,
 * battery saver, heat, a typing hold, a shader held for weight). The shader's clock runs only
 * while frames are drawn, so it continues where it paused ({@link ShaderInputs#advance}).</p>
 *
 * <p>Safety (spec decision 8): a lost context is recreated once, a second loss within
 * {@link ShaderSafety#LOSS_WINDOW_MS} reverts to photo through {@link Listener#onRevert}; heavy
 * frames step the quality down and at Quarter hold the still ({@link #heldReason}); after
 * {@link ShaderSafety#PENDING_CLEAR_MS} of drawing the pending marker is cleared.</p>
 */
public final class SceneLayerView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "SceneLayer";
    private static final long KEYBOARD_REFRESH_MS = 250L;
    private static final long GATE_REFRESH_MS = 1000L;
    /** The longest the main thread waits for the scene thread to let go of a surface. */
    private static final long HANDOFF_WAIT_MS = 1000L;
    /** How often the debug shader file's time stamp is looked at. */
    private static final long WATCH_MS = 1000L;
    private static final long BATTERY_MS = 30_000L;

    /** Why the layer asks to go back to photo. */
    public static final String REVERT_CONTEXT_LOST = "context_lost";
    public static final String REVERT_NO_GLES3 = "no_gles3";
    public static final String REVERT_COMPILE = "compile";

    /** What the layer runs: one shader file, its channel image and its quality. Immutable. */
    public static final class Spec {
        /** Two specs with the same key are the same layer (the controller keeps it). */
        @NonNull public final String key;
        @NonNull public final File source;
        @Nullable public final File channel0;
        @NonNull public final ShaderSafety.Quality quality;
        /** Debug route: reload the source when its time stamp moves, and never revert on errors. */
        public final boolean debugFile;

        public Spec(@NonNull String key, @NonNull File source, @Nullable File channel0,
                    @NonNull ShaderSafety.Quality quality, boolean debugFile) {
            this.key = key;
            this.source = source;
            this.channel0 = channel0;
            this.quality = quality;
            this.debugFile = debugFile;
        }
    }

    /** Main-thread callbacks. */
    public interface Listener {
        /** The shader cannot go on: the choice should go back to photo. {@code reason} is a REVERT_* constant. */
        void onRevert(@NonNull String reason);
    }

    private static final FrameProbe.Source FRAMES = FrameProbe.source("scene");

    // ---- Main thread ---------------------------------------------------------------------------

    @NonNull private final SceneEventRing mRing = new SceneEventRing();
    @NonNull private final Handler mMain = new Handler(Looper.getMainLooper());
    @NonNull private final BooleanSupplier mLazyMode;
    @NonNull private final Listener mListener;
    @NonNull private final int[] mScreenLoc = new int[2];
    private final float mDensity;
    @NonNull private final Spec mSpec;
    @NonNull private final Context mAppContext;
    @Nullable private Loop mLoop;
    private boolean mAnimatorsOn = true;
    private boolean mLazy;
    private boolean mScreenOn = true;
    private boolean mDestroyed;
    private boolean mMainSurfaceReady;
    private boolean mPolling;
    private long mGateAtMs = Long.MIN_VALUE;
    private boolean mReverted;

    // ---- Shared between the threads ------------------------------------------------------------

    /** The layer could be seen and may draw; written by the main thread, read by the scene thread. */
    private volatile boolean mRun;
    /** Ambient motion may run (animations are on). */
    private volatile boolean mMotion = true;
    private volatile long mPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_RATE_HZ);
    private volatile long mGlassPeriodNs = SceneFramePolicy.ambientPeriodNs(SceneFramePolicy.DEFAULT_GLASS_RATE_HZ);
    private volatile boolean mGlassLowRes = true;
    /** Battery saver or severe heat: no motion, the last frame held. */
    private volatile boolean mPowerHold;
    /** The render scale the user chose; the scene thread may step below it for weight. */
    private volatile ShaderSafety.Quality mQuality;
    /** Why the shader holds at its still on its own ({@link ShaderSafety#REASON_TOO_HEAVY}), or null. */
    @Nullable private volatile String mHeldReason;
    private volatile float mPageOffset, mKeyboard;
    /** The look's dark flag and colours (accent, surface, on-surface); applied when {@link #mLookGen} moves. */
    private volatile boolean mDark;
    private volatile int mAccent, mSurface, mOnSurface;
    private volatile int mLookGen;
    private static final com.termux.terminal.DebugChoice SHADER_GUARD = new com.termux.terminal.DebugChoice(
        SceneFramePolicy.PROP_SHADER_GUARD, "on", com.termux.terminal.DebugSwitch.SYSTEM_PROPERTIES);
    private static final com.termux.terminal.DebugChoice GLASS_DIV = new com.termux.terminal.DebugChoice(
        SceneFramePolicy.PROP_GLASS_DIV, Integer.toString(SceneFramePolicy.DEFAULT_GLASS_DIV),
        com.termux.terminal.DebugSwitch.SYSTEM_PROPERTIES);
    /** The scene thread has a frame callback pending or is mid-frame: it will see new work by itself. */
    private volatile boolean mAwake;
    private final AtomicBoolean mKickPending = new AtomicBoolean();
    private volatile long mDrawCount;

    @NonNull private final Runnable mPoll = this::poll;

    public SceneLayerView(@NonNull Context context, @NonNull Spec spec,
                          @NonNull BooleanSupplier lazyMode, @NonNull Listener listener) {
        super(context);
        mSpec = spec;
        mQuality = spec.quality;
        mAppContext = context.getApplicationContext();
        mLazyMode = lazyMode;
        mListener = listener;
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

    // ---- Configuration, from the controller ---------------------------------------------------

    public void setRateHz(int hz) {
        long period = SceneFramePolicy.ambientPeriodNs(hz);
        if (period == mPeriodNs) return;
        mPeriodNs = period;
        kick();
    }

    /** The user's quality; a change clears a hold for weight and redraws. */
    public void setQuality(@NonNull ShaderSafety.Quality quality) {
        if (quality == mSpec.quality && quality == mQuality) return;
        mQuality = quality;
        mHeldReason = null;
        kick();
    }

    /**
     * Battery saver or severe heat (see {@link SceneFramePolicy#holdAtStill}): while set the shader
     * holds its last frame and draws nothing more; when cleared it goes on from where it stopped.
     */
    public void setPowerHold(boolean hold) {
        if (hold == mPowerHold) return;
        mPowerHold = hold;
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

    /**
     * {@code tlPageOffset}, 0..1. Nothing feeds the scene a page position yet (the photo's parallax
     * lives in the backdrop), so it stays 0 until a caller sets it.
     */
    public void setPageOffset(float offset) {
        mPageOffset = Math.max(0f, Math.min(1f, offset));
    }

    /** {@code tlDark} and {@code tlColor0..2}: the look's accent, surface and on-surface (ARGB). */
    public void setLook(boolean dark, int accent, int surface, int onSurface) {
        if (dark == mDark && accent == mAccent && surface == mSurface && onSurface == mOnSurface
            && mLookGen != 0) {
            return;
        }
        mDark = dark;
        mAccent = accent;
        mSurface = surface;
        mOnSurface = onSurface;
        mLookGen++;
        kick();
    }

    /** Scene frames drawn so far, for the invalidation diagnostic. */
    public long drawCount() {
        return mDrawCount;
    }

    /** {@link ShaderSafety#REASON_TOO_HEAVY} while the shader holds at its still for weight, else null. */
    @Nullable
    public String heldReason() {
        return mHeldReason;
    }

    /** Which shader this layer runs: its {@link Spec#key}. */
    @NonNull
    public String key() {
        return mSpec.key;
    }

    // ---- The pointer: main thread, no lock, no allocation --------------------------------------

    /** A pointer record at screen px: {@link SceneEventRing#DOWN}, {@code MOVE} or {@code UP}. */
    public void touch(int kind, float rawX, float rawY) {
        if (kind == SceneEventRing.DOWN && !motionAllowed()) return;
        mRing.push(kind, rawX - mScreenLoc[0], rawY - mScreenLoc[1]);
        wake();
    }

    public void touchDown(float rawX, float rawY) {
        touch(SceneEventRing.DOWN, rawX, rawY);
    }

    public void touchMove(float rawX, float rawY) {
        touch(SceneEventRing.MOVE, rawX, rawY);
    }

    public void touchUp(float rawX, float rawY) {
        touch(SceneEventRing.UP, rawX, rawY);
    }

    /** How long the scene holds still after a key, so typing never shares the render thread. */
    static final long TYPING_HOLD_MS = 1500L;
    /** Uptime until which motion and the glass pass hold still; written on main. */
    private volatile long mHoldUntilMs;

    /**
     * A key was committed: motion and the glass pass stop until the typing pauses for
     * {@link #TYPING_HOLD_MS}. No wake: a running loop sees the hold on its next frame, and an
     * idle one has nothing to stop.
     */
    public void holdForTyping() {
        mHoldUntilMs = SystemClock.uptimeMillis() + TYPING_HOLD_MS;
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

    /** Recomputes whether the layer could be seen; starts the poll and wakes the scene thread when it can. */
    private void updateRun() {
        boolean run = !mDestroyed && mScreenOn && isShown() && getWindowVisibility() == VISIBLE
            && mMainSurfaceReady && !mLazy && SceneController.isSupported();
        mRun = run;
        if (!run) return;
        if (!mPolling) {
            mPolling = true;
            mMain.post(mPoll);
        }
        wake();
    }

    /** The keyboard height can only be read here: poll it a few times a second while the layer runs. */
    private void poll() {
        // mPolling stays set while this runs, so updateRun() does not post a second poll loop.
        refreshGates(SystemClock.uptimeMillis());
        updateRun();
        if (!mRun) {
            mPolling = false;
            return;
        }
        mKeyboard = keyboardFraction();
        mPolling = true;
        mMain.postDelayed(mPoll, KEYBOARD_REFRESH_MS);
    }

    /**
     * {@code tlKeyboard}: the system keyboard's height as a fraction of this layer's (API 30+, else
     * 0). The in-app keyboard is a view of the window, not an IME, and is not counted.
     */
    private float keyboardFraction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || getHeight() <= 0) return 0f;
        WindowInsets insets = getRootWindowInsets();
        if (insets == null) return 0f;
        int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        return Math.max(0f, Math.min(1f, ime / (float) getHeight()));
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

    private void revert(@NonNull String reason) {
        mMain.post(() -> {
            if (mReverted || mDestroyed) return;
            mReverted = true;
            mListener.onRevert(reason);
        });
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
        @NonNull private final SceneTouch mTouch = new SceneTouch();
        @NonNull private final ShaderInputs mIn = new ShaderInputs();
        @NonNull private final ShaderSafety.HeavyFrames mHeavy = new ShaderSafety.HeavyFrames();
        @NonNull private final ShaderSafety.ContextLoss mLoss = new ShaderSafety.ContextLoss();
        @Nullable private SceneGl mGl;
        @Nullable private EGLSurface mWindow;
        @Nullable private android.view.Surface mSurfaceObj;
        @Nullable private SceneLowRes mLowRes;
        /** The surface's size (the view's: the window surface follows the layout). */
        private int mSurfaceW, mSurfaceH;
        private boolean mSurfaceReady;
        /** The surface has no frame of ours yet (new or resized): draw once even when nothing moves. */
        private boolean mSurfaceBlank;
        private boolean mPosted;
        private boolean mChannelsPaused;
        private long mLastDrawNs = Long.MIN_VALUE / 2;
        private long mLastLowNs = Long.MIN_VALUE / 2;
        /** The quality frames are drawn at: the user's, or lower after heavy frames. */
        @Nullable private ShaderSafety.Quality mDrawQuality;
        @Nullable private ShaderSafety.Quality mSeenQuality;
        private int mSeenLookGen = -1;
        private long mSourceMtime = Long.MIN_VALUE;
        private long mWatchAtMs = Long.MIN_VALUE;
        private long mBatteryAtMs = Long.MIN_VALUE;
        private long mFirstDrawMs = Long.MIN_VALUE;
        private boolean mPendingCleared;
        private boolean mGaveUp;

        Loop() {
            thread = new HandlerThread("SceneThread", Process.THREAD_PRIORITY_DISPLAY);
            thread.start();
            handler = new Handler(thread.getLooper());
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
            if (!mRun || !mSurfaceReady || mSurfaceW <= 0 || mGaveUp) {
                mAwake = false;
                return;
            }
            mAwake = true;
            mPosted = true;
            choreographer().postFrameCallback(mFrame);
        }

        private void onFrame(long frameTimeNanos) {
            mPosted = false;
            if (!mRun || !mSurfaceReady || mGaveUp) {
                goIdle();
                return;
            }
            long nowMs = SystemClock.uptimeMillis();
            boolean touched = false;
            if (!mRing.isEmpty()) {
                mRing.drainInto(mTouch);
                touched = true;
            }
            boolean motion = mMotion;
            long holdUntil = mHoldUntilMs;
            boolean held = nowMs < holdUntil;
            boolean powerHold = mPowerHold;
            ShaderSafety.Quality quality = mQuality;
            if (quality != mSeenQuality) {
                // The user's choice wins over an earlier step-down, and a hold for weight ends.
                mSeenQuality = quality;
                mDrawQuality = quality;
                mHeavy.reset();
                mSurfaceBlank = true;
            }
            boolean heavyHold = mHeldReason != null;
            if (mLookGen != mSeenLookGen) {
                mSeenLookGen = mLookGen;
                mSurfaceBlank = true;
            }
            if (mSpec.debugFile && nowMs - mWatchAtMs >= WATCH_MS) {
                mWatchAtMs = nowMs;
                if (mSpec.source.lastModified() != mSourceMtime) {
                    loadShader();
                    mSurfaceBlank = true;
                }
            }
            boolean moving = motion && !held && !powerHold && !heavyHold;
            long period = moving ? mPeriodNs : Long.MAX_VALUE;
            if (period == Long.MAX_VALUE) mIn.pause();
            // With no ambient motion (rate 0) a touch still shows: one frame for it.
            boolean touchFrame = touched && moving && mPeriodNs == Long.MAX_VALUE;
            if (SceneFramePolicy.shouldDraw(false, period, mLastDrawNs, frameTimeNanos)
                || mSurfaceBlank || touchFrame) {
                mSurfaceBlank = false;
                mLastDrawNs = frameTimeNanos;
                if (period != Long.MAX_VALUE) mIn.advance(nowMs);
                else mIn.deltaSec = 0f;
                boolean glass = mGlassLowRes && !held
                    && SceneFramePolicy.shouldDraw(false, mGlassPeriodNs, mLastLowNs, frameTimeNanos);
                if (glass) mLastLowNs = frameTimeNanos;
                FRAMES.tick();
                mDrawCount++;
                drawScene(frameTimeNanos, nowMs, glass, period);
            }
            if (period != Long.MAX_VALUE && !mGaveUp) {
                mPosted = true;
                // At rest the thread sleeps until the next frame is due instead of waking on
                // every vsync just to skip it.
                long delayMs = SceneFramePolicy.nextCallbackDelayMs(false, period, mLastDrawNs,
                    frameTimeNanos);
                if (delayMs > 0L) choreographer().postFrameCallbackDelayed(mFrame, delayMs);
                else choreographer().postFrameCallback(mFrame);
            } else if (held && motion && !powerHold && !heavyHold) {
                // Typing: sleep until the hold ends, then pick the motion back up.
                mPosted = true;
                choreographer().postFrameCallbackDelayed(mFrame, Math.max(1L, holdUntil - nowMs));
            } else {
                // Going idle. Clear the flag, then look again: a record pushed just before the
                // clear saw the flag set and posted nothing.
                goIdle();
                if (!mRing.isEmpty()) start();
            }
        }

        private void goIdle() {
            mAwake = false;
            mIn.pause();
            pauseChannels();
        }

        private void pauseChannels() {
            SceneGl gl = mGl;
            if (gl == null || mChannelsPaused) return;
            mChannelsPaused = true;
            for (SceneChannel ch : gl.channels) if (ch != null) ch.pause();
        }

        private void resumeChannels() {
            SceneGl gl = mGl;
            if (gl == null || !mChannelsPaused) return;
            mChannelsPaused = false;
            for (SceneChannel ch : gl.channels) if (ch != null) ch.resume();
        }

        /** One frame: inputs, the shader into its texture, the glass pass, the blit, the swap. */
        private void drawScene(long frameTimeNanos, long nowMs, boolean glass, long period) {
            SceneGl gl = mGl;
            EGLSurface window = mWindow;
            int w = mSurfaceW, h = mSurfaceH;
            if (gl == null || window == null || !mSurfaceReady || w <= 0 || h <= 0) return;
            Trace.beginSection("Scene.draw");
            long startNs = System.nanoTime();
            try {
                if (!gl.egl.makeCurrent(window)) {
                    if (EGL14.eglGetError() == EGL14.EGL_CONTEXT_LOST) contextLost(nowMs);
                    return;
                }
                resumeChannels();
                ShaderSafety.Quality q = mDrawQuality != null ? mDrawQuality : mQuality;
                int fw = Math.max(1, Math.round(w * q.scale));
                int fh = Math.max(1, Math.round(h * q.scale));
                fillInputs(nowMs, fw / (float) w, fh / (float) h);
                boolean drew = gl.renderScene(mIn, fw, fh, frameTimeNanos);
                if (drew && glass) {
                    renderLowRes(gl, nowMs);
                    if (!gl.egl.makeCurrent(window)) return;
                }
                if (drew) {
                    gl.blitScene(w, h, false);
                } else {
                    // No shader (the debug file does not build): black, not a stale frame.
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                    GLES20.glViewport(0, 0, w, h);
                    GLES20.glClearColor(0f, 0f, 0f, 1f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                }
                int swap = gl.egl.swap(window);
                if (swap == EGL14.EGL_CONTEXT_LOST || gl.outOfMemory()) {
                    contextLost(nowMs);
                    return;
                }
                mIn.frame++;
                mTouch.frameDrawn();
                if (drew) afterDrawn(nowMs, System.nanoTime() - startNs, period, q);
            } finally {
                Trace.endSection();
            }
        }

        /** {@code scaleX}/{@code scaleY}: render-target pixels per view pixel, so iMouse shares iResolution's space. */
        private void fillInputs(long nowMs, float scaleX, float scaleY) {
            float viewH = mSurfaceH;
            mTouch.fill(mIn.mouse, viewH, scaleX, scaleY);
            mTouch.fillTouch(mIn.touch, viewH, scaleX, scaleY);
            mIn.pointerCount = mTouch.isDown() ? 1 : 0;
            mIn.pointers[0] = mIn.touch[0];
            mIn.pointers[1] = mIn.touch[1];
            mIn.pointers[2] = 0f;
            mIn.setDate(System.currentTimeMillis());
            if (nowMs - mBatteryAtMs >= BATTERY_MS) {
                mBatteryAtMs = nowMs;
                BatteryManager bm = mAppContext.getSystemService(BatteryManager.class);
                int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (pct >= 0 && pct <= 100) mIn.battery = pct / 100f;
            }
            mIn.pageOffset = mPageOffset;
            mIn.offset[0] = mPageOffset;
            mIn.offset[1] = 0f;
            mIn.keyboard = mKeyboard;
            mIn.dark = mDark ? 1f : 0f;
            mIn.setColor(0, mAccent);
            mIn.setColor(1, mSurface);
            mIn.setColor(2, mOnSurface);
        }

        /** The heavy-frame rule and the pending marker, after a frame the shader drew. */
        private void afterDrawn(long nowMs, long wallNs, long period, @NonNull ShaderSafety.Quality q) {
            if (mFirstDrawMs == Long.MIN_VALUE) mFirstDrawMs = nowMs;
            if (!mPendingCleared && ShaderSafety.pendingMayClear(mFirstDrawMs, nowMs)) {
                mPendingCleared = true;
                ShaderStore.clearPending(mAppContext);
            }
            // debug.tl.shader_guard=off: no step-down or hold (a CPU-rendered emulator is always heavy).
            if (!SceneFramePolicy.shaderGuard(SHADER_GUARD.get(nowMs))) {
                mHeavy.reset();
                return;
            }
            switch (mHeavy.onFrame(wallNs, period, q)) {
                case ShaderSafety.HeavyFrames.STEP_DOWN:
                    mDrawQuality = q.lower();
                    Log.w(TAG, "heavy frames: quality " + q + " -> " + mDrawQuality);
                    break;
                case ShaderSafety.HeavyFrames.HOLD:
                    Log.w(TAG, "heavy frames at quarter: holding the still");
                    mHeldReason = ShaderSafety.REASON_TOO_HEAVY;
                    break;
                default:
                    break;
            }
        }

        private void renderLowRes(@NonNull SceneGl gl, long nowMs) {
            int w = mSurfaceW, h = mSurfaceH;
            if (w <= 0 || h <= 0) return;
            SceneLowRes low = mLowRes;
            int div = SceneFramePolicy.parseGlassDiv(GLASS_DIV.get(nowMs));
            // A changed divisor rebuilds the target here, where it is sized, never per frame.
            if (low != null && low.divisor() != div) {
                closeLowRes();
                low = null;
            }
            if (low == null) {
                low = new SceneLowRes(w, h, div, handler, gl.egl);
                mLowRes = low;
            }
            low.render(gl, mDensity);
        }

        void closeLowRes() {
            SceneLowRes low = mLowRes;
            mLowRes = null;
            if (low != null) low.close();
            SceneGl gl = mGl;
            if (gl != null) gl.deleteLowTarget();
        }

        // ---- GL lifecycle ---------------------------------------------------------------------

        /** Makes the context, the window surface and the program; false (and a revert) when it cannot. */
        private boolean bringUpGl() {
            if (mGl == null) {
                try {
                    mGl = new SceneGl();
                } catch (SceneEgl.EglException e) {
                    Log.e(TAG, "no OpenGL ES 3 context: " + e.getMessage());
                    giveUp(REVERT_NO_GLES3);
                    return false;
                }
                mSeenLookGen = -1;
                loadShader();
                if (mSpec.channel0 != null) mGl.setChannel(0, new ImageChannel(mSpec.channel0));
                mChannelsPaused = false;
            }
            android.view.Surface surface = mSurfaceObj;
            if (mWindow == null && surface != null && surface.isValid()) {
                mWindow = mGl.egl.windowSurface(surface);
                if (mWindow == null) Log.w(TAG, "EGL refused the scene surface");
            }
            return mWindow != null;
        }

        /** (Re)reads the source file and builds the program; on failure the old one is dropped. */
        private void loadShader() {
            SceneGl gl = mGl;
            if (gl == null) return;
            mSourceMtime = mSpec.source.lastModified();
            String text = ShaderStore.readText(mSpec.source);
            if (text == null) {
                Log.e(TAG, "cannot read the shader file " + mSpec.source);
                gl.setShader(null);
                if (!mSpec.debugFile) giveUp(REVERT_COMPILE);
                return;
            }
            gl.egl.makeCurrentOffscreen();
            try {
                gl.setShader(GlShader.build(text));
                mHeavy.reset();
                Log.i(TAG, "shader built (" + gl.shader().built.flavour + ") from " + mSpec.source.getName());
            } catch (GlShader.CompileException e) {
                Log.e(TAG, "shader does not build:\n" + e.getMessage());
                gl.setShader(null);
                if (!mSpec.debugFile) giveUp(REVERT_COMPILE);
            }
        }

        private void contextLost(long nowMs) {
            int action = mLoss.onLoss(nowMs);
            Log.w(TAG, "GL context lost or out of memory; " + (action == ShaderSafety.ContextLoss.REVERT
                ? "second time within " + ShaderSafety.LOSS_WINDOW_MS + " ms, back to photo" : "recreating"));
            dropGl(true);
            if (action == ShaderSafety.ContextLoss.REVERT) {
                giveUp(REVERT_CONTEXT_LOST);
                return;
            }
            if (bringUpGl()) mSurfaceBlank = true;
        }

        private void giveUp(@NonNull String reason) {
            if (mGaveUp) return;
            mGaveUp = true;
            revert(reason);
        }

        /** Lets go of the glass pass, the window surface and (when {@code lost}) the dead context. */
        private void dropGl(boolean lost) {
            closeLowRes();
            SceneGl gl = mGl;
            if (gl == null) return;
            gl.egl.destroySurface(mWindow);
            mWindow = null;
            mGl = null;
            if (lost) gl.abandon();
            else gl.release();
        }

        // ---- Surface lifecycle, run here from the main thread's posts ---------------------------

        void attachSurface(@NonNull android.view.Surface surface) {
            Trace.beginSection("Scene.surface");
            try {
                mSurfaceObj = surface;
                mSurfaceReady = bringUpGl();
                mSurfaceBlank = true;
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
                goIdle();
                closeLowRes();
                SceneGl gl = mGl;
                if (gl != null) gl.egl.destroySurface(mWindow);
                mWindow = null;
                mSurfaceObj = null;
            } finally {
                Trace.endSection();
            }
        }

        /** Frees the context, its textures and the glass target; the thread is quit by the caller. */
        void teardown() {
            detachSurface();
            dropGl(false);
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
        if (!SceneController.isSupported() || mDestroyed) return;
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

    /** Called when the controller removes the layer: stops frames, frees the context and the offscreen targets. */
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
