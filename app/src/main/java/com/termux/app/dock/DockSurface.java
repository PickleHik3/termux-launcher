package com.termux.app.dock;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.DockShelfView;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;

import java.util.Collections;
import java.util.List;

/**
 * The dock's surface (ADR 0018 §1), behind {@link DockController}: whether it floats, the
 * capsule's radius, the shape its glass host is cut to, the style's own glass on the dock's
 * program, the shelf under a floating slab, whether the program draws the dock's edge, the plank
 * a lying-down pinned-apps row stands on away from the dock and its glass, and the ink of the
 * row's own tiles on the dock's face. Moved
 * line for line out of the activity (D17, step one); it reaches the activity only through
 * {@link DockController.Host}.
 */
final class DockSurface {

    @NonNull private final DockController.Host mHost;
    /** The controller this stands behind: the beads the glass and the shelf draw. */
    @NonNull private final DockController mDock;

    DockSurface(@NonNull DockController.Host host, @NonNull DockController dock) {
        mHost = host;
        mDock = dock;
    }

    // ------------------------------------------------------------------ Placement

    /**
     * The dock's one answer to "am I placed as the Floating card?" (DockPlacement): the Floating
     * Style, unless the Peek glass card stands, which rises from the screen's edge as it does
     * Docked. Every read of the dock's own placement and dressing asks this — its margin under
     * the stack, its sides, its outline, the strip under the pill, its shelf, veil, edge glow,
     * hinge and launch wave — while the panes, the status bar, the keyboard and the extra keys
     * keep asking the Style itself.
     */
    boolean dockFloats() {
        return dockFloatsIn(mHost.floatingStyle());
    }

    /** {@link #dockFloats} for the dock measured in {@code floatingStyle}. */
    boolean dockFloatsIn(boolean floatingStyle) {
        return com.termux.app.dock.DockPlacement.floats(floatingStyle,
            floatingStyle && mHost.peekCardWanted());
    }

    /**
     * The Floating card's radius for a surface this tall, Corners capped at half of it. Under
     * Docked no surface carries a radius of its own: the frame's outer corners are the model's.
     */
    float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) {
        return Math.min(mHost.layoutCornersPx(), Math.max(0, surfaceHeightPx) / 2f);
    }

    /** What the dock material's tint covers of the frost under it, 0..1. */
    float dockGlassCover() {
        if (mHost.preferences() == null) return 0f;
        return mHost.preferences().getAppBarOpacity(mHost.stackLookPage()) / 100f;
    }

    // ------------------------------------------------------------------ Shape

    /** The dock's clip: its slice of the Docked frame, or its own card under Floating. */
    private final com.termux.app.chrome.ChromeShapeOutlineProvider mDockOutline =
        new com.termux.app.chrome.ChromeShapeOutlineProvider();

    /** The dock host's shape as last cut, so {@link #setDockProgramEdge} can re-cut it alone. */
    private boolean mDockShapeCut;
    private boolean mDockShapeCapsule;
    private int mDockShapeHeightPx;

    /** Whether the host's shape has been cut once, and the height it was last cut for. */
    boolean dockShapeCut() {
        return mDockShapeCut;
    }

    boolean dockShapeCapsule() {
        return mDockShapeCapsule;
    }

    int dockShapeHeightPx() {
        return mDockShapeHeightPx;
    }

    /**
     * @param innerRim whether the surface's inner edge carries the containing stroke in docked:
     *                 the dock does, the same line the window bar keeps along its bottom; the
     *                 strip under the pill has no inner edge and draws none
     */
    void applyDockSurfaceShape(@NonNull View surface, boolean capsule, int surfaceHeightPx,
                               boolean ownsInnerEdge, boolean innerRim) {
        if (surface.getId() == R.id.accessory_surface_host) {
            mDockShapeCut = true;
            mDockShapeCapsule = capsule;
            mDockShapeHeightPx = surfaceHeightPx;
            // The host is never padded: its layers span it under every design, the Peek card
            // being only its outline. A background that once handed it padding (the card's rim
            // as a plain InsetDrawable did) leaves that padding behind when it is swapped out.
            if (surface.getPaddingLeft() != 0 || surface.getPaddingTop() != 0
                || surface.getPaddingRight() != 0 || surface.getPaddingBottom() != 0)
                surface.setPadding(0, 0, 0, 0);
            // Peek glass: the card is worked out from the row and the host as laid out now, and
            // the host dressed as the card where one stands (the activity's Peek glass).
            if (mHost.dressPeekCard(surface)) return;
            if (mDock.beads() != null) {
                // Lens beads: there is no slab, so no rim under it and no clip to its outline;
                // the host clips its own drawing to the beads (DockSurfaceHostLayout).
                surface.setBackground(null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    surface.setClipToOutline(false);
                return;
            }
        }
        if (!capsule) {
            // Docked, the dock is a slice of the one frame. The model says which of its edges
            // face the opening (the only ones that wear the rim) and what its clip is; its
            // corners are square except a screen corner it stands on.
            List<com.termux.app.place.ChromeShape.PieceId> ids = mHost.dockPieceIds(
                ownsInnerEdge && mHost.unifiedKeyboardGlass());
            // The program's gathered light is the dock's edge wherever it runs; the rim under the
            // glass would be a second one.
            int strokeEdges = innerRim && !dockGlassDrawsEdge() ? mHost.chromeRimEdges(
                ids.toArray(new com.termux.app.place.ChromeShape.PieceId[0]))
                : com.termux.app.chrome.ChromeEdgeRule.NONE;
            surface.setBackground(mHost.chrome().glass().rimUnderGlass(0f, strokeEdges));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Clip the dock to its slice of the frame so the reactive edge-glow's outward blur
                // can't spill past the dock edges and make it look wider.
                if (!ownsInnerEdge) {
                    surface.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                    surface.setClipToOutline(true);
                    return;
                }
                boolean changed = mDockOutline.setClip(
                    com.termux.app.chrome.LiveChromeShape.outlineOf(mHost.chromeShape(), ids));
                if (surface.getOutlineProvider() != mDockOutline)
                    surface.setOutlineProvider(mDockOutline);
                else if (changed)
                    surface.invalidateOutline();
                mDockOutline.follow(surface);
                surface.setClipToOutline(mDockOutline.clipsCorners());
            }
            return;
        }

        // Barely-there containing stroke; where the program runs, its gathered light is the edge
        // and the capsule keeps only the outline it clips to, or the two would read as a double
        // line ("inside rim") over the glass.
        float capsuleRadiusPx = resolveDockCapsuleCornerRadiusPx(surfaceHeightPx);
        Drawable outline = dockGlassDrawsEdge()
            ? mHost.chrome().glass().outlineOnly(capsuleRadiusPx)
            : mHost.chrome().glass().rimUnderGlass(capsuleRadiusPx);
        surface.setBackground(outline);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            surface.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            surface.setClipToOutline(true);
        }
    }

    // ------------------------------------------------------------------ Glass

    /**
     * Whether the dock's backdrop is on screen with a refraction effect installed right now, so
     * its program's gathered light is the dock's edge; see {@link #setDockProgramEdge}.
     */
    private boolean mDockProgramEdge;

    /**
     * Whether the dock's own glass draws its outline: its backdrop is up with a refraction effect
     * installed ({@link com.termux.app.chrome.GlassEffects}), whose gathered light is then the dock's
     * edge, so the host lays no {@code rimUnderGlass} under it. What is installed, not the rule
     * that would install it: a backdrop gone while a blur is in flight, an effect that threw or a
     * driver that refused the program all leave the rim.
     */
    boolean dockGlassDrawsEdge() {
        return mDockProgramEdge;
    }

    /**
     * Records whether the dock's backdrop now draws its edge, and re-cuts the host's shape when
     * that moved: the shape is set early in a pass, before the effect is built, so the pass that
     * installs or drops the effect re-dresses it at once rather than one pass late.
     */
    void setDockProgramEdge(boolean drawsEdge) {
        if (mDockProgramEdge == drawsEdge) return;
        mDockProgramEdge = drawsEdge;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        if (host != null && mDockShapeCut)
            applyDockSurfaceShape(host, mDockShapeCapsule, mDockShapeHeightPx, true, true);
    }

    /**
     * The slab's own spec for the glass program and the shelf: the dock's design's, except that
     * Peek glass with no card standing draws exactly the tray (its shelf and shadow included)
     * ({@link com.termux.app.dock.DockSurfaceSpec#forCard}).
     */
    @NonNull
    com.termux.app.dock.DockSurfaceSpec dockSlabSpec() {
        return mDock.shownDockSurfaceSpec().forCard(mHost.peekCard());
    }

    /**
     * The dock's style's own glass on its program ({@link com.termux.app.dock.DockSurfaceSpec}):
     * the soft rim band, the lower shelf where the slab floats (Docked, its lower edge is a seam
     * at the screen's edge and has no shelf). The plain dock writes all of it off, which is the
     * program as it always was.
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    void applyDockSurfaceGlass(@NonNull com.termux.app.chrome.GlassRefraction.Program program) {
        com.termux.app.dock.DockSurfaceSpec spec = dockSlabSpec();
        // Beads are drawn under Lens beads, and across the seam while they melt into the slab
        // with the beaded half's share of the slide (dockBeadsWanted), whichever page is committed.
        com.termux.app.dock.LensBeadGeometry.Beads beads = mDock.dockBeadsDrawn();
        if (beads != null) {
            // Lens beads: each bead floats, whatever the Style, so each wears the shelf's lip;
            // the beads stand where the host's origin sits in the backdrop. The glass is the
            // beads' own whichever page is committed, so a melt keeps it to the far side.
            com.termux.app.dock.DockSurfaceSpec beadSpec = com.termux.app.dock.DockSurfaceSpec.LENS_BEADS;
            program.setDockSurface(beadSpec.softRimDp, beadSpec.shelf);
            com.termux.app.dock.LensBeadGeometry.Beads inGlass =
                beads.offset(mHost.glassOriginLeft(), mHost.glassOriginTop());
            float[] centers = new float[inGlass.count()];
            for (int i = 0; i < centers.length; i++) centers[i] = inGlass.centerX(i);
            program.setBeads(centers, centers.length, inGlass.centerY, inGlass.radiusPx,
                inGlass.iconHalfPx);
            // The Search field's capsule under them (#84): one more shape of the same glass.
            if (inGlass.hasCapsule()) {
                program.setBeadCapsule(inGlass.capsuleLeft, inGlass.capsuleTop,
                    inGlass.capsuleRight, inGlass.capsuleBottom);
            } else {
                program.clearBeadCapsule();
            }
            // The A–Z row's letter beads under them, risen by the finger's lift.
            com.termux.app.dock.AzBeadRow az = mDock.azBeads();
            if (az != null) {
                program.setAzBeads(az.firstCenterX + mHost.glassOriginLeft(), az.pitchPx, az.count,
                    az.centerY + mHost.glassOriginTop(), az.radiusPx,
                    Float.isNaN(mDock.azLiftX()) ? Float.NaN : mDock.azLiftX() + mHost.glassOriginLeft(),
                    mDock.azLiftPeakPx());
            } else {
                program.clearAzBeads();
            }
        } else {
            // A row the beads cannot draw keeps the slab, and the slab the tray's own band.
            float softRim = spec.beads
                ? com.termux.app.dock.DockSurfaceSpec.GLASS_TRAY.softRimDp : spec.softRimDp;
            program.setDockSurface(softRim, dockFloats() ? spec.shelf : 0f);
            program.clearBeads();
        }
    }

    /**
     * The shelf under a floating Glass tray, a contact shadow and a glow on the wallpaper below
     * its slab: shown while the dock's glass refracts (so never below Android 13), the slab
     * floats, and the keyboard is not standing under it. It follows the glass host by itself.
     */
    void syncDockShelf(@Nullable ChromeSpec state) {
        DockShelfView shelf = mHost.findViewById(R.id.accessory_shelf_fx);
        if (shelf == null) return;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        // Beads always float, so each keeps its shadow under Docked too.
        boolean beads = mDock.beads() != null;
        boolean show = state != null && mHost.glassParamsValid() && host != null
            && mHost.dockRows().surfaceShown() && !state.keyboardShown
            && (dockFloats() || beads) && dockSlabSpec().contactShadow;
        shelf.setBeads(show ? mDock.dockBeadsShadowed() : null);
        shelf.setBeadShelfAlpha(mDock.dockBeadShelfAlpha());
        shelf.follow(show ? host : null);
        mHost.dockRows().setShelfShown(show);
    }

    // ------------------------------------------------------------------ Off-dock plank

    /** The plank's glass height as last sized, which its corner radius is clamped to. */
    private int mOffDockPlankGlassHeightPx;
    /** The edge the shared off-dock plank is standing on, as last applied from the place. */
    @NonNull private PlaceLayout.Edge mOffDockPlankEdge = PlaceLayout.Edge.TOP;
    private final com.termux.app.chrome.ChromeShapeOutlineProvider mOffDockPlankOutline =
        new com.termux.app.chrome.ChromeShapeOutlineProvider();

    /**
     * The edge a pinned-apps row lies down on away from the dock, or null when no such row stands.
     * Along the bottom the row is the dock's own and wears the dock's glass; on a side it is the
     * rail, which is a column rather than a plank.
     */
    @Nullable
    PlaceLayout.Edge offDockPlankEdge(@NonNull PlaceLayout layout) {
        if (!PlaceChromePolicy.appsShown(layout)) return null;
        PlaceLayout.Edge edge = PlaceChromePolicy.appsEdge(layout);
        if (edge.isOnSide() || edge == PlaceLayout.Edge.BOTTOM) return null;
        return edge;
    }

    /**
     * The bars sharing one plank on that edge, outermost first: the pinned apps row, and the
     * alphabets index when it rides that row from the band right beside it. An index riding from
     * further up the stack — with something else standing between the two — keeps its own capsule,
     * because a plank that swallowed it would draw the two bars in an order the user did not ask
     * for.
     */
    @NonNull
    List<Element> offDockPlankElements(@NonNull PlaceLayout layout,
                                       @Nullable PlaceLayout.Edge edge) {
        if (edge == null || edge != offDockPlankEdge(layout)) return Collections.emptyList();
        return com.termux.app.dock.DockGeometry.offDockPlankBars(layout);
    }

    /**
     * The air a plank keeps <em>outside</em> its sheet of glass. A plank carrying two bars keeps
     * the margin every bar off the dock has always kept from the screen. A plank that is one lone
     * row keeps its air inside the sheet instead ({@link DockLayoutPolicy#LONE_ROW_AIR_DP}), so the
     * sheet is exactly the bar and the row is not spaced twice over — which is what a row on its
     * own plank read as: the plank's margin, then the dock's own row padding inside it.
     */
    int offDockPlankAirPx(@NonNull List<Element> onPlank) {
        return onPlank.size() > 1 ? mHost.azBarHostMarginPx() : 0;
    }

    /**
     * The glass sheet's own thickness: the bars standing on the plank, without the air around
     * them. It is what the corner radius is clamped to, so it is kept from the pass that sized the
     * plank rather than measured off a view that may not have been laid out yet.
     */
    int offDockPlankGlassHeightPx() {
        return mOffDockPlankGlassHeightPx;
    }

    /**
     * The plank a lying-down row off the dock stands on: shown while such a row stands, inset from
     * the screen's sides by the dock's own side gap and keeping the same air above and below it
     * that a bar off the dock has always kept.
     *
     * @return whether the plank appeared, went or changed size, which the content's edge padding
     *     and every crop cut against it are derived from
     */
    boolean syncOffDockPlank(@NonNull PlaceLayout layout) {
        View host = mHost.findViewById(R.id.place_off_dock_plank_host);
        if (host == null) return false;
        PlaceLayout.Edge edge = offDockPlankEdge(layout);
        boolean want = edge != null;
        int glassHeightPx = 0;
        if (want) {
            mOffDockPlankEdge = edge;
            List<Element> onPlank = offDockPlankElements(layout, edge);
            DockLayout dockLayout = mDock.dockLayoutIn(layout);
            // The row's band carries the ticks inside its own air, so the plank is the band and
            // whatever else stands on it — nothing is added for the strip.
            glassHeightPx = dockLayout.appsRowBandPx
                + (onPlank.contains(Element.AZ) ? mHost.azBarThicknessPx() : 0);
            int sideInsetPx = dockLayout.horizontalInsetPx;
            int airPx = offDockPlankAirPx(onPlank);
            // Padding rather than margins: the glass fills the padded box, so whatever air the
            // plank keeps is outside the sheet and the sheet is exactly what the radius rounds.
            // A lone row keeps none here — its air is inside the sheet instead. Docked and leading
            // the top edge, the sheet runs on into the strip behind the status bar, so its air goes
            // below it instead: the same band claimed, with no gap between the sheet and the strip.
            boolean flushTop = mHost.dockedStatusStripLeadsWith(layout, onPlank);
            mHost.updateViewPadding(host, sideInsetPx, flushTop ? 0 : airPx, sideInsetPx,
                flushTop ? 2 * airPx : airPx);
        }
        boolean changed = (host.getVisibility() == View.VISIBLE) != want
            || mOffDockPlankGlassHeightPx != glassHeightPx;
        mOffDockPlankGlassHeightPx = glassHeightPx;
        host.setVisibility(want ? View.VISIBLE : View.GONE);
        if (changed) {
            mHost.refreshOffDockGlass(layout);
            mHost.requestRootInsets();
        }
        return changed;
    }

    /** The plank's glass re-read from the dock's own surface tuning, while the plank is up. */
    void refreshOffDockPlankGlass(@NonNull PlaceLayout layout) {
        View plank = mHost.findViewById(R.id.place_off_dock_plank_host);
        if (plank != null && plank.getVisibility() == View.VISIBLE) {
            applyOffDockPlankGlass(R.id.place_off_dock_plank_glass,
                R.id.place_off_dock_plank_frost,
                R.id.place_off_dock_plank_surface,
                com.termux.app.chrome.SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK,
                mOffDockPlankOutline, mOffDockPlankEdge, offDockPlankGlassHeightPx(),
                mHost.chromePieceIds(offDockPlankElements(layout, mOffDockPlankEdge)), layout);
        }
    }

    /**
     * The dock's own glass behind a sheet that is not on the dock: the same material, the same blur
     * and the same radius rule, so a plank on another edge reads as a piece of the same kit.
     *
     * <p>{@code heightPx} is the sheet's own thickness across the edge it stands on — one bar's for
     * a capsule, both bars' for the shared plank — because the radius is clamped to a true
     * half-capsule of whatever is being glazed. Handing it one bar's thickness while two stood on
     * it is what kept a top plank looking square.
     *
     * <p>The blur is the shared wallpaper frame at the sheet's own place on screen, as every other
     * sheet's is, through Fancier Glass's refraction when that is on, rimmed along the sheet's own
     * outline (the Wallpaper backdrop kind). With no frame yet, or with wallpaper mode off, the
     * sheet wears its wash alone: only the launcher's own flat ground is behind it then.
     */
    void applyOffDockPlankGlass(
        int glassId,
        int frostId,
        int surfaceId,
        @NonNull com.termux.app.chrome.SurfaceDirtyLedger.FrostRect frostKey,
        @NonNull com.termux.app.chrome.ChromeShapeOutlineProvider outline,
        @NonNull PlaceLayout.Edge edge,
        int heightPx,
        @NonNull List<com.termux.app.place.ChromeShape.PieceId> pieces,
        @NonNull PlaceLayout layout
    ) {
        float opacity = mHost.preferences() == null ? 1f
            : mHost.preferences().getAppBarOpacity(mHost.stackLookPage()) / 100f;
        // Floating's card radius is Corners, clamped to half of the sheet being glazed. Under
        // Docked the sheet carries no radius of its own: its glass view clips to its slice of
        // the frame below, and the frost beneath it stays square.
        boolean capsule = mHost.floatingStyle();
        float cornerRadiusPx = capsule ? resolveDockCapsuleCornerRadiusPx(heightPx) : 0f;
        View glass = mHost.findViewById(glassId);
        View sheetHost = glass != null && glass.getParent() instanceof View
            ? (View) glass.getParent() : null;
        // The edge rule is the model's: Docked, only the edge facing the opening is a rim and
        // every join and screen edge is a seam; the one leading the top edge runs on into the
        // strip behind the status bar as well, and renders the lower slice of the light model the
        // strip renders the top of. Floating keeps a rim all round, as it always has.
        com.termux.app.place.ChromeShape shape = mHost.chromeShape(layout);
        boolean continuesStrip = !capsule && edge == PlaceLayout.Edge.TOP
            && mHost.continuesIntoStatusInsetStrip(sheetHost);
        int seams = capsule ? com.termux.app.chrome.ChromeEdgeRule.NONE
            : com.termux.app.chrome.LiveChromeShape.seamEdges(shape, pieces);
        float sliceStart = continuesStrip ? mHost.statusInsetSheetSliceStart(sheetHost) : 0f;
        View frostView = mHost.findViewById(frostId);
        // Under a joined Docked frame the frame's glass is this sheet: frost and wash go.
        if (mHost.frameStandsDown()) {
            if (frostView instanceof ImageView)
                mHost.chrome().frost().hideOffDockSheet((ImageView) frostView, frostKey);
            View ownSurface = mHost.findViewById(surfaceId);
            if (ownSurface != null) ownSurface.setBackground(null);
            return;
        }
        // The Wallpaper kind: the stored pre-blurred frame at the sheet's own place on screen.
        if (frostView instanceof ImageView)
            mHost.chrome().frost().applyOffDockSheet((ImageView) frostView, frostKey, cornerRadiusPx,
                seams);
        View surface = mHost.findViewById(surfaceId);
        if (surface != null) {
            // Without the dark foot: this is a plank floating clear of the screen's edges, not a
            // slab standing on one, so a shaded underside would read as a drawn border.
            surface.setBackground(mHost.deckSlabOr(
                mHost.chrome().glass().dockSurface(opacity, sliceStart, 1f, false)));
            surface.setAlpha(1f);
        }
        // The sheet is what clips, not the host: the host has to let the wave's lift out of it.
        // Its clip is the model's: its own card under Floating, its slice of the one frame under
        // Docked.
        if (glass == null) return;
        outline.setClip(com.termux.app.chrome.LiveChromeShape.outlineOf(shape, pieces));
        if (glass.getOutlineProvider() != outline) glass.setOutlineProvider(outline);
        outline.follow(glass);
        glass.setClipToOutline(outline.clipsCorners());
        glass.invalidateOutline();
    }

    // ------------------------------------------------------------------ Tiles ink

    @NonNull private final Rect mDockTilesInkRect = new Rect();
    @NonNull private final int[] mDockTilesInkLocation = new int[2];
    /** The root's screen position, read by the tiles' ink measure. */
    @NonNull private final int[] mDockInkRootScratch = new int[2];

    /**
     * The apps row's Drawer tile and Command palette tile glyphs on the dock's face as measured
     * under the row, as the extra keys' labels are: a dark theme over a light wallpaper gets dark
     * glyphs. The row retints only when the ink moved.
     */
    void syncDockTilesInk(float dockOpacity) {
        SuggestionBarView row = mHost.dockRow();
        if (row == null || row.getWidth() <= 0 || row.getHeight() <= 0 || !row.isShown()) return;
        mHost.chrome().glass().noteDockGlassUnder(GlassBackdropCache.Band.DOCK, dockOpacity);
        com.termux.app.chrome.GlassAnchor.layoutRectOnScreen(row, mDockTilesInkRect,
            mDockTilesInkLocation, mDockInkRootScratch);
        row.setTileFace(mHost.chrome().ink().faceUnder(GlassBackdropCache.Band.DOCK,
            mDockTilesInkRect), mHost.chrome().ink().target(OnGlass.TARGET_LARGE_TEXT));
    }
}
