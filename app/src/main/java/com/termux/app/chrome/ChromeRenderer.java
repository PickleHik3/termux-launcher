package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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

        boolean fullStatusBarEngaged();

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
    /** Builds and applies a spec synchronously, before this call returns. */
    public static final int SCOPE_APPLY_NOW = 1 << 5;
    /** Restarts the blur backstop heartbeat and arms the short recovery retry. */
    public static final int SCOPE_BLUR_HEALTH = 1 << 6;

    private static final long ACCESSORY_BLUR_BACKSTOP_MS = 300_000L;
    private static final long ACCESSORY_BLUR_RECOVERY_RETRY_MS = 120L;

    @NonNull private final Surfaces mSurfaces;
    @NonNull private final SurfaceDirtyLedger mLedger = new SurfaceDirtyLedger();
    @NonNull private final WallpaperBlurCache mBlurCache;
    @NonNull private final GlassSurfaceFactory mGlass;
    @NonNull private final WallpaperFrostPainter mFrost;

    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRenderSyncPending;

    private final Runnable mRenderSyncRunnable;
    private final Runnable mBlurHeartbeatRunnable;
    private final Runnable mBlurRecoveryRunnable;

    public ChromeRenderer(@NonNull Surfaces surfaces) {
        mSurfaces = surfaces;
        mRenderSyncRunnable = () -> {
            mRenderSyncPending = false;
            mSurfaces.applyChromeSpec(mSurfaces.buildChromeSpec());
            mSurfaces.enforceAccessoryFxInvariants();
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
        mBlurCache = new WallpaperBlurCache(surfaces, mLedger::markFrostDirty);
        mGlass = new GlassSurfaceFactory(surfaces);
        mFrost = new WallpaperFrostPainter(surfaces, mBlurCache, mLedger);
    }

    // ------------------------------------------------------------------- entry

    /**
     * The chrome's single "something changed" entry point. Each bit is exactly one of the requests
     * the Activity used to make by hand, so a call site keeps costing precisely what it did before:
     * a layout pass asks for {@link #SCOPE_ACCESSORY_RENDER} alone, and only a radius change may
     * ask for {@link #SCOPE_WALLPAPER_BLUR_CACHE}.
     *
     * <p>Work runs in dependency order — drop the shared frames, invalidate the crops that were cut
     * from them, then re-render — and the accessory render is coalesced to one pass per main-loop
     * turn no matter how many callers ask for it.</p>
     */
    public void requestSync(int scopes) {
        if (scopes == 0) {
            return;
        }
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
        if ((scopes & SCOPE_APPLY_NOW) != 0) {
            mSurfaces.applyChromeSpec(mSurfaces.buildChromeSpec());
        }
        if ((scopes & SCOPE_TOP_PANE_FROST) != 0) {
            mFrost.updateTopPane();
        }
        if ((scopes & SCOPE_ACCESSORY_RENDER) != 0 && !mRenderSyncPending) {
            mRenderSyncPending = true;
            mHandler.post(mRenderSyncRunnable);
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

    /**
     * A new wallpaper (or a wallpaper the app can suddenly read) invalidates every pre-blurred
     * frame and every crop taken from one.
     */
    public void onWallpaperChanged() {
        requestSync(SCOPE_WALLPAPER_BLUR_CACHE | SCOPE_BACKDROPS | SCOPE_ACCESSORY_RENDER);
    }

    /**
     * Every pre-blurred wallpaper frame describes the orientation being left; a rotation makes all
     * of them wrong at once.
     */
    public void onConfigurationChanged() {
        mBlurCache.clear();
    }

    /**
     * A backgrounded home app that keeps several full-screen blur bitmaps alive is exactly what
     * aggressive vendor memory killers reap first. Everything released here is rebuilt on demand
     * through the ledger, so the only cost of a trim is one blur redraw on the way back in.
     */
    public void onTrimMemory() {
        mBlurCache.clear();
        mLedger.markAllBackdropsDirty();
    }

    /**
     * Drops every pending chrome pass — the coalesced render, the blur backstop heartbeat and the
     * short recovery retry. For the paths that are tearing the visible chrome down (onStop).
     */
    public void cancelPendingWork() {
        mHandler.removeCallbacks(mRenderSyncRunnable);
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        mHandler.removeCallbacks(mBlurRecoveryRunnable);
        mRenderSyncPending = false;
    }

    /**
     * The narrower cancel the in-place session recovery does: the pending render and the backstop
     * heartbeat go, but the short recovery retry stays armed so a reset that lands mid-blur still
     * gets its follow-up pass.
     */
    public void cancelPendingRender() {
        mHandler.removeCallbacks(mRenderSyncRunnable);
        mHandler.removeCallbacks(mBlurHeartbeatRunnable);
        mRenderSyncPending = false;
    }

    public void onDestroy() {
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
