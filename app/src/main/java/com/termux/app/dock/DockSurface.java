package com.termux.app.dock;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.DockShelfView;
import com.termux.app.chrome.ChromeSpec;

import java.util.List;

/**
 * The dock's surface (ADR 0018 §1), behind {@link DockController}: whether it floats, the
 * capsule's radius, the shape its glass host is cut to, the style's own glass on the dock's
 * program, the shelf under a floating slab, and whether the program draws the dock's edge. Moved
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
}
