package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.RuntimeShader;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.app.chrome.WallpaperBackdropView;
import com.termux.shared.logger.Logger;

/**
 * Drives the live wallpaper (animated-wallpaper SPEC §3.6, §5). A {@link Choreographer} callback
 * asks the {@link WallpaperDirector} for the frame at every vsync it is posted for; the director
 * answers with the rate. fps 0 means paused and <b>no further callback is posted</b>: a paused
 * wallpaper costs nothing and the last published frames stay on screen. Otherwise the clock steps
 * at that rate by skipping vsyncs (30 on a 120 Hz panel is every 4th), starts a render, and when
 * it lands, on the main thread, in one message: publishes the frames, switches the backdrop to
 * the shader whose uniforms were written for that very frame, and asks the host to re-record the
 * frame readers. The backdrop and the glass therefore change in the same window frame.
 *
 * <p>Two backdrop shaders alternate: the uniforms for render N are written into the one that is
 * not on screen, and it goes on screen when N lands. That keeps the clock independent of whether
 * the director reuses its {@code Frame} object.</p>
 *
 * <p>Self-check: a failed budget window steps the director down a tier (30 to 15 fps, then the
 * cheaper source resolution, then 10 fps) and rebuilds the renderer at it; only the lowest tier
 * failing, or a render exception, ends in {@link #die}.</p>
 *
 * <p>The host feeds the director its inputs and calls {@link #kick} when a condition changed; it
 * owns no rules. The renderer is released 30 s after {@link #stop} and on {@link #trimMemory},
 * and rebuilt lazily by the next frame. All methods run on the main thread.</p>
 */
@RequiresApi(34)
public final class AnimatedWallpaperClock implements Choreographer.FrameCallback {

    private static final String TAG = "AnimatedWallpaperClock";
    /** How long the renderer outlives {@link #stop}. */
    static final long RELEASE_DELAY_MS = 30_000L;
    /** A vsync renders when at least this share of the frame interval has passed. */
    private static final float INTERVAL_SHARE = 0.9f;

    public interface Host {
        /** Re-record every reader of the shared frame: the backdrop, the glass, the tabs. */
        void invalidateFrameReaders();

        /** The director's pending lock is due: run the lock method now. */
        void onLockDue();

        /** The radii, in dp, that the visible surfaces draw right now (duplicates welcome). */
        @NonNull float[] liveRadiiDp();

        /**
         * A render threw, or the lowest tier still failed a budget window: the clock is dead until
         * {@link AnimatedWallpaperClock#revive}; the still shows.
         */
        void onRendererUnhealthy();

        /**
         * The launcher wants the picture to recede right now (terminal sheet open, keyboard up).
         * Asked every frame; only a living still shows it, as a focus blur.
         */
        default boolean focusWanted() {
            return false;
        }
    }

    @NonNull private final Host mHost;
    @NonNull private final WallpaperDirector mDirector;
    @NonNull private final WallpaperBackdropView mBackdrop;
    @NonNull private final LiveWallpaperFrames mFrames;
    private final float mDensity;
    /** The last vsync callback's time, and the shortest gap between two seen (the frame period). */
    private long mPrevVsyncNanos;
    private long mFramePeriodNanos;
    private static final int GAPS = 32;
    private final long[] mGaps = new long[GAPS];
    private int mGapCount;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Choreographer mChoreographer = Choreographer.getInstance();
    @NonNull private final Runnable mRelease = this::releaseAll;

    @Nullable private AnimatedWallpaper mWallpaper;
    @Nullable private LiveWallpaperRenderer mRenderer;
    private final RuntimeShader[] mBackdropShaders = new RuntimeShader[2];
    private int mNextShader;
    private int mPendingShader = -1;
    private int mFrameW;
    private int mFrameH;
    private boolean mRunning;
    private boolean mPosted;
    private boolean mDead;
    private long mLastRenderNanos;

    /**
     * @param frames  normally {@link LiveWallpaperFrames#get()}
     * @param density the screen density
     */
    public AnimatedWallpaperClock(@NonNull Host host, @NonNull WallpaperDirector director,
                                  @NonNull WallpaperBackdropView backdrop,
                                  @NonNull LiveWallpaperFrames frames, float density) {
        mHost = host;
        mDirector = director;
        mBackdrop = backdrop;
        mFrames = frames;
        mDensity = density;
    }

    /** The wallpaper to play, or null for none; everything of the previous one is let go. */
    public void setWallpaper(@Nullable AnimatedWallpaper wallpaper) {
        if (wallpaper == mWallpaper) return;
        mWallpaper = wallpaper;
        releaseRenderer();
        mBackdropShaders[0] = null;
        mBackdropShaders[1] = null;
        mPendingShader = -1;
        kick();
    }

    /**
     * The frame rect's size in px (the 1.5x-wide rect of the blur cache). A change is a rotation
     * or a resize: the published frames are dropped and the rings rebuilt.
     */
    public void setFrameSize(int frameW, int frameH) {
        if (frameW == mFrameW && frameH == mFrameH) return;
        mFrameW = frameW;
        mFrameH = frameH;
        LiveWallpaperRenderer renderer = mRenderer;
        if (renderer != null) {
            unpublish();
            renderer.rebuild(frameW, frameH);
        }
    }

    /** Begin (or resume) following the director; the launcher is visible. */
    public void start() {
        if (!mRunning) restartWarmup();
        mRunning = true;
        mHandler.removeCallbacks(mRelease);
        post();
    }

    /** Stop following the director; the renderer goes 30 s later. The last frames stay up. */
    public void stop() {
        mRunning = false;
        if (mPosted) {
            mChoreographer.removeFrameCallback(this);
            mPosted = false;
        }
        mHandler.removeCallbacks(mRelease);
        mHandler.postDelayed(mRelease, RELEASE_DELAY_MS);
    }

    /** A condition changed: ask the director again, even if the clock had stopped posting. */
    public void kick() {
        if (mRunning) post();
    }

    /** The screen came on or the device unlocked: the renderer's self-check ignores the next renders. */
    public void restartWarmup() {
        LiveWallpaperRenderer renderer = mRenderer;
        if (renderer != null) renderer.restartWarmup();
    }

    /**
     * A new chance after a kill: the clock lives again, from the top tier with a fresh renderer.
     * The host calls it at the next visible session.
     */
    public void revive() {
        if (!mDead) return;
        mDead = false;
        mDirector.resetTier();
        Logger.logInfo(TAG, "Live wallpaper retried from tier 0");
        kick();
    }

    /** Low memory: let the renderer and its rings go now; the next frame rebuilds them. */
    public void trimMemory() {
        mHandler.removeCallbacks(mRelease);
        releaseAll();
    }

    /**
     * The renderer goes, and with it the backdrop shaders of a living still: they hold its decoded
     * pictures, which the next frame decodes again. The other backgrounds' shaders are tiny.
     */
    private void releaseAll() {
        releaseRenderer();
        if (mWallpaper instanceof LivingStill) {
            mBackdropShaders[0] = null;
            mBackdropShaders[1] = null;
            mPendingShader = -1;
            LivingStillTextures.trim();
        }
    }

    /** True once the renderer was killed; nothing plays until {@link #revive}. */
    public boolean isDead() {
        return mDead;
    }

    private void post() {
        if (mPosted || !mRunning || mDead) return;
        mPosted = true;
        mChoreographer.postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mPosted = false;
        noteVsync(frameTimeNanos);
        if (!mRunning || mDead || mWallpaper == null) return;
        mDirector.setFocus(mHost.focusWanted());
        WallpaperDirector.Frame f = mDirector.frame(frameTimeNanos);
        if (f.lockDue) mHost.onLockDue();
        if (f.fps <= 0) {
            // Paused: post nothing more. kick() restarts this when a condition changes.
            mLastRenderNanos = 0L;
            return;
        }
        post();
        if (mFrameW <= 0 || mFrameH <= 0) return;
        long interval = (long) (1_000_000_000L / (double) f.fps * INTERVAL_SHARE);
        if (mLastRenderNanos != 0L && frameTimeNanos - mLastRenderNanos < interval) return;
        LiveWallpaperRenderer renderer = ensureRenderer();
        if (renderer == null || renderer.busy()) return;
        int shaderIndex = mNextShader;
        WallpaperUniforms.apply(mBackdropShaders[shaderIndex], f, mFrameW, mFrameH);
        if (renderer.render(f, mHost.liveRadiiDp(), this::onRendered)) {
            mPendingShader = shaderIndex;
            mLastRenderNanos = frameTimeNanos;
        } else if (!renderer.healthy()) {
            die();
        }
    }

    /**
     * The display's frame period, as the median of the last {@link #GAPS} gaps between back-to-back
     * vsync callbacks. Callback times jitter (on pong the shortest gap read 7.3 ms at 120 Hz), and a
     * skipped vsync makes a gap longer, so the median and not the extreme. It sets the renderer's
     * self-check limit.
     */
    private void noteVsync(long frameTimeNanos) {
        long gap = frameTimeNanos - mPrevVsyncNanos;
        mPrevVsyncNanos = frameTimeNanos;
        if (gap < 2_000_000L || gap > 50_000_000L) return;
        mGaps[mGapCount++ % GAPS] = gap;
        if (mGapCount < GAPS || mGapCount % GAPS != 0) return;
        long[] sorted = mGaps.clone();
        java.util.Arrays.sort(sorted);
        mFramePeriodNanos = sorted[GAPS / 2];
        LiveWallpaperRenderer renderer = mRenderer;
        if (renderer != null) renderer.setFramePeriodMs(mFramePeriodNanos / 1_000_000f);
    }

    @Nullable
    private LiveWallpaperRenderer ensureRenderer() {
        LiveWallpaperRenderer renderer = mRenderer;
        if (renderer != null) return renderer;
        AnimatedWallpaper wallpaper = mWallpaper;
        if (wallpaper == null) return null;
        try {
            if (mBackdropShaders[0] == null) {
                mBackdropShaders[0] = WallpaperUniforms.newShader(wallpaper);
                mBackdropShaders[1] = WallpaperUniforms.newShader(wallpaper);
            }
            int tier = mDirector.tier();
            renderer = new LiveWallpaperRenderer(mDensity, WallpaperUniforms.newShader(wallpaper),
                mFrames, tier, WallpaperDirector.tierLowRes(tier));
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(TAG, "Live wallpaper shader failed", t);
            die();
            return null;
        }
        renderer.rebuild(mFrameW, mFrameH);
        if (mFramePeriodNanos != 0L) renderer.setFramePeriodMs(mFramePeriodNanos / 1_000_000f);
        mRenderer = renderer;
        return renderer;
    }

    /** Main thread, a render landed. */
    private void onRendered(boolean ok, @NonNull float[] radiiDp, @NonNull Bitmap[] frames,
                            int count) {
        LiveWallpaperRenderer renderer = mRenderer;
        if (renderer == null || mDead) return;
        if (!ok || !renderer.healthy()) {
            die();
            return;
        }
        if (renderer.slow()) {
            stepDown();
            return;
        }
        int shader = mPendingShader;
        if (shader < 0) return;
        Trace.beginSection("LiveWallpaper.publish");
        try {
            mFrames.publish(radiiDp, frames, count);
            mBackdrop.setLiveShader(mBackdropShaders[shader]);
            mNextShader = 1 - shader;
            mPendingShader = -1;
            mHost.invalidateFrameReaders();
        } finally {
            Trace.endSection();
        }
    }

    /** A budget window failed: one tier down with a fresh renderer, or a kill at the lowest. */
    private void stepDown() {
        int from = mDirector.tier();
        if (!mDirector.stepDown()) {
            die();
            return;
        }
        Logger.logInfo(TAG, "Live wallpaper too slow at tier " + from + "; stepping down to tier "
            + mDirector.tier() + " (" + WallpaperDirector.tierFps(mDirector.tier()) + " fps"
            + (WallpaperDirector.tierLowRes(mDirector.tier()) ? ", reduced resolution" : "") + ")");
        releaseRenderer();
        kick();
    }

    /** The renderer is unusable: pause until the next visible session and show the still. */
    private void die() {
        if (mDead) return;
        mDead = true;
        if (mPosted) {
            mChoreographer.removeFrameCallback(this);
            mPosted = false;
        }
        Logger.logWarn(TAG, "Live wallpaper killed at tier " + mDirector.tier() + "; showing the still");
        releaseRenderer();
        mHost.onRendererUnhealthy();
    }

    /** Drops the published frames and the backdrop shader so every reader draws its still. */
    private void unpublish() {
        mFrames.clear();
        mBackdrop.setLiveShader(null);
        mPendingShader = -1;
        mHost.invalidateFrameReaders();
    }

    private void releaseRenderer() {
        LiveWallpaperRenderer renderer = mRenderer;
        mLastRenderNanos = 0L;
        if (renderer == null) return;
        mRenderer = null;
        unpublish();
        renderer.release();
    }
}
