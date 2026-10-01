package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.ReducedMotion;

/**
 * Wallpaper frost: the shared pre-blurred wallpaper frame shown wherever a
 * {@code RealtimeBlurView} is blind.
 *
 * <p>In wallpaper passthrough mode a live-blur view can only sample the window's own (transparent)
 * content, so the top pane, the command palette, the app drawer plane and the sheets bars lie on
 * off the dock all read as flat tint — or grey mud over the window dim — while the dock shows
 * frosted wallpaper. Each of those surfaces
 * instead draws the same frame the dock samples, through a {@link SharedFrameDrawable} aimed at
 * the surface's own screen position on every draw, and its useless live-blur view rests.</p>
 *
 * <p>Nothing is cut: a slide, a resize or the parallax only moves where the drawable samples, so
 * the passes below allocate no bitmap. The rect bookkeeping that is left says when a surface has
 * moved since its frost was last aimed, so the frost is re-drawn on that pass rather than a frame
 * later.</p>
 */
public final class WallpaperFrostPainter {

    @NonNull private final ChromeRenderer.Surfaces mSurfaces;
    @NonNull private final WallpaperBlurCache mBlurCache;
    @NonNull private final SurfaceDirtyLedger mLedger;

    @NonNull private final int[] mTmpViewLocation = new int[2];

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
     * Gives the status inset band and the window-bar pane the same shared pre-blurred wallpaper
     * frame the dock uses, and rests the useless live-blur views. Runs after the blur views' own
     * visibility passes so its GONE wins while frost is active.
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
        // Rounded style: the pane is a floating capsule already clipped to its outline, so it takes
        // frost like any surface; the inset band above it shows raw wallpaper by design. This used
        // to bail out for the whole style, which left the capsule with no blur at all — its live
        // blur view is as blind to the wallpaper as every other RealtimeBlurView here.
        boolean capsule = mSurfaces.roundedDockStyle();
        ChromeEdgeRule.TopLead lead = capsule ? ChromeEdgeRule.TopLead.NONE : mSurfaces.topStackLead();
        // The strip continues whatever leads the top edge: the window bar's own glass, or the
        // dock's material on a sheet off the dock, at that material's own radius.
        boolean stripIsDockSheet = lead == ChromeEdgeRule.TopLead.DOCK_SHEET;
        int stripRadiusDp = stripIsDockSheet ? mSurfaces.effectiveDockBlurRadiusDp() : blurRadiusDp;
        if (!mSurfaces.wallpaperPassthroughEnabled() || (blurRadiusDp <= 0 && stripRadiusDp <= 0)) {
            clearTopPane();
            return;
        }
        // Docked: the strip touches the screen's top and sides and runs on into the surface below
        // it, so every one of its edges is a seam — no rim along the physical top of the screen.
        boolean statusApplied = lead != ChromeEdgeRule.TopLead.NONE && applyFrost(statusFrost,
            mSurfaces.findChromeView(R.id.terminal_status_bar_background), stripRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS,
            stripIsDockSheet ? SurfaceDirtyLedger.FrostRadius.OFF_DOCK
                : SurfaceDirtyLedger.FrostRadius.TOP_PANE,
            0f, ChromeEdgeRule.seams(false, ChromeEdgeRule.flushEdges(
                com.termux.app.place.PlaceLayout.Edge.TOP), ChromeEdgeRule.BOTTOM));
        if (lead == ChromeEdgeRule.TopLead.NONE)
            hide(statusFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        // A bar standing between the dock's own rows is on the dock's sheet, which already carries
        // this frost: a frost of its own here would draw the same blurred wallpaper a second time,
        // and with no wash over it — the bar wears no glass on the plank.
        boolean onPlank = mSurfaces.statusBarOnDockPlank();
        // Docked, the bar's screen edges are seams, and so is its top while the strip continues
        // it — whether or not the strip's own frost is up this pass. Only the edge the shape model
        // says faces the opening keeps its rim: a bar joined to the dock under it keeps none. The
        // capsule keeps its rim all round.
        int paneSeams = mSurfaces.statusBarSeamEdges();
        boolean paneApplied = !onPlank && applyFrost(paneFrost,
            mSurfaces.findChromeView(R.id.terminal_window_bar_host), blurRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR, SurfaceDirtyLedger.FrostRadius.TOP_PANE,
            capsule ? mSurfaces.statusBarRimCornerRadiusPx() : 0f,
            paneSeams);
        if (onPlank) hide(paneFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        View statusBlur = mSurfaces.findChromeView(R.id.terminal_status_bar_glass_blur);
        View paneBlur = mSurfaces.findChromeView(R.id.terminal_window_bar_blur);
        if (statusApplied && statusBlur != null) statusBlur.setVisibility(View.GONE);
        if (paneApplied && paneBlur != null) {
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
        if (statusFrost != null) hide(statusFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        if (paneFrost != null) hide(paneFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TERMINAL_SHEET);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.APP_DRAWER);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK);
        mLedger.clearFrostRect(SurfaceDirtyLedger.FrostRect.AZ_BAR_HOST);
        mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, -1);
    }

    /**
     * Wallpaper frost for the command palette glass. The palette's RealtimeBlurView has the same
     * blind spot as the top pane's: over the home wallpaper it can only blur the window's dim
     * scrim, which renders the glass as grey mud. Returns true when a frost was installed and the
     * live blur should rest; the frost spans the full glass pane and the pane's animated outline
     * clips it.
     */
    public boolean applyCommandPalette(@NonNull ImageView frost) {
        return applyFrost(frost, glassOf(frost, null), topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0);
    }

    /**
     * Wallpaper frost for the sheet plane's glass: the palette's material and radius, since a
     * sheet is a prompt in the same kit, aimed for the whole plane rather than the glass. The
     * glass is inset above the keyboard and clips the frost, which keeps the plane's full height
     * so the wallpaper stays in register. Its own rect entry, because the two planes are different
     * heights and sharing the palette's made every alternation between them re-aim.
     */
    public boolean applyTerminalSheet(@NonNull ImageView frost) {
        return applyFrost(frost, glassOf(frost, mSurfaces.findChromeView(R.id.terminal_sheet_host)),
            topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.TERMINAL_SHEET,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0);
    }

    /**
     * Wallpaper frost for the app drawer plane's glass, the same blind-spot fix the palette needs:
     * over the home wallpaper the plane's RealtimeBlurView can only blur the window's own dim
     * scrim. Unlike the palette this follows the dock's effective blur radius directly rather than
     * {@link #topGlassFrostRadiusDp()} — the plane grows out of the dock, so it has to show the
     * dock's radius or the two would read as different materials mid-handoff (and a fourth radius
     * would evict the dock's own entry from the pre-blur LRU). Returns true when a frost was
     * installed and the live blur should rest; the frost spans the full glass pane and the plane's
     * animated outline clips it.
     */
    public boolean applyAppDrawer(@NonNull ImageView frost) {
        return applyFrost(frost, glassOf(frost, null), mSurfaces.effectiveDockBlurRadiusDp(),
            SurfaceDirtyLedger.FrostRect.APP_DRAWER,
            SurfaceDirtyLedger.FrostRadius.APP_DRAWER, mSurfaces.planeGlassCornerRadiusPx(), 0);
    }

    /**
     * Wallpaper frost for a sheet a bar lies on off the dock: the shared plank a lying-down row
     * stands on ({@link SurfaceDirtyLedger.FrostRect#OFF_DOCK_PLANK}), or the alphabets bar's own
     * capsule on another edge ({@link SurfaceDirtyLedger.FrostRect#AZ_BAR_HOST}). Both are the
     * dock's glass carried to another edge, so both follow the dock's own blur radius, as the
     * drawer plane does, and share one radius entry. The frost fills the sheet and the sheet's
     * outline clips it; the rim follows that outline's radius on all four sides, since a sheet
     * off the dock stands clear of every edge and meets no other glass.
     *
     * <p>Returns true when the frost was installed and the sheet's live blur should rest; false
     * leaves the caller its live blur, which is what these sheets always wore.</p>
     */
    public boolean applyOffDockSheet(@NonNull ImageView frost,
                                     @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                                     float cornerRadiusPx) {
        return applyOffDockSheet(frost, rectKey, cornerRadiusPx, 0);
    }

    /**
     * @param seams the sheet's edges that take no rim ({@link ChromeEdgeRule#seams}): none for a
     *     sheet standing clear, the screen's sides for a docked one flush with them, and its top
     *     as well while it leads the top edge and the strip behind the status bar continues it
     */
    public boolean applyOffDockSheet(@NonNull ImageView frost,
                                     @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                                     float cornerRadiusPx, int seams) {
        followMoves(frost);
        return applyFrost(frost, glassOf(frost, null), mSurfaces.effectiveDockBlurRadiusDp(),
            rectKey, SurfaceDirtyLedger.FrostRadius.OFF_DOCK, cornerRadiusPx, seams);
    }

    /** The frosts {@link #followMoves} already watches. */
    @NonNull private final java.util.Set<View> mFollowed =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * Redraws {@code frost} after any layout that moved it on screen. A sheet off the dock is
     * carried by the edge stacks around it — the status bar folding above a top plank, a column
     * growing under the keyboard — and a view whose ancestor moved is not redrawn on its own, so
     * the frame would stay aimed at where it was. One location read per layout pass, none per
     * frame at rest.
     */
    private void followMoves(@NonNull ImageView frost) {
        if (!mFollowed.add(frost)) return;
        final int[] last = new int[2];
        final int[] now = new int[2];
        frost.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (frost.getVisibility() != View.VISIBLE || frost.getDrawable() == null) return;
            frost.getLocationOnScreen(now);
            if (now[0] == last[0] && now[1] == last[1]) return;
            last[0] = now[0];
            last[1] = now[1];
            frost.invalidate();
        });
    }

    /** The plane a full-pane frost fills: the view named, or the frost's own parent. */
    @Nullable
    private static View glassOf(@NonNull ImageView frost, @Nullable View named) {
        if (named != null) return named;
        return frost.getParent() instanceof View ? (View) frost.getParent() : null;
    }

    /**
     * Shows the shared frame on {@code frost} for {@code boundsView}'s screen rect; false hides the
     * frost. The same for a bar and a plane: the drawable aims itself, so all this decides is
     * whether there is a frame to show and whether the surface has moved since the last pass.
     *
     * <p>A miss while a fresh blur is in flight — a radius the editor just settled on, a source the
     * cache is still re-capturing — is not a reason to go tint-only: whatever frost is already on
     * screen is a closer match than nothing, so it stays right where it is until the next pass has
     * a frame to replace it with. It goes only when the frame it was captured for no longer
     * describes the screen (a rotation), where a stale frame would show as a shifted picture.</p>
     *
     * <p>Fancier Glass is restated on every pass, whether or not the frame moved: the look is one
     * compare on the drawable, and a knob the editor just turned reaches the frost on the very
     * pass it asks for rather than waiting for a frame change.</p>
     *
     * @param cornerRadiusPx the rounded rect the rim follows: the surface's own corner
     * @param seams          edges another glass surface continues past; see {@link GlassRefraction#rimRect}
     */
    private boolean applyFrost(@NonNull ImageView frost, @Nullable View boundsView, int blurRadiusDp,
                               @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                               @NonNull SurfaceDirtyLedger.FrostRadius radiusKey,
                               float cornerRadiusPx, int seams) {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        if (!mSurfaces.wallpaperPassthroughEnabled() || blurRadiusDp <= 0 || wallpaperFrame == null
            || boundsView == null || boundsView.getVisibility() != View.VISIBLE
            || boundsView.getWidth() <= 0 || boundsView.getHeight() <= 0) {
            hide(frost, rectKey);
            return false;
        }
        boundsView.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + boundsView.getWidth(), mTmpViewLocation[1] + boundsView.getHeight());
        SharedFrameDrawable installed = SharedFrameDrawable.of(frost.getDrawable());
        if (installed != null) refract(installed, cornerRadiusPx, seams);
        Bitmap frame = mBlurCache.obtain(blurRadiusDp, wallpaperFrame);
        Rect frameRect = mBlurCache.frameRectRef();
        if (frame == null) {
            if (installed != null && installed.frameRect().equals(frameRect)) {
                show(frost, rectKey, targetRect);
                return true;
            }
            hide(frost, rectKey);
            return false;
        }
        if (installed != null && installed.frame() == frame
            && mLedger.frostRadiusDp(radiusKey) == blurRadiusDp) {
            show(frost, rectKey, targetRect);
            return true;
        }
        // On the settle curve rather than outright when the cache says the frame this radius now
        // holds arrived to replace one a wallpaper change displaced — see
        // WallpaperBlurCache.isCrossfadedRadius. A rotation or a radius change never sets that
        // flag, so those keep landing the way they always have: on the next frame, all at once.
        boolean crossfade = installed != null && mBlurCache.isCrossfadedRadius(blurRadiusDp)
            && !ReducedMotion.isEnabled(frost.getContext());
        if (installed != null) {
            installed.setFrame(frame, frameRect, crossfade);
        } else {
            installed = new SharedFrameDrawable(frame, frameRect,
                mSurfaces.wallpaperParallax(), GlassAnchor.screen(frost));
            refract(installed, cornerRadiusPx, seams);
            frost.setImageDrawable(installed);
        }
        frost.setColorFilter(GlassFilters.frost());
        mLedger.setFrostRadiusDp(radiusKey, blurRadiusDp);
        show(frost, rectKey, targetRect);
        return true;
    }

    /** Fancier Glass on one frost, or the plain frost while the look is null. */
    private void refract(@NonNull SharedFrameDrawable frost, float cornerRadiusPx, int seams) {
        frost.setRefraction(mSurfaces.fancierGlassLook(),
            mSurfaces.context().getResources().getDisplayMetrics().density, cornerRadiusPx, seams);
    }

    /**
     * Puts the frost on screen, and redraws it when the surface it stands on has moved since the
     * last pass: a plane animating open, a bar that changed edge. The drawable re-aims on every
     * draw, but a view whose ancestor moved is not redrawn on its own.
     */
    private void show(@NonNull ImageView frost, @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                      @NonNull Rect targetRect) {
        if (!mLedger.matchesFrostRect(rectKey, targetRect)) {
            mLedger.recordFrostRect(rectKey, targetRect);
            frost.invalidate();
        }
        frost.setVisibility(View.VISIBLE);
    }

    private void hide(@NonNull ImageView frost, @NonNull SurfaceDirtyLedger.FrostRect rectKey) {
        frost.setImageDrawable(null);
        frost.setVisibility(View.GONE);
        mLedger.clearFrostRect(rectKey);
    }

    @Nullable
    private ImageView frostView(int viewId) {
        View view = mSurfaces.findChromeView(viewId);
        return view instanceof ImageView ? (ImageView) view : null;
    }
}
