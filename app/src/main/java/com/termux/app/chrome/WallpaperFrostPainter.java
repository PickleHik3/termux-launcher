package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * Wallpaper frost: the crops of the shared pre-blurred wallpaper frame that stand in for live blur
 * wherever a {@code RealtimeBlurView} is blind.
 *
 * <p>In wallpaper passthrough mode a live-blur view can only sample the window's own (transparent)
 * content, so the top pane, the command palette and the app drawer plane all read as flat tint —
 * or grey mud over the window dim — while the dock shows frosted wallpaper. Each of those surfaces
 * instead gets a crop of the same frame the dock is cut from, and its useless live-blur view rests.</p>
 */
public final class WallpaperFrostPainter {

    @NonNull private final ChromeRenderer.Surfaces mSurfaces;
    @NonNull private final WallpaperBlurCache mBlurCache;
    @NonNull private final SurfaceDirtyLedger mLedger;

    @NonNull private final Matrix mFullStatusFrostMatrix = new Matrix();
    @NonNull private final int[] mTmpViewLocation = new int[2];
    @NonNull private final Rect mTmpFrameRect = new Rect();

    WallpaperFrostPainter(@NonNull ChromeRenderer.Surfaces surfaces,
                          @NonNull WallpaperBlurCache blurCache,
                          @NonNull SurfaceDirtyLedger ledger) {
        mSurfaces = surfaces;
        mBlurCache = blurCache;
        mLedger = ledger;
    }

    /** Radius for wallpaper frost on top glass surfaces: follow the dock so the materials match. */
    public int topGlassFrostRadiusDp() {
        int radiusDp = mSurfaces.effectiveDockBlurRadiusDp();
        return radiusDp > 0 ? radiusDp : mSurfaces.effectiveStatusBarBlurRadiusDp();
    }

    /**
     * Gives the status inset band and the window-bar pane crops of the same shared pre-blurred
     * wallpaper frame the dock uses, and rests the useless live-blur views. Runs after the blur
     * views' own visibility passes so its GONE wins while frost is active.
     */
    public void updateTopPane() {
        // Ride the same triggers: every state change that can move or restyle the top-pane frost
        // can move the terminal's glass pane too.
        mSurfaces.updateTerminalGlassFrost();
        ImageView statusFrost = frostView(R.id.terminal_status_bar_wallpaper_backdrop);
        ImageView paneFrost = frostView(R.id.terminal_window_bar_wallpaper_backdrop);
        if (statusFrost == null || paneFrost == null) return;
        // The status surface's own radius, not the dock's: the editor tunes them apart, and the
        // status slider has to visibly change this pane.
        int blurRadiusDp = mSurfaces.effectiveStatusBarBlurRadiusDp();
        if (!mSurfaces.wallpaperPassthroughEnabled() || blurRadiusDp <= 0) {
            clearTopPane();
            return;
        }
        if (mSurfaces.fullStatusBarEngaged()) {
            alignFullStatusBar();
            return;
        }
        // Rounded style: the pane is a floating capsule already clipped to its outline, so it takes
        // frost like any surface; the inset band above it shows raw wallpaper by design. This used
        // to bail out for the whole style, which left the capsule with no blur at all — its live
        // blur view is as blind to the wallpaper as every other RealtimeBlurView here.
        boolean capsule = mSurfaces.roundedDockStyle();
        boolean statusApplied = !capsule && applyCrop(statusFrost,
            mSurfaces.findChromeView(R.id.terminal_status_bar_background), blurRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        if (capsule) {
            statusFrost.setImageDrawable(null);
            statusFrost.setVisibility(View.GONE);
            mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        }
        boolean paneApplied = applyCrop(paneFrost,
            mSurfaces.findChromeView(R.id.terminal_window_bar_host), blurRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        View statusBlur = mSurfaces.findChromeView(R.id.terminal_status_bar_glass_blur);
        View paneBlur = mSurfaces.findChromeView(R.id.terminal_window_bar_blur);
        if (statusApplied && statusBlur != null) statusBlur.setVisibility(View.GONE);
        // While FULL is engaged the pane's live blur deliberately stays on over the frost
        // (alignFullStatusBar) so the terminal behind shows through the glass.
        if (paneApplied && paneBlur != null && !mSurfaces.fullStatusBarEngaged()) {
            paneBlur.setVisibility(View.GONE);
        }
        if (statusApplied || paneApplied) {
            mLedger.clearFrostDirty();
            mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, blurRadiusDp);
        }
    }

    public void clearTopPane() {
        ImageView statusFrost = frostView(R.id.terminal_status_bar_wallpaper_backdrop);
        ImageView paneFrost = frostView(R.id.terminal_window_bar_wallpaper_backdrop);
        if (statusFrost != null) {
            statusFrost.setImageDrawable(null);
            statusFrost.setVisibility(View.GONE);
        }
        if (paneFrost != null) {
            paneFrost.setImageDrawable(null);
            paneFrost.setVisibility(View.GONE);
        }
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TERMINAL_SHEET);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.APP_DRAWER);
        mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, -1);
    }

    /**
     * FULL displays the already cached screen-sized status-radius frame through the existing pane
     * backdrop. Only its matrix changes as layout moves; no target-sized bitmap is allocated per
     * spring frame and no new blur-radius cache key exists.
     */
    public void alignFullStatusBar() {
        if (!mSurfaces.fullStatusBarEngaged() || !mSurfaces.wallpaperPassthroughEnabled()) return;
        int radiusDp = mSurfaces.effectiveStatusBarBlurRadiusDp();
        if (radiusDp <= 0) return;
        ImageView frost = frostView(R.id.terminal_window_bar_wallpaper_backdrop);
        View host = mSurfaces.findChromeView(R.id.terminal_window_bar_host);
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        if (frost == null || host == null || wallpaperFrame == null) return;
        Bitmap full = mBlurCache.obtain(radiusDp, wallpaperFrame);
        if (full == null || full.isRecycled()) return;
        host.getLocationOnScreen(mTmpViewLocation);
        float scaleX = mBlurCache.frameRectWidth() / (float) Math.max(1, full.getWidth());
        float scaleY = mBlurCache.frameRectHeight() / (float) Math.max(1, full.getHeight());
        mFullStatusFrostMatrix.reset();
        mFullStatusFrostMatrix.setScale(scaleX, scaleY);
        mFullStatusFrostMatrix.postTranslate(
            mBlurCache.frameRectLeft() - mTmpViewLocation[0],
            mBlurCache.frameRectTop() - mTmpViewLocation[1]);
        if (!(frost.getDrawable() instanceof BitmapDrawable)
            || ((BitmapDrawable) frost.getDrawable()).getBitmap() != full) {
            frost.setImageBitmap(full);
        }
        frost.setScaleType(ImageView.ScaleType.MATRIX);
        frost.setImageMatrix(mFullStatusFrostMatrix);
        frost.setColorFilter(GlassFilters.frost());
        frost.setVisibility(View.VISIBLE);
        // Live blur stays ON above the frost while FULL is engaged: it can see the frozen,
        // still-running terminal behind the pane, so the terminal shows through the glass even
        // in wallpaper mode (the frost keeps covering the wallpaper the blur cannot see).
        View liveBlur = mSurfaces.findChromeView(R.id.terminal_window_bar_blur);
        if (liveBlur != null) liveBlur.setVisibility(View.VISIBLE);
        mBlurCache.copyFrameRect(mTmpFrameRect);
        mLedger.recordFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR, mTmpFrameRect);
        mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, radiusDp);
        mLedger.clearFrostDirty();
    }

    public void releaseFullStatusBar() {
        ImageView frost = frostView(R.id.terminal_window_bar_wallpaper_backdrop);
        if (frost != null) {
            frost.setImageDrawable(null);
            frost.setScaleType(ImageView.ScaleType.FIT_XY);
        }
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        mLedger.markFrostDirty();
        updateTopPane();
    }

    /** Installs one frost crop matching {@code boundsView}'s screen rect; false hides the frost. */
    private boolean applyCrop(@NonNull ImageView frost, @Nullable View boundsView, int blurRadiusDp,
                              @NonNull SurfaceDirtyLedger.FrostRect rectKey) {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        if (boundsView == null || wallpaperFrame == null
            || boundsView.getVisibility() != View.VISIBLE
            || boundsView.getWidth() <= 0 || boundsView.getHeight() <= 0) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            mLedger.clearFrostRect(rectKey);
            return false;
        }
        boundsView.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + boundsView.getWidth(),
            mTmpViewLocation[1] + boundsView.getHeight());
        if (!mLedger.isFrostDirty() && mLedger.matchesFrostRect(rectKey, targetRect)
            && mLedger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE) == blurRadiusDp
            && frost.getDrawable() != null) {
            frost.setVisibility(View.VISIBLE);
            return true;
        }
        Bitmap crop = mBlurCache.crop(blurRadiusDp, targetRect, wallpaperFrame);
        if (crop == null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            mLedger.clearFrostRect(rectKey);
            return false;
        }
        frost.setImageBitmap(crop);
        frost.setColorFilter(GlassFilters.frost());
        frost.setVisibility(View.VISIBLE);
        mLedger.recordFrostRect(rectKey, targetRect);
        return true;
    }

    /**
     * Wallpaper frost for the command palette glass. The palette's RealtimeBlurView has the same
     * blind spot as the top pane's: over the home wallpaper it can only blur the window's dim
     * scrim, which renders the glass as grey mud. Returns true when a frost crop was installed
     * and the live blur should rest; the crop spans the full glass pane and the pane's animated
     * outline clips it.
     */
    public boolean applyCommandPalette(@NonNull ImageView frost) {
        return applyFullPane(frost, null, topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE);
    }

    /**
     * Wallpaper frost for the sheet plane's glass: the palette's material and radius, since a
     * sheet is a prompt in the same kit, cut for the whole plane rather than the glass. The glass
     * is inset above the keyboard and clips the frost, which keeps the plane's full height so the
     * wallpaper stays in register; cutting for the glass instead allocated a near-full-screen
     * copy on every open the keyboard was up for. Its own rect entry, because the two planes are
     * different heights and sharing the palette's made every alternation between them re-cut.
     */
    public boolean applyTerminalSheet(@NonNull ImageView frost) {
        return applyFullPane(frost, mSurfaces.findChromeView(R.id.terminal_sheet_host),
            topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.TERMINAL_SHEET,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE);
    }

    /**
     * Wallpaper frost for the app drawer plane's glass, the same blind-spot fix the palette needs:
     * over the home wallpaper the plane's RealtimeBlurView can only blur the window's own dim
     * scrim. Unlike the palette this follows the dock's effective blur radius directly rather than
     * {@link #topGlassFrostRadiusDp()} — the plane grows out of the dock, so it has to be cut from
     * the dock's radius or the two would read as different materials mid-handoff (and a fourth
     * radius would evict the dock's own entry from the pre-blur LRU). Returns true when a frost
     * crop was installed and the live blur should rest; the crop spans the full glass pane and the
     * plane's animated outline clips it.
     */
    public boolean applyAppDrawer(@NonNull ImageView frost) {
        return applyFullPane(frost, null, mSurfaces.effectiveDockBlurRadiusDp(),
            SurfaceDirtyLedger.FrostRect.APP_DRAWER,
            SurfaceDirtyLedger.FrostRadius.APP_DRAWER);
    }

    /**
     * The palette and the drawer plane frost the same way: one crop spanning the whole glass pane,
     * guarded so the repeated apply calls an open gesture makes do not each re-cut a full-screen
     * bitmap.
     */
    private boolean applyFullPane(@NonNull ImageView frost, @Nullable View boundsView, int blurRadiusDp,
                                  @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                                  @NonNull SurfaceDirtyLedger.FrostRadius radiusKey) {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        View glass = boundsView != null ? boundsView
            : frost.getParent() instanceof View ? (View) frost.getParent() : null;
        if (!mSurfaces.wallpaperPassthroughEnabled() || blurRadiusDp <= 0 || wallpaperFrame == null
            || glass == null || glass.getWidth() <= 0 || glass.getHeight() <= 0) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            return false;
        }
        glass.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + glass.getWidth(), mTmpViewLocation[1] + glass.getHeight());
        // Both re-apply their frost on every open (and the palette on every animated resize).
        // Without the same guard the top-pane path uses, each of those calls re-cut a full-pane crop.
        if (!mLedger.isFrostDirty() && mLedger.matchesFrostRect(rectKey, targetRect)
            && mLedger.frostRadiusDp(radiusKey) == blurRadiusDp && frost.getDrawable() != null) {
            frost.setVisibility(View.VISIBLE);
            return true;
        }
        Bitmap crop = mBlurCache.crop(blurRadiusDp, targetRect, wallpaperFrame);
        if (crop == null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            mLedger.clearFrostRect(rectKey);
            return false;
        }
        mLedger.recordFrostRect(rectKey, targetRect);
        mLedger.setFrostRadiusDp(radiusKey, blurRadiusDp);
        frost.setImageBitmap(crop);
        frost.setColorFilter(GlassFilters.frost());
        frost.setVisibility(View.VISIBLE);
        return true;
    }

    @Nullable
    private ImageView frostView(int viewId) {
        View view = mSurfaces.findChromeView(viewId);
        return view instanceof ImageView ? (ImageView) view : null;
    }
}
