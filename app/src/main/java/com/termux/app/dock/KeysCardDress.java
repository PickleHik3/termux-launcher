package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.DockShelfView;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.SharedFrameDrawable;
import com.termux.app.chrome.SurfaceDirtyLedger;

/**
 * The Terminal's extra-keys card wearing the dock capsule's glass (ADR 0032, #302): the same
 * steps the activity runs for the host when the host is the capsule, on the card's own views
 * ({@code extra_keys_card_*}). The numbers come from {@link CapsuleGlass}, the shape, the
 * program's slab glass, the shelf rule and the plank layers from {@link DockSurface} and
 * {@link DockController}, so the card and the capsule are one code and cannot drift; what is
 * single-instance state on the host's side is a second instance here (the effect's remembered
 * params, the ledger's {@code KEYS_CARD} record, the program-edge flag, the ground overlay, the
 * Live picture and its glass entry).
 *
 * <p>The card is Live glass only while it can be seen: dressed (and its picture registered) at a
 * rest where the Terminal stands in front, and at the begin of a Home to Terminal crossing so
 * its picture is up the frame the card first shows; released, with its picture dropped, at a rest
 * where it is gone or parked off screen at Home's rest. Never per frame: a crossing writes
 * nothing here.
 */
public final class KeysCardDress {

    /** What only the activity owns that the card's dress reads or writes. */
    public interface Source {
        /** A crossing is live: nothing is released or measured. */
        boolean crossing();
        /** The dock's blur is on for {@code state} (blur set, not standing down on a frame). */
        boolean blurWanted(@NonNull ChromeSpec state);
        /** Whether the card's backdrop shows a picture now, stored or live. */
        boolean pictureUp(@NonNull ImageView backdrop);
        /** The backdrop's live picture at {@code radiusDp}, registered with the glass service; null when not Live. */
        @Nullable Drawable attachLive(@NonNull ImageView backdrop, int radiusDp);
        /** Lets the backdrop's live picture go and drops its glass entry. */
        void releaseLive(@NonNull ImageView backdrop);
        /** The stored blur frame for the radius (null while one is in flight); its rect goes to {@code outRect}. */
        @Nullable Bitmap blurFrame(int radiusDp, @NonNull Rect outRect);
        /** The frost with the dock band's even-out measured under {@code surface}; null measures nothing. */
        @Nullable ColorFilter frostFilter(@Nullable View surface);
        /** The blur's source is the managed wallpaper. */
        boolean managedSource();
        /** A Frost drag is on: the frame up stays until its release. */
        boolean glassDragPreview();
        /** The Fancier Glass look, or null for the numbers the dock always ran with. */
        @Nullable GlassRefraction.Look fancierLook();
        /** The under-pill foot's height; above 0 the backdrop reaches down past the seam. */
        int footPx();
        /** The plate's glass, or null where a joined frame is the glass. */
        @Nullable Drawable plate(float barAlpha);
        /** The theme's outline colour, for the top edge's seam. */
        int outlineColor();
        /** The ground overlay of the page on {@code backdrop}, made once ({@code held} null); null while ground is off. */
        @Nullable GroundUnderGlass ground(@Nullable GroundUnderGlass held, @NonNull ImageView backdrop);
        /** Points {@code backdrop} at the live picture or the stored frame, whichever stands. */
        void installPicture(@NonNull ImageView backdrop, @Nullable SharedFrameDrawable installed,
                            @NonNull Bitmap frame, @NonNull Rect frameRect, int radiusDp,
                            @Nullable Drawable live);
    }

    @NonNull private final DockController mDock;
    @NonNull private final DockController.Host mHost;
    @NonNull private final Source mSource;
    @NonNull private final GlassBackdropDress mBackdrop;

    public KeysCardDress(@NonNull DockController dock, @NonNull Source source) {
        mDock = dock;
        mHost = dock.host();
        mBackdrop = new GlassBackdropDress(dock, source, new GlassBackdropDress.Owner() {
            @Override public float glassRadiusPx(int heightPx) {
                // The capsule: the program's rect and radius as the host's capsule computes them.
                return mDock.resolveDockCapsuleCornerRadiusPx(heightPx);
            }
            @Override public int seams() {
                return 0;
            }
            @Override public void dressShape(@NonNull View surface) {
                KeysCardDress.this.dressShape(surface,
                    mDock.resolveDockCapsuleCornerRadiusPx(cardHeightPx(surface)));
            }
            @Override public void syncShelf(@Nullable ChromeSpec state) {
                KeysCardDress.this.syncShelf(state);
            }
        }, SurfaceDirtyLedger.Backdrop.KEYS_CARD, R.id.extra_keys_card_blur_backdrop, true);
        mSource = source;
    }

    /** Whether the card's glass is up (dressed and not released). */
    public boolean shown() {
        return mBackdrop.shown();
    }

    /**
     * One rest pass: dresses the card while it stands (the cards apart, the Terminal in front),
     * releases it where it is gone or parked at Home's rest. A live crossing changes nothing.
     */
    public void update(@NonNull ChromeSpec state) {
        if (mSource.crossing()) return;
        View card = mHost.findViewById(R.id.extra_keys_card);
        boolean standing = card != null && card.getVisibility() == View.VISIBLE
            && state.toolbarShown && mHost.stackRenderer().keysCardStands();
        if (!standing) {
            release();
            return;
        }
        dress(state, true);
    }

    /**
     * A Home to Terminal crossing begins: the card, laid out at Home's rest already, is dressed
     * now so its picture is up when it first shows. A rest-like step, once per crossing.
     */
    public void onCrossingBegin(@NonNull ChromeSpec state) {
        View card = mHost.findViewById(R.id.extra_keys_card);
        if (card == null || card.getVisibility() != View.VISIBLE || !state.toolbarShown) return;
        // Parked off screen still: nothing is measured under it, the rest's pass does that.
        dress(state, false);
    }

    /** Lets the card's glass go: its picture and glass entry, its effect, its layers. */
    public void release() {
        if (!mBackdrop.shown() && !mBackdrop.watching()) return;
        mBackdrop.setShown(false);
        ImageView backdrop = mHost.findViewById(R.id.extra_keys_card_blur_backdrop);
        if (backdrop != null) mBackdrop.clearBackdrop(backdrop);
        mBackdrop.hide(R.id.extra_keys_card_top_edge_fx);
        mBackdrop.hide(R.id.extra_keys_card_background);
        mBackdrop.hide(R.id.extra_keys_card_edge_glow_fx);
        mBackdrop.hide(R.id.extra_keys_card_specular_fx);
        syncShelf(null);
    }

    // ---- The dress -----------------------------------------------------------------------------

    private void dress(@NonNull ChromeSpec state, boolean atRest) {
        View card = mHost.findViewById(R.id.extra_keys_card);
        ImageView backdrop = mHost.findViewById(R.id.extra_keys_card_blur_backdrop);
        if (card == null || backdrop == null) return;
        mBackdrop.watch(card);
        mBackdrop.setShown(true);
        float radiusPx = mDock.resolveDockCapsuleCornerRadiusPx(cardHeightPx(card));
        float barAlpha = state.barAlpha;
        // The plate, as doApplyChromeSpec dresses the host's sheet.
        View plate = mHost.findViewById(R.id.extra_keys_card_background);
        if (plate != null) {
            plate.setBackground(mSource.plate(barAlpha));
            plate.setAlpha(1f);
            if (plate.getVisibility() != View.VISIBLE) plate.setVisibility(View.VISIBLE);
        }
        // Edge glow and specular, the plank's reactive layers.
        View specular = mHost.findViewById(R.id.extra_keys_card_specular_fx);
        View glow = mHost.findViewById(R.id.extra_keys_card_edge_glow_fx);
        mDock.dressPlankLayers(specular, glow, radiusPx, mHost.resolveDockAccentColor(), barAlpha);
        // At rest the rim glow stands lit; the plank's own controller owns it once it is bound.
        if (glow != null && glow.getVisibility() != View.VISIBLE) glow.setVisibility(View.VISIBLE);
        View edgeFx = mHost.findViewById(R.id.extra_keys_card_top_edge_fx);
        if (edgeFx != null) CapsuleGlass.dressTopEdge(edgeFx, true, barAlpha, mSource.outlineColor());
        dressShape(card, radiusPx);
        mBackdrop.updateBackdrop(state, card, backdrop, atRest);
    }

    /** The rim under the glass (or the outline alone where the program draws the edge), as the capsule's. */
    private void dressShape(@NonNull View card, float radiusPx) {
        card.setBackground(mDock.surface().capsuleOutline(radiusPx, mBackdrop.programEdge()));
    }

    /** {@link DockSurface#syncDockShelf} on the card's shelf: it follows the card by itself. */
    private void syncShelf(@Nullable ChromeSpec state) {
        DockShelfView shelf = mHost.findViewById(R.id.extra_keys_card_shelf_fx);
        if (shelf == null) return;
        View card = mHost.findViewById(R.id.extra_keys_card);
        boolean show = state != null && card != null && DockSurface.shelfShown(
            mBackdrop.paramsValid(), mHost.dockRows().surfaceShown(), state.keyboardShown, true,
            mDock.keysCardSlabSpec().contactShadow);
        shelf.setBeads(null);
        shelf.follow(show ? card : null);
    }

    private static int cardHeightPx(@NonNull View card) {
        ViewGroup.LayoutParams params = card.getLayoutParams();
        return params != null && params.height > 0 ? params.height : card.getHeight();
    }
}
