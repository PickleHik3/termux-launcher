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
 * Wallpaper frost: the shared pre-blurred wallpaper frame, the Wallpaper backdrop kind.
 *
 * <p>In wallpaper passthrough mode the window's own content is transparent, so nothing drawn in
 * it can see the wallpaper. The status strip, the window bar and the sheets bars lie on off the
 * dock wear this kind alone; the app drawer, the command palette and the terminal sheet wear it
 * where they cannot record what is beneath them ({@link PanelBackdrop}). Each draws the same
 * frame the dock samples, through a {@link SharedFrameDrawable} aimed at the surface's own screen
 * position on every draw.</p>
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
     * frame the dock uses: their Wallpaper backdrop kind, and the only blur they wear.
     */
    public void updateTopPane() {
        // Ride the same triggers: every state change that can move or restyle the top-pane frost
        // can move the terminal's glass pane too.
        mSurfaces.updateTerminalGlassFrost();
        ImageView statusFrost = frostView(R.id.terminal_status_bar_wallpaper_backdrop);
        ImageView paneFrost = frostView(R.id.terminal_window_bar_wallpaper_backdrop);
        if (statusFrost == null || paneFrost == null) {
            mSurfaces.onWindowBarFrostEdge(false);
            return;
        }
        // The status surface's own radius, not the dock's: the editor tunes them apart, and the
        // status slider has to visibly change this pane.
        int blurRadiusDp = mSurfaces.effectiveStatusBarBlurRadiusDp();
        // Rounded style: the pane is a floating capsule already clipped to its outline, so it takes
        // frost like any surface; the inset band above it shows raw wallpaper by design. This used
        // to bail out for the whole style, which left the capsule with no blur at all.
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
                com.termux.app.place.PlaceLayout.Edge.TOP), ChromeEdgeRule.BOTTOM),
            stripIsDockSheet ? dockCover() : statusCover(), stripIsDockSheet ? dock() : status());
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
            paneSeams, statusCover(), status());
        if (onPlank) hide(paneFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        if (statusApplied || paneApplied) {
            mLedger.clearFrostDirty();
            mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, blurRadiusDp);
        }
        // Last, once the frost is where it will stay this pass: the bar's stroke follows it.
        mSurfaces.onWindowBarFrostEdge(paneApplied && GlassSurfaceFactory.frostDrawsEdge(paneFrost));
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
        mSurfaces.onWindowBarFrostEdge(false);
    }

    /**
     * Wallpaper frost for the command palette glass, where its recorded backdrop cannot see the
     * wallpaper ({@link PanelBackdrop}). Returns true when a frost was installed; the frost spans
     * the full glass pane and the pane's animated outline clips it.
     */
    public boolean applyCommandPalette(@NonNull ImageView frost) {
        return applyFrost(frost, glassOf(frost, null), topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheetsCover(), sheets(), true);
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
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheetsCover(), sheets(), true);
    }

    /**
     * Wallpaper frost for the app drawer plane's glass, as the palette's. Unlike the palette this follows the dock's effective blur radius directly rather than
     * {@link #topGlassFrostRadiusDp()} — the plane grows out of the dock, so it has to show the
     * dock's radius or the two would read as different materials mid-handoff (and a fourth radius
     * would evict the dock's own entry from the pre-blur LRU). Returns true when a frost was
     * installed; the frost spans the full glass pane and the plane's animated outline clips it.
     */
    public boolean applyAppDrawer(@NonNull ImageView frost) {
        return applyFrost(frost, glassOf(frost, null), mSurfaces.effectiveDockBlurRadiusDp(),
            SurfaceDirtyLedger.FrostRect.APP_DRAWER,
            SurfaceDirtyLedger.FrostRadius.APP_DRAWER, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheetsCover(), sheets(), true);
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
     * <p>Returns true when the frost was installed; false leaves the sheet its wash alone.</p>
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
            rectKey, SurfaceDirtyLedger.FrostRadius.OFF_DOCK, cornerRadiusPx, seams, dockCover(),
            dock());
    }

    /**
     * Takes an off-dock sheet's frost down, ledger and all, so the pass that dresses it again finds
     * it missing and rebuilds it. Used while the joined Docked frame's glass is the sheet instead.
     */
    public void hideOffDockSheet(@NonNull ImageView frost,
                                 @NonNull SurfaceDirtyLedger.FrostRect rectKey) {
        hide(frost, rectKey);
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
                               float cornerRadiusPx, int seams, float cover,
                               @NonNull ElementOn glass) {
        return applyFrost(frost, boundsView, blurRadiusDp, rectKey, radiusKey, cornerRadiusPx, seams,
            cover, glass, false);
    }

    /**
     * @param panel a panel's glass ({@link PanelBackdrop}): where it cannot record what is beneath
     *     it (below Android 12) the frost is its picture over the launcher's own wallpaper too
     */
    private boolean applyFrost(@NonNull ImageView frost, @Nullable View boundsView, int blurRadiusDp,
                               @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                               @NonNull SurfaceDirtyLedger.FrostRadius radiusKey,
                               float cornerRadiusPx, int seams, float cover,
                               @NonNull ElementOn glass, boolean panel) {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        boolean wanted = mSurfaces.wallpaperPassthroughEnabled()
            || (panel && !PanelBackdropPolicy.canRecord(android.os.Build.VERSION.SDK_INT));
        if (!wanted || blurRadiusDp <= 0 || wallpaperFrame == null
            || boundsView == null || boundsView.getVisibility() != View.VISIBLE
            || boundsView.getWidth() <= 0 || boundsView.getHeight() <= 0) {
            hide(frost, rectKey);
            return false;
        }
        boundsView.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + boundsView.getWidth(), mTmpViewLocation[1] + boundsView.getHeight());
        SharedFrameDrawable installed = SharedFrameDrawable.of(frost.getDrawable());
        GlassRefraction.Look look = glass.refraction();
        if (installed != null) refract(installed, look, cornerRadiusPx, seams, cover);
        Bitmap frame = mBlurCache.obtain(blurRadiusDp, wallpaperFrame);
        Rect frameRect = mBlurCache.frameRectRef();
        if (frame == null) {
            if (installed != null && installed.frameRect().equals(frameRect)) {
                clarify(installed, look, blurRadiusDp, wallpaperFrame);
                show(frost, rectKey, targetRect);
                return true;
            }
            hide(frost, rectKey);
            return false;
        }
        if (installed != null && installed.frame() == frame
            && mLedger.frostRadiusDp(radiusKey) == blurRadiusDp) {
            clarify(installed, look, blurRadiusDp, wallpaperFrame);
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
            refract(installed, look, cornerRadiusPx, seams, cover);
            frost.setImageDrawable(installed);
        }
        clarify(installed, look, blurRadiusDp, wallpaperFrame);
        frost.setColorFilter(GlassFilters.frost());
        mLedger.setFrostRadiusDp(radiusKey, blurRadiusDp);
        show(frost, rectKey, targetRect);
        return true;
    }

    /**
     * Fancier Glass on one frost, or the plain frost while the look is null. {@code cover} is how
     * much the surface's tint covers the frost, which its gathered light is raised by.
     */
    private void refract(@NonNull SharedFrameDrawable frost, @Nullable GlassRefraction.Look look,
                         float cornerRadiusPx, int seams, float cover) {
        frost.setRefraction(look == null ? null : look.withCover(cover),
            mSurfaces.context().getResources().getDisplayMetrics().density, cornerRadiusPx, seams);
    }

    /**
     * The glass element a frost belongs to and the page it is drawn for: its refraction is the
     * Fancier Glass look with that element's own Depth and Edge where it holds them (ADR 0017).
     */
    private final class ElementOn {
        @NonNull final com.termux.shared.termux.settings.preferences.LookGrid.Element element;
        final int page;

        ElementOn(@NonNull com.termux.shared.termux.settings.preferences.LookGrid.Element element,
                  int page) {
            this.element = element;
            this.page = page;
        }

        @Nullable
        GlassRefraction.Look refraction() {
            return ElementGlass.refraction(mSurfaces.preferences(), element, page,
                mSurfaces.fancierGlassLook());
        }
    }

    @NonNull private ElementOn status() { return new ElementOn(com.termux.shared.termux.settings.preferences.LookGrid.Element.STATUS, mSurfaces.statusLookPage()); }
    @NonNull private ElementOn dock() { return new ElementOn(com.termux.shared.termux.settings.preferences.LookGrid.Element.DOCK, mSurfaces.stackLookPage()); }
    @NonNull private ElementOn sheets() { return new ElementOn(com.termux.shared.termux.settings.preferences.LookGrid.Element.SHEETS, mSurfaces.stackLookPage()); }

    /** What a sheet's tint (the Sheets element's opacity) covers of its frost. */
    private float sheetsCover() {
        return ElementGlass.of(mSurfaces.preferences(), com.termux.shared.termux.settings.preferences.LookGrid.Element.SHEETS,
            mSurfaces.stackLookPage()).tintAlpha();
    }

    /** What the status bar's tint covers of its frost. */
    private float statusCover() {
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences =
            mSurfaces.preferences();
        return preferences != null ? preferences.getStatusBarOpacity(mSurfaces.statusLookPage()) / 100f : 0f;
    }

    /** What the dock's material (the planes and sheets wear it) covers of its frost. */
    private float dockCover() {
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences =
            mSurfaces.preferences();
        return preferences != null ? preferences.getAppBarOpacity(mSurfaces.stackLookPage()) / 100f : 0f;
    }

    /**
     * The clearer frame Fancier Glass's edge clarity shows through the bend
     * ({@link SharedFrameDrawable#setClearFrame}), or none. Wanted only while the look asks for
     * clarity and the surface's own frost is blurrier than the clear frame would be; a surface
     * already that clear has nothing clearer to show. The frame is the cache's one rect at
     * {@link GlassRefraction#CLARITY_RADIUS_DP}, so it lines up with the frost wherever it is
     * sampled. Null while that blur is still in flight: the edge shows the frost until it lands,
     * and the landing asks for the pass that hands it over. Restated on every pass like the look;
     * an unchanged answer is one identity compare on the drawable and redraws nothing.
     */
    private void clarify(@NonNull SharedFrameDrawable frost, @Nullable GlassRefraction.Look look,
                         int blurRadiusDp, @NonNull View wallpaperFrame) {
        frost.setClearFrame(wantsClearFrame(look, blurRadiusDp)
            ? mBlurCache.obtain(GlassRefraction.CLARITY_RADIUS_DP, wallpaperFrame) : null);
    }

    /**
     * True when a surface frosted at {@code blurRadiusDp} under {@code look} takes a clear frame.
     * The one rule for every surface that shows one: the frosts here, the keyboard host and the
     * under-keyboard card, and the pane slabs.
     */
    public static boolean wantsClearFrame(@Nullable GlassRefraction.Look look, int blurRadiusDp) {
        return look != null && look.clarityPercent > 0
            && blurRadiusDp > GlassRefraction.CLARITY_RADIUS_DP;
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
