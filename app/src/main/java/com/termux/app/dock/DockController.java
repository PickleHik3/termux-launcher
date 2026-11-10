package com.termux.app.dock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.AzScrubRowView;
import com.termux.app.DockShelfView;
import com.termux.app.DockSurfaceHostLayout;
import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.place.PlaceLayout;

/**
 * The dock's events and state (ADR 0018 §1): the Lens beads under the row's icons and the A–Z
 * row's letter beads, worked out from the views as laid out and handed to the dock's glass host
 * ({@link DockSurfaceHostLayout}) and its shelf ({@link DockShelfView}). It reaches the activity
 * only through {@link Host}.
 */
public final class DockController {

    /** What only the activity gives the dock. */
    public interface Host {
        /** The window's view of {@code id}, looked up afresh; null while it has none. */
        @Nullable <T extends View> T findViewById(int id);
        /** The window's view of {@code id}, found once per tree; null while it has none. */
        @Nullable <T extends View> T hotView(int id);
        /** Whether the preferences are read yet. */
        boolean hasPreferences();
        /** The crossing's bead share: NaN while nothing melts across the seam. */
        float beadShare();
        /** The dock design the half in front shows now. */
        @NonNull DockSurfaceSpec shownDockSurfaceSpec();
        /** Whether the dock's own placement floats (a capsule over the wallpaper). */
        boolean dockFloats();
        /** The capsule's corner radius for a surface this tall. */
        float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx);
        /** The dock's rows, which say whether the shelf is shown. */
        @NonNull DockRows dockRows();
        /** The chrome's renderer; null before it is made. */
        @Nullable ChromeRenderer chrome();
        /** The dock's effect rebuilt once, as a key lens's frame does. */
        void rebuildBackdropGlassEffectForLens();
        /** The dock's pinned row; null before it is inflated. */
        @Nullable SuggestionBarView dockRow();
        /** The place's layout as arranged now. */
        @NonNull PlaceLayout placeLayout();
        /** Whether the stack's sheets stand down on the joined frame's glass. */
        boolean stackStandsDownOnFrame();
        /** Whether the in-app keyboard is up and shares the dock's glass. */
        boolean keyboardSharesDockGlass();
        /** The Search field when it stands shown in the row's band under the icons; else null. */
        @Nullable PromptLineFieldView promptFieldInRowBand();
        /** The display's density. */
        float density();
        /** The row moved under the joined frame's band cut: the cut follows it. */
        void syncFrameBeadCut();
        /**
         * The beads were replaced: the slab's shape cut again on {@code host} where beads came or
         * went ({@code modeChanged}), the Search field's well, then the frame's cut.
         */
        void onDockBeadsReplaced(@Nullable View host, boolean modeChanged);
    }

    @NonNull private final Host mHost;

    public DockController(@NonNull Host host) {
        mHost = host;
    }

    /** The beads as last drawn, in the host's coordinates; null while the dock is a slab. */
    @Nullable
    public LensBeadGeometry.Beads beads() {
        return mDockBeads;
    }

    /** The letter beads in the host's coordinates, or null where the row has none. */
    @Nullable
    public AzBeadRow azBeads() {
        return mDockAzBeads;
    }

    /** The letter beads' lift as last drawn: the finger's x in the host, NaN for none. */
    public float azLiftX() {
        return mDockAzLiftX;
    }

    /** The letter beads' lift as last drawn: its peak, px. */
    public float azLiftPeakPx() {
        return mDockAzLiftPeakPx;
    }

    // ------------------------------------------------------------------ Lens beads
    //
    // Lens beads (DockSurfaceSpec.LENS_BEADS), on whichever half's dock wears the design (#93;
    // under the Workspace's extra keys or a keyboard sharing the glass the row keeps the slab,
    // LensBeadGeometry.rowWearsBeads): the dock's one glass host draws a round
    // bead under each icon instead of its slab. The host clips everything in it to the beads
    // (DockSurfaceHostLayout), the dock's one glass program draws every bead's rim, shelf and
    // reflection from the beads' centres (GlassRefraction.Program#setBeads), and the shelf view
    // draws each bead's shadow. The beads are worked out again only when the row or the host
    // lays out, before the frame that shows the new layout draws; at rest nothing runs.

    /** The beads as last drawn, in the host's coordinates; null while the dock is a slab. */
    @Nullable private com.termux.app.dock.LensBeadGeometry.Beads mDockBeads;
    /**
     * The beads where a page swipe draws the icons now ({@link #onDockIconsMoved}), in the
     * host's coordinates; null at rest, when the beads are {@link #mDockBeads}.
     */
    @Nullable private com.termux.app.dock.LensBeadGeometry.Beads mDockBeadsMoving;
    /** Where the row stands in the host sideways, and the span it draws its icons in there. */
    private float mDockBeadsRowDx;
    /** Where it stands down the host, and the bottom the beads are held above; NaN unread. */
    private float mDockBeadsRowDy = Float.NaN;
    private float mDockBeadsBottomPx = Float.NaN;
    private float mDockBeadsSpanLeft = Float.NaN, mDockBeadsSpanRight = Float.NaN;
    /** Scratch for the row's still tiles (Drawer, palette), whose beads a swipe holds still. */
    private final float[] mDockBeadStills = new float[2];
    /** Scratch for the neighbouring page's icons a swipe stages. */
    private final float[] mDockBeadNeighbours =
        new float[com.termux.app.dock.LensBeadGeometry.MAX_DRAWN_BEADS];

    /**
     * Whether the dock draws beads now (the row still has to wear them, {@link #resolveDockBeads}):
     * under Lens beads, but not where a slide has carried the beaded half wholly off; and across
     * the seam from a half without them while any of the beaded half's share shows, so they come
     * in as the page does.
     */
    public boolean dockBeadsWanted() {
        if (!mHost.hasPreferences()) return false;
        float share = mHost.beadShare();
        if (mHost.shownDockSurfaceSpec().beads) return Float.isNaN(share) || share > 0f;
        return !Float.isNaN(share) && share > 0f;
    }

    /** The beads where the row draws its icons now: moving with a swipe, or at rest. */
    @Nullable
    public com.termux.app.dock.LensBeadGeometry.Beads dockBeadsShadowed() {
        return mDockBeads != null && mDockBeadsMoving != null ? mDockBeadsMoving : mDockBeads;
    }

    /**
     * The beads as the dock's glass draws them now: where the row draws its icons, swollen by a
     * melt across the seam toward the slab they cover at its far end. The shadows stay the
     * beads' own ({@link #dockBeadsShadowed}); the swelling glass covers them.
     */
    @Nullable
    public com.termux.app.dock.LensBeadGeometry.Beads dockBeadsDrawn() {
        com.termux.app.dock.LensBeadGeometry.Beads beads = dockBeadsShadowed();
        float share = mHost.beadShare();
        if (beads == null || Float.isNaN(share) || share >= 1f) return beads;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return beads;
        float cover = com.termux.app.dock.LensBeadGeometry.coverRadiusPx(beads, host.getWidth(),
            host.getHeight());
        return beads.withRadius(com.termux.app.dock.LensBeadGeometry.meltRadiusPx(
            beads.radiusPx, cover, share));
    }

    /** How much of the beads' shadows and glow show: all at rest, none once they cover the slab. */
    public float dockBeadShelfAlpha() {
        return com.termux.app.dock.LensBeadGeometry.beadShelfAlpha(mHost.beadShare());
    }

    /** The slab clip the host cuts melting beads to: the slab's corners; NaN at rest. */
    private float dockBeadSlabClipPx() {
        float share = mHost.beadShare();
        if (mDockBeads == null || Float.isNaN(share) || share >= 1f) return Float.NaN;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host == null) return Float.NaN;
        return mHost.dockFloats() ? mHost.resolveDockCapsuleCornerRadiusPx(host.getHeight()) : 0f;
    }


    /**
     * The beads drawn again for the crossing's bead share (Crossing#beadShare): the host's clip and slab cut, and the
     * dock's effect once. Beads coming or going (the beaded half's share reaching or leaving
     * nothing) dress
     * the dock again, as a row turning to beads or back does. Mid-slide the row is read again
     * only when beads are to come or go ({@code settled}: always, the slide having landed).
     */
    public void applyDockBeadMelt(boolean settled) {
        boolean wereBeads = mDockBeads != null;
        boolean modeChanged = (settled || dockBeadsWanted() != wereBeads)
            && refreshDockBeads() && wereBeads != (mDockBeads != null);
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setBeads(dockBeadsDrawn());
            beadHost.setSlabClip(dockBeadSlabClipPx());
        }
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null) {
            if (mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
            shelf.setBeadShelfAlpha(dockBeadShelfAlpha());
        }
        if (modeChanged) {
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        } else if (mDockBeads != null) {
            mHost.rebuildBackdropGlassEffectForLens();
        }
    }

    /**
     * The row's icons moved off or back onto their places (a page swipe, a page switch): the
     * beads ride under them, from the touch or animation callback that moved them, so a bead is
     * under its icon on the very frame that draws it. Each frame of a swipe re-cuts the host's
     * clip, moves the shadows (their paints are kept) and rebuilds the dock's effect once, a
     * single pass of the glass program over the pre-blurred frost (the dock's chain has no blur
     * of its own); at rest and under the Glass tray nothing runs past the first check.
     */
    public void onDockIconsMoved() {
        SuggestionBarView row = mHost.dockRow();
        com.termux.app.dock.LensBeadGeometry.Beads rest = mDockBeads;
        com.termux.app.dock.LensBeadGeometry.Beads moving = null;
        if (row != null && rest != null && row.iconsOffTheirPlaces()) {
            int neighbours = row.drawnNeighbourIconCentersX(mDockBeadNeighbours);
            for (int i = 0; i < neighbours; i++) mDockBeadNeighbours[i] += mDockBeadsRowDx;
            float left = Float.isNaN(mDockBeadsSpanLeft) ? 0f : mDockBeadsSpanLeft;
            float right = Float.isNaN(mDockBeadsSpanRight) ? Float.MAX_VALUE : mDockBeadsSpanRight;
            // The Drawer tile (#97) and the palette tile are no page's: their beads stand still
            // under them.
            int stills = row.stillTileCentersX(mDockBeadStills);
            for (int i = 0; i < stills; i++) mDockBeadStills[i] += mDockBeadsRowDx;
            moving = com.termux.app.dock.LensBeadGeometry.follow(rest, row.drawnPageOffsetPx(),
                mDockBeadStills, stills, mDockBeadNeighbours, neighbours, left, right,
                row.drawnPageFade());
        }
        if (moving == null && mDockBeadsMoving == null) return;
        if (moving != null && moving.equals(mDockBeadsMoving)) return;
        mDockBeadsMoving = moving;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setBeadSpan(moving != null ? mDockBeadsSpanLeft : Float.NaN,
                moving != null ? mDockBeadsSpanRight : Float.NaN);
            beadHost.setBeads(dockBeadsDrawn());
        }
        if (moving == null) {
            // Back at rest: the page the row settled on may hold other icons than the beads did.
            boolean wereBeads = mDockBeads != null;
            refreshDockBeads();
            if (host instanceof DockSurfaceHostLayout)
                ((DockSurfaceHostLayout) host).setBeads(mDockBeads);
            if (wereBeads != (mDockBeads != null) && mHost.chrome() != null)
                mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                    | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        }
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        mHost.rebuildBackdropGlassEffectForLens();
    }
    /** The row's icon boxes, read into this; one icon more than the beads hold says "too many". */
    private final float[] mDockBeadBoxes =
        new float[(com.termux.app.dock.LensBeadGeometry.MAX_BEADS + 1) * 4];
    /** The views whose layout moves the beads, while the dock wears them. */
    @Nullable private View mDockBeadsWatchedHost;
    @Nullable private View mDockBeadsWatchedRow;
    private boolean mDockBeadsRefreshPending;
    private final View.OnLayoutChangeListener mDockBeadsLayoutListener =
        (v, l, t, r, b, ol, ot, or, ob) -> scheduleDockBeadsRefresh();
    private final ViewTreeObserver.OnPreDrawListener mDockBeadsPreDraw =
        new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                View host = mDockBeadsWatchedHost;
                if (host != null) host.getViewTreeObserver().removeOnPreDrawListener(this);
                mDockBeadsRefreshPending = false;
                onDockBeadsLayoutSettled();
                return true;
            }
        };

    /**
     * Watches the host and the row for layout while the dock's style draws beads, and lets go
     * of them otherwise, so the Glass tray and the Workspace's dock carry no listener of this.
     */
    public void syncDockBeadsListeners() {
        boolean beads = dockBeadsWanted();
        View host = beads ? mHost.findViewById(R.id.accessory_surface_host) : null;
        View row = beads ? mHost.dockRow() : null;
        if (mDockBeadsWatchedHost != host) {
            if (mDockBeadsWatchedHost != null) {
                mDockBeadsWatchedHost.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
                if (mDockBeadsRefreshPending)
                    mDockBeadsWatchedHost.getViewTreeObserver().removeOnPreDrawListener(mDockBeadsPreDraw);
                mDockBeadsRefreshPending = false;
            }
            mDockBeadsWatchedHost = host;
            if (host != null) host.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
        if (mDockBeadsWatchedRow != row) {
            if (mDockBeadsWatchedRow != null)
                mDockBeadsWatchedRow.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
            mDockBeadsWatchedRow = row;
            if (row != null) row.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
        // The A–Z row's letter beads follow its layout too.
        View azRow = beads ? mHost.findViewById(R.id.apps_bar_az_row) : null;
        if (mDockBeadsWatchedAzRow != azRow) {
            if (mDockBeadsWatchedAzRow != null)
                mDockBeadsWatchedAzRow.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
            mDockBeadsWatchedAzRow = azRow;
            if (azRow != null) azRow.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
    }

    @Nullable private View mDockBeadsWatchedAzRow;

    /** A layout moved the host or the row: the beads are read again before the next draw. */
    private void scheduleDockBeadsRefresh() {
        View host = mDockBeadsWatchedHost;
        if (mDockBeadsRefreshPending || host == null) return;
        mDockBeadsRefreshPending = true;
        host.getViewTreeObserver().addOnPreDrawListener(mDockBeadsPreDraw);
    }

    /**
     * After a layout: beads that moved re-aim the glass program (one rebuild of the dock's
     * effect) and the shadows; a row that turned to beads or back to the slab dresses the dock
     * again.
     */
    private void onDockBeadsLayoutSettled() {
        boolean wereBeads = mDockBeads != null;
        if (!refreshDockBeads()) return;
        mHost.rebuildBackdropGlassEffectForLens();
        DockShelfView shelf = mHost.findViewById(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        if (wereBeads != (mDockBeads != null) && mHost.chrome() != null)
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
    }

    /**
     * One frame of a crossing's morph under Lens beads: each bead worked out from its icon's
     * drawn box (SuggestionBarView#drawnIconBoxes) by the rule the beads at rest follow theirs
     * (LensBeadGeometry.drawnAt), on the surface the last full read found, so bead and icon
     * never part. The host, the shelf and the dock's effect once, as a swipe's frame does; at
     * rest and off beads nothing runs past the first check.
     */
    public void syncDockBeadsWithMorph() {
        com.termux.app.dock.LensBeadGeometry.Beads rest = mDockBeads;
        SuggestionBarView row = mHost.dockRow();
        if (rest == null || row == null || Float.isNaN(mDockBeadsRowDy)
            || Float.isNaN(mDockBeadsBottomPx)) return;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (!(host instanceof DockSurfaceHostLayout) || host.getWidth() <= 0) return;
        int count = row.drawnIconBoxes(mDockBeadBoxes);
        if (count <= 0 || count > com.termux.app.dock.LensBeadGeometry.MAX_BEADS) return;
        for (int i = 0; i < count; i++) {
            mDockBeadBoxes[i * 4] += mDockBeadsRowDx;
            mDockBeadBoxes[i * 4 + 1] += mDockBeadsRowDy;
            mDockBeadBoxes[i * 4 + 2] += mDockBeadsRowDx;
            mDockBeadBoxes[i * 4 + 3] += mDockBeadsRowDy;
        }
        com.termux.app.dock.LensBeadGeometry.Beads drawn =
            com.termux.app.dock.LensBeadGeometry.drawnAt(rest, mDockBeadBoxes, count, 0f, 0f,
                host.getWidth(), mDockBeadsBottomPx, mHost.density());
        if (drawn.equals(rest)) return;
        mDockBeads = drawn;
        ((DockSurfaceHostLayout) host).setBeads(dockBeadsDrawn());
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        mHost.rebuildBackdropGlassEffectForLens();
    }

    /**
     * Works the beads out again from the row and the host as laid out and hands them to the
     * host; true when they changed. A change between beads and the slab re-cuts the host's
     * shape at once (the slab's rim and clip go, or come back).
     */
    public boolean refreshDockBeads() {
        syncDockBeadsListeners();
        com.termux.app.dock.LensBeadGeometry.Beads beads = resolveDockBeads();
        // The A–Z row's letter beads under the icons', where it shares their plank.
        boolean azChanged = applyDockAzBeads(resolveDockAzBeads(beads));
        if (beads == null ? mDockBeads == null : beads.equals(mDockBeads)) {
            // The same beads, but the row may have moved on the screen under the frame's cut.
            mHost.syncFrameBeadCut();
            return azChanged;
        }
        boolean modeChanged = (beads == null) != (mDockBeads == null);
        mDockBeads = beads;
        // Back to the slab, nothing of the beads is kept: a swipe's moving beads would otherwise
        // be what the next bead mode draws first, and the swipe's sideways cut what it clips to.
        if (beads == null) mDockBeadsMoving = null;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setSlabOnlyChildren(mHost.findViewById(R.id.accessory_top_edge_fx),
                mHost.findViewById(R.id.accessory_edge_glow_fx));
            beadHost.setBeads(dockBeadsDrawn());
        }
        if (beads == null) {
            DockShelfView shelf = mHost.findViewById(R.id.accessory_shelf_fx);
            if (shelf != null) shelf.setBeads(null);
        }
        // The slab's cut again where beads came or went, the Search field's well (it nests in its
        // capsule, or in the slab again) and the frame's cut.
        mHost.onDockBeadsReplaced(host, modeChanged);
        return true;
    }

    /**
     * The beads for the row as laid out now, in the host's coordinates, or null where the dock
     * keeps its slab: a style that is not beads; the row not standing alone on the dock (another
     * band on the same glass, the keyboard sharing it, a joined Docked frame being the glass);
     * or a row the beads cannot draw ({@link com.termux.app.dock.LensBeadGeometry#compute}).
     * The Search field under the row is no band of that kind: it stands in a glass capsule of
     * its own under the beads (#84).
     */
    @Nullable
    private com.termux.app.dock.LensBeadGeometry.Beads resolveDockBeads() {
        if (!dockBeadsWanted()) return null;
        SuggestionBarView row = mHost.dockRow();
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        if (row == null || !(host instanceof DockSurfaceHostLayout) || container == null
            || host.getWidth() <= 0 || host.getHeight() <= 0 || row.getWidth() <= 0) return null;
        PlaceLayout layout = mHost.placeLayout();
        if (!com.termux.app.dock.LensBeadGeometry.rowWearsBeadsCuttingFrame(layout,
            mHost.stackStandsDownOnFrame(),
            mHost.keyboardSharesDockGlass())) return null;
        // Where the row draws its icons: as laid out at rest, where a crossing's morph draws
        // them mid-slide and through its landing (SuggestionBarView#drawnIconBoxes).
        int count = row.drawnIconBoxes(mDockBeadBoxes);
        if (count <= 0 || count > com.termux.app.dock.LensBeadGeometry.MAX_BEADS) return null;
        float[] rowAt = layoutOffsetIn(row, container);
        float[] hostAt = layoutOffsetIn(host, container);
        if (rowAt == null || hostAt == null) return null;
        float dx = rowAt[0] - hostAt[0];
        float dy = rowAt[1] - hostAt[1];
        // The span the row clips its icons to while a swipe carries them: the beads follow there.
        mDockBeadsRowDx = dx;
        mDockBeadsSpanLeft = Math.max(0f, dx);
        mDockBeadsSpanRight = Math.min(host.getWidth(), dx + row.getWidth());
        float[] boxes = new float[count * 4];
        for (int i = 0; i < count; i++) {
            boxes[i * 4] = mDockBeadBoxes[i * 4] + dx;
            boxes[i * 4 + 1] = mDockBeadBoxes[i * 4 + 1] + dy;
            boxes[i * 4 + 2] = mDockBeadBoxes[i * 4 + 2] + dx;
            boxes[i * 4 + 3] = mDockBeadBoxes[i * 4 + 3] + dy;
        }
        float density = mHost.density();
        // The Search field under the row (#84): the beads are held to the row's own band, as
        // they are without it, and the field stands in a glass capsule of its own under them,
        // its view's bounds (LensBeadGeometry.withCapsule).
        float[] fieldAt = null;
        com.termux.app.dock.PromptLineFieldView field = mHost.promptFieldInRowBand();
        if (field != null) {
            fieldAt = layoutOffsetIn(field, container);
        }
        float bottom = host.getHeight();
        if (fieldAt != null) {
            fieldAt[0] -= hostAt[0];
            fieldAt[1] -= hostAt[1];
            int topPad = ((ViewGroup.MarginLayoutParams) field.getLayoutParams()).topMargin;
            bottom = Math.min(bottom, fieldAt[1] - topPad);
        }
        mDockBeadsRowDy = dy;
        mDockBeadsBottomPx = bottom;
        com.termux.app.dock.LensBeadGeometry.Beads beads =
            com.termux.app.dock.LensBeadGeometry.compute(boxes, count, 0f, 0f, host.getWidth(),
                bottom, density);
        if (beads == null || fieldAt == null) return beads;
        return com.termux.app.dock.LensBeadGeometry.withCapsule(beads, fieldAt[0], fieldAt[1],
            fieldAt[0] + field.getWidth(), fieldAt[1] + field.getHeight(), density);
    }

    // ------------------------------------------------------------------ Letter beads
    //
    // Under Lens beads with the A–Z row on the dock's plank under the icons
    // (LensBeadGeometry.azRowWearsBeads), each letter stands on a small bead of the icons' glass
    // (AzBeadRow): the host clips its frost to them as to the icons' beads, the dock's one glass
    // program draws their rims from the row's first centre and pitch (Program#setAzBeads), and
    // a finger on the row lifts the bead under it and its neighbours with their letters
    // (AzScrubRowView#setBeadLift). A lift's frame re-cuts the host's clip in place and rebuilds
    // the dock's effect once, as a page swipe's frame does; no layout runs, and at rest nothing.

    /** The letter beads in the host's coordinates, or null where the row has none. */
    @Nullable private com.termux.app.dock.AzBeadRow mDockAzBeads;
    /** The A–Z row's x in the host, so a finger along the row is a finger in the host. */
    private float mDockAzRowDx;
    /** The lift as last drawn: the finger's x in the host (NaN for none) and the peak, px. */
    private float mDockAzLiftX = Float.NaN;
    private float mDockAzLiftPeakPx;
    /** The row the letter beads were last handed to, so it can be let go of. */
    @Nullable private AzScrubRowView mDockAzBeadRowView;
    private final AzScrubRowView.BeadLiftListener mDockAzBeadLift = this::onDockAzBeadLift;

    /**
     * The letter beads for the A–Z row as laid out now, in the host's coordinates, or null: no
     * icons' beads ({@code beads}), the row not under the icons on the dock's plank, not shown,
     * or too crowded for a bead to read as a lens.
     */
    @Nullable
    private com.termux.app.dock.AzBeadRow resolveDockAzBeads(
            @Nullable com.termux.app.dock.LensBeadGeometry.Beads beads) {
        if (beads == null) return null;
        View view = mHost.findViewById(R.id.apps_bar_az_row);
        if (!(view instanceof AzScrubRowView)) return null;
        AzScrubRowView az = (AzScrubRowView) view;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        if (host == null || container == null || !az.isShown() || az.isVerticalBar()
            || az.getWidth() <= 0 || az.getHeight() <= 0
            || !com.termux.app.dock.LensBeadGeometry.azRowWearsBeads(mHost.placeLayout()))
            return null;
        float[] azAt = layoutOffsetIn(az, container);
        float[] hostAt = layoutOffsetIn(host, container);
        if (azAt == null || hostAt == null) return null;
        float dx = azAt[0] - hostAt[0];
        float dy = azAt[1] - hostAt[1];
        mDockAzRowDx = dx;
        return com.termux.app.dock.AzBeadRow.compute(dx + az.letterCenterPx(0),
            az.letterPitchPx(), az.letterCount(), dy + az.getHeight() * 0.5f, az.getHeight(),
            mHost.density());
    }

    /**
     * Hands the letter beads to the host and the row (and lets go of them with null); true when
     * they changed. The dock's effect is rebuilt by the caller, as for the icons' beads.
     */
    private boolean applyDockAzBeads(@Nullable com.termux.app.dock.AzBeadRow azBeads) {
        View rowView = mHost.findViewById(R.id.apps_bar_az_row);
        AzScrubRowView row = azBeads != null && rowView instanceof AzScrubRowView
            ? (AzScrubRowView) rowView : null;
        if (mDockAzBeadRowView != null && mDockAzBeadRowView != row)
            mDockAzBeadRowView.setBeadLift(false, null);
        mDockAzBeadRowView = row;
        if (row != null) row.setBeadLift(true, mDockAzBeadLift);
        if (azBeads == null ? mDockAzBeads == null : azBeads.equals(mDockAzBeads)) return false;
        mDockAzBeads = azBeads;
        if (azBeads == null) {
            mDockAzLiftX = Float.NaN;
            mDockAzLiftPeakPx = 0f;
        }
        View host = mHost.findViewById(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setAzBeadLift(mDockAzLiftX, mDockAzLiftPeakPx);
            beadHost.setAzBeads(azBeads);
        }
        return true;
    }

    /**
     * One frame of the letter beads' lift (AzScrubRowView#setBeadLift): the finger along the row
     * and the spring's share. The host's clip is re-cut in place and the dock's effect rebuilt
     * once; nothing is laid out.
     */
    private void onDockAzBeadLift(float fingerAlongPx, float strength) {
        if (mDockAzBeads == null) return;
        float x = Float.isNaN(fingerAlongPx) ? Float.NaN : fingerAlongPx + mDockAzRowDx;
        float peak = strength * com.termux.app.dock.AzBeadRow.LIFT_PEAK_DP
            * mHost.density();
        if (Float.compare(x, mDockAzLiftX) == 0 && Float.compare(peak, mDockAzLiftPeakPx) == 0)
            return;
        mDockAzLiftX = x;
        mDockAzLiftPeakPx = peak;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout)
            ((DockSurfaceHostLayout) host).setAzBeadLift(x, peak);
        mHost.rebuildBackdropGlassEffectForLens();
    }

    /**
     * A view's laid-out offset inside {@code ancestor}, from layout values only (a transform in
     * flight, such as the dock's press dip, is not baked in), or null when it is not under it.
     */
    @Nullable
    public static float[] layoutOffsetIn(@NonNull View view, @NonNull View ancestor) {
        float left = 0f;
        float top = 0f;
        for (View v = view; v != null; ) {
            if (v == ancestor) return new float[] {left, top};
            left += v.getLeft();
            top += v.getTop();
            android.view.ViewParent parent = v.getParent();
            if (parent instanceof View) {
                View p = (View) parent;
                left -= p.getScrollX();
                top -= p.getScrollY();
                v = p;
            } else {
                v = null;
            }
        }
        return null;
    }
}
