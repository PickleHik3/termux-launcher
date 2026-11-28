package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
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
    @NonNull private final FrostPassGate mPassGate = new FrostPassGate();

    /** A bounds view's visibility, screen position and size, into the pass's key. */
    private void addBounds(@Nullable View view) {
        if (view == null) {
            mPassGate.add(-1);
            return;
        }
        mPassGate.add(view.getVisibility());
        if (view.getVisibility() != View.VISIBLE) return;
        view.getLocationOnScreen(mTmpViewLocation);
        mPassGate.add(mTmpViewLocation[0]).add(mTmpViewLocation[1])
            .add(view.getWidth()).add(view.getHeight());
    }

    /** What a frost view shows now, so a hide from elsewhere is not skipped over. */
    private void addFrostState(@Nullable Slot frost) {
        if (frost == null) {
            mPassGate.add(-1);
            return;
        }
        mPassGate.add(frost.shown()).add(frost.drawable() != null);
    }

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
        Slot statusFrost = barSlot(R.id.terminal_status_bar_glass_surface);
        Slot paneFrost = barSlot(R.id.terminal_window_bar_background);
        Slot sideFrost = barSlot(R.id.terminal_window_bar_background_side);
        if (statusFrost == null || paneFrost == null) {
            mPassGate.invalidate();
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
        // A bar standing between the dock's own rows is on the dock's sheet, which already carries
        // this frost: a frost of its own here would draw the same blurred wallpaper a second time,
        // and with no wash over it — the bar wears no glass on the plank.
        boolean onPlank = mSurfaces.statusBarOnDockPlank();
        // Docked, the bar's screen edges are seams, and so is its top while the strip continues
        // it — whether or not the strip's own frost is up this pass. Only the edge the shape model
        // says faces the opening keeps its rim: a bar joined to the dock under it keeps none. The
        // capsule keeps its rim all round.
        int paneSeams = mSurfaces.statusBarSeamEdges();
        // A bar split round the camera has its cards' own views: the frost crops to the main
        // card, the second one to its own. The host still says whether the bar shows at all.
        View barHost = mSurfaces.findChromeView(R.id.terminal_window_bar_host);
        View mainCard = mSurfaces.findChromeView(R.id.terminal_window_bar_background);
        View sideCard = mSurfaces.findChromeView(R.id.terminal_window_bar_background_side);
        boolean barShown = barHost != null && barHost.getVisibility() == View.VISIBLE;

        // Nothing this pass reads has moved since it last landed: the frost is where it will stay.
        View statusBackground = mSurfaces.findChromeView(R.id.terminal_status_bar_background);
        float rimCornerPx = capsule ? mSurfaces.statusBarRimCornerRadiusPx() : 0f;
        mPassGate.begin();
        mPassGate.add(blurRadiusDp).add(stripRadiusDp).add(capsule).add(lead.ordinal())
            .add(onPlank).add(paneSeams).add(rimCornerPx)
            .add(mSurfaces.statusLookPage()).add(mSurfaces.stackLookPage())
            .add(java.util.Objects.hashCode(status().refraction()))
            .add(java.util.Objects.hashCode(dock().refraction()))
            .add(java.util.Objects.hashCode(mSurfaces.fancierGlassLook()))
            .add(mLedger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE))
            .add(mLedger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.OFF_DOCK))
            .add(mBlurCache.hasRadius(blurRadiusDp)).add(mBlurCache.hasRadius(stripRadiusDp))
            .add(mBlurCache.frameRectLeft()).add(mBlurCache.frameRectTop())
            .add(mBlurCache.frameRectWidth()).add(mBlurCache.frameRectHeight());
        addBounds(statusBackground);
        addBounds(barHost);
        addBounds(mainCard);
        addBounds(sideCard);
        addFrostState(statusFrost);
        addFrostState(paneFrost);
        addFrostState(sideFrost);
        if (mPassGate.unchanged(mLedger.isFrostDirty())) return;

        // Docked: the strip touches the screen's top and sides and runs on into the surface below
        // it, so every one of its edges is a seam — no rim along the physical top of the screen.
        boolean statusApplied = lead != ChromeEdgeRule.TopLead.NONE && applyFrost(statusFrost,
            mSurfaces.findChromeView(R.id.terminal_status_bar_background), stripRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS,
            stripIsDockSheet ? SurfaceDirtyLedger.FrostRadius.OFF_DOCK
                : SurfaceDirtyLedger.FrostRadius.TOP_PANE,
            0f, ChromeEdgeRule.seams(false, ChromeEdgeRule.flushEdges(
                com.termux.app.place.PlaceLayout.Edge.TOP), ChromeEdgeRule.BOTTOM),
            stripIsDockSheet ? dock() : status());
        if (lead == ChromeEdgeRule.TopLead.NONE)
            hide(statusFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        boolean paneApplied = !onPlank && applyFrost(paneFrost,
            barShown && mainCard != null ? mainCard : barHost, blurRadiusDp,
            SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR, SurfaceDirtyLedger.FrostRadius.TOP_PANE,
            capsule ? mSurfaces.statusBarRimCornerRadiusPx() : 0f,
            paneSeams, status());
        if (onPlank) hide(paneFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        boolean sideApplied = paneApplied && barShown && sideFrost != null && sideCard != null
            && sideCard.getVisibility() == View.VISIBLE && applyFrost(sideFrost, sideCard,
                blurRadiusDp, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR_SIDE,
                SurfaceDirtyLedger.FrostRadius.TOP_PANE,
                capsule ? mSurfaces.statusBarRimCornerRadiusPx() : 0f,
                paneSeams, status());
        if (!sideApplied && sideFrost != null)
            hide(sideFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR_SIDE);
        if (statusApplied || paneApplied) mPassGate.landed();
        else mPassGate.invalidate();
        if (statusApplied || paneApplied) {
            mLedger.clearFrostDirty();
            mLedger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, blurRadiusDp);
        }
        // Last, once the frost is where it will stay this pass: the bar's stroke follows it.
        mSurfaces.onWindowBarFrostEdge(paneApplied && paneFrost.drawsEdge());
    }


    public void clearTopPane() {
        mPassGate.invalidate();
        Slot statusFrost = barSlot(R.id.terminal_status_bar_glass_surface);
        Slot paneFrost = barSlot(R.id.terminal_window_bar_background);
        if (statusFrost != null) hide(statusFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        if (paneFrost != null) hide(paneFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR);
        Slot sideFrost = barSlot(R.id.terminal_window_bar_background_side);
        if (sideFrost != null) hide(sideFrost, SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR_SIDE);
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
        return applyCommandPalette(frost, topGlassFrostRadiusDp());
    }

    /**
     * {@link #applyCommandPalette(ImageView)} at {@code radiusDp}: under Terl the palette's
     * stored frame is blurred at Global's blur, the same the glass service's panel opens at.
     */
    public boolean applyCommandPalette(@NonNull ImageView frost, int radiusDp) {
        return applyFrost(new ImageSlot(frost), glassOf(frost, null), radiusDp,
            SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheets(), true);
    }

    /**
     * Wallpaper frost for the sheet plane's glass: the palette's material and radius, since a
     * sheet is a prompt in the same kit, aimed for the whole plane rather than the glass. The
     * glass is inset above the keyboard and clips the frost, which keeps the plane's full height
     * so the wallpaper stays in register. Its own rect entry, because the two planes are different
     * heights and sharing the palette's made every alternation between them re-aim.
     */
    public boolean applyTerminalSheet(@NonNull ImageView frost) {
        return applyFrost(new ImageSlot(frost), glassOf(frost, mSurfaces.findChromeView(R.id.terminal_sheet_host)),
            topGlassFrostRadiusDp(),
            SurfaceDirtyLedger.FrostRect.TERMINAL_SHEET,
            SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheets(), true);
    }

    /**
     * Wallpaper frost for the app drawer plane's glass, as the palette's. Unlike the palette this follows the dock's effective blur radius directly rather than
     * {@link #topGlassFrostRadiusDp()} — the plane grows out of the dock, so it has to show the
     * dock's radius or the two would read as different materials mid-handoff (and a fourth radius
     * would evict the dock's own entry from the pre-blur LRU). Returns true when a frost was
     * installed; the frost spans the full glass pane and the plane's animated outline clips it.
     */
    public boolean applyAppDrawer(@NonNull ImageView frost) {
        return applyFrost(new ImageSlot(frost), glassOf(frost, null), mSurfaces.effectiveDockBlurRadiusDp(),
            SurfaceDirtyLedger.FrostRect.APP_DRAWER,
            SurfaceDirtyLedger.FrostRadius.APP_DRAWER, mSurfaces.planeGlassCornerRadiusPx(), 0,
            sheets(), true);
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
        return applyFrost(new ImageSlot(frost), glassOf(frost, null), mSurfaces.effectiveDockBlurRadiusDp(),
            rectKey, SurfaceDirtyLedger.FrostRadius.OFF_DOCK, cornerRadiusPx, seams, dock());
    }

    /**
     * Takes an off-dock sheet's frost down, ledger and all, so the pass that dresses it again finds
     * it missing and rebuilds it. Used while the joined Docked frame's glass is the sheet instead.
     */
    public void hideOffDockSheet(@NonNull ImageView frost,
                                 @NonNull SurfaceDirtyLedger.FrostRect rectKey) {
        hide(new ImageSlot(frost), rectKey);
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
    private boolean applyFrost(@NonNull Slot frost, @Nullable View boundsView, int blurRadiusDp,
                               @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                               @NonNull SurfaceDirtyLedger.FrostRadius radiusKey,
                               float cornerRadiusPx, int seams,
                               @NonNull ElementOn glass) {
        return applyFrost(frost, boundsView, blurRadiusDp, rectKey, radiusKey, cornerRadiusPx, seams,
            glass, false);
    }

    /**
     * @param panel a panel's glass ({@link PanelBackdrop}): where it cannot picture what is beneath
     *     it (below Android 12, once the software blur has failed) the frost is its picture over the
     *     launcher's own wallpaper too
     */
    private boolean applyFrost(@NonNull Slot frost, @Nullable View boundsView, int blurRadiusDp,
                               @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                               @NonNull SurfaceDirtyLedger.FrostRadius radiusKey,
                               float cornerRadiusPx, int seams,
                               @NonNull ElementOn glass, boolean panel) {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        boolean wanted = mSurfaces.wallpaperPassthroughEnabled()
            || (panel && !PanelBackdrop.pictured());
        if (!wanted || blurRadiusDp <= 0 || wallpaperFrame == null
            || boundsView == null || boundsView.getVisibility() != View.VISIBLE
            || boundsView.getWidth() <= 0 || boundsView.getHeight() <= 0) {
            hide(frost, rectKey);
            return false;
        }
        boundsView.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + boundsView.getWidth(), mTmpViewLocation[1] + boundsView.getHeight());
        SharedFrameDrawable installed = SharedFrameDrawable.of(frost.drawable());
        GlassRefraction.Look look = glass.refraction();
        if (installed != null) refract(installed, look, cornerRadiusPx, seams);
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
            && !ReducedMotion.isEnabled(frost.view().getContext());
        if (installed != null) {
            installed.setFrame(frame, frameRect, crossfade);
        } else {
            installed = new SharedFrameDrawable(frame, frameRect,
                mSurfaces.wallpaperParallax(), GlassAnchor.screen(frost.view()));
            refract(installed, look, cornerRadiusPx, seams);
            frost.setDrawable(installed);
        }
        clarify(installed, look, blurRadiusDp, wallpaperFrame);
        frost.setColorFilter(GlassFilters.frost());
        frost.setParallax(mSurfaces.wallpaperParallax());
        mLedger.setFrostRadiusDp(radiusKey, blurRadiusDp);
        show(frost, rectKey, targetRect);
        return true;
    }

    /**
     * Fancier Glass on one frost, or the plain frost while the look is null.
     */
    private void refract(@NonNull SharedFrameDrawable frost, @Nullable GlassRefraction.Look look,
                         float cornerRadiusPx, int seams) {
        frost.setRefraction(look,
            mSurfaces.context().getResources().getDisplayMetrics().density, cornerRadiusPx, seams);
    }

    /**
     * The glass element a frost belongs to and the page it is drawn for: its refraction is the
     * Fancier Glass look with that element's own Depth and Edge where it holds them (ADR 0017).
     */
    private final class ElementOn {
        @Nullable final com.termux.shared.termux.settings.preferences.LookGrid.Element element;
        final int page;

        ElementOn(@Nullable com.termux.shared.termux.settings.preferences.LookGrid.Element element,
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
    /** The sheets wear Global's glass (the all-glass cell), not an element's own. */
    @NonNull private ElementOn sheets() { return new ElementOn(null, mSurfaces.stackLookPage()); }

    /**
     * The sharp frame the glass's edge shows through the bend
     * ({@link SharedFrameDrawable#setClearFrame}), or none ({@link #sharpFrame}). Null while a
     * blur is still in flight: the edge shows the frost until it lands, and the landing asks for
     * the pass that hands it over. Restated on every pass like the look; an unchanged answer is
     * one identity compare on the drawable and redraws nothing.
     */
    private void clarify(@NonNull SharedFrameDrawable frost, @Nullable GlassRefraction.Look look,
                         int blurRadiusDp, @NonNull View wallpaperFrame) {
        frost.setClearFrame(sharpFrame(mBlurCache, look, blurRadiusDp, wallpaperFrame));
    }

    /** Where {@link #sharpFrame} found its picture; pure, for its tests. */
    enum SharpSource {
        /** No sharp picture: the edge keeps the frost. */
        NONE,
        /** The clear frame obtained for a look whose stored clarity asks for one. */
        CLEAR_OBTAINED,
        /** The clear frame already resident for another surface, at no new cost. */
        CLEAR_RESIDENT,
        /** The wallpaper itself (radius 0), resident for the wall, at no new cost. */
        WALLPAPER_RESIDENT,
    }

    /**
     * Which picture the edge of a surface frosted at {@code blurRadiusDp} under {@code look}
     * samples, without ever making a frame for a look that did not hold one before: a look whose
     * stored clarity is above 0 obtains the clear frame as it always did; any other look takes
     * the clear frame only if it is already resident, else the wallpaper itself only if it is
     * resident (the wall paints it) and the frost carries no bake the bare wallpaper lacks
     * (Vibrancy, frame tint), else nothing and its edge keeps the frost. Pure.
     */
    @NonNull
    static SharpSource sharpSource(@Nullable GlassRefraction.Look look, int blurRadiusDp,
                                   boolean clearResident, boolean wallpaperResident,
                                   int bakedVibrancy, int bakedFrameTint) {
        if (!wantsClearFrame(look, blurRadiusDp)) return SharpSource.NONE;
        if (look.clarityPercent > 0) return SharpSource.CLEAR_OBTAINED;
        if (clearResident) return SharpSource.CLEAR_RESIDENT;
        boolean baked = GlassVibrancy.bakes(blurRadiusDp, bakedVibrancy)
            || GlassFrameTint.bakes(blurRadiusDp, bakedFrameTint);
        return wallpaperResident && !baked ? SharpSource.WALLPAPER_RESIDENT : SharpSource.NONE;
    }

    /**
     * The sharp picture for {@link #sharpSource}, from {@code cache}: obtained only where a look
     * held one before, otherwise a frame already resident ({@link WallpaperBlurCache#peek}, which
     * neither captures nor blurs), so it adds no pixels. Any of them is the cache's one frame
     * rect, so it lines up with the frost wherever it is sampled. The one rule for every surface
     * that shows one: the frosts here, the keyboard host and the under-keyboard card, the drawer
     * cards, the look glass and the pane slabs.
     */
    @Nullable
    public static android.graphics.Bitmap sharpFrame(@NonNull WallpaperBlurCache cache,
                                                     @Nullable GlassRefraction.Look look,
                                                     int blurRadiusDp, @NonNull View wallpaperFrame) {
        android.graphics.Bitmap clear = wantsClearFrame(look, blurRadiusDp)
            ? cache.peek(GlassRefraction.CLARITY_RADIUS_DP) : null;
        android.graphics.Bitmap wallpaper = clear == null && wantsClearFrame(look, blurRadiusDp)
            ? cache.peek(0) : null;
        android.graphics.Bitmap out;
        switch (sharpSource(look, blurRadiusDp, clear != null, wallpaper != null,
                cache.bakedVibrancy(), cache.bakedFrameTint())) {
            case CLEAR_OBTAINED:
                out = cache.obtain(GlassRefraction.CLARITY_RADIUS_DP, wallpaperFrame);
                break;
            case CLEAR_RESIDENT:
                out = clear;
                break;
            case WALLPAPER_RESIDENT:
                out = wallpaper;
                break;
            default:
                out = null;
        }
        return out != null && !out.isRecycled() ? out : null;
    }

    /**
     * True when a surface frosted at {@code blurRadiusDp} under {@code look} may show a sharp
     * picture at its edge at all: a convex look, over a frost blurrier than the clear frame.
     */
    public static boolean wantsClearFrame(@Nullable GlassRefraction.Look look, int blurRadiusDp) {
        return look != null && !look.concave && blurRadiusDp > GlassRefraction.CLARITY_RADIUS_DP;
    }

    /**
     * Puts the frost on screen, and redraws it when the surface it stands on has moved since the
     * last pass: a plane animating open, a bar that changed edge. The drawable re-aims on every
     * draw, but a view whose ancestor moved is not redrawn on its own.
     */
    private void show(@NonNull Slot frost, @NonNull SurfaceDirtyLedger.FrostRect rectKey,
                      @NonNull Rect targetRect) {
        if (!mLedger.matchesFrostRect(rectKey, targetRect)) {
            mLedger.recordFrostRect(rectKey, targetRect);
            frost.invalidate();
        }
        frost.setShown(true);
    }

    private void hide(@NonNull Slot frost, @NonNull SurfaceDirtyLedger.FrostRect rectKey) {
        frost.setDrawable(null);
        frost.setShown(false);
        mLedger.clearFrostRect(rectKey);
    }

    /** The frost slot a bar's glass view holds, or null when the view is not one. */
    @Nullable
    private Slot barSlot(int viewId) {
        View view = mSurfaces.findChromeView(viewId);
        if (!(view instanceof BarGlassView)) return null;
        BarGlassView bar = (BarGlassView) view;
        BarSlot slot = mBarSlots.get(bar);
        if (slot == null) {
            slot = new BarSlot(bar);
            mBarSlots.put(bar, slot);
        }
        return slot;
    }

    /** The bar slots made so far, one per glass view, so a pass allocates none. */
    @NonNull private final java.util.Map<BarGlassView, BarSlot> mBarSlots =
        new java.util.WeakHashMap<>();

    /**
     * Where a frost lives: an ImageView of its own (the planes, sheets and plank) or inside a bar's
     * glass view. The passes show, hide and aim either one the same way.
     */
    private interface Slot {
        @NonNull View view();
        @Nullable Drawable drawable();
        void setDrawable(@Nullable Drawable drawable);
        void setColorFilter(@Nullable ColorFilter filter);
        void setParallax(@Nullable WallpaperParallax parallax);
        void setShown(boolean shown);
        boolean shown();
        void invalidate();
        /** Whether the frost is up and its program draws the edge ({@link GlassSurfaceFactory#frostDrawsEdge}). */
        boolean drawsEdge();
    }

    private static final class ImageSlot implements Slot {
        @NonNull private final ImageView mFrost;

        ImageSlot(@NonNull ImageView frost) { mFrost = frost; }

        @Override @NonNull public View view() { return mFrost; }
        @Override @Nullable public Drawable drawable() { return mFrost.getDrawable(); }
        @Override public void setDrawable(@Nullable Drawable drawable) { mFrost.setImageDrawable(drawable); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { mFrost.setColorFilter(filter); }
        @Override public void setParallax(@Nullable WallpaperParallax parallax) { }
        @Override public void setShown(boolean shown) { mFrost.setVisibility(shown ? View.VISIBLE : View.GONE); }
        @Override public boolean shown() { return mFrost.getVisibility() == View.VISIBLE; }
        @Override public void invalidate() { mFrost.invalidate(); }
        @Override public boolean drawsEdge() { return GlassSurfaceFactory.frostDrawsEdge(mFrost); }
    }

    private static final class BarSlot implements Slot {
        @NonNull private final BarGlassView mBar;

        BarSlot(@NonNull BarGlassView bar) { mBar = bar; }

        @Override @NonNull public View view() { return mBar; }
        @Override @Nullable public Drawable drawable() { return mBar.getFrost(); }
        @Override public void setDrawable(@Nullable Drawable drawable) { mBar.setFrost(drawable); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { mBar.setFrostColorFilter(filter); }
        @Override public void setParallax(@Nullable WallpaperParallax parallax) { mBar.setParallax(parallax); }
        @Override public void setShown(boolean shown) { mBar.setFrostShown(shown); }
        @Override public boolean shown() { return mBar.frostShown(); }
        @Override public void invalidate() { mBar.invalidate(); }
        @Override public boolean drawsEdge() { return GlassSurfaceFactory.frostDrawsEdge(mBar); }
    }
}
