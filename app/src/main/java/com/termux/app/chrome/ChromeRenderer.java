package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The accessory chrome: the glass, blur, frost and backdrop treatment shared by the dock, the
 * in-app keyboard, the under-pill nav strip, the top pane, the command palette and the app drawer
 * plane.
 *
 * <p>All of it used to be ~130 methods and ~35 correlated fields on {@code TermuxActivity}, where
 * the pieces that must agree — the shared pre-blurred wallpaper frame, the per-surface "is the crop
 * still valid" bookkeeping, and the render pass that consumes both — sat over a thousand lines
 * apart. They are one mechanism, so they live in one module: the callers say <em>what changed</em>
 * through {@link #requestSync(int)} and the module decides what that costs.</p>
 *
 * <p>Everything the module needs from the Activity goes through {@link Surfaces}, which is what
 * makes the ordering testable without a window: build the spec, apply it, then let the pre-draw
 * gates run.</p>
 */
public final class ChromeRenderer {

    /**
     * The Activity-side slots and lookups the chrome renders into. It is also the blur cache's
     * {@link WallpaperBlurCache.Source}: the wallpaper the frames are captured from is the same
     * wallpaper the rest of the chrome reads.
     */
    public interface Surfaces extends WallpaperBlurCache.Source {

        @NonNull Context context();

        /** Resolves a chrome view slot by id; null before inflation, or when the slot is absent. */
        @Nullable View findChromeView(int viewId);

        @Nullable TermuxAppSharedPreferences preferences();

        float dpToPx(float dp);

        // ---- theme values the glass material is mixed from

        int glassBaseColor();

        int accentColor();

        int outlineColor();

        /** True while the dock (and the surfaces that follow it) render as floating capsules. */
        boolean roundedDockStyle();

        /** Corner radius baked into the status bar's containing stroke, 0 when it has none. */
        float statusBarRimCornerRadiusPx();

        // ---- the wallpaper the blurred frames are captured from: WallpaperBlurCache.Source

        /** Blurs a captured frame with the shared renderer; a fake overrides this to skip the blur. */
        @Nullable
        @Override
        default Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
            return WallpaperBlurRenderer.preBlur(context(), sourceBitmap, blurRadiusDp);
        }

        // ---- chrome state

        boolean isActivityVisible();

        boolean wallpaperPassthroughEnabled();


        /** The dock's effective blur radius (0 while a live wallpaper or the slider disables it). */
        int effectiveDockBlurRadiusDp();

        /** The status bar's own effective blur radius; tuned apart from the dock's. */
        int effectiveStatusBarBlurRadiusDp();

        // ---- the render pass

        @NonNull ChromeSpec buildChromeSpec();

        void applyChromeSpec(@NonNull ChromeSpec spec);

        void enforceAccessoryFxInvariants();

        /** The terminal pane's own glass frost, which rides the same triggers as the top pane's. */
        void updateTerminalGlassFrost();

        /** Whether the blurred surfaces actually have a backdrop installed for this spec. */
        boolean isBlurHealthy(@NonNull ChromeSpec spec);
    }

    // ------------------------------------------------------------------ scopes

    /** Coalesced accessory re-render: one build+apply plus the FX invariants, next main-loop pass. */
    public static final int SCOPE_ACCESSORY_RENDER = 1;
    /** Invalidates the dock/unified accessory crop alone: its geometry moved under a settled plane. */
    public static final int SCOPE_DOCK_BACKDROP = 1 << 1;
    /** Invalidates the under-pill nav strip's crop alone: the strip was rebuilt or re-laid out. */
    public static final int SCOPE_NAV_STRIP_BACKDROP = 1 << 7;
    /**
     * Invalidates the dock/unified accessory crop and the under-pill nav strip's crop — what the
     * old reason-keyword path ({@code "wallpaper"}, {@code "style"}, {@code "blur"}) marked dirty.
     */
    public static final int SCOPE_BACKDROPS = SCOPE_DOCK_BACKDROP | SCOPE_NAV_STRIP_BACKDROP;
    /** Invalidates the keyboard-local crop as well; only the paths that touched all three pass it. */
    public static final int SCOPE_KEYBOARD_BACKDROP = 1 << 2;
    /** Throws away the shared pre-blurred wallpaper frames — the most expensive thing to request. */
    public static final int SCOPE_WALLPAPER_BLUR_CACHE = 1 << 3;
    /** Re-cuts the top pane's wallpaper frost (status inset band + window-bar pane) now. */
    public static final int SCOPE_TOP_PANE_FROST = 1 << 4;
    /**
     * Builds and applies a spec once before the frame this request lands in is laid out, no matter
     * how many callers ask for it in the meantime.
     *
     * <p>This used to run inline, inside the caller. Every path that moves the wall or re-lays the
     * dock asks for it several times over — the inset listener, the geometry pass, the place look,
     * the toolbar toggle each behaved as if they were the only caller — and two page changes cost
     * 27–38 full applies, 1.2–1.3 s of main thread, for a handful of distinct states (measured on
     * Pong, 2026-09-08). What every caller actually needs is "applied before the user sees the next
     * frame", so the apply now rides the frame's animation phase: after input, before layout and
     * draw. A request made from inside a traversal (an inset dispatch, a layout listener) has
     * missed that phase, so a pre-draw gate commits it in the same frame instead, re-running the
     * layout when the apply moved anything.</p>
     */
    public static final int SCOPE_APPLY_THIS_FRAME = 1 << 5;
    /** Restarts the blur backstop heartbeat and arms the short recovery retry. */
    public static final int SCOPE_BLUR_HEALTH = 1 << 6;

    private static final long ACCESSORY_BLUR_BACKSTOP_MS = 300_000L;
    private static final long ACCESSORY_BLUR_RECOVERY_RETRY_MS = 120L;

    @NonNull private final Surfaces mSurfaces;
    @Nullable private final Executor mBlurWorker;
    private boolean mDestroyed;
    @NonNull private final SurfaceDirtyLedger mLedger = new SurfaceDirtyLedger();
    @NonNull private final WallpaperBlurCache mBlurCache;
    @NonNull private final GlassSurfaceFactory mGlass;
    @NonNull private final WallpaperFrostPainter mFrost;

    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRenderSyncPending;
    /** True only while {@link #mRenderSyncRunnable} is on the stack. */
    private boolean mRenderSyncRunning;
    /** Set when the render pass's own apply asked for another render pass. */
    private boolean mRenderSyncAskedForItself;

    /** True while a {@link #SCOPE_APPLY_THIS_FRAME} commit waits for its frame. */
    private boolean mCommitPending;
    /** The view the pending commit is riding; null when it fell back to a plain post. */
    @Nullable private View mCommitGateView;
    private final Runnable mCommitRunnable = this::commit;
    private final ViewTreeObserver.OnPreDrawListener mCommitPreDrawListener = this::commitBeforeDraw;

    private final Runnable mRenderSyncRunnable;
    private final Runnable mBlurHeartbeatRunnable;
    private final Runnable mBlurRecoveryRunnable;

    /** The production renderer: wallpaper decodes and blurs run on their own thread. */
    public ChromeRenderer(@NonNull Surfaces surfaces) {
        this(surfaces, newBlurWorker());
    }

    /**
     * {@code blurWorker} null keeps every blur-cache miss synchronous, which is what a window-less
     * test wants; the Activity passes a worker so a miss costs the main thread nothing but a
     * re-render when the frame lands.
     */
    public ChromeRenderer(@NonNull Surfaces surfaces, @Nullable Executor blurWorker) {
        mSurfaces = surfaces;
        mBlurWorker = blurWorker;
        mRenderSyncRunnable = () -> {
            mRenderSyncPending = false;
            mRenderSyncAskedForItself = false;
            // What the pass is for is re-cutting the crops that went stale under settled
            // geometry. Anything the apply below invalidates is therefore work this pass cannot
            // do — it has already read the geometry — and earns the one follow-up pass; anything
            // it merely asks for again does not. Two page changes cost 37 of these passes for a
            // handful of distinct states, because the apply asks unconditionally (Pong, 2026-09-09).
            long dirtyBefore = mLedger.dirtyGeneration();
            mRenderSyncRunning = true;
            try {
                mSurfaces.applyChromeSpec(mSurfaces.buildChromeSpec());
                mSurfaces.enforceAccessoryFxInvariants();
            } finally {
                mRenderSyncRunning = false;
            }
            if (mRenderSyncAskedForItself && mLedger.dirtyGeneration() != dirtyBefore) {
                requestSync(SCOPE_ACCESSORY_RENDER);
            }
        };
        mBlurHeartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mSurfaces.isActivityVisible()) {
                    return;
                }
                ChromeSpec spec = mSurfaces.buildChromeSpec();
                if (!spec.toolbarShown || !spec.blurEnabled) {
                    return;
                }
                if (!mSurfaces.isBlurHealthy(spec)) {
                    mLedger.markAllBackdropsDirty();
                    requestSync(SCOPE_BACKDROPS | SCOPE_ACCESSORY_RENDER);
                }
                mHandler.postDelayed(this, ACCESSORY_BLUR_BACKSTOP_MS);
            }
        };
        mBlurRecoveryRunnable = () -> {
            if (!mSurfaces.isActivityVisible()) {
                return;
            }
            ChromeSpec spec = mSurfaces.buildChromeSpec();
            if (!spec.toolbarShown || !spec.blurEnabled) {
                return;
            }
            if (!mSurfaces.isBlurHealthy(spec)) {
                mLedger.markAllBackdropsDirty();
            }
            requestSync(SCOPE_BACKDROPS | SCOPE_ACCESSORY_RENDER);
        };
        // Every frost crop was cut from a frame that a clear destroys.
        mBlurCache = new WallpaperBlurCache(surfaces, mLedger::markFrostDirty,
            WallpaperBlurCache.DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES, blurWorker,
            blurWorker == null ? null : mHandler::post,
            blurWorker == null ? null : this::onBlurFrameReady);
        mGlass = new GlassSurfaceFactory(surfaces);
        mFrost = new WallpaperFrostPainter(surfaces, mBlurCache, mLedger);
    }

    /** One low-priority thread: decodes and blurs are sequential, and never on the main thread. */
    @NonNull
    public static Executor newBlurWorker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wallpaper-blur");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    /**
     * A frame the worker finished has just been filed. Every surface that drew nothing for want of
     * it re-cuts its crop now: the accessory backdrops, the keyboard's, the top pane's frost and
     * the terminal glass, then one coalesced render.
     */
    private void onBlurFrameReady() {
        if (mDestroyed) return;
        mLedger.markAllBackdropsDirty();
        mLedger.markFrostDirty();
        mSurfaces.updateTerminalGlassFrost();
        requestSync(SCOPE_BACKDROPS | SCOPE_KEYBOARD_BACKDROP | SCOPE_TOP_PANE_FROST
            | SCOPE_ACCESSORY_RENDER);
    }

    // ------------------------------------------------------------------- entry

    /**
     * The chrome's single "something changed" entry point. Each bit is exactly one of the requests
     * the Activity used to make by hand, so a call site keeps costing precisely what it did before:
     * a layout pass asks for {@link #SCOPE_ACCESSORY_RENDER} alone, and only a radius change may
     * ask for {@link #SCOPE_WALLPAPER_BLUR_CACHE}.
     *
     * <p>Work runs in dependency order — drop the shared frames, invalidate the crops that were cut
     * from them, then re-render — and both passes are coalesced: the apply to one commit before the
     * frame's layout ({@link #SCOPE_APPLY_THIS_FRAME}), the accessory render to one pass after it
     * ({@link #SCOPE_ACCESSORY_RENDER}), no matter how many callers ask for either.</p>
     */
    public void requestSync(int scopes) {
        if (scopes == 0) {
            return;
        }
        // The scopes ride in the section name so a trace shows which kind of request each caller
        // made; the concatenation only happens while a trace is being recorded.
        Trace.beginSection(tracing() ? "Chrome.requestSync " + scopes : "Chrome.requestSync");
        try {
            sync(scopes);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Whether a trace is being recorded, and whether we may even ask. {@code Trace.isEnabled}
     * arrived in API 29 and this app runs from 26, where the call site does not resolve at all —
     * every chrome request threw {@link NoSuchMethodError} on Android 8 and 9, which for a home
     * screen is the whole screen. Below 29 the plain section name is used.
     */
    private static boolean tracing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled();
    }

    private void sync(int scopes) {
        if ((scopes & SCOPE_WALLPAPER_BLUR_CACHE) != 0) {
            mBlurCache.clear();
        }
        if ((scopes & SCOPE_DOCK_BACKDROP) != 0) {
            mLedger.markDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY);
        }
        if ((scopes & SCOPE_NAV_STRIP_BACKDROP) != 0) {
            mLedger.markDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR);
        }
        if ((scopes & SCOPE_KEYBOARD_BACKDROP) != 0) {
            mLedger.markDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD);
        }
        if ((scopes & SCOPE_APPLY_THIS_FRAME) != 0) {
            scheduleCommit();
        }
        if ((scopes & SCOPE_TOP_PANE_FROST) != 0) {
            Trace.beginSection("Frost.updateTopPane");
            try {
                mFrost.updateTopPane();
            } finally {
                Trace.endSection();
            }
        }
        if ((scopes & SCOPE_ACCESSORY_RENDER) != 0) {
            if (mRenderSyncRunning) {
                // The pass that would run is the pass asking. It decides for itself, once, at the
                // end of its run — from whether it left anything stale — rather than each caller
                // inside it booking a successor.
                mRenderSyncAskedForItself = true;
            } else if (!mRenderSyncPending) {
                mRenderSyncPending = true;
                mHandler.post(mRenderSyncRunnable);
            }
        }
        if ((scopes & SCOPE_BLUR_HEALTH) != 0) {
            restartBlurHeartbeat();
            scheduleBlurRecovery();
        }
    }

    /** True while a coalesced accessory render is waiting for its main-loop turn. */
    public boolean isRenderSyncPending() {
        return mRenderSyncPending;
    }

    /** True while a {@link #SCOPE_APPLY_THIS_FRAME} commit is waiting for its frame. */
    public boolean isCommitPending() {
        return mCommitPending;
    }

    // ------------------------------------------------------------------ commit

    /**
     * Books the one apply pass for this frame. On an attached window it rides the root view's
     * animation phase, which the platform runs after input and before layout, so the layout that
     * follows already sees the applied visibilities; a pre-draw gate on the same view catches a
     * request made after that phase had passed. Without an attached root — before the first
     * layout, after {@code onStop}, in a window-less test — a plain post keeps the old contract.
     */
    private void scheduleCommit() {
        if (mCommitPending) {
            return;
        }
        mCommitPending = true;
        View gate = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        if (gate == null || !gate.isAttachedToWindow()) {
            mHandler.post(mCommitRunnable);
            return;
        }
        mCommitGateView = gate;
        gate.postOnAnimation(mCommitRunnable);
        ViewTreeObserver observer = gate.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(mCommitPreDrawListener);
        }
    }

    private void commit() {
        if (!mCommitPending) {
            return;
        }
        unscheduleCommit();
        Trace.beginSection("Chrome.commit");
        try {
            mSurfaces.applyChromeSpec(mSurfaces.buildChromeSpec());
        } finally {
            Trace.endSection();
        }
    }

    /**
     * The pre-draw gate: a commit still pending here was requested from inside this traversal.
     * Apply it now, and when the apply moved a view, cancel this draw so the layout runs again
     * before anything is shown — one frame later beats one frame wrong.
     */
    private boolean commitBeforeDraw() {
        if (!mCommitPending) {
            return true;
        }
        View gate = mCommitGateView;
        commit();
        return gate == null || !gate.isLayoutRequested();
    }

    private void unscheduleCommit() {
        mCommitPending = false;
        View gate = mCommitGateView;
        mCommitGateView = null;
        mHandler.removeCallbacks(mCommitRunnable);
        if (gate == null) {
            return;
        }
        gate.removeCallbacks(mCommitRunnable);
        ViewTreeObserver observer = gate.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(mCommitPreDrawListener);
        }
    }

    /**
     * A new wallpaper (or a wallpaper the app can suddenly read) invalidates every pre-blurred
     * frame and every crop taken from one.
     */
    public void onWallpaperChanged() {
        requestSync(SCOPE_WALLPAPER_BLUR_CACHE | SCOPE_BACKDROPS | SCOPE_ACCESSORY_RENDER);
    }

    /**
     * Every pre-blurred wallpaper frame describes the orientation and frame it was captured in; a
     * rotation makes all of them wrong at once. The same callback also carries changes that leave
     * the frames right — a hardware keyboard, navigation, screen layout — so the cache decides by
     * comparing its recorded source rather than clearing on every call.
     */
    public void onConfigurationChanged() {
        mBlurCache.dropIfSourceMoved();
    }

    /**
     * Real memory pressure ({@link ChromePolicy#trimReleasesBlurFrames}): a home app that keeps
     * several full-screen blur bitmaps alive is exactly what aggressive vendor memory killers reap
     * first. Everything released here is rebuilt on demand through the ledger — one decode and
     * blur per radius on the way back in, which is why an ordinary trip to the background does not
     * reach this method.
     */
    public void onTrimMemory() {
        mBlurCache.clear();
        mLedger.markAllBackdropsDirty();
    }

    /**
     * Drops every pending chrome pass — the frame's commit, the coalesced render, the blur backstop
     * heartbeat and the short recovery retry. For the paths that are tearing the visible chrome
     * down (onStop).
     */
    public void cancelPendingWork() {
        unscheduleCommit();
        mHandler.removeCallbacks(mRenderSyncRunnable);
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        mHandler.removeCallbacks(mBlurRecoveryRunnable);
        mRenderSyncPending = false;
        mRenderSyncAskedForItself = false;
    }

    /**
     * The narrower cancel the in-place session recovery does: the pending render and the backstop
     * heartbeat go, but the short recovery retry stays armed so a reset that lands mid-blur still
     * gets its follow-up pass, and a commit already booked for this frame still lands — the
     * activity stays on screen through a recovery.
     */
    public void cancelPendingRender() {
        mHandler.removeCallbacks(mRenderSyncRunnable);
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        mRenderSyncPending = false;
        mRenderSyncAskedForItself = false;
    }

    public void onDestroy() {
        mDestroyed = true;
        if (mBlurWorker instanceof ExecutorService) ((ExecutorService) mBlurWorker).shutdownNow();
        unscheduleCommit();
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        mHandler.removeCallbacks(mBlurRecoveryRunnable);
        mBlurCache.clear();
    }

    // ------------------------------------------------------------- collaborators

    /**
     * The per-surface "what is painted where" bookkeeping. For the render pass that installs and
     * checks crops; a caller that only wants to say a crop went stale uses {@link #requestSync}.
     */
    @NonNull
    public SurfaceDirtyLedger ledger() {
        return mLedger;
    }

    @NonNull
    public WallpaperBlurCache blurCache() {
        return mBlurCache;
    }

    @NonNull
    public GlassSurfaceFactory glass() {
        return mGlass;
    }

    @NonNull
    public WallpaperFrostPainter frost() {
        return mFrost;
    }

    // ------------------------------------------------------------- blur health

    private void restartBlurHeartbeat() {
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        ChromeSpec spec = mSurfaces.buildChromeSpec();
        if (mSurfaces.isActivityVisible() && spec.toolbarShown && spec.blurEnabled) {
            mHandler.postDelayed(mBlurHeartbeatRunnable, ACCESSORY_BLUR_BACKSTOP_MS);
        }
    }

    private void scheduleBlurRecovery() {
        mHandler.removeCallbacks(mBlurRecoveryRunnable);
        ChromeSpec spec = mSurfaces.buildChromeSpec();
        if (mSurfaces.isActivityVisible() && spec.toolbarShown && spec.blurEnabled) {
            mHandler.postDelayed(mBlurRecoveryRunnable, ACCESSORY_BLUR_RECOVERY_RETRY_MS);
        }
    }
}
